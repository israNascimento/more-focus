package br.com.morefocus.blocking

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
        if (screen.packageName != INSTAGRAM_PACKAGE) return false

        val className = screen.className.normalized()
        if (className.contains("clips") || className.contains("reels")) return true

        val root = screen.root ?: return false
        val nodes = root.allNodes().toList()
        val hasExplicitSurface = nodes.any { node ->
            val viewId = node.viewIdResourceName.normalized()
            viewId.contains("clips") || viewId.contains("reels")
        }
        if (hasExplicitSurface) return true

        val labels = nodes.flatMap { node ->
            listOf(node.text.normalized(), node.contentDescription.normalized())
        }.filter { it.isNotBlank() }

        val hasReelsTitle = labels.any { it == "reels" || it.contains("instagram reels") }
        val actionCount = labels.count { label -> label in REELS_ACTION_LABELS }
        val hasAudioLabel = labels.any { it.contains("audio original") || it.contains("original audio") }

        return hasReelsTitle && (actionCount >= MIN_REELS_ACTION_LABELS || hasAudioLabel)
    }

    private fun String?.normalized(): String = this
        ?.trim()
        ?.lowercase()
        .orEmpty()

    private companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val MIN_REELS_ACTION_LABELS = 2

        val REELS_ACTION_LABELS = setOf(
            "curtir",
            "like",
            "comentar",
            "comment",
            "compartilhar",
            "share"
        )
    }
}
