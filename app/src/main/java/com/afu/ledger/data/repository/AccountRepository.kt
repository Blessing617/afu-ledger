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

    suspend fun create(account: Account): Long = accountDao.insert(account.toEntity())

    suspend fun save(account: Account) = accountDao.update(account.toEntity())

    suspend fun setArchived(id: Long, archived: Boolean) {
        val current = accountDao.findById(id) ?: return
        accountDao.update(current.copy(archived = archived))
    }

    suspend fun delete(id: Long) = accountDao.deleteById(id)
}
