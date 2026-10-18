package com.afu.ledger.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color

/**
 * 在图片绘制之上叠加一层纯色遮罩。
 *
 * 与「图片层 + 额外遮罩 Box」相比，这里是同一次绘制里完成，
 * 少一层全屏合成 —— 背景保持原分辨率时，这一层的成本不可忽略。
 */
fun Modifier.drawWithScrim(scrimAlpha: Float, scrimColor: Color): Modifier =
    if (scrimAlpha <= 0f) {
        this
    } else {
        drawWithContent {
            drawContent()
            drawRect(color = scrimColor, alpha = scrimAlpha)
        }
    }
