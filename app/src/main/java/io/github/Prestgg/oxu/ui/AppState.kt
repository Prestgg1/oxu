// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.Prestgg.oxu.data.Prefs
import io.github.Prestgg.oxu.pdf.PdfSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { FIRST_RUN, HOME, SETTINGS, READER }

class AppState(context: Context) {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val prefs = Prefs(app)

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
                val name = displayName(uri)
                val file = copyToCache(uri, name)
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

    private suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
    }

    private suspend fun copyToCache(uri: Uri, name: String): File = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "docs").apply { mkdirs() }
        val target = File(dir, "${name.hashCode().toUInt().toString(16)}-${uri.toString().hashCode().toUInt().toString(16)}.pdf")
        if (target.exists() && target.length() > 1024) return@withContext target
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
}