// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu

import io.github.Prestgg.oxu.doc.Formats
import io.github.Prestgg.oxu.doc.Html
import io.github.Prestgg.oxu.doc.TextDocSource
import io.github.Prestgg.oxu.doc.WebReader
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocTest {

    @Test
    fun pagesNeverSplitAParagraph() {
        val paras = List(10) { i -> "Paragraph $i " + "x".repeat(500) }
        val pages = TextDocSource.paginate(paras, 1200)
        assertEquals(5, pages.size)
        assertEquals(paras.joinToString("\n\n"), pages.joinToString("\n\n") { it.trimEnd() })
    }

    @Test
    fun overlongParagraphGetsItsOwnPage() {
        val pages = TextDocSource.paginate(listOf("short", "y".repeat(5000), "tail"), 1000)
        assertEquals(listOf("short\n", "y".repeat(5000) + "\n", "tail\n"), pages)
    }

    @Test
    fun plainTextUtf8AndBlankLines() {
        val text = "Birinci abzas.\nDavamı.\n\n\nİkinci abzas — çox gözəl."
        assertEquals(listOf("Birinci abzas.\nDavamı.", "İkinci abzas — çox gözəl."), Formats.plainText(text.toByteArray()))
    }

    @Test
    fun plainTextWithoutBlankLinesIsOneParagraphPerLine() {
        assertEquals(listOf("one", "two"), Formats.plainText("one\r\ntwo\r\n".toByteArray()))
    }

    @Test
    fun plainTextFallsBackToWindows1251() {
        val bytes = "Привет, мир".toByteArray(charset("windows-1251"))
        assertEquals(listOf("Привет, мир"), Formats.plainText(bytes))
    }

    @Test
    fun htmlBlocksBecomeParagraphs() {
        val doc = Jsoup.parse(
            """<body><h1>Title</h1><p>First <b>bold</b>   text.</p><script>evil()</script>
               <ul><li>one</li><li>two<br>lines</li></ul><table><tr><td>a</td><td>b</td></tr></table></body>"""
        )
        assertEquals(listOf("Title", "First bold text.", "one", "two\nlines", "a · b"), Html.paragraphs(doc))
    }

    @Test
    fun webReaderKeepsTheArticleAndDropsTheRest() {
        val article = (1..6).joinToString("") { "<p>Sentence number $it of the article, with enough words to count as real text.</p>" }
        val html = """
            <html><head><title>Site | Story</title><meta property="og:title" content="The Story"></head><body>
            <header><a href="/">Home</a> <a href="/news">News</a></header>
            <nav><a href="/a">A</a></nav>
            <div class="page has-sidebar">
              <div class="content">$article</div>
              <div class="sidebar"><p>Popular posts you might like to read next time you visit.</p></div>
              <div class="share-buttons">Share on everything</div>
            </div>
            <footer>Copyright</footer></body></html>
        """
        val page = WebReader.extract(Jsoup.parse(html), "https://example.com/story")
        assertEquals("The Story", page.title)
        assertEquals("The Story", page.paragraphs.first())
        assertEquals(7, page.paragraphs.size)
        val all = page.paragraphs.joinToString(" ")
        assertFalse(all.contains("Popular posts"))
        assertFalse(all.contains("Share on"))
        assertFalse(all.contains("Copyright"))
        assertTrue(all.contains("Sentence number 6"))
    }

    @Test
    fun findsLinksInSharedText() {
        assertEquals("https://example.com/a?b=1", WebReader.findUrl("Look: https://example.com/a?b=1."))
        assertEquals(null, WebReader.findUrl("no link here"))
        assertEquals("https://example.com", WebReader.normalizeUrl(" example.com "))
        assertEquals("http://x.org", WebReader.normalizeUrl("http://x.org"))
    }
}
