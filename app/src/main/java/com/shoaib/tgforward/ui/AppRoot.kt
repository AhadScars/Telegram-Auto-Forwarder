package com.shoaib.tgforward.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shoaib.tgforward.engine.AuthStep
import com.shoaib.tgforward.engine.ForwardEngine

@Composable
fun AppRoot(engine: ForwardEngine) {
    val step by engine.step.collectAsStateWithLifecycle()
    val busy by engine.busy.collectAsStateWithLifecycle()
    val error by engine.error.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (step is AuthStep.Ready) {
            val account by engine.account.collectAsStateWithLifecycle()
            val chats by engine.chats.collectAsStateWithLifecycle()
            val sources by engine.sources.collectAsStateWithLifecycle()
            val targetId by engine.targetId.collectAsStateWithLifecycle()
            val enabled by engine.enabled.collectAsStateWithLifecycle()
            val copy by engine.copyWithoutHeader.collectAsStateWithLifecycle()
            val shortChatId by engine.shortChatId.collectAsStateWithLifecycle()
            val shortenEnabled by engine.shortenEnabled.collectAsStateWithLifecycle()
            val loading by engine.loadingChats.collectAsStateWithLifecycle()
            val log by engine.log.collectAsStateWithLifecycle()
            HomeScreen(
                engine, account, chats, sources, targetId, enabled, copy,
                shortChatId, shortenEnabled, loading, error, log,
            )
        } else {
            Box(Modifier.fillMaxSize()) {
                AuthScreen(engine, step, busy, error)
            }
        }
    }
}
