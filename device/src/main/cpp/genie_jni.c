// JNI bridge to Qualcomm Genie (QAIRT 2.40, libGenie.so) for GenieNative.kt.
//
// libGenie.so is opened with dlopen at run time, not linked, so the app still builds and runs
// (with the fake LLM) on machines and devices without the Qualcomm libraries in jniLibs.
// Strings cross the boundary as UTF-8 byte arrays: JNI's modified UTF-8 is not real UTF-8.
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define TAG "GenieJni"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// Genie C API, only the calls used here (checked against libGenie.so 1.13 exports)
typedef int32_t Genie_Status_t;
typedef const void* GenieDialogConfig_Handle_t;
typedef const void* GenieDialog_Handle_t;
typedef int GenieDialog_SentenceCode_t; // 0 = the query is one complete sentence
typedef void (*GenieDialog_QueryCallback_t)(const char*, const GenieDialog_SentenceCode_t, const void*);

static Genie_Status_t (*config_create)(const char*, GenieDialogConfig_Handle_t*);
static Genie_Status_t (*config_free)(const GenieDialogConfig_Handle_t);
static Genie_Status_t (*dialog_create)(const GenieDialogConfig_Handle_t, GenieDialog_Handle_t*);
static Genie_Status_t (*dialog_query)(const GenieDialog_Handle_t, const char*, const GenieDialog_SentenceCode_t,
                                      const GenieDialog_QueryCallback_t, const void*);
static Genie_Status_t (*dialog_reset)(const GenieDialog_Handle_t);
static Genie_Status_t (*dialog_free)(const GenieDialog_Handle_t);

typedef struct {
    GenieDialogConfig_Handle_t config;
    GenieDialog_Handle_t dialog;
} Genie;

typedef struct {
    char* text;
    size_t length;
    size_t capacity;
} Answer;

static void throw_state(JNIEnv* env, const char* message) {
    jclass type = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (type) (*env)->ThrowNew(env, type, message);
}

// a NUL-terminated copy of a Java byte array, or NULL with an exception pending
static char* c_string(JNIEnv* env, jbyteArray bytes) {
    jsize length = (*env)->GetArrayLength(env, bytes);
    char* text = malloc((size_t)length + 1);
    if (!text) {
        throw_state(env, "out of memory");
        return NULL;
    }
    (*env)->GetByteArrayRegion(env, bytes, 0, length, (jbyte*)text);
    text[length] = 0;
    return text;
}

static void on_text(const char* response, const GenieDialog_SentenceCode_t code, const void* user) {
    (void)code;
    Answer* answer = (Answer*)user;
    if (!response || !answer->text) return;
    size_t add = strlen(response);
    if (answer->length + add + 1 > answer->capacity) {
        size_t capacity = (answer->length + add + 1) * 2;
        char* grown = realloc(answer->text, capacity);
        if (!grown) {
            free(answer->text);
            answer->text = NULL; // reported as out of memory after the query
            return;
        }
        answer->text = grown;
        answer->capacity = capacity;
    }
    memcpy(answer->text + answer->length, response, add + 1);
    answer->length += add;
}

// null when libGenie.so and every call it needs are there, otherwise why not
JNIEXPORT jstring JNICALL
Java_com_k2j3_seniorassistant_device_ai_llm_GenieNative_nativeOpen(JNIEnv* env, jclass clazz) {
    (void)clazz;
    if (dialog_query) return NULL;
    void* genie = dlopen("libGenie.so", RTLD_NOW | RTLD_LOCAL);
    if (!genie) return (*env)->NewStringUTF(env, dlerror());
    config_create = dlsym(genie, "GenieDialogConfig_createFromJson");
    config_free = dlsym(genie, "GenieDialogConfig_free");
    dialog_create = dlsym(genie, "GenieDialog_create");
    dialog_reset = dlsym(genie, "GenieDialog_reset");
    dialog_free = dlsym(genie, "GenieDialog_free");
    dialog_query = dlsym(genie, "GenieDialog_query");
    if (!config_create || !config_free || !dialog_create || !dialog_reset || !dialog_free || !dialog_query) {
        dialog_query = NULL;
        return (*env)->NewStringUTF(env, "libGenie.so is missing a GenieDialog call");
    }
    return NULL;
}

JNIEXPORT jlong JNICALL
Java_com_k2j3_seniorassistant_device_ai_llm_GenieNative_nativeCreate(JNIEnv* env, jclass clazz, jbyteArray json) {
    (void)clazz;
    if (!dialog_query) {
        throw_state(env, "libGenie.so is not open");
        return 0;
    }
    char* config_json = c_string(env, json);
    if (!config_json) return 0;
    Genie* genie = calloc(1, sizeof(Genie));
    Genie_Status_t status = config_create(config_json, &genie->config);
    free(config_json);
    char message[96];
    if (status != 0) {
        snprintf(message, sizeof message, "GenieDialogConfig_createFromJson failed: %d", status);
        free(genie);
        throw_state(env, message);
        return 0;
    }
    status = dialog_create(genie->config, &genie->dialog);
    if (status != 0) {
        snprintf(message, sizeof message, "GenieDialog_create failed: %d", status);
        config_free(genie->config);
        free(genie);
        throw_state(env, message);
        return 0;
    }
    return (jlong)(intptr_t)genie;
}

// every prompt starts from an empty KV cache, like a fresh genie-t2t-run (checked on the board: same answers)
JNIEXPORT jbyteArray JNICALL
Java_com_k2j3_seniorassistant_device_ai_llm_GenieNative_nativeQuery(JNIEnv* env, jclass clazz, jlong handle,
                                                                    jbyteArray prompt_bytes) {
    (void)clazz;
    Genie* genie = (Genie*)(intptr_t)handle;
    char* prompt = c_string(env, prompt_bytes);
    if (!prompt) return NULL;
    Answer answer = {calloc(1, 256), 0, 256};
    dialog_reset(genie->dialog);
    Genie_Status_t status = dialog_query(genie->dialog, prompt, 0, on_text, &answer);
    dialog_reset(genie->dialog);
    free(prompt);
    if (status != 0 || !answer.text) {
        char message[64];
        snprintf(message, sizeof message, status != 0 ? "GenieDialog_query failed: %d" : "out of memory", status);
        free(answer.text);
        throw_state(env, message);
        return NULL;
    }
    jbyteArray result = (*env)->NewByteArray(env, (jsize)answer.length);
    if (result) (*env)->SetByteArrayRegion(env, result, 0, (jsize)answer.length, (const jbyte*)answer.text);
    free(answer.text);
    return result;
}

JNIEXPORT void JNICALL
Java_com_k2j3_seniorassistant_device_ai_llm_GenieNative_nativeFree(JNIEnv* env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    Genie* genie = (Genie*)(intptr_t)handle;
    if (!genie) return;
    if (dialog_free(genie->dialog) != 0) LOGW("GenieDialog_free failed");
    config_free(genie->config);
    free(genie);
}
