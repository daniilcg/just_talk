package app.justtalk

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.justtalk.core.directory.DirectoryEvent
import app.justtalk.core.directory.DirectorySession
import app.justtalk.data.ProfileStore
import app.justtalk.ui.screens.AuthScreen
import app.justtalk.ui.screens.CallScreen
import app.justtalk.ui.screens.HomeScreen
import app.justtalk.ui.screens.ChatScreen
import app.justtalk.ui.screens.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun JustTalkApp(initialRoomId: String?) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colorScheme =
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (dark) DarkColors else LightColors
        }

    MaterialTheme(colorScheme = colorScheme) {
        val store = remember { ProfileStore(context) }
        val hasUid = remember { runBlocking { !store.uid.first().isNullOrBlank() } }
        val session = remember { DirectorySession(context.applicationContext) }
        var incomingInvite by remember { mutableStateOf<Pair<String, String>?>(null) } // fromPeerId, roomId

        LaunchedEffect(Unit) {
            session.start()
            session.events.collectLatest { ev ->
                if (ev is DirectoryEvent.Invite) {
                    incomingInvite = ev.fromPeerId to ev.roomId
                }
            }
        }

        val nav = rememberNavController()
        val startDestination =
            if (!initialRoomId.isNullOrBlank() && hasUid) "call/$initialRoomId" else "auth"

        val invite = incomingInvite
        if (invite != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { incomingInvite = null },
                title = { androidx.compose.material3.Text("Входящий звонок") },
                text = { androidx.compose.material3.Text("Комната: ${invite.second}") },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = {
                        val room = invite.second
                        incomingInvite = null
                        nav.navigate("call/$room?video=1")
                    }) { androidx.compose.material3.Text("Принять") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { incomingInvite = null }) {
                        androidx.compose.material3.Text("Отклонить")
                    }
                }
            )
        }

        NavHost(navController = nav, startDestination = startDestination) {
            composable("auth") {
                AuthScreen(
                    onDone = { nav.navigate("home") { popUpTo("auth") { inclusive = true } } },
                    onOpenSettings = { nav.navigate("settings") }
                )
            }
            composable("home") {
                HomeScreen(
                    onStartCall = { roomId ->
                        nav.navigate("call/$roomId?video=1")
                    },
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenChat = { chatUid -> nav.navigate("chat/$chatUid") },
                    session = session
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
            composable("chat/{uid}") { backStackEntry ->
                val chatUid = backStackEntry.arguments?.getString("uid").orEmpty()
                ChatScreen(
                    uid = chatUid,
                    onBack = { nav.popBackStack() },
                    onStartCall = { roomId, isVideo ->
                        nav.navigate("call/$roomId?video=${if (isVideo) 1 else 0}")
                    },
                    session = session
                )
            }
            composable("call/{roomId}?video={video}") { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId").orEmpty()
                val isVideo = backStackEntry.arguments?.getString("video") != "0"
                CallScreen(
                    roomId = roomId,
                    isVideo = isVideo,
                    onHangup = { nav.popBackStack() }
                )
            }
        }
    }
}

