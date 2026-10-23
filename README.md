# Shizuku

Shizuku is a utility that allows normal applications to use system APIs directly with elevated permissions (root or ADB / Wireless ADB).

This custom build includes enhanced Wireless ADB pairing, direct on-screen code entry, and VR / headset (e.g., Meta Quest) optimization.

## Key Features & Enhancements

- **Direct On-Screen Wireless ADB Pairing**:
  - Enter the 6-digit pairing code and port directly on the step-by-step setup screen without relying on system notifications.
  - Built-in mDNS service discovery automatically detects the local ADB pairing port.
- **VR & Headset / Android TV Friendly (Meta Quest)**:
  - Removed notification dependencies for pairing, making Wireless ADB setup seamless on devices where system notification inputs are limited or unavailable (such as Meta Quest headsets or Android TV).
- **Smart Developer Options Navigation & Guidance**:
  - Automatically detects whether Developer Options are enabled (`Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`).
  - **Enabled**: Opens Developer Options directly via `com.android.settings.Settings$DevelopmentSettingsDashboardActivity`.
  - **Disabled**: Changes the action button to "Settings" (opening system Settings app info `com.android.settings`) and displays clear step-by-step guidance on how to enable Developer Options (About device -> tap Build number 5 times -> System -> Developer options -> Wireless debugging).
- **Automatic `WRITE_SECURE_SETTINGS` & Auto-Enabling Developer Options**:
  - Once Shizuku receives access, it automatically grants `android.permission.WRITE_SECURE_SETTINGS` to Shizuku Manager in the background and sets `development_settings_enabled = 1`.

## Background

When developing apps that require root or ADB permissions, the traditional method is running commands in a `su` or `adb` shell (e.g., `pm enable/disable`).

That method has significant disadvantages:
1. **Extremely slow** (Creating multiple shell processes)
2. **Super unreliable** (Parsing text output)
3. **Limited** to available command-line tools

Shizuku uses a completely different approach. It acts as an IPC bridge, passing binder requests from client applications directly to system services with root/ADB privileges.

## User Guide & Official Info

<https://shizuku.rikka.app/>

## How Does Shizuku Work?

Android uses `binder` for interprocess communication (IPC) between app processes and system service processes (e.g. `PackageManagerService`). `Binder` allows the server side to verify the client's `uid` and `pid`.

Shizuku guides users to start a background service (`shizuku_server`) via root or ADB once per boot. When client applications launch, a binder to the Shizuku server is shared with them. Shizuku acts as a middleman, executing higher-permission system API calls on behalf of authorized applications with almost zero overhead.

## Developing Shizuku Itself

### Build

- Clone with submodules:
  ```bash
  git clone --recurse-submodules https://github.com/RikkaApps/Shizuku.git
  ```
- Build Debug APK:
  ```bash
  ./gradlew :manager:assembleDebug
  ```

Compiled APK output path:
`manager/build/outputs/apk/debug/shizuku-v13.6.0.r1091.b844bc49-debug.apk`

## License

All code files in this project are licensed under the Apache 2.0 License.

Under Apache 2.0 section 6:
* You are **FORBIDDEN** from using `manager/src/main/res/mipmap*/ic_launcher*.png` image files, unless for displaying Shizuku itself.
* You are **FORBIDDEN** from using `Shizuku` as your app name or using `moe.shizuku.privileged.api` as application ID.
