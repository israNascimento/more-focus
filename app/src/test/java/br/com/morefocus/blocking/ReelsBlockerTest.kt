package br.com.morefocus.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelsBlockerTest {
    private val engine = BlockDecisionEngine(ReelsDetector())

    @Test
    fun blocksInstagramReelsWhenFocusAndRuleAreEnabled() {
        val screen = ScreenSnapshot(
            packageName = "com.instagram.android",
            className = "com.instagram.clips.viewer.ClipsViewerActivity",
            root = NodeSnapshot(
                text = "Reels",
                children = listOf(
                    NodeSnapshot(contentDescription = "Curtir"),
                    NodeSnapshot(contentDescription = "Comentar"),
                    NodeSnapshot(contentDescription = "Compartilhar")
                )
            )
        )

        assertTrue(
            engine.shouldBlock(
                settings = BlockSettings(focusEnabled = true, instagramReelsEnabled = true),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun doesNotBlockInstagramFeedProfileOrMessages() {
        val settings = BlockSettings(focusEnabled = true, instagramReelsEnabled = true)

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
                settings = BlockSettings(focusEnabled = false, instagramReelsEnabled = true),
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
                settings = BlockSettings(focusEnabled = true, instagramReelsEnabled = false),
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
                    pauseUntilMillis = 1_300L
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
                    pauseUntilMillis = 999L
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
                settings = BlockSettings(focusEnabled = true, instagramReelsEnabled = true),
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
            root = NodeSnapshot(
                viewIdResourceName = "com.instagram.android:id/clips_viewer_container",
                children = listOf(
                    NodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_video_player"),
                    NodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_ufi_like_button"),
                    NodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_ufi_comments_button"),
                    NodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_ufi_share_button")
                )
            )
        )

        assertTrue(
            engine.shouldBlock(
                settings = BlockSettings(focusEnabled = true, instagramReelsEnabled = true),
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
            root = NodeSnapshot(
                children = listOf(
                    NodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_viewer_video_layout"),
                    NodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_ufi_like_button"),
                    NodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_ufi_comments_button")
                )
            )
        )

        assertTrue(
            engine.shouldBlock(
                settings = BlockSettings(focusEnabled = true, instagramReelsEnabled = true),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun doesNotBlockSingleGenericClipsNavigationResourceId() {
        val screen = ScreenSnapshot(
            packageName = "com.instagram.android",
            className = "com.instagram.mainactivity.InstagramMainActivity",
            root = NodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_tab")
        )

        assertFalse(
            engine.shouldBlock(
                settings = BlockSettings(focusEnabled = true, instagramReelsEnabled = true),
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
            root = NodeSnapshot(
                children = listOf(
                    NodeSnapshot(viewIdResourceName = "com.instagram.android:id/like_button"),
                    NodeSnapshot(viewIdResourceName = "com.instagram.android:id/comment_button"),
                    NodeSnapshot(viewIdResourceName = "com.instagram.android:id/share_button")
                )
            )
        )

        assertFalse(
            engine.shouldBlock(
                settings = BlockSettings(focusEnabled = true, instagramReelsEnabled = true),
                screen = screen,
                nowMillis = 1_000L
            )
        )
    }

    private fun explicitReelsScreen(): ScreenSnapshot =
        ScreenSnapshot(
            packageName = "com.instagram.android",
            className = null,
            root = NodeSnapshot(
                viewIdResourceName = "com.instagram.android:id/clips_viewer_container",
                children = listOf(NodeSnapshot(viewIdResourceName = "com.instagram.android:id/clips_video_player"))
            )
        )

    private fun instagramScreen(label: String): ScreenSnapshot =
        ScreenSnapshot(
            packageName = "com.instagram.android",
            className = "com.instagram.mainactivity.InstagramMainActivity",
            root = NodeSnapshot(text = label)
        )
}
