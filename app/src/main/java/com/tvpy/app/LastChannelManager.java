package com.tvpy.app;

import android.content.Context;
import android.content.SharedPreferences;

public class LastChannelManager {
    private static final String PREFS_NAME = "LastChannelPrefs";
    private static final String KEY_LAST_CHANNEL_URL = "last_channel_url";

    public static void saveLastChannel(Context context, String url) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_CHANNEL_URL, url).apply();
    }

    public static String getLastChannel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_CHANNEL_URL, null);
    }
}
