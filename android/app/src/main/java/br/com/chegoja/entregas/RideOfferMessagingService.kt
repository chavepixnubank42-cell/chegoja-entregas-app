package br.com.chegoja.entregas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recebe as mensagens do Firebase Cloud Messaging mesmo com o app
 * completamente fechado. Mensagens do tipo "new-offer" (corrida nova
 * oferecida a este motoboy) abrem uma tela cheia com som de toque, igual
 * uma ligação chegando — as outras mensagens viram só uma notificação
 * normal do sistema.
 */
class RideOfferMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // O app, quando aberto, já reenvia o token novo para o backend
        // (ver ensureFcmRegistration no entregas.html) — nada a fazer aqui.
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

    private fun showRingingNotification(remoteMessage: RemoteMessage) {
        val orderId = remoteMessage.data["orderId"] ?: ""
        val title = remoteMessage.data["title"]?.takeIf { it.isNotBlank() } ?: "Nova corrida disponível!"
        val body = remoteMessage.data["body"]?.takeIf { it.isNotBlank() } ?: "Toque para ver os detalhes"

        val answerIntent = Intent(this, RideOfferActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("orderId", orderId)
            putExtra("title", title)
            putExtra("body", body)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            this, 0, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // O botão "Dispensar" também abre a RideOfferActivity (precisa ser
        // uma Activity, não um BroadcastReceiver, para funcionar de forma
        // confiável com o app fechado), só que com um sinal pra ela se
        // fechar na hora, sem mostrar nada nem tocar som.
        val declineIntent = Intent(this, RideOfferActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "decline")
        }
        val declinePendingIntent = PendingIntent.getActivity(
            this, 1, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ensureChannels()

        // Nota: tentamos usar o CallStyle do Android aqui (o recurso que
        // apps de chamada usam), que é mais confiável para abrir a tela
        // sozinho — só que ele também traz a interface NATIVA de chamada
        // do próprio sistema ("Atender"/"Recusar" genéricos), sem a cara do
        // app. Voltamos para uma notificação customizada (nossa tela
        // própria, com "Ver corrida"/"Dispensar"), e para a confiabilidade
        // de abrir sozinho, o usuário também precisa desativar a
        // otimização de bateria para o app (ver MainActivity.java).
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(answerPendingIntent, true)
            .addAction(0, "Ver corrida", answerPendingIntent)
            .addAction(0, "Dispensar", declinePendingIntent)
            .setContentIntent(answerPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

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

            val rideChannel = NotificationChannel(
                CHANNEL_ID, "Corridas novas", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisa quando uma corrida nova é oferecida a você"
                enableVibration(true)
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
        const val CHANNEL_ID = "ride-offer"
        const val DEFAULT_CHANNEL_ID = "default"
        const val RIDE_OFFER_NOTIFICATION_ID = 1001
        const val DEFAULT_NOTIFICATION_ID = 1002
    }
}
