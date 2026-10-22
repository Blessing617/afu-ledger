package com.afu.ledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * 该账户名下的交易笔数。
     *
     * 删除账户是级联删除（外键 `onDelete = CASCADE`），确认弹窗必须先告诉用户会连带删掉多少笔。
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id")
    suspend fun countTransactions(id: Long): Int

    /** 下一个排序值：追加到列表末尾。 */
    @Query("SELECT IFNULL(MAX(sortOrder), -1) + 1 FROM accounts")
    suspend fun nextSortOrder(): Int

    @Query("UPDATE accounts SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    /**
     * 按给定顺序重排。
     *
     * 放在一个事务里：逐条更新时界面会按 sortOrder 实时查询并重组列表，
     * 中间态会出现「两行同序、顺序闪一下」。
     */
    @Transaction
    suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

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
