package com.tweetme.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tweetme.app.data.AppPrefs
import com.tweetme.app.ui.AppNav
import com.tweetme.app.ui.theme.TweetMeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TweetMeApp).container
        setContent {
            val prefs by container.prefs.prefs.collectAsState(initial = AppPrefs())
            TweetMeTheme(prefs) {
                AppNav(container, prefs)
            }
        }
    }
}
