package com.k2j3.seniorassistant.device.ai

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelFilesTest {
    private val root: File = Files.createTempDirectory("models").toFile()
    private val files = ModelFiles(root)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun everythingIsMissingAtFirst() {
        assertEquals(
            listOf(
                "vad/silero_vad.onnx",
                "stt/model.int8.onnx", "stt/tokens.txt",
                "tts/ko_KO-kss_low.onnx", "tts/tokens.txt", "tts/espeak-ng-data",
            ),
            files.missing(),
        )
    }

    @Test
    fun pushedFilesAreFound() {
        put("vad/silero_vad.onnx")
        put("stt/model.int8.onnx")
        put("stt/tokens.txt")

        assertEquals(emptyList<String>(), files.missing(files.vadFiles + files.sttFiles))
    }

    @Test
    fun emptyFileOrFolderCountsAsMissing() { // adb push가 중간에 끊긴 경우
        File(root, "vad").mkdirs()
        File(root, "vad/silero_vad.onnx").createNewFile()
        File(root, "tts/espeak-ng-data").mkdirs()

        assertEquals(listOf("vad/silero_vad.onnx"), files.missing(files.vadFiles))
        assertEquals(listOf("tts/espeak-ng-data"), files.missing(listOf(files.ttsDataDir)))
    }

    @Test
    fun dataFolderWithFilesIsFound() {
        put("tts/espeak-ng-data/ko_dict")
        assertEquals(emptyList<String>(), files.missing(listOf(files.ttsDataDir)))
    }

    private fun put(path: String) {
        File(root, path).apply { parentFile!!.mkdirs() }.writeText("x")
    }
}
