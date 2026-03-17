package app.justtalk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var roomId by remember { mutableStateOf(intent?.getStringExtra("roomId")) }
            var video by remember { mutableStateOf(intent?.getIntExtra("video", 1) ?: 1) }
            JustTalkApp(
                initialRoomId = roomId,
                initialVideo = (video != 0)
            )
            // Update when notification tap delivers a new intent.
            this@MainActivity.intent?.let {
                roomId = it.getStringExtra("roomId") ?: roomId
                video = it.getIntExtra("video", video)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

