package com.tvpy.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Clase estática que actúa como "puente" entre MainActivity y PlayerActivity.
 * Evita el TransactionTooLargeException que ocurre al pasar listas grandes por Intent.
 * Los datos viven en memoria mientras la app está abierta.
 */
public class ChannelSession {

    private static List<Channel> channels = new ArrayList<>();
    private static int startIndex = 0;

    public static void set(List<Channel> list, int index) {
        channels = new ArrayList<>(list);
        startIndex = index;
    }

    public static List<Channel> getChannels() {
        return channels;
    }

    public static int getStartIndex() {
        return startIndex;
    }
}
