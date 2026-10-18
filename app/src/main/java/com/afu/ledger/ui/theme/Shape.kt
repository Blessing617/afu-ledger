package com.afu.ledger.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 全局形状。
 *
 * 设计约束：圆润、柔和。所有页面必须复用这里的形状，不要在组件里写死 dp。
 */
val AfuShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 卡片统一圆角。 */
val CardShape = RoundedCornerShape(20.dp)

/** 输入框统一圆角。 */
val InputShape = RoundedCornerShape(16.dp)

/** 胶囊形底衬：用于需要「浮在背景图上但仍可读」的窄条容器（如列表日期分组标题）。 */
val CapsuleShape = RoundedCornerShape(percent = 50)
