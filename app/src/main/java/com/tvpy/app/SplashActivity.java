package com.tvpy.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isAndroidTV()) {
            String lastUrl = LastChannelManager.getLastChannel(this);
            if (lastUrl != null) {
                // Necesitamos cargar los canales y buscar el índice
                // Para simplicidad en el inicio, recarguemos todo
                java.util.List<Channel> allChannels = new java.util.ArrayList<>();
                allChannels.addAll(ChannelData.getChannels(this));
                allChannels.addAll(ChannelStore.loadM3uChannels(this));
                allChannels = ChannelDeduplicator.deduplicate(allChannels);

                int lastIndex = -1;
                for (int i = 0; i < allChannels.size(); i++) {
                    if (allChannels.get(i).getUrl().equals(lastUrl)) {
                        lastIndex = i;
                        break;
                    }
                }

                if (lastIndex != -1) {
                    ChannelSession.set(allChannels, lastIndex);
                    startActivity(new Intent(this, PlayerActivity.class));
                    finish();
                    return;
                }
            }
        }

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private boolean isAndroidTV() {
        android.app.UiModeManager uiModeManager = (android.app.UiModeManager) getSystemService(UI_MODE_SERVICE);
        return uiModeManager != null && uiModeManager.getCurrentModeType() == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
    }
}
