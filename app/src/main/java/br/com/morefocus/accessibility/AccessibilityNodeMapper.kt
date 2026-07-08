package br.com.morefocus.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import br.com.morefocus.blocking.NodeSnapshot

object AccessibilityNodeMapper {
    fun from(root: AccessibilityNodeInfo?): NodeSnapshot? {
        if (root == null) return null

        var visitedNodes = 0

        fun mapNode(node: AccessibilityNodeInfo, depth: Int): NodeSnapshot {
            visitedNodes += 1
            val children = if (depth < MAX_DEPTH && visitedNodes < MAX_NODES) {
                (0 until node.childCount).mapNotNull { index ->
                    if (visitedNodes >= MAX_NODES) {
                        null
                    } else {
                        node.getChild(index)?.let { child -> mapNode(child, depth + 1) }
                    }
                }
            } else {
                emptyList()
            }

            return NodeSnapshot(
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                viewIdResourceName = node.viewIdResourceName,
                className = node.className?.toString(),
                children = children
            )
        }

        return mapNode(root, depth = 0)
    }

    private const val MAX_DEPTH = 8
    private const val MAX_NODES = 80
}
