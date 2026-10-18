package com.afu.ledger.data.prefs

/** 个性化设置。 */
data class ThemeSettings(
    val themeColorId: String = DEFAULT_COLOR_ID,
    val darkMode: DarkModeSetting = DarkModeSetting.SYSTEM,
    /** 自定义背景图的 content URI；null 表示使用主题色渐变背景。 */
    val backgroundUri: String? = null,
    /** 背景可见度：0 = 几乎看不见，1 = 接近原图。 */
    val backgroundVisibility: Float = DEFAULT_VISIBILITY,
    /** 背景是否模糊（Android 12+ 生效，低版本自动忽略）。 */
    val backgroundBlur: Boolean = false,
    /** 是否叠加暗色遮罩以提升文字对比度。 */
    val backgroundScrim: Boolean = true,
    /** 内容卡片不透明度：由用户决定「要图还是要字」。 */
    val cardOpacity: Float = DEFAULT_CARD_OPACITY,
) {
    companion object {
        const val DEFAULT_COLOR_ID: String = "default"
        const val DEFAULT_VISIBILITY: Float = 0.5f

        /** 卡片不透明度的可调下限：再低就完全读不清了。 */
        const val MIN_CARD_OPACITY: Float = 0.35f
        const val DEFAULT_CARD_OPACITY: Float = 0.95f
    }
}
