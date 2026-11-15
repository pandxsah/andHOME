# Android capability boundaries

The core launcher experience uses the same code on Android 10 and newer. No artificial API-level feature tiers.

- **Default home:** Android requires the user's role selection; declaring HOME does not silently make an app default. OEM launcher gestures/Recents animations can remain tied to the system launcher. andHOME does not replace SystemUI or Recents.
- **App visibility:** Android 11+ filters package queries. The manifest queries MAIN/LAUNCHER activities, not every installed package. The MVP enumerates current-user launchable activities only. Disabled/uninstalled apps and apps without a launch activity are not shown. Work/private profiles need explicit future LauncherApps/user-profile support; private space also has role/permission requirements on applicable versions.
- **Control Center:** ordinary apps cannot freely toggle protected settings. Android 10+ restricts direct Wi-Fi toggles. Open system settings/panels where required. Global brightness writes require user-granted special access; Bluetooth and related permissions vary by Android version. A panel inside the launcher is possible on API 29; replacing the notification shade is not a normal launcher capability.
- **Music:** reading other apps' active media sessions normally requires user-enabled notification listener access. Each player controls available transport commands. A HOME role alone does not grant media access.
- **Colors/blur:** Android 12 introduced dynamic system palettes and RenderEffect. Static palettes and lightweight compositing can preserve the look on API 29 without those APIs. This MVP uses consistent custom palettes on every supported version.
- **Widgets/background:** third-party widgets require an AppWidgetHost and explicit binding. Weather/sports refresh must respect background scheduling, battery limits and provider terms. No exact real-time delivery promise.
- **Wallpapers:** cinematic animation must pause when not visible and offer a static/low-power option. Full live wallpapers require separate service and user selection.
- **Backup/integration:** MVP automatic OS backup is disabled. Future explicit exports must omit secrets. tvHOME pairing requires a compatible protocol and separate tvHOME changes; no companion connection is currently attempted.

Primary references:

- https://developer.android.com/reference/android/app/role/RoleManager
- https://developer.android.com/training/package-visibility/declaring
- https://developer.android.com/about/versions/10/privacy/changes#configure-wifi
- https://developer.android.com/reference/android/media/session/MediaSessionManager
- https://developer.android.com/develop/ui/views/appwidgets/host
- https://developer.android.com/develop/background-work/background-tasks/persistent-work
