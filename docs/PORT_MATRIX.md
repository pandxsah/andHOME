# tvHOME → andHOME port matrix

Reference: the local TV Home README, feature guide, `SceneHome.java`, `Scenes.java`, `HomeDashboard.java`, original home photograph and bundled artwork. This is a phone adaptation with the same visual direction, not a claim that every inherited TV feature has been ported.

| tvHOME feature | andHOME 0.2 |
| --- | --- |
| Five Night Scenes, foreground depth, large clock | Ported artwork and depth composition, fitted to portrait. Glass panel textures retained. |
| Classic / Night Scenes looks | Implemented. |
| Glass dock, adjustable size, names, hiding and moving | Implemented for touch, capacity 3–7 with horizontal scrolling. |
| Home customization hub | Implemented: scenes, custom wallpaper, dock, grid, themes, widgets and hidden apps. |
| App widgets (tiles that launch installed apps) | Implemented through home pins with custom names/icons. |
| App drawer and search | Implemented for phone launchable activities in the current user. |
| Widget shelf and in-place removal | Horizontal shelf; add/remove/reorder in customization. |
| Music widget | Real sessions/artwork and supported transport actions. User enables notification access. Persistent last-played artwork is not implemented. |
| Weather / tomorrow forecast | Implemented with chosen city, cache and offline states. |
| Football | Optional real ESPN scoreboard, five leagues, cached results. Favorite teams and match-detail screens remain. |
| Prayer times | Optional AlAdhan card using selected city/time zone and MWL method. Method selection/countdown remain. |
| Latest movie/TV releases | Not yet ported. Needs a supported/licensed phone content provider and region-selection flow. No sample titles are displayed as real data. |
| Continue Watching / Nuvio / Stremio clouds | Not ported. Android TV Watch Next is unavailable as a general phone content source. Account integrations need a separate phone adapter and explicit user setup; no TV account tokens are copied. |
| Control Center | Functional phone settings panels, media volume, launcher brightness and recent apps. Does not replace Android's notification shade or Recents. |
| Profiles | Up to eight independently persisted local launcher profiles. |
| Icon editor | Choose a local image per app or restore original; rename/hide. Icon packs and drawing tools remain. |
| Custom wallpaper / shuffled wallpapers | Custom image and five scenes implemented. Folder/reddit fetching, automatic scene shuffle and full live wallpapers remain. |
| Cinematic presentation | Opt-in slow pan, depth masks, low-power/static behavior. This is not a system live-wallpaper service. |
| Backup/restore | Versioned all-profile JSON export/import, bounded/validated and explicitly confirmed. Custom image data is excluded. |
| Update from releases | Explicit GitHub release check, release notes and browser download handoff. No silent install. |
| On-screen crash report | Implemented locally on next launch; explicit copy/dismiss. |
| Remote mapper, TV input switching | TV hardware interactions have no matching phone launcher surface. Not carried over. |
| TV debloater / launcher disabler / ADB setup | Not carried over. Phone app-info and default-home settings are available; no apps are silently disabled or removed. |
| Lock screen / screensaver | Not ported. Android owns the phone lock screen; a separate optional ambient mode could be implemented later. |
| tvHOME companion / phone remote | Not implemented in tvHOME itself. Needs an authenticated paired protocol on both sides; tvHOME remains unchanged. |

Third-party Android widgets, folders, drag/drop, notification badges, work/private profiles, voice search and Android icon-pack support are also outside 0.2. These are not falsely represented by disabled buttons or fake data.
