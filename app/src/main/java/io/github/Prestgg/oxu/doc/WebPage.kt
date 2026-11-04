// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.Prestgg.oxu.doc

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

/** A web page reduced to its readable text: no menus, ads, scripts or styling. */
data class WebPage(val url: String, val title: String, val paragraphs: List<String>)

object WebReader {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0 Mobile Safari/537.36 Oxu/1.0"
    private const val MAX_BYTES = 8 * 1024 * 1024

    /** Finds the first http(s) link in shared text ("Look at this: https://…"). */
    fun findUrl(text: String?): String? =
        text?.let { Regex("https?://\\S+").find(it)?.value?.trimEnd('.', ',', ')', ']', '"', '\'') }

    /** Adds https:// when the user typed just "example.com/article". */
    fun normalizeUrl(input: String): String {
        val t = input.trim()
        return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t)) t else "https://$t"
    }

    /** Downloads and extracts a page. Blocking: call from a background thread. */
    fun load(url: String): WebPage {
        val response = try {
            Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(20_000)
                .maxBodySize(MAX_BYTES)
                .followRedirects(true)
                .execute()
        } catch (e: org.jsoup.HttpStatusException) {
            throw IOException("The site answered with error ${e.statusCode}")
        } catch (e: org.jsoup.UnsupportedMimeTypeException) {
            throw IOException("This link is not a web page (${e.mimeType})")
        }
        val doc = response.parse()
        return extract(doc, response.url().toString())
    }

    /** Exposed for tests: pulls the article out of an already parsed page. */
    fun extract(doc: Document, url: String): WebPage {
        val title = title(doc)

        val body = doc.body() ?: return WebPage(url, title, listOf(title).filter { it.isNotEmpty() })
        strip(body)

        val main = mainContent(body)
        dropLinkLists(main)
        var paras = Html.paragraphs(main)
        // The guess was too narrow (e.g. a page made of many small blocks): use the whole page.
        if (paras.sumOf { it.length } < 300) paras = Html.paragraphs(body)
        paras = paras
            .filter { it.length >= 3 } // leftovers like "|" or "»"
            .zipWithNext().let { pairs -> // repeated captions: keep the first of a run
                listOfNotNull(paras.firstOrNull { it.length >= 3 }) +
                    pairs.filter { (a, b) -> a != b }.map { it.second }
            }
        // The title goes first, once (pages often repeat it above the article).
        if (title.isNotEmpty()) paras = listOf(title) + paras.filterIndexed { i, p -> !(i < 5 && p == title) }
        if (paras.isEmpty()) throw IOException("No readable text found on this page")
        return WebPage(url, title.ifEmpty { url }, paras)
    }

    private val NOISE = Regex(
        "(^|[-_ ])(nav|navbar|menu|breadcrumbs?|sidebar|side-bar|footer|header|masthead|comments?|" +
            "share|sharing|social|related|recommended|promo|advert|ads?|banner|cookies?|consent|" +
            "newsletter|subscribe|popup|modal|signup|login|toolbar|widget|sponsored|infobox|navbox|" +
            "toc|mw-editsection|reflist|references)([-_ ]|$)",
        RegexOption.IGNORE_CASE
    )

    private val CITATION = Regex("\\[(\\d+|[a-z]|note \\d+|citation needed|edit)]", RegexOption.IGNORE_CASE)

    /** Removes page furniture that is never part of an article. */
    private fun strip(body: Element) {
        body.select(
            "nav, footer, aside, form, [role=navigation], [role=banner], [role=contentinfo], " +
                "[role=complementary], [aria-hidden=true], [hidden], dialog"
        ).remove()
        // Citation marks "[6]", "[note 2]" and "[edit]" links interrupt the sentences.
        body.select("sup, span").filter { CITATION.matches(it.text()) }.forEach { it.remove() }
        // <header> inside an article holds its title; elsewhere it is the site header.
        body.select("header").filter { it.closest("article") == null }.forEach { it.remove() }
        // A wrapper named e.g. "has-sidebar" may hold the whole article: never drop big blocks.
        val bodyLen = body.text().length.coerceAtLeast(1)
        body.select("[class], [id]").filter { el ->
            el.normalName() !in setOf("article", "main", "body") &&
                NOISE.containsMatchIn(el.className() + " " + el.id()) &&
                el.selectFirst("article, main, [role=main]") == null &&
                el.text().length < bodyLen * 0.3
        }.forEach { it.remove() }
    }

    /**
     * The element holding the article: an explicit <article>/<main> when it carries most of the
     * text, otherwise the block whose paragraphs add up to the most text (Readability's idea).
     */
    private fun mainContent(body: Element): Element {
        val bodyLen = body.text().length.coerceAtLeast(1)
        body.select("article, main, [role=main], [itemprop=articleBody]")
            .maxByOrNull { it.text().length }
            ?.takeIf { it.text().length > bodyLen * 0.4 }
            ?.let { return it }

        val scores = HashMap<Element, Double>()
        for (p in body.select("p, pre, blockquote")) {
            val len = p.text().length
            if (len < 25) continue
            val score = 1.0 + len / 100.0 + p.text().count { it == ',' }
            p.parent()?.let { scores.merge(it, score, Double::plus) }
            p.parent()?.parent()?.let { scores.merge(it, score / 2, Double::plus) }
        }
        return scores.maxByOrNull { (el, score) -> score * (1 - linkDensity(el)) }?.key ?: body
    }

    /**
     * "Baku - Wikipedia" → "Baku": the page's own <h1> wins when the site title merely
     * decorates it with the site name.
     */
    private fun title(doc: Document): String {
        val h1 = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        val meta = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty()
        val head = doc.title().trim()
        return when {
            h1.isNotEmpty() && (meta.contains(h1) || head.contains(h1)) -> h1
            meta.isNotEmpty() -> meta
            h1.isNotEmpty() -> h1
            else -> head
        }
    }

    /** Tabs, tag clouds, "related articles", navigation boxes: blocks that are mostly links. */
    private fun dropLinkLists(root: Element) {
        root.select("ul, ol, table, div, section, p").reversed().forEach { el ->
            if (el === root || el.parent() == null) return@forEach
            val len = el.text().length
            if (len == 0) return@forEach
            val density = linkDensity(el)
            if ((density > 0.6 && len < 2000) || (el.normalName() in setOf("ul", "ol", "table") && density > 0.45)) {
                el.remove()
            }
        }
    }

    private fun linkDensity(el: Element): Double {
        val total = el.text().length.coerceAtLeast(1)
        val links = el.select("a").sumOf { it.text().length }
        return (links.toDouble() / total).coerceIn(0.0, 1.0)
    }
}
