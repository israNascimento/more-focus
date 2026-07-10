package br.com.morefocus.blocking

class ShortsBlocker {
    fun isYouTubeShorts(screen: ScreenSnapshot): Boolean {
        if (screen.packageName != YOUTUBE_PACKAGE) return false

        return screen.root
            ?.allNodes()
            ?.mapNotNull { node -> node.resourceEntryName() }
            ?.any { resourceId -> resourceId in SHORTS_VIEWER_RESOURCE_IDS }
            ?: false
    }

    private fun NodeSnapshot.resourceEntryName(): String? {
        val viewId = viewIdResourceName?.trim().orEmpty()
        if (viewId.isBlank()) return null
        return viewId.substringAfterLast('/')
    }

    private companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"

        val SHORTS_VIEWER_RESOURCE_IDS = setOf(
            "reel_progress_bar",
            "reel_watch_fragment_root",
            "reel_watch_pager_view_pager",
            "reel_player_page_container"
        )
    }
}
