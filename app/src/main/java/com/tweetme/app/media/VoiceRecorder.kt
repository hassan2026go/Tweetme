package com.tweetme.app.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

class Recorded(val bytes: ByteArray, val durSec: Int)

/** مسجّل رسائل صوتية بصيغة AAC خفيفة (32kbps) لتبقى الرسالة صغيرة. */
class VoiceRecorder(private val context: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startMs = 0L

    fun elapsedSec(): Int = ((SystemClock.elapsedRealtime() - startMs) / 1000).toInt()

    fun start(): Boolean {
        val f = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
        val r = newRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16000)
            r.setAudioEncodingBitRate(32000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r
            file = f
            startMs = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            false
        }
    }

    /** يوقف التسجيل ويُرجع الصوت، أو null إن فشل أو كان قصيراً جداً. */
    fun stop(): Recorded? {
        val r = rec ?: return null
        rec = null
        val f = file
        file = null
        val dur = elapsedSec()
        val ok = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            false
        } finally {
            runCatching { r.release() }
        }
        if (f == null) return null
        if (!ok || dur < 1) {
            f.delete()
            return null
        }
        val bytes = f.readBytes()
        f.delete()
        return Recorded(bytes, dur)
    }

    fun cancel() {
        stop()
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()

    companion object {
        const val MAX_SEC = 90
    }
}
