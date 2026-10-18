package com.afu.ledger.auto

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 把无障碍节点树压平成文本列表。
 *
 * 限制节点数、深度与总字符数：支付页信息密集，但没必要遍历到底，
 * 更不能因为遍历把对方页面卡住。
 *
 * ## 为什么必须按屏幕坐标排序
 *
 * 节点树的遍历顺序**不等于**视觉顺序。真机实测支付宝账单详情页：
 * 视觉上是「红烧牛肉面 / -21.00 / 交易成功」，而节点 BFS 顺序是
 * 「支出21元 / 交易成功 / … / 红烧牛肉面（第 22 行）」——
 * 商户名被排到了页面末尾。
 *
 * 下游的 [MerchantExtractor] 只取金额的前后两行，顺序一乱商户名就取不到
 * （实测表现：卡片上显示「未识别到商户」）。按 (top, left) 排序后，
 * 节点通道与 OCR 通道给出的行顺序一致，商户提取才稳定。
 */
object NodeTextCollector {

    private const val MAX_NODES = 1_500
    private const val MAX_DEPTH = 24
    private const val MAX_TEXT_LENGTH = 6_000

    fun collect(root: AccessibilityNodeInfo?): List<String> {
        if (root == null) return emptyList()

        val items = ArrayList<Item>(64)
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        var visited = 0
        var chars = 0
        var order = 0
        val rect = Rect()

        while (queue.isNotEmpty() && visited < MAX_NODES && chars < MAX_TEXT_LENGTH) {
            val (node, depth) = queue.removeFirst()
            visited++

            val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val description = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }

            if (text != null && items.lastOrNull()?.text != text) {
                node.getBoundsInScreen(rect)
                items.add(Item(text, rect.left, rect.top, rect.width(), rect.height(), order++))
                chars += text.length
            }
            if (description != null && description != text) {
                node.getBoundsInScreen(rect)
                items.add(
                    Item(description, rect.left, rect.top, rect.width(), rect.height(), order++),
                )
                chars += description.length
            }

            if (depth < MAX_DEPTH) {
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let { child -> queue.addLast(child to depth + 1) }
                }
            }
        }

        return items
            .sortedWith(compareBy({ it.sortTop }, { it.sortLeft }, { it.order }))
            .map { it.text }
    }

    /**
     * 文本 + 它在屏幕上的位置。
     *
     * 位置必须在构造时**拷贝**成 int：`getBoundsInScreen` 复用同一个 [Rect]，
     * 直接持有引用会让所有节点共享最后一份坐标。
     */
    private class Item(
        val text: String,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        val order: Int,
    ) {
        private val hasBounds: Boolean = width > 0 && height > 0

        /** 无 bounds 的节点（个别 H5 文本）排到最后，而不是跑到顶部冒充标题。 */
        val sortTop: Int = if (hasBounds) top else Int.MAX_VALUE / 2

        val sortLeft: Int = if (hasBounds) left else Int.MAX_VALUE / 2
    }
}
