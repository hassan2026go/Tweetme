package com.tweetme.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tweetme.app.core.Draft
import com.tweetme.app.core.ErrorKind
import com.tweetme.app.core.Message
import com.tweetme.app.core.MsgType
import com.tweetme.app.core.User
import com.tweetme.app.core.errorKind
import com.tweetme.app.data.ChatBackend
import com.tweetme.app.media.MediaUtils
import com.tweetme.app.media.Recorded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** منطق شاشة المحادثة: الرسائل والإرسال وضغط الوسائط، بلا أي كود واجهة. */
class ChatViewModel(
    private val app: Context,
    private val backend: ChatBackend,
    private val me: String,
    private val peerUid: String
) : ViewModel() {

    private val chatId = listOf(me, peerUid).sorted().joinToString("_")

    val messages: StateFlow<List<Message>> =
        backend.messages(chatId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** وقت آخر قراءة للطرف الآخر، لعلامات القراءة المزدوجة. */
    val peerReadAt: StateFlow<Long> = backend.chats(me)
        .map { list -> list.firstOrNull { it.id == chatId }?.peerReadAt ?: 0L }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val _peer = MutableStateFlow<User?>(null)
    val peer: StateFlow<User?> = _peer

    private val _errors = MutableSharedFlow<ErrorKind>(extraBufferCapacity = 4)
    val errors: SharedFlow<ErrorKind> = _errors.asSharedFlow()

    init {
        viewModelScope.launch { _peer.value = runCatching { backend.getUser(peerUid) }.getOrNull() }
        viewModelScope.launch {
            var marked = 0L
            messages.collect { list ->
                val last = list.lastOrNull()
                if (last != null && last.from != me && last.at > marked) {
                    marked = last.at
                    backend.markRead(chatId, me)
                }
            }
        }
    }

    fun sendText(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) post(Draft(MsgType.TEXT, t))
    }

    fun sendImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.Default) {
            val bytes = runCatching { MediaUtils.compressImage(app, uri, 900, 480_000) }.getOrNull()
            if (bytes == null) {
                _errors.tryEmit(ErrorKind.TOO_BIG)
            } else {
                post(Draft(MsgType.IMAGE, url = MediaUtils.toDataUri("image/jpeg", bytes)))
            }
        }
    }

    fun sendVoice(r: Recorded) {
        if (r.bytes.size > 600_000) {
            _errors.tryEmit(ErrorKind.TOO_BIG)
            return
        }
        post(Draft(MsgType.VOICE, url = MediaUtils.toDataUri("audio/mp4", r.bytes), durSec = r.durSec))
    }

    private fun post(d: Draft) {
        viewModelScope.launch {
            try {
                backend.send(chatId, me, peerUid, d)
            } catch (e: Exception) {
                _errors.tryEmit(e.errorKind())
            }
        }
    }
}
