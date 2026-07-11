package com.tvpy.app;

import android.content.Context;
import android.util.Log;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class EpgManager {

    private static final String TAG = "EpgManager";
    private static final String EPG_URL = "https://raw.githubusercontent.com/globetvapp/epg/main/Paraguay/paraguay2.xml.gz";
    private static final String CACHE_FILE_NAME = "epg_cached.xml";
    private static final long CACHE_EXPIRY_MS = 6 * 3600 * 1000; // 6 horas

    public static class Program {
        public String title;
        public String description;
        public long startTime;
        public long endTime;

        public Program(String title, String description, long startTime, long endTime) {
            this.title = title;
            this.description = description;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    private static final Map<String, List<Program>> epgData = new ConcurrentHashMap<>();
    private static boolean isLoaded = false;

    public static boolean isLoaded() {
        return isLoaded;
    }

    /**
     * Devuelve el ID de canal del EPG correspondiente para un canal dado.
     */
    private static String getEpgIdForChannelName(String name) {
        if (name == null) return null;
        String clean = name.trim().toLowerCase();
        if (clean.contains("telefuturo")) return "Telefuturo.py";
        if (clean.equals("snt") || clean.contains("snt ")) return "SNT.py";
        if (clean.contains("unicanal")) return "Unicanal.py";
        if (clean.equals("trece") || clean.contains("canal trece") || clean.equals("rpc")) return "RPC.py";
        if (clean.equals("c9n")) return "C9N.py";
        if (clean.contains("la tele") || clean.equals("latele")) return "latele.py";
        if (clean.contains("paraguay tv")) return "Paraguay TV.py";
        if (clean.contains("tigo sports")) return "Tigo Sports.py";
        return null;
    }

    /**
     * Inicia la descarga y procesamiento del EPG en segundo plano.
     */
    public static void fetchEpgAsync(final Context context) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                long now = System.currentTimeMillis();
                File cacheFile = new File(context.getCacheDir(), CACHE_FILE_NAME);

                // 1. Verificar si la caché local es válida
                boolean useCache = cacheFile.exists() && (now - cacheFile.lastModified() < CACHE_EXPIRY_MS);
                if (!useCache) {
                    Log.d(TAG, "Descargando EPG desde URL remota...");
                    HttpURLConnection conn = (HttpURLConnection) new URL(EPG_URL).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        try (InputStream is = new GZIPInputStream(new BufferedInputStream(conn.getInputStream()));
                             FileOutputStream fos = new FileOutputStream(cacheFile)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, read);
                            }
                        }
                        Log.d(TAG, "EPG descargado y guardado en caché.");
                    } else {
                        Log.w(TAG, "Error del servidor al descargar EPG: " + conn.getResponseCode());
                    }
                    conn.disconnect();
                }

                // 2. Parsear el archivo XML
                if (cacheFile.exists()) {
                    Log.d(TAG, "Procesando archivo EPG...");
                    parseEpgXml(cacheFile);
                    isLoaded = true;
                    Log.d(TAG, "EPG cargado exitosamente. Canales procesados: " + epgData.size());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error al cargar EPG: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Procesa el archivo XMLTV utilizando un XmlPullParser de bajo consumo.
     */
    private static void parseEpgXml(File file) throws Exception {
        Map<String, List<Program>> tempData = new ConcurrentHashMap<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));

        long now = System.currentTimeMillis();
        // Guardamos hasta 36 horas a futuro
        long timeLimit = now + 36 * 3600 * 1000L;

        try (FileInputStream fis = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(fis, "UTF-8");

            int eventType = parser.getEventType();
            String currentChannel = null;
            String startAttr = null;
            String stopAttr = null;
            String currentTag = null;
            String title = null;
            String desc = null;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    currentTag = parser.getName();
                    if ("programme".equals(currentTag)) {
                        startAttr = parser.getAttributeValue(null, "start");
                        stopAttr = parser.getAttributeValue(null, "stop");
                        currentChannel = parser.getAttributeValue(null, "channel");
                        title = null;
                        desc = null;
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if ("title".equals(currentTag)) {
                        title = parser.getText();
                    } else if ("desc".equals(currentTag)) {
                        desc = parser.getText();
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if ("programme".equals(tag)) {
                        if (currentChannel != null && title != null && startAttr != null && stopAttr != null) {
                            try {
                                Date startDate = format.parse(startAttr);
                                Date stopDate = format.parse(stopAttr);
                                if (startDate != null && stopDate != null) {
                                    long startMs = startDate.getTime();
                                    long stopMs = stopDate.getTime();

                                    // Filtrar programas antiguos o demasiado lejanos en el futuro
                                    if (stopMs >= now && startMs <= timeLimit) {
                                        List<Program> list = tempData.get(currentChannel);
                                        if (list == null) {
                                            list = new ArrayList<>();
                                            tempData.put(currentChannel, list);
                                        }
                                        list.add(new Program(title, desc != null ? desc : "", startMs, stopMs));
                                    }
                                }
                            } catch (Exception e) {
                                // Ignorar errores de formato de fecha individuales
                            }
                        }
                        currentChannel = null;
                        startAttr = null;
                        stopAttr = null;
                        title = null;
                        desc = null;
                    }
                    currentTag = null;
                }
                eventType = parser.next();
            }
        }

        // Ordenar cronológicamente las listas de cada canal
        for (Map.Entry<String, List<Program>> entry : tempData.entrySet()) {
            Collections.sort(entry.getValue(), (p1, p2) -> Long.compare(p1.startTime, p2.startTime));
        }

        epgData.clear();
        epgData.putAll(tempData);
    }

    /**
     * Obtiene el programa en emisión actual.
     */
    public static Program getCurrentProgram(String channelName) {
        String epgId = getEpgIdForChannelName(channelName);
        if (epgId == null) return null;
        List<Program> list = epgData.get(epgId);
        if (list == null) return null;
        long now = System.currentTimeMillis();
        for (Program p : list) {
            if (now >= p.startTime && now < p.endTime) {
                return p;
            }
        }
        return null;
    }

    /**
     * Obtiene el siguiente programa en la lista.
     */
    public static Program getNextProgram(String channelName) {
        String epgId = getEpgIdForChannelName(channelName);
        if (epgId == null) return null;
        List<Program> list = epgData.get(epgId);
        if (list == null) return null;
        long now = System.currentTimeMillis();
        for (int i = 0; i < list.size(); i++) {
            Program p = list.get(i);
            if (now >= p.startTime && now < p.endTime) {
                if (i + 1 < list.size()) {
                    return list.get(i + 1);
                }
                break;
            }
        }
        return null;
    }
}
