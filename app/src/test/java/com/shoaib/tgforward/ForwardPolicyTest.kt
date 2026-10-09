package com.shoaib.tgforward

import com.shoaib.tgforward.engine.ForwardPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardPolicyTest {
    @Test
    fun copiesAFreshSourcePost() {
        assertTrue(
            ForwardPolicy.shouldForward(
                enabled = true,
                chatId = 10,
                sourceIds = setOf(10, 11),
                targetId = 99,
                messageUnix = 1_000,
                enabledSinceUnix = 900,
                stillSending = false,
                scheduled = false,
                contentSupported = true,
                alreadyForwarded = false,
            )
        )
    }

    @Test
    fun skipsWhenSwitchIsOff() {
        assertFalse(base(enabled = false))
    }

    @Test
    fun skipsChatsThatAreNotSources() {
        assertFalse(base(chatId = 50))
    }

    @Test
    fun skipsTheTargetSoItCannotLoop() {
        assertFalse(base(chatId = 99, sourceIds = setOf(99), targetId = 99))
    }

    @Test
    fun skipsPostsFromBeforeTheSwitchWasTurnedOn() {
        assertFalse(base(messageUnix = 100, enabledSinceUnix = 900))
    }

    @Test
    fun skipsUnsentScheduledAndUnsupportedMessages() {
        assertFalse(base(stillSending = true))
        assertFalse(base(scheduled = true))
        assertFalse(base(contentSupported = false))
        assertFalse(base(alreadyForwarded = true))
        assertFalse(base(targetId = null))
    }

    private fun base(
        enabled: Boolean = true,
        chatId: Long = 10,
        sourceIds: Set<Long> = setOf(10),
        targetId: Long? = 99,
        messageUnix: Long = 1_000,
        enabledSinceUnix: Long = 900,
        stillSending: Boolean = false,
        scheduled: Boolean = false,
        contentSupported: Boolean = true,
        alreadyForwarded: Boolean = false,
    ) = ForwardPolicy.shouldForward(
        enabled,
        chatId,
        sourceIds,
        targetId,
        messageUnix,
        enabledSinceUnix,
        stillSending,
        scheduled,
        contentSupported,
        alreadyForwarded,
    )
}
