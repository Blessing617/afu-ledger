package com.afu.ledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String,
    val initialBalanceCents: Long,
    val colorArgb: Long,
    val iconKey: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
)
