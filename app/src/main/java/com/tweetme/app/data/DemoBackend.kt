package com.tweetme.app.data

import com.tweetme.app.core.ChatSummary
import com.tweetme.app.core.Draft
import com.tweetme.app.core.Message
import com.tweetme.app.core.MsgType
import com.tweetme.app.core.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * خدمة تجريبية داخل الذاكرة تعمل بلا إنترنت (قبل إضافة google-services.json).
 * المحادثة الوحيدة مع «Tweet بوت»، وتُمسح عند إغلاق التطبيق.
 */
class DemoBackend : ChatBackend {
    override val isDemo = true

    private val bot = User("bot", "Tweet بوت", "tweetbot")
    private val chatId = "bot_me"

    override val ready: StateFlow<Boolean> = MutableStateFlow(true)
    private val _user = MutableStateFlow<User?>(User("me", "ضيف", "guest"))
    override val currentUser: StateFlow<User?> = _user

    private val msgs = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private val unread = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        post(
            "bot",
            Draft(
                MsgType.TEXT,
                "أهلاً بك في Tweet me! 👋\nهذا وضع تجريبي على هاتفك فقط. بعد إضافة ملف google-services.json تتحول المراسلة إلى حقيقية بين الأعضاء."
            )
        )
    }

    private fun post(from: String, d: Draft) {
        val m = Message(UUID.randomUUID().toString(), from, d.type, d.text, d.url, d.durSec, System.currentTimeMillis())
        msgs.update { cur -> cur + (chatId to (cur[chatId].orEmpty() + m)) }
        if (from != "me") unread.update { cur -> cur + (chatId to ((cur[chatId] ?: 0) + 1)) }
    }

    override suspend fun signUp(name: String, username: String, email: String, password: String) {
        _user.value = User("me", name, username)
    }

    override suspend fun signIn(email: String, password: String) {
        _user.value = User("me", _user.value?.name ?: "ضيف", _user.value?.username ?: "guest")
    }

    override suspend fun resetPassword(email: String) {}

    override fun signOut() {
        _user.value = null
    }

    override suspend fun findUser(username: String): User? = if (username == bot.username) bot else null

    override suspend fun getUser(uid: String): User = if (uid == bot.uid) bot else _user.value ?: User("me", "ضيف", "guest")

    override suspend fun updateName(me: String, name: String) {
        _user.update { it?.copy(name = name) }
    }

    override fun chats(me: String): Flow<List<ChatSummary>> = combine(msgs, unread) { m, u ->
        m.map { (id, list) ->
            val last = list.last()
            ChatSummary(id, bot.uid, last.type, last.text, last.from, last.at, u[id] ?: 0, Long.MAX_VALUE)
        }
    }

    override fun messages(chatId: String): Flow<List<Message>> = msgs.map { it[chatId].orEmpty() }

    override suspend fun send(chatId: String, me: String, peerUid: String, draft: Draft) {
        post("me", draft)
        scope.launch {
            delay(800)
            val reply = when (draft.type) {
                MsgType.TEXT -> "وصلتني رسالتك: «${draft.text.take(80)}»"
                MsgType.IMAGE -> "وصلتني الصورة 📷"
                MsgType.VOICE -> "وصلتني رسالتك الصوتية 🎤"
            }
            post("bot", Draft(MsgType.TEXT, reply))
        }
    }

    override suspend fun markRead(chatId: String, me: String) {
        unread.update { it + (chatId to 0) }
    }
}
