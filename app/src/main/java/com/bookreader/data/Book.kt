package com.bookreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String,       // MD5 of file path
    val title: String,
    val filePath: String,
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val lastOpenedAt: Long = System.currentTimeMillis()
)
