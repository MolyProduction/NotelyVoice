package com.module.notelycompose.export.domain

interface ExportSelectionInteractor {
    fun exportAllSelection(
        audioPath: List<String>,
        texts: List<String>,
        titles: List<String>,
        onResult: (Result<String>) -> Unit
    )
    fun exportTextSelectionOnly(
        texts: List<String>,
        titles: List<String>,
        onResult: (Result<String>) -> Unit
    )
}
