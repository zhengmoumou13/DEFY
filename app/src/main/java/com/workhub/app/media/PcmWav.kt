package com.workhub.app.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Canonical mono, PCM16, 16 kHz WAV used by the recorder and voice requests. */
internal object PcmWav {
    const val RATE = 16_000
    const val HEADER_SIZE = 44
    fun header(dataBytes: Int): ByteArray {
        require(dataBytes >= 0 && dataBytes % 2 == 0)
        return ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataBytes)
        }.array()
    }
    fun validate(bytes: ByteArray, maxSeconds: Int) {
        require(bytes.size > HEADER_SIZE && bytes.size <= HEADER_SIZE + RATE * 2 * maxSeconds) { "录音为空或超过 $maxSeconds 秒，请重新录制" }
        val count = bytes.size - HEADER_SIZE
        require(count % 2 == 0 && bytes.copyOfRange(0, HEADER_SIZE).contentEquals(header(count))) { "GLM-ASR 需要本机录制的 16 kHz 单声道 WAV，请重新录音" }
    }
}
