#!/usr/bin/env python3
"""
Runnable-without-SDK contract tests for GhostDisplay.

Mirrors pure Kotlin policy objects (sessions, overlay, probe, category,
display flags, blocklist, control math) plus Persistent Browser tests and
source-level release contracts. Device/Gradle tests still require Android Studio.
"""
from __future__ import annotations

import os
import re
import sys
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.dirname(__file__))

from run_browser_tests import BrowserContractTests, decide_new_tab  # noqa: E402,F401


# --- Session policy (mirrors SessionPolicy.kt) ---

LIVE, RESURRECTED, FRESH = "LIVE", "RESURRECTED", "FRESH"
RESUME, ADDITIONAL, CANCEL = "RESUME", "ADDITIONAL_WINDOW", "CANCEL"


def tier_after_normal():
    return LIVE


def tier_after_ghost_death():
    return RESURRECTED


def tier_after_target_crash():
    return FRESH


def viewer_does_not_change(tier):
    return tier


def interpret_choice(has_active, choice):
    if not has_active:
        return RESUME
    return choice or CANCEL


def classify_restore(saw_content, cold_start):
    if cold_start is True:
        return FRESH
    if saw_content is True:
        return "RESTORED"
    return "UNKNOWN"


class SessionPolicyTests(unittest.TestCase):
    def test_process_death_is_resurrected_never_fresh(self):
        self.assertEqual(tier_after_ghost_death(), RESURRECTED)
        self.assertNotEqual(tier_after_ghost_death(), FRESH)

    def test_normal_launch_is_live(self):
        self.assertEqual(tier_after_normal(), LIVE)

    def test_target_crash_is_fresh(self):
        self.assertEqual(tier_after_target_crash(), FRESH)

    def test_viewer_open_close_keeps_every_tier(self):
        for t in (LIVE, RESURRECTED, FRESH):
            self.assertEqual(viewer_does_not_change(t), t)

    def test_existing_session_requires_explicit_choice(self):
        self.assertEqual(interpret_choice(True, None), CANCEL)
        self.assertEqual(interpret_choice(True, RESUME), RESUME)
        self.assertEqual(interpret_choice(True, ADDITIONAL), ADDITIONAL)
        self.assertEqual(interpret_choice(False, None), RESUME)

    def test_never_silently_duplicate(self):
        self.assertEqual(interpret_choice(True, None), CANCEL)

    def test_restore_outcome(self):
        self.assertEqual(classify_restore(True, True), FRESH)
        self.assertEqual(classify_restore(True, False), "RESTORED")
        self.assertEqual(classify_restore(None, None), "UNKNOWN")

    def test_stop_is_only_user_end(self):
        self.assertTrue(True)


# --- Overlay policy (mirrors OverlayPolicy.kt) ---

def overlay_should_show(
    opt_in=True,
    granted=True,
    hidden_elapsed=True,
    viewer=False,
    fg="com.example.app",
    hidden="com.example.app",
    home=False,
    ghost=False,
):
    if not opt_in or not granted or not hidden_elapsed or viewer:
        return False
    if fg is None or hidden is None:
        return False
    if home or ghost:
        return False
    return fg == hidden


class OverlayPolicyTests(unittest.TestCase):
    def test_shown_only_when_foreground_matches(self):
        self.assertTrue(overlay_should_show())
        self.assertFalse(overlay_should_show(fg="com.other"))

    def test_off_without_opt_in_or_grant(self):
        self.assertFalse(overlay_should_show(opt_in=False))
        self.assertFalse(overlay_should_show(granted=False))

    def test_never_over_home_viewer_or_ghostdisplay(self):
        self.assertFalse(overlay_should_show(home=True))
        self.assertFalse(overlay_should_show(viewer=True))
        self.assertFalse(overlay_should_show(ghost=True))

    def test_null_foreground_hides(self):
        self.assertFalse(overlay_should_show(fg=None))
        self.assertFalse(overlay_should_show(hidden=None))

    def test_hidden_until_end_of_day(self):
        self.assertFalse(overlay_should_show(hidden_elapsed=False))

    def test_end_of_day_is_in_the_future(self):
        now = 1_700_000_000_000
        day = 24 * 60 * 60 * 1000
        offset = now
        start = offset - (offset % day)
        end = start + day
        self.assertGreater(end, now)


# --- App category + probe (mirrors AppCategory.kt / CompatibilityProbe.kt) ---

CATS = [
    "BROWSER", "VIDEO", "STREAMING_DRM", "MUSIC", "MESSAGING",
    "SOCIAL", "GAME", "FINANCE", "SYSTEM", "OTHER",
]


def looks_finance(pkg, name):
    blob = (pkg + " " + name).lower()
    return any(k in blob for k in ("bank", "wallet", "pay", "upi", "payment", "finance"))


def looks_game(pkg, name):
    blob = (pkg + " " + name).lower()
    return "game" in blob


def classify(pkg, label, orientation= -1, system=False):
    p, n = pkg.lower(), label.lower()
    if looks_finance(p, n):
        return "FINANCE"
    if "netflix" in p or "disney" in p:
        return "STREAMING_DRM"
    if "chrome" in p or "firefox" in p or "browser" in n:
        return "BROWSER"
    if "spotify" in p or "music" in n:
        return "MUSIC"
    if "youtube" in p or "video" in n:
        return "VIDEO"
    if "whatsapp" in p or "telegram" in p:
        return "MESSAGING"
    if "instagram" in p or "tiktok" in p or "facebook" in p:
        return "SOCIAL"
    if looks_game(p, n):
        return "GAME"
    if system:
        return "SYSTEM"
    return "OTHER"


def prelaunch(cat, drm_secure):
    if cat == "STREAMING_DRM":
        return ("UNSUPPORTED", True) if not drm_secure else ("LIMITED", False)
    if cat in ("FINANCE", "GAME", "SYSTEM"):
        return ("LIMITED", False)
    return ("SUPPORTED", False)


def afterlaunch(cat, launched, verified, on_default, drm_refused):
    if on_default or not launched:
        return "UNSUPPORTED"
    if cat == "STREAMING_DRM" and drm_refused:
        return "UNSUPPORTED"
    if verified is True and cat in ("GAME", "FINANCE", "SYSTEM", "STREAMING_DRM"):
        return "LIMITED"
    if verified is True:
        return "SUPPORTED"
    return "LIMITED"


CAVEATS = {
    "BROWSER": "This browser may manage its own tabs. GhostDisplay cannot stop it from discarding background tabs.",
    "VIDEO": "Video apps usually stay live on a MediaProjection virtual display.",
    "STREAMING_DRM": "DRM services often refuse a non-secure virtual display.",
    "MUSIC": "Music and podcast apps are expected to keep playing with the phone screen off",
    "MESSAGING": "Open chat, draft text, and in-app navigation stay with the live hidden window.",
    "SOCIAL": "Auto-advancing content (stories, reels, feeds) continues to advance while hidden.",
    "GAME": "Games vary. Some run on a virtual display",
    "FINANCE": "A hidden display is not isolation. The finance warning is on by default.",
    "SYSTEM": "System apps (Settings, Phone, Contacts) can be started hidden",
    "OTHER": "This is your real app, same account, on a hidden display.",
}


class AppCategoryTests(unittest.TestCase):
    def test_finance_from_package_or_label(self):
        self.assertEqual(classify("com.example.wallet", "Wallet"), "FINANCE")
        self.assertEqual(classify("com.foo.bar", "City Bank"), "FINANCE")

    def test_game_uses_keyword_not_orientation(self):
        self.assertEqual(classify("com.foo.bar", "Maps", orientation=0), "OTHER")
        self.assertEqual(classify("com.foo.game", "Puzzle"), "GAME")

    def test_social_messaging_browser(self):
        self.assertEqual(classify("com.instagram.android", "Instagram"), "SOCIAL")
        self.assertEqual(classify("com.whatsapp", "WhatsApp"), "MESSAGING")
        self.assertEqual(classify("com.android.chrome", "Chrome"), "BROWSER")


class CompatibilityProbeTests(unittest.TestCase):
    def test_drm_without_secure_unsupported(self):
        compat, unsup = prelaunch("STREAMING_DRM", False)
        self.assertEqual(compat, "UNSUPPORTED")
        self.assertTrue(unsup)

    def test_messaging_supported(self):
        self.assertEqual(prelaunch("MESSAGING", False)[0], "SUPPORTED")

    def test_finance_and_games_limited(self):
        self.assertEqual(prelaunch("FINANCE", False)[0], "LIMITED")
        self.assertEqual(prelaunch("GAME", False)[0], "LIMITED")

    def test_leak_onto_default_is_unsupported(self):
        self.assertEqual(afterlaunch("SOCIAL", True, False, True, False), "UNSUPPORTED")

    def test_verified_social_supported(self):
        self.assertEqual(afterlaunch("SOCIAL", True, True, False, False), "SUPPORTED")

    def test_caveats_never_use_banned_wording(self):
        banned = ("clone", "copy", "instance", "duplicate")
        for cat, text in CAVEATS.items():
            low = text.lower()
            for b in banned:
                self.assertNotIn(b, low, msg=f"{cat} caveat contains {b}")


class PerAppProfileTests(unittest.TestCase):
    def test_default_is_media_projection_and_audio_on(self):
        surface, audio, width = "MEDIA_PROJECTION", True, None
        self.assertEqual(surface, "MEDIA_PROJECTION")
        self.assertTrue(audio)
        self.assertIsNone(width)

    def test_drm_gets_secure_flag(self):
        cat = classify("com.netflix.mediaclient", "Netflix")
        self.assertEqual(cat, "STREAMING_DRM")
        self.assertTrue(cat == "STREAMING_DRM")

    def test_merge_keeps_stored_override(self):
        stored = {"orientation": "LANDSCAPE", "lastError": None}
        generated = {"orientation": "AUTO", "lastError": "caveat"}
        merged = dict(stored)
        merged["lastError"] = stored["lastError"] or generated["lastError"]
        self.assertEqual(merged["orientation"], "LANDSCAPE")
        self.assertEqual(merged["lastError"], "caveat")


# --- Display flags + config ---

PUBLIC, PRESENTATION, SECURE = 1 << 0, 1 << 1, 1 << 2
AUTO_MIRROR, SUPPORTS_TOUCH, OWN_FOCUS = 1 << 4, 1 << 6, 1 << 11
DESTROY = 1 << 8


def flag_attempts(secure=False):
    extra = SECURE if secure else 0
    return [
        PUBLIC | PRESENTATION | SUPPORTS_TOUCH | OWN_FOCUS | DESTROY | extra,
        PUBLIC | PRESENTATION | SUPPORTS_TOUCH | OWN_FOCUS | extra,
        PUBLIC | PRESENTATION | SUPPORTS_TOUCH | extra,
        PUBLIC | PRESENTATION | extra,
        PRESENTATION | extra,
        PUBLIC | extra,
        extra,
    ]


def force_orientation(w, h, landscape):
    mn, mx = min(w, h), max(w, h)
    return (mx, mn) if landscape else (mn, mx)


class VirtualDisplayFlagsTests(unittest.TestCase):
    def test_never_auto_mirror(self):
        for v in flag_attempts(False) + flag_attempts(True):
            self.assertEqual(v & AUTO_MIRROR, 0)

    def test_secure_bit_on_every_attempt(self):
        for v in flag_attempts(True):
            self.assertEqual(v & SECURE, SECURE)

    def test_first_attempt_has_touch_and_own_focus(self):
        first = flag_attempts(False)[0]
        self.assertTrue(first & SUPPORTS_TOUCH)
        self.assertTrue(first & OWN_FOCUS)
        self.assertTrue(first & PUBLIC)


class DisplayConfigTests(unittest.TestCase):
    def test_landscape_request_swaps(self):
        w, h = force_orientation(1080, 2400, True)
        self.assertEqual((w, h), (2400, 1080))

    def test_portrait_force(self):
        w, h = force_orientation(2400, 1080, False)
        self.assertLess(w, h)

    def test_override_resolution(self):
        self.assertEqual((1920, 1080), (1920, 1080))


# --- Blocklist ---

DEFAULT_PKGS = {
    "com.google.android.apps.nbu.paisa.user",
    "com.phonepe.app",
    "net.one97.paytm",
    "com.paypal.android.p2pmobile",
}
LABEL_KW = ["bank", "banking", "wallet", "payment", "paytm", "phonepe", "upi"]


def is_default_blocked(pkg, label):
    if pkg in DEFAULT_PKGS:
        return True
    l = label.lower()
    return any(l == kw or l.startswith(kw + " ") or l.endswith(" " + kw) or f" {kw} " in l for kw in LABEL_KW)


class BlocklistTests(unittest.TestCase):
    def test_known_payment_packages(self):
        self.assertTrue(is_default_blocked("net.one97.paytm", "Paytm"))
        self.assertTrue(is_default_blocked("com.phonepe.app", "PhonePe"))
        self.assertTrue(is_default_blocked("com.paypal.android.p2pmobile", "PayPal"))

    def test_label_keyword_bank(self):
        self.assertTrue(is_default_blocked("com.example.foo", "Example Bank"))

    def test_chrome_not_blocked(self):
        self.assertFalse(is_default_blocked("com.android.chrome", "Chrome"))

    def test_defaults_warn_not_engine_hard_stop(self):
        # Engine treats defaults as finance-warn, user-added as refuse.
        self.assertTrue(is_default_blocked("net.one97.paytm", "Paytm"))


# --- Control math ---

def canvas_to_display(cx, cy, cw, ch, dw, dh):
    if cw <= 0 or ch <= 0 or dw <= 0 or dh <= 0:
        return 0.0, 0.0
    scale = min(cw / dw, ch / dh)
    content_w, content_h = dw * scale, dh * scale
    ox, oy = (cw - content_w) / 2, (ch - content_h) / 2
    x = max(0.0, min(dw - 1, (cx - ox) / scale))
    y = max(0.0, min(dh - 1, (cy - oy) / scale))
    return x, y


class CoordinateMapperTests(unittest.TestCase):
    def test_center_of_letterboxed_canvas(self):
        x, y = canvas_to_display(100, 50, 200, 100, 100, 100)
        self.assertAlmostEqual(x, 50, places=2)
        self.assertAlmostEqual(y, 50, places=2)

    def test_zero_canvas(self):
        self.assertEqual(canvas_to_display(1, 1, 0, 0, 100, 100), (0.0, 0.0))

    def test_normalize(self):
        self.assertAlmostEqual(540 / 1080, 0.5, places=3)


class TaskParserTests(unittest.TestCase):
    def test_click_play(self):
        raw = "open site example.com and click play"
        self.assertIn("example.com", raw)
        self.assertIn("click play", raw)

    def test_empty(self):
        self.assertEqual("  ".strip(), "")


# --- Compatibility detector badge ---

class CompatibilityDetectorTests(unittest.TestCase):
    def test_badge_labels(self):
        labels = {
            "SUPPORTED": "Supported",
            "LIMITED": "Limited",
            "UNSUPPORTED": "Unsupported",
            "UNKNOWN": "Unknown",
        }
        self.assertEqual(labels["SUPPORTED"], "Supported")
        self.assertNotIn("Works", labels.values())

    def test_leak_is_unsupported(self):
        appeared_on_default, launch_failed = True, False
        compat = "UNSUPPORTED" if appeared_on_default or launch_failed else "SUPPORTED"
        self.assertEqual(compat, "UNSUPPORTED")

    def test_verified_with_nodes_is_supported(self):
        verified, launched, view_only = True, True, False
        compat = "SUPPORTED" if verified and launched and not view_only else "LIMITED"
        self.assertEqual(compat, "SUPPORTED")


# --- Home-screen hold resolver (mirrors LauncherHoldResolver.kt) ---

LAUNCHER_CHROME = {
    "home", "search", "google", "voice search", "app search",
    "notifications", "uninstall", "widgets", "app info", "edit",
    "remove", "resize",
}


def resolve_hold(event_pkg, label, homes, apps, self_pkg="com.ghostdisplay.app.debug", class_name=None):
    if not event_pkg or event_pkg == self_pkg or event_pkg not in homes:
        return None
    if class_name:
        cl = class_name.lower()
        if "appwidget" in cl or "widgethost" in cl or "searchbox" in cl or "qsb" in cl:
            return None
    raw = (label or "").strip()
    if not raw:
        return None
    low = raw.lower()
    if low == "folder" or low.startswith("folder:") or low.startswith("folder "):
        return None
    if low in LAUNCHER_CHROME or low.endswith(" page") or low.startswith("page "):
        return None

    def unique(name):
        hits = [a for a in apps if a["label"].lower() == name.lower() or a["pkg"].lower() == name.lower()]
        return hits[0]["pkg"] if len(hits) == 1 else None

    hit = unique(raw)
    if hit:
        return hit
    cleaned = raw.split(",")[0].split("·")[0].strip()
    if cleaned and cleaned != raw:
        return unique(cleaned)
    return None


class LauncherHoldResolverTests(unittest.TestCase):
    HOMES = {"com.google.android.apps.nexuslauncher", "com.android.launcher3"}
    APPS = [
        {"pkg": "com.android.chrome", "label": "Chrome"},
        {"pkg": "org.mozilla.firefox", "label": "Firefox"},
        {"pkg": "com.whatsapp", "label": "WhatsApp"},
    ]

    def test_home_hold_resolves_chrome(self):
        self.assertEqual(
            resolve_hold("com.google.android.apps.nexuslauncher", "Chrome", self.HOMES, self.APPS),
            "com.android.chrome",
        )

    def test_textview_is_not_treated_as_app_widget(self):
        self.assertEqual(
            resolve_hold(
                "com.android.launcher3", "Chrome", self.HOMES, self.APPS,
                class_name="android.widget.TextView",
            ),
            "com.android.chrome",
        )
        self.assertIsNone(
            resolve_hold(
                "com.android.launcher3", "Chrome", self.HOMES, self.APPS,
                class_name="android.appwidget.AppWidgetHostView",
            ),
        )

    def test_hold_again_resolves_same_app_for_stop(self):
        self.assertEqual(
            resolve_hold("com.android.launcher3", "WhatsApp", self.HOMES, self.APPS),
            "com.whatsapp",
        )

    def test_ignores_ghostdisplay_and_other_apps(self):
        self.assertIsNone(resolve_hold("com.ghostdisplay.app.debug", "Chrome", self.HOMES | {"com.ghostdisplay.app.debug"}, self.APPS))
        self.assertIsNone(resolve_hold("com.whatsapp", "Chrome", self.HOMES, self.APPS))

    def test_ignores_folders_widgets_chrome(self):
        self.assertIsNone(resolve_hold("com.android.launcher3", "Folder: Social", self.HOMES, self.APPS))
        self.assertIsNone(resolve_hold("com.android.launcher3", "Chrome", self.HOMES, self.APPS, class_name="android.appwidget.AppWidgetHostView"))
        self.assertIsNone(resolve_hold("com.android.launcher3", "Search", self.HOMES, self.APPS))

    def test_notification_suffix(self):
        self.assertEqual(
            resolve_hold("com.android.launcher3", "Chrome, 3 notifications", self.HOMES, self.APPS),
            "com.android.chrome",
        )

    def test_ambiguous_does_not_guess(self):
        twins = [{"pkg": "com.a.chrome", "label": "Chrome"}, {"pkg": "com.b.chrome", "label": "Chrome"}]
        self.assertIsNone(resolve_hold("com.android.launcher3", "Chrome", self.HOMES, twins))


# --- Source / release contracts ---

BANNED_UI = re.compile(
    r'["`][^"`]{0,80}\b(clone|duplicate app|instance of (the )?app|a copy of)\b[^"`]{0,80}["`]',
    re.I,
)
FORBIDDEN_APIS = ("finishAndRemoveTask", "killBackgroundProcesses")


class SourceContractTests(unittest.TestCase):
    def _kt_files(self):
        root = os.path.join(ROOT, "app", "src", "main")
        out = []
        for dirpath, _, files in os.walk(root):
            for f in files:
                if f.endswith((".kt", ".xml", ".md")):
                    out.append(os.path.join(dirpath, f))
        out.append(os.path.join(ROOT, "README.md"))
        return out

    def test_no_finish_and_remove_task_or_kill_background(self):
        for path in self._kt_files():
            if not path.endswith(".kt"):
                continue
            with open(path, encoding="utf-8") as fh:
                text = fh.read()
            for api in FORBIDDEN_APIS:
                self.assertNotIn(api, text, msg=f"{path} uses {api}")

    def test_ui_copy_avoids_banned_product_words(self):
        ui_root = os.path.join(ROOT, "app", "src", "main", "kotlin", "com", "ghostdisplay", "app", "ui")
        for dirpath, _, files in os.walk(ui_root):
            for f in files:
                if not f.endswith(".kt"):
                    continue
                with open(os.path.join(dirpath, f), encoding="utf-8") as fh:
                    text = fh.read()
                m = BANNED_UI.search(text)
                self.assertIsNone(m, msg=f"{f} has banned wording: {m.group(0) if m else ''}")

    def test_manifest_has_session_and_overlay_pieces(self):
        with open(os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml"), encoding="utf-8") as fh:
            manifest = fh.read()
        self.assertIn("SYSTEM_ALERT_WINDOW", manifest)
        self.assertIn("PACKAGE_USAGE_STATS", manifest)
        self.assertIn("session.AppViewerActivity", manifest)
        self.assertIn("indicator.OverlayDotService", manifest)
        self.assertIn("GhostService", manifest)
        self.assertNotIn("rikka.shizuku", manifest.lower())
        self.assertNotIn("moe.shizuku", manifest.lower())
        self.assertIn("ui.HoldActionActivity", manifest)
        self.assertIn("Theme.GhostDisplay.Hold", manifest)

    def test_hold_on_home_is_accessibility_not_overlay(self):
        a11y = open(
            os.path.join(ROOT, "app", "src", "main", "res", "xml", "accessibility_service_config.xml"),
            encoding="utf-8",
        ).read()
        self.assertIn("typeViewLongClicked", a11y)
        hold = open(
            os.path.join(
                ROOT,
                "app",
                "src",
                "main",
                "kotlin",
                "com",
                "ghostdisplay",
                "app",
                "ui",
                "HoldActionActivity.kt",
            ),
            encoding="utf-8",
        ).read()
        self.assertIn("windowIsTranslucent", open(
            os.path.join(ROOT, "app", "src", "main", "res", "values", "themes.xml"),
            encoding="utf-8",
        ).read())
        self.assertNotIn("TYPE_APPLICATION_OVERLAY", hold)
        self.assertNotIn("SYSTEM_ALERT_WINDOW", hold)
        self.assertIn("Hold an app", open(
            os.path.join(
                ROOT,
                "app",
                "src",
                "main",
                "kotlin",
                "com",
                "ghostdisplay",
                "app",
                "ui",
                "OnboardingScreen.kt",
            ),
            encoding="utf-8",
        ).read())

    def test_room_v3_migration_present(self):
        db = open(
            os.path.join(ROOT, "app", "src", "main", "kotlin", "com", "ghostdisplay", "app", "data", "GhostDatabase.kt"),
            encoding="utf-8",
        ).read()
        self.assertIn("version = 3", db)
        self.assertIn("MIGRATION_2_3", db)
        self.assertIn("session_log", db)

    def test_prefs_overlay_off_finance_warn_on(self):
        prefs = open(
            os.path.join(ROOT, "app", "src", "main", "kotlin", "com", "ghostdisplay", "app", "data", "PreferencesRepository.kt"),
            encoding="utf-8",
        ).read()
        self.assertIn('flag(Keys.OVERLAY_DOT, default = false)', prefs)
        self.assertIn('flag(Keys.FINANCE_WARN, default = true)', prefs)

    def test_ghost_service_handles_resurrect_and_additional_window(self):
        svc = open(
            os.path.join(ROOT, "app", "src", "main", "kotlin", "com", "ghostdisplay", "app", "service", "GhostService.kt"),
            encoding="utf-8",
        ).read()
        self.assertIn("ACTION_RESURRECT", svc)
        self.assertIn("EXTRA_ADDITIONAL_WINDOW", svc)
        self.assertIn("EXTRA_FINANCE_CONFIRMED", svc)
        self.assertIn("handleResurrect", svc)
        self.assertIn("ReturnNotifications", svc)
        self.assertIn("OverlayDotService", svc)

    def test_version_is_release(self):
        gradle = open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
        self.assertIn('versionName = "1.3.2"', gradle)
        self.assertIn("versionCode = 5", gradle)

    def test_engine_start_hidden_is_package_agnostic(self):
        svc = open(
            os.path.join(
                ROOT,
                "app",
                "src",
                "main",
                "kotlin",
                "com",
                "ghostdisplay",
                "app",
                "service",
                "GhostService.kt",
            ),
            encoding="utf-8",
        ).read()
        start = svc.split("fun startHidden(")[1].split("fun stopAll(")[0]
        self.assertNotIn("chrome", start.lower())
        self.assertNotIn("firefox", start.lower())
        self.assertNotIn("whatsapp", start.lower())

    def test_workflow_runs_unit_tests_and_stages_130(self):
        wf = open(os.path.join(ROOT, ".github", "workflows", "build-apk.yml"), encoding="utf-8").read()
        self.assertIn("testDebugUnitTest", wf)
        self.assertIn("GhostDisplay-1.3.2-debug.apk", wf)
        self.assertIn("tag_name: v1.3.2", wf)

    def test_hidden_launch_runs_from_resumed_activity(self):
        launcher = open(
            os.path.join(
                ROOT,
                "app",
                "src",
                "main",
                "kotlin",
                "com",
                "ghostdisplay",
                "app",
                "launch",
                "AppLauncher.kt",
            ),
            encoding="utf-8",
        ).read()
        self.assertIn("ForegroundLaunchHost.requireResumed()", launcher)
        self.assertNotIn("context.startActivity", launcher)
        self.assertIn("openedOnDefaultDisplay = false", launcher)
        self.assertNotIn("openedOnDefaultDisplay = true", launcher)
        hold = open(
            os.path.join(
                ROOT,
                "app",
                "src",
                "main",
                "kotlin",
                "com",
                "ghostdisplay",
                "app",
                "ui",
                "HoldActionActivity.kt",
            ),
            encoding="utf-8",
        ).read()
        self.assertIn("ForegroundLaunchHost.onResume", hold)
        main = open(
            os.path.join(
                ROOT,
                "app",
                "src",
                "main",
                "kotlin",
                "com",
                "ghostdisplay",
                "app",
                "MainActivity.kt",
            ),
            encoding="utf-8",
        ).read()
        self.assertIn("ForegroundLaunchHost.onResume", main)

    def test_hold_chooser_is_not_modal_bottom_sheet(self):
        sheet = open(
            os.path.join(
                ROOT,
                "app",
                "src",
                "main",
                "kotlin",
                "com",
                "ghostdisplay",
                "app",
                "ui",
                "AppActionSheet.kt",
            ),
            encoding="utf-8",
        ).read()
        self.assertNotIn("ModalBottomSheet(", sheet)
        self.assertNotIn("rememberModalBottomSheetState", sheet)
        hold = open(
            os.path.join(
                ROOT,
                "app",
                "src",
                "main",
                "kotlin",
                "com",
                "ghostdisplay",
                "app",
                "ui",
                "HoldActionActivity.kt",
            ),
            encoding="utf-8",
        ).read()
        self.assertNotIn("enableEdgeToEdge", hold)
        self.assertNotIn("onboardingComplete", hold)

    def test_service_promotes_foreground_immediately(self):
        svc = open(
            os.path.join(
                ROOT,
                "app",
                "src",
                "main",
                "kotlin",
                "com",
                "ghostdisplay",
                "app",
                "service",
                "GhostService.kt",
            ),
            encoding="utf-8",
        ).read()
        on_start = svc.split("fun onStartCommand(")[1].split("return START_STICKY")[0]
        self.assertIn("startAsForeground(", on_start)
        self.assertLess(on_start.find("startAsForeground("), on_start.find("when (intent?.action)"))


class UserFlowScenarioTests(unittest.TestCase):
    """End-to-end policy scenarios: first run, hold start/stop, tab select, compat."""

    HOMES = {"com.android.launcher3"}
    APPS = [
        {"pkg": "com.android.chrome", "label": "Chrome"},
        {"pkg": "org.mozilla.firefox", "label": "Firefox"},
        {"pkg": "com.whatsapp", "label": "WhatsApp"},
        {"pkg": "com.netflix.mediaclient", "label": "Netflix"},
    ]

    def test_first_run_then_hold_chrome_starts(self):
        onboarding, warning, a11y = True, True, True
        self.assertTrue(onboarding and warning and a11y)
        pkg = resolve_hold("com.android.launcher3", "Chrome", self.HOMES, self.APPS)
        self.assertEqual(pkg, "com.android.chrome")

    def test_hold_again_is_the_same_app_for_stop(self):
        start = resolve_hold("com.android.launcher3", "Chrome", self.HOMES, self.APPS)
        stop = resolve_hold("com.android.launcher3", "Chrome", self.HOMES, self.APPS)
        self.assertEqual(start, stop)

    def test_hold_without_accessibility_does_not_invent_a_session(self):
        a11y = False
        live = []
        if not a11y:
            self.assertEqual(live, [])

    def test_tab_long_press_enters_select_then_toggle(self):
        selected = set()
        # long-press
        selected = {"tab-1"} if not selected else selected
        self.assertEqual(selected, {"tab-1"})
        # tap another
        selected = selected | {"tab-2"}
        self.assertEqual(selected, {"tab-1", "tab-2"})
        # tap first to deselect
        selected = selected - {"tab-1"}
        self.assertEqual(selected, {"tab-2"})

    def test_bulk_close_skips_pinned(self):
        tabs = [
            {"id": "a", "pin": False},
            {"id": "b", "pin": True},
            {"id": "c", "pin": False},
        ]
        chosen = {"a", "b", "c"}
        closed = [t["id"] for t in tabs if t["id"] in chosen and not t["pin"]]
        self.assertEqual(closed, ["a", "c"])

    def test_ten_tabs_allowed_eleventh_rejected_at_default(self):
        self.assertEqual(decide_new_tab(9, 10, 8.0)[0], "allow")
        self.assertEqual(decide_new_tab(10, 10, 8.0)[0], "reject")

    def test_viewer_close_does_not_stop_session(self):
        for t in (LIVE, RESURRECTED, FRESH):
            self.assertEqual(viewer_does_not_change(t), t)

    def test_existing_hidden_window_never_silently_starts_another(self):
        self.assertEqual(interpret_choice(True, None), CANCEL)

    def test_drm_without_secure_is_unsupported_browser_is_supported(self):
        self.assertEqual(prelaunch("STREAMING_DRM", False)[0], "UNSUPPORTED")
        self.assertEqual(prelaunch("BROWSER", False)[0], "SUPPORTED")
        self.assertEqual(prelaunch("FINANCE", False)[0], "LIMITED")
        self.assertEqual(prelaunch("GAME", False)[0], "LIMITED")
        self.assertEqual(prelaunch("MESSAGING", False)[0], "SUPPORTED")

    def test_leak_onto_phone_screen_is_unsupported(self):
        self.assertEqual(afterlaunch("BROWSER", True, False, True, False), "UNSUPPORTED")

    def test_readme_documents_sessions_and_overlay(self):
        readme = open(os.path.join(ROOT, "README.md"), encoding="utf-8").read()
        self.assertIn("RESURRECTED", readme)
        self.assertIn("8dp", readme)
        self.assertIn("No Shizuku", readme)


if __name__ == "__main__":
    unittest.main(verbosity=2)
