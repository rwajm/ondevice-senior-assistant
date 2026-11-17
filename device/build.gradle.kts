import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.k2j3.seniorassistant.device"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.k2j3.seniorassistant.device"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 문서 읽기(EXT-01)의 OpenAI 키: local.properties(git에 올리지 않음)의 openai.api.key.
        // 없으면 빈 값으로 빌드되고, 문서 읽기는 "지금은 문서를 읽을 수 없어요"로 답한다.
        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "OPENAI_API_KEY", "\"${localProperties.getProperty("openai.api.key", "").trim()}\"")

        ndk {
            // QCS6490 board and the Apple Silicon emulator; keeps the other sherpa-onnx ABIs (~90 MB) out
            abiFilters += "arm64-v8a"
        }
    }

    // the JNI bridge to Qualcomm Genie (on-device LLM on the NPU), see src/main/cpp/genie_jni.c
    ndkVersion = "27.0.12077973"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        jniLibs {
            // installed as real files: the NPU's DSP loads libQnnHtpV68Skel.so from the app's lib folder
            useLegacyPackaging = true
            // the Qualcomm runtime (src/main/jniLibs, copied from the LLM bundle, not in git) goes in as shipped;
            // the skel is a Hexagon DSP binary that the Android strip tool must not touch
            keepDebugSymbols += listOf("**/libQnn*.so", "**/libGenie.so", "**/libc++_shared.so")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":api"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    // STT (SenseVoice), VAD (Silero), TTS (VITS) on the CPU. Not on Maven; the AAR is the official
    // GitHub release asset: k2-fsa/sherpa-onnx v1.13.8, sha256 633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96
    // Stay on 1.x: 2.0 removed espeak-ng, which the Korean VITS model needs for pronunciation.
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}