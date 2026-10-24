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
     * 删掉「转入账户是该账户」的资产转移。
     *
     * 交易表的外键只覆盖 `accountId`（转出方），`transferAccountId` 不在外键里 ——
     * 不显式删就会留下一条转入方已不存在的悬空转移，余额聚合会把它算到一个不存在的账户上。
     */
    @Query("DELETE FROM transactions WHERE transferAccountId = :id")
    suspend fun deleteIncomingTransfers(id: Long)

    /** 删账户：先清掉以它为转入方的转移，再删账户（名下交易由外键级联删除）。 */
    @Transaction
    suspend fun deleteWithTransfers(id: Long) {
        deleteIncomingTransfers(id)
        deleteById(id)
    }

    /**
     * 该账户名下的交易笔数（含以它为转入方的资产转移）。
     *
     * 删除账户是级联删除（外键 `onDelete = CASCADE`），确认弹窗必须先告诉用户会连带删掉多少笔。
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id OR transferAccountId = :id")
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
     * 账户余额 = 初始余额 + Σ(收入) − Σ(支出) − Σ(转出) + Σ(转入)。
     *
     * 余额不落库，避免与交易表双写不一致。
     *
     * 转移的 JOIN 条件必须带上 `transferAccountId`：一笔转移对转出方是「自己名下的交易」，
     * 对转入方**只**出现在 `transferAccountId` 上。只 join `accountId` 会让转入方完全看不到这笔钱。
     * 一笔转移因此会 join 出两行（两个账户各一行），CASE 里用 `a.id` 判断「我站在哪一侧」。
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
               IFNULL(SUM(CASE
                   WHEN t.type = 'INCOME' THEN t.amountCents
                   WHEN t.type = 'EXPENSE' THEN -t.amountCents
                   WHEN t.type = 'TRANSFER' AND t.transferAccountId = a.id THEN t.amountCents
                   WHEN t.type = 'TRANSFER' THEN -t.amountCents
                   ELSE 0 END), 0)
               AS balanceCents
        FROM accounts a
        LEFT JOIN transactions t ON t.accountId = a.id OR t.transferAccountId = a.id
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
