package br.com.morefocus.blocking

internal const val TIKTOK_PACKAGE_NAME = "com.zhiliaoapp.musically"
internal const val TIKTOK_FEED_PAGE_ID = "view_rootview"

// On TikTok 47.7.3 the For You label is visible even when Following is selected.
internal fun ScreenSnapshot.isTikTokForYou(): Boolean =
    packageName == TIKTOK_PACKAGE_NAME && TIKTOK_FEED_PAGE_ID in resourceIds &&
        FOR_YOU_LABELS.any { it.equals(selectedTikTokTab, ignoreCase = true) }

private val FOR_YOU_LABELS = setOf("Para você", "For You", "Para ti")
