// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.offline

import io.github.Prestgg.oxu.bergamot.Bergamot
import io.github.Prestgg.oxu.data.Langs
import io.github.Prestgg.oxu.net.TranslationException
import io.github.Prestgg.oxu.net.Translator
import io.github.Prestgg.oxu.net.paragraphs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * On-device translation with Mozilla's bergamot engine.
 *
 * The source language is the one picked in Settings. Pairs that don't involve
 * English are translated through it (two models), as Firefox does.
 */
class OfflineTranslator(private val models: OfflineModels) : Translator {

    override val label = "Offline"

    override suspend fun translate(text: String, from: String, to: String, onPartial: (String) -> Unit): String {
        val t = text.trim()
        if (t.isEmpty()) return ""
        if (from == to) return t
        if (!engineAvailable) throw TranslationException("Offline translation is not supported on this device.")

        return withContext(engine) {
            val missing = listOf(from, to).filter { !models.isInstalled(it) }
            if (missing.isNotEmpty()) {
                throw TranslationException(
                    "Download the offline ${missing.joinToString(" and ") { Langs.name(it) }} language " +
                        "in Settings → Offline languages."
                )
            }

            val direct: String?
            val pivot: Pair<String, String>?
            when {
                from == "en" -> { direct = load("en", to); pivot = null }
                to == "en" -> { direct = load(from, "en"); pivot = null }
                else -> { direct = null; pivot = load(from, "en") to load("en", to) }
            }
            fun run(texts: List<String>): List<String> =
                if (direct != null) Bergamot.translate(direct, texts)
                else Bergamot.pivot(pivot!!.first, pivot.second, texts)

            // Paragraph by paragraph, in small batches, so the first lines show up quickly.
            val paras = paragraphs(t)
            val out = ArrayList<String>(paras.size)
            for (batch in paras.chunked(BATCH)) {
                out += run(batch)
                if (out.size < paras.size) onPartial(out.joinToString("\n\n"))
            }
            out.joinToString("\n\n")
        }
    }

    /** Loads one direction into the engine (once) and returns its key. Codes are Oxu codes. */
    private fun load(from: String, to: String): String {
        val dir = models.installedDir(from, to)
            ?: throw TranslationException("Offline model ${Langs.name(from)} → ${Langs.name(to)} is missing.")
        val key = dir.name
        if (Bergamot.isLoaded(key)) return key
        val files = JSONObject(File(dir, "pack.json").readText()).getJSONObject("files")
        val model = File(dir, files.getString("model"))
        val src = File(dir, files.optString("srcvocab").ifEmpty { files.getString("vocab") })
        val trg = File(dir, files.optString("trgvocab").ifEmpty { files.getString("vocab") })
        // Keep memory bounded: at most two directions (one pivot pair) stay loaded.
        loaded.remove(key)
        loaded.addLast(key)
        while (loaded.size > MAX_LOADED) Bergamot.unload(loaded.removeFirst())
        try {
            Bergamot.load(key, config(model, src, trg))
        } catch (e: Exception) {
            loaded.remove(key)
            throw TranslationException("Could not load the offline model: ${e.message}")
        }
        return key
    }

    private fun config(model: File, src: File, trg: File) = """
        |models:
        |  - ${model.absolutePath}
        |vocabs:
        |  - ${src.absolutePath}
        |  - ${trg.absolutePath}
        |beam-size: 1
        |normalize: 1.0
        |word-penalty: 0
        |max-length-break: 128
        |mini-batch-words: 1024
        |max-length-factor: 2.0
        |skip-cost: true
        |cpu-threads: 1
        |quiet: true
        |quiet-translation: true
        |gemm-precision: int8shiftAlphaAll
        |alignment: soft
        |""".trimMargin()

    companion object {
        private const val BATCH = 4
        private const val MAX_LOADED = 2

        /** Native calls are serialized on one thread; the engine is single threaded anyway. */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val engine = Dispatchers.Default.limitedParallelism(1)

        private val loaded = ArrayDeque<String>()

        /** False on ABIs the native engine isn't built for (e.g. 32-bit x86). */
        val engineAvailable: Boolean by lazy {
            try {
                Bergamot.isLoaded("")
                true
            } catch (_: Throwable) {
                false
            }
        }

        suspend fun unload(vararg keys: String) {
            if (!engineAvailable) return
            withContext(engine) {
                keys.forEach {
                    loaded.remove(it)
                    Bergamot.unload(it)
                }
            }
        }
    }
}
