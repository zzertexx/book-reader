package com.bookreader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: String): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: Book)

    @Query("UPDATE books SET currentPage = :page, lastOpenedAt = :time WHERE id = :id")
    suspend fun updateProgress(id: String, page: Int, time: Long = System.currentTimeMillis())

    @Query("UPDATE books SET totalPages = :total WHERE id = :id")
    suspend fun updateTotalPages(id: String, total: Int)

    @Delete
    suspend fun delete(book: Book)

    @Query("DELETE FROM books WHERE filePath = :path")
    suspend fun deleteByPath(path: String)
}
