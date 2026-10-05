// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class RecentDoc(val uri: String, val name: String, val page: Int, val at: Long)

class Prefs(app: Context) {

    private val sp = app.getSharedPreferences("oxu", Context.MODE_PRIVATE)

    private val targetState = mutableStateOf(sp.getString(K_TARGET, "") ?: "")
    var targetLang: String
        get() = targetState.value
        set(v) {
            targetState.value = v
            sp.edit().putString(K_TARGET, v).apply()
        }

    private val fontState = mutableFloatStateOf(sp.getFloat(K_FONT, 18f))
    var fontSize: Float
        get() = fontState.floatValue
        set(v) {
            fontState.floatValue = v
            sp.edit().putFloat(K_FONT, v).apply()
        }

    private val backendState = mutableStateOf(sp.getString(K_BACKEND, BACKEND_MYMEMORY) ?: BACKEND_MYMEMORY)
    var backend: String
        get() = backendState.value
        set(v) {
            backendState.value = v
            sp.edit().putString(K_BACKEND, v).apply()
        }

    private val urlState = mutableStateOf(sp.getString(K_URL, "") ?: "")
    var libreUrl: String
        get() = urlState.value
        set(v) {
            urlState.value = v
            sp.edit().putString(K_URL, v.trim()).apply()
        }

    private val keyState = mutableStateOf(sp.getString(K_KEY, "") ?: "")
    var libreKey: String
        get() = keyState.value
        set(v) {
            keyState.value = v
            sp.edit().putString(K_KEY, v.trim()).apply()
        }

    private val recentsState = mutableStateOf(loadRecents())
    val recents: List<RecentDoc> get() = recentsState.value

    fun touchRecent(uri: String, name: String, page: Int) {
        val list = recentsState.value.filterNot { it.uri == uri }
        val updated = (list + RecentDoc(uri, name, page, System.currentTimeMillis()))
            .sortedByDescending { it.at }
            .take(8)
        saveRecents(updated)
    }

    fun setPage(uri: String, page: Int) {
        val updated = recentsState.value.map { if (it.uri == uri) it.copy(page = page) else it }
        saveRecents(updated)
    }

    fun forget(uri: String) {
        saveRecents(recentsState.value.filterNot { it.uri == uri })
    }

    private fun saveRecents(list: List<RecentDoc>) {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(
                JSONObject()
                    .put("uri", d.uri)
                    .put("name", d.name)
                    .put("page", d.page)
                    .put("at", d.at)
            )
        }
        recentsState.value = list
        sp.edit().putString(K_RECENTS, arr.toString()).apply()
    }

    private fun loadRecents(): List<RecentDoc> {
        val raw = sp.getString(K_RECENTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RecentDoc(o.getString("uri"), o.optString("name"), o.optInt("page"), o.optLong("at"))
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val BACKEND_MYMEMORY = "mymemory"
        const val BACKEND_LIBRE = "libre"
        private const val K_FONT = "font_size"
        private const val K_TARGET = "target_lang"
        private const val K_BACKEND = "backend"
        private const val K_URL = "libre_url"
        private const val K_KEY = "libre_key"
        private const val K_RECENTS = "recents"
    }
}
