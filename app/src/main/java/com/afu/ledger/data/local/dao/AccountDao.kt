package com.afu.ledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.afu.ledger.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: Long): AccountEntity?

    /**
     * 账户余额 = 初始余额 + Σ(收入) − Σ(支出)。
     *
     * 余额不落库，避免与交易表双写不一致。
     */
    @Query(
        """
        SELECT a.id,
               a.name,
               a.type,
               a.initialBalanceCents,
               a.colorArgb,
               a.iconKey,
               a.archived,
               a.sortOrder,
               a.initialBalanceCents +
               IFNULL(SUM(CASE WHEN t.type = 'INCOME' THEN t.amountCents ELSE -t.amountCents END), 0)
               AS balanceCents
        FROM accounts a
        LEFT JOIN transactions t ON t.accountId = a.id
        GROUP BY a.id
        ORDER BY a.archived, a.sortOrder, a.id
        """
    )
    fun observeBalances(): Flow<List<AccountBalance>>

    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, id")
    suspend fun findAll(): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}
