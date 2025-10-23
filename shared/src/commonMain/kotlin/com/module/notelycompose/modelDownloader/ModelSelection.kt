package com.module.notelycompose.modelDownloader

import com.module.notelycompose.onboarding.data.PreferencesRepository
import kotlinx.coroutines.flow.first

const val NO_MODEL_SELECTION = -1
const val STANDARD_MODEL_SELECTION = 0
const val OPTIMIZED_MODEL_SELECTION = 1
const val HINDI_MODEL_SELECTION = 2
const val ENGLISH_MODEL = "en"
const val OPTIMIZED_MODEL = "en"
const val HINDI_MODEL = "hi"

data class TranscriptionModel(val name:String, val modelType: String, val size:String, val description:String, val url:String){
    fun getModelDownloadSize():String = size
    fun getModelDownloadType():String = modelType
}

class ModelSelection(private val preferencesRepository: PreferencesRepository) {

    /**
     * Available Whisper models
     */
    private val models = listOf(
        TranscriptionModel(
            "ggml-base-en.bin",
            ENGLISH_MODEL,
            "142 MB",
            "Multilingual model (supports 50+ languages)",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
        ),
        TranscriptionModel(
            "ggml-small.bin",
            OPTIMIZED_MODEL,
            "468 MB",
            "Multilingual model (supports 50+ languages, slower, more-accurate)",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
        ),
        TranscriptionModel(
            "ggml-base-hi.bin",
            HINDI_MODEL,
            "140 MB",
            "Hindi/Gujarati optimized model",
            "https://huggingface.co/khidrew/whisper-base-hindi-ggml/resolve/main/ggml-base-hi.bin"
        )
    )

    /**
     * Get the appropriate model based on transcription language
     * @return The model to use
     */
    suspend fun getSelectedModel(): TranscriptionModel {
        val defaultLanguage = preferencesRepository.getDefaultTranscriptionLanguage().first()
        return when (defaultLanguage) {
            "hi" -> models[1]
            "gu" -> models[1]
            else -> models[0]
        }
    }

    fun getDefaultTranscriptionModel() = models[0]


}