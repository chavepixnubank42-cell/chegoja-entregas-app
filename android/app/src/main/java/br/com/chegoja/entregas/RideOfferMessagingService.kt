package br.com.chegoja.entregas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recebe as mensagens do Firebase Cloud Messaging mesmo com o app
 * completamente fechado. Mensagens "new-offer" (corrida nova) iniciam o
 * RideRingService, que toca o som em loop e mostra a notificação.
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
            startRinging(remoteMessage)
        } else {
            showSimpleNotification(remoteMessage)
        }
    }

    private fun startRinging(remoteMessage: RemoteMessage) {
        val serviceIntent = Intent(this, RideRingService::class.java).apply {
            putExtra("orderId", remoteMessage.data["orderId"] ?: "")
            putExtra("title", remoteMessage.data["title"] ?: "")
            putExtra("body", remoteMessage.data["body"] ?: "")
        }
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            // Se o Android não deixar iniciar o serviço, ao menos avisa.
            showSimpleNotification(remoteMessage)
        }
    }

    private fun showSimpleNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "ChegouJá"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: ""

        ensureDefaultChannel()
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

    private fun ensureDefaultChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val defaultChannel = NotificationChannel(
                DEFAULT_CHANNEL_ID, "Geral", NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(defaultChannel)
        }
    }

    companion object {
        const val DEFAULT_CHANNEL_ID = "default"
        const val RIDE_OFFER_NOTIFICATION_ID = 1001
        const val DEFAULT_NOTIFICATION_ID = 1002
    }
}
