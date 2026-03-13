# Model System Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the bundled tiny German model and the small multilingual model; make the q5_0 German model the new on-demand "Schnell" default; restore the cstr full model as "Genau"; update all related UI and strings.

**Architecture:** All changes are in the shared KMP module. Model data lives in `ModelSelection.kt`; startup bundled-model logic in `NoteApp.kt`; DataStore keys in `PreferencesRepository.kt`; model-selection UI in `ModelSelectionScreen.kt` and `SettingsScreen.kt`; copy in string resource XMLs. No new files are created. The asset binary is deleted last.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose / Compose Multiplatform, Android DataStore, Gradle

**Spec:** `docs/superpowers/specs/2026-03-13-model-system-redesign.md`

---

## Chunk 1: Core model data layer

### Task 1: Rewrite `ModelSelection.kt`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/modelDownloader/ModelSelection.kt`

- [ ] **Step 1.1 – Replace the file contents**

Replace the entire file with the following. Key changes:
- `BUNDLED_GERMAN_MODEL_FILENAME` constant removed.
- `ggml-base-en.bin` (142 MB multilingual standard) removed from `models[]`.
- `ggml-large-v3-turbo-german.bin` (1.62 GB cstr "Genau") added as new entry.
- `models[]` final order: `[0:ggml-small, 1:ggml-base-hi, 2:q5_0, 3:cstr]`.
- `STANDARD_MODEL_SELECTION (0)` → q5_0 (index 2); `OPTIMIZED_MODEL_SELECTION (1)` → cstr (index 3).
- `MULTILINGUAL_STANDARD_SELECTION (2)` and `MULTILINGUAL_EXTENDED_SELECTION (3)` both → ggml-small (migration-safe).
- `getDefaultTranscriptionModel()` → q5_0.

```kotlin
package com.module.notelycompose.modelDownloader

import com.module.notelycompose.onboarding.data.PreferencesRepository
import kotlinx.coroutines.flow.first

const val NO_MODEL_SELECTION = -1
const val STANDARD_MODEL_SELECTION = 0       // German Quick (q5_0, downloadable)
const val OPTIMIZED_MODEL_SELECTION = 1      // German Accurate (cstr, downloadable)
const val MULTILINGUAL_STANDARD_SELECTION = 2 // Legacy — kept for DataStore migration, maps to ggml-small
const val MULTILINGUAL_EXTENDED_SELECTION = 3 // Multilingual (ggml-small)
const val MULTILINGUAL_MODEL = "en"
const val HINDI_MODEL = "hi"
const val FARSI = "fa"
const val GUJARATI = "gu"
const val GERMAN_MODEL = "de"

data class TranscriptionModel(val name: String, val modelType: String, val size: String, val description: String, val url: String?) {
    fun getModelDownloadSize(): String = size
    fun getModelDownloadType(): String = modelType
}

class ModelSelection(private val preferencesRepository: PreferencesRepository) {

    /**
     * Available Whisper models.
     *
     * Index layout (stable — constants below depend on these positions):
     *   0  ggml-small.bin              multilingual extended (465 MB)
     *   1  ggml-base-hi.bin            Hindi/Gujarati (140 MB)
     *   2  ggml-large-v3-turbo-german-q5_0.bin  German "Schnell" (574 MB)
     *   3  ggml-large-v3-turbo-german.bin        German "Genau"  (1.62 GB)
     */
    private val models = listOf(
        TranscriptionModel(
            "ggml-small.bin",
            MULTILINGUAL_MODEL,
            "465 MB",
            "Multilingual model (supports 50+ languages, slower, more-accurate)",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
        ),
        TranscriptionModel(
            "ggml-base-hi.bin",
            HINDI_MODEL,
            "140 MB",
            "Hindi/Gujarati optimized model",
            "https://huggingface.co/khidrew/whisper-base-hindi-ggml/resolve/main/ggml-base-hi.bin"
        ),
        TranscriptionModel(
            "ggml-large-v3-turbo-german-q5_0.bin",
            GERMAN_MODEL,
            "574 MB",
            "German large-v3-turbo model (high accuracy)",
            "https://huggingface.co/F1sk/whisper-large-v3-turbo-german-ggml-q5_0/resolve/main/ggml-large-v3-turbo-german-q5_0.bin"
        ),
        TranscriptionModel(
            "ggml-large-v3-turbo-german.bin",
            GERMAN_MODEL,
            "1.62 GB",
            "German large-v3-turbo model (highest accuracy)",
            "https://huggingface.co/cstr/whisper-large-v3-turbo-german-ggml/resolve/main/ggml-model.bin"
        )
    )

    /**
     * Returns the model matching the current user preferences.
     *
     * Model selection and transcription language are intentionally decoupled:
     * multilingual models support German transcription when Whisper receives language="de".
     */
    suspend fun getSelectedModel(): TranscriptionModel {
        val defaultLanguage = preferencesRepository.getDefaultTranscriptionLanguage().first()
        val modelSelectionValue = preferencesRepository.getModelSelection().first()

        return when (defaultLanguage) {
            HINDI_MODEL, GUJARATI -> models[1]
            FARSI -> models.first { it.name == "ggml-small.bin" }
            else -> when (modelSelectionValue) {
                OPTIMIZED_MODEL_SELECTION                        -> models[3] // German cstr "Genau"
                MULTILINGUAL_STANDARD_SELECTION,
                MULTILINGUAL_EXTENDED_SELECTION                  -> models[0] // ggml-small multilingual
                else                                             -> models[2] // German q5_0 "Schnell" (default)
            }
        }
    }

    fun getDefaultTranscriptionModel() = models[2] // German q5_0 "Schnell"
}
```

- [ ] **Step 1.2 – Verify compilation**

```bash
cd "C:\AI-Projekte\MolyEcho"
./gradlew :shared:compileDebugKotlinAndroid --quiet 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL (no errors in `ModelSelection.kt`)

- [ ] **Step 1.3 – Commit**

```bash
git add shared/src/commonMain/kotlin/com/module/notelycompose/modelDownloader/ModelSelection.kt
git commit -m "feat: rewrite ModelSelection — remove bundled/base-en models, add cstr Genau"
```

---

### Task 2: Strip bundled-model startup logic from `NoteApp.kt`

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/module/notelycompose/NoteApp.kt`

- [ ] **Step 2.1 – Replace file contents**

```kotlin
package com.module.notelycompose

import android.app.Application
import com.module.notelycompose.di.initKoinApplication
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class NoteApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())
        initKoinApplication {
            androidContext(this@NoteApp)
            androidLogger()
        }
    }
}
```

- [ ] **Step 2.2 – Verify compilation**

```bash
./gradlew :shared:compileDebugKotlinAndroid --quiet 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2.3 – Commit**

```bash
git add shared/src/androidMain/kotlin/com/module/notelycompose/NoteApp.kt
git commit -m "feat: remove bundled model startup extraction from NoteApp"
```

---

### Task 3: Remove bundled-model DataStore keys from `PreferencesRepository.kt`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/onboarding/data/PreferencesRepository.kt`

- [ ] **Step 3.1 – Remove the three bundled-model members**

Remove these lines from the `companion object`:
```kotlin
private const val BUNDLED_MODEL_VERSION = 1
private val KEY_BUNDLED_MODEL_VERSION = intPreferencesKey("bundled_model_version")
```

Remove these two methods from the class body:
```kotlin
fun isBundledModelExtracted(): Flow<Boolean> = dataStore.data.map { prefs ->
    (prefs[KEY_BUNDLED_MODEL_VERSION] ?: 0) >= BUNDLED_MODEL_VERSION
}

suspend fun setBundledModelExtracted(extracted: Boolean) {
    dataStore.edit { prefs ->
        prefs[KEY_BUNDLED_MODEL_VERSION] = if (extracted) BUNDLED_MODEL_VERSION else 0
    }
}
```

- [ ] **Step 3.2 – Verify compilation**

```bash
./gradlew :shared:compileDebugKotlinAndroid --quiet 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3.3 – Commit**

```bash
git add shared/src/commonMain/kotlin/com/module/notelycompose/onboarding/data/PreferencesRepository.kt
git commit -m "feat: remove bundled model DataStore tracking keys"
```

---

## Chunk 2: Model selection UI

### Task 4: Rewrite `ModelSelectionScreen.kt`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/notes/ui/settings/ModelSelectionScreen.kt`

This is the largest change. Follow the diff description precisely.

- [ ] **Step 4.1 – Remove unused imports (keep `MULTILINGUAL_STANDARD_SELECTION`)**

Remove these import lines:
```kotlin
import com.module.notelycompose.modelDownloader.BUNDLED_GERMAN_MODEL_FILENAME
import de.molyecho.notlyvoice.resources.model_label_multilingual_standard
import de.molyecho.notlyvoice.resources.speech_mode_multilingual_standard_option
```

> **Keep** `import com.module.notelycompose.modelDownloader.MULTILINGUAL_STANDARD_SELECTION` — it is still referenced in the `currentMode` `when` block in `SettingsScreen.kt` and optionally here.

- [ ] **Step 4.2 – Remove `MODE_MULTILINGUAL_STANDARD` private constant**

Remove this line near the top of the file:
```kotlin
private const val MODE_MULTILINGUAL_STANDARD = 2
```

- [ ] **Step 4.3 – Update `currentMode` derivation**

Find:
```kotlin
    val currentMode = when (modelSelection) {
        OPTIMIZED_MODEL_SELECTION       -> MODE_GERMAN_ACCURATE
        MULTILINGUAL_STANDARD_SELECTION -> MODE_MULTILINGUAL_STANDARD
        MULTILINGUAL_EXTENDED_SELECTION -> MODE_MULTILINGUAL_EXTENDED
        else                            -> MODE_GERMAN_QUICK
    }
```

Replace with:
```kotlin
    val currentMode = when (modelSelection) {
        OPTIMIZED_MODEL_SELECTION       -> MODE_GERMAN_ACCURATE
        MULTILINGUAL_EXTENDED_SELECTION -> MODE_MULTILINGUAL_EXTENDED
        else                            -> MODE_GERMAN_QUICK
    }
```

> The `MULTILINGUAL_STANDARD_SELECTION` branch is dropped — existing users who stored value `2` fall through to `MODE_GERMAN_QUICK`, which triggers the download dialog on next transcription. Acceptable per the spec.

- [ ] **Step 4.4 – Remove `multiStandardReady` and `multiStandardSizeMB` state variables**

Remove these two `var` declarations:
```kotlin
    var multiStandardReady by remember { mutableStateOf(false) }
    var multiStandardSizeMB by remember { mutableStateOf(0L) }
```

- [ ] **Step 4.5 – Remove the `multiStandard` checks from `LaunchedEffect`**

Find inside the `LaunchedEffect(Unit)` block:
```kotlin
            multiStandardReady = transcriber.doesModelExists("ggml-base-en.bin")
            multiStandardSizeMB = transcriber.getModelFileSizeBytes("ggml-base-en.bin") / 1024 / 1024
```
Delete both lines.

- [ ] **Step 4.6 – Add `schnellReady`/`schnellSizeMB` and rename `turboReady`/`turboSizeMB`**

Find:
```kotlin
    var turboReady by remember { mutableStateOf(false) }
    var multiStandardReady by remember { mutableStateOf(false) }
    var multiExtendedReady by remember { mutableStateOf(false) }
    var turboSizeMB by remember { mutableStateOf(0L) }
    var multiStandardSizeMB by remember { mutableStateOf(0L) }
    var multiExtendedSizeMB by remember { mutableStateOf(0L) }
```

After removing `multiStandard*` in the previous steps, what remains is:
```kotlin
    var turboReady by remember { mutableStateOf(false) }
    var multiExtendedReady by remember { mutableStateOf(false) }
    var turboSizeMB by remember { mutableStateOf(0L) }
    var multiExtendedSizeMB by remember { mutableStateOf(0L) }
```

Replace with:
```kotlin
    var schnellReady by remember { mutableStateOf(false) }
    var genauReady by remember { mutableStateOf(false) }
    var multiExtendedReady by remember { mutableStateOf(false) }
    var schnellSizeMB by remember { mutableStateOf(0L) }
    var genauSizeMB by remember { mutableStateOf(0L) }
    var multiExtendedSizeMB by remember { mutableStateOf(0L) }
```

- [ ] **Step 4.7 – Update `LaunchedEffect` model existence checks**

Find inside `LaunchedEffect(Unit)`:
```kotlin
            turboReady = transcriber.doesModelExists("ggml-large-v3-turbo-german-q5_0.bin")
            multiStandardReady = transcriber.doesModelExists("ggml-base-en.bin")
            multiExtendedReady = transcriber.doesModelExists("ggml-small.bin")
            turboSizeMB = transcriber.getModelFileSizeBytes("ggml-large-v3-turbo-german-q5_0.bin") / 1024 / 1024
            multiStandardSizeMB = transcriber.getModelFileSizeBytes("ggml-base-en.bin") / 1024 / 1024
            multiExtendedSizeMB = transcriber.getModelFileSizeBytes("ggml-small.bin") / 1024 / 1024
```

Replace with:
```kotlin
            schnellReady = transcriber.doesModelExists("ggml-large-v3-turbo-german-q5_0.bin")
            genauReady = transcriber.doesModelExists("ggml-large-v3-turbo-german.bin")
            multiExtendedReady = transcriber.doesModelExists("ggml-small.bin")
            schnellSizeMB = transcriber.getModelFileSizeBytes("ggml-large-v3-turbo-german-q5_0.bin") / 1024 / 1024
            genauSizeMB = transcriber.getModelFileSizeBytes("ggml-large-v3-turbo-german.bin") / 1024 / 1024
            multiExtendedSizeMB = transcriber.getModelFileSizeBytes("ggml-small.bin") / 1024 / 1024
```

- [ ] **Step 4.8 – Update `selectMode` function**

Find:
```kotlin
            when (mode) {
                MODE_GERMAN_QUICK       -> preferencesRepository.setModelSelection(STANDARD_MODEL_SELECTION)
                MODE_GERMAN_ACCURATE    -> preferencesRepository.setModelSelection(OPTIMIZED_MODEL_SELECTION)
                MODE_MULTILINGUAL_STANDARD -> preferencesRepository.setModelSelection(MULTILINGUAL_STANDARD_SELECTION)
                MODE_MULTILINGUAL_EXTENDED -> preferencesRepository.setModelSelection(MULTILINGUAL_EXTENDED_SELECTION)
            }
```

Replace with:
```kotlin
            when (mode) {
                MODE_GERMAN_QUICK          -> preferencesRepository.setModelSelection(STANDARD_MODEL_SELECTION)
                MODE_GERMAN_ACCURATE       -> preferencesRepository.setModelSelection(OPTIMIZED_MODEL_SELECTION)
                MODE_MULTILINGUAL_EXTENDED -> preferencesRepository.setModelSelection(MULTILINGUAL_EXTENDED_SELECTION)
            }
```

- [ ] **Step 4.9 – Update German Quick card status**

Find:
```kotlin
            SpeechModeCard(
                title = stringResource(Res.string.speech_mode_german_quick_title),
                subtitle = stringResource(Res.string.speech_mode_german_quick_subtitle),
                statusText = stringResource(Res.string.speech_mode_status_ready),
                statusReady = true,
                isSelected = currentMode == MODE_GERMAN_QUICK,
```

Replace with:
```kotlin
            SpeechModeCard(
                title = stringResource(Res.string.speech_mode_german_quick_title),
                subtitle = stringResource(Res.string.speech_mode_german_quick_subtitle),
                statusText = if (schnellReady) stringResource(Res.string.speech_mode_status_ready)
                             else stringResource(Res.string.speech_mode_status_download),
                statusReady = schnellReady,
                isSelected = currentMode == MODE_GERMAN_QUICK,
```

- [ ] **Step 4.10 – Update German Accurate card**

Find:
```kotlin
                statusText = if (turboReady) stringResource(Res.string.speech_mode_status_ready)
                             else stringResource(Res.string.speech_mode_status_download),
                statusReady = turboReady,
```

Replace with:
```kotlin
                statusText = if (genauReady) stringResource(Res.string.speech_mode_status_ready)
                             else stringResource(Res.string.speech_mode_status_download),
                statusReady = genauReady,
```

- [ ] **Step 4.11 – Replace Multilingual card (remove sub-options, make direct click)**

Find the entire Multilingual `SpeechModeCard(...)` block (from `SpeechModeCard(` through the closing `)` including the `expandedContent` lambda):
```kotlin
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
```

Replace with:
```kotlin
            SpeechModeCard(
                title = stringResource(Res.string.speech_mode_multilingual_title),
                subtitle = stringResource(Res.string.speech_mode_multilingual_subtitle),
                statusText = if (multiExtendedReady) stringResource(Res.string.speech_mode_status_ready)
                             else stringResource(Res.string.speech_mode_status_download),
                statusReady = multiExtendedReady,
                isSelected = currentMode == MODE_MULTILINGUAL_EXTENDED,
                accentColor = Color(0xFF546E7A),
                onClick = { selectMode(MODE_MULTILINGUAL_EXTENDED) },
                modifier = Modifier.padding(bottom = 32.dp)
            )
```

- [ ] **Step 4.12 – Update `anyDeletable` and `ManageModelsSection` call**

Find:
```kotlin
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
                            transcriber.deleteModel("ggml-large-v3-turbo-german-q5_0.bin")
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
                                preferencesRepository.setModelSelection(STANDARD_MODEL_SELECTION)
                            }
                        }
                    }
                )
            }
```

Replace with:
```kotlin
            val anyDeletable = schnellReady || genauReady || multiExtendedReady
            if (anyDeletable) {
                ManageModelsSection(
                    showManageModels = showManageModels,
                    onToggle = { showManageModels = !showManageModels },
                    schnellReady = schnellReady,
                    schnellSizeMB = schnellSizeMB,
                    genauReady = genauReady,
                    genauSizeMB = genauSizeMB,
                    multiExtendedReady = multiExtendedReady,
                    multiExtendedSizeMB = multiExtendedSizeMB,
                    onDeleteSchnell = {
                        coroutineScope.launch {
                            transcriber.deleteModel("ggml-large-v3-turbo-german-q5_0.bin")
                            schnellReady = false
                            schnellSizeMB = 0L
                            if (currentMode == MODE_GERMAN_QUICK) {
                                preferencesRepository.setModelSelection(STANDARD_MODEL_SELECTION)
                            }
                        }
                    },
                    onDeleteGenau = {
                        coroutineScope.launch {
                            transcriber.deleteModel("ggml-large-v3-turbo-german.bin")
                            genauReady = false
                            genauSizeMB = 0L
                            if (currentMode == MODE_GERMAN_ACCURATE) {
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
                                preferencesRepository.setModelSelection(STANDARD_MODEL_SELECTION)
                            }
                        }
                    }
                )
            }
```

- [ ] **Step 4.13 – Update `ManageModelsSection` function signature and body**

Find the `ManageModelsSection` private function signature:
```kotlin
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
```

Replace with:
```kotlin
private fun ManageModelsSection(
    showManageModels: Boolean,
    onToggle: () -> Unit,
    schnellReady: Boolean,
    schnellSizeMB: Long,
    genauReady: Boolean,
    genauSizeMB: Long,
    multiExtendedReady: Boolean,
    multiExtendedSizeMB: Long,
    onDeleteSchnell: () -> Unit,
    onDeleteGenau: () -> Unit,
    onDeleteMultiExtended: () -> Unit
) {
```

- [ ] **Step 4.14 – Update `ManageModelsSection` body**

Find the `Column` inside `AnimatedVisibility` containing the model rows:
```kotlin
            Column(
                modifier = Modifier
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bundled model — cannot delete
                ModelManagementRow(
                    name = stringResource(Res.string.model_label_german_quick),
                    sizeMB = 75L,
                    isDeletable = false,
                    onDelete = {}
                )
                if (turboReady) {
                    ModelManagementRow(
                        name = stringResource(Res.string.model_label_german_accurate),
                        sizeMB = turboSizeMB,
                        isDeletable = true,
                        onDelete = onDeleteTurbo
                    )
                }
                if (multiStandardReady) {
                    ModelManagementRow(
                        name = stringResource(Res.string.model_label_multilingual_standard),
                        sizeMB = multiStandardSizeMB,
                        isDeletable = true,
                        onDelete = onDeleteMultiStandard
                    )
                }
                if (multiExtendedReady) {
                    ModelManagementRow(
                        name = stringResource(Res.string.model_label_multilingual_extended),
                        sizeMB = multiExtendedSizeMB,
                        isDeletable = true,
                        onDelete = onDeleteMultiExtended
                    )
                }
            }
```

Replace with:
```kotlin
            Column(
                modifier = Modifier
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (schnellReady) {
                    ModelManagementRow(
                        name = stringResource(Res.string.model_label_german_quick),
                        sizeMB = schnellSizeMB,
                        isDeletable = true,
                        onDelete = onDeleteSchnell
                    )
                }
                if (genauReady) {
                    ModelManagementRow(
                        name = stringResource(Res.string.model_label_german_accurate),
                        sizeMB = genauSizeMB,
                        isDeletable = true,
                        onDelete = onDeleteGenau
                    )
                }
                if (multiExtendedReady) {
                    ModelManagementRow(
                        name = stringResource(Res.string.model_label_multilingual_extended),
                        sizeMB = multiExtendedSizeMB,
                        isDeletable = true,
                        onDelete = onDeleteMultiExtended
                    )
                }
            }
```

- [ ] **Step 4.15 – Verify compilation**

```bash
./gradlew :shared:compileDebugKotlinAndroid --quiet 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL. If there are import errors for `MULTILINGUAL_STANDARD_SELECTION`, add the import back (it may still be used in the `when` expression for `currentMode`).

- [ ] **Step 4.16 – Commit**

```bash
git add shared/src/commonMain/kotlin/com/module/notelycompose/notes/ui/settings/ModelSelectionScreen.kt
git commit -m "feat: update ModelSelectionScreen — schnell/genau states, single multilingual card"
```

---

### Task 5: Update `SettingsScreen.kt` model label mapping

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/notes/ui/settings/SettingsScreen.kt`

- [ ] **Step 5.1 – Remap `MULTILINGUAL_STANDARD_SELECTION` in `LanguageModelSelectionSection`**

Find:
```kotlin
    val currentTitle = when (modelSavedSelection) {
        OPTIMIZED_MODEL_SELECTION       -> stringResource(Res.string.model_label_german_accurate)
        MULTILINGUAL_STANDARD_SELECTION -> stringResource(Res.string.model_label_multilingual_standard)
        MULTILINGUAL_EXTENDED_SELECTION -> stringResource(Res.string.model_label_multilingual_extended)
        else                            -> stringResource(Res.string.model_label_german_quick)
    }
    val currentDesc = when (modelSavedSelection) {
        OPTIMIZED_MODEL_SELECTION       -> stringResource(Res.string.optimized_model_setting_desc)
        MULTILINGUAL_STANDARD_SELECTION -> stringResource(Res.string.standard_model_setting_desc)
        MULTILINGUAL_EXTENDED_SELECTION -> stringResource(Res.string.optimized_model_setting_desc)
        else                            -> stringResource(Res.string.standard_model_setting_desc)
    }
```

Replace with:
```kotlin
    val currentTitle = when (modelSavedSelection) {
        OPTIMIZED_MODEL_SELECTION                       -> stringResource(Res.string.model_label_german_accurate)
        MULTILINGUAL_STANDARD_SELECTION,
        MULTILINGUAL_EXTENDED_SELECTION                 -> stringResource(Res.string.model_label_multilingual_extended)
        else                                            -> stringResource(Res.string.model_label_german_quick)
    }
    val currentDesc = when (modelSavedSelection) {
        OPTIMIZED_MODEL_SELECTION                       -> stringResource(Res.string.optimized_model_setting_desc)
        MULTILINGUAL_STANDARD_SELECTION,
        MULTILINGUAL_EXTENDED_SELECTION                 -> stringResource(Res.string.optimized_model_setting_desc)
        else                                            -> stringResource(Res.string.standard_model_setting_desc)
    }
```

- [ ] **Step 5.2 – Verify compilation**

```bash
./gradlew :shared:compileDebugKotlinAndroid --quiet 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5.3 – Commit**

```bash
git add shared/src/commonMain/kotlin/com/module/notelycompose/notes/ui/settings/SettingsScreen.kt
git commit -m "feat: remap MULTILINGUAL_STANDARD to extended model in SettingsScreen"
```

---

## Chunk 3: Strings, asset removal, final build

### Task 6: Update German string resources

**Files:**
- Modify: `shared/src/commonMain/composeResources/values-de/strings.xml`

- [ ] **Step 6.1 – Update 5 string values**

Make the following replacements (use exact old values to locate each string):

| Key | Old value | New value |
|-----|-----------|-----------|
| `speech_mode_german_quick_subtitle` | `Eingebettetes Modell · Sofort einsatzbereit · ~75 MB` | `~574 MB Download · Schnell &amp; präzise` |
| `speech_mode_german_accurate_subtitle` | `Hohe Erkennungsqualität · Download ~574 MB` | `Höchste Genauigkeit · Download ~1,6 GB` |
| `onboarding_screen_three_title` | `Sofort\neinsatzbereit` | `Internet\nerforderlich` |
| `onboarding_screen_three_desc` | `Deutsches Sprachmodell ist eingebettet –\nTranskription ohne Download und ohne Internet` | `Das Sprachmodell (~574 MB) wird beim ersten Start automatisch heruntergeladen. Du benötigst eine aktive Internetverbindung.` |
| `onboarding_screen_four_desc` | `Lade das Deutsche Turbo-Modell in den\nEinstellungen für höchste Genauigkeit herunter` | `Für maximale Genauigkeit: Lade das Turbo-Modell (~1,6 GB) in den Einstellungen herunter` |

> Note: In XML, `&` must be written as `&amp;`

- [ ] **Step 6.2 – Verify compilation (resource processing)**

```bash
./gradlew :shared:generateCommonMainResourceAccessors --quiet 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6.3 – Commit**

```bash
git add shared/src/commonMain/composeResources/values-de/strings.xml
git commit -m "feat: update DE onboarding and model subtitle strings"
```

---

### Task 7: Update English string resources

**Files:**
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 7.1 – Update 5 string values**

| Key | Old value | New value |
|-----|-----------|-----------|
| `speech_mode_german_quick_subtitle` | `Bundled model · Instant · ~75 MB` | `~574 MB download · Fast &amp; precise` |
| `speech_mode_german_accurate_subtitle` | `High accuracy · Download ~574 MB` | `Highest accuracy · Download ~1.6 GB` |
| `onboarding_screen_three_title` | `Transcribe\nand Summarise` | `Internet\nRequired` |
| `onboarding_screen_three_desc` | `Convert voice notes to text and\nsummaries without internet` | `The speech model (~574 MB) is downloaded automatically on first launch. An active internet connection is required.` |
| `onboarding_screen_four_desc` | `Create and transcribe notes in\nyour preferred language` | `For maximum accuracy: download the Turbo model (~1.6 GB) in Settings` |

- [ ] **Step 7.2 – Verify compilation**

```bash
./gradlew :shared:generateCommonMainResourceAccessors --quiet 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7.3 – Commit**

```bash
git add shared/src/commonMain/composeResources/values/strings.xml
git commit -m "feat: update EN onboarding and model subtitle strings"
```

---

### Task 8: Delete bundled asset

**Files:**
- Delete: `shared/src/androidMain/assets/ggml-tiny-german.bin`

- [ ] **Step 8.1 – Delete the binary**

```bash
rm "shared/src/androidMain/assets/ggml-tiny-german.bin"
```

- [ ] **Step 8.2 – Verify asset folder is empty (or remove it if empty)**

```bash
ls "shared/src/androidMain/assets/"
```

If the directory is now empty, it can stay — Gradle ignores empty asset dirs.

- [ ] **Step 8.3 – Full build to confirm no asset reference remains**

```bash
./gradlew assembleDebug --quiet 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL. APK will be ~75 MB smaller.

- [ ] **Step 8.4 – Commit**

```bash
git add -A shared/src/androidMain/assets/
git commit -m "feat: remove bundled ggml-tiny-german.bin asset (~75 MB)"
```

---

### Task 9: Final integration verification

- [ ] **Step 9.1 – Full debug build**

```bash
./gradlew assembleDebug 2>&1 | tail -40
```

Expected: BUILD SUCCESSFUL with no warnings about missing resources or unresolved references.

- [ ] **Step 9.2 – Check for any remaining references to removed identifiers**

```bash
grep -r "BUNDLED_GERMAN_MODEL_FILENAME\|ggml-tiny-german\|ggml-base-en\|extractBundledGerman\|isBundledModel\|setBundledModel\|turboReady\|turboSizeMB\|onDeleteTurbo\|multiStandardReady\|multiStandardSizeMB\|onDeleteMultiStandard\|MODE_MULTILINGUAL_STANDARD" shared/src --include="*.kt" --include="*.xml" -l
```

Expected: no output (no files match).

```bash
# Also verify LanguageSelectionScreen.kt needs no change (per spec §6)
grep -n "OPTIMIZED_MODEL_SELECTION\|model_selection" "shared/src/commonMain/kotlin/com/module/notelycompose/notes/ui/settings/LanguageSelectionScreen.kt"
```

Expected: only the Farsi auto-select line (`preferencesRepository.setModelSelection(OPTIMIZED_MODEL_SELECTION)`) — still correct after the redesign (OPTIMIZED still maps to cstr "Genau", and Farsi uses ggml-small anyway via `getSelectedModel()`). No change needed.

- [ ] **Step 9.3 – Final commit**

```bash
git add -A
git status  # confirm nothing unexpected
git commit -m "chore: final cleanup — model system redesign complete"
```

Only commit if `git status` shows untracked/modified files. If all was committed in prior steps, skip this step.
