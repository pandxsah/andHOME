# andHOME 0.2 architecture

The Android phone project remains separate from tvHOME. The scene artwork has now been ported with upstream notices; the phone UI and integration code are implemented in Kotlin / Compose. No tvHOME source files were changed.

## Presentation

`SceneComponents.kt` loads tvHOME scene backgrounds and depth masks off the UI thread. The clock is composed between background and foreground layers. Small preblurred scene textures tint translucent panels on all supported Android versions, avoiding an API 31 blur dependency. Slow scene drift is opt-in and pauses outside STARTED lifecycle state, when battery saver is observed or when system animation scale is zero. Custom photos are decoded/downsampled into private files; their clock has no automatic depth cutout.

`Launcher.kt` owns home/drawer/customization/Control Center navigation. The home has a horizontal widget shelf above a horizontal dock, and a scrollable app grid when apps are pinned. Native HOME intents dismiss overlays and return to home. The clock area handles touch swipes; all functions also have visible buttons and accessible text labels.

`Widgets.kt` composes real provider states, media, battery broadcasts and launcher-tracked recent apps. Long press opens a remove confirmation. `CustomizeScreen.kt` hosts appearance, widget order, profiles, hidden apps, file import/export and update checks. `ControlCenter.kt` uses normal phone APIs and settings panels.

## State and persistence

`HomeLayout` is immutable, with pure move/pin/hide/dock-size rules. Stable component names distinguish multiple app activities. `HomeViewModel` owns state and delegates app discovery to `AppsRepository`; app enumeration runs off the main thread. Recents record launches from andHOME only, not device-wide usage.

`SettingsRepository` stores up to eight profiles in one versioned JSON preference and migrates the original 0.1 preferences. A profile contains layout, look/scene, theme, widget order, label/icon overrides, hidden apps, selected city and league. Settings apply immediately in memory and are serialized by SharedPreferences. Profiles change launcher preferences, not Android users or app data.

`LayoutCodec` centralizes decoding and defaults. Restore enforces a 256 KB limit, schema version, profile count/ID uniqueness, coordinate ranges, component syntax and entry counts before any state replacement. Portable exports omit local image filenames and recent history. Android cloud/device-transfer backup is explicitly excluded; explicit portable export is user initiated. Photo/icon input is bounded at 20 MB and downsampled; private filenames are validated before decoding. Import must never accept arbitrary filesystem paths.

## Integrations

- `MediaBridge` binds active media sessions to controller callbacks. An optional notification listener supplies access. It reads media metadata and advertised actions, never notification bodies. Playback buttons disable when the player does not support the action. Access revocation clears state.
- `WeatherRepository` uses Open-Meteo geocoding and forecasts over HTTPS, with deadlines, response bounds, cache and offline UI. City/coordinates/time zone are chosen explicitly. Weather refreshes on foreground lifecycle and every 15 minutes while enabled/visible.
- `FeedRepository` adapts the football and prayer providers also used by tvHOME. Football refreshes every five minutes, prayer data hourly, while their cards are composed and the activity is visible. Data is cached and marked saved on network failure. ESPN's public feed has no stability guarantee. AlAdhan currently uses a fixed MWL calculation method.
- `UpdateChecker` checks only this repository's latest published release on explicit request. The user opens its GitHub page in a browser for installation; no silent package installation.
- `AndHomeApplication` preserves a bounded last crash report privately and delegates to Android's original crash handler. The next launch offers copy/dismiss. No report is uploaded.

`features/FeatureContracts.kt` remains the proposed boundary for integrations still outside this release, especially authenticated tvHOME pairing and a real live-wallpaper service. It is not evidence those features are implemented.

## Compatibility

`minSdk 29`, compile/target SDK 36. There are no artificial feature tiers. API 33 broadcast registration is guarded; the core experience and scene compositing work on Android 10. Third-party AppWidgetHost support, work/private profiles, cloud watching integrations and TV-specific APIs remain separate work (see PORT_MATRIX.md).
