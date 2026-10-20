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
import com.afu.ledger.auto.keepalive.AutoLedgerKeepAlive
import com.afu.ledger.data.AppContainer
import com.afu.ledger.data.prefs.ThemeSettings
import com.afu.ledger.domain.model.TransType
import com.afu.ledger.ui.theme.AfuTheme
import com.afu.ledger.util.DateFormatter
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
    private val sessionDeduper: PaymentSessionDeduper,
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

    /** 用户在这张卡片上把方向改成了什么；null 表示用识别结果。 */
    private var directionOverride: TransType? = null

    /** 当前卡片对应的草稿与命中时刻，供「切方向后重建卡片」使用。 */
    private var currentDraft: PaymentDraft? = null
    private var currentHitAt: Long = 0L

    /** 卡片最近一次显示的时刻，用于「刚弹出时忽略换页信号」的冷却。 */
    private var shownAtMillis: Long = 0L

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

            // ② 会话级去重：同一笔付款在支付宝里会先后出现两个页面
            //    （「付款成功 / 聚合收款」有单号没商户，「支付成功」有商户没单号），
            //    两页指纹不同，只有按「金额 + 方向」的会话键才挡得住第二次。
            if (sessionDeduper.isDuplicate(draft)) {
                logger.log(draft.sourcePackage, "同一笔支付（跨页面），已忽略", draft.amount.cents, draft.merchant)
                return@launch
            }

            // ③ 进程内短时去重：页面反复刷新时不重复弹
            if (!deduper.shouldNotify(draft)) {
                logger.log(draft.sourcePackage, "同一笔支付，已忽略", draft.amount.cents, draft.merchant)
                return@launch
            }

            // ④ **先落待确认，再弹窗**。
            //    部分系统对支付类应用禁止悬浮窗（钱迹/iCost 的 FAQ 都写了），
            //    弹不出来就丢掉等于静默丢账。落了库之后，弹窗失败还能从通知与首页补确认。
            container.pendingBills.add(PendingBillStore.fromDraft(draft))

            // ⑤ 再记「刚弹过」（短有效期）：即使卡片被系统吞掉、用户没看见，
            //    十分钟内也不会被同一页面反复唤醒。
            processedBills.markShown(fingerprint)
            shownFingerprint = fingerprint
            showConfirm(draft, hitAt)
        }
    }

    fun hide() {
        timeoutJob?.cancel()
        shownFingerprint = null
        // 方向改选只对当前这张卡片有效，收起就清掉，避免影响下一笔
        directionOverride = null
        currentDraft = null
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

    /**
     * 页面变了（换页）：卡片属于上一页，直接收起。
     *
     * **但卡片刚弹出的一小段时间内必须忽略这个信号。**
     * 真机实测：点进账单后，微信的 WebView 会在卡片出现之后 1–3 秒再补发一次
     * `TYPE_VIEW_CLICKED`（页面加载收尾）。按「有点击就收起」处理，
     * 卡片弹出来一秒多就被自己关掉，用户根本来不及点「记一笔」——
     * 表现为「识别到了但卡片闪一下就没」。
     */
    fun onPageChanged() {
        if (!overlay.isShowing) return
        val shownFor = SystemClock.uptimeMillis() - shownAtMillis
        if (shownFor < PAGE_CHANGE_GRACE_MILLIS) {
            debugLog("ignore page change (shown for ${shownFor}ms)")
            return
        }
        dismiss("页面已切换")
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
        currentDraft = draft
        currentHitAt = hitAt
        val direction = directionOverride ?: draft.direction

        val accounts = container.accountRepository.observeActive().first()
        if (accounts.isEmpty()) {
            showNoAccount(draft, hitAt)
            return
        }

        // 分类必须跟着方向走：收入账单给支出分类，会让账本统计彻底错位
        val categories = container.categoryRepository.observeAll()
            .first()
            .filter { it.type == direction }
        if (categories.isEmpty()) {
            logger.log(
                draft.sourcePackage,
                if (direction == TransType.INCOME) "没有可用的收入分类" else "没有可用的支出分类",
                draft.amount.cents,
                draft.merchant,
            )
            return
        }

        val settings = settingsProvider()
        val accountId = settings.defaultAccountId
            ?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.first().id
        // 分类预选顺序：用户设置的默认分类 → 页面分类映射（支付宝「餐饮美食」→「餐饮」）
        // → 「其他」→ 第一个可用分类
        val mappedCategory = BillCategoryMapper.map(draft.pageCategory, direction)
        val categoryId = settings.defaultCategoryId
            ?.takeIf { id -> categories.any { it.id == id } }
            ?: mappedCategory?.let { name -> categories.firstOrNull { it.name == name } }?.id
            ?: categories.firstOrNull { it.name == RecordDetectedPaymentUseCase.FALLBACK_CATEGORY_NAME }?.id
            ?: categories.first().id

        overlayError.value = null
        debugLog("showConfirm accounts=${accounts.size} categories=${categories.size} direction=$direction")
        val shown = overlay.show(
            content = {
                AfuTheme(themeProvider()) {
                    PaymentConfirmContent(
                        amountText = MoneyFormatter.formatWithSymbol(draft.amount),
                        merchant = draft.merchant,
                        sourceLabel = draft.source,
                        directionLabel = if (direction == TransType.INCOME) "收入" else "支出",
                        billTimeText = DateFormatter.billTimeLabel(draft.occurredAt),
                        accounts = accounts,
                        categories = categories,
                        initialAccountId = accountId,
                        initialCategoryId = categoryId,
                        errorText = overlayError.value,
                        onDirectionChange = { toggleDirection() },
                        onConfirm = { account, category -> confirm(draft, account, category) },
                        onIgnore = { ignore(draft) },
                    )
                }
            },
            onFirstFrame = { composeMs -> logVisible(draft, hitAt, composeMs) },
        )
        if (!shown) {
            onOverlayBlocked(draft)
            return
        }
        shownAtMillis = SystemClock.uptimeMillis()
        startTimeout(draft)
    }

    /**
     * 卡片弹不出来（系统支付保护拦截悬浮窗）。
     *
     * 账单已经进了待确认库，这里只负责让用户「知道有这么一笔」：
     * 通知栏带计数，点进去就是本应用的待确认列表。
     */
    private fun onOverlayBlocked(draft: PaymentDraft) {
        logger.log(
            draft.sourcePackage,
            "弹窗被系统拦截，已存入待确认",
            draft.amount.cents,
            draft.merchant,
        )
        scope.launch {
            val count = runCatching { container.pendingBills.count() }.getOrDefault(1)
            AutoLedgerKeepAlive.showPendingNotification(service, count)
        }
    }

    /** 在卡片上切换收支方向，并按新方向重新加载分类。 */
    private fun toggleDirection() {
        val draft = currentDraft ?: return
        val current = directionOverride ?: draft.direction
        directionOverride = if (current == TransType.EXPENSE) TransType.INCOME else TransType.EXPENSE
        debugLog("direction toggled -> $directionOverride")
        val hitAt = currentHitAt
        scope.launch { showConfirm(draft, hitAt) }
    }

    private suspend fun showNoAccount(draft: PaymentDraft, hitAt: Long) {
        overlayError.value = null
        val shown = overlay.show(
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
        if (!shown) {
            onOverlayBlocked(draft)
            return
        }
        shownAtMillis = SystemClock.uptimeMillis()
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
        // 用用户在卡片上选定的方向入账，而不是识别结果本身
        val effective = draft.copy(direction = directionOverride ?: draft.direction)
        scope.launch {
            when (val result = container.recordDetectedPayment.execute(effective, accountId, categoryId)) {
                is RecordResult.Success -> {
                    // 入账成功才写长有效期：这张账单以后都不该再提示
                    processedBills.mark(fingerprint)
                    // 已经正式入账，待确认库里这条可以撤掉
                    container.pendingBills.remove(fingerprint)
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
        scope.launch {
            processedBills.mark(fingerprint)
            container.pendingBills.remove(fingerprint)
        }
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
        /**
         * 卡片无操作后的自动收起时间。
         *
         * 原来是 60 秒，真机上暴露一个问题：用户按**系统返回键**从账单页回到列表时，
         * 既不产生点击事件、前台包名也没变，卡片收不起来；而卡片存在期间识别被
         * `OVERLAY_SHOWING` 节流 —— 表现就是「这一分钟内点别的账单都不识别」。
         *
         * 20 秒足够选分类（真的要慢慢选可以再点一次账单重新弹），
         * 但不会让通道被堵整分钟。
         */
        const val OVERLAY_TIMEOUT_MILLIS = 20_000L
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        /**
         * 卡片弹出后的「换页信号冷却期」。
         *
         * 真机实测微信会在卡片出现后 1–3 秒补发一次点击事件（页面加载收尾），
         * 不冷却的话卡片会被自己关掉。3 秒足够覆盖这个尾巴，又不会长到
         * 让用户「真的翻页后卡片还赖着」。
         */
        const val PAGE_CHANGE_GRACE_MILLIS = 3_000L
    }
}
