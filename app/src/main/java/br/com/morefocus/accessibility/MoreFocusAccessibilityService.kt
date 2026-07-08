package br.com.morefocus.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import br.com.morefocus.blocking.BlockDecisionEngine
import br.com.morefocus.blocking.ReelsDetector
import br.com.morefocus.blocking.ScreenSnapshot
import br.com.morefocus.settings.MoreFocusPreferences
import br.com.morefocus.ui.theme.MoreFocusTheme

class MoreFocusAccessibilityService : AccessibilityService(), SavedStateRegistryOwner {
    private val decisionEngine = BlockDecisionEngine(ReelsDetector())
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private lateinit var preferences: MoreFocusPreferences
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        preferences = MoreFocusPreferences(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
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
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
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

    private fun buildOverlayView(): View =
        ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MoreFocusAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@MoreFocusAccessibilityService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MoreFocusTheme {
                    FocusOverlay(
                        onPauseClick = {
                            preferences.pauseFor(PAUSE_DURATION_MILLIS)
                            hideOverlay()
                        },
                        onExitClick = {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            hideOverlay()
                        }
                    )
                }
            }
        }

    private companion object {
        const val PAUSE_DURATION_MILLIS = 1 * 60 * 1000L
    }
}
