package com.tweetme.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.tweetme.app.data.AppPrefs
import com.tweetme.app.data.ThemeMode

/** اللون المناسب للنص فوق لون معيّن (أبيض أو كحلي غامق). */
fun onColorFor(c: Color): Color = if (c.luminance() > 0.55f) Color(0xFF06101F) else Color.White

/** ثيم التطبيق: أزرق وأسود، والمستخدم يغيّر الوضع واللون وحجم الخط من الإعدادات. */
@Composable
fun TweetMeTheme(prefs: AppPrefs, content: @Composable () -> Unit) {
    val dark = when (prefs.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    val accent = Color(prefs.accent)
    val onAccent = onColorFor(accent)

    val scheme = if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = onAccent,
            background = Color(0xFF050912), onBackground = Color(0xFFE8EEFC),
            surface = Color(0xFF0B1220), onSurface = Color(0xFFE8EEFC),
            surfaceVariant = Color(0xFF141F38), onSurfaceVariant = Color(0xFF8896B3),
            outline = Color(0xFF1A2540), outlineVariant = Color(0xFF1A2540),
            surfaceContainer = Color(0xFF0B1220), surfaceContainerHigh = Color(0xFF111B30)
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = onAccent,
            background = Color(0xFFF4F7FC), onBackground = Color(0xFF0B1220),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0B1220),
            surfaceVariant = Color(0xFFE6ECF7), onSurfaceVariant = Color(0xFF5D6B88),
            outline = Color(0xFFDBE3F1), outlineVariant = Color(0xFFDBE3F1),
            surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFEEF2FA)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalDensity provides Density(density.density, density.fontScale * prefs.fontScale)
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
