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
        if (PlayerActivity.isPlayerActive) {
            Log.d(TAG, "Reproductor activo, omitiendo descarga de EPG para priorizar streaming.");
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST);
                if (PlayerActivity.isPlayerActive) return;

                long now = System.currentTimeMillis();
                File cacheFile = new File(context.getCacheDir(), CACHE_FILE_NAME);

                // 1. Verificar si la caché local es válida
                boolean useCache = cacheFile.exists() && (now - cacheFile.lastModified() < CACHE_EXPIRY_MS);
                if (!useCache) {
                    if (PlayerActivity.isPlayerActive) return;
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
                                if (PlayerActivity.isPlayerActive) {
                                    conn.disconnect();
                                    return;
                                }
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
                if (cacheFile.exists() && !PlayerActivity.isPlayerActive) {
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
        SimpleDateFormat fallbackFormat = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
        fallbackFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

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
            int counter = 0;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (counter++ % 100 == 0 && PlayerActivity.isPlayerActive) {
                    // Detener si el usuario abre el reproductor para no quitar recursos
                    return;
                }
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
                                long startMs = parseXmltvDateFast(startAttr, fallbackFormat);
                                long stopMs = parseXmltvDateFast(stopAttr, fallbackFormat);
                                if (startMs > 0 && stopMs > 0) {
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
                            } catch (Exception ignored) {
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

    private static long parseXmltvDateFast(String s, SimpleDateFormat fallback) {
        if (s == null || s.length() < 14) return -1;
        try {
            int year = Integer.parseInt(s.substring(0, 4));
            int month = Integer.parseInt(s.substring(4, 6)) - 1;
            int day = Integer.parseInt(s.substring(6, 8));
            int hour = Integer.parseInt(s.substring(8, 10));
            int min = Integer.parseInt(s.substring(10, 12));
            int sec = Integer.parseInt(s.substring(12, 14));

            java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.clear();
            cal.set(year, month, day, hour, min, sec);

            // Ajuste si contiene offset de zona horaria (ej: +0000 o -0300)
            if (s.length() >= 19) {
                char sign = s.charAt(15);
                int tzHour = Integer.parseInt(s.substring(16, 18));
                int tzMin = Integer.parseInt(s.substring(18, 20));
                int offsetMs = (tzHour * 60 + tzMin) * 60 * 1000;
                if (sign == '+') {
                    cal.add(java.util.Calendar.MILLISECOND, -offsetMs);
                } else if (sign == '-') {
                    cal.add(java.util.Calendar.MILLISECOND, offsetMs);
                }
            }
            return cal.getTimeInMillis();
        } catch (Exception e) {
            try {
                Date d = fallback.parse(s);
                return d != null ? d.getTime() : -1;
            } catch (Exception ignored) {
                return -1;
            }
        }
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
