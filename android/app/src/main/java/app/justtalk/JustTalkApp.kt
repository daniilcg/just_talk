package app.justtalk

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.justtalk.data.ProfileStore
import app.justtalk.ui.screens.AuthScreen
import app.justtalk.ui.screens.CallScreen
import app.justtalk.ui.screens.HomeScreen
import kotlinx.coroutines.flow.first
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

        val nav = rememberNavController()
        val startDestination =
            if (!initialRoomId.isNullOrBlank() && hasUid) "call/$initialRoomId" else "auth"

        NavHost(navController = nav, startDestination = startDestination) {
            composable("auth") {
                AuthScreen(
                    onDone = { nav.navigate("home") { popUpTo("auth") { inclusive = true } } }
                )
            }
            composable("home") {
                HomeScreen(
                    onStartCall = { roomId ->
                        nav.navigate("call/$roomId")
                    }
                )
            }
            composable("call/{roomId}") { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId").orEmpty()
                CallScreen(
                    roomId = roomId,
                    onHangup = { nav.popBackStack() }
                )
            }
        }
    }
}

