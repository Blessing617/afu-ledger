package com.afu.ledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String,
    val iconKey: String,
    val colorArgb: Long,
    val sortOrder: Int = 0,
    val preset: Boolean = false,
)
