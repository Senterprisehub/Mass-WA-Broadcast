# Mass WA Broadcast

<p align="center">
  <img src="icon/icon.png" alt="Mass WA Broadcast app icon" width="128">
</p>

**Mass WA Broadcast** is an offline-first Android campaign and broadcast manager for WhatsApp and WhatsApp Business. It combines contact and template management, scheduled delivery, live recipient capture, accessibility-assisted sending, local campaign history, and Android OEM health controls in a single Jetpack Compose application.

> [!IMPORTANT]
> This project can interact with WhatsApp through Android automation and accessibility APIs. Use it only for recipients who have consented to receive messages and in accordance with WhatsApp Terms, applicable messaging policies, telecommunications rules, and local privacy law. The application does not grant permission to bypass WhatsApp restrictions or send unsolicited bulk messages.

## APK

The repository includes a generated APK build workflow. Successful `assembleDebug` or `assembleRelease` builds are synchronized to `/apk` with a versioned filename and SHA-256 manifest.

- Package: `com.aistudio.masswabroadcast.mwbxkp`
- Version: `1.4`
- Version code: `5`
- Minimum Android: 7.0 (API 24)
- Target/compile SDK: 36

The repository currently has no published `apk/` directory in the inspected tree. Build it locally or download a verified release artifact when one is available.

## Features

- **Campaign orchestration** — Create, schedule, launch, pause, resume, skip, stop, queue, and review broadcasts.
- **Offline local storage** — Room/SQLite stores contacts, groups, campaigns, templates, and broadcast logs without a required network backend.
- **Audience management** — Add contacts manually or import from the Android phonebook and WhatsApp's live chat/group picker.
- **Message templates** — Reuse templates with variables such as `{{name}}`, `{{first_name}}`, `{{company}}`, `{{phone}}`, `{{custom_tag}}`, and `{{date}}`.
- **Personalized dispatch** — Resolve campaign variables for each recipient and stage text/media attachments.
- **WhatsApp instance support** — Discover official WhatsApp, WhatsApp Business, and applicable clone/work-profile instances.
- **Scheduled broadcasts** — Exact alarms, boot re-arming, overlapping-campaign FIFO queueing, and missed-window handling.
- **Broadcast safeguards** — Incoming-call interruption, daily pacing estimates, recipient-level logs, and pause/resume controls.
- **Accessibility automation** — Dynamic package scoping limits automation to the active WhatsApp/clone instances while idle isolation keeps unrelated apps out of scope.
- **Encrypted credential vault** — Android Keystore AES-256-GCM storage for optional device and WhatsApp lock credentials.
- **OEM health guidance** — Battery, autostart, exact-alarm, notification, overlay, and accessibility checks for Xiaomi, Oppo/Realme/OnePlus, Vivo/iQOO, Samsung, and stock Android.
- **Responsive UI** — Adaptive Material 3 navigation uses a bottom bar on phones and a navigation rail on larger screens.
- **Privacy controls** — Log retention, accessibility auto-disable, backup exclusions, and local-only campaign data management.

## Screens

- Dashboard and live broadcast status
- Campaign list and campaign details
- Campaign creator with scheduling and media
- Contact and contact-group management
- Reusable message templates
- System health and application settings
- Accessibility and credential-vault dialogs
- Live WhatsApp recipient picker
- Lock-screen schedule alert overlay

## Technology Stack

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM, repository pattern, coroutines, and `StateFlow` |
| Local database | AndroidX Room / SQLite |
| Preferences | Jetpack DataStore Preferences |
| Security | Android Keystore, AES-256-GCM |
| Background work | Foreground service, AlarmManager, and boot receivers |
| Automation | Android Accessibility APIs |
| Media | FileProvider, staged campaign media, Coil |
| Build | Gradle Kotlin DSL + Android Gradle Plugin 9.1.1 |
| Testing | JUnit 4, Robolectric, Compose UI tests, Espresso |

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                 # Single-Activity Compose entry point
├── data/
│   ├── local/                      # Room entities, DAOs, and database migrations
│   ├── model/                      # Campaign, contact, log, and template models
│   └── repository/                 # Local persistence and campaign dispatch coordination
├── receiver/                       # Boot, alarm, and phone-call broadcast receivers
├── service/                        # Foreground scheduled-broadcast service
├── ui/
│   ├── activity/                   # Lock-screen schedule alert activity
│   ├── components/                 # Reusable Compose dialogs and controls
│   ├── screens/                    # Dashboard, campaigns, contacts, templates, settings
│   └── theme/                      # Material 3 theme and typography
├── util/                           # Scheduling, WhatsApp automation, health, crypto, and helpers
└── viewmodel/                      # Application state and UI orchestration

app/src/main/res/                   # Manifest resources, icons, themes, and service config
app/src/test/                       # JVM unit and stress tests
app/src/androidTest/                # Instrumented tests
public/assets/aistudio/             # Screenshots and architectural documentation
```

## Requirements

- Android Studio with Android SDK 36 support
- JDK 11 or newer
- Android 7.0 (API 24) or newer for supported builds
- WhatsApp or WhatsApp Business installed to use WhatsApp-specific workflows
- An Android device or emulator for Compose and instrumented testing

## Build

Clone the repository and open it as an Android Studio project:

```bash
git clone https://github.com/Senterprisehub/Mass-WA-Broadcast.git
cd Mass-WA-Broadcast
```

Debug build:

```bash
./gradlew assembleDebug
```

Release build:

```bash
./gradlew assembleRelease
```

Release builds expect signing variables or `my-upload-key.jks` according to `app/build.gradle.kts`:

```bash
export KEYSTORE_PATH=/absolute/path/to/my-upload-key.jks
export STORE_PASSWORD='your-store-password'
export KEY_PASSWORD='your-key-password'
./gradlew assembleRelease
```

Successful builds use `SyncApkToRootTask` to copy the APK to:

```text
apk/MassWABroadcast-v1.4.apk
```

The build task also creates `apk/build-manifest.json` with application ID, variant, version, size, and SHA-256 checksum.

## Run and Permissions

On first use, the app may request:

- **Contacts** — To import recipients from the device phonebook.
- **Notifications** — To show delivery, alert, and foreground-service status.
- **Phone state** — To pause broadcasts when an incoming or active call is detected.
- **Accessibility** — To automate WhatsApp recipient and send actions.
- **Exact alarms / full-screen alerts** — To deliver scheduled campaigns reliably.
- **Battery optimization exemption** — To reduce OEM/background execution restrictions.
- **Overlay permission** — For supported lock-screen schedule alerts.

Open **Settings → System Health** in the app to review each gate and device-specific guidance.

> [!NOTE]
> Android OEMs implement background restrictions differently. Accessibility services, exact alarms, lock-screen overlays, and battery controls may require manual confirmation on Samsung, Xiaomi/HyperOS, ColorOS/Realme, Vivo/OriginOS, and other devices.

## Testing

Run JVM tests:

```bash
./gradlew test
```

Run Android instrumented tests on a connected device or emulator:

```bash
./gradlew connectedAndroidTest
```

The test sources include coverage for:

- Indian phone-number normalization
- Pan-India stress scenarios
- Accessibility send-state logic
- Room and repository batch behavior
- AES-256-GCM vault security
- Boot grace and scheduling behavior
- Large-scale broadcast simulations
- OEM and device-state scenarios

## Privacy and Security

- Campaign and recipient data are stored locally with Room.
- Optional vault secrets are encrypted with Android Keystore and AES-256-GCM.
- Plaintext credential migration removes legacy plaintext keys.
- Accessibility events are dynamically scoped to WhatsApp and active broadcast/picker sessions where supported.
- The app can prune old broadcast logs based on a 30-, 90-, 180-day, or keep-all policy.
- Campaign media and database files can be excluded from Android backup and device-to-device transfer paths.

No cloud API is required for the core local broadcast workflow. Firebase and network dependencies are present but unused or commented in the current build configuration unless explicitly enabled.

## Responsible Use

Before adding bulk recipients:

1. Obtain explicit consent and maintain an opt-out mechanism.
2. Avoid deceptive, fraudulent, spam, or unauthorized messages.
3. Test with a small audience first.
4. Respect WhatsApp limits and the recipient's communication preferences.
5. Do not store or expose credentials in source control.
6. Test accessibility automation only on accounts you control.

## Contributing

Contributions are welcome. Please keep changes focused and include tests for scheduling, phone normalization, Room migrations, accessibility behavior, or security-sensitive code when applicable.

1. Fork the repository.
2. Create a feature branch.
3. Make your changes and add tests.
4. Run `./gradlew test`.
5. Open a pull request with a clear description and test evidence.

## License

No license is currently declared in this repository. By default, only the repository owner retains rights to reuse or distribute the source. Add a license file if you intend to permit open-source use.

## Support

Use the repository Issues page to report bugs or request features. Include Android version, OEM/device model, app version, relevant log entries, and steps to reproduce. Do not include passwords, vault secrets, private phone numbers, or WhatsApp session data in an issue.
