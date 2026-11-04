// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.doc

/** A document the reader shows page by page, whatever its original format. */
interface DocSource {
    val pageCount: Int

    /** Plain text of one page; paragraphs are separated by a blank line. */
    suspend fun text(page: Int): String

    /** Prepares a page in the background so that turning to it is instant. */
    suspend fun prefetch(page: Int) {}

    fun close() {}
}

/**
 * A document that was turned into paragraphs up front (EPUB, DOCX, web pages, …).
 * Paragraphs are packed into pages of roughly [pageChars] characters, never split
 * in the middle, so a page reads like a page of a book.
 */
class TextDocSource(paragraphs: List<String>, pageChars: Int = PAGE_CHARS) : DocSource {

    private val pages: List<String> = paginate(paragraphs, pageChars)

    override val pageCount: Int get() = pages.size

    override suspend fun text(page: Int): String = pages.getOrElse(page) { "" }

    companion object {
        const val PAGE_CHARS = 2200

        internal fun paginate(paragraphs: List<String>, pageChars: Int): List<String> {
            val out = ArrayList<String>()
            val sb = StringBuilder()
            for (p in paragraphs) {
                val para = p.trim()
                if (para.isEmpty()) continue
                if (sb.isNotEmpty() && sb.length + para.length > pageChars) {
                    out += sb.toString() + "\n"
                    sb.setLength(0)
                }
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append(para)
            }
            if (sb.isNotEmpty()) out += sb.toString() + "\n"
            return out.ifEmpty { listOf("") }
        }
    }
}
