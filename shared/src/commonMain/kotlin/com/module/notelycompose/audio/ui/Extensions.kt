package com.module.notelycompose.audio.ui

fun Int.formatTimeToHHMMSS(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        else -> "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

fun String.keepFirstCharCaseExt(): String =
    if (isEmpty()) "" else first() + substring(1).lowercase()
