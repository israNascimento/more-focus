package br.com.morefocus.blocking

class ShortsBlocker {
    fun isYouTubeShorts(screen: ScreenSnapshot): Boolean {
        if (screen.packageName != YOUTUBE_PACKAGE_NAME) return false

        return hasConclusiveResourceMatch(screen.resourceIds)
    }

    fun hasConclusiveResourceMatch(resourceIds: Set<String>): Boolean =
        resourceIds.any { it in SHORTS_VIEWER_RESOURCE_IDS }

    val resourceIdsOfInterest: Set<String> = SHORTS_VIEWER_RESOURCE_IDS

    private companion object {
        val SHORTS_VIEWER_RESOURCE_IDS = setOf(
            "reel_progress_bar",
            "reel_watch_fragment_root",
            "reel_watch_pager_view_pager",
            "reel_player_page_container"
        )
    }
}
