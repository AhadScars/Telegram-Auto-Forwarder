package com.shoaib.tgforward.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoaib.tgforward.engine.AmazonClient
import com.shoaib.tgforward.engine.ChatFilter
import com.shoaib.tgforward.engine.ChatItem
import com.shoaib.tgforward.engine.ForwardEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    engine: ForwardEngine,
    account: String,
    chats: List<ChatItem>,
    sources: Set<Long>,
    targetId: Long?,
    enabled: Boolean,
    copyWithoutHeader: Boolean,
    shortChatId: Long?,
    shortenEnabled: Boolean,
    loading: Boolean,
    error: String?,
    log: List<String>,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(ChatFilter.All) }
    var amazonOpen by rememberSaveable { mutableStateOf(false) }
    var amazonLinked by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(amazonOpen) {
        if (!amazonOpen) amazonLinked = AmazonClient.loggedIn()
    }
    if (amazonOpen) {
        AmazonLoginScreen(
            onDone = { amazonOpen = false },
            onClose = { amazonOpen = false },
        )
        return
    }

    val target = chats.firstOrNull { it.id == targetId }
    val shortChat = chats.firstOrNull { it.id == shortChatId }
    val shown = chats.filter { chat ->
        val kindOk = when (filter) {
            ChatFilter.All -> true
            ChatFilter.Channels -> chat.kind == "Channel"
            ChatFilter.Groups -> chat.kind == "Group"
            ChatFilter.Bots -> chat.kind == "Bot"
            ChatFilter.Chats -> chat.kind == "Chat" || chat.kind == "Secret"
        }
        kindOk && (query.isBlank() || chat.title.contains(query, ignoreCase = true))
    }.sortedWith(
        compareByDescending<ChatItem> { it.id in sources || it.id == targetId || it.id == shortChatId }
            .thenBy { it.title.lowercase() }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = {
                    Column {
                        Text("Auto Post", style = MaterialTheme.typography.titleMedium)
                        if (account.isNotBlank()) {
                            Text(account, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = engine::refreshChats) { Text("Refresh") }
                    TextButton(onClick = engine::logout) { Text("Logout") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        SwitchRow("Auto post", enabled) { on ->
                            if (on && Build.VERSION.SDK_INT >= 33) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            engine.setEnabled(on)
                        }
                        Text(
                            "${sources.size} source  ·  ${target?.title ?: "Target"}  ·  ${shortChat?.title ?: "Short"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                        SwitchRow("amzn.to", shortenEnabled, engine::setShortenEnabled)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { amazonOpen = true },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            ) {
                                Text(if (amazonLinked) "Amazon linked" else "Amazon login")
                            }
                            if (amazonLinked) {
                                TextButton(
                                    onClick = {
                                        AmazonClient.logout()
                                        amazonLinked = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) { Text("Logout") }
                            }
                        }
                        SwitchRow("Copy", copyWithoutHeader, engine::setCopyWithoutHeader)
                    }
                }
            }
            if (!error.isNullOrBlank()) {
                item {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search") },
                )
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(filter == ChatFilter.All, { filter = ChatFilter.All }, label = { Text("All ${chats.size}") })
                    FilterChip(filter == ChatFilter.Channels, { filter = ChatFilter.Channels }, label = { Text("Channels") })
                    FilterChip(filter == ChatFilter.Groups, { filter = ChatFilter.Groups }, label = { Text("Groups") })
                    FilterChip(filter == ChatFilter.Bots, { filter = ChatFilter.Bots }, label = { Text("Bots") })
                    FilterChip(filter == ChatFilter.Chats, { filter = ChatFilter.Chats }, label = { Text("Chats") })
                }
            }
            if (loading && chats.isEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text("Loading", modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(shown, key = { it.id }) { chat ->
                ChatRow(
                    chat = chat,
                    isSource = chat.id in sources,
                    isTarget = chat.id == targetId,
                    isShort = chat.id == shortChatId,
                    onSource = { engine.toggleSource(chat.id) },
                    onTarget = { engine.selectTarget(chat.id) },
                    onShort = { engine.selectShortChat(chat.id) },
                )
            }
            if (!loading && shown.isEmpty()) {
                item { Text("Koi chat nahi.", style = MaterialTheme.typography.bodySmall) }
            }
            item {
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }) { Text("Battery") }
            }
            if (log.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        log.take(4).forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MiniAction(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
    ) {
        Text(
            if (selected) "$label ✓" else label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatRow(
    chat: ChatItem,
    isSource: Boolean,
    isTarget: Boolean,
    isShort: Boolean,
    onSource: () -> Unit,
    onTarget: () -> Unit,
    onShort: () -> Unit,
) {
    val border = when {
        isShort -> BorderStroke(1.dp, Color(0xFF0F766E))
        isTarget -> BorderStroke(1.dp, Color(0xFF2563EB))
        isSource -> BorderStroke(1.dp, Color(0xFF93C5FD))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = border,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(chat.title, fontWeight = FontWeight.Medium, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                val extra = if (chat.protectedContent) " · lock" else ""
                Text(
                    chat.kind + extra,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            MiniAction("Src", isSource, !isTarget, onSource)
            MiniAction("To", isTarget, true, onTarget)
            MiniAction("Sh", isShort, true, onShort)
        }
    }
}
