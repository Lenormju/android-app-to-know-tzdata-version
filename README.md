# Android tzdata version checker

Tiny Android app that shows the device's tzdata version, to compare before and after a reboot that applies a time zone update. Made for a Galaxy S10 (Android 12), no adb needed.

## What it shows

- ICU tzdata version: `android.icu.util.TimeZone.getTZDataVersion()`
- Content of `/apex/com.android.tzdata/etc/tz/tz_version`, format `<format>.<IANA version>.<revision>` (e.g. `004.2025b.001`). May show `n/a` if SELinux blocks the read.
- Default `java.util.TimeZone` id
- tzdata and i18n APEX versions, decoded by hand from `/apex/com.android.<name>/apex_manifest.pb` (`/apex/apex-info-list.xml` and the `/apex` listing are blocked by SELinux for apps)
- Header of `/apex/com.android.tzdata/etc/tz/tzdata` (e.g. `tzdata2022f`)
- Whether `/data/misc/zoneinfo/current/tzdata` is visible, size of `icu_tzdata.dat`, and the `icudt*.dat` file name in `/apex/com.android.i18n/etc/icu`
- Raw UTC offsets on 2025-07-15T12:00Z for Africa/Cairo and America/Asuncion from `java.util.TimeZone`, `android.icu.util.TimeZone` and `java.time.ZoneId` (API 26+), to see which data each API uses

Every probe prints `n/a: <exception>` if it fails, to show what is blocked.

## Install

GitHub Actions builds the debug APK on every push. In the Actions tab, download the artifact `tzdata-debug-apk`, unzip it and install the APK (allow unknown sources; Play Protect may warn).

## Use

Open the app and note the values, force-stop it, reboot, reopen and compare.

Limitation: it shows the active version only, not a staged (pending) update.

## Build locally

Needs JDK 17 and the Android SDK:

    ./gradlew assembleDebug

Output: `app/build/outputs/apk/debug/app-debug.apk`
