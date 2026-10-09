package com.shoaib.tgforward

import com.shoaib.tgforward.engine.LinkText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTextTest {
    private val longUrl =
        "https://www.amazon.in/Some-Very-Long-Product-Name-With-Extra-Words/dp/B07PGL2N7J/ref=sr_1_1?keywords=phone&qid=123456&tag=earnkaro-21"
    private val shortUrl = "https://www.amazon.in/dp/B07PGL2N7J?tag=earnkaro-21"

    @Test
    fun replacesAVisibleLongUrlAndKeepsTheWordsAfterIt() {
        val text = "Buy $longUrl now"
        val now = text.indexOf("now")
        val result = LinkText.rewrite(text, listOf(LinkText.Entity(now, 3, null))) { shortUrl }
        assertEquals("Buy $shortUrl now", result.text)
        assertTrue(result.changed)
        assertEquals(result.text.indexOf("now"), result.entities[0].offset)
        assertEquals(3, result.entities[0].length)
    }

    @Test
    fun shortensAHiddenButtonUrlWithoutChangingTheLabel() {
        val result = LinkText.rewrite("Buy", listOf(LinkText.Entity(0, 3, longUrl))) { shortUrl }
        assertEquals("Buy", result.text)
        assertEquals(shortUrl, result.entities[0].hiddenUrl)
        assertTrue(result.changed)
    }

    @Test
    fun leavesAnAlreadyShortLinkAlone() {
        val result = LinkText.rewrite("https://ekaro.in/deal123", emptyList()) { error("should not call") }
        assertFalse(result.changed)
        assertEquals("https://ekaro.in/deal123", result.text)
    }

    @Test
    fun leavesFlipkartAndAmznToAlone() {
        val flipkart = "https://www.flipkart.com/some-very-long-product-name-that-is-already-shortened-by-the-network/p/itm123"
        assertFalse(LinkText.needsShort(flipkart))
        assertFalse(LinkText.needsShort("https://amzn.to/4yAKsH9"))
        assertFalse(LinkText.needsShort("https://ekaro.in/enkr123"))
        assertTrue(LinkText.needsShort("https://link.amazon/B079J3ZQJX"))
    }

    @Test
    fun shortensTheMoreSearchLinkAndLeavesOtherShops() {
        val more =
            "https://www.amazon.in/s?rh=n%3A15723937031%2Cp_4%3AFastrack&s=price-asc-rank&tag=bytethegame0f-21"
        val text = "Watch https://amzn.to/4hPRXms\nMore : $more\nhttps://ekaro.in/deal"
        val result = LinkText.rewrite(text, emptyList()) { url ->
            check(url == more)
            "https://amzn.to/more01"
        }
        assertEquals(
            "Watch https://amzn.to/4hPRXms\nMore : https://amzn.to/more01\nhttps://ekaro.in/deal",
            result.text,
        )
        assertTrue(result.changed)
    }

    @Test
    fun turnsLinkAmazonIntoAmznTo() {
        val source = "Deal https://link.amazon/B079J3ZQJX"
        val result = LinkText.rewrite(source, emptyList()) { "https://amzn.to/4yAKsH9" }
        assertEquals("Deal https://amzn.to/4yAKsH9", result.text)
        assertTrue(result.changed)
    }

    @Test
    fun doesNotEatTheClosingBracket() {
        val text = "($longUrl)"
        val result = LinkText.rewrite(text, emptyList()) { shortUrl }
        assertEquals("($shortUrl)", result.text)
    }

    @Test
    fun reportsFailureWhenTheShortenerReturnsNothing() {
        val result = LinkText.rewrite(longUrl, emptyList()) { "" }
        assertFalse(result.changed)
        assertTrue(result.failed)
        assertEquals(longUrl, result.text)
    }
}
