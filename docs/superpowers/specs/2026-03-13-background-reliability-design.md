# Background Reliability – Design Spec
**Date:** 2026-03-13
**Branch:** german-fork
**Status:** Approved (rev 3 – reviewer issues addressed)

## Problem

The app does not run reliably in the background on Android when not in the foreground. Five root causes need addressing (problem 5 – download tracking – was found to already be implemented and is removed):

1. Transcription runs in `viewModelScope` with no foreground service → Android kills the process
2. `AudioRecordingService.onStartCommand` returns `START_NOT_STICKY` → not restarted after kill
3. Recording notification has no `contentIntent` and is not `ongoing`
4. Back navigation during an active recording has no confirmation dialog

---

## Design

### Change 1: `TranscriptionForegroundService` + Platform Bridge

**Purpose:** Protect the CPU-intensive Whisper transcription from Android's background process limits (API 26+).

**Architecture constraint:** `TranscriptionViewModel` is in `commonMain` (KMP shared code) and cannot call Android APIs directly. Solution: inject a `TranscriptionServiceController` interface.

#### 1a. `TranscriptionServiceController` interface (commonMain)

```
shared/src/commonMain/kotlin/.../transcription/TranscriptionServiceController.kt
```

```kotlin
interface TranscriptionServiceController {
    fun startTranscriptionService()
    fun stopTranscriptionService()
}
```

#### 1b. Android implementation (androidMain)

```
shared/src/androidMain/kotlin/.../transcription/TranscriptionServiceController.android.kt
```

Calls `context.startForegroundService(Intent(context, TranscriptionForegroundService::class.java))` and `context.stopService(...)`. Registered as a Koin `single<TranscriptionServiceController>` in `platformModule`.

#### 1c. iOS implementation (iosMain)

No-op — iOS does not have the same background execution limits for in-process CPU work.

#### 1d. `TranscriptionForegroundService` (new, androidMain)

```
shared/src/androidMain/kotlin/.../service/TranscriptionForegroundService.kt
```

- `Service` subclass
- `onCreate()`: creates notification channel, calls `startForeground(2, buildNotification())`
- `onStartCommand()`: returns `START_NOT_STICKY` — transcription cannot be meaningfully resumed after a kill
- `onDestroy()`: no coroutine needed — the service is purely a "process anchor"
- Notification: channel `"transcription_channel"`, title "Transkription läuft…", `setOngoing(true)`, `setContentIntent(PendingIntent → MainActivity)`
- Progress updates: `TranscriptionViewModel` calls `updateTranscriptionNotification(progress)` via the controller after each `onProgress` callback (optional for v1 — static text is acceptable)

**Why no transcription logic in the service:** The service is a *lifecycle anchor only*. The `Transcriber` singleton (Koin `single`) is shared between the ViewModel and any code that holds a reference to it. The ViewModel's coroutine continues to drive `transcriber.start()`. The foreground service just tells Android "this process is doing user-visible work, don't kill it." This minimises changes to existing code.

#### 1e. `TranscriptionViewModel` changes

`startRecognizer()`:
1. Call `serviceController.startTranscriptionService()` before launching the coroutine
2. In `onComplete` callback: call `serviceController.stopTranscriptionService()`
3. In `onError` callback: call `serviceController.stopTranscriptionService()`

`stopRecognizer()` / `finishRecognizer()`:
- Also call `serviceController.stopTranscriptionService()` so the service is cleaned up if the user cancels

`onCleared()`:
- Also call `serviceController.stopTranscriptionService()`

#### 1f. Manifest additions

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<service
    android:name="com.module.notelycompose.service.TranscriptionForegroundService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

**Known limitation:** On Android 14+ (API 34) `dataSync` foreground services are force-stopped after 6 hours. For typical German voice notes this limit will never be hit. A future improvement can switch to `mediaProcessing` (API 34+) which was designed for on-device ML inference and has no time limit — this would require adding `android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING` and an API-34 guard in the service startup code.

---

### Change 2: `AudioRecordingService` — `START_STICKY` + null-intent guard

**File:** `AudioRecordingService.kt`

Two changes:

1. Return `START_STICKY` instead of `START_NOT_STICKY`
2. In `onStartCommand`, handle the null-intent restart case explicitly:

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent == null) {
        // Restarted after kill with no pending intent — nothing to record, clean up
        stopSelf()
        return START_NOT_STICKY
    }
    when (intent.action) { ... }
    return START_STICKY
}
```

This prevents a zombie foreground service showing a persistent notification while doing nothing.

---

### Change 3: Recording Notification Improvements

**File:** `AudioRecordingService.kt` — `buildNotification()`

- `setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply { flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_SINGLE_TOP }, PendingIntent.FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))`
- `setOngoing(true)` — cannot be dismissed by swipe while recording is active
- `setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)` — wrapped in `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)` guard to avoid lint failure on minSdk < 31

---

### Change 4: Back-Button Confirmation Dialog During Recording

**Purpose:** Prevent accidental recording termination via Back/back-arrow.

**Affected file:** `RecordingScreen.kt`

**Two interception points** need updating:

#### 4a. `HandlePlatformBackNavigation` (system back gesture/button)

Current: single `HandlePlatformBackNavigation(enabled = true) { navigateBack() }` placed after the `when(screenState)` block — unconditional.

New: replace the single handler with two handlers, **placed outside the `when` block** (same position as current, after line 141). Both handlers must always be in the composition tree to avoid being torn down during a state transition frame:

```kotlin
// Intercept back in Recording state — show confirmation dialog
HandlePlatformBackNavigation(enabled = screenState == ScreenState.Recording) {
    showStopConfirmDialog = true
}
// Allow back in all other states
HandlePlatformBackNavigation(enabled = screenState != ScreenState.Recording) {
    navigateBack()
}
```

The conditions `screenState == Recording` and `screenState != Recording` are logically mutually exclusive, so at most one handler fires at any time. Composition order does not matter here because only one handler is ever enabled simultaneously.

#### 4b. `RecordingUiComponentBackButton` (on-screen back arrow)

Current signature: `(onNavigateBack: () -> Unit, onStopRecording: () -> Unit)` — both called unconditionally on click.

New signature: replace with a single `onBackPress: () -> Unit` parameter.

`RecordingUiComponentBackButton` is passed to **three** composables; all three must be updated:

| Call site | New `onBackPress` value |
|---|---|
| `RecordingInitialScreen` (line 155) | `navigateBack` — unchanged behaviour |
| `LandscapeRecordingInProgressScreen` (line 244) | `{ showStopConfirmDialog = true }` |
| `PotraitRecordingInProgressScreen` (line 356) | `{ showStopConfirmDialog = true }` |

`showStopConfirmDialog` is declared in `RecordingScreen` and passed down as a lambda. The sub-screens pass it through to `RecordingUiComponentBackButton`.

#### 4c. Dialog implementation

`showStopConfirmDialog` is a local `remember { mutableStateOf(false) }` in `RecordingScreen` — pure UI state, not in ViewModel or UiState.

When `showStopConfirmDialog == true`, show `AlertDialog`:
- Title: "Aufnahme beenden?" (use existing strings resource; add if missing)
- Confirm button: "Beenden" → `viewModel.onStopRecording()` (transitions to `ScreenState.Success`, plays animation, then `navigateBack()` after 2s — the existing success flow)
- Dismiss button: "Weiter aufnehmen" → `showStopConfirmDialog = false`

**No changes to `AudioRecorderViewModel.onCleared()`.** The existing stop-on-clear behaviour remains correct. The dialog is the gate before Back navigation is allowed.

---

## Files Changed

| File | Change |
|---|---|
| `transcription/TranscriptionServiceController.kt` | **NEW** (commonMain interface) |
| `transcription/TranscriptionServiceController.android.kt` | **NEW** (androidMain impl) |
| `transcription/TranscriptionServiceController.ios.kt` | **NEW** (iosMain no-op) |
| `service/TranscriptionForegroundService.kt` | **NEW** |
| `transcription/TranscriptionViewModel.kt` | Inject + use controller |
| `di/Modules.android.kt` | Register controller in Koin |
| `di/Modules.ios.kt` | Register no-op controller in Koin |
| `service/AudioRecordingService.kt` | `START_STICKY`, null-intent guard, notification improvements |
| `shared/src/androidMain/AndroidManifest.xml` | New service + permission |
| `audio/ui/recorder/RecordingScreen.kt` | `BackHandler` + dialog |

---

## Out of Scope

- Option B (recording continues after Back) — reserved for future build
- Notification actions (Pause/Stop buttons in notification) — future enhancement
- `isRunning` static var persistence across process death — not needed; `START_STICKY` with null-intent guard handles the restart correctly
- Download tracking — already correctly implemented in `ModelDownloaderViewModel.checkTranscriptionAvailability()`
