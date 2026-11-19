package br.com.morefocus.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TikTokForYouTest {
    private val engine = BlockDecisionEngine(ReelsDetector(), ShortsBlocker())
    private val forYou = ScreenSnapshot(TIKTOK_PACKAGE_NAME, null, setOf("view_rootview"), "Para você")
    private val atLimit = BlockSettings(true, true, dailyVerticalVideosUsageMillis = 900_000)

    @Test
    fun selectedForYouFeedUsesTheExistingWarningAndHardLimit() {
        fun action(settings: BlockSettings) = engine.verticalVideoAction(
            settings, engine.isEnabledVerticalVideo(settings, forYou), nowMillis = 1_000
        )
        assertEquals(VerticalVideoAction.NONE, action(atLimit.copy(dailyVerticalVideosUsageMillis = 899_999)))
        assertEquals(VerticalVideoAction.SHOW_WARNING, action(atLimit))
        assertEquals(VerticalVideoAction.GO_BACK, action(atLimit.copy(hardBlockVerticalVideos = true)))
        assertEquals(VerticalVideoAction.NONE, action(atLimit.copy(tikTokForYouEnabled = false)))
    }

    @Test
    fun otherTabsAndScreensWithoutThePlayerAreFreeAtTheLimit() {
        val inbox = ScreenSnapshot(TIKTOK_PACKAGE_NAME, null, setOf("opg"))
        val profile = ScreenSnapshot(TIKTOK_PACKAGE_NAME, null, setOf("oph"))
        for (screen in listOf(inbox, profile, forYou.copy(selectedTikTokTab = "Seguindo"),
            forYou.copy(selectedTikTokTab = null), forYou.copy(resourceIds = emptySet()),
            forYou.copy(packageName = "com.example.other"))
        ) {
            assertFalse(engine.shouldBlock(atLimit, screen, nowMillis = 1_000))
        }
    }
}
