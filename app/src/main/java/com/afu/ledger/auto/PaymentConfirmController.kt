package com.afu.ledger.auto

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 确认悬浮窗的编排：展示、入账、忽略、超时。
 *
 * 从无障碍服务里抽出来的原因：服务本身应该只管「收事件、发事件」，
 * 弹窗与入账是另一件事，混在一起会让服务文件失控。
 */
class PaymentConfirmController(
    private val service: AutoLedgerAccessibilityService,
    private val container: AppContainer,
    private val overlay: PaymentConfirmOverlay,
    private val deduper: DetectionDeduper,
    private val scope: CoroutineScope,
    private val settingsProvider: () -> AutoLedgerSettings,
    private val themeProvider: () -> ThemeSettings,
    private val logger: AutoLedgerLogger,
) {

    private val overlayError = mutableStateOf<String?>(null)
    private var timeoutJob: Job? = null

    val isShowing: Boolean get() = overlay.isShowing

    fun handleHit(draft: PaymentDraft) {
        // 命中时间点：用于测量「命中 → 悬浮窗可见」的真实延迟
        val hitAt = SystemClock.uptimeMillis()
        debugLog("hit amount=${draft.amount.cents} merchant=${draft.merchant}")

        // 已经有待确认卡片时不再叠加：服务被系统重绑后内存去重会重置，
        // 这里用「卡片是否还在」兜底，避免同一页弹两次。
        if (overlay.isShowing) return

        if (!deduper.shouldNotify(draft)) {
            logger.log(draft.sourcePackage, "同一笔支付，已忽略", draft.amount.cents, draft.merchant)
            return
        }
        scope.launch { showConfirm(draft, hitAt) }
    }

    fun hide() {
        timeoutJob?.cancel()
        overlay.hide()
    }

    private suspend fun showConfirm(draft: PaymentDraft, hitAt: Long) {
        val accounts = container.accountRepository.observeActive().first()
        if (accounts.isEmpty()) {
            showNoAccount(draft, hitAt)
            return
        }

        val categories = container.categoryRepository.observeAll()
            .first()
            .filter { it.type == TransType.EXPENSE }
        if (categories.isEmpty()) {
            logger.log(draft.sourcePackage, "没有可用的支出分类", draft.amount.cents, draft.merchant)
            return
        }

        val settings = settingsProvider()
        val accountId = settings.defaultAccountId
            ?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.first().id
        val categoryId = settings.defaultCategoryId
            ?.takeIf { id -> categories.any { it.id == id } }
            ?: categories.firstOrNull { it.name == RecordDetectedPaymentUseCase.FALLBACK_CATEGORY_NAME }?.id
            ?: categories.first().id

        overlayError.value = null
        debugLog("showConfirm accounts=${accounts.size} categories=${categories.size}")
        overlay.show(
            content = {
                AfuTheme(themeProvider()) {
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
            },
            onFirstFrame = { composeMs -> logVisible(draft, hitAt, composeMs) },
        )
        startTimeout(draft)
    }

    private fun showNoAccount(draft: PaymentDraft, hitAt: Long) {
        overlayError.value = null
        overlay.show(
            content = {
                AfuTheme(themeProvider()) {
                    NoAccountCard(
                        amountText = MoneyFormatter.formatWithSymbol(draft.amount),
                        onOpenApp = { openMainActivity() },
                        onIgnore = { ignore(draft) },
                    )
                }
            },
            onFirstFrame = { composeMs -> logVisible(draft, hitAt, composeMs) },
        )
        startTimeout(draft)
    }

    /** 记录「命中 → 可见」耗时：写入识别日志，供设置页与真机验收查询。 */
    private fun logVisible(draft: PaymentDraft, hitAt: Long, composeMs: Long) {
        val totalMs = SystemClock.uptimeMillis() - hitAt
        debugLog("overlay visible in ${totalMs}ms (compose ${composeMs}ms)")
        logger.log(
            packageName = draft.sourcePackage,
            outcome = "弹窗可见",
            amountCents = draft.amount.cents,
            merchant = draft.merchant,
            detail = "命中→可见 ${totalMs}ms（其中组合 ${composeMs}ms）",
        )
    }

    private fun confirm(draft: PaymentDraft, accountId: Long, categoryId: Long) {
        timeoutJob?.cancel()
        scope.launch {
            when (val result = container.recordDetectedPayment.execute(draft, accountId, categoryId)) {
                is RecordResult.Success -> {
                    logger.log(draft.sourcePackage, "已入账", draft.amount.cents, draft.merchant)
                    overlay.hide()
                    toast("已记一笔 ${MoneyFormatter.formatWithSymbol(draft.amount)}")
                }

                RecordResult.NoAccount -> {
                    overlayError.value = "账户不可用，请重新选择"
                    logger.log(draft.sourcePackage, "入账失败：账户不可用", draft.amount.cents, draft.merchant)
                    startTimeout(draft)
                }

                is RecordResult.Failed -> {
                    overlayError.value = result.message
                    logger.log(
                        draft.sourcePackage,
                        "入账失败：${result.message}",
                        draft.amount.cents,
                        draft.merchant,
                    )
                    startTimeout(draft)
                }
            }
        }
    }

    private fun ignore(draft: PaymentDraft) {
        logger.log(draft.sourcePackage, "用户忽略", draft.amount.cents, draft.merchant)
        hide()
    }

    private fun startTimeout(draft: PaymentDraft) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(OVERLAY_TIMEOUT_MILLIS)
            if (overlay.isShowing) {
                logger.log(draft.sourcePackage, "超时未确认", draft.amount.cents, draft.merchant)
                overlay.hide()
            }
        }
    }

    private fun openMainActivity() {
        runCatching {
            service.startActivity(
                Intent(service, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        overlay.hide()
    }

    private fun toast(text: String) {
        runCatching { Toast.makeText(service, text, Toast.LENGTH_SHORT).show() }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "AutoLedger"
        const val OVERLAY_TIMEOUT_MILLIS = 60_000L
    }
}
