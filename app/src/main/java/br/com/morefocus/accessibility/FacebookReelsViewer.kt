package br.com.morefocus.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

internal fun hasFacebookReelsViewer(root: AccessibilityNodeInfo): Boolean {
    // Facebook 588 keeps the feed mounted behind the viewer and strips its player IDs.
    // Only follow branches large enough to contain the open Reel, leaving room for the viewer controls.
    val center = Rect().also(root::getBoundsInScreen)
    if (center.isEmpty) return false
    center.inset(center.width() / 8, center.height() / 8)
    val pending = ArrayDeque<AccessibilityNodeInfo>()
    val bounds = Rect()
    var hasNativePager = false
    pending.add(root)
    while (pending.isNotEmpty()) {
        val node = pending.removeLast()
        node.getBoundsInScreen(bounds)
        if (!bounds.contains(center)) continue
        if (node.isVisibleToUser && isFacebookReelPage(node.className, node.contentDescription, node.isClickable)) {
            return true
        }
        if (node.isVisibleToUser && node.isScrollable && node.className == "androidx.viewpager.widget.ViewPager") {
            hasNativePager = true
        }
        // The viewer is stacked after the underlying feed.
        for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
    }
    // The native replay player reached from a Reel link keeps its comments toggle below the pager.
    return hasNativePager && commentControlSearchTerms.any { term ->
        root.findAccessibilityNodeInfosByText(term).any { node ->
            node.isVisibleToUser && node.isClickable && node.contentDescription?.toString() in replayControls
        }
    }
}

internal fun isFacebookReelPage(className: CharSequence?, description: CharSequence?, clickable: Boolean): Boolean {
    if (clickable || description == null) return false
    val label = description.toString().substringBefore('\n')
    return when (className?.toString()) {
        "android.widget.Button" -> label == "Reel" || label.startsWith("Reel de ") || label.startsWith("Reel by ")
        "android.view.ViewGroup" -> label in swipeHints
        else -> false
    }
}

private val swipeHints = setOf("Reel. Deslize para cima para ver mais.", "Reel. Swipe up to see more.")
private val commentControlSearchTerms = listOf("detalhes e comentários", "details and comments")
private val replayControls = setOf(
    "Ocultar detalhes e comentários", "Expandir detalhes e comentários",
    "Hide details and comments", "Expand details and comments"
)
