package com.tweetme.app.ui.common

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tweetme.app.R
import com.tweetme.app.core.User
import java.util.Calendar
import java.util.Locale
import kotlin.math.absoluteValue

@Composable
fun Avatar(user: User?, size: Dp = 46.dp) {
    val hue = ((user?.uid ?: "?").hashCode().absoluteValue % 360).toFloat()
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(Color.hsv(hue, 0.55f, 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = user?.name?.trim()?.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp
        )
    }
}

@Composable
fun ScreenTopBar(title: AnnotatedString, actions: @Composable RowScope.() -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
    }
}

@Composable
fun SoonScreen(titleRes: Int) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(AnnotatedString(stringResource(titleRes)))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.soon), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.soon_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}

// ---------- أدوات الوقت (أرقام لاتينية دائماً) ----------

fun formatClock(ctx: Context, ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val h = c.get(Calendar.HOUR_OF_DAY)
    val h12 = if (h % 12 == 0) 12 else h % 12
    val suffix = ctx.getString(if (h < 12) R.string.am else R.string.pm)
    return String.format(Locale.US, "%d:%02d %s", h12, c.get(Calendar.MINUTE), suffix)
}

private fun dayKey(ms: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

fun sameDay(a: Long, b: Long): Boolean = dayKey(a) == dayKey(b)

fun dayLabel(ctx: Context, ms: Long): String {
    val now = System.currentTimeMillis()
    if (sameDay(ms, now)) return ctx.getString(R.string.today)
    if (sameDay(ms, now - 24L * 60 * 60 * 1000)) return ctx.getString(R.string.yesterday)
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return String.format(Locale.US, "%d/%d/%d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
}

/** وقت مختصر لقائمة المحادثات: الساعة لليوم، وإلا اسم اليوم أو التاريخ. */
fun listTime(ctx: Context, ms: Long): String =
    if (sameDay(ms, System.currentTimeMillis())) formatClock(ctx, ms) else dayLabel(ctx, ms)

fun formatDuration(sec: Int): String = String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)
