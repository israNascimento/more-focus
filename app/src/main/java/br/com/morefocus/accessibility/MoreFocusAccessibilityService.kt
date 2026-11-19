package br.com.morefocus.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
import br.com.morefocus.blocking.DailyUsageState
import br.com.morefocus.blocking.FACEBOOK_PACKAGE_NAME
import br.com.morefocus.blocking.INSTAGRAM_PACKAGE_NAME
import br.com.morefocus.blocking.KWAI_PACKAGE_NAME
import br.com.morefocus.blocking.PauseExtensionTarget
import br.com.morefocus.blocking.ReelsDetector
import br.com.morefocus.blocking.ScreenSnapshot
import br.com.morefocus.blocking.ShortsBlocker
import br.com.morefocus.blocking.TIKTOK_FEED_PAGE_ID
import br.com.morefocus.blocking.TIKTOK_PACKAGE_NAME
import br.com.morefocus.blocking.VerticalVideoAction
import br.com.morefocus.blocking.X_PACKAGE_NAME
import br.com.morefocus.blocking.X_VIDEO_VIEWER_ID
import br.com.morefocus.blocking.YOUTUBE_PACKAGE_NAME
import br.com.morefocus.blocking.isTikTokForYou
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
    private var watchingVideoFeed = false
    private var blockedFeedPackage: String? = null
    private var nextVideoBackAtUptime = 0L
    private var feedVideoMask: FeedVideoMask? = null
    private var overlayView: View? = null
    private var overlayMessage: OverlayMessage? = null
    private var overlayMutationInProgress = false
    private var verticalVideosSessionPackageName: String? = null
    private var verticalVideosSessionStartedAtMillis: Long? = null
    private var activeLimitedAppPackageName: String? = null
    private var activeLimitedAppStartedAtMillis: Long? = null
    private var scheduledCheck: Runnable? = null
    private var usageFlush: Runnable? = null
    private val eventEvaluationThrottle by lazy {
        EventEvaluationThrottle<AccessibilityEventContext>(
            intervalMillis = EVENT_EVALUATION_INTERVAL_MILLIS,
            currentTimeMillis = SystemClock::uptimeMillis,
            postDelayed = { runnable, delayMillis ->
                handler.postDelayed(runnable, delayMillis)
            },
            removeCallbacks = handler::removeCallbacks,
            evaluate = ::evaluateCurrentScreen
        )
    }

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
        val container = (application as MoreFocusApplication).appContainer
        settingsRepository = container.settingsRepository
        container.dailyUsageNotification.start()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        feedVideoMask = FeedVideoMask(this, windowManager)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        updateFeedVideoMask(null)
        evaluateCurrentScreen()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (overlayMutationInProgress || !::settingsRepository.isInitialized) return
        if (overlayView != null) {
            val message = overlayMessage ?: return
            if (message.pauseExtensionTarget != PauseExtensionTarget.VerticalVideos) return
            val viewerPackage = message.verticalVideosPackageNameToTrackOnPause
            if (viewerPackage != INSTAGRAM_PACKAGE_NAME && viewerPackage != X_PACKAGE_NAME &&
                viewerPackage != TIKTOK_PACKAGE_NAME && viewerPackage != FACEBOOK_PACKAGE_NAME &&
                viewerPackage != KWAI_PACKAGE_NAME
            ) return

            // The touchable dialog can be the active accessibility window. Inspect the app below it.
            val appRoot = windows.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
            }?.root ?: return
            if (appRoot.packageName == viewerPackage) {
                val stillWatching = when (viewerPackage) {
                    X_PACKAGE_NAME -> xVideoScreenIds(appRoot).isNotEmpty()
                    KWAI_PACKAGE_NAME -> hasKwaiHomeFeed(appRoot)
                    FACEBOOK_PACKAGE_NAME -> hasFacebookReelsViewer(appRoot)
                    TIKTOK_PACKAGE_NAME -> videoScreen(appRoot, null).isTikTokForYou()
                    else -> visibleResourceId(appRoot, reelsDetector.homeFeedResourceIds) == null
                }
                if (stillWatching) return
            }
            hideOverlay()
            updateFeedVideoMask(appRoot.packageName?.toString())
            evaluateCurrentScreen(AccessibilityEventContext(appRoot.packageName?.toString(), null))
            return
        }
        val eventPackage = event?.packageName?.toString()
        if (updateFeedVideoMask(eventPackage) && eventPackage == packageName) return
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        eventEvaluationThrottle.submit(
            AccessibilityEventContext(
                packageName = event?.packageName?.toString(),
                className = event?.className?.toString()
            )
        )
    }

    private fun updateFeedVideoMask(eventPackage: String?): Boolean {
        val scope = settingsRepository.getMonitoringScope()
        val checkWindow = eventPackage == null || watchingVideoFeed ||
            scope.masksFeedVideos(eventPackage) || (blockedFeedPackage != null && eventPackage == blockedFeedPackage)
        val root = if (checkWindow) foregroundRoot() else null
        val activePackage = root?.packageName?.toString()
        val hideVideos = scope.masksFeedVideos(activePackage)
        val blockTaps = activePackage == blockedFeedPackage && scope.monitorsVerticalVideos(activePackage)
        val watch = hideVideos || blockTaps
        if (watch != watchingVideoFeed) {
            watchingVideoFeed = watch
            val info = serviceInfo
            val scroll = AccessibilityEvent.TYPE_VIEW_SCROLLED
            info.eventTypes = if (watch) info.eventTypes or scroll else info.eventTypes and scroll.inv()
            info.notificationTimeout = if (watch) 32L else EVENT_EVALUATION_INTERVAL_MILLIS
            serviceInfo = info
        }
        if (watch && root != null) feedVideoMask?.update(root, hideVideos, blockTaps)
        else feedVideoMask?.clear()
        return watch
    }

    private fun foregroundRoot(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow
        if (watchingVideoFeed && root?.packageName == packageName) {
            // A touch on a feed cover can make our overlay the active window.
            return windows.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
            }?.root
        }
        return root
    }

    private fun evaluateCurrentScreen(eventContext: AccessibilityEventContext? = null) {
        if (
            !::settingsRepository.isInitialized ||
            overlayView != null ||
            overlayMutationInProgress
        ) {
            return
        }

        val nowMillis = System.currentTimeMillis()
        var resolvedRoot: AccessibilityNodeInfo? = null
        var rootWasResolved = false
        fun activeRoot(): AccessibilityNodeInfo? {
            if (!rootWasResolved) {
                resolvedRoot = foregroundRoot()
                rootWasResolved = true
            }
            return resolvedRoot
        }

        val packageName = eventContext?.packageName
            ?.takeIf { it.isNotBlank() }
            ?: activeRoot()?.packageName?.toString()
        // Updating the notification can emit status bar events while the video is still in focus.
        val trackedVideoPackage = verticalVideosSessionPackageName ?: blockedFeedPackage
        if (trackedVideoPackage != null && packageName != trackedVideoPackage &&
            activeRoot()?.packageName?.toString() == trackedVideoPackage
        ) return

        val monitoringScope = settingsRepository.getMonitoringScope()

        if (!monitoringScope.monitors(packageName)) {
            if (blockedFeedPackage != null) {
                blockedFeedPackage = null
                updateFeedVideoMask(packageName)
            }
            nextVideoBackAtUptime = 0L
            finishTracking(nowMillis)
            hideOverlay()
            return
        }

        val className = eventContext?.className
        finishSessionsFromOtherPackages(packageName, nowMillis)
        val storedSettings = settingsRepository.getBlockSettingsForPackage(
            packageName = packageName,
            nowMillis = nowMillis
        )
        val settings = storedSettings.withActiveUsage(packageName, nowMillis)
        val blockFeedFor = packageName?.takeIf {
            (it == INSTAGRAM_PACKAGE_NAME || it == YOUTUBE_PACKAGE_NAME) &&
                settings.forcesVideoExit(nowMillis) && monitoringScope.monitorsVerticalVideos(it)
        }
        if (blockFeedFor != blockedFeedPackage) {
            blockedFeedPackage = blockFeedFor
            updateFeedVideoMask(packageName)
        }

        val isEnabledVerticalVideo = monitoringScope.monitorsVerticalVideos(packageName) &&
            activeRoot()?.let { root ->
                root.packageName?.toString() == packageName &&
                    decisionEngine.isEnabledVerticalVideo(settings, videoScreen(root, className))
            } == true
        val videoAction = decisionEngine.verticalVideoAction(settings, isEnabledVerticalVideo, nowMillis)
        if (videoAction == VerticalVideoAction.GO_BACK) {
            finishActiveVerticalVideosSession(nowMillis)
            updateActiveLimitedAppTracking(settings, packageName, nowMillis)
            scheduleUsageFlush()
            val uptime = SystemClock.uptimeMillis()
            if (uptime >= nextVideoBackAtUptime) {
                exitVideoViewer(packageName, activeRoot())
                nextVideoBackAtUptime = uptime + VIDEO_BACK_RETRY_MILLIS
            }
            // Recheck the foreground after navigation before issuing another Back.
            scheduleEvaluation(nextVideoBackAtUptime - uptime)
            return
        }
        nextVideoBackAtUptime = 0L
        val blockedAppRule = decisionEngine.blockedAppLimitRule(
            settings = settings,
            packageName = packageName,
            nowMillis = nowMillis
        )

        if (blockedAppRule != null) {
            flushActiveUsage(nowMillis)
            stopActiveSessions()
            cancelScheduledCheck()
            cancelUsageFlush()
            showOverlay(
                appLimitOverlayMessage(
                    rule = blockedAppRule,
                    verticalVideosPackageNameToTrackOnPause = packageName.takeIf {
                        settings.focusEnabled && isEnabledVerticalVideo
                    }
                )
            )
            return
        }

        if (videoAction == VerticalVideoAction.SHOW_WARNING) {
            val trackedAppRule = settings.appLimitRuleFor(packageName)
            flushActiveUsage(nowMillis)
            stopActiveSessions()
            cancelScheduledCheck()
            cancelUsageFlush()
            showOverlay(verticalVideosOverlayMessage(trackedAppRule, packageName))
        } else {
            hideOverlay()
            updateActiveLimitedAppTracking(settings, packageName, nowMillis)
            updateActiveVerticalVideosTracking(
                settings,
                packageName,
                isEnabledVerticalVideo,
                nowMillis
            )
            scheduleUsageFlush()
            scheduleNextCheck(
                settings,
                packageName,
                isEnabledVerticalVideo,
                nowMillis
            )
        }
    }

    private fun visibleResourceId(root: AccessibilityNodeInfo, ids: Set<String>): String? {
        val viewport = Rect().also(root::getBoundsInScreen)
        return ids.firstOrNull { id ->
            root.findAccessibilityNodeInfosByViewId("${root.packageName}:id/$id").any { node ->
                node.isVisibleToUser && Rect.intersects(viewport, Rect().also(node::getBoundsInScreen))
            }
        }
    }

    private fun videoScreen(root: AccessibilityNodeInfo, eventClass: String?): ScreenSnapshot {
        val packageName = root.packageName?.toString()
        val className = eventClass ?: root.className?.toString()
        if (packageName == KWAI_PACKAGE_NAME) {
            return ScreenSnapshot(packageName, className, kwaiHomeFeed = hasKwaiHomeFeed(root))
        }
        if (packageName == FACEBOOK_PACKAGE_NAME) {
            return ScreenSnapshot(packageName, className, facebookReelsViewer = hasFacebookReelsViewer(root))
        }
        if (packageName == TIKTOK_PACKAGE_NAME) {
            val tab = root.findAccessibilityNodeInfosByViewId("android:id/text1")
                .firstOrNull { it.isSelected && it.isVisibleToUser }?.text?.toString()
            val page = tab?.let { visibleResourceId(root, setOf(TIKTOK_FEED_PAGE_ID)) }
            return ScreenSnapshot(packageName, className, setOfNotNull(page), selectedTikTokTab = tab)
        }
        val ids = when (packageName) {
            INSTAGRAM_PACKAGE_NAME -> {
                val homeMarker = visibleResourceId(root, reelsDetector.homeFeedResourceIds)
                when {
                    homeMarker != null -> setOf(homeMarker)
                    reelsDetector.hasViewerClass(className) -> emptySet()
                    else -> AccessibilityNodeScanner.resourceEntryNames(
                        root, reelsDetector.resourceIdsOfInterest, visibleOnly = true,
                        hasConclusiveMatch = reelsDetector::hasConclusiveResourceMatch
                    )
                }
            }
            YOUTUBE_PACKAGE_NAME -> setOfNotNull(visibleResourceId(root, shortsBlocker.resourceIdsOfInterest))
            X_PACKAGE_NAME -> xVideoScreenIds(root)
            else -> emptySet()
        }
        return ScreenSnapshot(packageName, className, ids)
    }

    private fun xVideoScreenIds(root: AccessibilityNodeInfo): Set<String> {
        // Compose exposes VideoTab as a bare tag, below the native content container.
        val content = root.findAccessibilityNodeInfosByViewId("android:id/content").firstOrNull() ?: root
        return AccessibilityNodeScanner.resourceEntryNames(
            content, setOf(X_VIDEO_VIEWER_ID), visibleOnly = true, hasConclusiveMatch = { it.isNotEmpty() }
        )
    }

    private fun hasKwaiHomeFeed(root: AccessibilityNodeInfo): Boolean {
        // Kwai marks the icon inside the selected bottom tab, and uses a separate resource namespace.
        val home = root.findAccessibilityNodeInfosByViewId("com.kwai.video.basis:id/id_home_bottom_tab_home")
            .firstOrNull { it.isVisibleToUser } ?: return false
        val homeSelected = home.findAccessibilityNodeInfosByViewId("com.kwai.video.basis:id/consume_icon_view")
            .any { it.isSelected && it.isVisibleToUser }
        return homeSelected && root.findAccessibilityNodeInfosByViewId("com.kwai.video.basis:id/slide_play_view_pager")
            .any { it.isVisibleToUser }
    }

    private fun exitVideoViewer(viewerPackage: String?, root: AccessibilityNodeInfo?) {
        // Back from TikTok's home feed can leave the app before the user can reach other tabs.
        if (viewerPackage == TIKTOK_PACKAGE_NAME && root?.packageName == TIKTOK_PACKAGE_NAME) {
            val profile = root.findAccessibilityNodeInfosByViewId("$TIKTOK_PACKAGE_NAME:id/oph")
                .firstOrNull { it.isVisibleToUser && it.contentDescription in listOf("Perfil", "Profile") }
            if (profile?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
        }
        if (viewerPackage == KWAI_PACKAGE_NAME && root?.packageName == KWAI_PACKAGE_NAME) {
            val profile = root.findAccessibilityNodeInfosByViewId("com.kwai.video.basis:id/id_home_bottom_tab_me")
                .firstOrNull { it.isVisibleToUser && it.isClickable }
            if (profile?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onInterrupt() {
        feedVideoMask?.clear()
        stopTracking(System.currentTimeMillis())
        hideOverlay()
    }

    override fun onDestroy() {
        (application as MoreFocusApplication).appContainer.dailyUsageNotification.stop()
        feedVideoMask?.clear()
        stopTracking(System.currentTimeMillis())
        hideOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private fun showOverlay(message: OverlayMessage) {
        feedVideoMask?.clear()
        val messageWithPauseLock = message.copy(
            pauseLockSeconds = settingsRepository.pauseExtensionLockSeconds(
                message.pauseExtensionTarget
            )
        )
        if (overlayView != null && overlayMessage == messageWithPauseLock) return
        if (overlayMutationInProgress) return

        overlayMutationInProgress = true
        try {
            eventEvaluationThrottle.cancel()
            if (overlayView != null) hideOverlay()

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

            overlayMessage = messageWithPauseLock
            overlayView = view
            runCatching { windowManager.addView(view, params) }
                .onFailure {
                    overlayView = null
                    overlayMessage = null
                }
                .getOrThrow()
        } finally {
            overlayMutationInProgress = false
        }
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
                            message.verticalVideosPackageNameToTrackOnPause?.let { packageName ->
                                verticalVideosSessionPackageName = packageName
                                verticalVideosSessionStartedAtMillis = nowMillis
                            }
                            hideOverlay()
                            scheduleUsageFlush()
                            scheduleEvaluation(MIN_SCHEDULE_DELAY_MILLIS)
                        },
                        onExitClick = {
                            stopTracking(System.currentTimeMillis())
                            val viewerPackage = message.verticalVideosPackageNameToTrackOnPause
                            if (message.pauseExtensionTarget == PauseExtensionTarget.VerticalVideos &&
                                (viewerPackage == TIKTOK_PACKAGE_NAME || viewerPackage == KWAI_PACKAGE_NAME)
                            ) {
                                val root = windows.firstOrNull {
                                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
                                }?.root
                                exitVideoViewer(viewerPackage, root)
                            } else {
                                performGlobalAction(GLOBAL_ACTION_BACK)
                            }
                            hideOverlay()
                        }
                    )
                }
            }
        }

    private fun appLimitOverlayMessage(
        rule: AppLimitRule,
        verticalVideosPackageNameToTrackOnPause: String?
    ): OverlayMessage =
        OverlayMessage(
            title = getString(R.string.app_limit_overlay_title),
            description = getString(R.string.app_limit_overlay_description, rule.appName),
            pauseExtensionTarget = PauseExtensionTarget.App(rule.packageName),
            packageNameToTrackOnPause = rule.packageName,
            verticalVideosPackageNameToTrackOnPause =
                verticalVideosPackageNameToTrackOnPause
        )

    private fun verticalVideosOverlayMessage(
        trackedAppRule: AppLimitRule?,
        packageName: String?
    ): OverlayMessage =
        OverlayMessage(
            title = getString(R.string.focus_overlay_title),
            description = getString(R.string.focus_overlay_description),
            pauseExtensionTarget = PauseExtensionTarget.VerticalVideos,
            packageNameToTrackOnPause = trackedAppRule?.packageName,
            verticalVideosPackageNameToTrackOnPause = packageName
        )

    private fun BlockSettings.withActiveUsage(
        packageName: String?,
        nowMillis: Long
    ): BlockSettings {
        val activeAppUsageMillis = if (activeLimitedAppPackageName == packageName) {
            activeUsageMillis(activeLimitedAppStartedAtMillis, nowMillis)
        } else {
            0L
        }
        val activeVerticalVideosUsageMillis =
            if (verticalVideosSessionPackageName == packageName) {
                activeUsageMillis(verticalVideosSessionStartedAtMillis, nowMillis)
            } else {
                0L
            }

        return copy(
            dailyVerticalVideosUsageMillis =
                dailyVerticalVideosUsageMillis + activeVerticalVideosUsageMillis,
            appLimitRules = appLimitRules.map { rule ->
                if (rule.packageName == packageName) {
                    rule.copy(usageMillis = rule.usageMillis + activeAppUsageMillis)
                } else {
                    rule
                }
            }
        )
    }

    private fun activeUsageMillis(startedAtMillis: Long?, nowMillis: Long): Long {
        val startedAt = startedAtMillis ?: return 0L
        if (nowMillis <= startedAt) return 0L

        return dailyUsageClock.addVisibleTime(
            state = DailyUsageState(
                dayKey = dailyUsageClock.dayKey(nowMillis),
                usedMillis = 0L
            ),
            startedAtMillis = startedAt,
            endedAtMillis = nowMillis
        ).usedMillis
    }

    private fun recordActiveLimitedAppUsage(nowMillis: Long) {
        val packageName = activeLimitedAppPackageName ?: return
        val startedAtMillis = activeLimitedAppStartedAtMillis ?: return
        if (nowMillis <= startedAtMillis) return
        settingsRepository.recordDailyAppUsage(
            packageName = packageName,
            startedAtMillis = startedAtMillis,
            endedAtMillis = nowMillis
        )
        activeLimitedAppStartedAtMillis = nowMillis
    }

    private fun recordActiveVerticalVideosUsage(nowMillis: Long) {
        val startedAtMillis = verticalVideosSessionStartedAtMillis ?: return
        if (nowMillis <= startedAtMillis) return
        settingsRepository.recordDailyVerticalVideosUsage(
            startedAtMillis = startedAtMillis,
            endedAtMillis = nowMillis
        )
        verticalVideosSessionStartedAtMillis = nowMillis
    }

    private fun stopTracking(nowMillis: Long) {
        finishTracking(nowMillis)
        eventEvaluationThrottle.cancel()
    }

    private fun finishTracking(nowMillis: Long) {
        if (::settingsRepository.isInitialized) flushActiveUsage(nowMillis)
        stopActiveSessions()
        cancelScheduledCheck()
        cancelUsageFlush()
    }

    private fun flushActiveUsage(nowMillis: Long) {
        recordActiveLimitedAppUsage(nowMillis)
        recordActiveVerticalVideosUsage(nowMillis)
    }

    private fun stopActiveSessions() {
        activeLimitedAppPackageName = null
        activeLimitedAppStartedAtMillis = null
        verticalVideosSessionPackageName = null
        verticalVideosSessionStartedAtMillis = null
    }

    private fun finishSessionsFromOtherPackages(
        packageName: String?,
        nowMillis: Long
    ) {
        if (
            activeLimitedAppPackageName != null &&
            activeLimitedAppPackageName != packageName
        ) {
            finishActiveLimitedAppSession(nowMillis)
        }
        if (
            verticalVideosSessionPackageName != null &&
            verticalVideosSessionPackageName != packageName
        ) {
            finishActiveVerticalVideosSession(nowMillis)
        }
    }

    private fun finishActiveLimitedAppSession(nowMillis: Long) {
        recordActiveLimitedAppUsage(nowMillis)
        activeLimitedAppPackageName = null
        activeLimitedAppStartedAtMillis = null
    }

    private fun finishActiveVerticalVideosSession(nowMillis: Long) {
        recordActiveVerticalVideosUsage(nowMillis)
        verticalVideosSessionPackageName = null
        verticalVideosSessionStartedAtMillis = null
    }

    private fun updateActiveLimitedAppTracking(
        settings: BlockSettings,
        packageName: String?,
        nowMillis: Long
    ) {
        val rule = settings.appLimitRuleFor(packageName)
        if (settings.focusEnabled && rule != null) {
            if (activeLimitedAppPackageName != rule.packageName) {
                finishActiveLimitedAppSession(nowMillis)
                activeLimitedAppPackageName = rule.packageName
                activeLimitedAppStartedAtMillis = nowMillis
            }
        } else {
            finishActiveLimitedAppSession(nowMillis)
        }
    }

    private fun updateActiveVerticalVideosTracking(
        settings: BlockSettings,
        packageName: String?,
        isEnabledVerticalVideo: Boolean,
        nowMillis: Long
    ) {
        if (settings.focusEnabled && isEnabledVerticalVideo) {
            if (verticalVideosSessionPackageName != packageName) {
                finishActiveVerticalVideosSession(nowMillis)
                verticalVideosSessionPackageName = packageName
                verticalVideosSessionStartedAtMillis = nowMillis
            }
        } else {
            finishActiveVerticalVideosSession(nowMillis)
        }
    }

    private fun scheduleUsageFlush() {
        if (
            activeLimitedAppStartedAtMillis == null &&
            verticalVideosSessionStartedAtMillis == null
        ) {
            cancelUsageFlush()
            return
        }
        if (usageFlush != null) return

        val nextFlush = Runnable {
            usageFlush = null
            if (::settingsRepository.isInitialized) {
                val nowMillis = System.currentTimeMillis()
                finishDisabledSessions(nowMillis)
                flushActiveUsage(nowMillis)
                if (
                    activeLimitedAppStartedAtMillis == null &&
                    verticalVideosSessionStartedAtMillis == null
                ) {
                    cancelScheduledCheck()
                }
                scheduleUsageFlush()
            }
        }
        usageFlush = nextFlush
        handler.postDelayed(nextFlush, USAGE_FLUSH_INTERVAL_MILLIS)
    }

    private fun finishDisabledSessions(nowMillis: Long) {
        val monitoringScope = settingsRepository.getMonitoringScope()
        if (!monitoringScope.monitorsAppLimit(activeLimitedAppPackageName)) {
            finishActiveLimitedAppSession(nowMillis)
        }
        if (!monitoringScope.monitorsVerticalVideos(verticalVideosSessionPackageName)) {
            finishActiveVerticalVideosSession(nowMillis)
        }
    }

    private fun cancelUsageFlush() {
        usageFlush?.let { handler.removeCallbacks(it) }
        usageFlush = null
    }

    private fun scheduleNextCheck(
        settings: BlockSettings,
        packageName: String?,
        isEnabledVerticalVideo: Boolean,
        nowMillis: Long
    ) {
        val isTrackingApp = settings.focusEnabled && settings.appLimitRuleFor(packageName) != null
        val isTrackingVerticalVideos = settings.focusEnabled && isEnabledVerticalVideo

        if (!isTrackingApp && !isTrackingVerticalVideos && blockedFeedPackage == null) {
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
                    (settings.pauseUntilMillis <= nowMillis ||
                        settings.hardBlockVerticalVideos) &&
                    it > 0L
            }
        val resetDelayMillis = dailyUsageClock.millisUntilNextDay(nowMillis)
            .takeIf { it > 0L }
        val nextDelayMillis = listOfNotNull(
            (settings.focusSessionEndsAtMillis - nowMillis).takeIf { blockedFeedPackage != null && it > 0L },
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
        const val EVENT_EVALUATION_INTERVAL_MILLIS = 250L
        const val VIDEO_BACK_RETRY_MILLIS = 1_000L
        const val USAGE_FLUSH_INTERVAL_MILLIS = 5 * 1000L
    }
}

private data class AccessibilityEventContext(
    val packageName: String?,
    val className: String?
)

private data class OverlayMessage(
    val title: String,
    val description: String,
    val pauseExtensionTarget: PauseExtensionTarget,
    val packageNameToTrackOnPause: String?,
    val verticalVideosPackageNameToTrackOnPause: String?,
    val pauseLockSeconds: Int = 0
)
