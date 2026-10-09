package com.shoaib.tgforward.engine

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Amazon Associates SiteStripe is the only thing that mints a real amzn.to code.
 * The long URL is the EarnKaro link, so it is sent through unchanged. Its tag is
 * copied into the request and is never replaced by a different associate id.
 */
object AmznTo {
    const val HOME = "https://www.amazon.in/"
    const val MARKETPLACE_ID = "44571"
    const val SIGN_IN =
        "https://www.amazon.in/ap/signin?openid.pape.max_auth_age=0" +
            "&openid.return_to=https%3A%2F%2Fwww.amazon.in%2F" +
            "&openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select" +
            "&openid.assoc_handle=inflex" +
            "&openid.mode=checkid_setup" +
            "&openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select" +
            "&openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0"

    private val shortJson = Regex(""""shortUrl"\s*:\s*"(https://amzn\.to/[A-Za-z0-9]+)"""")
    private val notOk = Regex(""""isOk"\s*:\s*false""")
    private val trackingId = Regex(""""([A-Za-z0-9][A-Za-z0-9_-]{1,40}-[0-9]{2})"""")

    fun tagMapUrl(): String =
        "https://www.amazon.in/associates/sitestripe/getStoreTagMap?marketplaceId=$MARKETPLACE_ID"

    /**
     * [longUrl] is sent unchanged, so an EarnKaro tag and ascsubtag stay in the
     * destination. [associateTag] is only the SiteStripe account parameter.
     */
    fun requestUrl(longUrl: String, associateTag: String? = null): String {
        val encoded = URLEncoder.encode(longUrl, Charsets.UTF_8.name())
        val builder = StringBuilder()
        builder.append("https://www.amazon.in/associates/sitestripe/getShortUrl")
        builder.append("?longUrl=").append(encoded)
        builder.append("&marketplaceId=").append(MARKETPLACE_ID)
        builder.append("&linkCode=sl1")
        builder.append("&ref_=as_li_ss_tl")
        val tag = associateTag?.takeIf { it.isNotBlank() } ?: tagOf(longUrl)
        if (!tag.isNullOrBlank()) {
            builder.append("&tag=").append(URLEncoder.encode(tag, Charsets.UTF_8.name()))
        }
        return builder.toString()
    }

    fun tagOf(url: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty() || !url.contains('?')) return null
        for (part in query.split('&')) {
            if (part.substringBefore('=') != "tag") continue
            val raw = part.substringAfter('=', "")
            if (raw.isEmpty()) return null
            return try {
                URLDecoder.decode(raw, Charsets.UTF_8.name())
            } catch (_: Exception) {
                raw
            }
        }
        return null
    }

    fun parseShortUrl(body: String?): String? {
        if (body.isNullOrBlank()) return null
        if (notOk.containsMatchIn(body)) return null
        val url = shortJson.find(body)?.groupValues?.get(1) ?: return null
        return if (url.startsWith("https://amzn.to/")) url else null
    }

    fun parseTrackingId(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return trackingId.find(body)?.groupValues?.get(1)
    }

    fun isLoggedIn(cookieHeader: String?): Boolean {
        if (cookieHeader.isNullOrBlank()) return false
        return cookieHeader.split(';').any { part ->
            val piece = part.trim()
            val name = piece.substringBefore('=').trim()
            val value = piece.substringAfter('=', "")
            value.isNotEmpty() && (name == "at-acbin" || name == "sess-at-acbin")
        }
    }
}
