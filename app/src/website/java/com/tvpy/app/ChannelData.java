package com.tvpy.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Carga la lista de canales predeterminados.
 * Primero intenta leer la caché local descargada desde GitHub,
 * y en su defecto lee el archivo default_channels.m3u desde assets.
 */
public class ChannelData {

    public static List<Channel> getChannels(android.content.Context context) {
        List<Channel> channels = new ArrayList<>();
        
        String m3uContent = "";
        try {
            java.io.File cacheFile = new java.io.File(context.getFilesDir(), "default_channels_cached.m3u");
            if (cacheFile.exists()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(cacheFile));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                m3uContent = sb.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (m3uContent.isEmpty() || !m3uContent.startsWith("#EXTM3U")) {
            // Fallback: assets
            try {
                java.io.InputStream is = context.getAssets().open("default_channels.m3u");
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                m3uContent = sb.toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!m3uContent.isEmpty()) {
            List<Channel> parsed = M3uParser.parse(m3uContent);
            for (Channel ch : parsed) {
                if (RecommendedChannels.NAMES.contains(ch.getName())) {
                    channels.add(ch);
                }
            }
        }
        
        return channels;
    }

    public static List<Channel> getChannels() {
        return new ArrayList<>();
    }
}