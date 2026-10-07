package br.com.chegoja.entregas

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recebe as mensagens do Firebase Cloud Messaging mesmo com o app
 * completamente fechado. Mensagens do tipo "new-offer" (corrida nova)
 * viram uma notificação que toca o som do app em loop (igual uma
 * ligação) e abre o app de verdade (MainActivity) em tela cheia.
 */
class RideOfferMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // O app, quando aberto, já reenvia o token novo para o backend.
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val type = remoteMessage.data["type"]
        if (type == "new-offer") {
            showRingingNotification(remoteMessage)
        } else {
            showSimpleNotification(remoteMessage)
        }
    }

    private fun rideSoundUri(): Uri {
        val resId = resources.getIdentifier("toque_corrida", "raw", packageName)
        return if (resId != 0) {
            Uri.parse("android.resource://$packageName/$resId")
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }
    }

    private fun showRingingNotification(remoteMessage: RemoteMessage) {
        val orderId = remoteMessage.data["orderId"] ?: ""
        val title = remoteMessage.data["title"]?.takeIf { it.isNotBlank() } ?: "Nova corrida disponível!"
        val body = remoteMessage.data["body"]?.takeIf { it.isNotBlank() } ?: "Toque para ver os detalhes"

        // Abre o app de verdade (tela de entrada), não uma tela separada.
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ride_offer", true)
            putExtra("orderId", orderId)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Dispensar" só cancela a notificação (e o som), sem abrir o app.
        val declineIntent = Intent(this, RideOfferActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "decline")
        }
        val declinePendingIntent = PendingIntent.getActivity(
            this, 1, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ensureChannels()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(rideSoundUri())
            .setVibrate(longArrayOf(0, 800, 500))
            .setFullScreenIntent(openAppPendingIntent, true)
            .addAction(0, "Ver corrida", openAppPendingIntent)
            .addAction(0, "Dispensar", declinePendingIntent)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            // Some sozinha (e o som para) depois do prazo da oferta.
            .setTimeoutAfter(OFFER_TIMEOUT_MS)
            .build()

        // INSISTENT = o som repete em loop até a notificação sumir.
        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(RIDE_OFFER_NOTIFICATION_ID, notification)
    }

    private fun showSimpleNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "ChegouJá"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: ""

        ensureChannels()
        val notification = NotificationCompat.Builder(this, DEFAULT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(DEFAULT_NOTIFICATION_ID, notification)
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Apaga os canais antigos (sem som ou com som errado).
            nm.deleteNotificationChannel("ride-offer")
            nm.deleteNotificationChannel("ride-offer-v2")

            val rideChannel = NotificationChannel(
                CHANNEL_ID, "Corridas novas", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisa quando uma corrida nova é oferecida a você"
                setSound(
                    rideSoundUri(),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            nm.createNotificationChannel(rideChannel)

            val defaultChannel = NotificationChannel(
                DEFAULT_CHANNEL_ID, "Geral", NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(defaultChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "ride-offer-v3"
        const val DEFAULT_CHANNEL_ID = "default"
        const val RIDE_OFFER_NOTIFICATION_ID = 1001
        const val DEFAULT_NOTIFICATION_ID = 1002
        const val OFFER_TIMEOUT_MS = 30_000L
    }
}
