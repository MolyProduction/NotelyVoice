# Model Loading: Performance & Crash Fix + Background UX

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the 30–60 s model-loading wait, prevent native crashes when the app is closed during model loading, and improve background-operation UX with a WakeLock and completion notification.

**Architecture:** Three independent improvements:
1. **Crash fix** – a `Mutex` inside `Transcriber.loadBaseModel()` makes `finish()` wait until the JNI call is done before stopping the service, so the foreground service never drops while the CPU is mid-load.
2. **Performance fix** – pre-warm the default model as soon as the user opens a note that has a recording; by the time they tap "Transkribieren" the model is already (partially) loaded.
3. **Notification UX** – WakeLock during load/transcription; distinguish "Modell wird geladen" vs "Transkription läuft" in the notification; post a silent "Transkription abgeschlossen" notification when done.

**Tech Stack:** Kotlin Multiplatform / Compose, Android Foreground Services, `kotlinx.coroutines.sync.Mutex`, `PowerManager.WakeLock`, `NotificationManager`, whisper.cpp Flash Attention (`flash_attn=true`)

---

## Root-Cause Summary (read before touching code)

### Bug 1 – Langsames Laden

| # | Ursache | Datei |
|---|---------|-------|
| R1 | Modell wird erst beim Drücken von "Transkribieren" geladen – kein Pre-Warming | `TranscriptionViewModel.startRecognizer()` |
| R2 | `loadBaseModel()` ist ein blockierender synchroner JNI-Aufruf; kein Fortschritt sichtbar | `Transcriper.android.kt:94` |
| R3 | `whisper_context_default_params()` hat `flash_attn=false`; CPU Flash Attention ist implementiert (`ggml-cpu.c`, `ops.cpp`) und kann 15–30 % Inferenz-Beschleunigung bringen | `jni.c:167` |
| R4 | Keine "Modell wird geladen"-Anzeige in Notification → Nutzer weiß nicht warum es dauert | `TranscriptionForegroundService.kt` |

### Bug 2 – Absturz beim Schließen

| # | Ursache | Datei |
|---|---------|-------|
| C1 | `stopTranscriptionService()` wird in `onCleared()` aufgerufen bevor der JNI-Call zurückkehrt → Foreground Service weg → Android darf Prozess killen → nativer Absturz | `TranscriptionViewModel.kt:158` |
| C2 | Kein `WakeLock` → Doze/Battery-Saver kann CPU während des Ladens suspendieren | `TranscriptionForegroundService.kt` |
| C3 | `loadBaseModel()` ist nicht abbrechbar; beim Abbrechen des Coroutine-Scopes läuft der JNI-Thread weiter, aber ohne Prozessschutz | `Transcriper.android.kt:94` |

### Feature – Hintergrund-UX

- Keine "Fertig"-Notification nach abgeschlossener Transkription
- Keine Phasenunterscheidung in der laufenden Notification
- Kein Hinweis auf Akkuoptimierung

---

## Chunk 1: WakeLock + Service-Notification-Phasen

### Task 1: WAKE_LOCK-Permission im Manifest

**Files:**
- Modify: `shared/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Permission hinzufügen**

Direkt nach der letzten `<uses-permission …>`-Zeile (vor `<application>`):

```xml
    <uses-permission android:name="android.permission.WAKE_LOCK" />
```

---

### Task 2: WakeLock + Phasen-Notifications in `TranscriptionForegroundService`

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/module/notelycompose/service/TranscriptionForegroundService.kt`

- [ ] **Step 1: Imports erweitern**

Füge folgende Imports hinzu (nicht vorhandene):

```kotlin
import android.content.Context
import android.os.PowerManager
```

- [ ] **Step 2: WakeLock-Feld und Phasenkonstanten hinzufügen**

In der Klasse, direkt nach der öffnenden Klassen-Klammer:

```kotlin
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_START = "START_TRANSCRIPTION"
        const val ACTION_PHASE_LOADING = "PHASE_LOADING"
        const val ACTION_PHASE_TRANSCRIBING = "PHASE_TRANSCRIBING"
        const val ACTION_COMPLETE = "ACTION_COMPLETE"
        private const val CHANNEL_ID = "transcription_channel"
        private const val CHANNEL_DONE_ID = "transcription_done_channel"
        private const val NOTIFICATION_ID = 2
        private const val NOTIFICATION_DONE_ID = 3
    }
```

(Die alte `companion object`-Definition ersetzen.)

- [ ] **Step 3: `onCreate()` – WakeLock erwerben**

Ersetze den bestehenden `onCreate()`-Body:

```kotlin
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(loading = true))
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MolyEcho:transcription"
        )
        @Suppress("WakelockTimeout")
        wakeLock?.acquire(45 * 60 * 1000L) // max 45 Minuten
    }
```

- [ ] **Step 4: `onDestroy()` – WakeLock freigeben**

Füge `onDestroy()` hinzu (falls nicht vorhanden):

```kotlin
    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        wakeLock = null
    }
```

- [ ] **Step 5: `onStartCommand()` – Phasensteuerung**

Ersetze die bestehende `onStartCommand()`-Implementierung vollständig:

```kotlin
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PHASE_LOADING -> updateNotification(loading = true)
            ACTION_PHASE_TRANSCRIBING -> updateNotification(loading = false)
            ACTION_COMPLETE -> {
                postCompletionNotification()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
```

- [ ] **Step 6: Hilfsmethoden hinzufügen**

Füge folgende private Methoden hinzu (vor der `companion object`):

```kotlin
    private fun updateNotification(loading: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(loading))
    }

    private fun buildNotification(loading: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (loading)
            getString(R.string.notification_model_loading_title)
        else
            getString(R.string.notification_transcription_title)
        val text = if (loading)
            getString(R.string.notification_model_loading_text)
        else
            getString(R.string.notification_transcription_text)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun postCompletionNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        createDoneNotificationChannel(nm)
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_DONE_ID)
            .setContentTitle(getString(R.string.notification_transcription_done_title))
            .setContentText(getString(R.string.notification_transcription_done_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_DONE_ID, notification)
    }

    private fun createDoneNotificationChannel(nm: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_DONE_ID,
            getString(R.string.notification_transcription_done_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Transcription", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
```

(Die alte `buildNotification()` und `createNotificationChannel()` entfernen.)

---

### Task 3: String-Ressourcen für Phasen-Notifications

**Files:**
- Modify: `shared/src/androidMain/res/values/strings.xml`
- Modify: `shared/src/androidMain/res/values-de/strings.xml`

- [ ] **Step 1: EN-Strings hinzufügen**

In `values/strings.xml` vor `</resources>`:

```xml
    <string name="notification_model_loading_title">Loading model…</string>
    <string name="notification_model_loading_text">MolyEcho is preparing the speech recognition model</string>
    <string name="notification_transcription_done_title">Transcription complete</string>
    <string name="notification_transcription_done_text">Tap to view the result in MolyEcho</string>
    <string name="notification_transcription_done_channel_name">Transcription complete</string>
```

- [ ] **Step 2: DE-Strings hinzufügen**

In `values-de/strings.xml` vor `</resources>`:

```xml
    <string name="notification_model_loading_title">Modell wird geladen…</string>
    <string name="notification_model_loading_text">MolyEcho bereitet das Spracherkennungsmodell vor</string>
    <string name="notification_transcription_done_title">Transkription abgeschlossen</string>
    <string name="notification_transcription_done_text">Tippe um das Ergebnis in MolyEcho anzusehen</string>
    <string name="notification_transcription_done_channel_name">Transkription abgeschlossen</string>
```

- [ ] **Step 3: Build-Check**

```
./gradlew :shared:compileDebugKotlinAndroid
```

Erwartet: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add \
  shared/src/androidMain/AndroidManifest.xml \
  shared/src/androidMain/kotlin/com/module/notelycompose/service/TranscriptionForegroundService.kt \
  shared/src/androidMain/res/values/strings.xml \
  shared/src/androidMain/res/values-de/strings.xml
git commit -m "feat: add WakeLock, phase notifications, and completion notification to TranscriptionForegroundService"
```

---

## Chunk 2: Crash-Fix – Mutex + Service-Lebenszyklus

### Task 4: Mutex in `Transcriber` – `loadBaseModel` thread-safe & cancellation-aware

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/module/notelycompose/platform/Transcriper.android.kt`

**Hintergrund:** Der JNI-Aufruf `WhisperLib.initContext()` kann nicht abgebrochen werden. Wenn `finish()` während des Ladens aufgerufen wird, muss es warten bis der JNI-Call abgeschlossen ist – erst dann darf der Kontext freigegeben und der Service gestoppt werden. Ein `Mutex` serialisiert `loadBaseModel` und `finish()`.

- [ ] **Step 1: Import hinzufügen**

Füge am Anfang der Datei hinzu:

```kotlin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
```

- [ ] **Step 2: Felder hinzufügen**

In der Klasse direkt unter den bestehenden Feldern (z.B. nach `currentLoadedModelName`):

```kotlin
    // Serializes model loading and finish() so finish() always waits until the JNI
    // call completes before releasing the context and stopping the service.
    private val modelLoadMutex = Mutex()

    // Set to true by finish() so loadBaseModel() can self-cancel after JNI returns.
    @Volatile private var isCancelled = false
```

- [ ] **Step 3: `loadBaseModel()` zu `suspend fun` machen + Mutex einbauen**

Ersetze den bestehenden `loadBaseModel()`-Body vollständig:

```kotlin
    private suspend fun loadBaseModel(modelFileName: String) {
        modelLoadMutex.withLock {
            if (isCancelled) return@withLock
            try {
                debugPrintln { "Loading model: $modelFileName\n" }
                val targetDir = modelsPath ?: run {
                    debugPrintln { "External storage unavailable — cannot load model $modelFileName" }
                    return@withLock
                }
                val modelFile = File(targetDir, modelFileName)
                if (!modelFile.exists()) {
                    extractFromAssets(modelFileName, modelFile)
                }
                whisperContext = WhisperContext.createContextFromFile(modelFile.absolutePath)
                if (isCancelled) {
                    // finish() was called while JNI ran – release immediately
                    whisperContext?.release()
                    whisperContext = null
                    return@withLock
                }
                canTranscribe = true
                currentLoadedModelName = modelFileName
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
```

Signatur-Änderung: `private fun` → `private suspend fun` (kein Breaking Change, da Aufrufer `initialize()` bereits `suspend` ist).

- [ ] **Step 4: `initialize()` – `isCancelled` nach Load prüfen**

Ersetze den bestehenden `initialize()`-Body:

```kotlin
    actual suspend fun initialize(modelFileName: String) {
        if (currentLoadedModelName == modelFileName && whisperContext != null) {
            debugPrintln { "speech: model $modelFileName already loaded, skipping re-init" }
            resetInactivityTimer()
            return
        }
        cancelInactivityTimer()
        debugPrintln { "speech: initialize model $modelFileName" }
        whisperContext?.release()
        whisperContext = null
        currentLoadedModelName = null
        canTranscribe = false
        loadBaseModel(modelFileName)
        // If finish() was called while loadBaseModel ran, skip timer setup
        if (isCancelled) return
        if (whisperContext != null) {
            resetInactivityTimer()
        }
    }
```

- [ ] **Step 5: `finish()` – Mutex abwarten + `isCancelled` setzen**

Ersetze den bestehenden `finish()`-Body:

```kotlin
    actual suspend fun finish() {
        isCancelled = true
        cancelInactivityTimer()
        // Wait for any in-progress loadBaseModel() JNI call to complete
        // before releasing the context. This prevents the foreground service
        // from being stopped while the JNI thread is still executing.
        modelLoadMutex.withLock {
            whisperContext?.release()
            whisperContext = null
            currentLoadedModelName = null
            canTranscribe = false
        }
    }
```

**Wichtig:** `isCancelled` wird auf `true` gesetzt BEVOR `withLock` aufgerufen wird. Wenn kein Load läuft, wird die Lock sofort erworben. Wenn ein Load läuft, wird `withLock` suspended (gibt Thread frei) und wartet bis der JNI-Call fertig ist.

---

### Task 5: `TranscriptionViewModel.onCleared()` – Service erst nach `finish()` stoppen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/transcription/TranscriptionViewModel.kt`

**Hintergrund:** Aktuell stoppt `onCleared()` den Service sofort über `stopRecognizer()`. Mit dem Mutex-Fix muss der Service erst gestoppt werden, nachdem `finish()` den Mutex freigegeben hat (d.h. nach Abschluss des JNI-Calls).

- [ ] **Step 1: `stopTranscriptionService()` aus `stopRecognizer()` entfernen**

Finde `stopRecognizer()`:

```kotlin
fun stopRecognizer() {
    serviceController.stopTranscriptionService()
    _uiState.update { current ->
        current.copy(inTranscription = false)
    }
    viewModelScope.launch {
        transcriber.stop()
    }
}
```

Entferne den `serviceController.stopTranscriptionService()`-Aufruf:

```kotlin
fun stopRecognizer() {
    _uiState.update { current ->
        current.copy(inTranscription = false)
    }
    viewModelScope.launch {
        transcriber.stop()
    }
}
```

**Hinweis:** Der Service stoppt sich nun selbst via `ACTION_COMPLETE` (in `onComplete`/`onError`) oder durch `finishRecognizer()`. `stopRecognizer()` ist für das manuelle Abbrechen des JNI-Calls zuständig, nicht für den Service-Lebenszyklus.

- [ ] **Step 2: `finishRecognizer()` – Service-Stop hierhin verschieben**

`finishRecognizer()` ist der finale Aufräum-Schritt. Er DARF den Service stoppen:

```kotlin
fun finishRecognizer() {
    serviceController.stopTranscriptionService()
    _uiState.update { current ->
        current.copy(
            inTranscription = false,
            originalText = "",
            finalText = "",
            partialText = "",
            summarizedText = "",
            progress = 0
        )
    }
    viewModelScope.launch {
        transcriber.finish()
    }
}
```

(Keine Änderung notwendig – `stopTranscriptionService()` ist bereits hier; das ist korrekt.)

- [ ] **Step 3: `onCleared()` – Service NACH `finish()` stoppen**

Ersetze den bestehenden `onCleared()`-Body:

```kotlin
    override fun onCleared() {
        // onCleared() may be called while model loading is in progress (user swiped
        // app away). We must NOT stop the service here, because finish() uses a Mutex
        // to wait for the JNI call. The new coroutine below will:
        //  1. abort any in-progress transcription (stop())
        //  2. wait for model loading to complete (finish(), via Mutex)
        //  3. only then stop the service
        _uiState.update { it.copy(inTranscription = false) }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                transcriber.stop()
                transcriber.finish()
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                serviceController.stopTranscriptionService()
            }
        }
    }
```

- [ ] **Step 4: Service-Stop in `onComplete`/`onError` auf `ACTION_COMPLETE` umstellen**

In `startRecognizer()`, im `onComplete`-Callback:

```kotlin
onComplete = {
    serviceController.stopTranscriptionService()   // ← altes Vorgehen
    ...
}
```

Ersetzen mit:

```kotlin
onComplete = {
    // Signal service to post completion notification and then stop itself
    context.startService(
        Intent(context, TranscriptionForegroundService::class.java).apply {
            action = TranscriptionForegroundService.ACTION_COMPLETE
        }
    )
    ...
}
```

**Problem:** `TranscriptionViewModel` hat keinen Zugriff auf `context` oder `TranscriptionForegroundService`. Daher soll das Senden von `ACTION_COMPLETE` über den `AndroidTranscriptionServiceController` erfolgen.

Füge eine neue Methode in das `TranscriptionServiceController`-Interface hinzu:

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/transcription/TranscriptionServiceController.kt`

```kotlin
interface TranscriptionServiceController {
    fun startTranscriptionService()
    fun stopTranscriptionService()
    fun notifyTranscriptionPhaseLoading()
    fun notifyTranscriptionPhaseTranscribing()
    fun notifyTranscriptionComplete()
}
```

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/module/notelycompose/transcription/TranscriptionServiceController.android.kt`

```kotlin
class AndroidTranscriptionServiceController(
    private val context: Context
) : TranscriptionServiceController {

    override fun startTranscriptionService() {
        context.startForegroundService(
            Intent(context, TranscriptionForegroundService::class.java).apply {
                action = TranscriptionForegroundService.ACTION_START
            }
        )
    }

    override fun stopTranscriptionService() {
        context.stopService(Intent(context, TranscriptionForegroundService::class.java))
    }

    override fun notifyTranscriptionPhaseLoading() {
        context.startService(
            Intent(context, TranscriptionForegroundService::class.java).apply {
                action = TranscriptionForegroundService.ACTION_PHASE_LOADING
            }
        )
    }

    override fun notifyTranscriptionPhaseTranscribing() {
        context.startService(
            Intent(context, TranscriptionForegroundService::class.java).apply {
                action = TranscriptionForegroundService.ACTION_PHASE_TRANSCRIBING
            }
        )
    }

    override fun notifyTranscriptionComplete() {
        context.startService(
            Intent(context, TranscriptionForegroundService::class.java).apply {
                action = TranscriptionForegroundService.ACTION_COMPLETE
            }
        )
    }
}
```

Für iOS (No-op):

**Files:**
- Modify: iOS no-op implementation (falls vorhanden: `TranscriptionServiceController.ios.kt` o.ä.)

Alle neuen Methoden mit leerem Body einfügen.

- [ ] **Step 5: `startRecognizer()` – Phasen-Notifications einfügen**

Im `startRecognizer()`-Body in `TranscriptionViewModel`:

Nach dem `initialize()`-Aufruf (nach dem Laden des Modells), vor dem `start()`-Aufruf:

```kotlin
// Nach initialize():
serviceController.notifyTranscriptionPhaseTranscribing()

// In onComplete:
onComplete = {
    serviceController.notifyTranscriptionComplete()
    ...
}

// In onError:
onError = {
    serviceController.stopTranscriptionService()
    ...
}
```

Und direkt nach `serviceController.startTranscriptionService()` am Anfang von `startRecognizer()`:

```kotlin
serviceController.startTranscriptionService()
serviceController.notifyTranscriptionPhaseLoading()
```

- [ ] **Step 6: Build-Check**

```
./gradlew :shared:compileDebugKotlinAndroid
```

Erwartet: BUILD SUCCESSFUL. Bei Compile-Fehlern im Interface die iOS-No-op-Implementation überprüfen.

- [ ] **Step 7: Commit**

```bash
git add \
  shared/src/androidMain/kotlin/com/module/notelycompose/platform/Transcriper.android.kt \
  shared/src/commonMain/kotlin/com/module/notelycompose/transcription/TranscriptionViewModel.kt \
  shared/src/commonMain/kotlin/com/module/notelycompose/transcription/TranscriptionServiceController.kt \
  shared/src/androidMain/kotlin/com/module/notelycompose/transcription/TranscriptionServiceController.android.kt
git commit -m "fix: prevent crash on background by using Mutex to delay service stop until JNI model load completes"
```

---

## Chunk 3: Performance – Modell vorwärmen

### Task 6: Pre-Warm bei Öffnen einer Notiz mit Aufnahme

**Kontext:** Der `Transcriber` ist ein Koin-Singleton. Das Pre-Warming muss nur `transcriber.initialize(modelName)` aufrufen – ohne Service, ohne ViewModel-Scope. Wenn die Transcription-Screen sich öffnet und das Modell bereits geladen ist, springt `initialize()` sofort in den Cache-Pfad.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/notes/presentation/detail/NoteDetailScreenViewModel.kt`

- [ ] **Step 1: `Transcriber` und `ModelSelection` als Parameter hinzufügen**

Lies zunächst den aktuellen Konstruktor von `NoteDetailScreenViewModel` (falls noch nicht gelesen).

Füge `Transcriber` und `ModelSelection` als injizierte Parameter hinzu:

```kotlin
class NoteDetailScreenViewModel(
    // … bestehende Parameter …
    private val transcriber: Transcriber,
    private val modelSelection: ModelSelection,
) : ViewModel() {
```

Imports:
```kotlin
import com.module.notelycompose.platform.Transcriber
import com.module.notelycompose.modelDownloader.ModelSelection
```

- [ ] **Step 2: Pre-Warm-Methode hinzufügen**

```kotlin
    /**
     * Called when the note detail screen opens with an existing recording.
     * Starts loading the Whisper model in the background so it is ready when
     * the user taps "Transkribieren".
     */
    fun prewarmTranscriber() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val model = modelSelection.getSelectedModel()
                transcriber.initialize(model.name)
            } catch (e: Throwable) {
                // Pre-warming is best-effort; ignore errors here
                e.printStackTrace()
            }
        }
    }
```

- [ ] **Step 3: DI-Registrierung prüfen**

`Transcriber` und `ModelSelection` sind bereits als Koin-Singletons registriert (`Modules.android.kt:58`, `Modules.kt`). Koin löst sie automatisch auf. Keine Änderung an `Modules.android.kt` nötig, solange `NoteDetailScreenViewModel` per `viewModelOf(::NoteDetailScreenViewModel)` gebunden ist.

Prüfe in `Modules.kt` (oder wo auch immer `NoteDetailScreenViewModel` registriert ist):
```kotlin
viewModelOf(::NoteDetailScreenViewModel)
```
Koin's `viewModelOf` übergibt alle benötigten Parameter automatisch.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/notes/ui/detail/NoteDetailScreen.kt`

- [ ] **Step 4: `prewarmTranscriber()` aufrufen wenn Aufnahme vorhanden**

Finde den Composable-Aufruf in `NoteDetailScreen`. Füge einen `LaunchedEffect` hinzu, der `prewarmTranscriber()` aufruft sobald `recordingPath` nicht leer ist:

```kotlin
val editorState by editorViewModel.editorPresentationState.collectAsState()

LaunchedEffect(editorState.recording.recordingPath) {
    if (editorState.recording.recordingPath.isNotEmpty()) {
        noteDetailViewModel.prewarmTranscriber()
    }
}
```

Dieser Effekt läuft genau einmal (wenn `recordingPath` sich auf einen nicht-leeren Wert ändert). Er startet das Laden im Hintergrund, ohne den Service oder eine Notification zu starten.

- [ ] **Step 5: Build-Check**

```
./gradlew :shared:compileDebugKotlinAndroid
```

Erwartet: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add \
  shared/src/commonMain/kotlin/com/module/notelycompose/notes/presentation/detail/NoteDetailScreenViewModel.kt \
  shared/src/commonMain/kotlin/com/module/notelycompose/notes/ui/detail/NoteDetailScreen.kt
git commit -m "perf: pre-warm Whisper model when note with recording is opened"
```

---

## Chunk 4: UI – Ladezustand in TranscriptionScreen

### Task 7: `isModelLoading` zu `TranscriptionUiState` hinzufügen

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/transcription/TranscriptionUiState.kt`

- [ ] **Step 1: Feld hinzufügen**

```kotlin
data class TranscriptionUiState(
    val inTranscription: Boolean = false,
    val isModelLoading: Boolean = false,   // ← neu
    val progress: Int = 0,
    // … weitere bestehende Felder …
)
```

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/transcription/TranscriptionViewModel.kt`

- [ ] **Step 2: `isModelLoading = true` setzen bevor `initialize()` aufgerufen wird**

Im `startRecognizer()`-Coroutine-Body, direkt nach dem `_uiState.update { current -> current.copy(inTranscription = true) }`:

```kotlin
_uiState.update { it.copy(inTranscription = true, isModelLoading = true) }
// Initialize the correct model sequentially before transcribing.
val modelFileName = modelSelection.getSelectedModel()
transcriber.initialize(modelFileName.name)
// Model is loaded – update notification and UI state
serviceController.notifyTranscriptionPhaseTranscribing()
_uiState.update { it.copy(isModelLoading = false) }
```

- [ ] **Step 3: `isModelLoading = false` in `finishRecognizer()` und `stopRecognizer()` sicherstellen**

In `finishRecognizer()`:
```kotlin
_uiState.update { current ->
    current.copy(
        inTranscription = false,
        isModelLoading = false,  // ← hinzufügen
        originalText = "",
        // …
    )
}
```

In `stopRecognizer()`:
```kotlin
_uiState.update { current ->
    current.copy(inTranscription = false, isModelLoading = false)  // ← isModelLoading hinzufügen
}
```

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/transcription/TranscriptionScreen.kt`

- [ ] **Step 4: Ladezustand im UI anzeigen — Logo + Puls-Animation**

Ersetze den `LinearProgressIndicator`-Block (der Bereich, der bei `progress == 0` einen unbestimmten Balken zeigt):

```kotlin
// Modell lädt – Logo mit Puls-Animation + Label + unbestimmter Balken
if (transcriptionUiState.isModelLoading) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_pulse_alpha"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(Res.drawable.molyecho_logo),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .padding(top = 8.dp)
                .graphicsLayer(alpha = pulseAlpha),
            contentScale = ContentScale.Fit
        )
        Text(
            text = stringResource(Res.string.transcription_loading_model),
            color = LocalCustomColors.current.bodyContentColor,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
        LinearProgressIndicator(
            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
            strokeCap = StrokeCap.Round
        )
    }
} else if (transcriptionUiState.inTranscription && transcriptionUiState.progress == 0) {
    LinearProgressIndicator(
        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
        strokeCap = StrokeCap.Round
    )
} else if (transcriptionUiState.inTranscription && transcriptionUiState.progress in 1..99) {
    SmoothLinearProgressBar((transcriptionUiState.progress / 100f))
}
```

Neue Imports für `TranscriptionScreen.kt`:
```kotlin
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import de.molyecho.notlyvoice.resources.molyecho_logo
import org.jetbrains.compose.resources.painterResource
```

**Files:**
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`
- Modify: `shared/src/commonMain/composeResources/values-de/strings.xml`

- [ ] **Step 5: String-Ressourcen**

EN:
```xml
    <string name="transcription_loading_model">Loading speech recognition model…</string>
```

DE:
```xml
    <string name="transcription_loading_model">Spracherkennungsmodell wird geladen…</string>
```

- [ ] **Step 6: Build-Check**

```
./gradlew :shared:compileDebugKotlinAndroid
```

Erwartet: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add \
  shared/src/commonMain/kotlin/com/module/notelycompose/transcription/TranscriptionUiState.kt \
  shared/src/commonMain/kotlin/com/module/notelycompose/transcription/TranscriptionViewModel.kt \
  shared/src/commonMain/kotlin/com/module/notelycompose/transcription/TranscriptionScreen.kt \
  shared/src/commonMain/composeResources/values/strings.xml \
  shared/src/commonMain/composeResources/values-de/strings.xml
git commit -m "feat: show model-loading state in TranscriptionScreen UI"
```

---

## Chunk 5 (P2): Flash Attention in JNI

> **P3 (use_mlock) – nicht umgesetzt:** `whisper_context_params` hat kein mlock-Feld. Das Modell liegt im Heap (nicht mmap). Android `RLIMIT_MEMLOCK` ist zu klein für 574 MB. Der Foreground Service schützt den Prozess bereits. → P3 gestrichen.

### Task 9: Flash Attention in `jni.c` aktivieren

**Files:**
- Modify: `lib/src/main/jni/whisper/jni.c`

**Hintergrund:** `whisper_context_default_params()` hat `flash_attn = false`. Die CPU Flash-Attention-Implementierung ist vorhanden (`ggml-cpu.c` → `GGML_OP_FLASH_ATTN_EXT`, `ops.cpp`). Aktivieren kann 15–30 % Inferenz-Beschleunigung bringen. Bestätigt durch Prüfung von `CMakeLists.txt` (kein Vulkan eingebunden; `use_gpu=false` ist sicher).

- [ ] **Step 1: `initContext` – Custom params mit flash_attn**

Ersetze den Body von `Java_com_whispercpp_whisper_WhisperLib_00024Companion_initContext`:

```c
JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str) {
    UNUSED(thiz);
    struct whisper_context *context = NULL;
    const char *model_path_chars = (*env)->GetStringUTFChars(env, model_path_str, NULL);
    struct whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;    // Vulkan not compiled in; explicit no-op
    params.flash_attn = true;  // CPU flash-attention: fuses QKV for ~15-30% speedup
    context = whisper_init_from_file_with_params(model_path_chars, params);
    (*env)->ReleaseStringUTFChars(env, model_path_str, model_path_chars);
    return (jlong) context;
}
```

- [ ] **Step 2: `whisper_init_from_asset` – Custom params mit flash_attn**

Ersetze die letzte Zeile der statischen `whisper_init_from_asset`-Funktion:

```c
// Vorher:
return whisper_init_with_params(&loader, whisper_context_default_params());

// Nachher:
struct whisper_context_params params = whisper_context_default_params();
params.use_gpu = false;
params.flash_attn = true;
return whisper_init_with_params(&loader, params);
```

- [ ] **Step 3: Build-Check**

```
./gradlew :lib:assembleDebug
```

Erwartet: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add lib/src/main/jni/whisper/jni.c
git commit -m "perf: enable flash_attn=true in whisper JNI for 15-30% inference speedup"
```

---

## Chunk 6: Abschluss-Verifikation

### Task 10: Full Build + Smoke-Tests

- [ ] **Step 1: Debug-APK bauen**

```
./gradlew assembleDebug
```

Erwartet: BUILD SUCCESSFUL ohne Fehler.

- [ ] **Step 2: Release-APK bauen (optional, zum Smoke-Test)**

```
./gradlew assembleRelease
```

- [ ] **Step 3: Manuelle Smoke-Tests auf Gerät**

```
./gradlew installDebug
```

| Szenario | Erwartetes Verhalten |
|---|---|
| App öffnen → Notiz mit Aufnahme öffnen → 10 s warten → Transkribieren | Ladezeit deutlich kürzer als ohne Pre-Warming |
| TranscriptionScreen öffnen | Notification zeigt "Modell wird geladen…"; wechselt zu "Transkription läuft" wenn Modell fertig |
| Transkription abschließen | Notification "Transkription abgeschlossen" erscheint; Ongoing-Notification verschwindet |
| Während Modell lädt: App mit Recents schließen → nach 60 s App neu öffnen | Kein Absturz; Modell wurde vollständig geladen; nächste Transkription startet sofort (gecacht) |
| Gerät sperren während Transkription | CPU bleibt aktiv (WakeLock); Transkription läuft durch; Fertig-Notification erscheint |
| Energiesparmodus aktiv; Transkription starten | Foreground-Service schützt Prozess; kein Abbruch |

- [ ] **Step 4: Final Commit + Push**

```bash
git push origin german-fork
```

---

## Dateiübersicht

| Datei | Aktion |
|---|---|
| `lib/src/main/jni/whisper/jni.c` | Modify – flash_attn=true, use_gpu=false (P2) |
| `androidMain/AndroidManifest.xml` | Modify – WAKE_LOCK permission |
| `service/TranscriptionForegroundService.kt` | Modify – WakeLock, Phasen-Actions, Fertig-Notification |
| `androidMain/res/values/strings.xml` | Modify – neue Notification-Strings |
| `androidMain/res/values-de/strings.xml` | Modify – neue Notification-Strings (DE) |
| `platform/Transcriper.android.kt` | Modify – Mutex, isCancelled, loadBaseModel suspend |
| `transcription/TranscriptionViewModel.kt` | Modify – onCleared Lifecycle-Fix, isModelLoading, phase notifications |
| `transcription/TranscriptionServiceController.kt` | Modify – neue Interface-Methoden |
| `transcription/TranscriptionServiceController.android.kt` | Modify – neue Methoden implementiert |
| `notes/ui/detail/NoteDetailScreen.kt` | Modify – LaunchedEffect für Pre-Warming |
| `transcription/TranscriptionUiState.kt` | Modify – isModelLoading Feld |
| `transcription/TranscriptionScreen.kt` | Modify – Logo + Puls-Animation bei Modell-Ladestand |
| `composeResources/values/strings.xml` | Modify – transcription_loading_model |
| `composeResources/values-de/strings.xml` | Modify – transcription_loading_model (DE) |
