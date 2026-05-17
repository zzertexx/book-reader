package com.bookreader.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bookreader.data.Book
import com.bookreader.databinding.ItemBookBinding
import java.io.File

class BookAdapter(
    private val onClick: (Book) -> Unit,
    private val onLongClick: (Book) -> Boolean
) : ListAdapter<Book, BookAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ViewHolder(private val b: ItemBookBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(book: Book) {
            b.tvTitle.text = book.title
            val fileExists = File(book.filePath).exists()

            if (book.totalPages > 0) {
                val pct = (book.currentPage.toFloat() / book.totalPages * 100).toInt()
                b.tvProgress.text = "Page ${book.currentPage + 1} / ${book.totalPages}  ($pct%)"
                b.progressBar.progress = pct
                b.progressBar.visibility = android.view.View.VISIBLE
            } else {
                b.tvProgress.text = "Not opened yet"
                b.progressBar.visibility = android.view.View.GONE
            }

            b.ivMissing.visibility = if (fileExists) android.view.View.GONE else android.view.View.VISIBLE
            b.root.alpha = if (fileExists) 1f else 0.5f

            b.root.setOnClickListener { onClick(book) }
            b.root.setOnLongClickListener { onLongClick(book) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Book>() {
            override fun areItemsTheSame(a: Book, b: Book) = a.id == b.id
            override fun areContentsTheSame(a: Book, b: Book) = a == b
        }
    }
}
