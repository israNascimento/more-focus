package br.com.morefocus.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsBlockerTest {
    private val blocker = ShortsBlocker()

    @Test
    fun feedCardsRequireTheShortPlaybackAction() {
        assertTrue(isYouTubeFeedShort("Título do vídeo - ver o Shorts"))
        assertTrue(isYouTubeFeedShort("A title - with a dash - Play Short"))
        assertFalse(isYouTubeFeedShort("Shorts"))
        assertFalse(isYouTubeFeedShort("Como criar Shorts - ver o vídeo"))
        assertFalse(isYouTubeFeedShort("Título - ver o Shorts - Mais opções"))
        assertFalse(isYouTubeFeedShort(null))
    }

    @Test
    fun detectsYouTubeShortsFromAccessibleProgressBarId() {
        val screen = youtubeScreen(resourceIds = setOf("reel_progress_bar"))

        assertTrue(blocker.isYouTubeShorts(screen))
    }

    @Test
    fun detectsYouTubeShortsFromViewerStructureId() {
        val screen = youtubeScreen(resourceIds = setOf("reel_watch_fragment_root"))

        assertTrue(blocker.isYouTubeShorts(screen))
    }

    @Test
    fun doesNotDetectYouTubeShortsFromNavigationLabelOrTabId() {
        val screen = youtubeScreen(resourceIds = setOf("button_shorts_container"))

        assertFalse(blocker.isYouTubeShorts(screen))
    }

    @Test
    fun doesNotDetectMarkerFromAnotherPackage() {
        val screen = ScreenSnapshot(
            packageName = "com.example.video",
            className = null,
            resourceIds = setOf("reel_progress_bar")
        )

        assertFalse(blocker.isYouTubeShorts(screen))
    }

    private fun youtubeScreen(resourceIds: Set<String>): ScreenSnapshot =
        ScreenSnapshot(
            packageName = "com.google.android.youtube",
            className = null,
            resourceIds = resourceIds
        )
}
