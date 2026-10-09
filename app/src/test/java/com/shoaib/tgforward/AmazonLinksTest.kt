package com.shoaib.tgforward

import com.shoaib.tgforward.engine.AmazonLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmazonLinksTest {
    @Test
    fun keepsTheProductAndTheAffiliateTag() {
        val longUrl =
            "https://www.amazon.in/Some-Very-Long-Product-Name-With-Extra-Words/dp/B07PGL2N7J/ref=sr_1_1" +
                "?keywords=phone&qid=123456&sr=8-1&tag=earnkaro-21&ascsubtag=user123&linkCode=ll1&linkId=abc&th=1&psc=1"
        assertEquals(
            "https://www.amazon.in/dp/B07PGL2N7J?tag=earnkaro-21&ascsubtag=user123&linkCode=ll1&linkId=abc",
            AmazonLinks.compact(longUrl),
        )
        assertTrue(AmazonLinks.canCompact(longUrl))
    }

    @Test
    fun ignoresLinksThatAreNotAmazonProducts() {
        assertFalse(AmazonLinks.canCompact("https://ekaro.in/enkr123"))
        assertFalse(AmazonLinks.canCompact("https://amzn.to/4yAKsH9"))
        assertFalse(AmazonLinks.canCompact("https://www.amazon.in/dp/B07PGL2N7J?tag=earnkaro-21"))
    }

    @Test
    fun onlyAmazonProductLinksAreSentToTheShortener() {
        assertTrue(AmazonLinks.shouldShorten("https://link.amazon/B079J3ZQJX"))
        assertTrue(AmazonLinks.shouldShorten("https://www.amazon.in/dp/B07PGL2N7J?tag=earnkaro-21"))
        assertTrue(AmazonLinks.shouldShorten(longProduct()))
        assertFalse(AmazonLinks.shouldShorten("https://amzn.to/4yAKsH9"))
        assertFalse(AmazonLinks.shouldShorten("https://ekaro.in/enkr123"))
        assertFalse(AmazonLinks.shouldShorten("https://www.flipkart.com/some-very-long-product-name/p/itm123"))
        assertFalse(AmazonLinks.shouldShorten("https://a.co/d/abc123"))
        val more =
            "https://www.amazon.in/s?rh=n%3A15723937031%2Cp_4%3AFastrack&s=price-asc-rank&tag=bytethegame0f-21"
        assertTrue(AmazonLinks.shouldShorten(more))
        assertFalse(AmazonLinks.canCompact(more))
    }

    private fun longProduct() =
        "https://www.amazon.in/Some-Very-Long-Product-Name/dp/B07PGL2N7J?tag=earnkaro-21"
}
