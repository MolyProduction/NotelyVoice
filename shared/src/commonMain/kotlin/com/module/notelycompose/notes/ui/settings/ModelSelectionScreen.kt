package com.module.notelycompose.notes.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.module.notelycompose.modelDownloader.BUNDLED_GERMAN_MODEL_FILENAME
import com.module.notelycompose.modelDownloader.GERMAN_MODEL
import com.module.notelycompose.modelDownloader.NO_MODEL_SELECTION
import com.module.notelycompose.modelDownloader.OPTIMIZED_MODEL_SELECTION
import com.module.notelycompose.modelDownloader.STANDARD_MODEL_SELECTION
import com.module.notelycompose.notes.ui.detail.AndroidNoteTopBar
import com.module.notelycompose.notes.ui.detail.IOSNoteTopBar
import com.module.notelycompose.notes.ui.theme.LocalCustomColors
import com.module.notelycompose.onboarding.data.PreferencesRepository
import com.module.notelycompose.platform.getPlatform
import com.module.notelycompose.platform.Transcriber
import com.module.notelycompose.resources.Res
import com.module.notelycompose.resources.delete_model
import com.module.notelycompose.resources.manage_models
import com.module.notelycompose.resources.manage_models_desc
import com.module.notelycompose.resources.model_cannot_delete
import com.module.notelycompose.resources.model_info_size
import com.module.notelycompose.resources.speech_mode_german_accurate_subtitle
import com.module.notelycompose.resources.speech_mode_german_accurate_title
import com.module.notelycompose.resources.speech_mode_german_quick_subtitle
import com.module.notelycompose.resources.speech_mode_german_quick_title
import com.module.notelycompose.resources.speech_mode_multilingual_extended_option
import com.module.notelycompose.resources.speech_mode_multilingual_standard_option
import com.module.notelycompose.resources.speech_mode_multilingual_subtitle
import com.module.notelycompose.resources.speech_mode_multilingual_title
import com.module.notelycompose.resources.speech_mode_status_download
import com.module.notelycompose.resources.speech_mode_status_ready
import com.module.notelycompose.resources.speech_recognition
import com.module.notelycompose.resources.speech_recognition_desc
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val MODE_GERMAN_QUICK = 0
private const val MODE_GERMAN_ACCURATE = 1
private const val MODE_MULTILINGUAL_STANDARD = 2
private const val MODE_MULTILINGUAL_EXTENDED = 3

data class ModelOption(
    val title: String,
    val description: String,
    val size: String = ""
)

@Composable
fun ModelSelectionScreen(
    navigateBack: () -> Unit,
    navigateToModelExplanation: () -> Unit,
    preferencesRepository: PreferencesRepository = koinInject(),
    transcriber: Transcriber = koinInject()
) {
    val language by preferencesRepository.getDefaultTranscriptionLanguage().collectAsState("de")
    val modelSelection by preferencesRepository.getModelSelection().collectAsState(NO_MODEL_SELECTION)
    val coroutineScope = rememberCoroutineScope()

    val currentMode = when {
        language == GERMAN_MODEL && modelSelection == OPTIMIZED_MODEL_SELECTION -> MODE_GERMAN_ACCURATE
        language == GERMAN_MODEL -> MODE_GERMAN_QUICK
        modelSelection == OPTIMIZED_MODEL_SELECTION -> MODE_MULTILINGUAL_EXTENDED
        else -> MODE_MULTILINGUAL_STANDARD
    }

    var turboReady by remember { mutableStateOf(false) }
    var multiStandardReady by remember { mutableStateOf(false) }
    var multiExtendedReady by remember { mutableStateOf(false) }
    var turboSizeMB by remember { mutableStateOf(0L) }
    var multiStandardSizeMB by remember { mutableStateOf(0L) }
    var multiExtendedSizeMB by remember { mutableStateOf(0L) }
    var showManageModels by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        turboReady = transcriber.doesModelExists("ggml-large-v3-turbo-german.bin")
        multiStandardReady = transcriber.doesModelExists("ggml-base-en.bin")
        multiExtendedReady = transcriber.doesModelExists("ggml-small.bin")
        turboSizeMB = transcriber.getModelFileSizeBytes("ggml-large-v3-turbo-german.bin") / 1024 / 1024
        multiStandardSizeMB = transcriber.getModelFileSizeBytes("ggml-base-en.bin") / 1024 / 1024
        multiExtendedSizeMB = transcriber.getModelFileSizeBytes("ggml-small.bin") / 1024 / 1024
    }

    fun selectMode(mode: Int) {
        coroutineScope.launch {
            when (mode) {
                MODE_GERMAN_QUICK -> {
                    preferencesRepository.setDefaultTranscriptionLanguage(GERMAN_MODEL)
                    preferencesRepository.setModelSelection(STANDARD_MODEL_SELECTION)
                }
                MODE_GERMAN_ACCURATE -> {
                    preferencesRepository.setDefaultTranscriptionLanguage(GERMAN_MODEL)
                    preferencesRepository.setModelSelection(OPTIMIZED_MODEL_SELECTION)
                }
                MODE_MULTILINGUAL_STANDARD -> {
                    preferencesRepository.setDefaultTranscriptionLanguage("en")
                    preferencesRepository.setModelSelection(STANDARD_MODEL_SELECTION)
                }
                MODE_MULTILINGUAL_EXTENDED -> {
                    preferencesRepository.setDefaultTranscriptionLanguage("en")
                    preferencesRepository.setModelSelection(OPTIMIZED_MODEL_SELECTION)
                }
            }
        }
        navigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalCustomColors.current.bodyBackgroundColor)
    ) {
        if (getPlatform().isAndroid) {
            AndroidNoteTopBar(title = "", onNavigateBack = navigateBack)
        } else {
            IOSNoteTopBar(onNavigateBack = navigateBack)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(Res.string.speech_recognition),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = LocalCustomColors.current.bodyContentColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(Res.string.speech_recognition_desc),
                fontSize = 15.sp,
                color = LocalCustomColors.current.bodyContentColor,
                modifier = Modifier.padding(bottom = 28.dp)
            )

            // ── German models section ──────────────────────────────────────
            SectionLabel(text = "🇩🇪 Deutsch")

            SpeechModeCard(
                title = stringResource(Res.string.speech_mode_german_quick_title),
                subtitle = stringResource(Res.string.speech_mode_german_quick_subtitle),
                statusText = stringResource(Res.string.speech_mode_status_ready),
                statusReady = true,
                isSelected = currentMode == MODE_GERMAN_QUICK,
                accentColor = Color(0xFF1565C0),
                onClick = { selectMode(MODE_GERMAN_QUICK) },
                modifier = Modifier.padding(bottom = 12.dp)
            )

            SpeechModeCard(
                title = stringResource(Res.string.speech_mode_german_accurate_title),
                subtitle = stringResource(Res.string.speech_mode_german_accurate_subtitle),
                statusText = if (turboReady) stringResource(Res.string.speech_mode_status_ready)
                             else stringResource(Res.string.speech_mode_status_download),
                statusReady = turboReady,
                isSelected = currentMode == MODE_GERMAN_ACCURATE,
                accentColor = Color(0xFF1565C0),
                onClick = { selectMode(MODE_GERMAN_ACCURATE) },
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // ── Multilingual section ───────────────────────────────────────
            SectionLabel(text = "🌐 Mehrsprachig")

            SpeechModeCard(
                title = stringResource(Res.string.speech_mode_multilingual_title),
                subtitle = stringResource(Res.string.speech_mode_multilingual_subtitle),
                statusText = if (multiStandardReady || multiExtendedReady)
                                 stringResource(Res.string.speech_mode_status_ready)
                             else stringResource(Res.string.speech_mode_status_download),
                statusReady = multiStandardReady || multiExtendedReady,
                isSelected = currentMode == MODE_MULTILINGUAL_STANDARD || currentMode == MODE_MULTILINGUAL_EXTENDED,
                accentColor = Color(0xFF546E7A),
                onClick = null, // expands sub-options, not a direct selection
                expandedContent = {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        SubOptionRow(
                            label = stringResource(Res.string.speech_mode_multilingual_standard_option),
                            isSelected = currentMode == MODE_MULTILINGUAL_STANDARD,
                            isReady = multiStandardReady,
                            onClick = { selectMode(MODE_MULTILINGUAL_STANDARD) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SubOptionRow(
                            label = stringResource(Res.string.speech_mode_multilingual_extended_option),
                            isSelected = currentMode == MODE_MULTILINGUAL_EXTENDED,
                            isReady = multiExtendedReady,
                            onClick = { selectMode(MODE_MULTILINGUAL_EXTENDED) }
                        )
                    }
                },
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // ── Model management ──────────────────────────────────────────
            val anyDeletable = turboReady || multiStandardReady || multiExtendedReady
            if (anyDeletable) {
                ManageModelsSection(
                    showManageModels = showManageModels,
                    onToggle = { showManageModels = !showManageModels },
                    turboReady = turboReady,
                    turboSizeMB = turboSizeMB,
                    multiStandardReady = multiStandardReady,
                    multiStandardSizeMB = multiStandardSizeMB,
                    multiExtendedReady = multiExtendedReady,
                    multiExtendedSizeMB = multiExtendedSizeMB,
                    onDeleteTurbo = {
                        coroutineScope.launch {
                            transcriber.deleteModel("ggml-large-v3-turbo-german.bin")
                            turboReady = false
                            turboSizeMB = 0L
                            if (currentMode == MODE_GERMAN_ACCURATE) {
                                preferencesRepository.setModelSelection(STANDARD_MODEL_SELECTION)
                            }
                        }
                    },
                    onDeleteMultiStandard = {
                        coroutineScope.launch {
                            transcriber.deleteModel("ggml-base-en.bin")
                            multiStandardReady = false
                            multiStandardSizeMB = 0L
                            if (currentMode == MODE_MULTILINGUAL_STANDARD) {
                                preferencesRepository.setDefaultTranscriptionLanguage(GERMAN_MODEL)
                                preferencesRepository.setModelSelection(STANDARD_MODEL_SELECTION)
                            }
                        }
                    },
                    onDeleteMultiExtended = {
                        coroutineScope.launch {
                            transcriber.deleteModel("ggml-small.bin")
                            multiExtendedReady = false
                            multiExtendedSizeMB = 0L
                            if (currentMode == MODE_MULTILINGUAL_EXTENDED) {
                                preferencesRepository.setDefaultTranscriptionLanguage(GERMAN_MODEL)
                                preferencesRepository.setModelSelection(STANDARD_MODEL_SELECTION)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = LocalCustomColors.current.bodyContentColor,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun SpeechModeCard(
    title: String,
    subtitle: String,
    statusText: String,
    statusReady: Boolean,
    isSelected: Boolean,
    accentColor: Color,
    onClick: (() -> Unit)?,
    expandedContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isExpandable = onClick == null && expandedContent != null
    val isExpanded = isSelected || isExpandable

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
                } else if (isExpandable && expandedContent != null) {
                    Modifier.clickable { /* expand handled by parent state */ }
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = LocalCustomColors.current.modelSelectionBgColor
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, accentColor)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalCustomColors.current.bodyContentColor,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = LocalCustomColors.current.modelSelectionDescColor,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(text = statusText, isReady = statusReady)
                    if (onClick != null) {
                        RadioButton(
                            selected = isSelected,
                            onClick = onClick,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accentColor,
                                unselectedColor = Color.Gray
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (expandedContent != null) {
                expandedContent()
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, isReady: Boolean) {
    val bgColor = if (isReady) Color(0xFF1B5E20).copy(alpha = 0.1f) else Color(0xFF1565C0).copy(alpha = 0.1f)
    val textColor = if (isReady) Color(0xFF2E7D32) else Color(0xFF1565C0)
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, fontSize = 12.sp, color = textColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SubOptionRow(
    label: String,
    isSelected: Boolean,
    isReady: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFF546E7A),
                unselectedColor = Color.Gray
            )
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = LocalCustomColors.current.bodyContentColor,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        if (!isReady) {
            StatusBadge(
                text = "⬇",
                isReady = false
            )
        }
    }
}

@Composable
private fun ManageModelsSection(
    showManageModels: Boolean,
    onToggle: () -> Unit,
    turboReady: Boolean,
    turboSizeMB: Long,
    multiStandardReady: Boolean,
    multiStandardSizeMB: Long,
    multiExtendedReady: Boolean,
    multiExtendedSizeMB: Long,
    onDeleteTurbo: () -> Unit,
    onDeleteMultiStandard: () -> Unit,
    onDeleteMultiExtended: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.manage_models),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = LocalCustomColors.current.bodyContentColor
                )
                Text(
                    text = stringResource(Res.string.manage_models_desc),
                    fontSize = 13.sp,
                    color = LocalCustomColors.current.modelSelectionDescColor
                )
            }
            Text(
                text = if (showManageModels) "▲" else "▼",
                color = LocalCustomColors.current.modelSelectionDescColor
            )
        }

        AnimatedVisibility(
            visible = showManageModels,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bundled model — cannot delete
                ModelManagementRow(
                    name = "🇩🇪 Deutsch – Schnell",
                    sizeMB = 75L,
                    isDeletable = false,
                    onDelete = {}
                )
                if (turboReady) {
                    ModelManagementRow(
                        name = "🇩🇪 Deutsch – Genau",
                        sizeMB = turboSizeMB,
                        isDeletable = true,
                        onDelete = onDeleteTurbo
                    )
                }
                if (multiStandardReady) {
                    ModelManagementRow(
                        name = "🌐 Mehrsprachig Standard",
                        sizeMB = multiStandardSizeMB,
                        isDeletable = true,
                        onDelete = onDeleteMultiStandard
                    )
                }
                if (multiExtendedReady) {
                    ModelManagementRow(
                        name = "🌐 Mehrsprachig Erweitert",
                        sizeMB = multiExtendedSizeMB,
                        isDeletable = true,
                        onDelete = onDeleteMultiExtended
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelManagementRow(
    name: String,
    sizeMB: Long,
    isDeletable: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, fontSize = 14.sp, color = LocalCustomColors.current.bodyContentColor)
            if (sizeMB > 0) {
                Text(
                    text = stringResource(Res.string.model_info_size, sizeMB.toString()),
                    fontSize = 12.sp,
                    color = LocalCustomColors.current.modelSelectionDescColor
                )
            }
        }
        if (isDeletable) {
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(Res.string.delete_model),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }
        } else {
            Text(
                text = stringResource(Res.string.model_cannot_delete),
                fontSize = 12.sp,
                color = LocalCustomColors.current.modelSelectionDescColor,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
