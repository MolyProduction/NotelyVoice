# Background Operation & Notifications – Design Spec

**Datum:** 2026-03-15
**Branch:** german-fork
**Status:** Approved

---

## Übersicht

Diese Spec beschreibt vier zusammenhängende Änderungen, die MolyEcho besser für den Hintergrundbetrieb optimieren:

1. Android-Permissions für Notifications und Akku-Whitelist
2. Neue Onboarding-Seite zur Berechtigungsanfrage
3. Angepasste letzte Onboarding-Seite mit direkter Navigation zur Modellauswahl
4. Download-Abschluss-Notification

---

## 1. Permissions & Manifest

### Neue Einträge in `shared/src/androidMain/AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

### Laufzeit-Handling

**POST_NOTIFICATIONS** (dangerous permission, API 33+):
- Wird per `ActivityResultContracts.RequestPermission` in `MainActivity.kt` registriert
- Lambda `onRequestNotificationPermission: () -> Unit` wird von `MainActivity.kt` nach `App()` weitergegeben
- Ergebnis (granted/denied) aktualisiert `MutableState<Boolean> isNotificationGranted` in `MainActivity.kt`
- Dieser Boolean wird als Parameter an `App()` → `OnboardingWalkthrough()` durchgereicht

**REQUEST_IGNORE_BATTERY_OPTIMIZATIONS** (keine dangerous permission):
- Wird per `startActivity(Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))` geöffnet
- Lambda `onRequestBatteryOptimization: () -> Unit` von `MainActivity.kt` nach `App()`
- Status `isBatteryOptimizationDisabled: Boolean` wird in `MainActivity.kt` via `PowerManager.isIgnoringBatteryOptimizations` berechnet und als `MutableState<Boolean>` gehalten
- Wird ebenfalls als Parameter durchgereicht

### Parameterkette

```
MainActivity.kt
  → App(
      isNotificationGranted: Boolean,
      isBatteryOptimizationDisabled: Boolean,
      onRequestNotificationPermission: () -> Unit,
      onRequestBatteryOptimization: () -> Unit
    )
  → OnboardingWalkthrough(
      isNotificationGranted: Boolean,
      isBatteryOptimizationDisabled: Boolean,
      onRequestNotificationPermission: (() -> Unit)?,
      onRequestBatteryOptimization: (() -> Unit)?
    )
```

Alle neuen Parameter sind nullable/optional mit Defaults (`null` / `false`), um die Signatur rückwärtskompatibel zu halten.

---

## 2. Neue Onboarding-Berechtigungsseite (Seite 3)

### Position im Flow

```
Seite 0: Welcome (MolyEchoWelcomePage)
Seite 1: Feature 1 (OnboardingPageContent)
Seite 2: Feature 2 (OnboardingPageContent)
Seite 3: Berechtigungen (PermissionsOnboardingPage) ← NEU
Seite 4: Modellübersicht (ModelOverviewPage)
```

`pageCount` wird von 4 auf 5 erhöht.

### UI-Aufbau (`PermissionsOnboardingPage`)

Implementiert in `OnboardingWalkthrough.kt` (commonMain). Layout von oben nach unten:

- **Icon:** MolyEcho-App-Icon, 88dp, ContentScale.Fit
- **Titel:** „Im Hintergrund aktiv" (28sp, Bold, PoppingsFontFamily, zentriert)
- **Erklärtext:** „Transkriptionen können je nach Aufnahmelänge einige Minuten dauern. MolyEcho kann dabei im Hintergrund weiterarbeiten – du wirst benachrichtigt, sobald das Ergebnis bereit ist." (16sp, Normal, `Color(0xFF555555)`, zentriert)
- **Karte 1 – Benachrichtigungen** (auf Basis von `ModelInfoCard`, angepasst mit Button):
  - Linker Balken: MolyGreen
  - Text: „Benachrichtigungen" (SemiBold) + „Damit du informiert wirst, wenn eine Transkription abgeschlossen ist."
  - Button rechts: „Erlauben" (MolyGreen) wenn `!isNotificationGranted`, „Erteilt ✓" (ausgegraut, disabled) wenn gewährt
- **Karte 2 – Akku-Optimierung** (gleiche Struktur):
  - Text: „Akku-Optimierung" (SemiBold) + „Verhindert, dass Android MolyEcho im Hintergrund stoppt."
  - Button rechts: „Deaktivieren" (MolyGreen) wenn `!isBatteryOptimizationDisabled`, „Deaktiviert ✓" (ausgegraut, disabled) wenn bereits whitegelistet

### Strings

Neue Einträge in `values/strings.xml` (EN) und `values-de/strings.xml` (DE):

| Key | EN | DE |
|-----|----|----|
| `onboarding_permissions_title` | „Background Operation" | „Im Hintergrund aktiv" |
| `onboarding_permissions_desc` | „Transcriptions can take a few minutes depending on recording length. MolyEcho can continue working in the background – you'll be notified when the result is ready." | „Transkriptionen können je nach Aufnahmelänge einige Minuten dauern. MolyEcho kann dabei im Hintergrund weiterarbeiten – du wirst benachrichtigt, sobald das Ergebnis bereit ist." |
| `onboarding_permission_notifications_title` | „Notifications" | „Benachrichtigungen" |
| `onboarding_permission_notifications_desc` | „So you're informed when a transcription is complete." | „Damit du informiert wirst, wenn eine Transkription abgeschlossen ist." |
| `onboarding_permission_battery_title` | „Battery Optimization" | „Akku-Optimierung" |
| `onboarding_permission_battery_desc` | „Prevents Android from stopping MolyEcho in the background." | „Verhindert, dass Android MolyEcho im Hintergrund stoppt." |
| `onboarding_permission_allow` | „Allow" | „Erlauben" |
| `onboarding_permission_granted` | „Granted ✓" | „Erteilt ✓" |
| `onboarding_permission_disable` | „Disable" | „Deaktivieren" |
| `onboarding_permission_disabled` | „Disabled ✓" | „Deaktiviert ✓" |

### Verhalten

- Der „Weiter"-Button am unteren Rand ist immer aktiv – Berechtigungen sind optional
- Tippt der Nutzer „Erlauben": `onRequestNotificationPermission?.invoke()` → System-Dialog erscheint → nach Rückkehr aktualisiert sich der Button-Zustand
- Tippt der Nutzer „Deaktivieren": `onRequestBatteryOptimization?.invoke()` → System-Einstellungen öffnen sich → nach Rückkehr aktualisiert sich der Button-Zustand

---

## 3. Letzte Onboarding-Seite – Navigation zur Modellauswahl

### Textänderung in `onboarding_models_subtitle`

| | Alt | Neu |
|---|---|---|
| DE | „Wähle in den Einstellungen das Modell, das zu dir passt." | „Wähle jetzt gleich dein Modell aus – du wirst direkt zur Modellauswahl weitergeleitet." |
| EN | „Choose the model that fits you best in Settings." | „Choose your model right now – you'll be taken directly to the model selection." |

### Neuer Callback in `OnboardingWalkthrough`

```kotlin
fun OnboardingWalkthrough(
    onFinish: () -> Unit = {},
    onFinishToModelSelection: () -> Unit = {},  // NEU
    ...
)
```

Der „Los geht's"-Button auf Seite 4 (letzte Seite) ruft `onFinishToModelSelection()` auf.
„Überspringen"-Button und Swipe-Navigation rufen weiterhin `onFinish()`.

### Flag in `App.kt`

```kotlin
var openModelSelectionOnLaunch by remember { mutableStateOf(false) }

OnboardingWalkthrough(
    onFinish = { viewmodel.onCompleteOnboarding() },
    onFinishToModelSelection = {
        openModelSelectionOnLaunch = true
        viewmodel.onCompleteOnboarding()
    },
    ...
)

is OnboardingState.Completed ->
    NoteAppRoot(platformUiState, openModelSelection = openModelSelectionOnLaunch)
```

### Navigation in `NoteAppRoot`

```kotlin
fun NoteAppRoot(platformUiState: PlatformUiState, openModelSelection: Boolean = false) {
    val navController = rememberNavController()
    LaunchedEffect(openModelSelection) {
        if (openModelSelection) {
            navController.navigate(Routes.LanguageModelSelection)
        }
    }
    ...
}
```

Back-Stack-Ergebnis: NoteList → ModelSelection. Zurück landet auf der Notizliste.

---

## 4. Download-Abschluss-Notification

### Implementierungsort

`shared/src/androidMain/kotlin/.../platform/Downloader.android.kt`

### Neue Methode `postDownloadCompleteNotification()`

Aufgerufen wenn `finalStatus == DownloadManager.STATUS_SUCCESSFUL` in `trackDownloadProgress`.

Ablauf:
1. Notification-Channel `download_done_channel` erstellen (Importance: DEFAULT)
2. `PendingIntent` zur `MainActivity` (analog zu `TranscriptionForegroundService`)
3. Notification mit Auto-Cancel, Titel + Text aus Ressourcen
4. Vor dem Posten auf Android 13+: `ContextCompat.checkSelfPermission(POST_NOTIFICATIONS)` prüfen – bei Fehlen wird die Notification still übersprungen

### Notification-IDs (vollständige Übersicht)

| ID | Verwendung |
|----|------------|
| 1 | Aufnahme läuft (AudioRecordingService) |
| 2 | Transkription läuft (TranscriptionForegroundService) |
| 3 | Transkription abgeschlossen (TranscriptionForegroundService) |
| 4 | Download abgeschlossen (Downloader) ← NEU |

### Neue Strings in `values/strings.xml` und `values-de/strings.xml`

| Key | EN | DE |
|-----|----|----|
| `notification_download_done_title` | „Model downloaded" | „Modell heruntergeladen" |
| `notification_download_done_text` | „MolyEcho is ready for transcription" | „MolyEcho ist bereit zur Transkription" |
| `notification_download_done_channel_name` | „Download complete" | „Download abgeschlossen" |

---

## Dateien die geändert werden

| Datei | Änderung |
|-------|----------|
| `shared/src/androidMain/AndroidManifest.xml` | +2 permissions |
| `shared/src/androidMain/kotlin/.../MainActivity.kt` | Permission-Launcher, State, Callbacks |
| `shared/src/commonMain/kotlin/.../App.kt` | Neue Parameter, Flag-Logik, `NoteAppRoot`-Parameter |
| `shared/src/commonMain/kotlin/.../onboarding/ui/OnboardingWalkthrough.kt` | pageCount 4→5, neue Seite, neue Callbacks |
| `shared/src/commonMain/composeResources/values/strings.xml` | +10 neue Strings |
| `shared/src/commonMain/composeResources/values-de/strings.xml` | +10 neue Strings |
| `shared/src/androidMain/res/values/strings.xml` | +3 neue Notification-Strings |
| `shared/src/androidMain/res/values-de/strings.xml` | +3 neue Notification-Strings |
| `shared/src/androidMain/kotlin/.../platform/Downloader.android.kt` | +`postDownloadCompleteNotification()` |

---

## Nicht im Scope

- iOS-spezifische Anpassungen (MolyEcho ist reine Android-App)
- Änderungen an bestehendem Transkriptions-Notification-Flow
- Persistierung des Permission-Status in DataStore
- In-App-Permission-Erklärungsdialog (Rationale) – System-Dialog reicht
