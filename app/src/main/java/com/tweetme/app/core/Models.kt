package com.tweetme.app.core

/** عضو في التطبيق. */
data class User(val uid: String, val name: String, val username: String)

/** نوع الرسالة. القيمة النصية wire هي ما يُخزَّن في Firestore. */
enum class MsgType(val wire: String) {
    TEXT("text"), IMAGE("image"), VOICE("voice");

    companion object {
        fun from(s: String?): MsgType = entries.firstOrNull { it.wire == s } ?: TEXT
    }
}

/** رسالة داخل محادثة. url تحمل الصورة أو الصوت (data URI) عند الحاجة. */
data class Message(
    val id: String,
    val from: String,
    val type: MsgType,
    val text: String,
    val url: String,
    val durSec: Int,
    val at: Long,
    val pending: Boolean = false
)

/** ملخص محادثة لقائمة المحادثات. */
data class ChatSummary(
    val id: String,
    val peerUid: String,
    val lastType: MsgType,
    val lastText: String,
    val lastFrom: String,
    val updatedAt: Long,
    val unread: Int,
    val peerReadAt: Long
)

/** مسودة رسالة قبل الإرسال. */
data class Draft(
    val type: MsgType,
    val text: String = "",
    val url: String = "",
    val durSec: Int = 0
)
