package com.tweetme.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.tweetme.app.data.AppPrefs
import com.tweetme.app.media.MediaUtils
import com.tweetme.app.ui.theme.BG_IMAGE_ID
import com.tweetme.app.ui.theme.chatBackgrounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** خلفية المحادثة حسب اختيار المستخدم: لون أو تدرج أو صورة من المعرض. */
@Composable
fun ChatBackground(prefs: AppPrefs, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val ctx = LocalContext.current
    val image by produceState<ImageBitmap?>(null, prefs.bgId, prefs.bgStamp) {
        value = if (prefs.bgId == BG_IMAGE_ID) {
            withContext(Dispatchers.IO) { MediaUtils.loadChatBackground(ctx)?.asImageBitmap() }
        } else {
            null
        }
    }
    val brush = chatBackgrounds.firstOrNull { it.id == prefs.bgId }?.brush
    val base = if (brush != null) {
        modifier.background(brush)
    } else {
        modifier.background(MaterialTheme.colorScheme.background)
    }
    Box(base) {
        val img = image
        if (img != null) {
            Image(bitmap = img, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        content()
    }
}
