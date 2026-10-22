package com.tweetme.app.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tweetme.app.R
import com.tweetme.app.core.ChatSummary
import com.tweetme.app.core.MsgType
import com.tweetme.app.core.User
import com.tweetme.app.core.errorKind
import com.tweetme.app.core.errorRes
import com.tweetme.app.data.ChatBackend
import com.tweetme.app.ui.common.Avatar
import com.tweetme.app.ui.common.ScreenTopBar
import com.tweetme.app.ui.common.listTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatRow(val summary: ChatSummary, val peer: User?)

class ChatListViewModel(private val backend: ChatBackend, me: String) : ViewModel() {
    private val peers = MutableStateFlow<Map<String, User>>(emptyMap())
    private val chats: StateFlow<List<ChatSummary>> =
        backend.chats(me).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rows: StateFlow<List<ChatRow>> = combine(chats, peers) { list, p ->
        list.sortedByDescending { it.updatedAt }.map { ChatRow(it, p[it.peerUid]) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            chats.collect { list ->
                list.map { it.peerUid }.distinct().filter { it !in peers.value }.forEach { uid ->
                    launch {
                        val u = runCatching { backend.getUser(uid) }.getOrNull()
                        if (u != null) peers.update { m -> m + (uid to u) }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatListScreen(vm: ChatListViewModel, backend: ChatBackend, me: User, onOpenChat: (String) -> Unit) {
    val rows by vm.rows.collectAsState()
    var showNew by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            buildAnnotatedString {
                append("Tweet ")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("me") }
            }
        ) {
            if (backend.isDemo) {
                Text(stringResource(R.string.demo_badge), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (rows.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.no_chats_title), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.no_chats_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.summary.id }) { row ->
                        ChatRowItem(row, me.uid) { onOpenChat(row.summary.peerUid) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            FloatingActionButton(
                onClick = { showNew = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_chat))
            }
        }
    }

    if (showNew) {
        NewChatDialog(
            backend = backend,
            me = me.uid,
            onDismiss = { showNew = false },
            onPick = { uid -> showNew = false; onOpenChat(uid) }
        )
    }
}

@Composable
private fun ChatRowItem(row: ChatRow, meUid: String, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val s = row.summary
    val prefix = if (s.lastFrom == meUid || s.lastFrom == "me") stringResource(R.string.you_prefix) else ""
    val preview = prefix + when (s.lastType) {
        MsgType.IMAGE -> stringResource(R.string.msg_image)
        MsgType.VOICE -> stringResource(R.string.msg_voice)
        MsgType.TEXT -> s.lastText
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Avatar(row.peer)
        Column(Modifier.weight(1f)) {
            Text(row.peer?.name ?: "…", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                preview, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(listTime(ctx, s.updatedAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            if (s.unread > 0) {
                Box(
                    Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(s.unread.toString(), color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NewChatDialog(backend: ChatBackend, me: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<User?>(null) }
    var msg by remember { mutableStateOf<Int?>(null) }

    fun search() {
        val name = q.trim().lowercase().removePrefix("@")
        if (name.isEmpty()) return
        loading = true
        found = null
        msg = null
        scope.launch {
            try {
                val u = backend.findUser(name)
                when {
                    u == null -> msg = R.string.user_not_found
                    u.uid == me -> msg = R.string.that_is_you
                    else -> found = u
                }
            } catch (e: Exception) {
                msg = errorRes(e.errorKind())
            } finally {
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_chat)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    label = { Text(stringResource(R.string.friend_username)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                )
                if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                msg?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                found?.let { u ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(u.uid) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Avatar(u)
                        Column {
                            Text(u.name, fontWeight = FontWeight.SemiBold)
                            Text("@${u.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { search() }) { Text(stringResource(R.string.search)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}
