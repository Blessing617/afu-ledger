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

    suspend fun setArchived(id: Long, archived: Boolean) {
        val current = accountDao.findById(id) ?: return
        // 取消归档时重新排到末尾：否则它会带着旧 sortOrder 插回中间，
        // 用户会以为「刚恢复的账户跑到别的位置了」。
        val nextOrder = if (!archived) accountDao.nextSortOrder() else current.sortOrder
        accountDao.update(current.copy(archived = archived, sortOrder = nextOrder))
    }

    /** 按给定顺序重排账户（只在未归档账户之间拖动的结果）。 */
    suspend fun reorder(idsInOrder: List<Long>) = accountDao.reorder(idsInOrder)

    suspend fun delete(id: Long) = accountDao.deleteById(id)
}
