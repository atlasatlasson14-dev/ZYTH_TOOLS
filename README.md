# ZYTH TOOLS

Native Android utility app with a black/neon-purple interface.

## Features
- **APK installer:** choose an APK with Android's document picker and hand it to Android's official package installer. Android may ask you to allow installs from ZYTH TOOLS.
- **APK download:** fetches the latest APK release with Android's Download Manager, then opens the system installer.
- **Menu builder:** create named buttons, toggles, and integer sliders; validate slider ranges and export a versioned JSON layout.

The menu builder is a layout/configuration exporter for your own app or a game that explicitly supports mods. It does not inject code, modify another app, bypass protections, or create cheats.

## Cloud build and APK download
The GitHub Actions workflow in `.github/workflows/android-build.yml` compiles a debug APK on pushes and manual runs. It saves a 30-day workflow artifact and, on the repository's default branch, publishes or updates the `latest` GitHub Release with the asset `ZYTH_TOOLS.apk`.

The **DOWNLOAD APK** button uses this repository's latest Release asset URL.

The workflow creates a debug-signed APK for sideloading/testing; it is not a Play Store release. Debug builds from separate hosted runners may not share a signing key, so upgrading a prior installation may require uninstalling it first.

## Android Studio build
Open this folder in Android Studio with Android SDK Platform 35 and Java 17 installed. Sync Gradle and build the `app` debug variant.

## Meta Quest 3S
This is a standard 2D Android app, not an immersive Quest app. Quest 3S may run compatible Android APKs, but installation can depend on headset settings, developer mode, package architecture, and Horizon OS policy. ZYTH TOOLS never installs silently; Android controls the install confirmation.
