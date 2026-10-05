// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.pdf

import android.content.Context
import android.util.LruCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class PdfSource private constructor(private val file: File) {

    private val mutex = Mutex()
    private var doc: PDDocument? = null

    private val cache = object : LruCache<Int, String>(24) {}

    val pageCount: Int

    companion object {
        suspend fun open(context: Context, file: File): PdfSource = withContext(Dispatchers.IO) {
            PDFBoxResourceLoader.init(context.applicationContext)
            PdfSource(file)
        }
    }

    init {
        val d = load()
        pageCount = d.numberOfPages
    }

    private fun load(): PDDocument = doc ?: PDDocument.load(file).also { doc = it }

    suspend fun text(page: Int): String {
        cache.get(page)?.let { return it }
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val d = load()
                if (page !in 0 until d.numberOfPages) return@withLock ""
                val stripper = PDFTextStripper()
                stripper.startPage = page + 1
                stripper.endPage = page + 1
                val text = runCatching { stripper.getText(d) }.getOrDefault("")
                tidy(text).also { cache.put(page, it) }
            }
        }
    }

    private fun tidy(raw: String): String {
        if (raw.isBlank()) return ""
        val text = raw
            .replace("\r\n", "\n")
            .replace('­', ' ')
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" *\n *"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .replace(Regex("(\\p{Ll})-\n(\\p{Ll})"), "$1$2")
            .trim()
        return if (text.isBlank()) "" else text + "\n"
    }

    fun close() {
        try { doc?.close() } catch (_: Exception) {}
        doc = null
        cache.evictAll()
    }
}