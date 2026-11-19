package br.com.morefocus.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringScopeTest {
    @Test
    fun monitorsEnabledPlatformsAndConfiguredPackages() {
        val scope = MonitoringScope(
            focusEnabled = true,
            instagramReelsEnabled = true,
            youtubeShortsEnabled = true,
            limitedPackages = setOf("com.example.video")
        )

        assertTrue(scope.monitors(INSTAGRAM_PACKAGE_NAME))
        assertTrue(scope.monitors(YOUTUBE_PACKAGE_NAME))
        assertTrue(scope.monitors("com.example.video"))
        assertFalse(scope.monitors("com.example.notes"))
    }

    @Test
    fun ignoresDisabledPlatformsUnlessTheyHaveAnAppLimit() {
        val scope = MonitoringScope(
            focusEnabled = true,
            instagramReelsEnabled = false,
            youtubeShortsEnabled = false,
            limitedPackages = setOf(INSTAGRAM_PACKAGE_NAME)
        )

        assertTrue(scope.monitors(INSTAGRAM_PACKAGE_NAME))
        assertFalse(scope.monitors(YOUTUBE_PACKAGE_NAME))
    }

    @Test
    fun canMaskOnlyInstagramFeedWithoutCountingVerticalVideoTime() {
        val scope = MonitoringScope(true, false, false, emptySet(), hideFeedReels = true)

        assertTrue(scope.masksFeedVideos(INSTAGRAM_PACKAGE_NAME))
        assertFalse(scope.monitorsVerticalVideos(INSTAGRAM_PACKAGE_NAME))
        assertFalse(scope.masksFeedVideos(YOUTUBE_PACKAGE_NAME))
        assertFalse(scope.copy(focusEnabled = false).masksFeedVideos(INSTAGRAM_PACKAGE_NAME))
        assertFalse(scope.copy(hideFeedReels = false).masksFeedVideos(INSTAGRAM_PACKAGE_NAME))
    }

    @Test
    fun maskingShortsIsIndependentOfInstagramAndTheDailyLimit() {
        val scope = MonitoringScope(true, false, false, emptySet(), hideFeedShorts = true)

        assertTrue(scope.masksFeedVideos(YOUTUBE_PACKAGE_NAME))
        assertFalse(scope.masksFeedVideos(INSTAGRAM_PACKAGE_NAME))
        assertFalse(scope.monitorsVerticalVideos(YOUTUBE_PACKAGE_NAME))
        assertFalse(scope.copy(focusEnabled = false).masksFeedVideos(YOUTUBE_PACKAGE_NAME))
    }

    @Test
    fun monitorsNothingWhenFocusIsDisabled() {
        val scope = MonitoringScope(
            focusEnabled = false,
            instagramReelsEnabled = true,
            youtubeShortsEnabled = true,
            limitedPackages = setOf("com.example.video")
        )

        assertFalse(scope.monitors(INSTAGRAM_PACKAGE_NAME))
        assertFalse(scope.monitors(YOUTUBE_PACKAGE_NAME))
        assertFalse(scope.monitors("com.example.video"))
    }
}
