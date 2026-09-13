package com.tvpy.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Gestiona la persistencia de la señal preferida por canal en SharedPreferences.
 * Permite recordar qué señal (Señal 1, Señal 2, etc.) eligió el usuario manualmente
 * para cada canal específico.
 */
public class SignalPreferenceManager {

    private static final String PREF_NAME = "tvpy_signal_prefs";
    private static final String KEY_PREFIX = "pref_sig_";

    public static int getPreferredSignalIndex(Context context, String channelName) {
        if (context == null || channelName == null) return 0;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_PREFIX + normalizeKey(channelName), 0);
    }

    public static void setPreferredSignalIndex(Context context, String channelName, int index) {
        if (context == null || channelName == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_PREFIX + normalizeKey(channelName), Math.max(0, index)).apply();
    }

    public static void clearSignalPreference(Context context, String channelName) {
        if (context == null || channelName == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_PREFIX + normalizeKey(channelName)).apply();
    }

    private static String normalizeKey(String channelName) {
        return ChannelDeduplicator.cleanName(channelName).trim().toLowerCase();
    }
}
