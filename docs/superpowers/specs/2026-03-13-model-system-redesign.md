# Model System Redesign — Remove Bundled Tiny Model

**Date:** 2026-03-13
**Branch:** german-fork
**Scope:** Model data layer, startup logic, model selection UI, string resources, asset removal

---

## Problem

The app currently ships with a ~75 MB German whisper model (`ggml-tiny-german.bin`) bundled inside the APK. This inflates the download size and is no longer the desired default. The user wants:

1. The q5_0 quantized model (574 MB, good quality/speed trade-off) to become the new **"Schnell"** standard — downloaded on demand at first transcription attempt.
2. The full-precision cstr model (1.62 GB, highest accuracy) to be restored as the **"Genau"** option in Settings.
3. All bundled-model plumbing (asset extraction on startup, DataStore tracking key) removed.
4. Onboarding text updated to reflect that an internet connection is required on first use.

---

## Architecture

No structural changes to the model pipeline. The `ModelDownloaderViewModel.checkTranscriptionAvailability()` flow already handles downloadable models correctly:

- If model file absent and `url != null` → emit `AskForUserAcceptance()` (shows download dialog with progress)
- If model file present → emit `ModelsAreReady()`

Removing the bundled model means the `url == null` branch of that check becomes permanently unreachable for German models — no code change required there.

---

## Files Changed

### 1. `shared/src/commonMain/kotlin/.../modelDownloader/ModelSelection.kt`

- Remove `BUNDLED_GERMAN_MODEL_FILENAME` constant.
- Remove bundled tiny model entry from the `models` list (was index 3, `url = null`).
- Remap model indices:
  - `STANDARD_MODEL_SELECTION (0)` → `ggml-large-v3-turbo-german-q5_0.bin`, 574 MB, F1sk URL = **"Schnell"**
  - `OPTIMIZED_MODEL_SELECTION (1)` → `ggml-large-v3-turbo-german.bin`, 1.62 GB, cstr URL = **"Genau"**
- `getDefaultTranscriptionModel()` returns the q5_0 model.
- `getSelectedModel()` `else` branch returns the q5_0 model (was bundled tiny).

**Model entries after change:**

| Index | File | Size | URL |
|-------|------|------|-----|
| 0 | ggml-base-en.bin | 142 MB | ggerganov/whisper.cpp |
| 1 | ggml-small.bin | 465 MB | ggerganov/whisper.cpp |
| 2 | ggml-base-hi.bin | 140 MB | khidrew/whisper-base-hindi-ggml |
| 3 | ggml-large-v3-turbo-german-q5_0.bin | 574 MB | F1sk/whisper-large-v3-turbo-german-ggml-q5_0 |
| 4 | ggml-large-v3-turbo-german.bin | 1.62 GB | cstr/whisper-large-v3-turbo-german-ggml |

### 2. `shared/src/androidMain/kotlin/.../NoteApp.kt`

- Remove `extractBundledGermanModelIfNeeded()` method.
- Remove the `appScope` coroutine scope and its launch block.
- Remove import of `BUNDLED_GERMAN_MODEL_FILENAME`.

### 3. `shared/src/commonMain/kotlin/.../onboarding/data/PreferencesRepository.kt`

- Remove `KEY_BUNDLED_MODEL_VERSION` preferences key.
- Remove `BUNDLED_MODEL_VERSION` constant.
- Remove `isBundledModelExtracted()` and `setBundledModelExtracted()` methods.

### 4. `shared/src/commonMain/kotlin/.../notes/ui/settings/ModelSelectionScreen.kt`

- Add `schnellReady` state: `transcriber.doesModelExists("ggml-large-v3-turbo-german-q5_0.bin")`
- Rename `turboReady` → `genauReady`: checks `"ggml-large-v3-turbo-german.bin"`
- Rename `turboSizeMB` → `genauSizeMB`.
- German Quick `SpeechModeCard`: `statusReady = schnellReady` (remove hardcoded `true`).
- `ManageModelsSection`:
  - Remove the non-deletable bundled-model row.
  - Add deletable row for q5_0 model when `schnellReady`.
  - `onDeleteSchnell`: delete file, set `schnellReady = false`; if current mode is `MODE_GERMAN_QUICK`, fall back to `NO_MODEL_SELECTION`.
  - `anyDeletable = schnellReady || genauReady || multiStandardReady || multiExtendedReady`.

### 5. String resources (`values-de/strings.xml` and `values/strings.xml`)

| Key | Old value (DE) | New value (DE) |
|-----|----------------|----------------|
| `speech_mode_german_quick_subtitle` | `Eingebettetes Modell · Sofort einsatzbereit · ~75 MB` | `~574 MB Download · Schnell & präzise` |
| `speech_mode_german_accurate_subtitle` | `Hohe Erkennungsqualität · Download ~574 MB` | `Höchste Genauigkeit · Download ~1,6 GB` |
| `onboarding_screen_three_title` | `Sofort\neinsatzbereit` | `Internet\nerforderlich` |
| `onboarding_screen_three_desc` | `Deutsches Sprachmodell ist eingebettet – Transkription ohne Download und ohne Internet` | `Das Sprachmodell (~574 MB) wird beim ersten Start automatisch heruntergeladen. Du benötigst eine aktive Internetverbindung.` |
| `onboarding_screen_four_desc` | `Lade das Deutsche Turbo-Modell in den Einstellungen für höchste Genauigkeit herunter` | `Für maximale Genauigkeit: Lade das Turbo-Modell (~1,6 GB) in den Einstellungen herunter` |

English (`values/strings.xml`) updated with equivalent translations.

### 6. Asset removal

- Delete `shared/src/androidMain/assets/ggml-tiny-german.bin` (~75 MB binary).

---

## What Does NOT Change

- `TranscriptionViewModel` — no changes.
- `DownloaderDialog` — already shows filename, progress bar, downloaded/total MB. No changes needed.
- `ModelDownloaderViewModel` — no changes; the `url == null` branch stays in place.
- `Transcriber.android.kt` — `extractFromAssets()` fails gracefully when no asset exists. No changes.
- All UI layouts, navigation, themes, colors.
- iOS transcription logic.

---

## Constraints & Risks

- **Existing users with `model_selection = NO_MODEL_SELECTION (-1)`** will hit the `else` branch in `getSelectedModel()`, which after this change returns the q5_0 model. Since q5_0 is not bundled, `checkTranscriptionAvailability()` will show the download dialog — correct behavior.
- **Existing users who already downloaded q5_0** as the "Genau" model (OPTIMIZED_MODEL_SELECTION = 1) — after this change OPTIMIZED maps to the cstr model. Their q5_0 file stays on disk (not deleted), and the new STANDARD (0) pointing to q5_0 means it will be immediately available if they were on OPTIMIZED before. Edge case, acceptable.
- **Build size** will decrease by ~75 MB (asset removed from APK).
- **No ProGuard changes needed** — no new classes introduced.
- **No manual steps** required beyond building and testing.
