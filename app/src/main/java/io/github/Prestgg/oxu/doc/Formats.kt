// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.doc

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile

enum class Format(val label: String) {
    PDF("PDF"), EPUB("EPUB"), DOCX("DOCX"), ODT("ODT"), FB2("FB2"), HTML("HTML"), TEXT("Text")
}

/** Document types the file picker offers and the app accepts from other apps. */
val SUPPORTED_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/epub+zip",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.oasis.opendocument.text",
    "application/x-fictionbook+xml",
    "application/x-fictionbook",
    "text/html",
    "application/xhtml+xml",
    "text/plain",
    "text/markdown",
    // Many file managers report unknown extensions (.fb2, .md, …) like this.
    "application/octet-stream"
)

object Formats {

    /** Picks the format from the file content first, then the name and the MIME type. */
    fun detect(file: File, name: String, mime: String?): Format? {
        val head = ByteArray(8)
        val n = file.inputStream().use { it.read(head) }
        val ext = name.substringAfterLast('.', "").lowercase()
        if (n >= 4 && head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() &&
            head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte()
        ) return Format.PDF
        if (n >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
            return runCatching { zipFormat(file) }.getOrNull()
        }
        return when {
            ext == "fb2" || mime?.contains("fictionbook") == true -> Format.FB2
            ext in setOf("html", "htm", "xhtml") || mime == "text/html" || mime == "application/xhtml+xml" -> Format.HTML
            ext in setOf("txt", "md", "markdown", "text") || mime?.startsWith("text/") == true -> Format.TEXT
            else -> sniffText(file)
        }
    }

    private fun zipFormat(file: File): Format? = ZipFile(file).use { zip ->
        val mimetype = zip.getEntry("mimetype")?.let { e -> zip.getInputStream(e).use { it.readBytes().decodeToString().trim() } }
        when {
            mimetype == "application/epub+zip" || zip.getEntry("META-INF/container.xml") != null -> Format.EPUB
            mimetype == "application/vnd.oasis.opendocument.text" -> Format.ODT
            zip.getEntry("word/document.xml") != null -> Format.DOCX
            else -> null
        }
    }

    /** Unknown extension: treat it as text when it looks like text, as FB2/HTML when it says so. */
    private fun sniffText(file: File): Format? {
        val start = file.inputStream().use { input -> ByteArray(4096).let { b -> b.copyOf(input.read(b).coerceAtLeast(0)) } }
        if (start.any { it == 0.toByte() }) return null
        val s = start.decodeToString().lowercase()
        return when {
            "<fictionbook" in s -> Format.FB2
            "<html" in s || "<!doctype html" in s -> Format.HTML
            else -> Format.TEXT
        }
    }

    /** Turns a non-PDF document into paragraphs. Blocking: call from a background thread. */
    fun paragraphs(file: File, format: Format): List<String> = when (format) {
        Format.EPUB -> epub(file)
        Format.DOCX -> docx(file)
        Format.ODT -> odt(file)
        Format.FB2 -> file.inputStream().use { fb2(it) }
        // Usually a saved web page: clean it up like the web reader does.
        Format.HTML -> WebReader.extract(Jsoup.parse(file, null), file.name).paragraphs
        Format.TEXT -> plainText(file.readBytes())
        Format.PDF -> error("PDF is read page by page")
    }

    // --- EPUB: container.xml → package (OPF) → spine order → XHTML chapters ---

    private fun epub(file: File): List<String> = ZipFile(file).use { zip ->
        fun read(path: String): String? = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes().decodeToString() } }

        val container = Jsoup.parse(read("META-INF/container.xml") ?: error("Not an EPUB"), "", Parser.xmlParser())
        val opfPath = container.selectFirst("rootfile[full-path]")?.attr("full-path") ?: error("EPUB has no package file")
        val opf = Jsoup.parse(read(opfPath) ?: error("EPUB package file is missing"), "", Parser.xmlParser())
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

        val items = opf.select("manifest > item").associate { it.attr("id") to it.attr("href") }
        val chapters = opf.select("spine > itemref")
            .filter { it.attr("linear") != "no" }
            .mapNotNull { items[it.attr("idref")] }

        chapters.flatMap { href ->
            val path = normalize(base + URLDecoder.decode(href.substringBefore('#'), "UTF-8"))
            val html = read(path) ?: return@flatMap emptyList()
            Html.paragraphs(Jsoup.parse(html))
        }
    }

    /** Resolves "a/b/../c" inside the archive. */
    private fun normalize(path: String): String {
        val out = ArrayDeque<String>()
        for (part in path.split('/')) when (part) {
            "", "." -> Unit
            ".." -> out.removeLastOrNull()
            else -> out.addLast(part)
        }
        return out.joinToString("/")
    }

    // --- DOCX: word/document.xml, one paragraph per <w:p> ---

    private fun docx(file: File): List<String> = ZipFile(file).use { zip ->
        val entry = zip.getEntry("word/document.xml") ?: error("Not a Word document")
        zip.getInputStream(entry).use { input ->
            collect(input, paragraph = setOf("p"), inline = mapOf("tab" to "\t", "br" to "\n", "cr" to "\n"), text = setOf("t"))
        }
    }

    // --- ODT: content.xml, <text:p> and <text:h> ---

    private fun odt(file: File): List<String> = ZipFile(file).use { zip ->
        val entry = zip.getEntry("content.xml") ?: error("Not an OpenDocument text")
        zip.getInputStream(entry).use { input ->
            collect(input, paragraph = setOf("p", "h"), inline = mapOf("tab" to "\t", "line-break" to "\n", "s" to " "), text = null)
        }
    }

    // --- FB2: <body> sections; <binary> holds base64 images and is skipped ---

    private fun fb2(input: InputStream): List<String> =
        collect(
            input,
            paragraph = setOf("p", "v", "subtitle", "text-author"),
            inline = emptyMap(),
            text = null,
            skip = setOf("binary", "description")
        )

    /**
     * Streams an XML document and returns the text of every [paragraph] element.
     * [text]: only text inside these elements counts (null = all text inside a paragraph).
     * [inline]: elements that stand for a character. Namespaces are ignored (local names).
     */
    private fun collect(
        input: InputStream,
        paragraph: Set<String>,
        inline: Map<String, String>,
        text: Set<String>?,
        skip: Set<String> = emptySet()
    ): List<String> {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(input, null) // encoding from the XML declaration (FB2 is often windows-1251)
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inPara = 0
        var inText = 0
        var skipping = 0
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when {
                        skipping > 0 || name in skip -> skipping++
                        name in paragraph -> inPara++
                        inPara > 0 && name in inline -> sb.append(inline.getValue(name))
                        text != null && name in text -> inText++
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    when {
                        skipping > 0 -> skipping--
                        name in paragraph -> {
                            inPara--
                            if (inPara == 0) {
                                sb.toString().replace(SPACES, " ").trim().takeIf { it.isNotEmpty() }?.let { out += it }
                                sb.setLength(0)
                            }
                        }
                        text != null && name in text -> inText--
                    }
                }
                XmlPullParser.TEXT -> if (skipping == 0 && inPara > 0 && (text == null || inText > 0)) sb.append(parser.text)
                XmlPullParser.END_DOCUMENT -> return out
            }
        }
    }

    private val SPACES = Regex("[ \\u00A0]{2,}")

    // --- Plain text ---

    /** UTF-8 when valid, otherwise windows-1251 (common for older Russian/Azerbaijani texts). */
    internal fun plainText(bytes: ByteArray): List<String> {
        val body = decode(bytes).removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        // Blank lines separate paragraphs; a text without any has one paragraph per line.
        val parts = if (Regex("\\n\\s*\\n").containsMatchIn(body)) body.split(Regex("\\n\\s*\\n")) else body.split('\n')
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1251"))
    }
}
