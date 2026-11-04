// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.Prestgg.oxu.data.Prefs
import io.github.Prestgg.oxu.doc.DocSource
import io.github.Prestgg.oxu.doc.Format
import io.github.Prestgg.oxu.doc.Formats
import io.github.Prestgg.oxu.doc.TextDocSource
import io.github.Prestgg.oxu.doc.WebPage
import io.github.Prestgg.oxu.doc.WebReader
import io.github.Prestgg.oxu.net.Translators
import io.github.Prestgg.oxu.offline.OfflineModels
import io.github.Prestgg.oxu.pdf.PdfSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

enum class Screen { FIRST_RUN, HOME, SETTINGS, OFFLINE, READER }

class AppState(context: Context) {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val prefs = Prefs(app)

    val offline = OfflineModels(app, scope)

    val translators = Translators(prefs, offline)

    var screen by mutableStateOf(if (prefs.targetLang.isEmpty()) Screen.FIRST_RUN else Screen.HOME)
        private set

    var source by mutableStateOf<DocSource?>(null)
        private set

    var docName by mutableStateOf("")
        private set

    var docUri by mutableStateOf("")
        private set

    var loading by mutableStateOf(false)
        private set

    var initialPage by mutableStateOf(0)
        private set

    var error by mutableStateOf<String?>(null)

    fun go(target: Screen) {
        error = null
        screen = target
    }

    /** Opens a document from a content/file URI, or a web page from an http(s) link. */
    fun open(uri: Uri) {
        if (uri.scheme == "http" || uri.scheme == "https") return openUrl(uri.toString())
        load("Could not open the file") {
            val (name, size) = describe(uri)
            val file = copyToCache(uri, size)
            val mime = runCatching { app.contentResolver.getType(uri) }.getOrNull()
            val format = withContext(Dispatchers.IO) { Formats.detect(file, name, mime) }
                ?: throw IllegalStateException("$name: this file type is not supported")
            val src: DocSource = if (format == Format.PDF) {
                PdfSource.open(app, file)
            } else {
                val paras = withContext(Dispatchers.IO) { Formats.paragraphs(file, format) }
                if (paras.isEmpty()) throw IllegalStateException("No text found in this ${format.label} file")
                TextDocSource(paras)
            }
            Triple(src, name, uri.toString())
        }
    }

    /** Web reader: downloads the page and keeps only its readable text. */
    fun openUrl(input: String) {
        val url = WebReader.normalizeUrl(input)
        load("Could not open the page") {
            val page = withContext(Dispatchers.IO) {
                try {
                    WebReader.load(url).also { saveWebPage(url, it) }
                } catch (e: Exception) {
                    // No connection: show the copy saved the last time, if there is one.
                    loadWebPage(url) ?: throw e
                }
            }
            Triple(TextDocSource(page.paragraphs), page.title, url)
        }
    }

    /** Text shared from another app. Not added to the recent list: there is nothing to reopen. */
    fun openText(text: String) {
        val paras = Formats.plainText(text.toByteArray())
        if (paras.isEmpty()) return
        source?.close()
        source = TextDocSource(paras)
        docName = "Shared text"
        docUri = ""
        initialPage = 0
        error = null
        screen = Screen.READER
    }

    private fun load(failure: String, open: suspend () -> Triple<DocSource, String, String>) {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            try {
                val (src, name, key) = open()
                source?.close()
                source = src
                docName = name
                docUri = key
                initialPage = (prefs.recents.firstOrNull { it.uri == key }?.page ?: 0)
                    .coerceIn(0, (src.pageCount - 1).coerceAtLeast(0))
                prefs.touchRecent(key, name, initialPage)
                screen = Screen.READER
            } catch (e: Throwable) {
                error = "$failure: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    private fun webFile(url: String) = File(File(app.filesDir, "web").apply { mkdirs() }, cacheName(url, ".json"))

    private fun saveWebPage(url: String, page: WebPage) {
        val json = JSONObject().put("title", page.title).put("paragraphs", JSONArray(page.paragraphs))
        val file = webFile(url)
        runCatching { file.writeText(json.toString()) }
        // Saved pages are kept for the recent list only.
        val wanted = prefs.recents.map { cacheName(it.uri, ".json") }.toSet() + file.name
        file.parentFile?.listFiles()?.forEach { if (it.name !in wanted) it.delete() }
    }

    private fun loadWebPage(url: String): WebPage? = runCatching {
        val o = JSONObject(webFile(url).readText())
        val arr = o.getJSONArray("paragraphs")
        WebPage(url, o.getString("title"), (0 until arr.length()).map { arr.getString(it) })
    }.getOrNull()

    fun savePage(page: Int) {
        if (docUri.isNotEmpty()) prefs.setPage(docUri, page)
    }

    fun closeDoc() {
        source?.close()
        source = null
        docName = ""
        docUri = ""
    }

    fun dispose() {
        closeDoc()
        scope.cancel()
    }

    /** Display name and size (bytes, or -1 if unknown) of a document. */
    private suspend fun describe(uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
        var name: String? = null
        var size = -1L
        runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val s = c.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0) name = c.getString(n)
                        if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                    }
                }
        }
        (name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document") to size
    }

    private fun cacheName(uri: String, suffix: String = ".doc"): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
        return hash.take(16).joinToString("") { "%02x".format(it) } + suffix
    }

    private suspend fun copyToCache(uri: Uri, size: Long): File = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "docs").apply { mkdirs() }
        val target = File(dir, cacheName(uri.toString()))
        // Reuse the copy unless the original changed size (it was edited or replaced).
        if (target.exists() && target.length() > 0 && (size < 0 || size == target.length())) {
            target.setLastModified(System.currentTimeMillis())
            return@withContext target
        }
        pruneCache(dir, keep = target.name)
        val tmp = File(target.parentFile, target.name + ".part")
        tmp.delete()
        app.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { out -> input.copyTo(out, 128 * 1024) }
        } ?: throw IllegalStateException("File could not be read")
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        target
    }

    /** Keeps copies of recent documents only, so the cache doesn't grow forever. */
    private fun pruneCache(dir: File, keep: String) {
        val wanted = prefs.recents.map { cacheName(it.uri) }.toSet() + keep
        dir.listFiles()?.forEach { f -> if (f.name !in wanted) f.delete() }
    }
}