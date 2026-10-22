package com.tweetme.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** أدوات ضغط الصور وتحويل الوسائط إلى نص (data URI) لحفظها داخل Firestore مجاناً. */
object MediaUtils {
    private const val BG_FILE = "chat_bg.jpg"

    /** يضغط الصورة إلى JPEG بأبعاد وحجم أقصى، ويُرجع null إن تعذّر بلوغ الحجم. */
    fun compressImage(context: Context, uri: Uri, maxDim: Int, maxBytes: Int): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (e: Exception) {
            0
        }
        val scale = min(1f, maxDim.toFloat() / max(raw.width, raw.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)

        var quality = 80
        var bytes = ByteArray(0)
        do {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 10
        } while (bytes.size > maxBytes && quality >= 30)
        return if (bytes.size <= maxBytes) bytes else null
    }

    fun toDataUri(mime: String, bytes: ByteArray): String =
        "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun decodeDataUri(uri: String): ByteArray? {
        val i = uri.indexOf(',')
        if (!uri.startsWith("data:") || i < 0) return null
        return try {
            Base64.decode(uri.substring(i + 1), Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun bitmapFromDataUri(uri: String): Bitmap? {
        val bytes = decodeDataUri(uri) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /** يحفظ صورة خلفية المحادثة محلياً على الهاتف. */
    fun saveChatBackground(context: Context, uri: Uri): Boolean {
        val bytes = compressImage(context, uri, 1280, 1_500_000) ?: return false
        return try {
            File(context.filesDir, BG_FILE).writeBytes(bytes)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun loadChatBackground(context: Context): Bitmap? {
        val f = File(context.filesDir, BG_FILE)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }
}
