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
            "reel_watch_fragment_root",
            "reel_progress_bar",
            "reel_watch_pager_view_pager",
            "reel_player_page_container"
        )
    }
}

// A feed card's accessibility label ends with the playback action, after the video title.
internal fun isYouTubeFeedShort(description: CharSequence?): Boolean =
    description?.toString()?.substringAfterLast(" - ", "")?.trim()?.lowercase() in SHORT_PLAYBACK_ACTIONS

private val SHORT_PLAYBACK_ACTIONS = setOf("ver o shorts", "ver o short", "play short", "watch short")
