package br.com.chegoja.entregas

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela cheia que aparece por cima da tela de bloqueio quando chega uma
 * corrida nova, com som em loop e vibração — o efeito de "tocar igual
 * uma ligação" que apps de entrega (Uber, iFood) usam para o entregador
 * não perder a oferta mesmo com o celular no bolso/bloqueado.
 */
class RideOfferActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var titleView: TextView? = null
    private var bodyView: TextView? = null
    private val autoTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoTimeoutRunnable = Runnable { onOfferExpired() }

    // Mesmo prazo que o servidor usa para oferecer a corrida a este
    // motoboy antes de passar para o próximo da fila (ver OFFER_TIMEOUT_MS
    // no server.js) — depois disso não faz mais sentido continuar tocando.
    private val OFFER_TIMEOUT_MS = 30_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Veio do botão "Dispensar" da notificação (CallStyle) — só limpa
        // tudo e fecha, sem mostrar nada nem tocar som.
        if (intent.getStringExtra("action") == "decline") {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(RideOfferMessagingService.RIDE_OFFER_NOTIFICATION_ID)
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val title = intent.getStringExtra("title") ?: "Nova corrida disponível!"
        val body = intent.getStringExtra("body") ?: ""

        setContentView(buildLayout(title, body))
        startRinging()

        autoTimeoutHandler.postDelayed(autoTimeoutRunnable, OFFER_TIMEOUT_MS)
    }

    private fun onOfferExpired() {
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
        mediaPlayer = null
        try { vibrator?.cancel() } catch (e: Exception) {}
        titleView?.text = "O tempo para aceitar essa corrida acabou"
        bodyView?.text = "Abra o app para ver se ela ainda está disponível."
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(RideOfferMessagingService.RIDE_OFFER_NOTIFICATION_ID)
        // Deixa a mensagem visível por alguns segundos antes de fechar
        // sozinha, em vez de ficar presa na tela esperando a pessoa tocar
        // em algo.
        autoTimeoutHandler.postDelayed({ if(!isFinishing) finish() }, 4000)
    }

    private fun buildLayout(title: String, body: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 120, 60, 120)
            setBackgroundColor(0xFF1E3A5F.toInt())
        }

        val titleTv = TextView(this).apply {
            text = title
            textSize = 26f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 48, 0, 16)
        }
        val bodyTv = TextView(this).apply {
            text = body
            textSize = 16f
            setTextColor(0xFFE0E0E0.toInt())
            setPadding(0, 0, 0, 96)
        }
        titleView = titleTv
        bodyView = bodyTv

        val acceptButton = Button(this).apply {
            text = "Ver corrida"
            setOnClickListener { openApp() }
        }
        val spacer = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 32)
        }
        val declineButton = Button(this).apply {
            text = "Dispensar"
            setOnClickListener { dismissRinging() }
        }

        root.addView(titleTv)
        root.addView(bodyTv)
        root.addView(acceptButton)
        root.addView(spacer)
        root.addView(declineButton)
        return root
    }

    private fun startRinging() {
        try {
            val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@RideOfferActivity, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Se o som falhar por qualquer motivo, a vibração abaixo
            // ainda avisa a pessoa — não é crítico para o app funcionar.
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
        } catch (e: Exception) { }
    }

    private fun dismissRinging() {
        autoTimeoutHandler.removeCallbacks(autoTimeoutRunnable)
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
        mediaPlayer = null
        try { vibrator?.cancel() } catch (e: Exception) {}
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(RideOfferMessagingService.RIDE_OFFER_NOTIFICATION_ID)
        finish()
    }

    private fun openApp() {
        dismissRinging()
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(launchIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        autoTimeoutHandler.removeCallbacks(autoTimeoutRunnable)
        try { mediaPlayer?.release() } catch (e: Exception) {}
        try { vibrator?.cancel() } catch (e: Exception) {}
    }
}
