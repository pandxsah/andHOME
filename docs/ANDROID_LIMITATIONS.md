# Android phone capability boundaries

The core experience uses the same implementation on Android 10 and newer; no arbitrary version-based feature removal.

- **Default home:** the user must choose the HOME role. OEM gesture/Recents integrations can remain tied to the system launcher. andHOME cannot replace SystemUI, the system lock screen or all gesture animations as a normal app.
- **Apps:** MAIN/LAUNCHER package queries enumerate current-user launchable activities. No broad all-package permission. Work/private profiles need separate LauncherApps integration; apps without launcher activities are not listed.
- **Controls:** Internet, Bluetooth, airplane and sound controls open Android panels/settings where protected. Media volume uses normal AudioManager access. The brightness slider changes only andHOME's window; global device brightness remains in system display settings.
- **Music:** an enabled notification listener is required to read other apps' active sessions. Players control available actions. Notification access is optional; disabling it removes media state. No notification messages are stored.
- **Watching content:** Android TV's Watch Next and preview-channel providers cannot serve as a universal phone Continue Watching feed. Independent account/provider integrations are still required.
- **Scenes:** background/foreground compositing and preblurred textures run on API 29. Custom photos have no automatic foreground extraction. Cinematic mode is a launcher animation, not a live-wallpaper service. No RenderEffect requirement.
- **Network cards:** weather/football/prayer data requires Internet and provider availability. Cached data is labeled, refresh is lifecycle-bound, and there is no promise of exact background or real-time updates. ESPN's endpoint is public but not a stable contracted API. Open-Meteo's free endpoint requires non-commercial use.
- **Profiles:** launcher preferences only; not Android users, device-level privacy boundaries or work profiles.
- **Backup:** explicit JSON export only, excluding photo/icon files, recent usage, media metadata and credentials. Android cloud/device-transfer backup is excluded. Import replaces all profiles only after user confirmation and validation.
- **Companion:** pairing/remote control needs tvHOME-side support, explicit trust and an authenticated protocol. No TV changes or unsolicited connections are made.

Primary references:

- https://developer.android.com/reference/android/app/role/RoleManager
- https://developer.android.com/training/package-visibility/declaring
- https://developer.android.com/reference/android/provider/Settings.Panel
- https://developer.android.com/reference/android/media/session/MediaSessionManager
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness
- https://open-meteo.com/en/docs
- https://open-meteo.com/en/docs/geocoding-api
- https://aladhan.com/prayer-times-api
