package br.com.morefocus.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelsBlockerTest {
    private val engine = BlockDecisionEngine(ReelsDetector(), ShortsBlocker())

    @Test
    fun hardBlockRequiresFocusAndAnExhaustedSharedBudget() {
        val settings = settingsAtLimit().copy(hardBlockVerticalVideos = true)
        assertTrue(settings.hardVerticalVideoLimitReached)
        val justBeforeLimit = settings.copy(
            dailyVerticalVideosUsageMillis = settings.dailyVerticalVideosLimitMillis - 1
        )
        assertFalse(justBeforeLimit.hardVerticalVideoLimitReached)
        assertFalse(settings.copy(dailyVerticalVideosUsageMillis = 0L).hardVerticalVideoLimitReached)
        assertFalse(settings.copy(focusEnabled = false).hardVerticalVideoLimitReached)
        assertFalse(settingsAtLimit().hardVerticalVideoLimitReached)
    }

    @Test
    fun exhaustedEnabledViewersGoBackInHardModeAndShowTheWarningInNormalMode() {
        val xViewer = ScreenSnapshot(X_PACKAGE_NAME, "com.x.android.main.MainActivity", setOf("VideoTab"))
        val facebookViewer = ScreenSnapshot(FACEBOOK_PACKAGE_NAME, null, facebookReelsViewer = true)
        val kwaiFeed = ScreenSnapshot(KWAI_PACKAGE_NAME, null, kwaiHomeFeed = true)
        for (screen in listOf(explicitReelsScreen(), youtubeShortsScreen(), xViewer, facebookViewer, kwaiFeed)) {
            fun action(settings: BlockSettings) = engine.verticalVideoAction(
                settings, engine.isEnabledVerticalVideo(settings, screen), nowMillis = 1_000L
            )
            val hard = settingsAtLimit().copy(hardBlockVerticalVideos = true)
            assertEquals(VerticalVideoAction.GO_BACK, action(hard))
            assertEquals(VerticalVideoAction.GO_BACK, action(hard.copy(pauseUntilMillis = 2_000L)))
            assertEquals(VerticalVideoAction.NONE, action(hard.copy(dailyVerticalVideosUsageMillis = 0L)))
            assertEquals(VerticalVideoAction.NONE, action(hard.copy(focusEnabled = false)))
            assertEquals(VerticalVideoAction.SHOW_WARNING, action(settingsAtLimit()))
            assertEquals(VerticalVideoAction.NONE, action(settingsAtLimit().copy(pauseUntilMillis = 2_000L)))
        }
    }

    @Test
    fun focusSessionOverridesAnExtensionAndEndsAtTheSavedDeadline() {
        val settings = BlockSettings(true, true, focusSessionEndsAtMillis = 5_000L, pauseUntilMillis = 4_000L)
        val viewers = listOf(
            explicitReelsScreen(), youtubeShortsScreen(),
            ScreenSnapshot(FACEBOOK_PACKAGE_NAME, null, facebookReelsViewer = true),
            ScreenSnapshot(X_PACKAGE_NAME, null, setOf(X_VIDEO_VIEWER_ID)),
            ScreenSnapshot(TIKTOK_PACKAGE_NAME, null, setOf(TIKTOK_FEED_PAGE_ID), selectedTikTokTab = "Para você"),
            ScreenSnapshot(KWAI_PACKAGE_NAME, null, kwaiHomeFeed = true)
        )
        for (screen in viewers) {
            fun action(s: BlockSettings, now: Long) =
                engine.verticalVideoAction(s, engine.isEnabledVerticalVideo(s, screen), now)
            assertEquals(VerticalVideoAction.GO_BACK, action(settings, 3_000L))
            assertEquals(VerticalVideoAction.GO_BACK, action(settings, 4_999L))
            assertEquals(VerticalVideoAction.NONE, action(settings, 5_000L))
            assertEquals(VerticalVideoAction.SHOW_WARNING, action(settings.copy(dailyVerticalVideosUsageMillis = 900_000L), 5_000L))
            assertEquals(VerticalVideoAction.NONE, action(settings.copy(focusEnabled = false), 3_000L))
        }
    }

    @Test
    fun focusSessionRespectsViewerRulesAndWholeAppLimits() {
        val settings = BlockSettings(true, true, focusSessionEndsAtMillis = 5_000L)
        val inbox = appScreen(INSTAGRAM_PACKAGE_NAME)
        val regularVideo = appScreen(YOUTUBE_PACKAGE_NAME)
        val ordinaryScreens = listOf(inbox, regularVideo, appScreen(X_PACKAGE_NAME), appScreen(TIKTOK_PACKAGE_NAME),
            appScreen(FACEBOOK_PACKAGE_NAME), appScreen(KWAI_PACKAGE_NAME))
        for (screen in ordinaryScreens) assertFalse(engine.shouldBlock(settings, screen, 1_000L))
        val exhausted = appLimitRule(limitSeconds = 30, usageMillis = 30_000L).copy(packageName = YOUTUBE_PACKAGE_NAME)
        assertTrue(engine.shouldBlock(settings.copy(appLimitRules = listOf(exhausted)), regularVideo, 1_000L))
        assertFalse(engine.shouldBlock(settings.copy(instagramReelsEnabled = false), explicitReelsScreen(), 1_000L))
        assertFalse(engine.shouldBlock(settings.copy(youtubeShortsEnabled = false), youtubeShortsScreen(), 1_000L))
        val facebook = ScreenSnapshot(FACEBOOK_PACKAGE_NAME, null, facebookReelsViewer = true)
        assertFalse(engine.shouldBlock(settings.copy(facebookReelsEnabled = false), facebook, 1_000L))
        assertFalse(engine.shouldBlock(settings.copy(kwaiFeedEnabled = false),
            ScreenSnapshot(KWAI_PACKAGE_NAME, null, kwaiHomeFeed = true), 1_000L))
        assertFalse(engine.shouldBlock(settings.copy(xVideosEnabled = false),
            ScreenSnapshot(X_PACKAGE_NAME, null, setOf(X_VIDEO_VIEWER_ID)), 1_000L))
        assertFalse(engine.shouldBlock(settings.copy(tikTokForYouEnabled = false),
            ScreenSnapshot(TIKTOK_PACKAGE_NAME, null, setOf(TIKTOK_FEED_PAGE_ID), selectedTikTokTab = "Para você"), 1_000L))
    }

    @Test
    fun kwaiLeavesOtherTabsFreeAndHonorsItsOwnRule() {
        val settings = settingsAtLimit()
        val feed = ScreenSnapshot(KWAI_PACKAGE_NAME, null, kwaiHomeFeed = true)
        assertFalse(engine.shouldBlock(settings, feed.copy(kwaiHomeFeed = false), 1_000L))
        assertFalse(engine.shouldBlock(settings.copy(kwaiFeedEnabled = false), feed, 1_000L))
        assertFalse(engine.shouldBlock(settings, feed.copy(packageName = "com.other"), 1_000L))
    }

    @Test
    fun facebookOnlyCountsAnOpenViewerWithTheRuleEnabled() {
        val settings = settingsAtLimit()
        val feed = ScreenSnapshot(FACEBOOK_PACKAGE_NAME, "com.facebook.katana.activity.FbMainTabActivity")
        val viewer = feed.copy(facebookReelsViewer = true)
        assertFalse(engine.shouldBlock(settings, feed, 1_000L))
        assertTrue(engine.shouldBlock(settings, viewer, 1_000L))
        assertFalse(engine.shouldBlock(settings.copy(facebookReelsEnabled = false), viewer, 1_000L))
        assertFalse(engine.shouldBlock(settings, viewer.copy(packageName = "com.other"), 1_000L))
    }

    @Test
    fun xVideoRuleDistinguishesTheImmersiveFeedFromAnInlinePlayerInTheSameActivity() {
        val hard = settingsAtLimit().copy(hardBlockVerticalVideos = true)
        val timeline = ScreenSnapshot(X_PACKAGE_NAME, "com.x.android.main.MainActivity",
            setOf("MainLanding", "scaffold_home_tabbed", "timeline_post", "exo_content_frame"))
        assertFalse(engine.isEnabledVerticalVideo(hard, timeline))
        val video = timeline.copy(resourceIds = setOf("VideoTab", "ImmersiveVideo", "exo_content_frame"))
        val adBetweenVideos = timeline.copy(resourceIds = setOf("VideoTab", "ssp_immersive_ad_view"))
        assertTrue(engine.isEnabledVerticalVideo(hard, video))
        assertTrue(engine.isEnabledVerticalVideo(hard, adBetweenVideos))
        assertFalse(engine.isEnabledVerticalVideo(hard.copy(xVideosEnabled = false), video))
        assertFalse(engine.isEnabledVerticalVideo(hard, video.copy(packageName = "com.other")))
    }

    @Test
    fun hardModeRespectsIndividualRulesAndLeavesOtherScreensAlone() {
        val settings = settingsAtLimit().copy(hardBlockVerticalVideos = true, instagramReelsEnabled = false)
        assertFalse(engine.shouldBlock(settings, explicitReelsScreen(), nowMillis = 1_000L))
        assertTrue(engine.shouldBlock(settings, youtubeShortsScreen(), nowMillis = 1_000L))
        assertFalse(engine.shouldBlock(settings.copy(youtubeShortsEnabled = false), youtubeShortsScreen(), 1_000L))
        for (screen in listOf(instagramScreen("Feed"), appScreen(YOUTUBE_PACKAGE_NAME), appScreen("com.other"))) {
            assertFalse(engine.shouldBlock(settings.copy(instagramReelsEnabled = true), screen, 1_000L))
        }
    }

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

        assertTrue(engine.isEnabledVerticalVideo(reelsOnly, explicitReelsScreen()))
        assertFalse(engine.isEnabledVerticalVideo(reelsOnly, youtubeShortsScreen()))
        assertTrue(engine.isEnabledVerticalVideo(shortsOnly, youtubeShortsScreen()))
        assertFalse(engine.isEnabledVerticalVideo(shortsOnly, explicitReelsScreen()))
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
