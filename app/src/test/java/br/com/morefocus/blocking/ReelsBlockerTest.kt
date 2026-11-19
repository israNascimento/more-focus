package br.com.morefocus.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelsBlockerTest {
    private val engine = BlockDecisionEngine(ReelsDetector(), ShortsBlocker())

    @Test
    fun blocksConfiguredAppWhenDailyLimitIsReached() {
        assertTrue(
            engine.shouldBlock(
                settings = BlockSettings(
                    focusEnabled = true,
                    instagramReelsEnabled = true,
                    appLimitRules = listOf(appLimitRule(usageMillis = 10 * 60 * 1000L))
                ),
                screen = appScreen(packageName = "com.video"),
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun doesNotBlockConfiguredAppBeforeDailyLimit() {
        assertFalse(
            engine.shouldBlock(
                settings = BlockSettings(
                    focusEnabled = true,
                    instagramReelsEnabled = true,
                    appLimitRules = listOf(appLimitRule(usageMillis = 10 * 60 * 1000L - 1))
                ),
                screen = appScreen(packageName = "com.video"),
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun doesNotBlockAppWithoutRule() {
        assertFalse(
            engine.shouldBlock(
                settings = BlockSettings(
                    focusEnabled = true,
                    instagramReelsEnabled = true,
                    appLimitRules = listOf(appLimitRule(usageMillis = 10 * 60 * 1000L))
                ),
                screen = appScreen(packageName = "com.notes"),
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun doesNotBlockConfiguredAppWhenMoreFocusIsDisabled() {
        assertFalse(
            engine.shouldBlock(
                settings = BlockSettings(
                    focusEnabled = false,
                    instagramReelsEnabled = true,
                    appLimitRules = listOf(appLimitRule(usageMillis = 10 * 60 * 1000L))
                ),
                screen = appScreen(packageName = "com.video"),
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun doesNotBlockConfiguredAppWhileTemporaryPauseIsActive() {
        assertFalse(
            engine.shouldBlock(
                settings = BlockSettings(
                    focusEnabled = true,
                    instagramReelsEnabled = true,
                    pauseUntilMillis = 1_300L,
                    appLimitRules = listOf(appLimitRule(usageMillis = 10 * 60 * 1000L))
                ),
                screen = appScreen(packageName = "com.video"),
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun blocksConfiguredAppWhenLimitIsLoweredBelowCurrentUsage() {
        assertTrue(
            engine.shouldBlock(
                settings = BlockSettings(
                    focusEnabled = true,
                    instagramReelsEnabled = true,
                    appLimitRules = listOf(
                        appLimitRule(
                            limitSeconds = 5 * 60,
                            usageMillis = 10 * 60 * 1000L
                        )
                    )
                ),
                screen = appScreen(packageName = "com.video"),
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun blocksInstagramReelsWhenDailyLimitIsReached() {
        val screen = ScreenSnapshot(
            packageName = "com.instagram.android",
            className = "com.instagram.clips.viewer.ClipsViewerActivity",
            resourceIds = emptySet()
        )

        assertTrue(
            engine.shouldBlock(
                settings = settingsAtLimit(),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun doesNotBlockInstagramReelsBeforeDailyLimit() {
        assertFalse(
            engine.shouldBlock(
                settings = BlockSettings(
                    focusEnabled = true,
                    instagramReelsEnabled = true,
                    dailyVerticalVideosLimitSeconds = 15 * 60,
                    dailyVerticalVideosUsageMillis = 15 * 60 * 1000L - 1
                ),
                screen = explicitReelsScreen(),
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun doesNotBlockInstagramFeedProfileOrMessages() {
        val settings = settingsAtLimit()

        val feed = instagramScreen(label = "Página inicial")
        val profile = instagramScreen(label = "Perfil")
        val messages = instagramScreen(label = "Mensagens")

        assertFalse(engine.shouldBlock(settings, feed, nowMillis = 1_000L))
        assertFalse(engine.shouldBlock(settings, profile, nowMillis = 1_000L))
        assertFalse(engine.shouldBlock(settings, messages, nowMillis = 1_000L))
    }

    @Test
    fun doesNotBlockWhenMoreFocusIsDisabled() {
        val screen = explicitReelsScreen()

        assertFalse(
            engine.shouldBlock(
                settings = settingsAtLimit().copy(focusEnabled = false),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun doesNotBlockWhenInstagramReelsRuleIsDisabled() {
        val screen = explicitReelsScreen()

        assertFalse(
            engine.shouldBlock(
                settings = settingsAtLimit().copy(instagramReelsEnabled = false),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun blocksYouTubeShortsWhenSharedVerticalVideosLimitIsReached() {
        assertTrue(
            engine.shouldBlock(
                settings = settingsAtLimit(),
                screen = youtubeShortsScreen(),
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun doesNotBlockYouTubeShortsWhenItsRuleIsDisabled() {
        assertFalse(
            engine.shouldBlock(
                settings = settingsAtLimit().copy(youtubeShortsEnabled = false),
                screen = youtubeShortsScreen(),
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun onlyEnabledPlatformsAreCountedAsVerticalVideos() {
        val reelsOnly = settingsAtLimit().copy(
            instagramReelsEnabled = true,
            youtubeShortsEnabled = false
        )
        val shortsOnly = settingsAtLimit().copy(
            instagramReelsEnabled = false,
            youtubeShortsEnabled = true
        )

        assertTrue(reelsOnly.isEnabledVerticalVideo(true, false))
        assertFalse(reelsOnly.isEnabledVerticalVideo(false, true))
        assertTrue(shortsOnly.isEnabledVerticalVideo(false, true))
        assertFalse(shortsOnly.isEnabledVerticalVideo(true, false))
    }

    @Test
    fun doesNotBlockRegularYouTubeScreenWhenSharedLimitIsReached() {
        val screen = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            className = "com.google.android.apps.youtube.app.watchwhile.MainActivity",
            resourceIds = emptySet()
        )

        assertFalse(
            engine.shouldBlock(
                settings = settingsAtLimit(),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun doesNotBlockWhileTemporaryPauseIsActive() {
        val screen = explicitReelsScreen()

        assertFalse(
            engine.shouldBlock(
                settings = BlockSettings(
                    focusEnabled = true,
                    instagramReelsEnabled = true,
                    pauseUntilMillis = 1_300L,
                    dailyVerticalVideosUsageMillis = 15 * 60 * 1000L
                ),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun blocksAgainAfterTemporaryPauseExpires() {
        val screen = explicitReelsScreen()

        assertTrue(
            engine.shouldBlock(
                settings = BlockSettings(
                    focusEnabled = true,
                    instagramReelsEnabled = true,
                    pauseUntilMillis = 999L,
                    dailyVerticalVideosUsageMillis = 15 * 60 * 1000L
                ),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun singleReelsNavigationLabelIsNotEnoughToBlock() {
        val screen = instagramScreen(label = "Reels")

        assertFalse(
            engine.shouldBlock(
                settings = settingsAtLimit(),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun blocksInstagramReelsFromViewerResourceIdsWithoutText() {
        val screen = ScreenSnapshot(
            packageName = "com.instagram.android",
            className = "androidx.recyclerview.widget.RecyclerView",
            resourceIds = setOf(
                "clips_viewer_container",
                "clips_video_player",
                "clips_ufi_like_button",
                "clips_ufi_comments_button",
                "clips_ufi_share_button"
            )
        )

        assertTrue(
            engine.shouldBlock(
                settings = settingsAtLimit(),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun blocksInstagramReelsFromVideoAndSideActionResourceIdsWithoutText() {
        val screen = ScreenSnapshot(
            packageName = "com.instagram.android",
            className = "android.view.ViewGroup",
            resourceIds = setOf(
                "clips_viewer_video_layout",
                "clips_ufi_like_button",
                "clips_ufi_comments_button"
            )
        )

        assertTrue(
            engine.shouldBlock(
                settings = settingsAtLimit(),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun visibleHomeFeedTakesPrecedenceOverViewerEventsAndRetainedViewerIds() {
        val screen = explicitReelsScreen().copy(
            className = "com.instagram.clips.viewer.ClipsViewerActivity",
            resourceIds = explicitReelsScreen().resourceIds + "main_feed_action_bar"
        )

        assertFalse(engine.shouldBlock(settingsAtLimit(), screen, nowMillis = 1_000L))
    }

    @Test
    fun doesNotBlockSingleGenericClipsNavigationResourceId() {
        val screen = ScreenSnapshot(
            packageName = "com.instagram.android",
            className = "com.instagram.mainactivity.InstagramMainActivity",
            resourceIds = setOf("clips_tab")
        )

        assertFalse(
            engine.shouldBlock(
                settings = settingsAtLimit(),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun doesNotBlockGenericFeedActionButtonsWithoutReelsViewerIds() {
        val screen = ScreenSnapshot(
            packageName = "com.instagram.android",
            className = "com.instagram.mainactivity.InstagramMainActivity",
            resourceIds = setOf(
                "like_button",
                "comment_button",
                "share_button"
            )
        )

        assertFalse(
            engine.shouldBlock(
                settings = settingsAtLimit(),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun blocksWhenDailyLimitIsLoweredBelowCurrentUsage() {
        assertTrue(
            engine.shouldBlock(
                settings = BlockSettings(
                    focusEnabled = true,
                    instagramReelsEnabled = true,
                    dailyVerticalVideosLimitSeconds = 5 * 60,
                    dailyVerticalVideosUsageMillis = 10 * 60 * 1000L
                ),
                screen = explicitReelsScreen(),
                nowMillis = 1_000L
            )
        )
    }

    private fun explicitReelsScreen(): ScreenSnapshot =
        ScreenSnapshot(
            packageName = "com.instagram.android",
            className = null,
            resourceIds = setOf("clips_viewer_container", "clips_video_player")
        )

    private fun youtubeShortsScreen(): ScreenSnapshot =
        ScreenSnapshot(
            packageName = "com.google.android.youtube",
            className = "com.google.android.apps.youtube.app.watchwhile.MainActivity",
            resourceIds = setOf("reel_progress_bar")
        )

    private fun instagramScreen(label: String): ScreenSnapshot =
        ScreenSnapshot(
            packageName = "com.instagram.android",
            className = "com.instagram.mainactivity.InstagramMainActivity",
            resourceIds = emptySet()
        )

    private fun appScreen(packageName: String): ScreenSnapshot =
        ScreenSnapshot(
            packageName = packageName,
            className = null,
            resourceIds = emptySet()
        )

    private fun appLimitRule(
        limitSeconds: Int = 10 * 60,
        usageMillis: Long = 0L
    ): AppLimitRule =
        AppLimitRule(
            packageName = "com.video",
            appName = "Video",
            limitSeconds = limitSeconds,
            usageMillis = usageMillis
        )

    private fun settingsAtLimit(): BlockSettings =
        BlockSettings(
            focusEnabled = true,
            instagramReelsEnabled = true,
            dailyVerticalVideosLimitSeconds = 15 * 60,
            dailyVerticalVideosUsageMillis = 15 * 60 * 1000L
        )
}
