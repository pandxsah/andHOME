# andHOME

A separate native Android smartphone launcher inspired by tvHOME's calm, cinematic home screen. Kotlin and Jetpack Compose. Android 10+ (`minSdk 29`), compile/target SDK 36, application ID `app.andhome.launcher`.

## MVP

- User-approved default HOME role, with system settings fallback.
- Installed launchable apps, app icons, locale-sorted drawer and case-insensitive search. Keyboard search launches a single matching result.
- Home grid (3–5 columns), five-app dock, long-press edit menu, pin/move/remove controls.
- System, dark and light themes; persisted layout, theme and column settings.
- Package-change and resume refresh; missing apps are hidden while pins are retained.
- Local-first, no network permission, no analytics. Future features are interfaces, not active integrations.

## Build

Install JDK 17 and Android SDK platform 36 / build-tools 35.0.0. Set `JAVA_HOME` and `ANDROID_HOME`, or configure `sdk.dir` in an untracked `local.properties`.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

macOS/Linux: `sh ./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest`.

The wrapper pins Gradle 8.13; AGP 8.13.2 and Kotlin 2.2.21 are pinned. First build requires Internet access. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Try it

1. Install the debug APK on an Android 10+ phone or emulator (`adb install -r app-debug.apk`).
2. Open andHOME and tap **Set as default home**. Android asks you to confirm the launcher choice.
3. Open **All apps**, hold an app, then pin to home or dock. Hold a home app or tap **Edit** to move/remove pins.
4. Use **Settings** for theme and grid columns. Relaunch to verify saved choices.
5. Switch back through Android **Settings → Apps → Default apps → Home app** at any time.

## Verification

Pure layout unit tests cover transfers, duplicate prevention, full-dock safety, reorder bounds and removal. Instrumentation tests cover persistence recreation and the drawer/search/edit/theme flow. Run `./gradlew connectedDebugAndroidTest` with a connected test device. See `docs/VALIDATION.md` for the actual results of this build and the device acceptance checklist.

See `docs/ARCHITECTURE.md` for future integrations and `docs/ANDROID_LIMITATIONS.md` for platform boundaries. tvHOME was inspected read only and remains untouched.
