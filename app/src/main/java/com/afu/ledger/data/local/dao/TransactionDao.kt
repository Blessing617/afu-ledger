package com.afu.ledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.afu.ledger.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE occurredAt BETWEEN :fromInclusive AND :toExclusive " +
            "ORDER BY occurredAt DESC, id DESC"
    )
    fun observeBetween(fromInclusive: Long, toExclusive: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    @Query(
        "SELECT IFNULL(SUM(amountCents), 0) FROM transactions " +
            "WHERE type = :type AND occurredAt BETWEEN :fromInclusive AND :toExclusive"
    )
    suspend fun sumByType(type: String, fromInclusive: Long, toExclusive: Long): Long
}
