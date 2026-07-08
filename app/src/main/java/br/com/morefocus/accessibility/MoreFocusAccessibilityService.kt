package br.com.morefocus.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
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
import br.com.morefocus.blocking.BlockSettings
import br.com.morefocus.blocking.DailyUsageClock
import br.com.morefocus.blocking.ReelsDetector
import br.com.morefocus.blocking.ScreenSnapshot
import br.com.morefocus.settings.MoreFocusPreferences
import br.com.morefocus.ui.theme.MoreFocusTheme

class MoreFocusAccessibilityService : AccessibilityService(), SavedStateRegistryOwner {
    private val reelsDetector = ReelsDetector()
    private val decisionEngine = BlockDecisionEngine(reelsDetector)
    private val dailyUsageClock = DailyUsageClock()
    private val handler = Handler(Looper.getMainLooper())
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private lateinit var preferences: MoreFocusPreferences
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var reelsSessionStartedAtMillis: Long? = null
    private var scheduledCheck: Runnable? = null

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
        evaluateCurrentScreen(event)
    }

    private fun evaluateCurrentScreen(event: AccessibilityEvent? = null) {
        if (!::preferences.isInitialized) return

        val nowMillis = System.currentTimeMillis()
        val root = rootInActiveWindow
        val screen = ScreenSnapshot(
            packageName = event?.packageName?.toString() ?: root?.packageName?.toString(),
            className = event?.className?.toString() ?: root?.className?.toString(),
            root = AccessibilityNodeMapper.from(root)
        )
        val isInstagramReels = reelsDetector.isInstagramReels(screen)

        recordActiveReelsUsage(nowMillis)
        val settings = preferences.getBlockSettings(nowMillis)
        val shouldBlock = decisionEngine.shouldBlock(
            settings = settings,
            isInstagramReels = isInstagramReels,
            nowMillis = nowMillis
        )

        if (shouldBlock) {
            reelsSessionStartedAtMillis = null
            cancelScheduledCheck()
            showOverlay()
        } else {
            hideOverlay()
            if (settings.focusEnabled && settings.instagramReelsEnabled && isInstagramReels) {
                reelsSessionStartedAtMillis = nowMillis
                scheduleNextCheck(settings, nowMillis)
            } else {
                reelsSessionStartedAtMillis = null
                cancelScheduledCheck()
            }
        }
    }

    override fun onInterrupt() {
        stopTracking(System.currentTimeMillis())
        hideOverlay()
    }

    override fun onDestroy() {
        stopTracking(System.currentTimeMillis())
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
                            val nowMillis = System.currentTimeMillis()
                            preferences.pauseFor(PAUSE_DURATION_MILLIS, nowMillis)
                            hideOverlay()
                            reelsSessionStartedAtMillis = nowMillis
                            scheduleEvaluation(PAUSE_DURATION_MILLIS)
                        },
                        onExitClick = {
                            stopTracking(System.currentTimeMillis())
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            hideOverlay()
                        }
                    )
                }
            }
        }

    private fun recordActiveReelsUsage(nowMillis: Long) {
        val startedAtMillis = reelsSessionStartedAtMillis ?: return
        preferences.recordDailyReelsUsage(
            startedAtMillis = startedAtMillis,
            endedAtMillis = nowMillis
        )
        reelsSessionStartedAtMillis = nowMillis
    }

    private fun stopTracking(nowMillis: Long) {
        if (!::preferences.isInitialized) return
        recordActiveReelsUsage(nowMillis)
        reelsSessionStartedAtMillis = null
        cancelScheduledCheck()
    }

    private fun scheduleNextCheck(settings: BlockSettings, nowMillis: Long) {
        val pauseDelayMillis = (settings.pauseUntilMillis - nowMillis)
            .takeIf { settings.pauseUntilMillis > nowMillis }
        val limitDelayMillis = settings.remainingDailyLimitMillis
            .takeIf { settings.pauseUntilMillis <= nowMillis && it > 0L }
        val resetDelayMillis = dailyUsageClock.millisUntilNextDay(nowMillis)
            .takeIf { it > 0L }
        val nextDelayMillis = listOfNotNull(
            pauseDelayMillis,
            limitDelayMillis,
            resetDelayMillis
        ).minOrNull() ?: return

        scheduleEvaluation(nextDelayMillis)
    }

    private fun scheduleEvaluation(delayMillis: Long) {
        cancelScheduledCheck()
        val nextCheck = Runnable { evaluateCurrentScreen() }
        scheduledCheck = nextCheck
        handler.postDelayed(nextCheck, delayMillis.coerceAtLeast(MIN_SCHEDULE_DELAY_MILLIS))
    }

    private fun cancelScheduledCheck() {
        scheduledCheck?.let { handler.removeCallbacks(it) }
        scheduledCheck = null
    }

    private companion object {
        const val PAUSE_DURATION_MILLIS = 1 * 60 * 1000L
        const val MIN_SCHEDULE_DELAY_MILLIS = 250L
    }
}
