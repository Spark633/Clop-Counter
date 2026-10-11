package de.clopcounter.data

import android.net.Uri
import android.text.Html
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LinkPreview(val imageUrl: String?, val title: String?)

/**
 * Finds a preview image and title for a link: oEmbed for YouTube/TikTok, otherwise the page's
 * og:image / twitter:image, falling back to noembed.com.
 */
object LinkPreviewFetcher {
    private const val MAX_HTML_BYTES = 768 * 1024
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    // Sites serve Open Graph tags to link-preview bots without cookie/consent walls.
    private const val PREVIEW_BOT_USER_AGENT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"

    private val youtubeId = Regex(
        "(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|embed/|live/|v/)|youtu\\.be/)([A-Za-z0-9_-]{11})",
        RegexOption.IGNORE_CASE,
    )

    private val metaTag = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val linkTag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val attribute = Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
    private val titleTag = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun fetch(url: String): LinkPreview? {
        youtubeId.find(url)?.groupValues?.get(1)?.let { id ->
            val oembed = oEmbed("https://www.youtube.com/oembed?format=json&url=${encode(url)}")
            return LinkPreview(
                imageUrl = oembed?.imageUrl ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                title = oembed?.title,
            )
        }

        val host = runCatching { Uri.parse(url).host.orEmpty().lowercase().removePrefix("www.") }.getOrDefault("")
        if (host == "tiktok.com" || host.endsWith(".tiktok.com")) {
            oEmbed("https://www.tiktok.com/oembed?url=${encode(url)}")?.takeIf { it.imageUrl != null }?.let { return it }
        }

        // fxtwitter serves proper preview tags for posts on X/Twitter.
        val pageUrl = if (host == "x.com" || host == "twitter.com" || host == "mobile.twitter.com") {
            Uri.parse(url).buildUpon().authority("fxtwitter.com").build().toString()
        } else {
            url
        }

        var fallback: LinkPreview? = null
        for (agent in listOf(PREVIEW_BOT_USER_AGENT, BROWSER_USER_AGENT)) {
            val page = runCatching { fetchPage(pageUrl, agent, redirectsLeft = 5) }.getOrNull()
            if (page?.imageUrl != null) return page
            if (fallback?.title == null) fallback = page ?: fallback
        }

        val oembed = oEmbed("https://noembed.com/embed?url=${encode(url)}")
        if (oembed?.imageUrl != null) return oembed.copy(title = fallback?.title ?: oembed.title)
        return fallback
    }

    private fun oEmbed(endpoint: String): LinkPreview? = runCatching {
        val conn = open(endpoint, BROWSER_USER_AGENT).apply { instanceFollowRedirects = true }
        try {
            if (conn.responseCode !in 200..299) return null
            val json = JSONObject(conn.inputStream.use { readLimited(it) }.toString(Charsets.UTF_8))
            if (json.has("error")) return null
            val image = json.optString("thumbnail_url").ifEmpty {
                if (json.optString("type") == "photo") json.optString("url") else ""
            }
            LinkPreview(
                imageUrl = image.takeIf { it.isNotEmpty() },
                title = json.optString("title").takeIf { it.isNotBlank() },
            )
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun open(url: String, userAgent: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("User-Agent", userAgent)
        setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun fetchPage(url: String, userAgent: String, redirectsLeft: Int): LinkPreview? {
        val conn = open(url, userAgent).apply {
            instanceFollowRedirects = false
            setRequestProperty("Accept", "text/html,application/xhtml+xml,image/*;q=0.9,*/*;q=0.8")
        }
        try {
            val code = conn.responseCode
            // Follow redirects manually so http <-> https switches work too.
            if (code in 300..399 && redirectsLeft > 0) {
                val location = conn.getHeaderField("Location") ?: return null
                return fetchPage(URL(URL(url), location).toString(), userAgent, redirectsLeft - 1)
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
