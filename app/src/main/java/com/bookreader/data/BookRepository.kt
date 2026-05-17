package com.bookreader.data

import kotlinx.coroutines.flow.Flow

class BookRepository(private val dao: BookDao) {

    val allBooks: Flow<List<Book>> = dao.getAllBooks()

    suspend fun upsert(book: Book) = dao.upsert(book)

    suspend fun getBook(id: String): Book? = dao.getBook(id)

    suspend fun updateProgress(id: String, page: Int) =
        dao.updateProgress(id, page)

    suspend fun updateTotalPages(id: String, total: Int) =
        dao.updateTotalPages(id, total)

    suspend fun delete(book: Book) = dao.delete(book)
}
