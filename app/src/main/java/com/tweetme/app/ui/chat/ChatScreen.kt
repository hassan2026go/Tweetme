package com.tweetme.app.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.tweetme.app.R
import com.tweetme.app.core.Message
import com.tweetme.app.core.MsgType
import com.tweetme.app.core.User
import com.tweetme.app.core.errorRes
import com.tweetme.app.data.AppPrefs
import com.tweetme.app.media.MediaUtils
import com.tweetme.app.media.VoicePlayer
import com.tweetme.app.media.VoiceRecorder
import com.tweetme.app.ui.common.Avatar
import com.tweetme.app.ui.common.dayLabel
import com.tweetme.app.ui.common.formatClock
import com.tweetme.app.ui.common.formatDuration
import com.tweetme.app.ui.common.sameDay
import com.tweetme.app.ui.theme.onColorFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface ChatItem {
    val key: String

    data class Day(val label: String, val ms: Long) : ChatItem {
        override val key get() = "day_$ms"
    }

    data class Msg(val m: Message) : ChatItem {
        override val key get() = m.id
    }
}

private fun buildItems(ctx: Context, list: List<Message>): List<ChatItem> {
    val out = ArrayList<ChatItem>(list.size + 4)
    var prev: Message? = null
    for (m in list) {
        if (prev == null || !sameDay(prev.at, m.at)) out.add(ChatItem.Day(dayLabel(ctx, m.at), m.at))
        out.add(ChatItem.Msg(m))
        prev = m
    }
    return out
}

@Composable
fun ChatScreen(vm: ChatViewModel, me: User, player: VoicePlayer, prefs: AppPrefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val messages by vm.messages.collectAsState()
    val peer by vm.peer.collectAsState()
    val peerRead by vm.peerReadAt.collectAsState()
    val ps by player.state.collectAsState()

    var text by rememberSaveable { mutableStateOf("") }
    var viewing by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recSec by remember { mutableIntStateOf(0) }
    val recorder = remember { VoiceRecorder(ctx) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        vm.errors.collect { Toast.makeText(ctx, errorRes(it), Toast.LENGTH_SHORT).show() }
    }
    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            player.stop()
        }
    }

    val rows = remember(messages) { buildItems(ctx, messages) }
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.scrollToItem(rows.lastIndex)
    }

    fun beginRecording() {
        if (recorder.start()) {
            recording = true
            recSec = 0
        } else {
            Toast.makeText(ctx, R.string.err_record, Toast.LENGTH_SHORT).show()
        }
    }

    fun finishRecording(send: Boolean) {
        recording = false
        val r = recorder.stop()
        if (send && r != null) vm.sendVoice(r)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) beginRecording() else Toast.makeText(ctx, R.string.mic_permission, Toast.LENGTH_SHORT).show()
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.sendImage(uri)
    }

    LaunchedEffect(recording) {
        while (recording) {
            delay(250)
            recSec = recorder.elapsedSec()
            if (recSec >= VoiceRecorder.MAX_SEC) finishRecording(true)
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()
    ) {
        ChatTopBar(peer, onBack)

        ChatBackground(prefs, Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(rows, key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.Day -> DayChip(item.label)
                        is ChatItem.Msg -> {
                            val m = item.m
                            val mine = m.from == me.uid
                            Bubble(
                                m = m,
                                mine = mine,
                                read = !m.pending && m.at <= peerRead,
                                prefs = prefs,
                                playing = ps.id == m.id && ps.playing,
                                progress = if (ps.id == m.id) ps.progress else 0f,
                                onPlay = { player.toggle(m.id, m.url) },
                                onImage = { viewing = m.url }
                            )
                        }
                    }
                }
            }
        }

        Composer(
            text = text,
            onText = { text = it },
            recording = recording,
            recSec = recSec,
            onPickImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onSend = {
                vm.sendText(text)
                text = ""
            },
            onMic = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    beginRecording()
                } else {
                    permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onRecCancel = { finishRecording(false) },
            onRecSend = { finishRecording(true) }
        )
    }

    viewing?.let { url -> ImageViewer(url) { viewing = null } }
}

@Composable
private fun ChatTopBar(peer: User?, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Avatar(peer, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(peer?.name ?: "…", fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (!peer?.username.isNullOrEmpty()) {
                    Text(
                        "@${peer?.username}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun Bubble(
    m: Message,
    mine: Boolean,
    read: Boolean,
    prefs: AppPrefs,
    playing: Boolean,
    progress: Float,
    onPlay: () -> Unit,
    onImage: () -> Unit
) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val bg = if (mine) Color(if (prefs.bubble != 0L) prefs.bubble else prefs.accent) else scheme.surfaceVariant
    val fg = if (mine) onColorFor(bg) else scheme.onSurface
    val shape = RoundedCornerShape(
        topStart = 14.dp, topEnd = 14.dp,
        bottomStart = if (mine) 14.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 14.dp
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(color = bg, contentColor = fg, shape = shape, modifier = Modifier.widthIn(max = 290.dp)) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                when (m.type) {
                    MsgType.TEXT -> Text(m.text, fontSize = 15.sp)
                    MsgType.IMAGE -> ChatImage(m.url, onImage)
                    MsgType.VOICE -> VoiceBubble(m.durSec, playing, progress, fg, bg, onPlay)
                }
                Row(
                    Modifier.align(Alignment.End).padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(formatClock(ctx, m.at), fontSize = 11.sp, color = fg.copy(alpha = 0.75f))
                    if (mine) {
                        val icon = when {
                            m.pending -> Icons.Outlined.Schedule
                            read -> Icons.Filled.DoneAll
                            else -> Icons.Filled.Done
                        }
                        Icon(
                            icon, contentDescription = null, modifier = Modifier.size(14.dp),
                            tint = if (read) Color(0xFF6FE3FF) else fg.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatImage(url: String, onClick: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.Default) { MediaUtils.bitmapFromDataUri(url)?.asImageBitmap() }
    }
    val b = bmp
    if (b == null) {
        Box(Modifier.size(width = 200.dp, height = 140.dp))
    } else {
        Image(
            bitmap = b, contentDescription = stringResource(R.string.msg_image),
            contentScale = ContentScale.Fit,
            modifier = Modifier.heightIn(max = 280.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
        )
    }
}

@Composable
private fun VoiceBubble(durSec: Int, playing: Boolean, progress: Float, fg: Color, bg: Color, onPlay: () -> Unit) {
    Row(Modifier.widthIn(min = 190.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(
            onClick = onPlay,
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = fg.copy(alpha = 0.2f), contentColor = fg)
        ) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.play))
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f),
            color = fg,
            trackColor = fg.copy(alpha = 0.25f)
        )
        Text(formatDuration(durSec), fontSize = 12.sp, color = fg.copy(alpha = 0.85f))
    }
}

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    recording: Boolean,
    recSec: Int,
    onPickImage: () -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onRecCancel: () -> Unit,
    onRecSend: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val sendColors = IconButtonDefaults.iconButtonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (recording) {
                IconButton(onClick = onRecCancel) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                    Text(formatDuration(recSec), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onRecSend, colors = sendColors) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                }
            } else {
                IconButton(onClick = onPickImage) {
                    Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.attach_image))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 4000) onText(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.type_message)) },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp)
                )
                if (text.isBlank()) {
                    IconButton(onClick = onMic, colors = sendColors) {
                        Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.record_voice))
                    }
                } else {
                    IconButton(onClick = onSend, colors = sendColors) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageViewer(url: String, onDismiss: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.Default) { MediaUtils.bitmapFromDataUri(url)?.asImageBitmap() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            val b = bmp
            if (b != null) Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}
