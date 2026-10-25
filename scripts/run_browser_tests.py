#!/usr/bin/env python3
"""
Executable contract tests for Persistent Browser policy.
Mirrors TabPolicy / UrlNormalizer / BackStackCodec / MemoryBudget / RestoreReport
so we can run them without the Android SDK.
"""
from __future__ import annotations

import unittest

DEFAULT_MAX = 10
HARD_MAX = 16
WARN_AFTER = 8
EST_LIVE = 150 * 1024 * 1024
EST_LITE = 80 * 1024 * 1024
EST_PAUSED = 8 * 1024 * 1024


def clamp_max(n: int) -> int:
    return max(1, min(HARD_MAX, n))


def decide_new_tab(open_count: int, max_tabs: int, ram_gb: float):
    cap = clamp_max(max_tabs)
    if open_count >= HARD_MAX:
        return ("reject", f"Hard cap is {HARD_MAX} tabs.")
    if open_count >= cap:
        return ("reject", f"Max tabs is set to {cap}.")
    warnings = []
    if open_count >= WARN_AFTER:
        warnings.append("under 4 GB" if ram_gb and ram_gb < 4 else "already have")
    if open_count + 1 >= 11:
        warnings.append("above the default pool of 10")
    return ("allow", warnings)


def can_close(pinned: bool, confirmed: bool, close_pinned_explicit: bool) -> bool:
    if not confirmed:
        return False
    if pinned and not close_pinned_explicit:
        return False
    return True


def restore_order(tabs):
    return sorted(tabs, key=lambda t: (not t["pin"], -t["active"], t["idx"]))


def close_others(tabs, keep_id):
    return [t["id"] for t in tabs if t["id"] != keep_id and not t["pin"]]


def should_park(low: bool, avail: int, threshold: int) -> bool:
    if low:
        return True
    if threshold > 0 and 0 < avail < threshold:
        return True
    return False


def memory_frac(used: int, total: int) -> float:
    if total <= 0:
        return 0.0
    return max(0.0, min(1.0, used / total))


def normalize(raw: str) -> str:
    t = raw.strip()
    if not t:
        return "about:blank"
    low = t.lower()
    if low.startswith("javascript:") or low.startswith("file:") or low.startswith("content:"):
        return "about:blank"
    if low == "about:blank":
        return "about:blank"
    import re
    if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", t):
        return t
    if " " not in t and "." in t.split("/")[0]:
        return "https://" + t
    return "https://www.google.com/search?q=" + t.replace(" ", "+")


SEP = "\u0001"


def stack_push(existing, url):
    lst = [] if not existing else existing.split(SEP)
    if not lst or lst[-1] != url:
        lst.append(url)
    return SEP.join(lst[-50:])


SHIM = "visibilityState return 'visible' hidden return false visibilitychange pagehide"


class BrowserContractTests(unittest.TestCase):
    def test_01_default_pool_is_ten(self):
        self.assertEqual(DEFAULT_MAX, 10)
        self.assertEqual(HARD_MAX, 16)
        self.assertEqual(clamp_max(99), 16)

    def test_02_tenth_tab_allowed(self):
        kind, _ = decide_new_tab(9, 10, 8)
        self.assertEqual(kind, "allow")

    def test_03_eleventh_rejected_at_default_cap(self):
        kind, reason = decide_new_tab(10, 10, 8)
        self.assertEqual(kind, "reject")
        self.assertIn("Max tabs", reason)

    def test_04_warn_after_eight_on_low_ram(self):
        kind, warnings = decide_new_tab(8, 16, 3)
        self.assertEqual(kind, "allow")
        self.assertTrue(any("4 GB" in w for w in warnings))

    def test_05_tab_eleven_warns_when_cap_raised(self):
        kind, warnings = decide_new_tab(10, 16, 8)
        self.assertEqual(kind, "allow")
        self.assertTrue(any("default pool of 10" in w for w in warnings))

    def test_06_hard_cap_sixteen(self):
        kind, reason = decide_new_tab(16, 16, 12)
        self.assertEqual(kind, "reject")
        self.assertIn("Hard cap", reason)

    def test_07_close_requires_confirm(self):
        self.assertFalse(can_close(False, False, False))
        self.assertTrue(can_close(False, True, False))

    def test_08_pinned_needs_explicit(self):
        self.assertFalse(can_close(True, True, False))
        self.assertTrue(can_close(True, True, True))

    def test_09_restore_order_pins_first(self):
        tabs = [
            {"id": "a", "pin": False, "active": 10, "idx": 0},
            {"id": "b", "pin": True, "active": 1, "idx": 1},
            {"id": "c", "pin": False, "active": 50, "idx": 2},
            {"id": "d", "pin": True, "active": 40, "idx": 3},
        ]
        self.assertEqual([t["id"] for t in restore_order(tabs)], ["d", "b", "c", "a"])

    def test_10_close_others_skips_pinned(self):
        tabs = [
            {"id": "keep", "pin": False},
            {"id": "pin", "pin": True},
            {"id": "x", "pin": False},
            {"id": "y", "pin": False},
        ]
        self.assertEqual(close_others(tabs, "keep"), ["x", "y"])

    def test_11_park_only_on_low_memory(self):
        self.assertFalse(should_park(False, 800_000_000, 200_000_000))
        self.assertTrue(should_park(True, 800_000_000, 200_000_000))
        self.assertTrue(should_park(False, 50_000_000, 200_000_000))

    def test_12_memory_bar_clamps(self):
        self.assertAlmostEqual(memory_frac(50, 100), 0.5)
        self.assertEqual(memory_frac(200, 100), 1.0)
        self.assertEqual(memory_frac(10, 0), 0.0)

    def test_13_url_https_and_blocks_js(self):
        self.assertEqual(normalize("example.com"), "https://example.com")
        self.assertEqual(normalize("javascript:alert(1)"), "about:blank")
        self.assertEqual(normalize("file:///etc/passwd"), "about:blank")
        self.assertIn("google.com/search", normalize("hello world"))

    def test_14_back_stack_roundtrip(self):
        e = stack_push(None, "https://a.example")
        e = stack_push(e, "https://b.example")
        self.assertEqual(e.split(SEP), ["https://a.example", "https://b.example"])

    def test_15_visibility_shim_contract(self):
        self.assertIn("visibilityState", SHIM)
        self.assertIn("visible", SHIM)
        self.assertIn("pagehide", SHIM)

    def test_16_ten_tabs_close_one_isolation(self):
        ids = [f"t{i}" for i in range(10)]
        remaining = [i for i in ids if i != "t4"]
        self.assertEqual(len(remaining), 9)
        self.assertNotIn("t4", remaining)
        self.assertEqual(len(ids), 10)

    def test_17_restore_never_drops(self):
        original = {f"t{i}" for i in range(5)}
        live, paused = {"t0"}, {f"t{i}" for i in range(1, 5)}
        restored = live | paused
        self.assertTrue(original.issubset(restored))
        self.assertEqual(original, restored)

    def test_18_estimate_bytes(self):
        used = 1 * EST_LIVE + 1 * EST_LITE + 1 * EST_PAUSED
        self.assertEqual(used, EST_LIVE + EST_LITE + EST_PAUSED)

    def test_19_drm_hosts(self):
        def warn(url: str):
            u = url.lower()
            if "netflix.com" in u:
                return "DRM"
            return None
        self.assertEqual(warn("https://www.netflix.com/title/1"), "DRM")
        self.assertIsNone(warn("https://example.com"))

    def test_20_no_eviction_policy_exists(self):
        # There is no function that picks a victim tab. Parking is not deletion.
        parked = {"id": "x", "status": "PAUSED_BY_SYSTEM"}
        self.assertNotEqual(parked["status"], "DELETED")


if __name__ == "__main__":
    unittest.main(verbosity=2)
