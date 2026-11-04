// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu

import io.github.Prestgg.oxu.offline.OfflineModels
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {

    private fun rec(from: String, to: String, version: String, type: String, filter: String = "") =
        JSONObject()
            .put("fromLang", from).put("toLang", to).put("version", version)
            .put("fileType", type).put("filter_expression", filter)
            .put("attachment", JSONObject()
                .put("filename", "$type.$from$to.$version").put("location", "x/$type")
                .put("hash", "AB").put("size", 10))

    private fun catalog(vararg recs: JSONObject) =
        JSONObject().put("data", JSONArray().apply { recs.forEach { put(it) } }).toString()

    private fun pair(from: String, to: String, version: String) =
        arrayOf(rec(from, to, version, "model"), rec(from, to, version, "vocab"), rec(from, to, version, "lex"))

    @Test
    fun picksNewestReleasedVersionAndSkipsAlphasAndDesktopOnly() {
        val c = OfflineModels.parseCatalog(catalog(
            *pair("az", "en", "1.0"), *pair("az", "en", "2.1"), *pair("az", "en", "3.0a1"),
            *pair("en", "az", "1.0"),
            rec("en", "az", "2.0", "model", "env.appinfo.OS != 'Android' || env.channel != 'release'"),
            rec("en", "az", "2.0", "vocab", "env.appinfo.OS != 'Android' || env.channel != 'release'"),
        ))
        val az = c.getValue("az")
        assertEquals("az" to "en", az[0].from to az[0].to)
        assertEquals("2.1", az[0].version)
        assertEquals("1.0", az[1].version)
        // Lexical shortlists aren't used, so they aren't downloaded.
        assertFalse(az[0].files.any { it.role == "lex" })
        assertEquals("ab", az[0].files.first().sha256)
    }

    @Test
    fun separateVocabsAndOneWayLanguages() {
        val c = OfflineModels.parseCatalog(catalog(
            rec("en", "ja", "2.3", "model"), rec("en", "ja", "2.3", "srcvocab"), rec("en", "ja", "2.3", "trgvocab"),
            *pair("ja", "en", "2.1"),
            *pair("en", "xx", "1.0"), // no xx→en model: not offered
        ))
        assertEquals(3, c.getValue("ja")[1].files.size)
        assertTrue("xx" !in c)
    }
}
