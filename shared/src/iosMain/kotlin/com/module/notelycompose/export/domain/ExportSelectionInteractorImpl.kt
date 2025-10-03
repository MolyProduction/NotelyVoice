package com.module.notelycompose.export.domain

class ExportSelectionInteractorImpl(

): ExportSelectionInteractor {

    override fun exportAllSelection(
        audioPath: List<String>,
        texts: List<String>,
        titles: List<String>,
        onResult: (Result<String>) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun exportTextSelectionOnly(
        texts: List<String>,
        titles: List<String>,
        onResult: (Result<String>) -> Unit
    ) {
        TODO("Not yet implemented")
    }
}
