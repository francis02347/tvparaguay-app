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
                if (YELLOW_CHANNELS.contains(ch.getName())) {
                    channels.add(ch);
                }
            }
        }
        
        return channels;
    }

    public static List<Channel> getChannels() {
        return new ArrayList<>();
    }

    private static final java.util.Set<String> YELLOW_CHANNELS = new java.util.HashSet<>(java.util.Arrays.asList(
        "A24 Argentina",
        "ABC-TV Paraguay",
        "América TV Argentina",
        "Andalucía Cocina (1080p)",
        "Antena Uno RadioVideo (360p)",
        "Azteca Internacional (México)",
        "Bruno Masi TV",
        "C9N",
        "CBN Español (1080p)",
        "Canal 5 (Televisa) (México)",
        "Canal Pro",
        "Claro Sports",
        "DW en Español",
        "Dance FM (720p)",
        "Deportes TVC (1080p)",
        "E40",
        "EWTN Spain & Latin America (720p)",
        "Enlace (720p)",
        "Esport3 (1080p) [Not 24/7]",
        "FIFA+ Spain (720p)",
        "FOX (Estados Unidos)",
        "FS1 (Estados Unidos)",
        "Farra Play (720p) [Not 24/7]",
        "France 24 Español",
        "GAMTV.cr (720p)",
        "Gen",
        "Gol Classics (1080p)",
        "HEi Now",
        "ITV Deportes (720p)",
        "Kpop Mix (720p)",
        "LaLiga TV",
        "Las Estrellas (México) [Geo-blocked]",
        "Latele",
        "MiTV",
        "Monumental TV",
        "NASA TV",
        "NOW 70s (720p)",
        "NOW 80s (1080p)",
        "NOW 90s00s (1080p)",
        "NOW Rock (1080p)",
        "NOW Rock (1080p)",
        "NPY",
        "Next HD",
        "Noticias Telemundo",
        "Paraguay TV",
        "Paravisión",
        "Popu TV",
        "Productiva TV",
        "RDN (1080p) [Not 24/7]",
        "RT en Español",
        "RTVE Canal 24 Horas",
        "RTVE La 1",
        "Rally TV (Internacional)",
        "Red Digital",
        "SNT",
        "Siembra TV (720p)",
        "Sky News (Inglés)",
        "Stingray Classica (1080p)",
        "Stingray Greatest Holiday Hits",
        "Stingray Karaoke (1080p)",
        "Stingray Naturescape (1080p)",
        "Stingray Naturescape (1080p)",
        "Stingray Romance Latino (1080p)",
        "Stingray Today's Latin Pop (1080p)",
        "Stingray Urban Beat (1080p)",
        "SuperTennis (Italia)",
        "Supermúsica TV (720p)",
        "TV Globo Bahia (Brasil)",
        "TV Pública (Argentina)",
        "Teledeporte (1080p)",
        "Telefe Internacional (Argentina)",
        "Telefuturo",
        "Telemundo (Estados Unidos)",
        "Tennis Channel (Internacional)",
        "Tigo Sports",
        "Tigo Sports (1080p)",
        "Tigo Sports+",
        "Totalmusic (1080p)",
        "Totalmusic 2000s (720p)",
        "Totalmusic 80s (720p)",
        "Totalmusic Concerts (720p)",
        "Totalmusic Dance (720p)",
        "Trece",
        "Tropicalia 93.9 FM",
        "TyC Sports (1080p)",
        "TyC Sports USA",
        "Unicanal",
        "Universo (Estados Unidos)",
        "Venus Media",
        "Vevo Latino (1080p)",
        "Vevo Pop (1080p)",
        "beIN Sports XTRA en Español (1080p)",
        "Ñandutí"
    ));
}