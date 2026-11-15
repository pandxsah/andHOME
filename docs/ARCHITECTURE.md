# andHOME foundation

Native Kotlin / Jetpack Compose, Android 10+ (API 29). This is an independent implementation; no tvHOME code or assets were copied.

## Current boundaries

- `MainActivity`: HOME role request, lifecycle refresh, package change receiver and system bars. HOME intents return to the main screen and exit editing/search.
- `AppsRepository`: launcher activities in the current Android user, stable component IDs, icons, locale-aware sorting, explicit activity launching. No broad installed-package permission.
- `HomeLayout`: pure immutable pin/move/remove rules; a five-app dock, no duplicate pins across home and dock. Home is scrollable; grid column count is configurable.
- `SettingsRepository`: versioned local SharedPreferences with JSON arrays, default recovery for malformed lists/theme. Unavailable apps are hidden without deleting their pins, allowing restored/reenabled apps to return. New apps are available in the drawer, not automatically pinned.
- `HomeViewModel`: state owner; app loading off the main thread; layout/theme mutations persist immediately. A layout is seeded only once when apps first become available.
- `Launcher`: home, search drawer, edit menu and settings; lifecycle-aware state collection; system/dark/light schemes; insets and keyboard handling. Edit supports pin, move earlier/later, transfer between home/dock, and remove. Drag-and-drop is a later interaction enhancement.

## Planned extension points

`features/FeatureContracts.kt` defines interfaces only. These are architecture seams, not implemented features or fake data feeds.

| Feature | Implementation direction and boundary |
| --- | --- |
| Music | MediaSessionManager + user-enabled NotificationListenerService. Start/stop listeners with lifecycle; model no active session and revoked access. Transport controls only where supported by the active player. |
| Weather/sports | Separate WidgetProvider adapters and repositories, user-selected location/teams, cached snapshots and stale/offline states. API keys stay outside source. Refresh with WorkManager within OS limits. |
| Profiles | ProfileRepository owns named launcher layouts and active profile ID. These are launcher preferences; they do not create Android users/work profiles. |
| Control Center | In-app panel through ControlCenterGateway. Prefer Android Internet/settings panels for restricted controls; ask for special access only when that action needs it. Never assume system UI replacement. |
| Cinematic wallpapers | WallpaperController separates static fallback, balanced animation and cinematic quality. Respect reduced motion, battery saver, visibility and device performance. Live wallpaper service is a distinct future component. |
| Backup/restore | BackupService + versioned BackupEnvelope, Storage Access Framework export/import. Validate schema, limits, duplicate/malformed IDs; preview missing apps; replace atomically. No tokens, media-session data or companion credentials in export. |
| tvHOME companion | TvHomeCompanion pairing and transport adapter. One-time expiring pairing code, authenticated encrypted channel, scoped commands and revocation. Protocol and tvHOME support must be agreed separately. No implicit discovery or unsolicited remote control. |
| Third-party Android widgets | AppWidgetHost with explicit bind/configure flows and persisted widget IDs. Distinct from first-party provider cards. |

## tvHOME context inspected (read only)

The existing local tvHOME source checkout and its README were inspected read only.
README describes an Android TV launcher with glass dock, clock, app drawer, customizable widget row, media sessions, profiles, wallpapers and Control Center. It uses plain SDK shell builds and is a fork with upstream license obligations. andHOME carries over the design direction, not the TV-specific focus navigation, code, bundled assets, updater or debloater. No tvHOME files were modified.

## Deliberate MVP limits

Current-user launchable activities only; no work/private profile integration, shortcuts, folders, multiple home pages, drag/drop, icon packs, notification badges, widget hosting, live wallpaper, online services, backup UI or companion transport yet. No Internet, notification, location, accessibility or overlay permissions are requested. All core MVP behavior uses API 29-compatible APIs. Platform-specific work must be capability-checked and offer a fallback on API 29 rather than arbitrarily removing features.
