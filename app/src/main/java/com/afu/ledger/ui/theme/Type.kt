package com.afu.ledger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.afu.ledger.R

/**
 * 标题字体：得意黑 Smiley Sans（SIL Open Font License 1.1）。
 *
 * ## 为什么用它
 *
 * 系统默认字体是中性无衬线，界面再圆润也「不像贴纸」。得意黑是现成的开源中文标题字，
 * 字形倾斜、笔画圆头、重心紧凑，天生带一点俏皮，和女仆鲸鱼娘立绘放在一起不打架。
 * 它只用在标题、金额与按钮上；正文仍用系统字体 —— 得意黑在小字号下可读性不如系统黑体，
 * 长段说明必须能一眼读清。
 *
 * 字体文件 2.6MB，放在 `res/font`，随 APK 打包，不依赖网络。
 * 许可证见仓库 `licenses/smiley-sans-OFL.txt`。
 */
val SmileySans = FontFamily(
    Font(R.font.smiley_sans, weight = FontWeight.Normal),
    Font(R.font.smiley_sans, weight = FontWeight.Medium),
    Font(R.font.smiley_sans, weight = FontWeight.SemiBold),
    Font(R.font.smiley_sans, weight = FontWeight.Bold),
)

/** 字体层级。标题/金额/按钮用得意黑，正文用系统黑体。 */
val AfuTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = SmileySans,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SmileySans,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SmileySans,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SmileySans,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SmileySans,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SmileySans,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)
