package com.tweetme.app.data

import com.tweetme.app.core.ChatSummary
import com.tweetme.app.core.Draft
import com.tweetme.app.core.Message
import com.tweetme.app.core.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * العقد الوحيد بين الواجهة والخدمة الخلفية.
 * لإضافة خدمة جديدة (مجموعات، حالات، مكالمات...) أضف دوالها هنا ثم نفّذها في
 * FirebaseBackend وDemoBackend، ولا تلمس الواجهة قبل ذلك.
 * كل الأخطاء تُرمى كـ AppError.
 */
interface ChatBackend {
    val isDemo: Boolean

    /** true بعد معرفة حالة الدخول (مسجّل أو لا). */
    val ready: StateFlow<Boolean>

    /** العضو الحالي، أو null إذا لم يسجّل الدخول. */
    val currentUser: StateFlow<User?>

    suspend fun signUp(name: String, username: String, email: String, password: String)
    suspend fun signIn(email: String, password: String)
    suspend fun resetPassword(email: String)
    fun signOut()

    suspend fun findUser(username: String): User?
    suspend fun getUser(uid: String): User
    suspend fun updateName(me: String, name: String)

    fun chats(me: String): Flow<List<ChatSummary>>
    fun messages(chatId: String): Flow<List<Message>>
    suspend fun send(chatId: String, me: String, peerUid: String, draft: Draft)
    suspend fun markRead(chatId: String, me: String)
}
