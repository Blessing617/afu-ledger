package com.afu.ledger.auto

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 把无障碍节点树压平成文本列表。
 *
 * 限制节点数、深度与总字符数：支付页信息密集，但没必要遍历到底，
 * 更不能因为遍历把对方页面卡住。
 */
object NodeTextCollector {

    private const val MAX_NODES = 1_500
    private const val MAX_DEPTH = 24
    private const val MAX_TEXT_LENGTH = 6_000

    fun collect(root: AccessibilityNodeInfo?): List<String> {
        if (root == null) return emptyList()

        val result = ArrayList<String>(64)
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        var visited = 0
        var chars = 0

        while (queue.isNotEmpty() && visited < MAX_NODES && chars < MAX_TEXT_LENGTH) {
            val (node, depth) = queue.removeFirst()
            visited++

            val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val description = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }

            if (text != null && result.lastOrNull() != text) {
                result.add(text)
                chars += text.length
            }
            if (description != null && description != text) {
                result.add(description)
                chars += description.length
            }

            if (depth < MAX_DEPTH) {
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let { child -> queue.addLast(child to depth + 1) }
                }
            }
        }
        return result
    }
}
