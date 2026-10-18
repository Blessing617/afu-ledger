package com.afu.ledger.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.afu.ledger.ui.theme.AfuTheme
import com.afu.ledger.ui.theme.Spacing

/**
 * debug 专用：复刻支付页文案，用来触发真实的无障碍事件链路。
 *
 * 不参与发布构建，也不做任何真实支付：
 *
 * ```
 * adb shell am start -n com.afu.ledger/com.afu.ledger.debug.DemoPaymentActivity \
 *   --es scenario WECHAT_SUCCESS
 * ```
 */
class DemoPaymentActivity : ComponentActivity() {

    private val scenarioState = mutableStateOf(DemoScenario.WECHAT_SUCCESS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScenario(intent)
        setContent {
            AfuTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DemoPaymentContent(scenarioState.value)
                }
            }
        }
    }

    /** 重复 `am start --es scenario XXX` 时也要真的换页面，否则测不出来。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyScenario(intent)
    }

    private fun applyScenario(intent: Intent?) {
        scenarioState.value = DemoScenario.from(intent?.getStringExtra(EXTRA_SCENARIO))
    }

    companion object {
        const val EXTRA_SCENARIO = "scenario"
    }
}

@Composable
private fun DemoPaymentContent(scenario: DemoScenario) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "模拟页面 · ${scenario.label}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        scenario.texts.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = "debug 构建的测试页，不会真的扣款",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
