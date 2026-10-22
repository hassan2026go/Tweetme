package com.tweetme.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tweetme.app.AppContainer
import com.tweetme.app.R
import com.tweetme.app.core.User
import com.tweetme.app.data.AppPrefs
import com.tweetme.app.ui.auth.AuthScreen
import com.tweetme.app.ui.chat.ChatScreen
import com.tweetme.app.ui.chat.ChatViewModel
import com.tweetme.app.ui.chats.ChatListScreen
import com.tweetme.app.ui.chats.ChatListViewModel
import com.tweetme.app.ui.common.SoonScreen
import com.tweetme.app.ui.settings.SettingsScreen

private const val ROUTE_CHATS = "chats"
private const val ROUTE_STATUS = "status"
private const val ROUTE_CALLS = "calls"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CHAT = "chat/{uid}"

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

/** نقطة التنقل الرئيسية: شاشة التحميل ← الدخول ← التطبيق. لإضافة شاشة جديدة أضف route هنا. */
@Composable
fun AppNav(container: AppContainer, prefs: AppPrefs) {
    val ready by container.backend.ready.collectAsState()
    val user by container.backend.currentUser.collectAsState()
    val me = user
    when {
        !ready -> Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        me == null -> AuthScreen(container.backend)

        else -> key(me.uid) { SignedIn(container, prefs, me) }
    }
}

@Composable
private fun SignedIn(c: AppContainer, prefs: AppPrefs, me: User) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        Tab(ROUTE_CHATS, R.string.tab_chats, Icons.Outlined.Chat),
        Tab(ROUTE_STATUS, R.string.tab_status, Icons.Outlined.DonutLarge),
        Tab(ROUTE_CALLS, R.string.tab_calls, Icons.Outlined.Call),
        Tab(ROUTE_SETTINGS, R.string.tab_settings, Icons.Outlined.Settings)
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(ROUTE_CHATS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.label)) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = ROUTE_CHATS, modifier = Modifier.padding(pad)) {
            composable(ROUTE_CHATS) {
                val vm: ChatListViewModel = viewModel(
                    factory = viewModelFactory { initializer { ChatListViewModel(c.backend, me.uid) } }
                )
                ChatListScreen(vm, c.backend, me) { uid -> nav.navigate("chat/$uid") }
            }
            composable(ROUTE_STATUS) { SoonScreen(R.string.tab_status) }
            composable(ROUTE_CALLS) { SoonScreen(R.string.tab_calls) }
            composable(ROUTE_SETTINGS) { SettingsScreen(me, prefs, c.prefs, c.backend) }
            composable(ROUTE_CHAT, arguments = listOf(navArgument("uid") { type = NavType.StringType })) { e ->
                val uid = e.arguments?.getString("uid").orEmpty()
                val vm: ChatViewModel = viewModel(
                    key = "chat_$uid",
                    factory = viewModelFactory { initializer { ChatViewModel(c.context, c.backend, me.uid, uid) } }
                )
                ChatScreen(vm, me, c.player, prefs) { nav.popBackStack() }
            }
        }
    }
}
