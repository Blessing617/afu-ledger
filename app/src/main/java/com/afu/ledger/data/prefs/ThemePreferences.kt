package com.afu.ledger.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
                themeColorId = prefs[KEY_THEME_COLOR] ?: ThemeSettings.DEFAULT_COLOR_ID,
                darkMode = prefs[KEY_DARK_MODE]
                    ?.let { raw -> runCatching { DarkModeSetting.valueOf(raw) }.getOrNull() }
                    ?: DarkModeSetting.SYSTEM,
                backgroundUri = prefs[KEY_BACKGROUND_URI],
            )
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

    private companion object {
        val KEY_THEME_COLOR = stringPreferencesKey("theme_color_id")
        val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        val KEY_BACKGROUND_URI = stringPreferencesKey("background_uri")
    }
}
