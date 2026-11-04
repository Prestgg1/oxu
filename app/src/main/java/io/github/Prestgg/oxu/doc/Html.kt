// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.doc

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/** HTML → readable paragraphs. Shared by EPUB chapters, HTML files and the web reader. */
object Html {

    /** Elements that start a new paragraph. */
    private val BLOCKS = setOf(
        "p", "div", "section", "article", "main", "header", "footer", "aside", "nav",
        "h1", "h2", "h3", "h4", "h5", "h6", "li", "ul", "ol", "dl", "dt", "dd",
        "blockquote", "pre", "figure", "figcaption", "table", "tr", "hr", "address",
        "details", "summary", "center"
    )

    /** Never text a reader wants to see. */
    private const val JUNK = "script, style, noscript, template, iframe, object, embed, svg, canvas, " +
        "form, button, input, select, textarea, math, video, audio, map"

    /** Paragraphs of [root], in document order. Table cells on one row are joined with " · ". */
    fun paragraphs(root: Element): List<String> {
        val el = root.clone()
        el.select(JUNK).remove()
        val sb = StringBuilder()
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                when (node) {
                    is TextNode -> {
                        val inPre = node.parentNode()?.let { (it as? Element)?.tagName() == "pre" } == true
                        sb.append(if (inPre) node.wholeText else node.text())
                    }
                    is Element -> when (val tag = node.normalName()) {
                        "br" -> sb.append('\n')
                        "td", "th" -> if (node.elementSiblingIndex() > 0) sb.append(" · ")
                        else -> if (tag in BLOCKS) sb.append(BREAK)
                    }
                }
            }

            override fun tail(node: Node, depth: Int) {
                if (node is Element && node.normalName() in BLOCKS) sb.append(BREAK)
            }
        }, el)
        return split(sb.toString())
    }

    fun paragraphs(doc: Document): List<String> = paragraphs(doc.body() ?: doc)

    private const val BREAK = "\u0000"

    private fun split(raw: String): List<String> =
        raw.split(BREAK)
            .map { para ->
                para.lines()
                    .map { it.replace(WS, " ").trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
            }
            .filter { it.isNotEmpty() }

    private val WS = Regex("[\\s\\u00A0]+")
}
