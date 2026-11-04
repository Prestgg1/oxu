// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.Prestgg.oxu.data.Prefs
import io.github.Prestgg.oxu.net.Translators
import io.github.Prestgg.oxu.offline.OfflineModels
import io.github.Prestgg.oxu.pdf.PdfSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    var source by mutableStateOf<PdfSource?>(null)
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

    fun open(uri: Uri) {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            try {
                val (name, size) = describe(uri)
                val file = copyToCache(uri, size)
                val src = PdfSource.open(app, file)
                source?.close()
                source = src
                docName = name
                docUri = uri.toString()
                val key = uri.toString()
                initialPage = prefs.recents.firstOrNull { it.uri == key }?.page ?: 0
                prefs.touchRecent(key, name, initialPage)
                screen = Screen.READER
            } catch (e: Throwable) {
                error = "Could not open PDF: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                loading = false
            }
        }
    }

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
        (name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf") to size
    }

    private fun cacheName(uri: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
        return hash.take(16).joinToString("") { "%02x".format(it) } + ".pdf"
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