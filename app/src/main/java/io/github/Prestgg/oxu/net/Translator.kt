// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.net

import io.github.Prestgg.oxu.data.Langs
import io.github.Prestgg.oxu.data.Prefs
import io.github.Prestgg.oxu.offline.OfflineModels
import io.github.Prestgg.oxu.offline.OfflineTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val AUTO = "Autodetect"
private const val AUTO_LIBRE = "auto"
private const val USER_AGENT = "Oxu/1.0 (Android)"

open class TranslationException(message: String) : Exception(message)

/** The service could not be reached at all (no connection, DNS failure, timeout). */
class NetworkException(message: String) : TranslationException(message)

interface Translator {
    val label: String

    suspend fun translate(text: String, to: String, onPartial: (String) -> Unit = {}): String
}

/**
 * Hands out the translator matching the current settings. Instances are reused
 * while the settings stay the same, so their in-memory caches actually work.
 */
class Translators(private val prefs: Prefs, private val offline: OfflineModels) {

    private var key: String? = null
    private var cached: Translator? = null

    fun current(): Translator {
        val k = listOf(prefs.backend, prefs.libreUrl, prefs.libreKey, prefs.offlineFallback.toString()).joinToString("|")
        cached?.let { if (k == key) return it }
        val online: Translator? = when (prefs.backend) {
            Prefs.BACKEND_OFFLINE -> null
            Prefs.BACKEND_LIBRE -> LibreTranslator(prefs.libreUrl, prefs.libreKey)
            else -> MyMemoryTranslator()
        }
        val t = when {
            online == null -> OfflineTranslator(offline)
            prefs.offlineFallback && OfflineTranslator.engineAvailable ->
                FallbackTranslator(online, OfflineTranslator(offline), offline)
            else -> online
        }
        key = k
        cached = t
        return t
    }
}

/** Uses the online service, and the downloaded offline models when there is no connection. */
class FallbackTranslator(
    private val online: Translator,
    private val offline: Translator,
    private val models: OfflineModels
) : Translator {

    override val label get() = online.label

    override suspend fun translate(text: String, to: String, onPartial: (String) -> Unit): String =
        try {
            online.translate(text, to, onPartial)
        } catch (e: NetworkException) {
            if (!models.isInstalled(to)) throw e
            offline.translate(text, to, onPartial)
        }
}

private fun <K, V> lru(max: Int) = object : LinkedHashMap<K, V>(max / 2, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > max
}

class MyMemoryTranslator : Translator {

    override val label = "MyMemory"

    private val cache = lru<String, String>(200)

    override suspend fun translate(text: String, to: String, onPartial: (String) -> Unit): String {
        val t = text.trim()
        if (t.isEmpty()) return ""
        val dst = Langs.forMyMemory(to)

        val key = "$dst|$t"
        synchronized(cache) { cache[key] }?.let { return it }

        val result = translateChunked(t, 440, onPartial) { translateChunk(it, dst) }
        synchronized(cache) { cache[key] = result }
        return result
    }

    private suspend fun translateChunk(chunk: String, to: String): String {
        val url = "https://api.mymemory.translated.net/get?q=" +
            URLEncoder.encode(chunk, "UTF-8") + "&langpair=$AUTO|$to"

        val body = get(url)
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw TranslationException("Unreadable response (server said: ${body.take(120)})")

        val status = json.opt("responseStatus")
        val statusInt = when (status) {
            is Int -> status
            is String -> status.toIntOrNull() ?: 0
            else -> 0
        }
        val details = json.optString("responseDetails", "")
        if (statusInt != 200) {
            val msg = when {
                statusInt == 403 || details.contains("LIMIT", true) ->
                    "MyMemory daily limit reached. Try again tomorrow, or use LibreTranslate or offline translation."
                statusInt == 429 -> "Too many requests, please wait a moment."
                else -> details.ifBlank { "Translation failed ($statusInt)" }
            }
            throw TranslationException(msg)
        }
        val result = json.optJSONObject("responseData")?.optString("translatedText").orEmpty()
        if (result.isBlank()) throw TranslationException("Empty response: ${details.ifBlank { "nothing to show" }}")
        return result
    }
}

class LibreTranslator(rawUrl: String, private val key: String) : Translator {

    override val label = "LibreTranslate"

    private val endpoint = normalize(rawUrl)

    private val cache = lru<String, String>(200)

    private fun normalize(url: String): String {
        var u = url.trim().trimEnd('/')
        if (u.endsWith("/translate")) u = u.removeSuffix("/translate")
        return u
    }

    override suspend fun translate(text: String, to: String, onPartial: (String) -> Unit): String {
        val t = text.trim()
        if (t.isEmpty()) return ""
        if (endpoint.isEmpty()) {
            throw TranslationException("No LibreTranslate URL set. Enter your own server address in Settings.")
        }
        val dst = Langs.forLibre(to)

        val cacheKey = "$endpoint|$dst|$t"
        synchronized(cache) { cache[cacheKey] }?.let { return it }

        val result = translateChunked(t, 1800, onPartial) { c ->
            val payload = JSONObject()
                .put("q", c)
                .put("source", AUTO_LIBRE)
                .put("target", dst)
                .put("format", "text")
            if (key.isNotEmpty()) payload.put("api_key", key)
            val body = post("$endpoint/translate", payload.toString())
            val out = runCatching { JSONObject(body).optString("translatedText") }.getOrNull()
            if (out.isNullOrBlank()) throw TranslationException("No translation in the response: ${body.take(120)}")
            out
        }
        synchronized(cache) { cache[cacheKey] = result }
        return result
    }
}

/**
 * Splits PDF text into paragraphs. Lines inside a paragraph are joined: PDF text
 * is hard-wrapped, and translating half sentences gives poor results.
 */
fun paragraphs(text: String): List<String> =
    text.split(Regex("\\n\\s*\\n"))
        .map { it.replace(Regex("\\s*\\n\\s*"), " ").trim() }
        .filter { it.isNotEmpty() }

/** Translates paragraph by paragraph, each split into request-sized chunks, keeping the order. */
private suspend fun translateChunked(
    text: String,
    maxBytes: Int,
    onPartial: (String) -> Unit,
    translateOne: suspend (String) -> String
): String {
    val paras = paragraphs(text)
    val out = StringBuilder()
    paras.forEachIndexed { i, para ->
        if (i > 0) out.append("\n\n")
        chunk(para, maxBytes).forEachIndexed { j, c ->
            if (j > 0) out.append(' ')
            out.append(translateOne(c))
            onPartial(out.toString())
        }
    }
    return out.toString()
}

private fun utf8Len(s: CharSequence): Int {
    var n = 0
    var i = 0
    while (i < s.length) {
        val c = s[i]
        n += when {
            c.code < 0x80 -> 1
            c.code < 0x800 -> 2
            Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1]) -> { i++; 4 }
            else -> 3
        }
        i++
    }
    return n
}

/**
 * Splits [text] into pieces of at most [maxBytes] UTF-8 bytes, in order.
 * Prefers sentence ends, then spaces; text without spaces (Chinese, Japanese)
 * is cut at code point boundaries.
 */
internal fun chunk(text: String, maxBytes: Int = 440): List<String> {
    val t = text.trim()
    if (t.isEmpty()) return emptyList()
    if (utf8Len(t) <= maxBytes) return listOf(t)

    // Sentence-like units, keeping their punctuation. CJK full stops count too.
    val units = Regex("[^.!?。！？]*[.!?。！？]+\\s*|[^.!?。！？]+").findAll(t).map { it.value }.toList().ifEmpty { listOf(t) }

    val out = ArrayList<String>()
    val sb = StringBuilder()
    fun flush() {
        val s = sb.toString().trim()
        if (s.isNotEmpty()) out += s
        sb.setLength(0)
    }
    fun addPiece(piece: String) {
        if (utf8Len(sb) + utf8Len(piece) > maxBytes) flush()
        if (utf8Len(piece) <= maxBytes) {
            sb.append(piece)
            return
        }
        // Too long on its own: hard split at code point boundaries.
        var i = 0
        val cur = StringBuilder()
        while (i < piece.length) {
            val cp = piece.codePointAt(i)
            val chars = Character.charCount(cp)
            val next = piece.substring(i, i + chars)
            if (utf8Len(cur) + utf8Len(next) > maxBytes) {
                out += cur.toString().trim()
                cur.setLength(0)
            }
            cur.append(next)
            i += chars
        }
        sb.append(cur)
    }
    for (unit in units) {
        if (utf8Len(unit) <= maxBytes) {
            addPiece(unit)
        } else {
            // Long sentence: go word by word (keeping the separating spaces).
            Regex("\\S+\\s*").findAll(unit).forEach { addPiece(it.value) }
        }
    }
    flush()
    return out.filter { it.isNotEmpty() }
}

private fun networkError(e: IOException) =
    NetworkException("No connection to the translation service (${e.message ?: e.javaClass.simpleName}).")

private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15000
    conn.readTimeout = 25000
    conn.setRequestProperty("User-Agent", USER_AGENT)
    try {
        readResponse(conn)
    } catch (e: IOException) {
        throw networkError(e)
    } finally {
        conn.disconnect()
    }
}

private suspend fun post(url: String, body: String): String = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        readResponse(conn)
    } catch (e: IOException) {
        throw networkError(e)
    } finally {
        conn.disconnect()
    }
}

private fun readResponse(conn: HttpURLConnection): String {
    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
    if (code !in 200..299) {
        throw TranslationException("Server error ($code): ${text.take(120)}")
    }
    return text
}
