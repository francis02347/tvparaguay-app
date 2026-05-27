package com.tvpy.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Guarda y carga los canales importados desde M3U usando SharedPreferences.
 * Los canales se persisten en el dispositivo entre sesiones.
 */
public class ChannelStore {

    private static final String PREFS_NAME = "tvpy_channels";
    private static final String KEY_M3U_CHANNELS = "m3u_channels";

    /** Guarda la lista de canales M3U importados */
    public static void saveM3uChannels(Context ctx, List<Channel> channels) {
        try {
            JSONArray arr = new JSONArray();
            for (Channel ch : channels) {
                JSONObject obj = new JSONObject();
                obj.put("name", ch.getName());
                obj.put("url", ch.getUrl());
                obj.put("emoji", ch.getEmoji());
                obj.put("category", ch.getCategory());
                obj.put("country", ch.getCountry());
                obj.put("color", ch.getBackgroundColor());
                arr.put(obj);
            }
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_M3U_CHANNELS, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Carga los canales M3U guardados previamente */
    public static List<Channel> loadM3uChannels(Context ctx) {
        List<Channel> channels = new ArrayList<>();
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_M3U_CHANNELS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                channels.add(new Channel(
                        obj.getString("name"),
                        obj.getString("url"),
                        obj.getString("emoji"),
                        obj.getString("category"),
                        obj.optString("country", ""),
                        obj.getInt("color")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return channels;
    }

    /** Borra todos los canales M3U guardados */
    public static void clearM3uChannels(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_M3U_CHANNELS).apply();
    }

    /** Indica si hay canales M3U guardados */
    public static boolean hasM3uChannels(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_M3U_CHANNELS, "[]");
        return !json.equals("[]");
    }
}
