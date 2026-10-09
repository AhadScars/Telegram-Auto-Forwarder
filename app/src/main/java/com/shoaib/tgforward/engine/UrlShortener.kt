package com.shoaib.tgforward.engine

object UrlShortener {
    fun shorten(url: String): String {
        if (!AmazonLinks.shouldShorten(url)) return url
        val amzn = try {
            AmazonClient.shortenBlocking(url)
        } catch (_: Exception) {
            null
        }
        if (amzn != null && amzn.startsWith("https://amzn.to/") && amzn.length < url.length) {
            return amzn
        }
        val compact = AmazonLinks.compact(url)
        return if (compact != url && compact.length < url.length) compact else url
    }
}
