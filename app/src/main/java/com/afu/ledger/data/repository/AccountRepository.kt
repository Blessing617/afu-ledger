package com.afu.ledger.data.repository

import com.afu.ledger.data.local.dao.AccountDao
import com.afu.ledger.data.repository.mapper.toDomain
import com.afu.ledger.data.repository.mapper.toEntity
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.AccountWithBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountRepository(
    private val accountDao: AccountDao,
) {

    fun observeActive(): Flow<List<Account>> =
        accountDao.observeActive().map { list -> list.map { it.toDomain() } }

    fun observeAll(): Flow<List<Account>> =
        accountDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeWithBalance(): Flow<List<AccountWithBalance>> =
        accountDao.observeBalances().map { list -> list.map { it.toDomain() } }

    suspend fun findById(id: Long): Account? = accountDao.findById(id)?.toDomain()

    /** 新建账户追加到列表末尾，而不是全部挤在 sortOrder = 0。 */
    suspend fun create(account: Account): Long {
        val entity = account.toEntity().copy(sortOrder = accountDao.nextSortOrder())
        return accountDao.insert(entity)
    }

    suspend fun save(account: Account) = accountDao.update(account.toEntity())

    /** 该账户名下的交易笔数，用于删除确认弹窗。 */
    suspend fun countTransactions(accountId: Long): Int = accountDao.countTransactions(accountId)

    /** 按给定顺序重排账户。 */
    suspend fun reorder(idsInOrder: List<Long>) = accountDao.reorder(idsInOrder)

    /**
     * 删除账户。
     *
     * 交易表的外键是 `onDelete = CASCADE`，SQLite 会把该账户名下的交易一并删掉；
     * 以该账户为**转入方**的资产转移由 [AccountDao.deleteWithTransfers] 显式删除。
     * 调用方 MUST 先通过 [countTransactions] 告诉用户会影响多少笔。
     */
    suspend fun delete(id: Long) = accountDao.deleteWithTransfers(id)
}
