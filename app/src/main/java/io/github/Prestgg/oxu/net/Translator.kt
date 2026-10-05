// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.net

import io.github.Prestgg.oxu.data.Langs
import io.github.Prestgg.oxu.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val AUTO = "Autodetect"
private const val AUTO_LIBRE = "auto"

class TranslationException(message: String) : Exception(message)

interface Translator {
    val label: String

    suspend fun translate(text: String, to: String, onPartial: (String) -> Unit = {}): String
}

object Translate {
    fun fromPrefs(prefs: Prefs): Translator =
        if (prefs.backend == Prefs.BACKEND_LIBRE) {
            LibreTranslator(prefs.libreUrl, prefs.libreKey)
        } else {
            MyMemoryTranslator()
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
        cache[key]?.let { return it }

        val out = StringBuilder()
        chunk(t).forEach { c ->
            val part = translateChunk(c, dst)
            if (out.isNotEmpty()) out.append(' ')
            out.append(part)
            onPartial(out.toString())
        }
        val result = out.toString()
        cache[key] = result
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
                    "MyMemory daily limit reached. Try again tomorrow, or use LibreTranslate."
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
        cache[cacheKey]?.let { return it }

        val out = StringBuilder()
        chunk(t, 1800).forEach { c ->
            val payload = JSONObject()
                .put("q", c)
                .put("source", AUTO_LIBRE)
                .put("target", dst)
                .put("format", "text")
                .put("api_key", key)
            val body = post("$endpoint/translate", payload.toString())
            val text2 = runCatching { JSONObject(body).optString("translatedText") }.getOrNull()
            if (text2.isNullOrBlank()) throw TranslationException("No translation in the response: ${body.take(120)}")
            if (out.isNotEmpty()) out.append(' ')
            out.append(text2)
            onPartial(out.toString())
        }
        val result = out.toString()
        cache[cacheKey] = result
        return result
    }
}

internal fun chunk(text: String, maxBytes: Int = 440): List<String> {
    val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var bytes = 0
    for (w in words) {
        val wb = w.toByteArray(Charsets.UTF_8).size + 1
        if (wb > maxBytes) {
            var i = 0
            while (i < w.length) {
                val end = (i + maxBytes / 2).coerceAtMost(w.length)
                val piece = w.substring(i, end)
                out += piece
                i = end
            }
            continue
        }
        if (bytes + wb > maxBytes && sb.isNotEmpty()) {
            out += sb.toString().trim()
            sb.setLength(0)
            bytes = 0
        }
        sb.append(w).append(' ')
        bytes += wb
    }
    if (sb.isNotBlank()) out += sb.toString().trim()
    return out
}

private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15000
    conn.readTimeout = 25000
    conn.setRequestProperty("User-Agent", "Oxu/1.0 (Android)")
    try {
        readResponse(conn)
    } finally {
        conn.disconnect()
    }
}

private suspend fun post(url: String, body: String): String = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.connectTimeout = 15000
    conn.readTimeout = 30000
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("User-Agent", "Oxu/1.0 (Android)")
    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    try {
        readResponse(conn)
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
