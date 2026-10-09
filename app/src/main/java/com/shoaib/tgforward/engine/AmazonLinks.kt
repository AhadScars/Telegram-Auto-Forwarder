package com.shoaib.tgforward.engine

import java.net.URI

object AmazonLinks {
    private val asinInPath = Regex(
        """/(?:dp|gp/product|gp/aw/d)/([A-Za-z0-9]{10})(?![A-Za-z0-9])""",
        RegexOption.IGNORE_CASE,
    )
    private val keepParams = setOf(
        "tag",
        "ascsubtag",
        "linkCode",
        "linkId",
        "language",
        "camp",
        "creative",
        "creativeASIN",
    )

    fun canCompact(url: String): Boolean {
        val next = compact(url)
        return next != url && next.length < url.length
    }

    /** Long Amazon pages, including search and store links. Other shops are ignored. */
    fun shouldShorten(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false
        if (isOfficialShort(host)) return false
        if (host == "link.amazon" || host.endsWith(".link.amazon")) return true
        if (!isAmazonStore(host)) return false
        return url.length > 23
    }

    fun compact(url: String): String {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return url
        }
        val host = uri.host?.lowercase() ?: return url
        if (!isAmazonStore(host)) return url
        val asin = asinInPath.find(uri.rawPath ?: uri.path.orEmpty())?.groupValues?.get(1)?.uppercase()
            ?: return url
        val query = keptQuery(uri.rawQuery)
        return "https://${canonicalHost(host)}/dp/$asin$query"
    }

    private fun isOfficialShort(host: String): Boolean {
        return host == "amzn.to" || host.endsWith(".amzn.to") ||
            host == "amzn.eu" || host == "amzn.asia"
    }

    private fun isAmazonStore(host: String): Boolean {
        if (isOfficialShort(host)) return false
        if (host == "link.amazon" || host.endsWith(".link.amazon")) return false
        if (host == "a.co" || host.endsWith(".a.co")) return false
        if (host == "amzn.in") return false
        return host == "amazon.com" || host.endsWith(".amazon.com") ||
            host == "amazon.in" || host.endsWith(".amazon.in") ||
            host == "amazon.co.uk" || host.endsWith(".amazon.co.uk") ||
            host == "amazon.de" || host.endsWith(".amazon.de") ||
            host == "amazon.ca" || host.endsWith(".amazon.ca") ||
            host == "amazon.com.au" || host.endsWith(".amazon.com.au") ||
            host == "amazon.ae" || host.endsWith(".amazon.ae") ||
            host == "amazon.sg" || host.endsWith(".amazon.sg")
    }

    private fun canonicalHost(host: String): String {
        val bare = host.removePrefix("www.").removePrefix("m.").removePrefix("smile.")
        return "www.$bare"
    }

    private fun keptQuery(rawQuery: String?): String {
        if (rawQuery.isNullOrBlank()) return ""
        val parts = rawQuery.split("&").filter { part ->
            part.substringBefore("=") in keepParams && part.substringAfter("=", "").isNotEmpty()
        }
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }
}
