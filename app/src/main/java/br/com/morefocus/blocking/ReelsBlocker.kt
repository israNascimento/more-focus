package br.com.morefocus.blocking

import android.util.Log

data class BlockSettings(
    val focusEnabled: Boolean,
    val instagramReelsEnabled: Boolean,
    val pauseUntilMillis: Long = 0L
)

data class ScreenSnapshot(
    val packageName: String?,
    val className: String?,
    val root: NodeSnapshot?
)

data class NodeSnapshot(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val className: String? = null,
    val children: List<NodeSnapshot> = emptyList()
) {
    fun allNodes(): Sequence<NodeSnapshot> = sequence {
        yield(this@NodeSnapshot)
        children.forEach { child -> yieldAll(child.allNodes()) }
    }
}

class BlockDecisionEngine(
    private val reelsDetector: ReelsDetector
) {
    fun shouldBlock(settings: BlockSettings, screen: ScreenSnapshot, nowMillis: Long): Boolean {
        if (!settings.focusEnabled) return false
        if (!settings.instagramReelsEnabled) return false
        if (settings.pauseUntilMillis > nowMillis) return false

        return reelsDetector.isInstagramReels(screen)
    }
}

class ReelsDetector {
    fun isInstagramReels(screen: ScreenSnapshot): Boolean {
        if (screen.packageName != INSTAGRAM_PACKAGE) {
            logDecision("not_instagram package=${screen.packageName}")
            return false
        }

        val className = screen.className.normalized()
        val classMarker = VIEWER_CLASS_MARKERS.firstOrNull { marker -> className.contains(marker) }
        if (classMarker != null) {
            logDecision("viewer_class marker=$classMarker className=$className")
            return true
        }

        val root = screen.root
        if (root == null) {
            logDecision("no_root className=$className")
            return false
        }
        val nodes = root.allNodes().toList()
        val resourceIds = nodes.mapNotNull { node -> node.resourceEntryName() }.toSet()
        val primaryViewerIds = resourceIds.filter { it in PRIMARY_VIEWER_RESOURCE_IDS }

        if (primaryViewerIds.isNotEmpty()) {
            logDecision(
                "primary_viewer_resource className=$className nodeCount=${nodes.size} " +
                    "resourceIds=${resourceIds.size} matched=$primaryViewerIds"
            )
            return true
        }

        val mediaIds = resourceIds.filter { it in MEDIA_RESOURCE_IDS }
        val viewerChromeIds = resourceIds.filter { it in VIEWER_CHROME_RESOURCE_IDS }
        val sideActionIds = resourceIds.filter { it in SIDE_ACTION_RESOURCE_IDS }

        if (
            mediaIds.isNotEmpty() &&
            (viewerChromeIds.isNotEmpty() || sideActionIds.size >= MIN_SIDE_ACTION_RESOURCE_IDS)
        ) {
            logDecision(
                "viewer_resource_combo className=$className nodeCount=${nodes.size} " +
                    "resourceIds=${resourceIds.size} media=$mediaIds chrome=$viewerChromeIds sideActions=$sideActionIds"
            )
            return true
        }

        logDecision(
            "no_match className=$className nodeCount=${nodes.size} resourceIds=${resourceIds.size}"
        )

        return false
    }

    private fun String?.normalized(): String = this
        ?.trim()
        ?.lowercase()
        .orEmpty()

    private fun NodeSnapshot.resourceEntryName(): String? {
        val viewId = viewIdResourceName.normalized()
        if (viewId.isBlank()) return null
        return viewId.substringAfterLast('/')
    }

    private fun logDecision(message: String) {
        runCatching { Log.d(TAG, "isInstagramReels $message") }
    }

    private companion object {
        const val TAG = "ReelsDetector"
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val MIN_SIDE_ACTION_RESOURCE_IDS = 2

        val VIEWER_CLASS_MARKERS = setOf(
            "clips.viewer",
            "clipsviewer",
            "clips_viewer",
            "reels.viewer",
            "reelsviewer",
            "reels_viewer"
        )

        val PRIMARY_VIEWER_RESOURCE_IDS = setOf(
            "clips_viewer_container",
            "clips_viewer_view_pager"
        )

        val MEDIA_RESOURCE_IDS = setOf(
            "clips_viewer_video_layout",
            "clips_video_player",
            "clips_video_layout",
            "clips_video_container",
            "reels_player_asset"
        )

        val SIDE_ACTION_RESOURCE_IDS = setOf(
            "clips_ufi_component",
            "clips_ufi_like_button",
            "clips_ufi_comments_button",
            "clips_ufi_share_button",
            "clips_ufi_more_button"
        )

        val VIEWER_CHROME_RESOURCE_IDS = setOf(
            "clips_viewer_action_bar",
            "clips_viewer_action_bar_title",
            "clips_viewer_camera_button",
            "clips_navigation_bar_container",
            "clips_navigation_bar_row",
            "clips_author_info_component",
            "clips_caption_component",
            "clips_viewer_media_info",
            "clips_viewer_media_info_container",
            "clips_cta"
        )
    }
}
