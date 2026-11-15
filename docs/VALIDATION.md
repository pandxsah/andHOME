# Build and phone acceptance

Validation date: 15 November 2026.

## Build environment

Windows, Microsoft JDK 17.0.21, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.2.21, Android SDK platform 36, build-tools 35.0.0. Toolchain and caches are isolated under the chat workspace's `work/toolchain`, outside the source deliverable.

Build task set:

```text
assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

## Results

- Debug launcher APK compiled successfully.
- Five JVM layout tests passed: zero failures, errors or skips.
- Android lint completed with zero errors and nine advisory warnings: six dependency-version notices, two optional Kotlin extension suggestions and one backup compatibility advisory. The manifest explicitly disables legacy backup and references Android 12+ cloud/device-transfer exclusions; the backup advisory remained in the incremental report. Pinned dependency versions are intentional for this initial build.
- Instrumentation test APK compiled successfully. Its two tests cover settings persistence and the search/edit/theme navigation flow. These tests were **not executed** on a phone or emulator.
- APK signature verified with Android `apksigner` (v2 signing). Binary manifest checked with `aapt`: package `app.andhome.launcher`, version `0.1.0`, minimum SDK 29, target SDK 36.
- Source archive excludes toolchains, generated build directories, local caches, keystores and Git internals. Project repository: https://github.com/pandxsah/andHOME.

No APK was installed on any device during validation, no launcher role was changed and no tvHOME files were modified. Smartphone runtime behavior remains to be verified by installing the APK on a phone.

## Phone acceptance checklist

Use Android 10+ on a phone. Recommended coverage: API 29, API 30 and API 36, both gesture navigation and three-button navigation.

1. Install and open andHOME. Confirm the app drawer shows launchable apps and launches the selected app.
2. Tap Set as default home. Confirm Android's choice dialog works; test both declining and accepting. Press Home from another app and verify andHOME opens at its home screen.
3. Search using mixed case; clear the query; search for no matches; use the keyboard Search action with one result.
4. Long-press an app in the drawer, pin to home, transfer it to dock, reorder and remove. Fill the dock with five apps; verify a sixth cannot displace another pin.
5. Switch system/dark/light theme and grid columns. Force-stop/reopen and reboot; confirm layout and settings remain.
6. Install, disable, reenable and uninstall a test app. Confirm the drawer updates and unavailable pins disappear without a crash.
7. Open drawer/settings/edit mode, press Home, and confirm a clean home screen. Press Back at home and confirm the launcher stays available.
8. Check portrait/landscape, display cutouts, keyboard, 200% font scale and TalkBack labels. Test on a lower-memory Android 10 phone.
9. Restore the previous launcher through Android Settings → Apps → Default apps → Home app.

For automated device tests, connect a **phone/emulator** and select that device explicitly using `ANDROID_SERIAL` before `gradlew connectedDebugAndroidTest`; do not run against an unrelated connected TV.

## Files to start with

- `app/src/main/AndroidManifest.xml`: HOME entry points and launcher package visibility.
- `MainActivity.kt`: role selection and activity/package lifecycle.
- `Launcher.kt`: Compose home, dock, drawer, search, edit and settings.
- `HomeViewModel.kt`: app loading and state coordination.
- `data/`: immutable layout rules, persistence and installed-app access.
- `features/FeatureContracts.kt`: future capability interfaces.
- `docs/ARCHITECTURE.md`: extension plan and tvHOME context.
- `docs/ANDROID_LIMITATIONS.md`: Android version and privilege boundaries.

This is a debug-signed MVP for device evaluation. Drag-and-drop, third-party widgets and the planned online/companion features are not implemented in version 0.1.0.
