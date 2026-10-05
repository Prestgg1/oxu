// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.data

data class Lang(val code: String, val name: String)

object Langs {
    val all = listOf(
        Lang("en", "English"),
        Lang("tr", "Turkish"),
        Lang("az", "Azerbaijani"),
        Lang("ru", "Russian"),
        Lang("de", "German"),
        Lang("fr", "French"),
        Lang("es", "Spanish"),
        Lang("it", "Italian"),
        Lang("pt", "Portuguese"),
        Lang("uk", "Ukrainian"),
        Lang("pl", "Polish"),
        Lang("nl", "Dutch"),
        Lang("sv", "Swedish"),
        Lang("el", "Greek"),
        Lang("he", "Hebrew"),
        Lang("ar", "Arabic"),
        Lang("fa", "Persian"),
        Lang("hi", "Hindi"),
        Lang("ja", "Japanese"),
        Lang("ko", "Korean"),
        Lang("zh-CN", "Chinese")
    )

    fun byCode(code: String?): Lang? = all.firstOrNull { it.code == code }

    fun name(code: String?): String = byCode(code)?.name ?: code ?: "—"

    fun codeOf(code: String?): String = if (code == null) "en" else code

    fun forLibre(code: String): String = if (code.startsWith("zh")) "zh" else code

    fun forMyMemory(code: String): String = code
}