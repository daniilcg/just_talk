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
            if (roomId.isNotBlank()) {
                NotificationHelper.showIncomingCall(this, from, roomId)
            }
        }
    }
}

