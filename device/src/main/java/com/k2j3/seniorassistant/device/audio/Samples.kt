package com.k2j3.seniorassistant.device.audio

/** -1..1 float → 16-bit. 범위를 넘는 값은 잘라서 '찢어지는' 소리를 막는다 */
fun FloatArray.toPcm16(): ShortArray = ShortArray(size) { i ->
    (this[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
}

/** 16-bit → -1..1 float (sherpa-onnx 입력) */
fun ShortArray.toFloats(count: Int = size): FloatArray = FloatArray(count) { i -> this[i] / 32768f }
