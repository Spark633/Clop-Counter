package de.clopcounter.data

import android.text.Html
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class LinkPreview(val imageUrl: String?, val title: String?)

/** Minimal Open-Graph scraper: finds og:image / twitter:image and the page title. */
object LinkPreviewFetcher {
    private const val MAX_HTML_BYTES = 768 * 1024
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val metaTag = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val linkTag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val attribute = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
    private val titleTag = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun fetch(url: String): LinkPreview? = runCatching { fetchOrThrow(url, redirectsLeft = 5) }.getOrNull()

    private fun fetchOrThrow(url: String, redirectsLeft: Int): LinkPreview? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,image/*;q=0.9,*/*;q=0.8")
        }
        try {
            val code = conn.responseCode
            // Follow redirects manually so http <-> https switches work too.
            if (code in 300..399 && redirectsLeft > 0) {
                val location = conn.getHeaderField("Location") ?: return null
                return fetchOrThrow(URL(URL(url), location).toString(), redirectsLeft - 1)
            }
            if (code !in 200..299) return null

            val type = conn.contentType.orEmpty().lowercase()
            if (type.startsWith("image/")) return LinkPreview(imageUrl = url, title = null)
            if (!type.contains("html")) return LinkPreview(imageUrl = null, title = null)

            val html = conn.inputStream.use { readLimited(it) }.toString(Charsets.UTF_8)
            return parse(html, baseUrl = url)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(html: String, baseUrl: String): LinkPreview {
        val metas = metaTag.findAll(html).map { attributes(it.value) }.toList()
        fun meta(key: String): String? = metas.firstOrNull {
            (it["property"] ?: it["name"] ?: it["itemprop"])?.equals(key, ignoreCase = true) == true
        }?.get("content")?.takeIf { it.isNotBlank() }

        val image = meta("og:image:secure_url")
            ?: meta("og:image")
            ?: meta("og:image:url")
            ?: meta("twitter:image")
            ?: meta("twitter:image:src")
            ?: meta("image")
            ?: linkTag.findAll(html).map { attributes(it.value) }
                .firstOrNull { it["rel"]?.contains("image_src", ignoreCase = true) == true }?.get("href")

        val title = meta("og:title")
            ?: meta("twitter:title")
            ?: titleTag.find(html)?.groupValues?.get(1)

        return LinkPreview(
            imageUrl = image?.let { runCatching { URL(URL(baseUrl), unescape(it).trim()).toString() }.getOrNull() },
            title = title?.let { unescape(it).trim() }?.takeIf { it.isNotEmpty() },
        )
    }

    private fun attributes(tag: String): Map<String, String> =
        attribute.findAll(tag).associate { m ->
            val v = m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value ?: ""
            m.groupValues[1].lowercase() to v
        }

    private fun unescape(s: String): String = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < MAX_HTML_BYTES) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
