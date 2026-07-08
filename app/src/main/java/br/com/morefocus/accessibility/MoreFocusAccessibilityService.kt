package br.com.morefocus.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import br.com.morefocus.blocking.BlockDecisionEngine
import br.com.morefocus.blocking.ReelsDetector
import br.com.morefocus.blocking.ScreenSnapshot
import br.com.morefocus.settings.MoreFocusPreferences

class MoreFocusAccessibilityService : AccessibilityService() {
    private val decisionEngine = BlockDecisionEngine(ReelsDetector())
    private lateinit var preferences: MoreFocusPreferences
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        preferences = MoreFocusPreferences(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::preferences.isInitialized) return

        val root = rootInActiveWindow
        val screen = ScreenSnapshot(
            packageName = event?.packageName?.toString() ?: root?.packageName?.toString(),
            className = event?.className?.toString() ?: root?.className?.toString(),
            root = AccessibilityNodeMapper.from(root)
        )

        val shouldBlock = decisionEngine.shouldBlock(
            settings = preferences.getBlockSettings(),
            screen = screen,
            nowMillis = System.currentTimeMillis()
        )

        if (shouldBlock) {
            showOverlay()
        } else {
            hideOverlay()
        }
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val view = buildOverlayView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    private fun hideOverlay() {
        val view = overlayView ?: return
        runCatching { windowManager.removeView(view) }
        overlayView = null
    }

    private fun buildOverlayView(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(222, 17, 24, 39))
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 24.dp(), 24.dp(), 24.dp())
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 18.dp().toFloat()
            }
        }

        panel.addView(
            TextView(this).apply {
                text = "Reels bloqueado"
                setTextColor(Color.rgb(17, 24, 39))
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
            }
        )

        panel.addView(
            TextView(this).apply {
                text = "Você pediu ao MoreFocus para evitar Instagram Reels."
                setTextColor(Color.rgb(55, 65, 81))
                textSize = 16f
                setPadding(0, 10.dp(), 0, 18.dp())
            }
        )

        panel.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END

                addView(
                    Button(context).apply {
                        text = "Ignorar 5 min"
                        setOnClickListener {
                            preferences.pauseFor(PAUSE_DURATION_MILLIS)
                            hideOverlay()
                        }
                    }
                )

                addView(
                    Button(context).apply {
                        text = "Sair"
                        setOnClickListener {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            hideOverlay()
                        }
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = 8.dp()
                    }
                )
            }
        )

        root.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                leftMargin = 24.dp()
                rightMargin = 24.dp()
            }
        )

        return root
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val PAUSE_DURATION_MILLIS = 5 * 60 * 1000L
    }
}
