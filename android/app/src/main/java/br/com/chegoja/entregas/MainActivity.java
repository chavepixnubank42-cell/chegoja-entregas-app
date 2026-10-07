package br.com.chegoja.entregas;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    // Mesmo ID usado em RideOfferMessagingService.RIDE_OFFER_NOTIFICATION_ID
    private static final int RIDE_OFFER_NOTIFICATION_ID = 1001;

    // true = o celular está bloqueado e o toque da corrida ainda está
    // tocando; ele para na primeira vez que a pessoa encosta na tela.
    private boolean ringPending = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleRideOfferIntent(getIntent());
        checkFullScreenIntentPermission();
        checkBatteryOptimization();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleRideOfferIntent(intent);
    }

    // Chamado quando o app é aberto pela notificação de corrida nova.
    private void handleRideOfferIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra("ride_offer", false)) return;
        intent.removeExtra("ride_offer");

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean locked = km != null && km.isKeyguardLocked();
        if (locked) {
            // Celular bloqueado: mostra o app por cima do bloqueio, acende
            // a tela e deixa o toque tocando até a pessoa encostar nela.
            showOverLockScreen();
            ringPending = true;
        } else {
            // Já está desbloqueado (a pessoa tocou em "Ver corrida"):
            // não precisa mais tocar.
            cancelRideNotification();
        }
    }

    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    private void cancelRideNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(RIDE_OFFER_NOTIFICATION_ID);
        } catch (Exception e) {
            // não crítico
        }
    }

    // Primeira vez que a pessoa encosta na tela do app com o toque
    // tocando: para o som e pede para desbloquear o celular.
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ringPending && ev.getAction() == MotionEvent.ACTION_DOWN) {
            ringPending = false;
            cancelRideNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                    if (km != null && km.isKeyguardLocked()) {
                        km.requestDismissKeyguard(this, null);
                    }
                } catch (Exception e) {
                    // não crítico
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // A partir do Android 14, a permissão de abrir uma tela cheia sozinho
    // (usada pela notificação de corrida nova) vem DESATIVADA por padrão.
    // Aqui a gente checa isso ao abrir o app e, se precisar, leva a pessoa
    // direto para a tela de configuração certa.
    private void checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= 34) { // Android 14 ou mais novo
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                new AlertDialog.Builder(this)
                    .setTitle("Permissão necessária")
                    .setMessage("Para o ChegouJá avisar sobre corridas novas tocando igual uma ligação — mesmo com a tela bloqueada — é preciso ativar uma permissão especial do Android.\n\nNa tela que vai abrir, ative a opção para o ChegouJá.")
                    .setPositiveButton("Abrir configurações", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            // Alguns aparelhos/fabricantes não têm essa tela específica — sem problema.
                        }
                    })
                    .setNegativeButton("Agora não", null)
                    .show();
            }
        }
    }

    // Em muitos aparelhos, o Android "congela" apps em segundo plano para
    // economizar bateria. Pedimos aqui pra colocar o ChegouJá na lista de
    // apps sem restrição de bateria.
    private void checkBatteryOptimization() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            new AlertDialog.Builder(this)
                .setTitle("Mais uma permissão importante")
                .setMessage("Para as corridas novas sempre tocarem, mesmo com o app fechado, o ChegouJá precisa ficar fora da otimização de bateria do seu celular.\n\nNa tela que vai abrir, procure \"Sem restrições\" ou \"Permitir\" para o ChegouJá.")
                .setPositiveButton("Abrir configurações", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        // Alguns aparelhos/fabricantes não têm essa tela específica — sem problema.
                    }
                })
                .setNegativeButton("Agora não", null)
                .show();
        }
    }
}
