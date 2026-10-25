package com.ghostdisplay.app.browser

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Single Presentation on the virtual display that holds every WebView.
 * Switching tabs never dismisses this Presentation.
 */
class PersistentPresentation(
    context: Context,
    display: Display,
) : Presentation(context, display) {

    lateinit var root: LinearLayout
        private set
    lateinit var webContainer: FrameLayout
        private set
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (8 * context.resources.displayMetrics.density).toInt()
        status = TextView(context).apply {
            text = "GhostDisplay Persistent Browser"
            setTextColor(0xFF7AE7C7.toInt())
            textSize = 12f
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        webContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            setBackgroundColor(0xFF000000.toInt())
        }
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0D10.toInt())
            addView(status, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            addView(webContainer)
        }
        setContentView(root)
    }

    fun setStatus(text: String) {
        if (::status.isInitialized) status.text = text
    }

    companion object {
        fun tryShow(context: Context, display: Display): Pair<PersistentPresentation?, String?> {
            return try {
                val p = PersistentPresentation(context, display)
                p.show()
                p to null
            } catch (t: Throwable) {
                null to (t.message ?: "Presentation.show() failed: ${t.javaClass.simpleName}. Tabs still run in-process; Viewer can interact. The virtual display may be blank on this OEM.")
            }
        }
    }
}
