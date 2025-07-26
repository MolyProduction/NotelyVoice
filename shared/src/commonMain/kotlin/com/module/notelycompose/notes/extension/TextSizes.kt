package com.module.notelycompose.notes.extension

const val TEXT_SIZE_TITLE = 24f
const val TEXT_SIZE_HEADING = 20f
const val TEXT_SIZE_SUBHEADING = 16f
const val TEXT_SIZE_BODY = 14f
const val TEXT_NO_SELECTION = 0f

fun Float.intBodyFontSizes(): Int {
    val size = this
    return when {
        size >= 32.0F -> 32
        size >= 28.0F -> 28
        size >= 26.0F -> 26
        size >= 24.0F -> 24
        size >= 22.0F -> 22
        size >= 20.0F -> 20
        size >= 18.0F -> 18
        size >= 16.0F -> 16
        size >= 14.0F -> 14
        else -> 12
    }
}
