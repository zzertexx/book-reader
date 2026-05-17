package com.bookreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bookreader.data.Book
import com.bookreader.data.BookDatabase
import com.bookreader.data.BookRepository
import com.bookreader.databinding.ActivityMainBinding
import com.bookreader.ui.BookAdapter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: BookRepository
    private lateinit var adapter: BookAdapter

    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importPdf(it) }
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pickPdf.launch(arrayOf("application/pdf"))
        else Toast.makeText(this, "Storage permission needed to pick PDFs", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val db = BookDatabase.getInstance(this)
        repo = BookRepository(db.bookDao())

        adapter = BookAdapter(
            onClick = { book -> openBook(book) },
            onLongClick = { book -> showDeleteDialog(book); true }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.fab.setOnClickListener { pickPdfWithPermission() }

        lifecycleScope.launch {
            repo.allBooks.collect { books ->
                adapter.submitList(books)
                binding.emptyView.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val user = FirebaseAuth.getInstance().currentUser
        menu.findItem(R.id.action_account).title =
            if (user != null) user.displayName ?: user.email ?: "Account"
            else "Sign In"
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_account -> {
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) showSignOutDialog() else goToLogin()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun pickPdfWithPermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ — ACTION_OPEN_DOCUMENT doesn't need runtime permission
                pickPdf.launch(arrayOf("application/pdf"))
            }
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                pickPdf.launch(arrayOf("application/pdf"))
            }
            else -> requestPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun importPdf(uri: Uri) {
        lifecycleScope.launch {
            try {
                val fileName = queryFileName(uri) ?: "book_${System.currentTimeMillis()}.pdf"
                val title = fileName.removeSuffix(".pdf")
                val destFile = File(filesDir, "pdfs/$fileName")
                destFile.parentFile?.mkdirs()

                if (!destFile.exists()) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }
                }

                val id = md5(destFile.absolutePath)
                val existing = repo.getBook(id)
                if (existing != null) {
                    Toast.makeText(this@MainActivity, "$title already in library", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                repo.upsert(Book(id = id, title = title, filePath = destFile.absolutePath))
                Toast.makeText(this@MainActivity, "Added: $title", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openBook(book: Book) {
        if (!File(book.filePath).exists()) {
            Toast.makeText(this, "File not found. Re-import the PDF.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, ReaderActivity::class.java).apply {
            putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id)
            putExtra(ReaderActivity.EXTRA_FILE_PATH, book.filePath)
            putExtra(ReaderActivity.EXTRA_TITLE, book.title)
            putExtra(ReaderActivity.EXTRA_PAGE, book.currentPage)
        }
        startActivity(intent)
    }

    private fun showDeleteDialog(book: Book) {
        AlertDialog.Builder(this)
            .setTitle("Remove \"${book.title}\"?")
            .setMessage("The PDF copy inside the app will be deleted.")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    File(book.filePath).delete()
                    repo.delete(book)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSignOutDialog() {
        val user = FirebaseAuth.getInstance().currentUser
        AlertDialog.Builder(this)
            .setTitle("Account")
            .setMessage("Signed in as ${user?.email ?: user?.displayName}")
            .setPositiveButton("Sign Out") { _, _ ->
                FirebaseAuth.getInstance().signOut()
                goToLogin()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun queryFileName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
