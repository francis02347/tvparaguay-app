package com.tvpy.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Proveedor de Canales para la versión de la Google Play Store.
 * Devuelve una lista de canales vacía, actuando puramente como un reproductor vacío de IPTV
 * de acuerdo con las Políticas del Programa para Desarrolladores de Google Play.
 */
public class ChannelData {

    public static List<Channel> getChannels(android.content.Context context) {
        // Reproductor completamente vacío por políticas de derechos de autor.
        return new ArrayList<>();
    }

    public static List<Channel> getChannels() {
        return new ArrayList<>();
    }
}
