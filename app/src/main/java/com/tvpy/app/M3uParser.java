package com.tvpy.app;

import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

public class M3uParser {

    private static final int[] COLORS = {
        Color.parseColor("#1A237E"), Color.parseColor("#B71C1C"),
        Color.parseColor("#1B5E20"), Color.parseColor("#E65100"),
        Color.parseColor("#4A148C"), Color.parseColor("#006064"),
        Color.parseColor("#880E4F"), Color.parseColor("#0D47A1"),
        Color.parseColor("#004D40"), Color.parseColor("#37474F"),
    };

    public static List<Channel> parse(String m3uContent) { return parse(m3uContent, ""); }

    public static List<Channel> parse(String m3uContent, String defaultCountry) {
        List<Channel> channels = new ArrayList<>();
        if (m3uContent == null || m3uContent.isEmpty()) return channels;

        String[] lines = m3uContent.split("\n");
        int colorIdx = 0;

        String name = null, category = "General", emoji = "📺", country = "";

        for (String raw : lines) {
            String line = raw.trim();

            if (line.startsWith("#EXTINF")) {
                // Nombre visible (después de la última coma)
                int comma = line.lastIndexOf(',');
                name = comma >= 0 && comma < line.length() - 1
                    ? line.substring(comma + 1).trim() : "";

                // Atributos
                String tvgName  = extractAttr(line, "tvg-name");
                category        = extractAttr(line, "group-title");
                country = extractAttr(line, "tvg-country");
                if (country.isEmpty()) country = defaultCountry;

                if (category.contains(";")) {
                    String[] parts = category.split(";");
                    if (parts.length > 0) {
                        category = parts[0].trim();
                    }
                }

                if (category.isEmpty())  category = "General";
                if (name.isEmpty())      name = tvgName.isEmpty() ? "Canal " + (channels.size()+1) : tvgName;
                emoji = emojiForCategory(category);

            } else if (!line.startsWith("#") && !line.isEmpty() && name != null) {
                channels.add(new Channel(
                    name, line, emoji, category, country,
                    COLORS[colorIdx++ % COLORS.length]
                ));
                name = null; category = "General"; country = ""; emoji = "📺";
            }
        }
        return channels;
    }

    private static String extractAttr(String line, String attr) {
        String search = attr + "=\"";
        int start = line.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = line.indexOf("\"", start);
        return end < 0 ? "" : line.substring(start, end).trim();
    }

    private static String emojiForCategory(String cat) {
        String l = cat.toLowerCase();
        if (l.contains("notic") || l.contains("news"))          return "📰";
        if (l.contains("deport") || l.contains("sport"))        return "⚽";
        if (l.contains("pelicul") || l.contains("movie"))       return "🎬";
        if (l.contains("music"))                                 return "🎵";
        if (l.contains("niño") || l.contains("kid"))             return "🧸";
        if (l.contains("docu"))                                  return "🎥";
        if (l.contains("religi"))                                return "✝️";
        if (l.contains("entret") || l.contains("variedades"))   return "🌟";
        return "📺";
    }
}
