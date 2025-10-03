package com.module.notelycompose.export.domain

import android.content.Context
import com.module.notelycompose.FolderPickerHandler
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TEXT_BLANK_DEFAULT = "blank"

class ExportSelectionInteractorImpl(
    private val context: Context,
    private val folderPickerHandler: FolderPickerHandler
): ExportSelectionInteractor {

    override fun exportAllSelection(
        audioPath: List<String>,
        texts: List<String>,
        titles: List<String>,
        onResult: (Result<String>) -> Unit
    ) {

    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun exportTextSelectionOnly(
        texts: List<String>,
        titles: List<String>,
        onResult: (Result<String>) -> Unit
    ) {
        folderPickerHandler.pickFolder { folderUri ->
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                val result = performTextExport(folderUri, texts, titles)
                withContext(Dispatchers.Main) {
                    onResult(result)
                }
            }
        }
    }

    private suspend fun performTextExport(
        folderUri: Uri,
        texts: List<String>,
        titles: List<String>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val folder = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext Result.failure(Exception("Invalid folder URI"))

            val timestamp = SimpleDateFormat("yyyy-MM-ddHH-mm-ss", Locale.getDefault()).format(Date())
            val exportFolder = folder.createDirectory("TextExport_$timestamp")
                ?: return@withContext Result.failure(Exception("Failed to create export folder"))

            texts.forEachIndexed { index, text ->
                val timestampText = SimpleDateFormat("yyyy-MM-ddHH-mm-ss", Locale.getDefault()).format(Date())
                val textTitle = titles[index].takeIf { it.isNotBlank() } ?: TEXT_BLANK_DEFAULT
                val textFile = exportFolder.createFile("text/plain", "${timestampText}-${textTitle}.txt")
                    ?: return@withContext Result.failure(Exception("Failed to create text file $index"))

                context.contentResolver.openOutputStream(textFile.uri)?.use { outputStream ->
                    outputStream.write(text.toByteArray())
                }
            }

            Result.success("Successfully exported ${texts.size} text files")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
