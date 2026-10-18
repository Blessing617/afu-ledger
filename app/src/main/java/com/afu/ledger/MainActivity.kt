package com.afu.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afu.ledger.data.prefs.ThemeSettings
import com.afu.ledger.ui.AfuApp
import com.afu.ledger.ui.theme.AfuTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AfuApplication).container

        setContent {
            val themeSettings by container.themePreferences.settings
                .collectAsStateWithLifecycle(initialValue = ThemeSettings())

            AfuTheme(settings = themeSettings) {
                AfuApp(container = container)
            }
        }
    }
}
