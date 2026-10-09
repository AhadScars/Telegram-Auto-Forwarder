package com.shoaib.tgforward.engine

sealed interface AuthStep {
    data object NeedApi : AuthStep
    data object Connecting : AuthStep
    data object Phone : AuthStep
    data class Code(val hint: String) : AuthStep
    data class Password(val hint: String) : AuthStep
    data object Email : AuthStep
    data object EmailCode : AuthStep
    data object Registration : AuthStep
    data class OtherDevice(val link: String) : AuthStep
    data object Ready : AuthStep
}

enum class ChatFilter { All, Channels, Groups, Bots, Chats }

data class ChatItem(
    val id: Long,
    val title: String,
    val kind: String,
    val protectedContent: Boolean,
)

object ForwardPolicy {
    fun shouldForward(
        enabled: Boolean,
        chatId: Long,
        sourceIds: Set<Long>,
        targetId: Long?,
        messageUnix: Long,
        enabledSinceUnix: Long,
        stillSending: Boolean,
        scheduled: Boolean,
        contentSupported: Boolean,
        alreadyForwarded: Boolean,
    ): Boolean {
        if (!enabled || alreadyForwarded || stillSending || scheduled || !contentSupported) return false
        if (targetId == null || targetId == 0L) return false
        if (chatId == targetId) return false
        if (chatId !in sourceIds) return false
        return messageUnix >= enabledSinceUnix
    }
}
