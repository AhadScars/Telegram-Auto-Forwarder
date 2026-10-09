package com.shoaib.tgforward.engine

import android.content.Context
import android.os.Build
import io.github.tdlibandroid.ktx.TdClient
import io.github.tdlibandroid.ktx.TdException
import java.io.File
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import com.shoaib.tgforward.service.ForwardService

class ForwardEngine(private val app: Context) {
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendMutex = Mutex()
    private val shortenMutex = Mutex()
    private val ownedIds = HashSet<Long>()

    private val _step = MutableStateFlow<AuthStep>(AuthStep.Connecting)
    val step: StateFlow<AuthStep> = _step.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatItem>>(emptyList())
    val chats: StateFlow<List<ChatItem>> = _chats.asStateFlow()

    private val _sources = MutableStateFlow<Set<Long>>(emptySet())
    val sources: StateFlow<Set<Long>> = _sources.asStateFlow()

    private val _targetId = MutableStateFlow<Long?>(null)
    val targetId: StateFlow<Long?> = _targetId.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _copyWithoutHeader = MutableStateFlow(true)
    val copyWithoutHeader: StateFlow<Boolean> = _copyWithoutHeader.asStateFlow()

    private val _shortChatId = MutableStateFlow<Long?>(null)
    val shortChatId: StateFlow<Long?> = _shortChatId.asStateFlow()

    private val _shortenEnabled = MutableStateFlow(false)
    val shortenEnabled: StateFlow<Boolean> = _shortenEnabled.asStateFlow()

    private val _account = MutableStateFlow("")
    val account: StateFlow<String> = _account.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _loadingChats = MutableStateFlow(false)
    val loadingChats: StateFlow<Boolean> = _loadingChats.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private var client: TdClient? = null
    private var updatesJob: Job? = null
    private var apiId: Int = 0
    private var apiHash: String = ""

    @Volatile
    private var enabledSince: Long = 0L

    @Volatile
    private var shortenSince: Long = 0L

    private var reconnects = 0

    private val done = Collections.synchronizedSet(LinkedHashSet<String>())
    private val inFlight = Collections.synchronizedSet(HashSet<String>())
    private val albums = HashMap<String, AlbumBucket>()

    fun bootstrap() {
        _sources.value = prefs.getString(KEY_SOURCES, "").orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }
            .toSet()
        _targetId.value = prefs.getLong(KEY_TARGET, 0L).takeIf { it != 0L }
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
        _copyWithoutHeader.value = prefs.getBoolean(KEY_COPY, true)
        _shortChatId.value = prefs.getLong(KEY_SHORT_CHAT, 0L).takeIf { it != 0L }
        _shortenEnabled.value = prefs.getBoolean(KEY_SHORT_ON, false)
        enabledSince = prefs.getLong(KEY_SINCE, 0L)
        shortenSince = prefs.getLong(KEY_SHORT_SINCE, 0L)
        apiId = prefs.getInt(KEY_API_ID, 0)
        apiHash = prefs.getString(KEY_API_HASH, "").orEmpty()
        if (apiId == 0 || apiHash.length < 8) {
            _step.value = AuthStep.NeedApi
        } else {
            connect()
        }
    }

    fun saveApi(idText: String, hash: String) {
        val id = idText.trim().toIntOrNull()
        val cleanHash = hash.trim()
        if (id == null || id <= 0 || cleanHash.length < 8) {
            _error.value = "API ID number hona chahiye, aur API hash poora paste karo."
            return
        }
        _error.value = null
        apiId = id
        apiHash = cleanHash
        prefs.edit().putInt(KEY_API_ID, id).putString(KEY_API_HASH, cleanHash).apply()
        connect()
    }

    fun submitPhone(phone: String) {
        val number = phone.trim().replace(" ", "")
        if (!number.startsWith("+") || number.length < 8) {
            _error.value = "Number country code ke saath likho, jaise +9198XXXXXXXX."
            return
        }
        sendAuth(TdApi.SetAuthenticationPhoneNumber(number, phoneSettings()))
    }

    fun submitCode(code: String) {
        val clean = code.trim()
        if (clean.isEmpty()) {
            _error.value = "Code khali hai."
            return
        }
        sendAuth(TdApi.CheckAuthenticationCode(clean))
    }

    fun resendCode() {
        sendAuth(TdApi.ResendAuthenticationCode(TdApi.ResendCodeReasonUserRequest()))
    }

    fun submitPassword(password: String) {
        if (password.isEmpty()) {
            _error.value = "Password khali hai."
            return
        }
        sendAuth(TdApi.CheckAuthenticationPassword(password))
    }

    fun submitEmail(email: String) {
        val clean = email.trim()
        if (!clean.contains("@")) {
            _error.value = "Email sahi nahi hai."
            return
        }
        sendAuth(TdApi.SetAuthenticationEmailAddress(clean))
    }

    fun submitEmailCode(code: String) {
        val clean = code.trim()
        if (clean.isEmpty()) {
            _error.value = "Email code khali hai."
            return
        }
        sendAuth(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(clean)))
    }

    fun submitRegistration(first: String, last: String) {
        if (first.isBlank()) {
            _error.value = "Pehla naam likho."
            return
        }
        sendAuth(TdApi.RegisterUser(first.trim(), last.trim(), false))
    }

    fun clearError() {
        _error.value = null
    }

    fun refreshChats() {
        scope.launch { loadChats() }
    }

    fun toggleSource(id: Long) {
        if (_targetId.value == id) {
            _error.value = "Target channel source nahi ban sakta."
            return
        }
        val next = _sources.value.toMutableSet()
        if (!next.add(id)) next.remove(id)
        _sources.value = next
        saveRules()
    }

    fun selectTarget(id: Long) {
        val next = if (_targetId.value == id) null else id
        if (next != null && next == _shortChatId.value) {
            _error.value = "Yeh channel Short ke liye hai. Target Earnkaro bot rakho."
            return
        }
        _targetId.value = next
        if (next != null) _sources.value = _sources.value - next
        saveRules()
    }

    fun setCopyWithoutHeader(on: Boolean) {
        _copyWithoutHeader.value = on
        saveRules()
    }

    fun selectShortChat(id: Long) {
        if (id == _targetId.value) {
            _error.value = "Short us channel par lagao jahan affiliate link dikhti hai, Earnkaro bot par nahi."
            return
        }
        _error.value = null
        _shortChatId.value = if (_shortChatId.value == id) null else id
        saveRules()
    }

    fun setShortenEnabled(on: Boolean) {
        if (on && _shortChatId.value == null) {
            _error.value = "Pehle apna channel Short pe select karo. Wahi jahan lambi affiliate link aati hai."
            return
        }
        _error.value = null
        val was = _shortenEnabled.value
        _shortenEnabled.value = on
        val editor = prefs.edit().putBoolean(KEY_SHORT_ON, on)
        if (on && !was) {
            shortenSince = (System.currentTimeMillis() / 1000L) - 5L
            editor.putLong(KEY_SHORT_SINCE, shortenSince)
        }
        editor.apply()
        keepRunning()
        addLog(
            when {
                !on -> "amzn.to off"
                AmazonClient.loggedIn() -> "amzn.to on"
                else -> "amzn.to on · Amazon login baaki"
            },
        )
    }

    fun setEnabled(on: Boolean) {
        if (on && _sources.value.isEmpty()) {
            _error.value = "Kam az kam ek source channel ya group choose karo."
            return
        }
        if (on && _targetId.value == null) {
            _error.value = "Target channel choose karo. Us channel me aap admin hone chahiye."
            return
        }
        _error.value = null
        val was = _enabled.value
        _enabled.value = on
        val editor = prefs.edit().putBoolean(KEY_ENABLED, on)
        if (on && !was) {
            enabledSince = (System.currentTimeMillis() / 1000L) - 5L
            editor.putLong(KEY_SINCE, enabledSince)
        }
        editor.apply()
        keepRunning()
        addLog(if (on) "Auto post on. Ab source ke naye posts target par copy honge." else "Auto post off.")
    }

    fun logout() {
        scope.launch {
            _enabled.value = false
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            withContext(Dispatchers.Main) { ForwardService.stop(app) }
            try {
                client?.send(TdApi.LogOut())
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun resetApi() {
        scope.launch {
            _enabled.value = false
            prefs.edit()
                .putBoolean(KEY_ENABLED, false)
                .remove(KEY_API_ID)
                .remove(KEY_API_HASH)
                .apply()
            withContext(Dispatchers.Main) { ForwardService.stop(app) }
            updatesJob?.cancel()
            try {
                client?.close()
            } catch (_: Exception) {
            }
            client = null
            File(app.filesDir, "tdlib").deleteRecursively()
            _chats.value = emptyList()
            _account.value = ""
            _step.value = AuthStep.NeedApi
        }
    }

    private fun connect() {
        updatesJob?.cancel()
        try {
            client?.close()
        } catch (_: Exception) {
        }
        _step.value = AuthStep.Connecting
        val dir = File(app.filesDir, "tdlib").apply { mkdirs() }.absolutePath
        val td = TdClient(
            filesDir = dir,
            verbosityLevel = 1,
            apiId = apiId,
            apiHash = apiHash,
        )
        client = td
        updatesJob = scope.launch {
            val subscribed = CompletableDeferred<Unit>()
            launch {
                td.updates
                    .onSubscription {
                        if (!subscribed.isCompleted) subscribed.complete(Unit)
                    }
                    .collect { update ->
                        when (update) {
                            is TdApi.UpdateAuthorizationState -> handleAuth(update.authorizationState)
                            is TdApi.UpdateNewMessage -> onNewMessage(update.message)
                        }
                    }
            }
            subscribed.await()
            td.init()
        }
    }

    private suspend fun handleAuth(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> _step.value = AuthStep.Phone
            is TdApi.AuthorizationStateWaitCode -> _step.value = AuthStep.Code(codeHint(state.codeInfo))
            is TdApi.AuthorizationStateWaitPassword -> _step.value = AuthStep.Password(state.passwordHint.orEmpty())
            is TdApi.AuthorizationStateWaitEmailAddress -> _step.value = AuthStep.Email
            is TdApi.AuthorizationStateWaitEmailCode -> _step.value = AuthStep.EmailCode
            is TdApi.AuthorizationStateWaitRegistration -> _step.value = AuthStep.Registration
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> _step.value = AuthStep.OtherDevice(state.link)
            is TdApi.AuthorizationStateReady -> {
                reconnects = 0
                onReady()
            }
            is TdApi.AuthorizationStateLoggingOut -> _step.value = AuthStep.Connecting
            is TdApi.AuthorizationStateClosed -> {
                if (reconnects < 2 && _step.value !is AuthStep.NeedApi) {
                    reconnects++
                    scope.launch {
                        delay(500)
                        connect()
                    }
                } else {
                    _error.value = "Telegram session band ho gayi. App band karke dubara kholo."
                }
            }
            else -> Unit
        }
    }

    private suspend fun sendParameters() {
        val td = client ?: return
        try {
            td.send(
                TdApi.SetTdlibParameters(
                    false,
                    File(app.filesDir, "tdlib").absolutePath,
                    File(app.filesDir, "tdlib/files").absolutePath,
                    ByteArray(0),
                    true,
                    true,
                    true,
                    false,
                    apiId,
                    apiHash,
                    "en",
                    Build.MODEL ?: "Android",
                    Build.VERSION.RELEASE ?: "14",
                    "1.0.0",
                )
            )
        } catch (_: Exception) {
        }
    }

    private suspend fun onReady() {
        _step.value = AuthStep.Ready
        val td = client ?: return
        try {
            val me = td.send(TdApi.GetMe())
            val name = listOf(me.firstName, me.lastName).filter { it.isNotBlank() }.joinToString(" ")
            val user = me.usernames?.activeUsernames?.firstOrNull()
            _account.value = if (user != null) "$name (@$user)" else name
        } catch (_: Exception) {
        }
        loadChats()
        keepRunning()
    }

    private suspend fun loadChats() {
        val td = client ?: return
        _loadingChats.value = true
        try {
            val ids = LinkedHashSet<Long>()
            ids.addAll(chatIds(td, TdApi.ChatListMain()).toList())
            try {
                ids.addAll(chatIds(td, TdApi.ChatListArchive()).toList())
            } catch (_: Exception) {
            }
            val items = ArrayList<ChatItem>(ids.size)
            for (id in ids) {
                val chat = try {
                    td.send(TdApi.GetChat(id))
                } catch (_: Exception) {
                    continue
                }
                items.add(
                    ChatItem(
                        id = chat.id,
                        title = chat.title.ifBlank { "Untitled" },
                        kind = kindOf(td, chat),
                        protectedContent = chat.hasProtectedContent,
                    )
                )
            }
            _chats.value = items.sortedBy { it.title.lowercase() }
        } catch (e: Exception) {
            _error.value = e.message ?: "Chat list load nahi hui."
        } finally {
            _loadingChats.value = false
        }
    }

    private suspend fun chatIds(td: TdClient, list: TdApi.ChatList): LongArray {
        while (true) {
            try {
                td.send(TdApi.LoadChats(list, 200))
            } catch (e: TdException) {
                if (e.code == 404) break else throw e
            }
        }
        return td.send(TdApi.GetChats(list, 10_000)).chatIds
    }

    private suspend fun kindOf(td: TdClient, chat: TdApi.Chat): String = when (val type = chat.type) {
        is TdApi.ChatTypeSupergroup -> if (type.isChannel) "Channel" else "Group"
        is TdApi.ChatTypeBasicGroup -> "Group"
        is TdApi.ChatTypePrivate -> {
            val user = try {
                td.send(TdApi.GetUser(type.userId))
            } catch (_: Exception) {
                null
            }
            if (user?.type is TdApi.UserTypeBot) "Bot" else "Chat"
        }
        is TdApi.ChatTypeSecret -> "Secret"
        else -> "Chat"
    }

    private fun onNewMessage(message: TdApi.Message) {
        if (_shortenEnabled.value && message.chatId == _shortChatId.value) {
            scope.launch { shortenMutex.withLock { shortenIncoming(message) } }
            return
        }
        val supported = contentSupported(message.content)
        val forward = ForwardPolicy.shouldForward(
            enabled = _enabled.value,
            chatId = message.chatId,
            sourceIds = _sources.value,
            targetId = _targetId.value,
            messageUnix = message.date.toLong(),
            enabledSinceUnix = enabledSince,
            stillSending = message.sendingState != null,
            scheduled = message.schedulingState != null,
            contentSupported = supported,
            alreadyForwarded = false,
        )
        if (!forward) return
        if (message.mediaAlbumId != 0L) {
            queueAlbum(message)
        } else {
            val key = "${message.chatId}:${message.id}"
            if (!begin(key)) return
            scope.launch {
                val ok = forwardIds(message.chatId, longArrayOf(message.id))
                finish(key, ok)
            }
        }
    }

    private fun queueAlbum(message: TdApi.Message) {
        val key = "${message.chatId}:${message.mediaAlbumId}"
        synchronized(albums) {
            val bucket = albums.getOrPut(key) { AlbumBucket() }
            bucket.ids.add(message.id)
            bucket.job?.cancel()
            bucket.job = scope.launch {
                delay(1200)
                val ids = synchronized(albums) {
                    val current = albums.remove(key) ?: return@launch
                    current.ids.distinct().sorted().toLongArray()
                }
                val fresh = ids.filter { begin("$key:$it") }.toLongArray()
                if (fresh.isEmpty()) return@launch
                val ok = forwardIds(message.chatId, fresh)
                fresh.forEach { finish("$key:$it", ok) }
            }
        }
    }

    private suspend fun forwardIds(fromChatId: Long, ids: LongArray): Boolean {
        val target = _targetId.value ?: return false
        val td = client ?: return false
        if (ids.isEmpty()) return false
        return sendMutex.withLock {
            try {
                td.send(
                    TdApi.ForwardMessages(
                        target,
                        null,
                        fromChatId,
                        ids,
                        null,
                        _copyWithoutHeader.value,
                        false,
                    )
                )
                val from = _chats.value.firstOrNull { it.id == fromChatId }?.title ?: "source"
                val to = _chats.value.firstOrNull { it.id == target }?.title ?: "target"
                val line = "${ids.size} post copy hua: $from → $to"
                addLog(line)
                ForwardService.update(app, line)
                true
            } catch (e: Exception) {
                val line = "Copy fail: ${e.message}"
                addLog(line)
                ForwardService.update(app, line)
                false
            }
        }
    }

    private fun contentSupported(content: TdApi.MessageContent): Boolean = when (content) {
        is TdApi.MessageText,
        is TdApi.MessagePhoto,
        is TdApi.MessageVideo,
        is TdApi.MessageAnimation,
        is TdApi.MessageDocument,
        is TdApi.MessageAudio,
        is TdApi.MessageVoiceNote,
        is TdApi.MessageVideoNote,
        is TdApi.MessageSticker,
        is TdApi.MessagePoll,
        is TdApi.MessageLocation,
        is TdApi.MessageVenue,
        is TdApi.MessageContact,
        is TdApi.MessageDice -> true
        else -> false
    }

    private fun begin(key: String): Boolean {
        synchronized(done) {
            if (key in done || key in inFlight) return false
            inFlight.add(key)
            return true
        }
    }

    private fun finish(key: String, ok: Boolean) {
        synchronized(done) {
            inFlight.remove(key)
            if (ok) {
                done.add(key)
                while (done.size > 2000) {
                    val it = done.iterator()
                    if (!it.hasNext()) break
                    it.next()
                    it.remove()
                }
            }
        }
    }

    private fun <T : TdApi.Object> sendAuth(function: TdApi.Function<T>) {
        val td = client
        if (td == null) {
            _error.value = "Telegram abhi connect nahi hua."
            return
        }
        scope.launch {
            _busy.value = true
            _error.value = null
            try {
                td.send(function)
            } catch (e: Exception) {
                _error.value = e.message ?: "Login fail."
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun shortenIncoming(message: TdApi.Message) {
        if (ownedIds.remove(message.id)) return
        if (message.date.toLong() < shortenSince) return
        if (message.sendingState != null || message.schedulingState != null) return
        val key = "short:${message.chatId}:${message.id}"
        if (!begin(key)) return
        val ok = try {
            applyShortLinks(message)
        } catch (e: Exception) {
            addLog("Short link fail: ${e.message}")
            false
        }
        finish(key, ok)
    }

    private suspend fun applyShortLinks(message: TdApi.Message): Boolean {
        val td = client ?: return false
        val body = bodyOf(message)
        val originalEntities = body?.formatted?.entities?.toList().orEmpty()
        val rewritten = LinkText.rewrite(
            body?.formatted?.text.orEmpty(),
            originalEntities.map { entity ->
                LinkText.Entity(
                    entity.offset,
                    entity.length,
                    (entity.type as? TdApi.TextEntityTypeTextUrl)?.url,
                )
            },
            UrlShortener::shorten,
        )
        val markup = rewriteMarkup(message.replyMarkup)
        if (!rewritten.changed && markup == null) {
            if (rewritten.failed) addLog("amzn.to nahi bana")
            return !rewritten.failed
        }
        val newEntities = originalEntities.mapIndexedNotNull { index, entity ->
            val updated = rewritten.entities.getOrNull(index) ?: return@mapIndexedNotNull null
            if (updated.length <= 0) return@mapIndexedNotNull null
            val type = when (val current = entity.type) {
                is TdApi.TextEntityTypeTextUrl -> TdApi.TextEntityTypeTextUrl(updated.hiddenUrl ?: current.url)
                else -> current
            }
            TdApi.TextEntity(updated.offset, updated.length, type)
        }.toTypedArray()
        val formatted = if (body == null) null else TdApi.FormattedText(rewritten.text, newEntities)
        val replyMarkup = markup ?: message.replyMarkup
        return try {
            editShortened(td, message, body, formatted, replyMarkup)
            addLog(shortLog(rewritten, replyMarkup, replaced = false))
            true
        } catch (_: Exception) {
            replaceUneditable(td, message, body, formatted, replyMarkup, rewritten)
        }
    }

    private suspend fun editShortened(
        td: TdClient,
        message: TdApi.Message,
        body: MessageBody?,
        formatted: TdApi.FormattedText?,
        replyMarkup: TdApi.ReplyMarkup?,
    ) {
        when {
            body?.isPlainText == true && formatted != null -> td.send(
                TdApi.EditMessageText(
                    message.chatId,
                    message.id,
                    replyMarkup,
                    TdApi.InputMessageText(
                        formatted,
                        (message.content as? TdApi.MessageText)?.linkPreviewOptions,
                        false,
                    ),
                )
            )
            body != null && formatted != null -> td.send(
                TdApi.EditMessageCaption(
                    message.chatId,
                    message.id,
                    replyMarkup,
                    formatted,
                    body.captionAbove,
                )
            )
            else -> td.send(TdApi.EditMessageReplyMarkup(message.chatId, message.id, replyMarkup))
        }
    }

    private suspend fun replaceUneditable(
        td: TdClient,
        message: TdApi.Message,
        body: MessageBody?,
        formatted: TdApi.FormattedText?,
        replyMarkup: TdApi.ReplyMarkup?,
        rewritten: LinkText.Rewritten,
    ): Boolean {
        val copied = try {
            td.send(
                TdApi.ForwardMessages(
                    message.chatId,
                    null,
                    message.chatId,
                    longArrayOf(message.id),
                    null,
                    true,
                    false,
                )
            )
        } catch (e: Exception) {
            addLog("Short link fail: ${e.message}")
            return false
        }
        val copy = copied.messages?.firstOrNull { it != null } ?: run {
            addLog("Short link fail: channel par naya post nahi bana.")
            return false
        }
        ownedIds.add(copy.id)
        return try {
            editShortened(td, copy, body, formatted, replyMarkup)
            td.send(TdApi.DeleteMessages(message.chatId, longArrayOf(message.id), true))
            addLog(shortLog(rewritten, replyMarkup, replaced = true))
            true
        } catch (e: Exception) {
            try {
                td.send(TdApi.DeleteMessages(copy.chatId, longArrayOf(copy.id), true))
            } catch (_: Exception) {
            }
            addLog("Short link fail: ${e.message}")
            false
        }
    }

    private fun bodyOf(message: TdApi.Message): MessageBody? = when (val content = message.content) {
        is TdApi.MessageText -> MessageBody(content.text, false, true)
        is TdApi.MessagePhoto -> content.caption?.let { MessageBody(it, content.showCaptionAboveMedia, false) }
        is TdApi.MessageVideo -> content.caption?.let { MessageBody(it, content.showCaptionAboveMedia, false) }
        is TdApi.MessageAnimation -> content.caption?.let { MessageBody(it, content.showCaptionAboveMedia, false) }
        is TdApi.MessageDocument -> content.caption?.let { MessageBody(it, false, false) }
        is TdApi.MessageAudio -> content.caption?.let { MessageBody(it, false, false) }
        else -> null
    }

    private fun rewriteMarkup(markup: TdApi.ReplyMarkup?): TdApi.ReplyMarkup? {
        val keyboard = markup as? TdApi.ReplyMarkupInlineKeyboard ?: return null
        var changed = false
        val rows = keyboard.rows.map { row ->
            row.map { button ->
                val type = button.type
                if (type is TdApi.InlineKeyboardButtonTypeUrl && LinkText.needsShort(type.url)) {
                    val short = UrlShortener.shorten(type.url)
                    if (short.startsWith("http") && short.length < type.url.length) {
                        changed = true
                        TdApi.InlineKeyboardButton(
                            button.text,
                            button.iconCustomEmojiId,
                            button.style,
                            TdApi.InlineKeyboardButtonTypeUrl(short),
                        )
                    } else {
                        button
                    }
                } else {
                    button
                }
            }.toTypedArray()
        }.toTypedArray()
        return if (changed) TdApi.ReplyMarkupInlineKeyboard(rows, keyboard.forceReply) else null
    }

    private fun shortLog(rewritten: LinkText.Rewritten, markup: TdApi.ReplyMarkup?, replaced: Boolean): String {
        val amzn = rewritten.text.contains("https://amzn.to/") ||
            rewritten.entities.any { it.hiddenUrl?.startsWith("https://amzn.to/") == true } ||
            markupHasAmzn(markup)
        return when {
            amzn && replaced -> "amzn.to lag gayi"
            amzn -> "amzn.to lag gayi"
            replaced -> "amazon.in/dp · amzn.to nahi bana"
            else -> "amzn.to nahi bana"
        }
    }

    private fun markupHasAmzn(markup: TdApi.ReplyMarkup?): Boolean {
        val keyboard = markup as? TdApi.ReplyMarkupInlineKeyboard ?: return false
        return keyboard.rows.any { row ->
            row.any { button ->
                val type = button.type
                type is TdApi.InlineKeyboardButtonTypeUrl && type.url.startsWith("https://amzn.to/")
            }
        }
    }

    private fun keepRunning() {
        if (_enabled.value || _shortenEnabled.value) ForwardService.start(app) else ForwardService.stop(app)
    }

    private fun saveRules() {
        prefs.edit()
            .putString(KEY_SOURCES, _sources.value.joinToString(","))
            .putLong(KEY_TARGET, _targetId.value ?: 0L)
            .putLong(KEY_SHORT_CHAT, _shortChatId.value ?: 0L)
            .putBoolean(KEY_COPY, _copyWithoutHeader.value)
            .apply()
    }

    private fun addLog(line: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            .format(java.util.Date())
        _log.value = (listOf("$stamp  $line") + _log.value).take(12)
    }

    private fun phoneSettings() = TdApi.PhoneNumberAuthenticationSettings(
        false,
        false,
        false,
        false,
        false,
        null,
        emptyArray(),
    )

    private fun codeHint(info: TdApi.AuthenticationCodeInfo): String = when (val type = info.type) {
        is TdApi.AuthenticationCodeTypeTelegramMessage -> "Code aapki Telegram app me aa gaya hai (${type.length} digit)."
        is TdApi.AuthenticationCodeTypeSms -> "SMS code dalo (${type.length} digit)."
        is TdApi.AuthenticationCodeTypeCall -> "Call suno aur code dalo (${type.length} digit)."
        is TdApi.AuthenticationCodeTypeMissedCall -> "Missed call ke number ke last ${type.length} digit dalo."
        is TdApi.AuthenticationCodeTypeFragment -> "Fragment se code lo, phir yahan dalo."
        else -> "Telegram ka login code dalo."
    }

    private class AlbumBucket {
        val ids = ArrayList<Long>()
        var job: Job? = null
    }

    private data class MessageBody(
        val formatted: TdApi.FormattedText,
        val captionAbove: Boolean,
        val isPlainText: Boolean,
    )

    companion object {
        private const val PREFS = "tg_forward"
        private const val KEY_API_ID = "api_id"
        private const val KEY_API_HASH = "api_hash"
        private const val KEY_SOURCES = "sources"
        private const val KEY_TARGET = "target"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_COPY = "copy"
        private const val KEY_SINCE = "enabled_since"
        private const val KEY_SHORT_CHAT = "short_chat"
        private const val KEY_SHORT_ON = "short_on"
        private const val KEY_SHORT_SINCE = "short_since"
    }
}
