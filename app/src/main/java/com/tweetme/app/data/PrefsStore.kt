package com.tweetme.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { DARK, LIGHT, AUTO }

/** إعدادات المظهر التي يتحكم بها المستخدم. أضف حقلاً هنا ثم مفتاحاً ودالة حفظ في PrefsStore. */
data class AppPrefs(
    val mode: ThemeMode = ThemeMode.DARK,
    val accent: Long = DEFAULT_ACCENT,
    val bgId: String = "default",
    val bgStamp: Long = 0L,
    /** لون فقاعة رسائلي؛ 0 يعني نفس لون التطبيق. */
    val bubble: Long = 0L,
    val fontScale: Float = 1f
) {
    companion object {
        const val DEFAULT_ACCENT = 0xFF2F7BFFL
    }
}

private val Context.dataStore by preferencesDataStore(name = "tweetme_prefs")

class PrefsStore(private val context: Context) {
    private val kMode = stringPreferencesKey("mode")
    private val kAccent = longPreferencesKey("accent")
    private val kBg = stringPreferencesKey("bg")
    private val kBgStamp = longPreferencesKey("bg_stamp")
    private val kBubble = longPreferencesKey("bubble")
    private val kFont = floatPreferencesKey("font_scale")

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { p: Preferences ->
        AppPrefs(
            mode = ThemeMode.entries.firstOrNull { it.name == p[kMode] } ?: ThemeMode.DARK,
            accent = p[kAccent] ?: AppPrefs.DEFAULT_ACCENT,
            bgId = p[kBg] ?: "default",
            bgStamp = p[kBgStamp] ?: 0L,
            bubble = p[kBubble] ?: 0L,
            fontScale = p[kFont] ?: 1f
        )
    }

    suspend fun setMode(m: ThemeMode) { context.dataStore.edit { it[kMode] = m.name } }
    suspend fun setAccent(c: Long) { context.dataStore.edit { it[kAccent] = c } }
    suspend fun setBubble(c: Long) { context.dataStore.edit { it[kBubble] = c } }
    suspend fun setFontScale(f: Float) { context.dataStore.edit { it[kFont] = f } }

    suspend fun setBackground(id: String) {
        context.dataStore.edit {
            it[kBg] = id
            it[kBgStamp] = System.currentTimeMillis()
        }
    }
}
