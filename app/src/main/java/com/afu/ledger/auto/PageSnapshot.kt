package com.afu.ledger.auto

/**
 * 页面里的一条文本，带它在屏幕上的位置。
 *
 * **为什么要坐标**：无障碍节点树的顺序不等于视觉顺序。真机实测支付宝账单页里，
 * 值节点的 top 比它左边标签的 top 还小 7px：
 *
 * ```
 * top=1071 left=451  2026-10-19 18:01:22     ← 值
 * top=1078 left= 91  支付时间                 ← 标签
 * ```
 *
 * 只按 top 排序的扁平列表会变成「值在前、标签在后」，于是「支付时间」后面跟的是
 * 下一行的内容，字段提取必然错位。要还原「标签 → 值」必须靠坐标按视觉行分组。
 *
 * @param top 距屏幕顶部的像素；无视觉位置时为 [NO_POSITION]
 * @param left 距屏幕左侧的像素；无视觉位置时为 [NO_POSITION]
 * @param height 该行文字的高度（像素）；未知时为 0。
 *   OCR 通道能给出它，于是可以判断「这一行是不是页面上最大的字」——
 *   支付结果页的金额永远是全页字号的峰值，聊天正文里的数字则与正文同高。
 */
data class PageText(
    val text: String,
    val top: Int = NO_POSITION,
    val left: Int = NO_POSITION,
    val height: Int = 0,
) {
    val hasPosition: Boolean get() = top != NO_POSITION && left != NO_POSITION

    companion object {
        /** 没有视觉位置的文本（个别 H5 节点、合成的兜底行）。 */
        const val NO_POSITION: Int = Int.MAX_VALUE / 4
    }
}

/**
 * 当前窗口的文本快照。
 *
 * 识别逻辑只依赖这个类型，不依赖任何 Android 类，因此可以在 JVM 上直接单测。
 *
 * @param items 文本及其位置，已按 (top, left) 排序
 */
data class PageSnapshot(
    val packageName: String,
    val items: List<PageText>,
) {
    /** 只要文本的视图，供「不需要位置」的旧逻辑使用。 */
    val texts: List<String> get() = items.map { it.text }

    /** 是否所有文本都没有位置（OCR 之外的纯文本测试常见）。 */
    val hasPositions: Boolean get() = items.any { it.hasPosition }

    companion object {
        /**
         * 无坐标的便捷构造：按行号虚构坐标，保持顺序。
         *
         * 用于测试与只做文本判断的调用方；真实采集路径一律带坐标。
         */
        fun ofTexts(packageName: String, texts: List<String>): PageSnapshot =
            PageSnapshot(
                packageName = packageName,
                items = texts.mapIndexed { index, text -> PageText(text, top = index * 100, left = 0) },
            )
    }
}
