package com.bookreader

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bookreader.data.BookDatabase
import com.bookreader.data.BookRepository
import com.bookreader.databinding.ActivityReaderBinding
import com.github.barteksc.pdfviewer.listener.OnErrorListener
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File

class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PAGE = "page"
    }

    private lateinit var binding: ActivityReaderBinding
    private lateinit var repo: BookRepository
    private val sync = SyncManager()

    private lateinit var bookId: String
    private lateinit var filePath: String
    private lateinit var title: String
    private var startPage: Int = 0

    private var totalPages: Int = 0
    private var currentPage: Int = 0
    private var saveJob: Job? = null
    private var syncObserveJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: run { finish(); return }
        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run { finish(); return }
        title = intent.getStringExtra(EXTRA_TITLE) ?: "Book"
        startPage = intent.getIntExtra(EXTRA_PAGE, 0)

        supportActionBar?.title = title

        val db = BookDatabase.getInstance(this)
        repo = BookRepository(db.bookDao())

        resolveStartPageAndLoad()
    }

    private fun resolveStartPageAndLoad() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val remote = sync.fetchProgress(bookId)
            if (remote != null && remote.page > startPage) {
                startPage = remote.page
                showSyncedToast(remote.page, remote.totalPages)
            }
            loadPdf(startPage)
            startObservingRemoteProgress()
        }
    }

    private fun loadPdf(page: Int) {
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.pdfView.fromFile(file)
            .defaultPage(page)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .enableAntialiasing(true)
            .scrollHandle(DefaultScrollHandle(this))
            .spacing(4)
            .onLoad(OnLoadCompleteListener { pages ->
                binding.progressBar.visibility = View.GONE
                totalPages = pages
                currentPage = page
                lifecycleScope.launch {
                    repo.updateTotalPages(bookId, pages)
                }
                updatePageIndicator(page, pages)
            })
            .onPageChange(OnPageChangeListener { pageNum, pageCount ->
                currentPage = pageNum
                totalPages = pageCount
                updatePageIndicator(pageNum, pageCount)
                scheduleSave(pageNum)
            })
            .onError(OnErrorListener { t ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to open PDF: ${t.message}", Toast.LENGTH_SHORT).show()
                finish()
            })
            .load()
    }

    private fun scheduleSave(page: Int) {
        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            delay(1_500)
            repo.updateProgress(bookId, page)
            sync.pushProgress(bookId, title, page, totalPages)
        }
    }

    private fun startObservingRemoteProgress() {
        syncObserveJob = lifecycleScope.launch {
            sync.observeProgress(bookId)
                .catch { /* silently ignore network errors */ }
                .collect { remote ->
                    if (remote != null && remote.page != currentPage && remote.page > 0) {
                        showSyncPrompt(remote.page, remote.totalPages)
                    }
                }
        }
    }

    private fun showSyncedToast(page: Int, total: Int) {
        val msg = if (total > 0) "Resumed at page ${page + 1} / $total (from other device)"
                  else "Resumed at page ${page + 1} (synced)"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun showSyncPrompt(remotePage: Int, total: Int) {
        val msg = if (total > 0) "Other device is on page ${remotePage + 1} / $total. Jump there?"
                  else "Other device is on page ${remotePage + 1}. Jump there?"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Sync")
            .setMessage(msg)
            .setPositiveButton("Jump") { _, _ -> binding.pdfView.jumpTo(remotePage, true) }
            .setNegativeButton("Stay", null)
            .show()
    }

    private fun updatePageIndicator(page: Int, total: Int) {
        binding.tvPageIndicator.text = "${page + 1} / $total"
        if (total > 0) {
            binding.pageProgressBar.max = total - 1
            binding.pageProgressBar.progress = page
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        saveJob?.cancel()
        // Save immediately when leaving
        lifecycleScope.launch {
            repo.updateProgress(bookId, currentPage)
            sync.pushProgress(bookId, title, currentPage, totalPages)
        }
    }

    override fun onDestroy() {
        syncObserveJob?.cancel()
        super.onDestroy()
    }
}
