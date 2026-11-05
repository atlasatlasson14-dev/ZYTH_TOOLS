# ZYTH TOOLS

Native Android utility app with a black/neon-purple interface.

## Features
- **APK installer:** choose an APK with Android's document picker and hand it to Android's official package installer. Android may ask you to allow installs from ZYTH TOOLS.
- **APK download:** fetches the latest APK release with Android's Download Manager, then opens the system installer.
- **Menu builder:** create named buttons, toggles, and integer sliders; validate slider ranges and export a versioned JSON layout.
- **AI MOD BUILDER:** inspect an APK's package/version metadata and basic Android API/native-ABI compatibility locally; edit buttons, toggles, sliders, and dropdowns with a live preview; export a JSON menu configuration; and check/open the public cloud build status.

The current AI MOD BUILDER does not call an AI model; its starter menu is generated locally from a fixed template. No model service or credentials are configured.

APK inspection only reads metadata and ABI entries on the device; it does not upload, install, modify, sign, or repack the selected APK. Compatibility checks are basic indicators, not a guarantee that an app will install or run.

The menu builder exports a `ZYTH_MENU_V1` JSON layout for source projects that explicitly implement that interface. APK upload alone cannot prove a compatible extension point, so the app does not insert menu code into arbitrary or protected APKs. Modifying third-party games can violate their rules and may result in account bans.

## Cloud build and APK download
The GitHub Actions workflow in `.github/workflows/android-build.yml` compiles a debug APK on pushes and manual runs. It saves a 30-day workflow artifact and, on the repository's default branch, publishes or updates the `latest` GitHub Release with the asset `ZYTH_TOOLS.apk`.

The **DOWNLOAD APK** button uses this repository's latest Release asset URL.

The workflow creates a debug-signed ZYTH TOOLS APK for sideloading/testing; it is not a Play Store release. The in-app cloud-build link opens the GitHub Actions workflow, which must be started by a signed-in repository user. It compiles ZYTH TOOLS source only, not an APK selected for inspection. Debug builds from separate hosted runners may not share a signing key, so upgrading a prior installation may require uninstalling it first.

## Android Studio build
Open this folder in Android Studio with Android SDK Platform 35 and Java 17 installed. Sync Gradle and build the `app` debug variant.

## Meta Quest 3S
This is a standard 2D Android app, not an immersive Quest app. Quest 3S may run compatible Android APKs, but installation can depend on headset settings, developer mode, package architecture, and Horizon OS policy. ZYTH TOOLS never installs silently; Android controls the install confirmation.
