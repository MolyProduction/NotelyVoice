package com.module.notelycompose.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.content.ContextCompat
import audio.utils.LauncherHolder
import com.module.notelycompose.core.debugPrintln
import com.module.notelycompose.utils.decodeWaveFileStream
import com.whispercpp.whisper.WhisperCallback
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

actual class Transcriber(
    private val context: Context,
    private val launcherHolder: LauncherHolder
) {
    private var canTranscribe: Boolean = false
    private var isTranscribing = false
    private val modelsPath = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    private var whisperContext: WhisperContext? = null
    private var permissionContinuation: ((Boolean) -> Unit)? = null


    actual fun hasRecordingPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }


    actual suspend fun requestRecordingPermission(): Boolean {
        if (hasRecordingPermission()) {
            return true
        }

        return suspendCancellableCoroutine { continuation ->
            permissionContinuation = { isGranted ->
                continuation.resume(isGranted)
            }

            if (launcherHolder.permissionLauncher != null) {
                launcherHolder.permissionLauncher?.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            } else {
                continuation.resume(false)
            }

            continuation.invokeOnCancellation {
                permissionContinuation = null
            }
        }
    }


    actual suspend fun initialize() {
        debugPrintln{"speech: initialize model"}
        loadBaseModel()
    }

    private fun loadBaseModel(){
        debugPrintln{"Loading model...\n"}
        val firstModel = File(modelsPath, "ggml-base.bin")
        whisperContext = WhisperContext.createContextFromFile(firstModel.absolutePath)
        canTranscribe = true
    }

    actual fun doesModelExists() : Boolean{
        val firstModel = File(modelsPath, "ggml-base.bin")
        return firstModel.exists()
    }

    actual fun isValidModel() : Boolean{
        try {
            loadBaseModel()
        }catch (e:Exception){
            return false
        }
        return true
    }

    actual suspend fun stop() {
        isTranscribing = false
        whisperContext?.stopTranscription()
    }

    actual suspend fun finish() {
        whisperContext?.release()
    }

    actual suspend fun start(
        filePath: String,
        language: String,
        onProgress: (Int) -> Unit,
        onNewSegment: (Long, Long, String) -> Unit,
        onComplete: () -> Unit
    ) {
        if (!canTranscribe) return
        canTranscribe = false

        try {
            debugPrintln { "Streaming wave samples in chunks..." }
            val file = File(filePath)

            // 1 second of audio at 16 kHz
            val chunkFrames = 16_000

            // track how many frames we've processed so we can report progress
            var totalFramesRead = 0L
            // approximate total frames from file size (minus 44 byte header):
            val totalFrames = ((file.length() - 44) / 2 /*bytes/sample*/)

            // 1) collectIndexed gives us the chunk index
            decodeWaveFileStream(file, chunkFrames).collectIndexed { chunkIndex, floatChunk ->
                totalFramesRead += floatChunk.size

                debugPrintln { "Chunk #$chunkIndex → ${floatChunk.size} samples" }

                // 2) send each chunk into Whisper
                whisperContext?.transcribeData(
                    floatChunk,
                    language,
                    callback = object : WhisperCallback {
                        override fun onNewSegment(startMs: Long, endMs: Long, text: String) {
                            onNewSegment(startMs, endMs, text)
                        }

                        override fun onProgress(progress: Int) {
                            onProgress(progress)
                        }

                        override fun onComplete() {
                            // per‑chunk completion if needed
                        }
                    }
                )

                // 3) fire a rough progress callback in ms
                val progressMs =
                    (totalFramesRead.toFloat() / totalFrames * (file.length() / 16_000)).toInt()
                onProgress(progressMs)
            }

            debugPrintln { "All chunks processed, signaling complete." }
            onComplete()

        } catch (e: Exception) {
            e.printStackTrace()
            debugPrintln { "Error: ${e.localizedMessage}" }
        } finally {
            canTranscribe = true
        }
    }
}