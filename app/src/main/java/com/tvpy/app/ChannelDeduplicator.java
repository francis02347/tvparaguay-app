package com.tvpy.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Elimina de la vista los canales duplicados que solo difieren en calidad/resolución.
 *
 * Ejemplos de nombres que se consideran el mismo canal:
 *   "GO TV"  vs  "GO TV (720p)"  vs  "GO TV (1080p)"  vs  "GO TV HD"
 *   "HEi Now"  vs  "HEi Now (1080p)"
 *
 * Criterio de selección: se queda con la versión de MAYOR calidad.
 * Orden de preferencia: 4K > 2160p > 1080p > 720p > HD > sin sufijo > SD > 480p > 360p > 240p
 *
 * Los canales originales NO se borran — solo se filtra cuál mostrar.
 */
public class ChannelDeduplicator {

    // Sufijos de calidad que se reconocen y normalizan
    private static final Pattern QUALITY_PATTERN = Pattern.compile(
        "\\s*[\\(\\[]?\\s*(4K|2160p?|1080p?|720p?|480p?|360p?|240p?|HD|FHD|UHD|SD|HQ|LQ|" +
        "Alta|Baja|Alta\\s*Calidad|Baja\\s*Calidad|High|Low|Med(?:ium)?|Standard)" +
        "\\s*[\\)\\]]?\\s*$",
        Pattern.CASE_INSENSITIVE
    );

    // Puntuación de calidad: mayor = mejor
    private static int qualityScore(String name) {
        String upper = name.toUpperCase();
        if (upper.contains("4K") || upper.contains("2160")) return 7;
        if (upper.contains("FHD") || upper.contains("1080")) return 6;
        if (upper.contains("720"))  return 5;
        if (upper.contains("HD") || upper.contains("UHD") || upper.contains("HQ") || upper.contains("ALTA")) return 4;
        if (upper.contains("480"))  return 2;
        if (upper.contains("360"))  return 1;
        if (upper.contains("240") || upper.contains("SD") || upper.contains("LQ") || upper.contains("BAJA")) return 0;
        return 3; // sin sufijo de calidad = neutro, preferido sobre SD pero menor que HD explícito
    }

    /**
     * Recibe la lista completa y devuelve una lista sin duplicados de calidad.
     * El canal con el nombre más limpio/mejor calidad es el que se muestra.
     * El orden original de aparición se mantiene.
     */
    public static List<Channel> deduplicate(List<Channel> input) {
        // 1. Deduplicate by URL first to remove identical streams
        Map<String, Channel> byUrl = new LinkedHashMap<>();
        for (Channel ch : input) {
            String url = ch.getUrl() != null ? ch.getUrl().trim() : "";
            if (url.isEmpty()) continue;
            if (!byUrl.containsKey(url)) {
                byUrl.put(url, ch);
            } else {
                Channel existing = byUrl.get(url);
                boolean existingCorrupt = existing.getName().contains("Ã");
                boolean newCorrupt = ch.getName().contains("Ã");
                if (existingCorrupt && !newCorrupt) {
                    byUrl.put(url, ch);
                } else if (!existingCorrupt && newCorrupt) {
                    // Keep existing
                } else {
                    // Prefer the shorter name as it's typically cleaner
                    if (ch.getName().length() < existing.getName().length()) {
                        byUrl.put(url, ch);
                    }
                }
            }
        }

        // 2. Deduplicate by normalized base name (different qualities)
        Map<String, Channel> best = new LinkedHashMap<>();
        for (Channel ch : byUrl.values()) {
            String baseName = normalizeBase(ch.getName());
            if (!best.containsKey(baseName)) {
                best.put(baseName, ch);
            } else {
                Channel current = best.get(baseName);
                if (qualityScore(ch.getName()) > qualityScore(current.getName())) {
                    best.put(baseName, ch);
                }
            }
        }

        return new ArrayList<>(best.values());
    }

    /**
     * Quita el sufijo de calidad y normaliza espacios para comparar.
     * "GO TV (1080p)" → "go tv"
     * "HEi Now HD"    → "hei now"
     */
    /**
     * Devuelve el nombre del canal sin el sufijo de calidad, conservando el casing original.
     * Ej: "GO TV (1080p)" → "GO TV",  "HEi Now HD" → "HEi Now"
     */
    public static String cleanName(String name) {
        return QUALITY_PATTERN.matcher(name).replaceAll("").trim();
    }

    private static String normalizeBase(String name) {
        return QUALITY_PATTERN.matcher(name).replaceAll("").trim().toLowerCase();
    }
}
