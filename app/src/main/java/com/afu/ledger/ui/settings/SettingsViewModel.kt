package com.afu.ledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afu.ledger.data.prefs.DarkModeSetting
import com.afu.ledger.data.prefs.ThemePreferences
import com.afu.ledger.data.prefs.ThemeSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    val settings: StateFlow<ThemeSettings> = themePreferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeSettings(),
    )

    fun setThemeColor(colorId: String) {
        viewModelScope.launch { themePreferences.setThemeColor(colorId) }
    }

    fun setDarkMode(mode: DarkModeSetting) {
        viewModelScope.launch { themePreferences.setDarkMode(mode) }
    }

    fun setBackgroundUri(uri: String?) {
        viewModelScope.launch { themePreferences.setBackgroundUri(uri) }
    }
}
