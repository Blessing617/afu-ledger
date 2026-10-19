package com.afu.ledger.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

/** 主题色、深色模式、背景图的持久化读写。 */
class ThemePreferences(
    private val context: Context,
) {

    val settings: Flow<ThemeSettings> = context.themeDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            ThemeSettings(
                themeStyleId = prefs[KEY_THEME_STYLE]
                    ?.takeIf { raw -> raw.isNotBlank() }
                    ?: ThemeSettings.DEFAULT_STYLE_ID,
                themeColorId = prefs[KEY_THEME_COLOR] ?: ThemeSettings.DEFAULT_COLOR_ID,
                darkMode = prefs[KEY_DARK_MODE]
                    ?.let { raw -> runCatching { DarkModeSetting.valueOf(raw) }.getOrNull() }
                    ?: DarkModeSetting.SYSTEM,
                backgroundUri = prefs[KEY_BACKGROUND_URI],
                backgroundVisibility = prefs[KEY_BG_VISIBILITY] ?: ThemeSettings.DEFAULT_VISIBILITY,
                backgroundBlur = prefs[KEY_BG_BLUR] ?: false,
                backgroundScrim = prefs[KEY_BG_SCRIM] ?: true,
                // 夹到当前下限：旧版本允许拖到 0.35，那个档位在自定义背景图下文字不可读，
                // 读取时夹紧可以让老用户升级后直接获得可读的观感，不必手动调回去。
                cardOpacity = (prefs[KEY_CARD_OPACITY] ?: ThemeSettings.DEFAULT_CARD_OPACITY)
                    .coerceIn(ThemeSettings.MIN_CARD_OPACITY, 1f),
            )
        }

    suspend fun setThemeStyle(styleId: String) {
        context.themeDataStore.edit { it[KEY_THEME_STYLE] = styleId }
    }

    suspend fun setThemeColor(colorId: String) {
        context.themeDataStore.edit { it[KEY_THEME_COLOR] = colorId }
    }

    suspend fun setDarkMode(mode: DarkModeSetting) {
        context.themeDataStore.edit { it[KEY_DARK_MODE] = mode.name }
    }

    suspend fun setBackgroundUri(uri: String?) {
        context.themeDataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(KEY_BACKGROUND_URI) else prefs[KEY_BACKGROUND_URI] = uri
        }
    }

    suspend fun setBackgroundVisibility(value: Float) {
        context.themeDataStore.edit { it[KEY_BG_VISIBILITY] = value.coerceIn(0f, 1f) }
    }

    suspend fun setBackgroundBlur(enabled: Boolean) {
        context.themeDataStore.edit { it[KEY_BG_BLUR] = enabled }
    }

    suspend fun setBackgroundScrim(enabled: Boolean) {
        context.themeDataStore.edit { it[KEY_BG_SCRIM] = enabled }
    }

    suspend fun setCardOpacity(value: Float) {
        context.themeDataStore.edit {
            it[KEY_CARD_OPACITY] = value.coerceIn(ThemeSettings.MIN_CARD_OPACITY, 1f)
        }
    }

    /** 恢复默认：连背景显示参数一起重置。 */
    suspend fun resetBackground() {
        context.themeDataStore.edit { prefs ->
            prefs.remove(KEY_BACKGROUND_URI)
            prefs.remove(KEY_BG_VISIBILITY)
            prefs.remove(KEY_BG_BLUR)
            prefs.remove(KEY_BG_SCRIM)
        }
    }

    private companion object {
        val KEY_THEME_STYLE = stringPreferencesKey("theme_style_id")
        val KEY_THEME_COLOR = stringPreferencesKey("theme_color_id")
        val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        val KEY_BACKGROUND_URI = stringPreferencesKey("background_uri")
        val KEY_BG_VISIBILITY = floatPreferencesKey("background_visibility")
        val KEY_BG_BLUR = booleanPreferencesKey("background_blur")
        val KEY_BG_SCRIM = booleanPreferencesKey("background_scrim")
        val KEY_CARD_OPACITY = floatPreferencesKey("card_opacity")
    }
}
