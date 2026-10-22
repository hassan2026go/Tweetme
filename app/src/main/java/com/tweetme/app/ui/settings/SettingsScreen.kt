package com.tweetme.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tweetme.app.R
import com.tweetme.app.core.User
import com.tweetme.app.core.errorKind
import com.tweetme.app.core.errorRes
import com.tweetme.app.data.AppPrefs
import com.tweetme.app.data.ChatBackend
import com.tweetme.app.data.PrefsStore
import com.tweetme.app.data.ThemeMode
import com.tweetme.app.media.MediaUtils
import com.tweetme.app.ui.chat.ChatBackground
import com.tweetme.app.ui.common.Avatar
import com.tweetme.app.ui.common.ScreenTopBar
import com.tweetme.app.ui.theme.BG_IMAGE_ID
import com.tweetme.app.ui.theme.chatBackgrounds
import com.tweetme.app.ui.theme.onColorFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ألوان التطبيق الجاهزة؛ أضف لوناً بإضافة رقمه هنا. */
private val ACCENTS = listOf(0xFF2F7BFFL, 0xFF0A84FFL, 0xFF1E5BD8L, 0xFF00B4D8L, 0xFF3B5BDBL, 0xFF7C4DFFL, 0xFF00C389L, 0xFFFF6B6BL)
private val BUBBLES = listOf(0xFF1E5BD8L, 0xFF00A884L, 0xFF7C4DFFL, 0xFFD6336CL, 0xFF111827L, 0xFFF59F00L)
private val HEX_RE = Regex("^#?[0-9a-fA-F]{6}$")

@Composable
fun SettingsScreen(me: User, prefs: AppPrefs, store: PrefsStore, backend: ChatBackend) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(me.name) { mutableStateOf(me.name) }
    var hex by remember { mutableStateOf("") }
    var fontScale by remember { mutableFloatStateOf(prefs.fontScale) }
    var confirmLogout by remember { mutableStateOf(false) }

    fun toast(res: Int) = Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show()

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { MediaUtils.saveChatBackground(ctx, uri) }
                if (ok) store.setBackground(BG_IMAGE_ID) else toast(R.string.err_bg)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(AnnotatedString(stringResource(R.string.tab_settings)))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            SectionTitle(R.string.sec_account)
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(me)
                    Column {
                        Text(me.name, fontWeight = FontWeight.SemiBold)
                        Text("@${me.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 40) name = it },
                    label = { Text(stringResource(R.string.f_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        if (name.trim().length < 2) {
                            toast(R.string.err_name)
                        } else {
                            scope.launch {
                                try {
                                    backend.updateName(me.uid, name.trim())
                                    toast(R.string.saved)
                                } catch (e: Exception) {
                                    toast(errorRes(e.errorKind()))
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.save_name)) }
            }

            SectionTitle(R.string.sec_appearance)
            SectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        ThemeMode.DARK to R.string.mode_dark,
                        ThemeMode.LIGHT to R.string.mode_light,
                        ThemeMode.AUTO to R.string.mode_auto
                    ).forEach { (mode, label) ->
                        Pill(stringResource(label), prefs.mode == mode, Modifier.weight(1f)) {
                            scope.launch { store.setMode(mode) }
                        }
                    }
                }
                Text(stringResource(R.string.app_color))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ACCENTS.forEach { c ->
                        Swatch(c, prefs.accent == c) { scope.launch { store.setAccent(c) } }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hex, onValueChange = { hex = it.take(7) },
                        label = { Text(stringResource(R.string.custom_color)) },
                        placeholder = { Text("#2F7BFF") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = {
                        if (HEX_RE.matches(hex)) {
                            val v = hex.removePrefix("#").toLong(16) or 0xFF000000L
                            scope.launch { store.setAccent(v) }
                        } else {
                            toast(R.string.err_hex)
                        }
                    }) { Text(stringResource(R.string.apply)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.font_size), Modifier.weight(1f))
                    Text(String.format(java.util.Locale.US, "%.0f%%", fontScale * 100), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(
                    value = fontScale,
                    onValueChange = { fontScale = it },
                    onValueChangeFinished = { scope.launch { store.setFontScale(fontScale) } },
                    valueRange = 0.85f..1.3f
                )
            }

            SectionTitle(R.string.sec_chat)
            SectionCard {
                ChatBackground(prefs, Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(12.dp))) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                        PreviewBubble(stringResource(R.string.preview_in), mine = false, prefs = prefs)
                        PreviewBubble(stringResource(R.string.preview_out), mine = true, prefs = prefs)
                    }
                }
                Text(stringResource(R.string.chat_background))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chatBackgrounds.forEach { bg ->
                        val selected = prefs.bgId == bg.id
                        val base = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                            .let { m -> if (bg.brush != null) m.background(bg.brush) else m.background(MaterialTheme.colorScheme.surfaceVariant) }
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable { scope.launch { store.setBackground(bg.id) } }
                        Box(base, contentAlignment = Alignment.Center) {
                            if (bg.brush == null) Text(stringResource(R.string.bg_default), fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                if (prefs.bgId == BG_IMAGE_ID) 3.dp else 1.dp,
                                if (prefs.bgId == BG_IMAGE_ID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.bg_from_gallery), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.bubble_color))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Swatch(prefs.accent, prefs.bubble == 0L) { scope.launch { store.setBubble(0L) } }
                    BUBBLES.forEach { c ->
                        Swatch(c, prefs.bubble == c) { scope.launch { store.setBubble(c) } }
                    }
                }
            }

            SectionTitle(R.string.sec_app)
            SectionCard {
                if (backend.isDemo) {
                    Text(stringResource(R.string.demo_note), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Button(
                    onClick = { confirmLogout = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.logout)) }
                Text(
                    stringResource(R.string.version_label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; backend.signOut() }) { Text(stringResource(R.string.logout)) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(
        stringResource(res),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Swatch(color: Long, selected: Boolean, onClick: () -> Unit) {
    val c = Color(color)
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(c)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun PreviewBubble(text: String, mine: Boolean, prefs: AppPrefs) {
    val bg = if (mine) Color(if (prefs.bubble != 0L) prefs.bubble else prefs.accent) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (mine) onColorFor(bg) else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(12.dp)) {
            Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 13.sp)
        }
    }
}
