package com.afu.ledger.auto

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import com.afu.ledger.AfuApplication
import com.afu.ledger.BuildConfig
import com.afu.ledger.MainActivity
import com.afu.ledger.auto.overlay.NoAccountCard
import com.afu.ledger.auto.overlay.PaymentConfirmContent
import com.afu.ledger.auto.overlay.PaymentConfirmOverlay
import com.afu.ledger.data.AppContainer
import com.afu.ledger.data.prefs.ThemeSettings
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.ui.theme.AfuTheme
import com.afu.ledger.util.MoneyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 自动记账的无障碍服务。
 *
 * 职责很窄：收事件 → 去抖 → 把窗口文本交给 [PaymentPageDetector] →
 * 命中就弹确认窗。业务判断与解析都在可单测的纯 Kotlin 层。
 */
class AutoLedgerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val detector = PaymentPageDetector(
        debugPackageName = if (BuildConfig.DEBUG) BuildConfig.APPLICATION_ID else null,
    )
    private val deduper = DetectionDeduper()
    private val overlayError = mutableStateOf<String?>(null)

    private var container: AppContainer? = null
    private var overlay: PaymentConfirmOverlay? = null
    private var pendingJob: Job? = null
    private var timeoutJob: Job? = null

    @Volatile
    private var settings = AutoLedgerSettings()

    @Volatile
    private var themeSettings = ThemeSettings()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as AfuApplication
        container = app.container
        // 系统可能重复绑定；复用实例，别把还活着的悬浮窗引用丢了
        if (overlay == null) {
            overlay = PaymentConfirmOverlay(this)
        }

        scope.launch {
            app.container.autoLedgerSettings.settings.collect { settings = it }
        }
        scope.launch {
            app.container.themePreferences.settings.collect { themeSettings = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        debugLog("event pkg=$packageName enabled=${settings.enabled} type=${event.eventType}")
        if (!settings.enabled) return
        if (!isSupportedPackage(packageName)) return

        // 页面渲染往往连续触发多个事件，合并到一次识别
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(DEBOUNCE_MILLIS)
            inspectWindow(packageName)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        pendingJob?.cancel()
        timeoutJob?.cancel()
        overlay?.hide()
        scope.cancel()
        super.onDestroy()
    }

    private fun isSupportedPackage(packageName: String): Boolean =
        packageName in PaymentPageRules.supportedPackages ||
            (BuildConfig.DEBUG && packageName == this.packageName)

    private fun inspectWindow(packageName: String) {
        val snapshot = PageSnapshot(packageName, NodeTextCollector.collect(rootInActiveWindow))
        debugLog("inspect texts=${snapshot.texts.size} ${snapshot.texts.take(10)}")
        when (val result = detector.detect(snapshot)) {
            is DetectionResult.Hit -> handleHit(result.draft)
            is DetectionResult.Missed -> {
                debugLog("miss ${result.reason.name}")
                logMiss(packageName, result.reason)
            }
        }
    }

    private fun handleHit(draft: PaymentDraft) {
        debugLog("hit amount=${draft.amount.cents} merchant=${draft.merchant}")

        // 已经有待确认卡片时不再叠加：服务被系统重绑后内存去重会重置，
        // 这里用「卡片是否还在」兜底，避免同一页弹两次。
        if (overlay?.isShowing == true) return

        if (!deduper.shouldNotify(draft)) {
            log(draft.sourcePackage, "同一笔支付，已忽略", draft.amount.cents, draft.merchant)
            return
        }
        scope.launch { showConfirm(draft) }
    }

    private suspend fun showConfirm(draft: PaymentDraft) {
        val app = container ?: return

        val accounts = app.accountRepository.observeActive().first()
        if (accounts.isEmpty()) {
            showNoAccount(draft)
            return
        }

        val categories = app.categoryRepository.observeAll()
            .first()
            .filter { it.type == TransType.EXPENSE }
        if (categories.isEmpty()) {
            log(draft.sourcePackage, "没有可用的支出分类", draft.amount.cents, draft.merchant)
            return
        }

        val accountId = settings.defaultAccountId
            ?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.first().id
        val categoryId = settings.defaultCategoryId
            ?.takeIf { id -> categories.any { it.id == id } }
            ?: categories.firstOrNull { it.name == RecordDetectedPaymentUseCase.FALLBACK_CATEGORY_NAME }?.id
            ?: categories.first().id

        overlayError.value = null
        debugLog("showConfirm accounts=${accounts.size} categories=${categories.size}")
        overlay?.show {
            AfuTheme(themeSettings) {
                PaymentConfirmContent(
                    amountText = MoneyFormatter.formatWithSymbol(draft.amount),
                    merchant = draft.merchant,
                    accounts = accounts,
                    categories = categories,
                    initialAccountId = accountId,
                    initialCategoryId = categoryId,
                    errorText = overlayError.value,
                    onConfirm = { account, category -> confirm(draft, account, category) },
                    onIgnore = { ignore(draft) },
                )
            }
        }
        startTimeout(draft)
    }

    private fun showNoAccount(draft: PaymentDraft) {
        overlayError.value = null
        overlay?.show {
            AfuTheme(themeSettings) {
                NoAccountCard(
                    amountText = MoneyFormatter.formatWithSymbol(draft.amount),
                    onOpenApp = { openMainActivity() },
                    onIgnore = { ignore(draft) },
                )
            }
        }
        startTimeout(draft)
    }

    private fun confirm(draft: PaymentDraft, accountId: Long, categoryId: Long) {
        val app = container ?: return
        timeoutJob?.cancel()
        scope.launch {
            when (val result = app.recordDetectedPayment.execute(draft, accountId, categoryId)) {
                is RecordResult.Success -> {
                    log(draft.sourcePackage, "已入账", draft.amount.cents, draft.merchant)
                    overlay?.hide()
                    toast("已记一笔 ${MoneyFormatter.formatWithSymbol(draft.amount)}")
                }

                RecordResult.NoAccount -> {
                    overlayError.value = "账户不可用，请重新选择"
                    log(draft.sourcePackage, "入账失败：账户不可用", draft.amount.cents, draft.merchant)
                    startTimeout(draft)
                }

                is RecordResult.Failed -> {
                    overlayError.value = result.message
                    log(draft.sourcePackage, "入账失败：${result.message}", draft.amount.cents, draft.merchant)
                    startTimeout(draft)
                }
            }
        }
    }

    private fun ignore(draft: PaymentDraft) {
        log(draft.sourcePackage, "用户忽略", draft.amount.cents, draft.merchant)
        timeoutJob?.cancel()
        overlay?.hide()
    }

    private fun startTimeout(draft: PaymentDraft) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(OVERLAY_TIMEOUT_MILLIS)
            if (overlay?.isShowing == true) {
                log(draft.sourcePackage, "超时未确认", draft.amount.cents, draft.merchant)
                overlay?.hide()
            }
        }
    }

    private fun openMainActivity() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        overlay?.hide()
    }

    private fun logMiss(packageName: String, reason: MissReason) {
        if (reason == MissReason.UNSUPPORTED_PACKAGE) return
        log(packageName, reason.label)
    }

    private fun log(
        packageName: String,
        outcome: String,
        amountCents: Long? = null,
        merchant: String? = null,
        detail: String? = null,
    ) {
        if (!settings.recognitionLogEnabled) return
        container?.recognitionLog?.record(
            RecognitionEntry(
                at = System.currentTimeMillis(),
                packageName = packageName,
                outcome = outcome,
                amountCents = amountCents,
                merchant = merchant,
                detail = detail,
            ),
        )
    }

    private fun toast(text: String) {
        runCatching { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "AutoLedger"
        const val DEBOUNCE_MILLIS = 400L
        const val OVERLAY_TIMEOUT_MILLIS = 60_000L
    }
}
