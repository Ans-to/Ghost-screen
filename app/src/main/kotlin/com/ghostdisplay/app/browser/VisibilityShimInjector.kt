package com.ghostdisplay.app.browser

/**
 * Best-effort override of Page Visibility so sites that pause media when
 * `document.hidden === true` keep playing in off-screen tabs.
 *
 * This cannot defeat DRM / Widevine / "secure surface" checks. It is documented
 * in the Viewer as a per-tab toggle.
 */
object VisibilityShimInjector {
    const val SCRIPT = """
(function() {
  if (window.__ghostKeepVisible) return;
  window.__ghostKeepVisible = true;
  try {
    Object.defineProperty(document, 'hidden', { configurable: true, get: function() { return false; } });
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: function() { return 'visible'; } });
    Object.defineProperty(document, 'webkitHidden', { configurable: true, get: function() { return false; } });
    var stop = function(e) { e.stopImmediatePropagation(); };
    document.addEventListener('visibilitychange', stop, true);
    document.addEventListener('webkitvisibilitychange', stop, true);
    window.addEventListener('pagehide', stop, true);
    window.addEventListener('freeze', stop, true);
    document.addEventListener('freeze', stop, true);
  } catch (e) {}
  try {
    var videos = document.querySelectorAll('video,audio');
    for (var i = 0; i < videos.length; i++) {
      videos[i].setAttribute('playsinline', 'true');
      videos[i].setAttribute('webkit-playsinline', 'true');
    }
  } catch (e2) {}
})();
"""

    const val MEDIA_WATCH = """
(function() {
  if (window.__ghostMediaWatch) return;
  window.__ghostMediaWatch = true;
  setInterval(function() {
    try {
      var p = false;
      var nodes = document.querySelectorAll('video,audio');
      for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i];
        if (!n.paused && !n.ended && n.currentTime > 0) p = true;
      }
      if (window.GhostBridge && GhostBridge.media) GhostBridge.media(p);
    } catch (e) {}
  }, 1000);
})();
"""

    const val MEDIA_PROBE = """
(function() {
  try {
    var nodes = document.querySelectorAll('video,audio');
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      if (!n.paused && !n.ended && n.currentTime > 0) return true;
    }
  } catch (e) {}
  return false;
})();
"""

    const val CAPTURE_FORMS = """
(function() {
  try {
    var out = [];
    var nodes = document.querySelectorAll('input,textarea,select');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (el.type === 'password') continue;
      out.push({id: el.id || '', name: el.name || '', value: el.value || '', type: el.type || ''});
    }
    return JSON.stringify(out);
  } catch (e) { return '[]'; }
})();
"""

    fun restoreFormsScript(json: String): String {
        val escaped = json.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
        return """
(function() {
  try {
    var data = JSON.parse('$escaped');
    for (var i = 0; i < data.length; i++) {
      var d = data[i];
      var el = d.id ? document.getElementById(d.id) : null;
      if (!el && d.name) el = document.getElementsByName(d.name)[0];
      if (el && d.type !== 'password') el.value = d.value;
    }
  } catch (e) {}
})();
"""
    }

    fun scrollScript(x: Int, y: Int): String = "window.scrollTo($x, $y);"

    fun drmWarningForUrl(url: String): String? {
        val u = url.lowercase()
        return when {
            "netflix.com" in u || "disneyplus.com" in u || "hulu.com" in u ||
                "primevideo.com" in u || "max.com" in u || "hbomax.com" in u ->
                "This site uses DRM. It may refuse to play on a virtual display or in a non-visible tab. The keep-visible shim is best-effort and cannot override Widevine."
            "spotify.com" in u || "music.apple.com" in u ->
                "Some streaming players pause when they think the document is hidden. Enable the keep-visible shim on this tab."
            else -> null
        }
    }
}
