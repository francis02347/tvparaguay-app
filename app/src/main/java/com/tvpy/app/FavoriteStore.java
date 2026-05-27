package com.tvpy.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * Persiste la lista de canales favoritos (por URL) en SharedPreferences.
 */
public class FavoriteStore {

    private static final String PREFS_NAME = "tvpy_favorites";
    private static final String KEY_FAVS   = "fav_urls";

    public static Set<String> loadFavorites(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_FAVS, new HashSet<>()));
    }

    public static void saveFavorites(Context ctx, Set<String> urls) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putStringSet(KEY_FAVS, urls).apply();
    }

    public static boolean isFavorite(Context ctx, String url) {
        return loadFavorites(ctx).contains(url);
    }

    public static void toggle(Context ctx, String url) {
        Set<String> favs = loadFavorites(ctx);
        if (favs.contains(url)) favs.remove(url);
        else favs.add(url);
        saveFavorites(ctx, favs);
    }
}
