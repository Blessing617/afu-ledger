package com.afu.ledger.ui.theme

import com.afu.ledger.data.prefs.ThemeSettings
import com.afu.ledger.ui.component.AvailableCategoryIcons
import com.afu.ledger.ui.component.categoryIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 主题风格与图标的纯逻辑部分。 */
class ThemeStyleTest {

    @Test
    fun `内置风格 id 唯一`() {
        val ids = ThemeStyles.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `只保留默认与大肥鱼两个风格`() {
        assertEquals(listOf("plain", "fatfish"), ThemeStyles.map { it.id })
    }

    @Test
    fun `未知风格回退到第一个`() {
        assertEquals(ThemeStyles.first(), themeStyle("no-such-style"))
    }

    @Test
    fun `已下线的风格回退到默认`() {
        // 之前版本的 sakura / midnight 已移除，旧数据读到时不能崩
        assertEquals("plain", themeStyle("sakura").id)
        assertEquals("plain", themeStyle("midnight").id)
    }

    @Test
    fun `大肥鱼风格默认显示吉祥物并有描边`() {
        val style = themeStyle("fatfish")
        assertTrue(style.showsMascot)
        assertTrue(style.outlineWidth.value > 0f)
        assertEquals(Decoration.BUBBLE, style.decoration)
        assertEquals(CardStyle.OUTLINED, style.cardStyle)
    }

    @Test
    fun `默认主题设置为清爽`() {
        val settings = ThemeSettings()
        assertEquals("plain", settings.themeStyleId)
        assertEquals(ThemeStyles.first(), themeStyle(settings.themeStyleId))
    }

    @Test
    fun `全部可选分类图标都有映射`() {
        AvailableCategoryIcons.forEach { key ->
            assertNotNull("icon key $key 未映射", categoryIcon(key))
        }
    }

    @Test
    fun `未知图标 key 回退而不是崩溃`() {
        assertNotNull(categoryIcon("definitely-unknown-key"))
        assertNotNull(categoryIcon(""))
    }
}
