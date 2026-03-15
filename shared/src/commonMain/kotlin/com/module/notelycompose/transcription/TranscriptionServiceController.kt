package com.module.notelycompose.transcription

interface TranscriptionServiceController {
    fun startTranscriptionService()
    fun stopTranscriptionService()
    fun notifyTranscriptionPhaseLoading()
    fun notifyTranscriptionPhaseTranscribing()
    fun notifyTranscriptionComplete()
}
