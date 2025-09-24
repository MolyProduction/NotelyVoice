package com.module.notelycompose.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streams decoded PCM samples from a WAV file in fixed‑size chunks.
 *
 * @param file           16 kHz, 16‑bit, mono WAV file.
 * @param chunkFrames    how many audio frames (samples) per invocation.
 *                       Smaller ⇒ lower memory but more callbacks.
 * @return               a Flow that emits FloatArrays of up to [chunkFrames] samples.
 */
fun decodeWaveFileStream(
    file: File,
    chunkFrames: Int = 4_096
): Flow<FloatArray> = flow {
    FileInputStream(file).use { input ->
        // 1) Skip WAV header (44 bytes)
        val header = ByteArray(44)
        if (input.read(header) != header.size) {
            throw IOException("Unable to read WAV header")
        }
        // (we assume mono, 16‑bit, little endian)
        val bytesPerSample = 2
        val frameSizeBytes = bytesPerSample * /* channels */ 1

        // 2) Now read & emit in chunks
        val buffer = ByteArray(chunkFrames * frameSizeBytes)
        var bytesRead: Int

        while (input.read(buffer).also { bytesRead = it } > 0) {
            val framesRead = bytesRead / frameSizeBytes
            val floatChunk = FloatArray(framesRead)
            val bb = ByteBuffer.wrap(buffer, 0, bytesRead)
                .order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until framesRead) {
                // mono: read one 16‑bit sample
                floatChunk[i] = (bb.short / 32767f).coerceIn(-1f, 1f)
            }
            emit(floatChunk)
        }
    }
}.flowOn(Dispatchers.IO)


fun decodeWaveFile(file: File): FloatArray {
    val baos = ByteArrayOutputStream()
    file.inputStream().use { it.copyTo(baos) }
    val buffer = ByteBuffer.wrap(baos.toByteArray())
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    val channel = buffer.getShort(22).toInt()
    buffer.position(44)
    val shortBuffer = buffer.asShortBuffer()
    val shortArray = ShortArray(shortBuffer.limit())
    shortBuffer.get(shortArray)
    return FloatArray(shortArray.size / channel) { index ->
        when (channel) {
            1 -> (shortArray[index] / 32767.0f).coerceIn(-1f..1f)
            else -> ((shortArray[2 * index] + shortArray[2 * index + 1]) / 32767.0f / 2.0f).coerceIn(
                -1f..1f
            )
        }
    }
}

fun encodeWaveFile(file: File, data: ShortArray) {
    file.outputStream().use {
        it.write(headerBytes(data.size * 2))
        val buffer = ByteBuffer.allocate(data.size * 2)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().put(data)
        val bytes = ByteArray(buffer.limit())
        buffer.get(bytes)
        it.write(bytes)
    }
}

private fun headerBytes(totalLength: Int): ByteArray {
    require(totalLength >= 44)
    ByteBuffer.allocate(44).apply {
        order(ByteOrder.LITTLE_ENDIAN)

        put('R'.code.toByte())
        put('I'.code.toByte())
        put('F'.code.toByte())
        put('F'.code.toByte())

        putInt(totalLength - 8)

        put('W'.code.toByte())
        put('A'.code.toByte())
        put('V'.code.toByte())
        put('E'.code.toByte())

        put('f'.code.toByte())
        put('m'.code.toByte())
        put('t'.code.toByte())
        put(' '.code.toByte())

        putInt(16)
        putShort(1.toShort())
        putShort(1.toShort())
        putInt(16000)
        putInt(32000)
        putShort(2.toShort())
        putShort(16.toShort())

        put('d'.code.toByte())
        put('a'.code.toByte())
        put('t'.code.toByte())
        put('a'.code.toByte())

        putInt(totalLength - 44)
        position(0)
    }.also {
        val bytes = ByteArray(it.limit())
        it.get(bytes)
        return bytes
    }
}