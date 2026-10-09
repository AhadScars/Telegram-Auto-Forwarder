package com.shoaib.tgforward

import com.shoaib.tgforward.engine.AmznTo
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmznToTest {
    private val longUrl =
        "https://www.amazon.in/dp/B07PGL2N7J?tag=earnkaro-21&ascsubtag=user123&linkCode=ll1&linkId=abc"

    @Test
    fun requestKeepsTheEarnKaroUrlAndCopiesItsTag() {
        val request = AmznTo.requestUrl(longUrl)
        val longParam = request.substringAfter("longUrl=").substringBefore("&")
        assertEquals(longUrl, URLDecoder.decode(longParam, Charsets.UTF_8.name()))
        val names = request.substringAfter('?').split('&').map { it.substringBefore('=') }
        assertEquals(listOf("longUrl", "marketplaceId", "linkCode", "ref_", "tag"), names)
        assertTrue(request.contains("marketplaceId=44571"))
        assertTrue(request.contains("linkCode=sl1"))
        assertTrue(request.contains("tag=earnkaro-21"))
        assertFalse(request.contains("tag=my-store-21"))
    }

    @Test
    fun requestOmitsTagWhenTheLongUrlHasNone() {
        val bare = "https://www.amazon.in/dp/B07PGL2N7J"
        val request = AmznTo.requestUrl(bare)
        val names = request.substringAfter('?').split('&').map { it.substringBefore('=') }
        assertEquals(listOf("longUrl", "marketplaceId", "linkCode", "ref_"), names)
        assertEquals(bare, URLDecoder.decode(request.substringAfter("longUrl=").substringBefore("&"), "UTF-8"))
    }

    @Test
    fun parseShortUrlReadsAmznToAndRejectsFailure() {
        assertEquals(
            "https://amzn.to/4yAKsH9",
            AmznTo.parseShortUrl("""{"shortUrl":"https://amzn.to/4yAKsH9","isOk":true}"""),
        )
        assertEquals(
            "https://amzn.to/4fuNlzS",
            AmznTo.parseShortUrl("""{ "shortUrl" : "https://amzn.to/4fuNlzS" }"""),
        )
        assertNull(AmznTo.parseShortUrl("""{"shortUrl":"https://amzn.to/4yAKsH9","isOk":false}"""))
        assertNull(AmznTo.parseShortUrl("To discuss automated access to Amazon data please contact api-services-support@amazon.com."))
        assertNull(AmznTo.parseShortUrl("""{"shortUrl":"https://link.amazon/B079J3ZQJX","isOk":true}"""))
        assertNull(AmznTo.parseShortUrl(null))
    }

    @Test
    fun associateTagDoesNotReplaceTheDestinationUrl() {
        val request = AmznTo.requestUrl(longUrl, "myshop-21")
        val longParam = request.substringAfter("longUrl=").substringBefore("&")
        assertEquals(longUrl, URLDecoder.decode(longParam, Charsets.UTF_8.name()))
        assertTrue(request.endsWith("&tag=myshop-21"))
        assertFalse(request.contains("tag=earnkaro-21&") || request.endsWith("tag=earnkaro-21"))
    }

    @Test
    fun readsTheAssociatesTrackingId() {
        assertEquals(
            "myshop-21",
            AmznTo.parseTrackingId("""{"storeTagMap":{"shop":["myshop-21","other-21"]}}"""),
        )
        assertNull(AmznTo.parseTrackingId("no tags"))
    }

    @Test
    fun loginCookieIsTheIndiaAssociatesSession() {
        assertTrue(AmznTo.isLoggedIn("session-id=abc; at-acbin=Atza|xyz"))
        assertTrue(AmznTo.isLoggedIn("sess-at-acbin=token"))
        assertFalse(AmznTo.isLoggedIn("session-id=abc"))
        assertFalse(AmznTo.isLoggedIn("foo=at-acbin"))
        assertFalse(AmznTo.isLoggedIn("at-acbin="))
        assertFalse(AmznTo.isLoggedIn(null))
    }
}
