// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu

import io.github.Prestgg.oxu.net.chunk
import io.github.Prestgg.oxu.net.paragraphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkTest {

    private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8).size

    @Test
    fun shortTextIsOneChunk() {
        assertEquals(listOf("Hello world."), chunk("Hello world.", 440))
    }

    @Test
    fun keepsOrderAroundAnOverlongWord() {
        val long = "x".repeat(50)
        val parts = chunk("alpha beta $long gamma", 20)
        assertEquals("alpha beta $long gamma".replace(" ", ""), parts.joinToString("").replace(" ", ""))
        assertTrue(parts.all { bytes(it) <= 20 })
    }

    @Test
    fun cjkWithoutSpacesStaysUnderTheByteLimit() {
        val text = "これは日本語の文章です".repeat(40) // 3 bytes per character, no spaces
        val parts = chunk(text, 440)
        assertTrue(parts.all { bytes(it) <= 440 })
        assertEquals(text, parts.joinToString(""))
    }

    @Test
    fun emojiAreNotCutInHalf() {
        val text = "😀".repeat(100)
        val parts = chunk(text, 10)
        assertTrue(parts.all { bytes(it) <= 10 })
        assertEquals(text, parts.joinToString(""))
    }

    @Test
    fun splitsOnSentences() {
        val parts = chunk("One two three. Four five six. Seven eight nine.", 32)
        assertEquals(listOf("One two three. Four five six.", "Seven eight nine."), parts)
    }

    @Test
    fun leadingPunctuationIsKept() {
        val text = "... and then " + "word ".repeat(30)
        assertTrue(chunk(text, 40).first().startsWith("..."))
    }

    @Test
    fun paragraphsJoinWrappedLines() {
        val text = "First line\nsame sentence.\n\nSecond\nparagraph.\n"
        assertEquals(listOf("First line same sentence.", "Second paragraph."), paragraphs(text))
    }
}
