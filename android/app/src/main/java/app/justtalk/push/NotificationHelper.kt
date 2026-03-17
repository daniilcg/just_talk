package app.justtalk.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.justtalk.MainActivity

object NotificationHelper {
    // IMPORTANT:
    // Android does not update channel sound settings once a channel is created.
    // Bump IDs when changing sound/vibration behavior.
    const val CHANNEL_CALLS = "calls_v2"
    const val CHANNEL_MESSAGES = "messages_v2"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)

        val callSound = Uri.parse("android.resource://${context.packageName}/raw/ring")
        val msgSound = Uri.parse("android.resource://${context.packageName}/raw/msg")
        val callAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val msgAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val calls = NotificationChannel(
            CHANNEL_CALLS,
            "Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming calls"
            setSound(callSound, callAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 200, 250, 200, 450)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val messages = NotificationChannel(
            CHANNEL_MESSAGES,
            "Messages",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Messages"
            setSound(msgSound, msgAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 120, 80, 120)
            setShowBadge(true)
        }
        nm.createNotificationChannel(calls)
        nm.createNotificationChannel(messages)
    }

    fun showIncomingCall(context: Context, from: String?, roomId: String, isVideo: Boolean = true) {
        ensureChannels(context)
        val callSound = Uri.parse("android.resource://${context.packageName}/raw/ring")
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("roomId", roomId)
            putExtra("video", if (isVideo) 1 else 0)
        }
        val pi = PendingIntent.getActivity(
            context,
            roomId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (isVideo) "Входящий видеозвонок" else "Входящий аудиозвонок"
        val text = if (!from.isNullOrBlank()) "От: $from" else "Нажми, чтобы ответить"
        val notif = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(callSound)
            .setVibrate(longArrayOf(0, 250, 200, 250, 200, 450))
            .setContentIntent(pi)
            .build()

        NotificationManagerCompat.from(context).notify(1001, notif)
    }

    fun showMessage(context: Context, fromUid: String, text: String) {
        ensureChannels(context)
        val msgSound = Uri.parse("android.resource://${context.packageName}/raw/msg")
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            (fromUid + text).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(fromUid)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setSound(msgSound)
            .setVibrate(longArrayOf(0, 120, 80, 120))
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(2000 + (fromUid.hashCode() and 0x7fff), notif)
    }
}

