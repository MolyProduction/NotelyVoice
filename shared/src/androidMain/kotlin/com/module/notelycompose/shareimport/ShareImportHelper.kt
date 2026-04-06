package com.module.notelycompose.shareimport

import android.content.Context
import android.net.Uri
import android.os.Build
import audio.converter.AudioConverter
import audio.utils.savePickedAudioToAppStorage
import audio.utils.savePickedVideoToAppStorage
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UnsupportedAudioFormatException : Exception(
    "Dieses Audioformat wird auf diesem Gerät nicht unterstützt. Android 10 oder höher erforderlich."
)

/** Gibt true zurück, wenn der MIME-Typ einen Opus-Decoder benötigt (erst API 29+). */
internal fun mimeTypeRequiresModernApi(mimeType: String?): Boolean =
    mimeType == "audio/opus" || mimeType == "audio/ogg"
