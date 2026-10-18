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
 * 确认悬浮窗的编排：展示、入账、忽略、超时、收起。
 *
 * 从无障碍服务里抽出来的原因：服务本身应该只管「收事件、发事件」，
 * 弹窗与入账是另一件事，混在一起会让服务文件失控。
 */
class PaymentConfirmController(
    private val service: AutoLedgerAccessibilityService,
    private val container: AppContainer,
    private val overlay: PaymentConfirmOverlay,
    private val deduper: DetectionDeduper,
    private val processedBills: ProcessedBillStore,
    private val scope: CoroutineScope,
    private val settingsProvider: () -> AutoLedgerSettings,
    private val themeProvider: () -> ThemeSettings,
    private val logger: AutoLedgerLogger,
) {

    private val overlayError = mutableStateOf<String?>(null)
    private var timeoutJob: Job? = null

    /** 当前卡片对应的账单指纹；用于忽略 / 入账时写持久化记录。 */
    private var shownFingerprint: String? = null

    val isShowing: Boolean get() = overlay.isShowing

    fun handleHit(draft: PaymentDraft) {
        // 命中时间点：用于测量「命中 → 悬浮窗可见」的真实延迟
        val hitAt = SystemClock.uptimeMillis()
        debugLog("hit amount=${draft.amount.cents} merchant=${draft.merchant} tradeNo=${draft.tradeNo}")

        // 已经有待确认卡片时不再叠加
        if (overlay.isShowing) return

        scope.launch {
            val fingerprint = draft.fingerprint()

            // ① 持久化去重：同一张账单在 7 天内（或忽略过）不再打扰。
            //    这条是「忽略按钮真的有用」的基础 —— 进程重启、重开账单都不会复活。
            if (processedBills.contains(fingerprint)) {
                logger.log(draft.sourcePackage, "这笔账单已处理过", draft.amount.cents, draft.merchant)
                return@launch
            }

            // ② 进程内短时去重：页面反复刷新时不重复弹
            if (!deduper.shouldNotify(draft)) {
                logger.log(draft.sourcePackage, "同一笔支付，已忽略", draft.amount.cents, draft.merchant)
                return@launch
            }

            // ③ 先记「刚弹过」（短有效期）：即使卡片被系统吞掉、用户没看见，
            //    十分钟内也不会被同一页面反复唤醒。
            processedBills.markShown(fingerprint)
            shownFingerprint = fingerprint
            showConfirm(draft, hitAt)
        }
    }

    fun hide() {
        timeoutJob?.cancel()
        shownFingerprint = null
        overlay.hide()
    }

    /**
     * 前台窗口变了。
     *
     * 卡片是属于「刚才那个账单页」的，用户切走了就没有确认的意义。
     * 更重要的是：卡片一旦残留，识别通道会被 `OVERLAY_SHOWING` 节流，
     * 用户看到的就是「自动记账彻底不工作了」。
     *
     * 只认窗口状态变化事件，并跳过系统界面与自家包：
     * 状态栏、悬浮窗自身产生的点击事件每秒都有，按它们判定会把卡片立刻收起，
     * 用户根本来不及在卡片上选分类。
     */
    fun onWindowPackageChanged(packageName: String) {
        if (!overlay.isShowing) return
        if (packageName in PaymentPageRules.supportedPackages) return
        if (packageName == servicePackageName) return
        if (packageName == SYSTEM_UI_PACKAGE) return
        dismiss("已离开${PaymentPageRules.sourceLabel(packageName)}")
    }

    /** 页面变了（换页）：卡片属于上一页，直接收起。 */
    fun onPageChanged() {
        if (overlay.isShowing) dismiss("页面已切换")
    }

    /** 收起卡片。不属于用户的明确决定，所以不写长有效期。 */
    private fun dismiss(reason: String) {
        debugLog("dismiss overlay: $reason")
        hide()
    }

    /** 服务销毁时真正移除预热窗口，避免窗口泄漏。 */
    fun release() {
        timeoutJob?.cancel()
        overlay.release()
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
                        sourceLabel = draft.source,
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

    private suspend fun showNoAccount(draft: PaymentDraft, hitAt: Long) {
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
        val fingerprint = shownFingerprint ?: draft.fingerprint()
        scope.launch {
            when (val result = container.recordDetectedPayment.execute(draft, accountId, categoryId)) {
                is RecordResult.Success -> {
                    // 入账成功才写长有效期：这张账单以后都不该再提示
                    processedBills.mark(fingerprint)
                    logger.log(draft.sourcePackage, "已入账", draft.amount.cents, draft.merchant)
                    hide()
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
        val fingerprint = shownFingerprint ?: draft.fingerprint()
        // 先收起，再落盘：用户点「忽略」必须立刻有反馈，不能等一次 IO。
        hide()
        logger.log(draft.sourcePackage, "用户忽略", draft.amount.cents, draft.merchant)
        scope.launch { processedBills.mark(fingerprint) }
    }

    private fun startTimeout(draft: PaymentDraft) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(OVERLAY_TIMEOUT_MILLIS)
            if (overlay.isShowing) {
                logger.log(draft.sourcePackage, "超时未确认", draft.amount.cents, draft.merchant)
                hide()
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
        hide()
    }

    private fun toast(text: String) {
        runCatching { Toast.makeText(service, text, Toast.LENGTH_SHORT).show() }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    /** 自家包名：悬浮窗自身的点击事件不该被当成「切走了」。 */
    private val servicePackageName: String get() = service.packageName

    private companion object {
        const val TAG = "AutoLedger"
        const val OVERLAY_TIMEOUT_MILLIS = 60_000L
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
