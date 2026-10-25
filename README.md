# GhostDisplay

GhostDisplay **1.2.0** — a standalone native Android app whose **primary mode is Persistent Browser**: a WebView tab engine hosted on a hidden `VirtualDisplay` that keeps **at least 10 tabs alive, rendering, and playing media** until you close them. Installed browsers (Chrome, Firefox, …) are a secondary “External Browser” mode and **cannot** guarantee this — they own their own tab discard policy.

It can also launch any installed app on an invisible virtual display (no floating window, split-screen, PiP, or mirroring).

**No Shizuku. No root. No ADB. No companion app. No secondary service to install.**

The only user actions after installing the APK are:

1. Normal runtime permission grants (notifications, microphone for audio routing)
2. One built-in Settings toggle for **Accessibility**
3. The standard **MediaProjection** (screen-capture) consent dialog, when a session needs it

GhostDisplay does **not** record or exfiltrate frames. The optional debug preview is shown only when you open it.

---

## Install and run

### Requirements

- Android 10 (API 29) or later; compiled against / targeting API 34
- Android Studio Ladybug (or newer) with JDK 17 and the Android 34 SDK
- A physical device is strongly recommended. Emulators often cap virtual displays.

### Open the project

```bash
git clone <this-repo> GhostDisplay
# Open the GhostDisplay folder in Android Studio
# Let Gradle sync (AGP 8.5.2, Gradle 8.7, Kotlin 2.0.21)
```

If `gradle/wrapper/gradle-wrapper.jar` is missing, Android Studio will generate the wrapper on first sync, or run `gradle wrapper` from a machine that already has Gradle 8.7.

### Build and install

From Android Studio: **Run → Run ‘app’**.

From the command line (once the wrapper jar is present):

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug application id is `com.ghostdisplay.app.debug`. The release id is `com.ghostdisplay.app`.

There is no extra binary, Magisk module, Shizuku service, or `adb shell` step.

---

## One-time permissions (exact list)

GhostDisplay asks for nothing beyond what Android already exposes to ordinary apps.

| Grant | Where | Required? | What it is for |
|---|---|---|---|
| **Accessibility → GhostDisplay control** | Setup → “Open Accessibility settings”, or Settings → Accessibility | For *control*. Launch still works without it. | Node-based click / type / scroll on the virtual display. The only control path. |
| **Notifications** (`POST_NOTIFICATIONS` on API 33+) | Runtime prompt | Yes, for the foreground service | Persistent “\<app\> running hidden” notification with **Stop** |
| **Ignore battery optimizations** | Setup → system dialog (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) | Recommended | Keeps the hidden session alive |
| **Microphone** (`RECORD_AUDIO`) | Runtime prompt | Only for audio routing | Android reuses this permission for `AudioPlaybackCaptureConfiguration` |
| **MediaProjection** (screen-capture dialog) | Shown when you tap **Run hidden** if the profile needs it | For video, secure surfaces, audio routing | Creates a capture-capable virtual display. **Frames are not saved.** |
| **Notification listener** | Settings → Notification access | Optional | Drops shade notifications from a hidden app when that app’s toggle is on |
| **Usage access** (`PACKAGE_USAGE_STATS`) | Setup → Usage access | Optional, overlay only | Lets the 8dp return-indicator dot know which real app is in the foreground |
| **Draw over other apps** (`SYSTEM_ALERT_WINDOW`) | Setup → overlay permission, then the overlay toggle | Optional, **off by default** | 8dp draggable return-indicator dot — not a floating app window |

Not requested, not used: root, Shizuku, `WRITE_SECURE_SETTINGS`, `DUMP`, `INJECT_EVENTS`, ADB. Overlay is opt-in only.

---

## Persistent Browser (primary)

This is the only way GhostDisplay can guarantee no auto-discard, no background throttling, and full session persistence. We do **not** drive Chrome.

### How to use

1. Open GhostDisplay → **Browser**.
2. Tap **Start persistent browser**. Grant notifications / battery exemption as in Setup. MediaProjection is optional (needed to *route* hidden audio; tabs still play without it).
3. Tap **Open in Viewer**. Create tabs (default cap 10, hard cap 16). Load mixed sites — static, YouTube, music, a web game, a social feed.
4. Play video in one tab, switch to another, **close the Viewer**, use a different app, lock the phone. Every tab stays live. Audio keeps mixing. Closing Viewer does not call `WebView.onPause()`, `pauseTimers()`, `destroy()`, or `GONE`.
5. Come back hours later: the Viewer shows every tab where you left it.
6. Force-stop the app: reopen it. All **tab records** restore automatically (URL, title, scroll, form snapshot, back stack, cookies/localStorage/IndexedDB/service workers via the shared non-incognito WebView profile). If RAM is too tight, a tab is **“paused by system, tap to resume”** — that is the only non-live state, and the record is never dropped.
7. Manual close (with confirmation) is the **only** way a tab disappears.

### Restore guarantees

| Event | What happens |
|---|---|
| Close Viewer | Nothing pauses. Tabs keep rendering and playing. |
| Screen off / other app | Foreground service + `PARTIAL_WAKE_LOCK`. Media continues. |
| Process death / force-stop | Room records rebuild every tab. Cookies survive. Playback position may reset. |
| Reboot + “Restore after reboot” | Engine and tabs come back automatically (no MediaProjection until you grant it again for audio routing). |
| OS low memory | Extra tabs park as “paused by system”. **Never deleted.** |
| Kill switch / Stop engine | WebViews released; **records kept** and restored on next start. |

There is no tab eviction, no swipe-to-close without confirm, no inactive-tab cleanup.

### Memory guidance

- Default 10 tabs, configurable to 16 (warning at 9+ and 11+).
- `android:largeHeap="true"`. Each live tab is estimated ~150 MB (lite mode ~80 MB).
- Devices with **under 4 GB RAM** will likely struggle past 8 video-heavy tabs. The OS may kill the process; tabs restore, playback position may reset.
- Per-tab **lite mode** reduces cache/pre-raster cost. Memory bar is on Viewer and Browser home.

### Per-site caveats

- **DRM** (Netflix, Disney+, etc.) may refuse a virtual display or a non-visible tab. Per-tab warning + optional **keep-visible shim** (`document.hidden` / `visibilityState`). The shim is **best-effort** and cannot override Widevine.
- Some players still pause; enable the shim on that tab.
- **External Browser** (installed Chrome/Firefox on a virtual display): the browser may discard background tabs. GhostDisplay cannot prevent that. The Apps tab states this plainly.

### Test checklist (Persistent Browser)

- [ ] Create 10 tabs with mixed sites (static, YouTube, music, web game, social).
- [ ] Play video in tab 3, switch to tab 7, close Viewer, open another app: tab 3 audio continues, tab 7 keeps running.
- [ ] Lock the phone 30 minutes, unlock, reopen Viewer: all 10 present, state intact.
- [ ] Force-stop from system settings, reopen: all 10 records restored, logins intact (cookies).
- [ ] Close tab 5 with confirmation: only tab 5 dies; the other 9 keep running.
- [ ] No tab disappears without that confirm (except OS kill → restore, not loss).
- [ ] Still no Shizuku / root / ADB / companion app.

```
./gradlew :app:testDebugUnitTest
python3 scripts/run_all_tests.py
python3 scripts/run_browser_tests.py
```

## How hidden-app mode works

```
App picker ──► GhostService (FGS specialUse | mediaProjection)
                  │
                  ├─ VirtualDisplayEngine
                  │     DisplayManager.createVirtualDisplay  (ImageReader surface)
                  │     or MediaProjection.createVirtualDisplay
                  │     flags: PUBLIC | PRESENTATION | SUPPORTS_TOUCH | OWN_FOCUS
                  │     (hidden bits passed as integers; fallback ladder on failure)
                  │
                  ├─ AppLauncher
                  │     1. ActivityOptions.setLaunchDisplayId
                  │     2. Launch, then ActivityManager.moveTaskToDisplay (reflection)
                  │     3. Clear “unsupported on this device” — never silent
                  │
                  ├─ AudioRouter  (playback capture → AudioTrack)
                  │
                  └─ PARTIAL_WAKE_LOCK
                         │
                         ▼
              GhostAccessibilityService  (node-first, gesture fallback)
                         │
                         ▼
              Remote Control activity / optional debug preview
```

Layers are intentionally separate so they can be unit-tested without a device:

| Layer | Types | Tests |
|---|---|---|
| Persistent Browser | `TabManager`, `TabPolicy`, `PersistentWebViewHost`, `SessionRestorer`, `ViewerActivity`, `MemoryBudgetMonitor`, `VisibilityShimInjector` | `TabPolicyTest`, `TabManagerTest`, `UrlAndShimTest` |
| Display | `VirtualDisplayEngine`, `DisplayConfig`, `DisplayConfigResolver`, `VirtualDisplayFlags`, `GhostSurface` | `DisplayConfigResolverTest`, `VirtualDisplayFlagsTest` |
| Launch | `AppLauncher`, `AppCatalog`, `CompatibilityDetector`, `KnownLimits` | `CompatibilityDetectorTest`, `KnownLimitsTest` |
| Sessions | `SessionManager`, `SessionPolicy`, `ViewerReattach`, `AppViewerActivity`, `RoomSessionStore` | `SessionPolicyTest` |
| Profile | `PerAppProfileManager`, `CompatibilityProbe`, `AppCategory` | `CompatibilityProbeTest`, `AppCategoryTest` |
| Return indicator | `ReturnNotifications`, `OverlayDotService`, `OverlayPolicy`, `ForegroundAppDetector` | `OverlayPolicyTest` |
| Control | `AccessibilityController`, `CoordinateMapper`, `TaskModeParser`, `GhostAccessibilityService` | `CoordinateMapperTest`, `TaskModeParserTest` |
| Audio | `AudioRouter` | (device-only; needs MediaProjection) |
| Safety | `Blocklist`, `GhostNotificationListenerService` | `BlocklistTest` |
| Runtime | `GhostService`, `MediaProjectionHolder`, `SessionRepository` | — |

Capability limits are **always** written onto the session card (`CapabilityReport.notices`). The app never pretends a fallback succeeded.

---

## Per-app compatibility matrix

Runtime detection always wins. This is what we typically see on stock Android 12–14 without privileged identity.

| Category | Badge | Notes |
|---|---|---|
| Browser (Chrome, Firefox, …) | Supported / Limited | Most reliable hidden-launch target. Node control usually works once Accessibility can see the virtual-display window. Installed browsers may still discard their own tabs. |
| Video player (VLC, local players) | Supported with MediaProjection | Use MediaProjection surface mode. Audio via playback capture; continues with the screen off. |
| Streaming (YouTube) | Limited | Needs MediaProjection. May pause if the app decides it is not in the foreground. |
| Streaming (Netflix / Disney+ / Widevine) | Limited / Unsupported | DRM often refuses a virtual display. You may get black video + audio, or nothing. Secure flag is tried; it is not magic. |
| Game (landscape) | Limited | Virtual display is sized landscape from `requestedOrientation`. Many engines ignore virtual displays or need real touch injection, which Android does not grant third-party apps. May be view-only. |
| Social / messaging | Supported / Limited | The real app, same account, on a hidden display. Still uses network and notifications unless you enable the filter. |
| Banking / payments | Limited (warn) | Warns before start (toggle on by default). A hidden display is not isolation. |

OEM policy is the main variable. Some devices ignore `setLaunchDisplayId` for non-system apps and open the target on the **physical** screen. GhostDisplay checks Accessibility windows, and if the app leaked onto display 0 it is marked **Unsupported** with an explicit notice — it will not fail silently.

---

## First-run flow

1. Three onboarding pages: nothing shows on your screen; control is Accessibility; hidden is not isolated.
2. Warning screen you must accept.
3. **Setup** checklist with one-tap deep links.
4. **Apps** — searchable launcher list of every launchable app, **Run hidden** per row, 6dp live badge, gear for per-app settings, compatibility badge (Supported / Limited / Unsupported).
5. **Sessions** — tier (LIVE / RESURRECTED / FRESH), notices, Open Viewer, Remote Control, Stop, kill switch, session log.
6. Silent grouped return notifications (`IMPORTANCE_MIN`) plus the persistent FGS notification with Stop.
7. Optional 8dp overlay return-indicator dot (opt-in, off by default).

Per-app settings: resolution, DPI, orientation, surface mode (ImageReader vs MediaProjection), secure display, audio, block notifications. Defaults match the physical display and MediaProjection.

### Persistent app sessions

Every installed launchable app can run as a **hidden window of the real app** (same package, account, data, version) on a `VirtualDisplay`. The home-screen icon is never intercepted: tapping it opens a second window of that real app on the main display. Return via **Open Viewer** or the return indicator.

| Tier | Meaning |
|---|---|
| **LIVE** | Hidden task is alive. Viewer reattaches; the target is not restarted. |
| **RESURRECTED** | GhostDisplay’s process died. The VirtualDisplay is recreated and the same real app is relaunched so it can restore via `savedInstanceState`. Never labeled FRESH just because GhostDisplay died. |
| **FRESH** | The target app crashed or the user force-stopped it. |

Stop is the only user end. Closing the Viewer, locking the phone, or memory pressure must not end the session. GhostDisplay never calls `finishAndRemoveTask`, never stops the VirtualDisplay while a session is active, and never force-stops the target.

Tapping **Run hidden** on an app that already has a live hidden window asks **Resume / Start additional window / Cancel** — never silently starts another window.

Return indicator:

- In-app 6dp badge + “Running hidden · LIVE/RESURRECTED”
- Silent grouped `IMPORTANCE_MIN` notifications (default on) with per-app Stop
- 8dp overlay dot (opt-in, off) only while that real app is foreground on the main display. Tap opens Viewer; long-press hides until end of day.

---

## Test checklist

Use a physical device. Grant Accessibility + battery exemption before the launch tests.

- [ ] **Install APK only.** No Shizuku, no root, no `adb shell`, no second app.
- [ ] **Chrome hidden.** Tap Run hidden on Chrome. The physical screen does **not** show Chrome. You can open another app and use it normally. Session card says Running hidden (or Limited/Unsupported with a reason).
- [ ] **Video / streaming audio, screen off.** Profile: MediaProjection + audio on. Launch a player, start playback via Remote Control or before hiding, turn the screen off. Audio continues on speaker / Bluetooth. Mute toggle on the session card silences the router.
- [ ] **Landscape game.** A game with `requestedOrientation=landscape` gets a landscape virtual display (`width > height` on the session card).
- [ ] **Remote Control.** Where the accessibility tree exists: tap on the canvas clicks, inject text uses `ACTION_SET_TEXT`. If nodes and gestures cannot target the virtual display, the screen says **VIEW-ONLY on this device** — not a silent no-op.
- [ ] **Stop.** Stop on the session card **and** Stop on the notification fully release the VirtualDisplay, wake lock, MediaProjection, audio loop, and foreground service. No leftover “running hidden” notification.
- [ ] **Finance warning.** A banking/wallet package warns (does not hard-stop). User-added blocklist packages still refuse launch.
- [ ] **Open Viewer** reattaches the live VirtualDisplay. Closing it does not stop the hidden window or change the tier.
- [ ] **Run hidden** on an app that already has a live session shows Resume / Start additional window / Cancel.
- [ ] **Process death.** Force-stop GhostDisplay, reopen: sessions come back as RESURRECTED, not FRESH.
- [ ] **Return notifications.** Silent grouped “running hidden” rows with Stop. No heads-up, no sound.
- [ ] **Overlay dot.** Off until Setup toggle + overlay grant. Appears only over the matching real app; never over home, GhostDisplay, or Viewer. Long-press hides for the day.
- [ ] **Kill switch** stops every session.

Unit tests (no device):

```bash
./gradlew :app:testDebugUnitTest
python3 scripts/run_all_tests.py
```

---

## Known platform limits (surfaced, never swallowed)

- `VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH` and `OWN_FOCUS` are `@hide`. We pass the integer bits and fall back if `createVirtualDisplay` throws.
- `ActivityManager.moveTaskToDisplay` is a hidden system API. Reflection is attempted; on production devices it is usually blocked. Step 2 of the launch ladder then reports that it could not aim a task id (Android no longer gives third-party apps other apps’ task ids).
- `AccessibilityWindowInfo.getDisplayId()` exists only from API 30. On API 29 we cannot verify which display a window is on, and we say so.
- `dispatchGesture` has **no public display id**. If it cannot target the virtual display, Remote Control becomes view-only with an explicit banner.
- System Back / Home / Recents are **not** display-scoped. Remote Control will not fire them unless you flip “Allow system actions”, and that toggle warns they may hit the physical screen.
- MediaProjection consent **cannot** survive reboot or process death. Restore is an opt-in notification, never an automatic relaunch.
- Some apps set `allowAudioPlaybackCapture="false"`; they stay silent in the router.

---

## Project layout

```
app/src/main/kotlin/com/ghostdisplay/app/
  display/     VirtualDisplayEngine, DisplayConfig, DebugPreviewActivity
  launch/      AppLauncher, AppCatalog, CompatibilityDetector, KnownLimits
  session/     SessionManager, SessionPolicy, ViewerReattach, AppViewerActivity
  profile/     PerAppProfileManager, CompatibilityProbe, AppCategory
  indicator/   ReturnNotifications, OverlayDotService, OverlayPolicy, ForegroundAppDetector
  control/     GhostAccessibilityService, AccessibilityController, RemoteControlActivity
  audio/       AudioRouter
  service/     GhostService, MediaProjectionHolder, restore worker
  data/        Room profiles / sessions / session_log / blocklist, DataStore prefs
  safety/      Blocklist, NotificationListenerService
  ocr/         optional ML Kit fallback (in-memory, never written to disk)
  ui/          AppPicker, PerAppSettings, ActiveSessions, Setup, onboarding
```

minSdk 29, targetSdk 34, Kotlin, Jetpack Compose, coroutines/Flow, Room, DataStore, WorkManager, Foreground Service `specialUse` (+ `mediaProjection` when that consent is held).
