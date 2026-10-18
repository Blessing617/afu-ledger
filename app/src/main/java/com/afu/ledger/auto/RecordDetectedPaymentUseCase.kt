package com.afu.ledger.auto

import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.data.repository.CategoryRepository
import com.afu.ledger.data.repository.TransactionRepository
import com.afu.ledger.domain.model.Category
import com.afu.ledger.domain.model.Transaction
import com.afu.ledger.domain.model.TransType
import kotlinx.coroutines.flow.first

/** 把识别结果写成一笔支出。复用现有 TransactionRepository，不新增写库路径。 */
class RecordDetectedPaymentUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) {

    /**
     * @param accountId 用户在悬浮窗里选定的账户
     * @param categoryId 用户选定的分类；为 null 或已失效时回退到支出「其他」
     */
    suspend fun execute(
        draft: PaymentDraft,
        accountId: Long,
        categoryId: Long?,
    ): RecordResult {
        val account = accountRepository.findById(accountId)
            ?: return RecordResult.NoAccount
        if (account.archived) return RecordResult.NoAccount

        val category = resolveCategory(categoryId)
            ?: return RecordResult.Failed("没有可用的支出分类")

        val now = System.currentTimeMillis()
        val transactionId = transactionRepository.add(
            Transaction(
                accountId = account.id,
                categoryId = category.id,
                type = TransType.EXPENSE,
                amount = draft.amount,
                note = draft.merchant.orEmpty(),
                occurredAt = draft.capturedAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return RecordResult.Success(transactionId)
    }

    private suspend fun resolveCategory(categoryId: Long?): Category? {
        val expenseCategories = categoryRepository.observeAll()
            .first()
            .filter { it.type == TransType.EXPENSE }

        categoryId?.let { id ->
            expenseCategories.firstOrNull { it.id == id }?.let { return it }
        }
        return expenseCategories.firstOrNull { it.name == FALLBACK_CATEGORY_NAME }
            ?: expenseCategories.firstOrNull()
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
