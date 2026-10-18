package com.afu.ledger.data.repository

import com.afu.ledger.data.local.dao.TransactionDao
import com.afu.ledger.data.repository.mapper.toDomain
import com.afu.ledger.data.repository.mapper.toEntity
import com.afu.ledger.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepository(
    private val transactionDao: TransactionDao,
) {

    fun observeAll(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun findById(id: Long): Transaction? =
        transactionDao.findById(id)?.toDomain()

    suspend fun add(transaction: Transaction): Long =
        transactionDao.insert(transaction.toEntity())

    suspend fun update(transaction: Transaction) =
        transactionDao.update(transaction.toEntity())

    suspend fun delete(id: Long) = transactionDao.deleteById(id)
}
