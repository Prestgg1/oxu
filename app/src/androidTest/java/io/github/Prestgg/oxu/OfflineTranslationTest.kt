// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.Prestgg.oxu.bergamot.Bergamot
import io.github.Prestgg.oxu.offline.OfflineModels
import io.github.Prestgg.oxu.offline.OfflineModels.PackState
import io.github.Prestgg.oxu.offline.OfflineTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** End to end: real catalog, real downloads, real native engine. Needs internet. */
@RunWith(AndroidJUnit4::class)
class OfflineTranslationTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun OfflineModels.install(code: String) {
        if (isInstalled(code)) return
        download(code)
        withTimeout(10 * 60_000) {
            while (true) {
                when (val s = states[code]) {
                    PackState.Installed -> return@withTimeout
                    is PackState.Failed -> throw AssertionError("download $code: ${s.message}")
                    else -> delay(500)
                }
            }
        }
    }

    @Test
    fun detectsLanguages() {
        assertEquals("en", Bergamot.detect("The quick brown fox jumps over the lazy dog near the river bank.").code)
        assertEquals("az", Bergamot.detect("Bu gün hava çox gözəldir və biz parka gəzməyə getdik.").code)
    }

    @Test
    fun downloadsAndTranslatesOffline() = runBlocking(Dispatchers.Main) {
        val models = OfflineModels(ctx, this)
        models.refreshCatalog(force = true)
        withTimeout(60_000) { while (models.catalogLoading || !models.hasCatalog()) delay(200) }
        assertTrue(models.isAvailable("az"))

        models.install("az")
        models.install("tr")
        val t = OfflineTranslator(models)

        val partials = ArrayList<String>()
        val az = t.translate("The weather is very nice today. We went for a walk in the park.", "az") { partials += it }
        android.util.Log.i("OxuTest", "en→az: $az")
        assertTrue(az.isNotBlank())
        assertNotEquals("The weather is very nice today. We went for a walk in the park.", az)
        assertTrue(az.contains("park", ignoreCase = true))

        // Not English on either side: goes through English (az → en → tr).
        val tr = t.translate(az, "tr")
        android.util.Log.i("OxuTest", "az→tr: $tr")
        assertTrue(tr.isNotBlank())
        assertNotEquals(az, tr)

        // Emoji / non-BMP characters survive the JNI boundary.
        val emoji = t.translate("I love books 📚 and coffee ☕.", "az")
        android.util.Log.i("OxuTest", "emoji: $emoji")
        assertTrue(emoji.contains("📚"))

        // Same language as the target: returned unchanged.
        assertEquals("Bu gün hava çox gözəldir və biz parka gəzməyə getdik.",
            t.translate("Bu gün hava çox gözəldir və biz parka gəzməyə getdik.", "az"))
    }
}
