package com.bookreader

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class RemoteProgress(val page: Int, val totalPages: Int, val updatedAt: Long)

class SyncManager {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val isSignedIn: Boolean get() = auth.currentUser != null
    val userId: String? get() = auth.currentUser?.uid

    private fun bookDoc(bookId: String) =
        db.collection("users")
            .document(userId ?: return db.collection("_null").document("_null"))
            .collection("books")
            .document(bookId)

    suspend fun pushProgress(bookId: String, bookTitle: String, page: Int, totalPages: Int) {
        if (!isSignedIn) return
        val data = mapOf(
            "title" to bookTitle,
            "currentPage" to page,
            "totalPages" to totalPages,
            "updatedAt" to System.currentTimeMillis(),
            "deviceId" to android.os.Build.MODEL
        )
        bookDoc(bookId).set(data, SetOptions.merge()).await()
    }

    suspend fun fetchProgress(bookId: String): RemoteProgress? {
        if (!isSignedIn) return null
        val snap = bookDoc(bookId).get().await()
        if (!snap.exists()) return null
        val page = (snap.getLong("currentPage") ?: 0).toInt()
        val total = (snap.getLong("totalPages") ?: 0).toInt()
        val ts = snap.getLong("updatedAt") ?: 0L
        return RemoteProgress(page, total, ts)
    }

    fun observeProgress(bookId: String): Flow<RemoteProgress?> = callbackFlow {
        if (!isSignedIn) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val reg: ListenerRegistration = bookDoc(bookId).addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            val page = (snap.getLong("currentPage") ?: 0).toInt()
            val total = (snap.getLong("totalPages") ?: 0).toInt()
            val ts = snap.getLong("updatedAt") ?: 0L
            trySend(RemoteProgress(page, total, ts))
        }
        awaitClose { reg.remove() }
    }

    fun signOut() = auth.signOut()
}
