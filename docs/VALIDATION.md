# andHOME 0.2 validation

Validation date: 15 November 2026. Development build for Android phones, not TV installation.

## Environment

Windows, JDK 17.0.21, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, Android SDK 36 / build-tools 35.0.0. A separate headless Pixel 2 phone emulator runs Android 10 / API 29. All device commands explicitly target `emulator-5580`; no user phone or TV is modified.

## Checks

```text
assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest
```

The build and verification record is delivered with the APK. Tests include:

Final result: build succeeded; **10 JVM tests and 7 API 29 device tests passed**, with zero failures/skips. Android lint completed with **0 errors and 12 advisory warnings** (dependency update notices and Kotlin extension suggestions). APK v2 signature and binary minimum/target SDK values were verified: version 0.2.0, minSdk 29, targetSdk 36.

- JVM layout tests: duplicate prevention, home/dock transfer, full-dock safety, ordering, hide behavior and dock resize without losing pins.
- JVM input limits: complete allowed import payloads and rejection above the bound.
- Device UI: search/no-results, theme navigation and profile creation/deletion.
- Device persistence: profile isolation, settings recreation, backup round-trip, rejected imports leaving existing data intact, portable export exclusions and migration of 0.1 preferences.
- Phone screenshots reviewed against tvHOME's home photograph: portrait scene/clock/depth, glass panels, horizontal shelf and dock.

Automated ActivityScenario tests run with the stock launcher selected. Default-HOME behavior is checked separately: assign andHOME's HOME role, press Home, and verify resolution to `app.andhome.launcher/.MainActivity`. A test teardown run with andHOME already selected as HOME encountered an ActivityScenario destruction timeout; this is recorded as a test-fixture limitation rather than ignored.

## Still requiring real-phone coverage

Media session artwork/actions across Spotify/YouTube Music/other players, revoking notification access, file-picker permissions across providers, screen cutouts and gesture navigation, 200% font scaling/TalkBack, power saver/motion transitions, Android 11–16 behavior and OEM default-home dialogs. Online feeds and update checks also depend on live provider responses; unit/UI tests do not establish long-term provider reliability.

## Phone acceptance

1. Install on Android 10+ and select andHOME as default. Return Home from another app.
2. Open All apps, search, launch, pin, move, rename, hide/show, choose a custom icon and restore the original icon.
3. Choose all five scenes and a custom photo. Test Classic, themes, motion/battery saver and landscape.
4. Swipe the widget shelf; configure a city; enable media access; test supported playback actions. Enable optional cards and verify cached/offline states.
5. Resize the dock and verify overflow moves to home. Create a second profile and confirm independent layout/theme/widget preferences.
6. Export a backup, change settings, import with explicit confirmation; try a malformed/oversized file and confirm nothing is replaced.
7. Force-stop/reopen and reboot. Confirm profiles, home pins, hidden apps and settings persist.
8. Switch back through Android Settings → Apps → Default apps → Home app.

Current scope and remaining parity work are listed in `PORT_MATRIX.md`. Do not interpret the scene UI port as completed parity for TV-only/account integrations.
