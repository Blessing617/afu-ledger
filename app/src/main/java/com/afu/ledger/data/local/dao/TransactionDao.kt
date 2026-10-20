package com.afu.ledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.afu.ledger.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    /**
     * 定时记账专用：靠 `(scheduleRuleId, occurredAt)` 唯一索引做幂等。
     *
     * 冲突时返回 -1，不抛异常 —— 同一期次被重复执行（闹钟 + 兜底任务 + 打开应用补算
     * 三条路径可能同时撞上）是正常情况，不该让调用方崩溃。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicate(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE scheduleRuleId = :ruleId ORDER BY occurredAt DESC")
    suspend fun findByRule(ruleId: Long): List<TransactionEntity>

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

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /**
     * 备份导出用的分页读取。
     *
     * 按 id 而不是 occurredAt 排序：id 是主键，OFFSET 分页不会因为排序不稳定而重复或漏行。
     */
    @Query("SELECT * FROM transactions ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
