package com.afu.ledger.data.repository

import com.afu.ledger.data.local.dao.ScheduleRuleDao
import com.afu.ledger.data.repository.mapper.toDomain
import com.afu.ledger.data.repository.mapper.toEntity
import com.afu.ledger.domain.schedule.ScheduleRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class ScheduleRuleRepository(
    private val dao: ScheduleRuleDao,
) {

    fun observeAll(): Flow<List<ScheduleRule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun all(): List<ScheduleRule> = dao.findAll().map { it.toDomain() }

    suspend fun enabled(): List<ScheduleRule> = dao.findEnabled().map { it.toDomain() }

    suspend fun findById(id: Long): ScheduleRule? = dao.findById(id)?.toDomain()

    suspend fun create(rule: ScheduleRule): Long =
        dao.insert(rule.toEntity())

    suspend fun save(rule: ScheduleRule) = dao.update(rule.toEntity())

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** 暂停 / 恢复；恢复时清掉暂停原因。 */
    suspend fun setEnabled(id: Long, enabled: Boolean, reason: String? = null) =
        dao.setEnabled(id, enabled, if (enabled) null else reason, System.currentTimeMillis())

    /**
     * 推进一步期次游标。
     *
     * @param date 本次处理（入账或忽略）的期次
     * @param count 处理后的累计期数
     * @param enabled 达到结束条件时置 false
     */
    suspend fun advance(id: Long, date: LocalDate, count: Int, enabled: Boolean) =
        dao.advance(id, date.toEpochDay(), count, enabled, System.currentTimeMillis())
}
