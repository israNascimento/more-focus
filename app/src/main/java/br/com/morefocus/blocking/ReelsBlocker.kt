package br.com.morefocus.blocking

private const val MILLIS_PER_SECOND = 1000L

internal const val INSTAGRAM_PACKAGE_NAME = "com.instagram.android"
internal const val YOUTUBE_PACKAGE_NAME = "com.google.android.youtube"
internal const val X_PACKAGE_NAME = "com.twitter.android"
// X exposes this Compose tag throughout the swipeable viewer, including between videos.
internal const val X_VIDEO_VIEWER_ID = "VideoTab"

data class BlockSettings(
    val focusEnabled: Boolean,
    val instagramReelsEnabled: Boolean,
    val youtubeShortsEnabled: Boolean = true,
    val pauseUntilMillis: Long = 0L,
    val dailyVerticalVideosLimitSeconds: Int = 15 * 60,
    val dailyVerticalVideosUsageMillis: Long = 0L,
    val appLimitRules: List<AppLimitRule> = emptyList(),
    val hardBlockVerticalVideos: Boolean = false,
    val xVideosEnabled: Boolean = true
) {
    val dailyVerticalVideosLimitMillis: Long
        get() = dailyVerticalVideosLimitSeconds.coerceAtLeast(1) * MILLIS_PER_SECOND

    val remainingDailyVerticalVideosLimitMillis: Long
        get() = (dailyVerticalVideosLimitMillis - dailyVerticalVideosUsageMillis)
            .coerceAtLeast(0L)

    val hardVerticalVideoLimitReached: Boolean
        get() = hardBlockVerticalVideos && focusEnabled &&
            remainingDailyVerticalVideosLimitMillis == 0L

    fun appLimitRuleFor(packageName: String?): AppLimitRule? =
        packageName
            ?.takeIf { it.isNotBlank() }
            ?.let { activePackageName ->
                appLimitRules.firstOrNull { rule ->
                    rule.enabled && rule.packageName == activePackageName
                }
            }
}

data class ScreenSnapshot(
    val packageName: String?,
    val className: String?,
    val resourceIds: Set<String> = emptySet()
)

data class MonitoringScope(
    val focusEnabled: Boolean,
    val instagramReelsEnabled: Boolean,
    val youtubeShortsEnabled: Boolean,
    val limitedPackages: Set<String>,
    val hideFeedReels: Boolean = false,
    val hideFeedShorts: Boolean = false,
    val xVideosEnabled: Boolean = true
) {
    fun monitors(packageName: String?): Boolean =
        monitorsAppLimit(packageName) || monitorsVerticalVideos(packageName)

    fun masksFeedVideos(packageName: String?): Boolean =
        focusEnabled && when (packageName) {
            INSTAGRAM_PACKAGE_NAME -> hideFeedReels
            YOUTUBE_PACKAGE_NAME -> hideFeedShorts
            else -> false
        }

    fun monitorsAppLimit(packageName: String?): Boolean =
        focusEnabled && packageName != null && packageName in limitedPackages

    fun monitorsVerticalVideos(packageName: String?): Boolean =
        focusEnabled && (
            (instagramReelsEnabled && packageName == INSTAGRAM_PACKAGE_NAME) ||
                (youtubeShortsEnabled && packageName == YOUTUBE_PACKAGE_NAME) ||
                (xVideosEnabled && packageName == X_PACKAGE_NAME)
        )
}

enum class VerticalVideoAction { NONE, SHOW_WARNING, GO_BACK }

class BlockDecisionEngine(
    private val reelsDetector: ReelsDetector,
    private val shortsBlocker: ShortsBlocker
) {
    fun shouldBlock(settings: BlockSettings, screen: ScreenSnapshot, nowMillis: Long): Boolean {
        if (blockedAppLimitRule(settings, screen.packageName, nowMillis) != null) return true

        return verticalVideoAction(settings, isEnabledVerticalVideo(settings, screen), nowMillis) !=
            VerticalVideoAction.NONE
    }

    fun isEnabledVerticalVideo(
        settings: BlockSettings,
        screen: ScreenSnapshot
    ): Boolean =
        (settings.instagramReelsEnabled && reelsDetector.isInstagramReels(screen)) ||
            (settings.youtubeShortsEnabled && shortsBlocker.isYouTubeShorts(screen)) ||
            (settings.xVideosEnabled && screen.packageName == X_PACKAGE_NAME && X_VIDEO_VIEWER_ID in screen.resourceIds)

    fun verticalVideoAction(
        settings: BlockSettings,
        isEnabledVerticalVideo: Boolean,
        nowMillis: Long
    ): VerticalVideoAction = when {
        !isEnabledVerticalVideo || !settings.focusEnabled -> VerticalVideoAction.NONE
        settings.hardVerticalVideoLimitReached -> VerticalVideoAction.GO_BACK
        settings.pauseUntilMillis > nowMillis -> VerticalVideoAction.NONE
        settings.remainingDailyVerticalVideosLimitMillis == 0L -> VerticalVideoAction.SHOW_WARNING
        else -> VerticalVideoAction.NONE
    }

    fun blockedAppLimitRule(
        settings: BlockSettings,
        packageName: String?,
        nowMillis: Long
    ): AppLimitRule? {
        if (!settings.focusEnabled) return null
        if (settings.pauseUntilMillis > nowMillis) return null

        val rule = settings.appLimitRuleFor(packageName) ?: return null
        return rule.takeIf { it.usageMillis >= it.limitMillis }
    }
}

class ReelsDetector {
    // Home and the Reels viewer can coexist in Instagram's tab pager.
    val homeFeedResourceIds = setOf("main_feed_action_bar", "sticky_header_list")

    fun isInstagramReels(screen: ScreenSnapshot): Boolean {
        if (screen.packageName != INSTAGRAM_PACKAGE_NAME) return false

        if (screen.resourceIds.any { it in homeFeedResourceIds }) return false
        if (hasViewerClass(screen.className)) return true

        return hasConclusiveResourceMatch(screen.resourceIds)
    }

    fun hasViewerClass(className: String?): Boolean {
        val normalizedClassName = className.normalized()
        return VIEWER_CLASS_MARKERS.any { marker -> normalizedClassName.contains(marker) }
    }

    fun hasConclusiveResourceMatch(resourceIds: Set<String>): Boolean {
        if (resourceIds.any { it in PRIMARY_VIEWER_RESOURCE_IDS }) return true

        val hasMedia = resourceIds.any { it in MEDIA_RESOURCE_IDS }
        if (!hasMedia) return false

        return resourceIds.any { it in VIEWER_CHROME_RESOURCE_IDS } ||
            resourceIds.count { it in SIDE_ACTION_RESOURCE_IDS } >=
            MIN_SIDE_ACTION_RESOURCE_IDS
    }

    val resourceIdsOfInterest: Set<String> =
        PRIMARY_VIEWER_RESOURCE_IDS +
            MEDIA_RESOURCE_IDS +
            SIDE_ACTION_RESOURCE_IDS +
            VIEWER_CHROME_RESOURCE_IDS

    private fun String?.normalized(): String = this
        ?.trim()
        ?.lowercase()
        .orEmpty()

    private companion object {
        const val MIN_SIDE_ACTION_RESOURCE_IDS = 2

        val VIEWER_CLASS_MARKERS = setOf(
            "clips.viewer",
            "clipsviewer",
            "clips_viewer",
            "reels.viewer",
            "reelsviewer",
            "reels_viewer"
        )

        val PRIMARY_VIEWER_RESOURCE_IDS = setOf(
            "clips_viewer_container",
            "clips_viewer_view_pager"
        )

        val MEDIA_RESOURCE_IDS = setOf(
            "clips_viewer_video_layout",
            "clips_video_player",
            "clips_video_layout",
            "clips_video_container",
            "reels_player_asset"
        )

        val SIDE_ACTION_RESOURCE_IDS = setOf(
            "clips_ufi_component",
            "clips_ufi_like_button",
            "clips_ufi_comments_button",
            "clips_ufi_share_button",
            "clips_ufi_more_button"
        )

        val VIEWER_CHROME_RESOURCE_IDS = setOf(
            "clips_viewer_action_bar",
            "clips_viewer_action_bar_title",
            "clips_viewer_camera_button",
            "clips_navigation_bar_container",
            "clips_navigation_bar_row",
            "clips_author_info_component",
            "clips_caption_component",
            "clips_viewer_media_info",
            "clips_viewer_media_info_container",
            "clips_cta"
        )
    }
}
