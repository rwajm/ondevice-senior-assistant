package com.k2j3.seniorassistant.device.audio

/** -1..1 float → 16-bit. 범위를 넘는 값은 잘라서 '찢어지는' 소리를 막는다 */
fun FloatArray.toPcm16(): ShortArray = ShortArray(size) { i ->
    (this[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
}

/** 16-bit → -1..1 float (sherpa-onnx 입력) */
fun ShortArray.toFloats(count: Int = size): FloatArray = FloatArray(count) { i -> this[i] / 32768f }

/** WAV 소리 → -1..1 float mono. 채널이 여럿이면 평균한다 */
fun Pcm16.toMonoFloats(): FloatArray = FloatArray(frames) { f ->
    var sum = 0
    for (c in 0 until channels) {
        val at = (f * channels + c) * 2
        sum += (data[at].toInt() and 0xFF) or (data[at + 1].toInt() shl 8)
    }
    sum / channels / 32768f
}
