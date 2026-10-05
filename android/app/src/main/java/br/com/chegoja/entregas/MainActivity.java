package br.com.chegoja.entregas;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkFullScreenIntentPermission();
    }

    // A partir do Android 14, a permissão de abrir uma tela cheia sozinho
    // (usada pela notificação de corrida nova, ver RideOfferMessagingService)
    // vem DESATIVADA por padrão para a maioria dos apps — sem isso, a tela
    // nunca abre automaticamente, mesmo com tudo certo no resto do código.
    // Aqui a gente checa isso ao abrir o app e, se precisar, leva a pessoa
    // direto para a tela de configuração certa.
    private void checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= 34) { // Android 14 (UPSIDE_DOWN_CAKE) ou mais novo
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
                            // Alguns aparelhos/fabricantes não têm essa tela específica — sem problema, só ignora.
                        }
                    })
                    .setNegativeButton("Agora não", null)
                    .show();
            }
        }
    }
}
