# Android tzdata version checker

Tiny Android app that shows the device's tzdata version, to compare before and after a reboot that applies a time zone update. Made for a Galaxy S10 (Android 12), no adb needed.

## What it shows

- ICU tzdata version: `android.icu.util.TimeZone.getTZDataVersion()`
- Content of `/apex/com.android.tzdata/etc/tz/tz_version`, format `<format>.<IANA version>.<revision>` (e.g. `004.2025b.001`). May show `n/a` if SELinux blocks the read.
- Default `java.util.TimeZone` id
- Version (package name, `versionName`, `longVersionCode`) of the tzdata and i18n (ICU) APEX modules, read via `PackageManager` with `MATCH_APEX` (API 29+, no permission). Shows `n/a: not visible` if the package is hidden.

## Install

GitHub Actions builds the debug APK on every push. In the Actions tab, download the artifact `tzdata-debug-apk`, unzip it and install the APK (allow unknown sources; Play Protect may warn).

## Use

Open the app and note the values, force-stop it, reboot, reopen and compare.

Limitation: it shows the active version only, not a staged (pending) update.

## Build locally

Needs JDK 17 and the Android SDK:

    ./gradlew assembleDebug

Output: `app/build/outputs/apk/debug/app-debug.apk`
