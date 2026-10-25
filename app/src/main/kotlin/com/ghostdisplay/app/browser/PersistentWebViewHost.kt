package com.ghostdisplay.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Pool of never-paused WebViews hosted in a Presentation on the virtual
 * display. Inactive tabs stay VISIBLE, full size, translated off-screen.
 * [WebView.onPause], [WebView.pauseTimers], destroy, and GONE are forbidden
 * except when the user closes that tab.
 */
class PersistentWebViewHost(
    private val appContext: Context,
    private val onTabInfo: (TabRecord) -> Unit,
    private val onNewWindowUrl: (String) -> Unit,
) : TabEngineHost {

    private val webViews = LinkedHashMap<String, GhostWebView>()
    private val records = LinkedHashMap<String, TabRecord>()
    private val downTimes = HashMap<String, Long>()
    private val main = Handler(Looper.getMainLooper())
    private var presentation: PersistentPresentation? = null
    private var container: FrameLayout? = null
    private var activeId: String? = null
    private var masterMuted: Boolean = false

    val attached: Boolean get() = container != null
    val presentationDisplayId: Int? get() = presentation?.display?.displayId

    fun attach(presentation: PersistentPresentation, container: FrameLayout) {
        this.presentation = presentation
        this.container = container
        webViews.values.forEach { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            container.addView(wv, fullLayout())
        }
        relayout()
    }

    fun detachPresentationKeepingViews() {
        // Viewer closing must NOT pause tabs. Presentation may be recreated;
        // WebViews stay in memory. If the Presentation is dismissed we reparent
        // to a hidden in-process holder so they remain attached to a window.
        val holder = inProcessHolder()
        webViews.values.forEach { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            holder.addView(wv, fullLayout())
        }
        container = holder
        presentation = null
        relayout()
    }

    override fun createLiveTab(tab: TabRecord): HostResult {
        return try {
            if (webViews.containsKey(tab.id)) {
                records[tab.id] = tab
                relayout()
                return HostResult.Live(tab.copy(liveStatus = TabLiveStatus.LIVE))
            }
            val ctx = presentation?.context ?: appContext
            val wv = createWebView(ctx, tab)
            webViews[tab.id] = wv
            records[tab.id] = tab.copy(liveStatus = TabLiveStatus.LIVE)
            val parent = container ?: inProcessHolder()
            if (container == null) container = parent
            parent.addView(wv, fullLayout())
            wv.loadUrl(tab.url)
            if (tab.scrollX != 0 || tab.scrollY != 0) {
                wv.postDelayed({
                    wv.evaluateJavascript(VisibilityShimInjector.scrollScript(tab.scrollX, tab.scrollY), null)
                    tab.formStateJson?.let { wv.evaluateJavascript(VisibilityShimInjector.restoreFormsScript(it), null) }
                }, 1200)
            }
            applyMute(tab.id)
            relayout()
            HostResult.Live(records[tab.id]!!)
        } catch (t: Throwable) {
            Log.w(TAG, "createLiveTab failed", t)
            HostResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    override fun destroyTab(id: String) {
        val wv = webViews.remove(id) ?: return
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.removeJavascriptInterface(BRIDGE)
        // User-confirmed close is the only destroy path.
        wv.stopLoading()
        wv.loadUrl("about:blank")
        wv.destroy()
        records.remove(id)
        downTimes.remove(id)
        if (activeId == id) activeId = webViews.keys.lastOrNull()
        relayout()
    }

    override fun loadUrl(id: String, url: String) {
        webViews[id]?.loadUrl(url)
        records[id] = records[id]?.copy(url = url) ?: return
    }

    override fun goBack(id: String): Boolean {
        val wv = webViews[id] ?: return false
        if (!wv.canGoBack()) return false
        wv.goBack()
        return true
    }

    override fun goForward(id: String): Boolean {
        val wv = webViews[id] ?: return false
        if (!wv.canGoForward()) return false
        wv.goForward()
        return true
    }

    override fun reload(id: String) {
        webViews[id]?.reload()
    }

    override fun setMuted(id: String, muted: Boolean) {
        records[id] = records[id]?.copy(muted = muted) ?: return
        applyMute(id)
    }

    override fun setMasterMuted(muted: Boolean) {
        masterMuted = muted
        webViews.keys.forEach { applyMute(it) }
    }

    override fun setLiteMode(id: String, lite: Boolean) {
        val wv = webViews[id] ?: return
        records[id]?.let { records[id] = it.copy(liteMode = lite) }
        applyLite(wv, lite)
    }

    override fun setKeepVisibleShim(id: String, enabled: Boolean) {
        records[id]?.let { records[id] = it.copy(keepVisibleShim = enabled) }
        if (enabled) webViews[id]?.evaluateJavascript(VisibilityShimInjector.SCRIPT, null)
    }

    override fun showTab(id: String) {
        activeId = id
        relayout()
        // Intentionally do NOT onPause the others.
    }

    override fun captureScrollAndForms(id: String): Pair<Int, Int>? {
        val wv = webViews[id] ?: return null
        return wv.scrollX to wv.scrollY
    }

    override fun dispatchTouch(id: String, action: Int, x: Float, y: Float): Boolean {
        val wv = webViews[id] ?: return false
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTimes[id] = now
        val down = downTimes[id] ?: now
        val ev = MotionEvent.obtain(down, now, action, x, y, 0)
        return try {
            wv.dispatchTouchEvent(ev)
        } finally {
            ev.recycle()
        }
    }

    override fun injectText(id: String, text: String) {
        val escaped = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        webViews[id]?.evaluateJavascript(
            "(function(){var e=document.activeElement; if(!e) return; if('value' in e){e.value += '$escaped'; e.dispatchEvent(new Event('input',{bubbles:true}));} else {document.execCommand('insertText', false, '$escaped');}})();",
            null,
        )
    }

    override fun evaluateMedia(id: String): Boolean {
        // Async in practice; the JS bridge pushes playing state.
        return records[id]?.audioPlaying == true
    }

    override fun snapshot(id: String): TabRecord? {
        val wv = webViews[id]
        val rec = records[id] ?: return null
        if (wv == null) return rec
        return rec.copy(
            url = wv.url ?: rec.url,
            title = wv.title ?: rec.title,
            canGoBack = wv.canGoBack(),
            canGoForward = wv.canGoForward(),
            scrollX = wv.scrollX,
            scrollY = wv.scrollY,
        )
    }

    fun releaseAllViews() {
        webViews.keys.toList().forEach { destroyTab(it) }
        runCatching { presentation?.dismiss() }
        presentation = null
        container = holder
    }

    fun webViewCount(): Int = webViews.size

    fun activeWebView(): WebView? = activeId?.let { webViews[it] }

    fun contentSize(): Pair<Int, Int> {
        val c = container
        return (c?.width ?: 1) to (c?.height ?: 1)
    }

    fun neverPaused(): Boolean {
        // Diagnostic: we never call onPause/pauseTimers.
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(ctx: Context, tab: TabRecord): GhostWebView {
        val wv = GhostWebView(ctx)
        wv.tabId = tab.id
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        wv.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportMultipleWindows(true)
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.loadsImagesAutomatically = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.userAgentString = s.userAgentString + " GhostDisplayPersistent/1.0"
        s.allowFileAccess = false
        s.allowContentAccess = false
        if (Build.VERSION.SDK_INT >= 33) {
            s.isAlgorithmicDarkeningAllowed = false
        }
        applyLite(wv, tab.liteMode)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.addJavascriptInterface(MediaBridge(tab.id), BRIDGE)
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
            override fun onPageFinished(view: WebView, url: String) {
                val rec = records[tab.id] ?: return
                val next = rec.copy(
                    url = url,
                    title = view.title ?: rec.title,
                    canGoBack = view.canGoBack(),
                    canGoForward = view.canGoForward(),
                    backStackJson = BackStackCodec.push(rec.backStackJson, url),
                )
                records[tab.id] = next
                if (next.keepVisibleShim) view.evaluateJavascript(VisibilityShimInjector.SCRIPT, null)
                view.evaluateJavascript(VisibilityShimInjector.MEDIA_WATCH, null)
                onTabInfo(next)
                VisibilityShimInjector.drmWarningForUrl(url)?.let {
                    // Surface, don't hide.
                    Log.i(TAG, it)
                }
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                val rec = records[tab.id] ?: return
                val next = rec.copy(title = title.orEmpty())
                records[tab.id] = next
                onTabInfo(next)
            }
            override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                // Favicon bitmap is not persisted as a blob (non-goal: no frame exfil).
                // Hostname is enough for the strip fallback.
            }
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?,
            ): Boolean {
                val hit = view.hitTestResult.extra
                if (!hit.isNullOrBlank()) onNewWindowUrl(hit)
                return false
            }
        }
        return wv
    }

    private fun applyLite(wv: WebView, lite: Boolean) {
        val s = wv.settings
        if (lite) {
            s.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            if (Build.VERSION.SDK_INT >= 23) s.offscreenPreRaster = false
            s.blockNetworkImage = false
        } else {
            s.cacheMode = WebSettings.LOAD_DEFAULT
            if (Build.VERSION.SDK_INT >= 23) s.offscreenPreRaster = true
            s.blockNetworkImage = false
        }
    }

    private fun applyMute(id: String) {
        val wv = webViews[id] ?: return
        val muted = masterMuted || (records[id]?.muted == true)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) {
            WebViewCompat.setAudioMuted(wv, muted)
        } else {
            val js = if (muted) {
                "(function(){document.querySelectorAll('video,audio').forEach(function(n){n.muted=true;});})();"
            } else {
                "(function(){document.querySelectorAll('video,audio').forEach(function(n){n.muted=false;});})();"
            }
            wv.evaluateJavascript(js, null)
        }
    }

    private fun relayout() {
        val parent = container ?: return
        val w = parent.width.coerceAtLeast(1)
        webViews.forEach { (id, wv) ->
            wv.visibility = View.VISIBLE
            wv.alpha = 1f
            if (id == activeId) {
                wv.translationX = 0f
                wv.translationY = 0f
                wv.elevation = 8f
                wv.bringToFront()
            } else {
                // Off-screen but still laid out at full size so media keeps compositing.
                wv.translationX = (w + 10_000).toFloat()
                wv.elevation = 0f
            }
        }
    }

    private fun fullLayout() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )

    private var holder: FrameLayout? = null
    private fun inProcessHolder(): FrameLayout {
        holder?.let { return it }
        val fl = FrameLayout(appContext)
        fl.visibility = View.VISIBLE
        holder = fl
        return fl
    }

    inner class MediaBridge(private val tabId: String) {
        @JavascriptInterface
        fun media(playing: Boolean) {
            main.post {
                val rec = records[tabId] ?: return@post
                val next = rec.copy(audioPlaying = playing, wasPlayingMedia = rec.wasPlayingMedia || playing)
                records[tabId] = next
                onTabInfo(next)
            }
        }
    }

    class GhostWebView(context: Context) : WebView(context) {
        var tabId: String = ""
    }

    companion object {
        private const val TAG = "GhostTabs"
        private const val BRIDGE = "GhostBridge"

        fun prepareProcess(context: Context) {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().flush()
            try {
                WebView(context.applicationContext).destroy()
            } catch (_: Throwable) {
            }
        }
    }
}
