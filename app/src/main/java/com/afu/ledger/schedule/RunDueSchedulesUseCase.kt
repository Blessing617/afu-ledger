package com.afu.ledger.schedule

import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.data.repository.ScheduleRuleRepository
import com.afu.ledger.data.repository.TransactionRepository
import com.afu.ledger.domain.model.Money
import com.afu.ledger.domain.model.Transaction
import com.afu.ledger.domain.model.TransactionSource
import com.afu.ledger.domain.schedule.ScheduleBehavior
import com.afu.ledger.domain.schedule.ScheduleCalculator
import com.afu.ledger.domain.schedule.ScheduleRule
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * 把所有到期期次处理掉：能自动入账的直接入账，提醒型的落成待确认。
 *
 * ## 幂等
 *
 * - 规则上维护 `lastGeneratedDate` 游标，处理完一期就推进，处理过的期次不会再被算作到期
 * - 交易表上 `(scheduleRuleId, occurredAt)` 唯一索引兜底：闹钟、兜底任务、打开应用补算
 *   三条路径同时跑也不会写出两笔
 *
 * ## 补记
 *
 * 关机 / 任务没跑导致错过的期次会一次补齐（上限 [ScheduleCalculator.MAX_CATCH_UP]），
 * 每一笔的 `occurredAt` 都是**该期次的计划时刻**，并标 `backfilled = true`。
 *
 * 这个 use case 不做任何 IO 之外的判断：账户归档、分类删除这类「规则失效」只暂停规则，
 * 绝不生成指向不存在对象的账单。
 */
class RunDueSchedulesUseCase(
    private val rules: ScheduleRuleRepository,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val pending: PendingOccurrenceSink,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    data class Report(
        val autoPosted: Int = 0,
        val pendingCreated: Int = 0,
        /** 因为补记上限被跳过的期数。 */
        val skippedByLimit: Int = 0,
        /** 因账户/分类不可用而被暂停的规则数。 */
        val pausedRules: Int = 0,
        /** 达到结束条件的规则数。 */
        val finishedRules: Int = 0,
    ) {
        val changed: Boolean
            get() = autoPosted > 0 || pendingCreated > 0 || pausedRules > 0 || finishedRules > 0
    }

    suspend fun run(): Report {
        val all = rules.all()
        if (all.none { it.enabled }) return Report()

        val activeAccounts = accounts.observeActive().first().associateBy { it.id }
        val allCategories = categories.observeAll().first().associateBy { it.id }
        val nowLocal = now()

        var autoPosted = 0
        var pendingCreated = 0
        var skipped = 0
        var paused = 0
        var finished = 0

        all.filter { it.enabled }.forEach { rule ->
            val account = activeAccounts[rule.accountId]
            val category = allCategories[rule.categoryId]
            val pauseReason = when {
                account == null -> "账户不可用，已暂停"
                category == null -> "分类不可用，已暂停"
                else -> null
            }
            if (pauseReason != null) {
                rules.setEnabled(rule.id, false, pauseReason)
                paused++
                return@forEach
            }

            val due = ScheduleCalculator.dueOccurrences(rule, nowLocal)
            skipped += due.skipped
            if (due.isEmpty) return@forEach

            var cursor = rule.lastGeneratedDate
            var count = rule.generatedCount
            var stillEnabled = true

            for (date in due.dates) {
                val occurredAt = ScheduleCalculator.occurrenceMillis(date, rule.timeOfDay, zone())
                val backfilled = date.isBefore(nowLocal.toLocalDate())

                when (rule.behavior) {
                    ScheduleBehavior.AUTO_POST -> {
                        val id = transactions.addIgnoringDuplicate(
                            Transaction(
                                accountId = rule.accountId,
                                categoryId = rule.categoryId,
                                type = rule.type,
                                amount = Money(rule.amount.cents),
                                note = rule.note,
                                occurredAt = occurredAt,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                                source = TransactionSource.SCHEDULED,
                                scheduleRuleId = rule.id,
                                backfilled = backfilled,
                            ),
                        )
                        if (id > 0) autoPosted++
                    }

                    ScheduleBehavior.NEED_CONFIRM -> {
                        pending.add(
                            PendingOccurrenceStore.Occurrence(
                                ruleId = rule.id,
                                occurredAt = occurredAt,
                                amountCents = rule.amount.cents,
                                type = rule.type,
                                accountId = rule.accountId,
                                categoryId = rule.categoryId,
                                note = rule.note,
                                createdAt = 0L,
                            ),
                        )
                        pendingCreated++
                    }
                }

                cursor = date
                count += 1
                // 达到结束期数后必须立刻停：否则会把上一轮没算到的期次也补出来，
                // 「执行满 N 期」就形同虚设
                if (rule.maxOccurrences != null && count >= rule.maxOccurrences) {
                    stillEnabled = false
                    break
                }
            }

            rules.advance(rule.id, cursor ?: rule.startDate, count, stillEnabled)
            if (!stillEnabled) finished++
        }

        return Report(
            autoPosted = autoPosted,
            pendingCreated = pendingCreated,
            skippedByLimit = skipped,
            pausedRules = paused,
            finishedRules = finished,
        )
    }

    /** 恢复一条被暂停的规则。 */
    suspend fun resume(ruleId: Long) {
        val rule = rules.findById(ruleId) ?: return
        rules.setEnabled(rule.id, true, null)
    }

    /**
     * 恢复规则并选择是否补记暂停期间的期次。
     *
     * 「不补」的做法是把游标直接推到今天：用户换了账户/分类，恢复只是从现在继续，
     * 不让两个月前的一串账单突然冒出来。
     */
    suspend fun resumeWithCatchUp(ruleId: Long, catchUp: Boolean) {
        val rule = rules.findById(ruleId) ?: return
        if (catchUp) {
            rules.setEnabled(rule.id, true, null)
            return
        }
        val today = now().toLocalDate()
        val cursor = rule.lastGeneratedDate
        if (cursor == null || cursor.isBefore(today)) {
            rules.advance(rule.id, today, rule.generatedCount, true)
        } else {
            rules.setEnabled(rule.id, true, null)
        }
    }
}

/** 规则里没直接依赖的读取统一用 flow.first()，避免引入额外的快照概念。 */
