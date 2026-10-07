package br.com.chegoja.entregas

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Serviço que toca o som de corrida nova em loop (igual uma ligação),
 * mesmo com o app fechado, e mostra a notificação que abre o app de
 * verdade. Para quando a pessoa interage, ou depois de 30 segundos.
 */
class RideRingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val orderId = intent?.getStringExtra("orderId") ?: ""
        val title = intent?.getStringExtra("title")?.takeIf { it.isNotBlank() } ?: "Nova corrida disponível!"
        val body = intent?.getStringExtra("body")?.takeIf { it.isNotBlank() } ?: "Toque para ver os detalhes"

        val notification = buildNotification(title, body, orderId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        stopRinging() // caso chegue uma segunda corrida enquanto toca
        startRinging()

        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, body: String, orderId: String): Notification {
        ensureChannel()

        // Abre o app de verdade (tela de entrada).
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

        // "Dispensar" só para o som e some com a notificação.
        val stopIntent = Intent(this, RideRingService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(openAppPendingIntent, true)
            .addAction(0, "Ver corrida", openAppPendingIntent)
            .addAction(0, "Dispensar", stopPendingIntent)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.deleteNotificationChannel("ride-offer-v2")
            nm.deleteNotificationChannel("ride-offer-v3")
            val channel = NotificationChannel(
                CHANNEL_ID, "Corridas novas", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisa quando uma corrida nova é oferecida a você"
                setSound(null, null) // quem toca o som é este serviço
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun startRinging() {
        val resId = resources.getIdentifier("toque_corrida", "raw", packageName)
        val candidates = listOfNotNull(
            if (resId != 0) Uri.parse("android.resource://$packageName/$resId") else null,
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )

        for (uri in candidates) {
            var player: MediaPlayer? = null
            try {
                Log.d(TAG, "Tentando tocar: $uri")
                player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                player.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                player.setDataSource(this, uri)
                player.isLooping = true
                player.setOnCompletionListener { mp ->
                    try {
                        mp.seekTo(0)
                        mp.start()
                    } catch (e: Exception) {
                        Log.e(TAG, "Falha ao reiniciar o som", e)
                    }
                }
                player.prepare()
                player.start()
                mediaPlayer = player
                Log.d(TAG, "Tocando em loop: $uri")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao tocar $uri", e)
                try { player?.release() } catch (e2: Exception) {}
            }
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 800, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha na vibração", e)
        }
    }

    private fun stopRinging() {
        try { mediaPlayer?.stop() } catch (e: Exception) {}
        try { mediaPlayer?.release() } catch (e: Exception) {}
        mediaPlayer = null
        try { vibrator?.cancel() } catch (e: Exception) {}
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutRunnable)
        stopRinging()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "br.com.chegoja.entregas.STOP_RING"
        const val CHANNEL_ID = "ride-offer-v4"
        const val NOTIFICATION_ID = 1001
        const val TIMEOUT_MS = 30_000L
        private const val TAG = "ChegojaRing"
    }
}
