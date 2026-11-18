package br.com.morefocus.accessibility

import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityNodeScanner {
    fun resourceEntryNames(
        root: AccessibilityNodeInfo?,
        resourceIdsOfInterest: Set<String>,
        hasConclusiveMatch: (Set<String>) -> Boolean
    ): Set<String> {
        if (root == null || resourceIdsOfInterest.isEmpty()) return emptySet()

        val matches = mutableSetOf<String>()
        val pendingNodes = ArrayDeque<TraversalFrame>()
        pendingNodes.addLast(TraversalFrame(root, depth = 0))
        var visitedNodes = 0

        while (pendingNodes.isNotEmpty()) {
            val frame = pendingNodes.last()
            if (!frame.nodeVisited) {
                frame.nodeVisited = true
                visitedNodes += 1
                val resourceEntryName = frame.node.viewIdResourceName.resourceEntryName()
                if (
                    resourceEntryName != null &&
                    resourceEntryName in resourceIdsOfInterest
                ) {
                    matches.add(resourceEntryName)
                    if (hasConclusiveMatch(matches)) return matches
                }
            }

            if (
                frame.depth >= MAX_DEPTH ||
                visitedNodes >= MAX_NODES ||
                frame.nextChildIndex >= frame.childCount
            ) {
                pendingNodes.removeLast()
                continue
            }

            val child = frame.node.getChild(frame.nextChildIndex)
            frame.nextChildIndex += 1
            if (child != null) pendingNodes.addLast(TraversalFrame(child, frame.depth + 1))
        }

        return matches
    }

    private fun String?.resourceEntryName(): String? =
        this
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.substringAfterLast('/')

    private class TraversalFrame(
        val node: AccessibilityNodeInfo,
        val depth: Int,
        val childCount: Int = node.childCount,
        var nextChildIndex: Int = 0,
        var nodeVisited: Boolean = false
    )

    private const val MAX_DEPTH = 8
    private const val MAX_NODES = 80
}
