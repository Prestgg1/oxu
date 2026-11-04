// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.offline

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.Prestgg.oxu.data.Langs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloadable on-device translation models (Mozilla Firefox Translations).
 *
 * A "language pack" for language X holds the two models X→English and English→X.
 * English is the pivot, so any two downloaded languages can translate into each
 * other (e.g. ru→en→az). English itself needs no download.
 *
 * Layout on disk: files/offline/<from><to>/{model.bin, vocab files, pack.json}.
 * pack.json is written last, after every file passed its SHA-256 check, so a
 * directory without it is an unfinished download and is ignored.
 */
class OfflineModels(context: Context, private val scope: CoroutineScope) {

    sealed interface PackState {
        data object NotInstalled : PackState
        data class Downloading(val done: Long, val total: Long) : PackState
        data object Installed : PackState
        data class Failed(val message: String) : PackState
    }

    /** One downloadable file of a model. */
    data class ModelFile(val role: String, val name: String, val url: String, val sha256: String, val size: Long)

    /** One direction, e.g. en→az. */
    data class Model(val from: String, val to: String, val version: String, val files: List<ModelFile>) {
        val key get() = "$from$to"
        val size get() = files.sumOf { it.size }
    }

    private val root = File(context.filesDir, "offline").apply { mkdirs() }
    private val catalogFile = File(root, "catalog.json")

    /** Mozilla language code → its two models (to English, from English); either may be missing. */
    private var catalog: Map<String, List<Model>> = emptyMap()

    var catalogState by mutableStateOf<String?>(null)
        private set

    var catalogLoading by mutableStateOf(false)
        private set

    val states = mutableStateMapOf<String, PackState>()
    private val jobs = HashMap<String, Job>()

    init {
        Langs.all.filter { it.code != "en" }.forEach { lang ->
            states[lang.code] = if (isInstalled(lang.code)) PackState.Installed else PackState.NotInstalled
        }
        runCatching { if (catalogFile.exists()) catalog = parseCatalog(catalogFile.readText()) }
    }

    fun hasCatalog() = catalog.isNotEmpty()

    /** Total download size of a language pack, or null if the catalog is not loaded or has no model. */
    fun packSize(code: String): Long? = catalog[Langs.forBergamot(code)]?.sumOf { it.size }?.takeIf { it > 0 }

    fun isAvailable(code: String) = catalog.containsKey(Langs.forBergamot(code))

    fun isInstalled(code: String): Boolean {
        if (code == "en") return true
        val m = Langs.forBergamot(code)
        return File(dirOf(m, "en"), PACK_FILE).exists() && File(dirOf("en", m), PACK_FILE).exists()
    }

    /** Installed model directory for one direction, or null. Codes are Oxu codes. */
    internal fun installedDir(from: String, to: String): File? {
        val dir = dirOf(Langs.forBergamot(from), Langs.forBergamot(to))
        return dir.takeIf { File(it, PACK_FILE).exists() }
    }

    fun installedCodes(): List<String> = Langs.all.map { it.code }.filter { isInstalled(it) }

    fun refreshCatalog(force: Boolean = false) {
        if (catalogLoading) return
        val fresh = catalogFile.exists() && System.currentTimeMillis() - catalogFile.lastModified() < CATALOG_MAX_AGE
        if (!force && hasCatalog() && fresh) return
        catalogLoading = true
        catalogState = null
        scope.launch {
            try {
                val body = withContext(Dispatchers.IO) { httpGet(CATALOG_URL) }
                val parsed = withContext(Dispatchers.Default) { parseCatalog(body) }
                if (parsed.isEmpty()) throw IOException("The model list is empty")
                catalog = parsed
                withContext(Dispatchers.IO) { catalogFile.writeText(body) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                catalogState = "Could not load the language list: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                catalogLoading = false
            }
        }
    }

    fun download(code: String) {
        if (jobs[code]?.isActive == true) return
        val models = catalog[Langs.forBergamot(code)]
        if (models.isNullOrEmpty() || models.size < 2) {
            states[code] = PackState.Failed("Not available offline")
            return
        }
        val total = models.sumOf { it.size }
        states[code] = PackState.Downloading(0, total)
        jobs[code] = scope.launch {
            try {
                var done = 0L
                var shown = 0L
                for (model in models) {
                    installModel(model) { bytes ->
                        done += bytes
                        // Throttle UI updates to roughly every 512 KB.
                        if (done - shown >= 512 * 1024 || done == total) {
                            shown = done
                            states[code] = PackState.Downloading(done, total)
                        }
                    }
                }
                states[code] = PackState.Installed
            } catch (e: CancellationException) {
                states[code] = if (isInstalled(code)) PackState.Installed else PackState.NotInstalled
                throw e
            } catch (e: Exception) {
                states[code] = PackState.Failed(e.message ?: "Download failed")
            } finally {
                jobs.remove(code)
            }
        }
    }

    fun cancel(code: String) {
        jobs[code]?.cancel()
    }

    fun delete(code: String) {
        cancel(code)
        val m = Langs.forBergamot(code)
        scope.launch {
            OfflineTranslator.unload("${m}en", "en$m")
            withContext(Dispatchers.IO) {
                dirOf(m, "en").deleteRecursively()
                dirOf("en", m).deleteRecursively()
            }
            states[code] = PackState.NotInstalled
        }
    }

    private fun dirOf(from: String, to: String) = File(root, "$from$to")

    private suspend fun installModel(model: Model, onBytes: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val dir = dirOf(model.from, model.to)
        val pack = File(dir, PACK_FILE)
        if (pack.exists() && runCatching { JSONObject(pack.readText()).optString("version") }.getOrNull() == model.version) {
            onBytes(model.size)
            return@withContext
        }
        pack.delete()
        dir.mkdirs()
        val files = JSONObject()
        for (f in model.files) {
            val target = File(dir, f.name)
            if (!(target.exists() && target.length() == f.size && sha256(target) == f.sha256)) {
                downloadVerified(f, target, onBytes)
            } else {
                onBytes(f.size)
            }
            files.put(f.role, f.name)
        }
        dir.listFiles()?.filter { it.name != PACK_FILE && model.files.none { f -> f.name == it.name } }
            ?.forEach { it.delete() }
        File(dir, "$PACK_FILE.part").apply {
            writeText(JSONObject().put("version", model.version).put("files", files).toString())
            if (!renameTo(pack)) throw IOException("Could not save the model")
        }
    }

    private suspend fun downloadVerified(f: ModelFile, target: File, onBytes: (Long) -> Unit) {
        val part = File(target.parentFile, target.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = URL(f.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (conn.responseCode !in 200..299) throw IOException("Server error (${conn.responseCode})")
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        onBytes(n.toLong())
                    }
                }
            }
        } catch (e: Throwable) {
            part.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        val hash = digest.digest().toHex()
        if (hash != f.sha256) {
            part.delete()
            throw IOException("Downloaded file is corrupted, please try again")
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("Could not save the model")
    }

    companion object {
        private const val CATALOG_URL =
            "https://firefox.settings.services.mozilla.com/v1/buckets/main/collections/translations-models/records"
        private const val ATTACHMENTS_URL = "https://firefox-settings-attachments.cdn.mozilla.net/"
        private const val CATALOG_MAX_AGE = 7L * 24 * 3600 * 1000
        private const val PACK_FILE = "pack.json"
        internal const val USER_AGENT = "Oxu (Android)"

        private val ROLES = setOf("model", "vocab", "srcvocab", "trgvocab")

        /**
         * Picks, for every language, the newest released model in each direction.
         * Records gated to other Firefox channels / desktop only, and alpha versions, are skipped.
         */
        internal fun parseCatalog(body: String): Map<String, List<Model>> {
            val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
            data class Rec(val from: String, val to: String, val version: String, val file: ModelFile)

            val recs = ArrayList<Rec>()
            for (i in 0 until data.length()) {
                val o = data.optJSONObject(i) ?: continue
                val role = o.optString("fileType")
                if (role !in ROLES) continue
                val filter = o.optString("filter_expression").trim()
                if (filter.isNotEmpty() && filter != "env.appinfo.OS == 'Android'") continue
                val version = o.optString("version")
                if (version.isEmpty() || version.any { it.isLetter() }) continue
                val att = o.optJSONObject("attachment") ?: continue
                val name = att.optString("filename")
                if (name.isEmpty() || name.contains('/') || name.startsWith(".")) continue
                recs += Rec(
                    o.optString("fromLang"), o.optString("toLang"), version,
                    ModelFile(role, name, ATTACHMENTS_URL + att.optString("location"),
                        att.optString("hash").lowercase(), att.optLong("size"))
                )
            }

            val models = recs.groupBy { it.from to it.to }.mapNotNull { (pair, list) ->
                val best = list.map { it.version }.distinct().maxWithOrNull(::compareVersions) ?: return@mapNotNull null
                val files = list.filter { it.version == best }.map { it.file }.distinctBy { it.role }
                val roles = files.map { it.role }.toSet()
                val complete = "model" in roles && ("vocab" in roles || ("srcvocab" in roles && "trgvocab" in roles))
                if (!complete) null else Model(pair.first, pair.second, best, files)
            }

            val byLang = HashMap<String, MutableList<Model>>()
            for (m in models) {
                if (m.to == "en") byLang.getOrPut(m.from) { ArrayList() }.add(0, m)
                else if (m.from == "en") byLang.getOrPut(m.to) { ArrayList() }.add(m)
            }
            // Only languages with both directions are offered.
            return byLang.filterValues { list -> list.size == 2 }
        }

        private fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }

        private fun httpGet(url: String): String {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (conn.responseCode !in 200..299) throw IOException("Server error (${conn.responseCode})")
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().toHex()
        }

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
