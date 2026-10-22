package com.tweetme.app.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.tweetme.app.core.AppError
import com.tweetme.app.core.ChatSummary
import com.tweetme.app.core.Draft
import com.tweetme.app.core.ErrorKind
import com.tweetme.app.core.Message
import com.tweetme.app.core.MsgType
import com.tweetme.app.core.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * التنفيذ الحقيقي على Firebase (Auth + Firestore).
 * هيكل البيانات:
 *   users/{uid}                 الاسم واسم المستخدم
 *   usernames/{username}        { uid } لضمان تفرّد الاسم
 *   chats/{uidA_uidB}           members, last, updatedAt, unread{uid}, lastRead{uid}
 *   chats/{id}/messages/{mid}   from, type, text, url, dur, at
 */
class FirebaseBackend : ChatBackend {
    override val isDemo = false

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = ConcurrentHashMap<String, User>()

    @Volatile
    private var busy = false

    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready
    private val _user = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _user

    init {
        auth.addAuthStateListener { fa ->
            val fu = fa.currentUser
            if (fu == null) {
                _user.value = null
                _ready.value = true
            } else if (!busy) {
                scope.launch {
                    _user.value = loadProfile(fu.uid)
                    _ready.value = true
                }
            }
        }
    }

    private suspend fun loadProfile(uid: String): User? {
        val ref = db.collection("users").document(uid)
        val snap = try {
            ref.get().await()
        } catch (e: Exception) {
            try {
                ref.get(Source.CACHE).await()
            } catch (e2: Exception) {
                null
            }
        }
        if (snap == null) {
            // بلا إنترنت وبلا نسخة محفوظة: نسمح بالدخول بمعلومات أساسية
            return User(uid, auth.currentUser?.email?.substringBefore('@').orEmpty(), "")
        }
        if (!snap.exists()) {
            auth.signOut()
            return null
        }
        return User(uid, snap.getString("name").orEmpty(), snap.getString("username").orEmpty()).also { cache[uid] = it }
    }

    // ---------- الحساب ----------

    override suspend fun signUp(name: String, username: String, email: String, password: String) = wrap {
        busy = true
        try {
            val res = auth.createUserWithEmailAndPassword(email, password).await()
            val fu = res.user ?: throw AppError(ErrorKind.UNKNOWN)
            val uid = fu.uid
            try {
                val ok = db.runTransaction(Transaction.Function<Boolean> { tx ->
                    val uref = db.collection("usernames").document(username)
                    if (tx.get(uref).exists()) {
                        false
                    } else {
                        tx.set(uref, mapOf("uid" to uid))
                        tx.set(
                            db.collection("users").document(uid),
                            mapOf("name" to name, "username" to username, "createdAt" to FieldValue.serverTimestamp())
                        )
                        true
                    }
                }).await()
                if (!ok) throw AppError(ErrorKind.USERNAME_TAKEN)
            } catch (e: Exception) {
                runCatching { fu.delete().await() }
                throw e
            }
            val me = User(uid, name, username)
            cache[uid] = me
            _user.value = me
            _ready.value = true
            runCatching { fu.sendEmailVerification() }
            Unit
        } finally {
            busy = false
        }
    }

    override suspend fun signIn(email: String, password: String) = wrap {
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    override suspend fun resetPassword(email: String) = wrap {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    override fun signOut() {
        auth.signOut()
    }

    // ---------- الأعضاء ----------

    override suspend fun findUser(username: String): User? = wrap {
        val s = db.collection("usernames").document(username).get().await()
        val uid = s.getString("uid")
        if (!s.exists() || uid == null) null else getUser(uid)
    }

    override suspend fun getUser(uid: String): User = cache[uid] ?: wrap {
        val s = db.collection("users").document(uid).get().await()
        val u = if (s.exists()) {
            User(uid, s.getString("name").orEmpty(), s.getString("username").orEmpty())
        } else {
            User(uid, "مستخدم", "")
        }
        cache[uid] = u
        u
    }

    override suspend fun updateName(me: String, name: String) = wrap {
        db.collection("users").document(me).update("name", name).await()
        val old = _user.value
        if (old != null && old.uid == me) {
            val updated = old.copy(name = name)
            cache[me] = updated
            _user.value = updated
        }
        Unit
    }

    // ---------- المحادثات ----------

    override fun chats(me: String): Flow<List<ChatSummary>> = callbackFlow {
        val reg = db.collection("chats").whereArrayContains("members", me)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    trySend(emptyList())
                    close()
                    return@addSnapshotListener
                }
                trySend(snap.documents.mapNotNull { it.toSummary(me) })
            }
        awaitClose { reg.remove() }
    }

    override fun messages(chatId: String): Flow<List<Message>> = callbackFlow {
        val reg = db.collection("chats").document(chatId).collection("messages")
            .orderBy("at", Query.Direction.DESCENDING).limit(120)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, e ->
                if (e != null || snap == null) {
                    trySend(emptyList())
                    close()
                    return@addSnapshotListener
                }
                trySend(snap.documents.mapNotNull { it.toMessage() }.reversed())
            }
        awaitClose { reg.remove() }
    }

    override suspend fun send(chatId: String, me: String, peerUid: String, draft: Draft) = wrap {
        val chatRef = db.collection("chats").document(chatId)
        val batch = db.batch()
        batch.set(
            chatRef,
            mapOf(
                "members" to listOf(me, peerUid).sorted(),
                "last" to mapOf("type" to draft.type.wire, "text" to draft.text, "from" to me),
                "updatedAt" to FieldValue.serverTimestamp(),
                "unread" to mapOf(peerUid to FieldValue.increment(1)),
                "lastRead" to mapOf(me to FieldValue.serverTimestamp())
            ),
            SetOptions.merge()
        )
        batch.set(
            chatRef.collection("messages").document(),
            mapOf(
                "from" to me,
                "type" to draft.type.wire,
                "text" to draft.text,
                "url" to draft.url,
                "dur" to draft.durSec,
                "at" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Unit
    }

    override suspend fun markRead(chatId: String, me: String) {
        // قد لا توجد المحادثة بعد، فنتجاهل الفشل
        runCatching {
            db.collection("chats").document(chatId)
                .update(mapOf<String, Any>("unread.$me" to 0, "lastRead.$me" to FieldValue.serverTimestamp()))
                .await()
        }
    }

    // ---------- أدوات داخلية ----------

    private fun Any?.millis(): Long = (this as? Timestamp)?.toDate()?.time ?: 0L

    private fun DocumentSnapshot.toSummary(me: String): ChatSummary? {
        val data = getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
        val members = (data["members"] as? List<*>)?.filterIsInstance<String>() ?: return null
        val peer = members.firstOrNull { it != me } ?: return null
        val last = data["last"] as? Map<*, *>
        val unread = ((data["unread"] as? Map<*, *>)?.get(me) as? Number)?.toInt() ?: 0
        val peerRead = (data["lastRead"] as? Map<*, *>)?.get(peer).millis()
        return ChatSummary(
            id = id,
            peerUid = peer,
            lastType = MsgType.from(last?.get("type") as? String),
            lastText = last?.get("text") as? String ?: "",
            lastFrom = last?.get("from") as? String ?: "",
            updatedAt = data["updatedAt"].millis().takeIf { it > 0 } ?: System.currentTimeMillis(),
            unread = unread,
            peerReadAt = peerRead
        )
    }

    private fun DocumentSnapshot.toMessage(): Message? {
        val m = getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
        return Message(
            id = id,
            from = m["from"] as? String ?: "",
            type = MsgType.from(m["type"] as? String),
            text = m["text"] as? String ?: "",
            url = m["url"] as? String ?: "",
            durSec = (m["dur"] as? Number)?.toInt() ?: 0,
            at = m["at"].millis().takeIf { it > 0 } ?: System.currentTimeMillis(),
            pending = metadata.hasPendingWrites()
        )
    }

    private inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: AppError) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.toApp()
    }

    private fun Throwable.toApp(): AppError = AppError(
        when (this) {
            is FirebaseAuthWeakPasswordException -> ErrorKind.WEAK_PASSWORD
            is FirebaseAuthUserCollisionException -> ErrorKind.EMAIL_IN_USE
            is FirebaseAuthInvalidCredentialsException ->
                if (errorCode == "ERROR_INVALID_EMAIL") ErrorKind.INVALID_EMAIL else ErrorKind.BAD_CREDENTIALS
            is FirebaseAuthInvalidUserException -> ErrorKind.BAD_CREDENTIALS
            is FirebaseNetworkException -> ErrorKind.NETWORK
            is FirebaseTooManyRequestsException -> ErrorKind.TOO_MANY
            is FirebaseFirestoreException -> when (code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> ErrorKind.PERMISSION
                FirebaseFirestoreException.Code.UNAVAILABLE -> ErrorKind.NETWORK
                else -> ErrorKind.UNKNOWN
            }
            else -> ErrorKind.UNKNOWN
        },
        this
    )
}
