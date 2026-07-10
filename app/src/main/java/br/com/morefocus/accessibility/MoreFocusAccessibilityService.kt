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
import br.com.morefocus.MoreFocusApplication
import br.com.morefocus.R
import br.com.morefocus.blocking.AppLimitRule
import br.com.morefocus.blocking.BlockDecisionEngine
import br.com.morefocus.blocking.BlockSettings
import br.com.morefocus.blocking.DailyUsageClock
import br.com.morefocus.blocking.PauseExtensionTarget
import br.com.morefocus.blocking.ReelsDetector
import br.com.morefocus.blocking.ScreenSnapshot
import br.com.morefocus.blocking.ShortsBlocker
import br.com.morefocus.blocking.isEnabledVerticalVideo
import br.com.morefocus.data.MoreFocusSettingsRepository
import br.com.morefocus.ui.theme.MoreFocusTheme

class MoreFocusAccessibilityService : AccessibilityService(), SavedStateRegistryOwner {
    private val reelsDetector = ReelsDetector()
    private val shortsBlocker = ShortsBlocker()
    private val decisionEngine = BlockDecisionEngine(reelsDetector, shortsBlocker)
    private val dailyUsageClock = DailyUsageClock()
    private val handler = Handler(Looper.getMainLooper())
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private lateinit var settingsRepository: MoreFocusSettingsRepository
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var overlayMessage: OverlayMessage? = null
    private var verticalVideosSessionStartedAtMillis: Long? = null
    private var activeLimitedAppPackageName: String? = null
    private var activeLimitedAppStartedAtMillis: Long? = null
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
        settingsRepository = (application as MoreFocusApplication)
            .appContainer
            .settingsRepository
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (overlayView != null) return
        evaluateCurrentScreen(event)
    }

    private fun evaluateCurrentScreen(event: AccessibilityEvent? = null) {
        if (!::settingsRepository.isInitialized) return

        val nowMillis = System.currentTimeMillis()
        val root = rootInActiveWindow
        val screen = ScreenSnapshot(
            packageName = event?.packageName?.toString() ?: root?.packageName?.toString(),
            className = event?.className?.toString() ?: root?.className?.toString(),
            root = AccessibilityNodeMapper.from(root)
        )
        val isInstagramReels = reelsDetector.isInstagramReels(screen)
        val isYouTubeShorts = shortsBlocker.isYouTubeShorts(screen)

        recordActiveLimitedAppUsage(nowMillis)
        recordActiveVerticalVideosUsage(nowMillis)
        val settings = settingsRepository.getBlockSettings(nowMillis)
        val isEnabledVerticalVideo = settings.isEnabledVerticalVideo(
            isInstagramReels = isInstagramReels,
            isYouTubeShorts = isYouTubeShorts
        )
        val blockedAppRule = decisionEngine.blockedAppLimitRule(
            settings = settings,
            packageName = screen.packageName,
            nowMillis = nowMillis
        )

        if (blockedAppRule != null) {
            stopActiveSessions()
            cancelScheduledCheck()
            showOverlay(
                appLimitOverlayMessage(
                    rule = blockedAppRule,
                    trackVerticalVideosOnPause = settings.focusEnabled &&
                        isEnabledVerticalVideo
                )
            )
            return
        }

        val shouldBlockVerticalVideos = decisionEngine.shouldBlock(
            settings = settings,
            isInstagramReels = isInstagramReels,
            isYouTubeShorts = isYouTubeShorts,
            nowMillis = nowMillis
        )

        if (shouldBlockVerticalVideos) {
            val trackedAppRule = settings.appLimitRuleFor(screen.packageName)
            verticalVideosSessionStartedAtMillis = null
            activeLimitedAppPackageName = null
            activeLimitedAppStartedAtMillis = null
            cancelScheduledCheck()
            showOverlay(verticalVideosOverlayMessage(trackedAppRule))
        } else {
            hideOverlay()
            updateActiveLimitedAppTracking(settings, screen.packageName, nowMillis)
            updateActiveVerticalVideosTracking(
                settings,
                isEnabledVerticalVideo,
                nowMillis
            )
            scheduleNextCheck(
                settings,
                screen.packageName,
                isEnabledVerticalVideo,
                nowMillis
            )
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

    private fun showOverlay(message: OverlayMessage) {
        val messageWithPauseLock = message.copy(
            pauseLockSeconds = settingsRepository.pauseExtensionLockSeconds(
                message.pauseExtensionTarget
            )
        )
        if (overlayView != null && overlayMessage == messageWithPauseLock) return
        if (overlayView != null) hideOverlay()

        overlayMessage = messageWithPauseLock
        val view = buildOverlayView(messageWithPauseLock)
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
        overlayMessage = null
    }

    private fun buildOverlayView(message: OverlayMessage): View =
        ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@MoreFocusAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@MoreFocusAccessibilityService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MoreFocusTheme {
                    FocusOverlay(
                        title = message.title,
                        description = message.description,
                        initialPauseLockSeconds = message.pauseLockSeconds,
                        onPauseClick = {
                            val nowMillis = System.currentTimeMillis()
                            settingsRepository.pauseFor(
                                durationMillis = PAUSE_DURATION_MILLIS,
                                target = message.pauseExtensionTarget,
                                nowMillis = nowMillis
                            )
                            message.packageNameToTrackOnPause?.let { packageName ->
                                activeLimitedAppPackageName = packageName
                                activeLimitedAppStartedAtMillis = nowMillis
                            }
                            if (message.trackVerticalVideosOnPause) {
                                verticalVideosSessionStartedAtMillis = nowMillis
                            }
                            hideOverlay()
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

    private fun appLimitOverlayMessage(
        rule: AppLimitRule,
        trackVerticalVideosOnPause: Boolean
    ): OverlayMessage =
        OverlayMessage(
            title = getString(R.string.app_limit_overlay_title),
            description = getString(R.string.app_limit_overlay_description, rule.appName),
            pauseExtensionTarget = PauseExtensionTarget.App(rule.packageName),
            packageNameToTrackOnPause = rule.packageName,
            trackVerticalVideosOnPause = trackVerticalVideosOnPause
        )

    private fun verticalVideosOverlayMessage(trackedAppRule: AppLimitRule?): OverlayMessage =
        OverlayMessage(
            title = getString(R.string.focus_overlay_title),
            description = getString(R.string.focus_overlay_description),
            pauseExtensionTarget = PauseExtensionTarget.VerticalVideos,
            packageNameToTrackOnPause = trackedAppRule?.packageName,
            trackVerticalVideosOnPause = true
        )

    private fun recordActiveLimitedAppUsage(nowMillis: Long) {
        val packageName = activeLimitedAppPackageName ?: return
        val startedAtMillis = activeLimitedAppStartedAtMillis ?: return
        settingsRepository.recordDailyAppUsage(
            packageName = packageName,
            startedAtMillis = startedAtMillis,
            endedAtMillis = nowMillis
        )
        activeLimitedAppStartedAtMillis = nowMillis
    }

    private fun recordActiveVerticalVideosUsage(nowMillis: Long) {
        val startedAtMillis = verticalVideosSessionStartedAtMillis ?: return
        settingsRepository.recordDailyVerticalVideosUsage(
            startedAtMillis = startedAtMillis,
            endedAtMillis = nowMillis
        )
        verticalVideosSessionStartedAtMillis = nowMillis
    }

    private fun stopTracking(nowMillis: Long) {
        if (!::settingsRepository.isInitialized) return
        recordActiveLimitedAppUsage(nowMillis)
        recordActiveVerticalVideosUsage(nowMillis)
        stopActiveSessions()
        cancelScheduledCheck()
    }

    private fun stopActiveSessions() {
        activeLimitedAppPackageName = null
        activeLimitedAppStartedAtMillis = null
        verticalVideosSessionStartedAtMillis = null
    }

    private fun updateActiveLimitedAppTracking(
        settings: BlockSettings,
        packageName: String?,
        nowMillis: Long
    ) {
        val rule = settings.appLimitRuleFor(packageName)
        if (settings.focusEnabled && rule != null) {
            activeLimitedAppPackageName = rule.packageName
            activeLimitedAppStartedAtMillis = nowMillis
        } else {
            activeLimitedAppPackageName = null
            activeLimitedAppStartedAtMillis = null
        }
    }

    private fun updateActiveVerticalVideosTracking(
        settings: BlockSettings,
        isEnabledVerticalVideo: Boolean,
        nowMillis: Long
    ) {
        if (settings.focusEnabled && isEnabledVerticalVideo) {
            verticalVideosSessionStartedAtMillis = nowMillis
        } else {
            verticalVideosSessionStartedAtMillis = null
        }
    }

    private fun scheduleNextCheck(
        settings: BlockSettings,
        packageName: String?,
        isEnabledVerticalVideo: Boolean,
        nowMillis: Long
    ) {
        val isTrackingApp = settings.focusEnabled && settings.appLimitRuleFor(packageName) != null
        val isTrackingVerticalVideos = settings.focusEnabled && isEnabledVerticalVideo

        if (!isTrackingApp && !isTrackingVerticalVideos) {
            cancelScheduledCheck()
            return
        }

        val pauseDelayMillis = (settings.pauseUntilMillis - nowMillis)
            .takeIf { settings.pauseUntilMillis > nowMillis }
        val appLimitDelayMillis = settings.appLimitRuleFor(packageName)
            ?.remainingDailyLimitMillis
            ?.takeIf { settings.pauseUntilMillis <= nowMillis && it > 0L }
        val verticalVideosLimitDelayMillis =
            settings.remainingDailyVerticalVideosLimitMillis
            .takeIf {
                isTrackingVerticalVideos &&
                    settings.pauseUntilMillis <= nowMillis &&
                    it > 0L
            }
        val resetDelayMillis = dailyUsageClock.millisUntilNextDay(nowMillis)
            .takeIf { it > 0L }
        val nextDelayMillis = listOfNotNull(
            pauseDelayMillis,
            appLimitDelayMillis,
            verticalVideosLimitDelayMillis,
            resetDelayMillis
        ).minOrNull()

        if (nextDelayMillis == null) {
            cancelScheduledCheck()
            return
        }

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

private data class OverlayMessage(
    val title: String,
    val description: String,
    val pauseExtensionTarget: PauseExtensionTarget,
    val packageNameToTrackOnPause: String?,
    val trackVerticalVideosOnPause: Boolean,
    val pauseLockSeconds: Int = 0
)
