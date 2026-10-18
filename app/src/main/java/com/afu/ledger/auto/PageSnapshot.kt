package com.afu.ledger.auto

/**
 * 当前窗口的文本快照。
 *
 * 无障碍节点树被压平成一段可测的文本列表，识别逻辑只依赖这个类型，
 * 不依赖任何 Android 类，因此可以在 JVM 上直接单测。
 */
data class PageSnapshot(
    val packageName: String,
    val texts: List<String>,
)
