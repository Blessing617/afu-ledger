package com.afu.ledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.afu.ledger.data.local.entity.ScheduleRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleRuleDao {

    @Insert
    suspend fun insert(rule: ScheduleRuleEntity): Long

    @Update
    suspend fun update(rule: ScheduleRuleEntity)

    @Query("SELECT * FROM schedule_rules ORDER BY enabled DESC, id DESC")
    fun observeAll(): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules ORDER BY enabled DESC, id DESC")
    suspend fun findAll(): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rules WHERE enabled = 1")
    suspend fun findEnabled(): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rules WHERE id = :id")
    suspend fun findById(id: Long): ScheduleRuleEntity?

    @Query("DELETE FROM schedule_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "UPDATE schedule_rules SET enabled = :enabled, pausedReason = :reason, updatedAt = :now " +
            "WHERE id = :id"
    )
    suspend fun setEnabled(id: Long, enabled: Boolean, reason: String?, now: Long)

    /** 推进期次游标：入账或忽略后都要调用，保证同一期次只处理一次。 */
    @Query(
        "UPDATE schedule_rules SET lastGeneratedEpochDay = :epochDay, generatedCount = :count, " +
            "enabled = :enabled, updatedAt = :now WHERE id = :id"
    )
    suspend fun advance(id: Long, epochDay: Long, count: Int, enabled: Boolean, now: Long)

    @Query("SELECT COUNT(*) FROM schedule_rules")
    suspend fun count(): Int

    @Query("DELETE FROM schedule_rules")
    suspend fun deleteAll()
}
