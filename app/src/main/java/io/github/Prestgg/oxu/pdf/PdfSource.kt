// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.pdf

import android.content.Context
import android.util.LruCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PdfSource private constructor(file: File) {

    // PDFBox is not thread safe: every access to [doc] holds [lock].
    private val lock = ReentrantLock()
    private var doc: PDDocument? = PDDocument.load(file)

    @Volatile
    private var closed = false

    private val cache = LruCache<Int, String>(24)

    val pageCount: Int = doc!!.numberOfPages

    companion object {
        suspend fun open(context: Context, file: File): PdfSource = withContext(Dispatchers.IO) {
            PDFBoxResourceLoader.init(context.applicationContext)
            PdfSource(file)
        }
    }

    suspend fun text(page: Int): String {
        cache.get(page)?.let { return it }
        return withContext(Dispatchers.IO) {
            lock.withLock {
                cache.get(page)?.let { return@withLock it }
                val d = doc
                if (closed || d == null || page !in 0 until pageCount) return@withLock ""
                val stripper = PDFTextStripper()
                stripper.startPage = page + 1
                stripper.endPage = page + 1
                val text = runCatching { stripper.getText(d) }.getOrDefault("")
                tidy(text).also { if (!closed) cache.put(page, it) }
            }
        }
    }

    /** Extracts a page in the background so that turning to it is instant. */
    suspend fun prefetch(page: Int) {
        if (page in 0 until pageCount && cache.get(page) == null) text(page)
    }

    private fun tidy(raw: String): String {
        if (raw.isBlank()) return ""
        val text = raw
            .replace("\r\n", "\n")
            .replace("­", "") // soft hyphen: invisible, must not split the word
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" *\n *"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .replace(Regex("(\\p{Ll})-\n(\\p{Ll})"), "$1$2")
            .trim()
        return if (text.isBlank()) "" else text + "\n"
    }

    /** Safe to call from the main thread: never waits for a running extraction. */
    fun close() {
        closed = true
        cache.evictAll()
        if (lock.tryLock()) {
            try { closeDoc() } finally { lock.unlock() }
        } else {
            // An extraction is running; close as soon as it is done.
            Thread { lock.withLock { closeDoc() } }.start()
        }
    }

    private fun closeDoc() {
        try { doc?.close() } catch (_: Exception) {}
        doc = null
    }
}
