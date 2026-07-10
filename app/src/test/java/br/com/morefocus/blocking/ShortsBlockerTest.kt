package br.com.morefocus.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsBlockerTest {
    private val blocker = ShortsBlocker()

    @Test
    fun detectsYouTubeShortsFromAccessibleProgressBarId() {
        val screen = youtubeScreen(
            root = NodeSnapshot(
                viewIdResourceName = "com.google.android.youtube:id/reel_progress_bar"
            )
        )

        assertTrue(blocker.isYouTubeShorts(screen))
    }

    @Test
    fun detectsYouTubeShortsFromViewerStructureId() {
        val screen = youtubeScreen(
            root = NodeSnapshot(
                children = listOf(
                    NodeSnapshot(
                        viewIdResourceName = "com.google.android.youtube:id/reel_watch_fragment_root"
                    )
                )
            )
        )

        assertTrue(blocker.isYouTubeShorts(screen))
    }

    @Test
    fun doesNotDetectYouTubeShortsFromNavigationLabelOrTabId() {
        val screen = youtubeScreen(
            root = NodeSnapshot(
                text = "Shorts",
                children = listOf(
                    NodeSnapshot(
                        viewIdResourceName = "com.google.android.youtube:id/button_shorts_container"
                    )
                )
            )
        )

        assertFalse(blocker.isYouTubeShorts(screen))
    }

    @Test
    fun doesNotDetectMarkerFromAnotherPackage() {
        val screen = ScreenSnapshot(
            packageName = "com.example.video",
            className = null,
            root = NodeSnapshot(
                viewIdResourceName = "com.google.android.youtube:id/reel_progress_bar"
            )
        )

        assertFalse(blocker.isYouTubeShorts(screen))
    }

    private fun youtubeScreen(root: NodeSnapshot?): ScreenSnapshot =
        ScreenSnapshot(
            packageName = "com.google.android.youtube",
            className = null,
            root = root
        )
}
