package com.tvpy.app;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contiene la lista de canales recomendados y su orden prioritario,
 * sincronizado exactamente con la versión web de la plataforma (TVV.LAT).
 */
public class RecommendedChannels {

    public static final List<String> ORDERED_NAMES = Arrays.asList(
        "SNT",
        "Telefuturo",
        "Trece",
        "Unicanal",
        "Latele",
        "C9N",
        "NPY",
        "A24 Argentina",
        "América TV Argentina",
        "El Garage TV (Argentina)",
        "Gen",
        "TV Pública (Argentina)",
        "Telefe Internacional (Argentina)",
        "Paravisión",
        "E40",
        "Monumental TV",
        "Ñandutí",
        "ABC-TV Paraguay",
        "Canal Pro",
        "HEi Now",
        "Next HD",
        "Paraguay TV",
        "Popu TV",
        "Productiva TV",
        "Red Digital",
        "Tigo Sports (Paraguay)",
        "Venus Media",
        "Arena Sport 1",
        "Arena Sport 2",
        "Azteca Deportes Network",
        "beIN Sports XTRA en Español",
        "Claro Sports",
        "DAZN 1",
        "DAZN 2",
        "DAZN 3 Bar HD",
        "DAZN 4 Bar HD",
        "DAZN Combat",
        "DAZN F1",
        "DAZN LaLiga",
        "Deportes TVC (1080p)",
        "DSports (DirecTV Sports)",
        "DSports 2",
        "DSports+",
        "ESPN (Brasil)",
        "ESPN (Señal Principal)",
        "ESPN 2",
        "ESPN 2 (Brasil)",
        "ESPN 3",
        "ESPN 3 (Brasil)",
        "ESPN 4",
        "ESPN 4 (Brasil)",
        "ESPN 5",
        "ESPN 6",
        "ESPN 7",
        "ESPN Extra (Brasil)",
        "ESPN Premium",
        "ESPN8: The Ocho",
        "FIFA+ Spain (720p)",
        "FOX (Estados Unidos)",
        "Fox Deportes",
        "Fox Sports 1",
        "Fox Sports 2",
        "Fox Sports 3",
        "FS1 (Estados Unidos)",
        "Golazo Network",
        "GolTV Latinoamérica",
        "ITV Deportes (720p)",
        "M+ LaLiga TV HD",
        "M+ LaLiga TV 2 HD",
        "M+ Liga de Campeones HD",
        "M+ Liga de Campeones 2 HD",
        "M+ Vamos HD",
        "Motorvision TV (Español)",
        "Rally TV (Internacional)",
        "Real Madrid TV",
        "Red Bull TV (Español)",
        "SuperTennis (Italia)",
        "Teledeporte (1080p)",
        "Tennis Channel (Internacional)",
        "TyC Sports",
        "Waypoint TV (Estados Unidos)",
        "beIN Sports XTRA en Español (1080p)",
        "DW en Español",
        "Euronews en Español",
        "France 24 Español",
        "RDN (1080p) [Not 24/7]",
        "RT en Español",
        "Sky News (Inglés)",
        "Best of Dance TV (1080p)",
        "Clubbing TV (720p)",
        "DanceTV Deep House (1080p)",
        "DanceTV Techno Warehouse (720p)",
        "Farra Play (720p) [Not 24/7]",
        "NOW 70s (720p)",
        "NOW 80s (1080p)",
        "NOW 90s00s (1080p)",
        "NOW Rock (1080p)",
        "Radio Ibiza TV (720p)",
        "Stingray Classica (1080p)",
        "Stingray Greatest Holiday Hits",
        "Stingray Karaoke (1080p)",
        "Stingray Naturescape (1080p)",
        "Stingray Romance Latino (1080p)",
        "Stingray Today's Latin Pop (1080p)",
        "Stingray Urban Beat (1080p)",
        "Studio 29",
        "Totalmusic (1080p)",
        "Totalmusic 2000s (720p)",
        "Totalmusic 80s (720p)",
        "Totalmusic Concerts (720p)",
        "Totalmusic Dance (720p)",
        "Vevo Latino (1080p)",
        "Vevo Pop (1080p)",
        "Andalucía Cocina (1080p)",
        "Azteca Internacional (México)",
        "CBN Español (1080p)",
        "Caracol TV (Colombia)",
        "Chilevisión (Chile)",
        "Discovery Channel (Español)",
        "Discovery Home & Health (Español)",
        "Discovery Kids (Español)",
        "Discovery Turbo (Estados Unidos)",
        "Discovery World (Español)",
        "EWTN Spain & Latin America (720p)",
        "Enlace (720p)",
        "Historia (España)",
        "Historia y Vida",
        "History 2 (Latinoamérica)",
        "History Channel (Latinoamérica)",
        "Investigation Discovery (ID) (Español)",
        "Las Estrellas (México) [Geo-blocked]",
        "Nat Geo Wild (Español)",
        "National Geographic (Español)",
        "NatureTime (Español)",
        "Pluto TV Animales (Español)",
        "Pluto TV Documentales (Español)",
        "Pluto TV Historia (Español)",
        "Pluto TV Naturaleza (Español)",
        "RT Documentary (Internacional)",
        "RTVE La 1",
        "Runtime Cine y Series",
        "Smithsonian Channel (Español)",
        "TV Globo Bahia (Brasil)",
        "Telemundo (Estados Unidos)",
        "Terra Mater WILD (Inglés)",
        "The Fishing & Hunting Channel",
        "Top Gear (24/7)",
        "Universo (Estados Unidos)",
        "Westv on Streaming",
        "WildEarth (Inglés - Safaris en vivo)"
    );

    public static final Set<String> NAMES = new HashSet<>(ORDERED_NAMES);

    private static final Map<String, Integer> ORDER_MAP = new HashMap<>();

    static {
        for (int i = 0; i < ORDERED_NAMES.size(); i++) {
            ORDER_MAP.put(ORDERED_NAMES.get(i).toLowerCase().trim(), i);
        }
    }

    /**
     * Devuelve el índice de orden (0 a 149) del canal según la versión web.
     * Si no pertenece a la lista web (ej. canales de listas M3U personalizadas),
     * devuelve Integer.MAX_VALUE para posicionarlo al final.
     */
    public static int getOrderIndex(String channelName) {
        if (channelName == null) return Integer.MAX_VALUE;
        String normalized = channelName.toLowerCase().trim();
        Integer idx = ORDER_MAP.get(normalized);
        if (idx != null) return idx;

        // Intentar buscando sin sufijo de resolución/calidad (ej: "SNT (1080p)" -> "snt")
        String clean = ChannelDeduplicator.cleanName(channelName).toLowerCase().trim();
        idx = ORDER_MAP.get(clean);
        return idx != null ? idx : Integer.MAX_VALUE;
    }
}
