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

        /**
         * 卡片不透明度的可调下限。
         *
         * 原为 0.35，实测在自定义背景图下：卡片几乎全透明，背景里的人像直接压在
         * 「自动记账」「默认账户」「餐饮」这些文字上，既读不清，滚动时整片区域
         * 每帧重新混合，观感就是「糊、卡」。
         *
         * 提到 0.6：背景图仍能明显透出来（用户要的「存在感」还在），
         * 但文字始终可读，半透明区域的合成量也小得多。
         */
        const val MIN_CARD_OPACITY: Float = 0.6f
        const val DEFAULT_CARD_OPACITY: Float = 0.95f
    }
}
