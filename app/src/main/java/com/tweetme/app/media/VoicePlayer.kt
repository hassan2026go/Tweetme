package com.tweetme.app.media

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** مشغّل واحد للرسائل الصوتية: تشغيل رسالة يوقف السابقة. */
class VoicePlayer(private val context: Context) {
    data class State(val id: String? = null, val playing: Boolean = false, val progress: Float = 0f)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var mp: MediaPlayer? = null
    private var ticker: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun toggle(id: String, url: String) {
        val cur = mp
        if (_state.value.id == id && cur != null) {
            val playing = runCatching { cur.isPlaying }.getOrDefault(false)
            if (playing) {
                cur.pause()
                _state.update { it.copy(playing = false) }
            } else {
                cur.start()
                _state.update { it.copy(playing = true) }
                startTicker()
            }
            return
        }
        stop()
        val bytes = MediaUtils.decodeDataUri(url) ?: return
        val file = File(context.cacheDir, "play_${id.hashCode()}.m4a")
        val p = MediaPlayer()
        try {
            file.writeBytes(bytes)
            p.setDataSource(file.absolutePath)
            p.prepare()
            p.setOnCompletionListener { this@VoicePlayer.stop() }
            p.start()
        } catch (e: Exception) {
            p.release()
            return
        }
        mp = p
        _state.value = State(id, true, 0f)
        startTicker()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val p = mp ?: break
                val d = runCatching { p.duration }.getOrDefault(0)
                if (d > 0) {
                    val pos = runCatching { p.currentPosition }.getOrDefault(0)
                    _state.update { it.copy(progress = pos.toFloat() / d) }
                }
                delay(150)
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        runCatching { mp?.release() }
        mp = null
        _state.value = State()
    }
}
