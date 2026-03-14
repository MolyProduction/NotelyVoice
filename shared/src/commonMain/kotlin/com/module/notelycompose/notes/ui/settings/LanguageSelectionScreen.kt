package com.module.notelycompose.notes.ui.settings

import androidx.compose.foundation.background
import com.module.notelycompose.notes.ui.common.EmptyStateView
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.module.notelycompose.notes.ui.detail.AndroidNoteTopBar
import com.module.notelycompose.notes.ui.detail.IOSNoteTopBar
import com.module.notelycompose.notes.ui.theme.LocalCustomColors
import com.module.notelycompose.onboarding.data.PreferencesRepository
import com.module.notelycompose.platform.getPlatform
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import de.molyecho.notlyvoice.resources.Res
import de.molyecho.notlyvoice.resources.language_selection_no_languages_found
import de.molyecho.notlyvoice.resources.language_selection_supported_languages
import de.molyecho.notlyvoice.resources.language_selection_search
import de.molyecho.notlyvoice.resources.language_selection_select_language
import org.jetbrains.compose.resources.stringResource

val languageCodeMap = mapOf(
    "en" to "Englisch",
    "ar" to "Arabisch",
    "ca" to "Katalanisch",
    "zh" to "Chinesisch",
    "cs" to "Tschechisch",
    "nl" to "Niederländisch",
    "fi" to "Finnisch",
    "fr" to "Französisch",
    "gl" to "Galicisch",
    "de" to "Deutsch",
    "id" to "Indonesisch",
    "it" to "Italienisch",
    "ja" to "Japanisch",
    "ko" to "Koreanisch",
    "ms" to "Malaiisch",
    "no" to "Norwegisch",
    "sk" to "Slowakisch",
    "fa" to "Persisch (Farsi)",
    "pl" to "Polnisch",
    "pt" to "Portugiesisch",
    "ru" to "Russisch",
    "es" to "Spanisch",
    "sv" to "Schwedisch",
    "tl" to "Tagalog",
    "th" to "Thailändisch",
    "tr" to "Türkisch",
    "uk" to "Ukrainisch",
    "ur" to "Urdu",
    "vi" to "Vietnamesisch",
    "auto" to "Automatisch erkennen (weniger genau)"
)

@Composable
fun LanguageSelectionScreen(
    navigateBack: () -> Unit,
    preferencesRepository: PreferencesRepository = koinInject()
) {
    // TODO: move this implementation to a ViewModel
    val previousSelectedLanguage by preferencesRepository.getDefaultTranscriptionLanguage()
        .collectAsState("de")
    val coroutineScope = rememberCoroutineScope()
    var searchText by remember { mutableStateOf("") }
    val filteredLanguages by derivedStateOf {
        languageCodeMap.filter { (language, code) ->
            language.contains(searchText, ignoreCase = true) ||
                    code.contains(searchText, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalCustomColors.current.bodyBackgroundColor)
    ) {
        if (getPlatform().isAndroid) {
            AndroidNoteTopBar(
                title = "",
                onNavigateBack = navigateBack
            )
        } else {
            IOSNoteTopBar(
                onNavigateBack = navigateBack
            )
        }
        // content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalCustomColors.current.bodyBackgroundColor)
                .padding(16.dp)
        ) {
            // Title
            Text(
                text = stringResource(Res.string.language_selection_select_language),
                color = LocalCustomColors.current.bodyContentColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                textAlign = TextAlign.Center
            )

            // Search Bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                placeholder = {
                    Text(
                        text = stringResource(Res.string.language_selection_search),
                        color = LocalCustomColors.current.languageSearchBorderColor
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Suchen",
                        tint = LocalCustomColors.current.languageSearchBorderColor
                    )
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(
                            onClick = { searchText = "" },
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    LocalCustomColors.current.languageSearchCancelButtonColor.copy(alpha = 0.3f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Löschen",
                                tint = LocalCustomColors.current.languageSearchCancelIconTintColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LocalCustomColors.current.languageSearchUnfocusedColor,
                    unfocusedTextColor = LocalCustomColors.current.languageSearchUnfocusedColor,
                    focusedBorderColor = LocalCustomColors.current.languageSearchBorderColor,
                    unfocusedBorderColor = LocalCustomColors.current.languageSearchBorderColor,
                    cursorColor = LocalCustomColors.current.languageSearchUnfocusedColor
                ),
                shape = RoundedCornerShape(48.dp),
                singleLine = true
            )

            // Language List
            Text(
                text = stringResource(Res.string.language_selection_supported_languages),
                color = LocalCustomColors.current.languageListHeaderColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(LocalCustomColors.current.languageListBackgroundColor)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp))
            ) {
                if (filteredLanguages.isEmpty()) {
                    EmptyStateView(
                        text = stringResource(Res.string.language_selection_no_languages_found),
                        imageSize = 100.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        itemsIndexed(filteredLanguages.entries.toList()) { index, languageEntry ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            preferencesRepository.setDefaultTranscriptionLanguage(languageEntry.key)
                                        }
                                        navigateBack()
                                    },
                                color = LocalCustomColors.current.languageListBackgroundColor,
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = languageEntry.value,
                                            color = LocalCustomColors.current.languageListTextColor,
                                            fontSize = 16.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if(languageEntry.key == previousSelectedLanguage) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Ausgewählt",
                                                tint = LocalCustomColors.current.languageListTextColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    if (index < filteredLanguages.size - 1) {
                                        Divider(
                                            thickness = 0.5.dp,
                                            color = LocalCustomColors.current.languageListDividerColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
