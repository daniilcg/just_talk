package app.justtalk.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class JustTalkFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"]
        if (type == "invite") {
            val roomId = data["roomId"].orEmpty()
            val from = data["from"]
            // From server we send isVideo as "1"/"0" string for FCM data.
            val isVideo = when (val raw = data["isVideo"]) {
                "1", "true", "TRUE", "True" -> true
                "0", "false", "FALSE", "False" -> false
                else -> true // default to video if field missing/unknown
            }
            if (roomId.isNotBlank()) {
                NotificationHelper.showIncomingCall(this, from, roomId, isVideo = isVideo)
            }
        }
    }
}

