package app.justtalk

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import app.justtalk.core.directory.DirectoryEvent
import app.justtalk.core.directory.DirectorySession
import app.justtalk.data.ProfileStore
import app.justtalk.push.NotificationHelper
import app.justtalk.ui.theme.JustTalkTheme
import app.justtalk.ui.screens.AuthScreen
import app.justtalk.ui.screens.CallScreen
import app.justtalk.ui.screens.HomeScreen
import app.justtalk.ui.screens.ChatScreen
import app.justtalk.ui.screens.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking

@Composable
fun JustTalkApp(initialRoomId: String?) {
    val context = LocalContext.current
    JustTalkTheme {
        // Android 13+ requires runtime notification permission for sounds/notifications.
        if (Build.VERSION.SDK_INT >= 33) {
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { _ -> }
            LaunchedEffect(Unit) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val store = remember { ProfileStore(context) }
        val hasUid = remember { runBlocking { !store.uid.first().isNullOrBlank() } }
        val session = remember { DirectorySession(context.applicationContext) }
        var incomingInvite by remember { mutableStateOf<Pair<String, String>?>(null) } // fromPeerId, roomId

        LaunchedEffect(Unit) {
            session.start()
            session.events.collectLatest { ev ->
                if (ev is DirectoryEvent.Invite) {
                    incomingInvite = ev.fromPeerId to ev.roomId
                    NotificationHelper.showIncomingCall(
                        context = context.applicationContext,
                        from = ev.fromPeerId,
                        roomId = ev.roomId
                    )
                }
                if (ev is DirectoryEvent.Msg) {
                    // Show a lightweight notification while app is running.
                    NotificationHelper.showMessage(
                        context = context.applicationContext,
                        fromUid = ev.fromUid,
                        text = ev.text
                    )
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
                SettingsScreen(onBack = { nav.popBackStack() }, session = session)
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

