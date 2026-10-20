package com.afu.ledger.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.afu.ledger.ui.theme.AfuTheme
import com.afu.ledger.ui.theme.Spacing

/**
 * debug 专用：复刻支付页文案，用来触发真实的无障碍 / 截图 OCR 链路。
 *
 * 不参与发布构建，也不做任何真实支付：
 *
 * ```
 * adb shell am start -n com.afu.ledger/com.afu.ledger.debug.DemoPaymentActivity \
 *   --es scenario WECHAT_OCR_FULL
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
                    val scenario = scenarioState.value
                    if (scenario.canvasRendered) {
                        CanvasDemoContent(scenario)
                    } else {
                        DemoPaymentContent(scenario)
                    }
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

/** 节点可读的模拟页：验证无障碍通道。 */
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

/**
 * 自绘模拟页：整页文字画在 Canvas 上，**不产生任何无障碍文本节点**。
 *
 * 这就是微信页面的行为。截图 OCR 只能从像素里读文字，所以这里必须真的画出字来。
 */
@Composable
private fun CanvasDemoContent(scenario: DemoScenario) {
    val textMeasurer = rememberTextMeasurer()
    val background = if (scenario.darkBackground) Color(0xFF1C1C1E) else Color(0xFFEDEDED)
    val foreground = if (scenario.darkBackground) Color(0xFF6B6B6B) else Color(0xFF191919)

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            var y = size.height * 0.18f
            scenario.texts.forEachIndexed { index, line ->
                val fontSize = when {
                    index <= 1 -> 34.sp
                    line.firstOrNull()?.let { it == '¥' || it == '-' } == true -> 56.sp
                    else -> 34.sp
                }
                val layout = textMeasurer.measure(
                    text = AnnotatedString(line),
                    style = TextStyle(
                        fontSize = fontSize,
                        color = foreground,
                        fontWeight = if (index <= 1) FontWeight.Bold else FontWeight.Normal,
                    ),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = (size.width - layout.size.width) / 2f,
                        y = y,
                    ),
                )
                y += layout.size.height * 2.2f
            }
        }
    }
}
