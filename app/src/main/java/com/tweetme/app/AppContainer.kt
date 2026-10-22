package com.tweetme.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.tweetme.app.data.ChatBackend
import com.tweetme.app.data.DemoBackend
import com.tweetme.app.data.FirebaseBackend
import com.tweetme.app.data.PrefsStore
import com.tweetme.app.media.VoicePlayer

/**
 * مكان واحد ينشئ خدمات التطبيق ويوزعها.
 * عند وجود google-services.json يُستخدم Firebase، وإلا الوضع التجريبي.
 */
class AppContainer(val context: Context) {
    val prefs = PrefsStore(context)
    val player = VoicePlayer(context)
    val backend: ChatBackend = if (firebaseConfigured(context)) FirebaseBackend() else DemoBackend()

    private fun firebaseConfigured(c: Context): Boolean =
        FirebaseApp.getApps(c).isNotEmpty() || FirebaseApp.initializeApp(c) != null
}
