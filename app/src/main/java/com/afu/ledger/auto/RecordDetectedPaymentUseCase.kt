package com.afu.ledger.auto

import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.data.repository.TransactionRepository
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Transaction
import com.afu.ledger.domain.model.TransType
import kotlinx.coroutines.flow.first

/**
 * 把识别结果写成一笔交易。复用现有 TransactionRepository，不新增写库路径。
 *
 * 两个曾经写错的点，这里显式修正：
 * 1. **方向**：旧实现硬编码 `TransType.EXPENSE`，入账账单必然记成支出。
 *    现在用 `draft.direction`。
 * 2. **时间**：旧实现用 `draft.capturedAt`（识别时刻），于是「昨天支付的账单今天点开确认」
 *    就记到今天。现在用 `draft.occurredAt`（页面上的账单时间）。
 */
class RecordDetectedPaymentUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) {

    /**
     * @param accountId 用户在悬浮窗里选定的账户
     * @param categoryId 用户选定的分类；为 null 或已失效时按方向回退
     */
    suspend fun execute(
        draft: PaymentDraft,
        accountId: Long,
        categoryId: Long?,
    ): RecordResult {
        val account = accountRepository.findById(accountId)
            ?: return RecordResult.NoAccount
        if (account.archived) return RecordResult.NoAccount

        val category = resolveCategory(categoryId, draft.direction)
            ?: return RecordResult.Failed(
                if (draft.direction == TransType.INCOME) "没有可用的收入分类" else "没有可用的支出分类",
            )

        val now = System.currentTimeMillis()
        val transactionId = transactionRepository.add(
            Transaction(
                accountId = account.id,
                categoryId = category.id,
                type = draft.direction,
                amount = draft.amount,
                note = draft.merchant.orEmpty(),
                // 账单上的时间，不是识别时刻
                occurredAt = draft.occurredAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return RecordResult.Success(transactionId)
    }

    private suspend fun resolveCategory(categoryId: Long?, direction: TransType): Category? {
        val categories = categoryRepository.observeAll()
            .first()
            .filter { it.type == direction }

        categoryId?.let { id ->
            categories.firstOrNull { it.id == id }?.let { return it }
        }
        return categories.firstOrNull { it.name == FALLBACK_CATEGORY_NAME }
            ?: categories.firstOrNull()
    }

    companion object {
        const val FALLBACK_CATEGORY_NAME: String = "其他"
    }
}

/** 入账结果。 */
sealed interface RecordResult {

    data class Success(val transactionId: Long) : RecordResult

    /** 没有可用账户，需要引导用户去创建。 */
    data object NoAccount : RecordResult

    data class Failed(val message: String) : RecordResult
}
