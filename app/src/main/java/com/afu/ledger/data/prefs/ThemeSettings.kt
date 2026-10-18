package com.afu.ledger.data.prefs

/** 个性化设置。 */
data class ThemeSettings(
    val themeColorId: String = DEFAULT_COLOR_ID,
    val darkMode: DarkModeSetting = DarkModeSetting.SYSTEM,
    /** 自定义背景图的 content URI；null 表示使用主题色渐变背景。 */
    val backgroundUri: String? = null,
) {
    companion object {
        const val DEFAULT_COLOR_ID: String = "default"
    }
}
