package com.tvpy.app;

import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LiveTvService {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private static final String BASE_URL = "https://livetv.sx";
    private static final String ALL_UPCOMING_URL = "https://livetv.sx/es/allupcoming/";
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final String USER_AGENT = 
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static void getAllUpcoming(Callback<List<LiveEvent>> callback) {
        EXECUTOR.execute(() -> {
            try {
                String html = fetchHtml(ALL_UPCOMING_URL);
                List<LiveEvent> events = parseAllUpcoming(html);
                MAIN_HANDLER.post(() -> callback.onSuccess(events));
            } catch (Exception e) {
                MAIN_HANDLER.post(() -> callback.onError(e));
            }
        });
    }

    public static void getEventLinks(String eventUrl, Callback<List<LiveEventLink>> callback) {
        EXECUTOR.execute(() -> {
            try {
                String html = fetchHtml(eventUrl);
                List<LiveEventLink> links = parseEventLinks(html);
                MAIN_HANDLER.post(() -> callback.onSuccess(links));
            } catch (Exception e) {
                MAIN_HANDLER.post(() -> callback.onError(e));
            }
        });
    }

    private static String fetchHtml(String targetUrl) throws Exception {
        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Referer", BASE_URL + "/");
        conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(12000);

        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK && code != 301 && code != 302) {
            throw new Exception("HTTP Error: " + code);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    private static List<LiveEvent> parseAllUpcoming(String html) {
        List<LiveEvent> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        Pattern tablePattern = Pattern.compile("<table cellpadding=1 cellspacing=2 width=\"100%\">.*?</table>", Pattern.DOTALL);
        Matcher tableMatcher = tablePattern.matcher(html);

        while (tableMatcher.find()) {
            String tableHtml = tableMatcher.group();

            Matcher linkMatcher = Pattern.compile("href=[\"'](/es/eventinfo/(\\d+)[^\"']*)[\"'][^>]*>([^<]+)</a>").matcher(tableHtml);
            if (!linkMatcher.find()) continue;

            String path = linkMatcher.group(1);
            String eid = linkMatcher.group(2);
            String rawTitle = linkMatcher.group(3);

            if (seenIds.contains(eid)) continue;
            seenIds.add(eid);

            String title = rawTitle.replaceAll("&ndash;|&mdash;", "-")
                                   .replaceAll("<[^>]+>", "")
                                   .trim();

            String alt = "";
            Matcher altMatcher = Pattern.compile("<img[^>]*alt=[\"']([^\"']+)[\"']").matcher(tableHtml);
            if (altMatcher.find()) {
                alt = decodeHtmlEntities(altMatcher.group(1));
            }

            String desc = "";
            Matcher descMatcher = Pattern.compile("<span class=[\"']evdesc[\"']>(.*?)</span>", Pattern.DOTALL).matcher(tableHtml);
            if (descMatcher.find()) {
                desc = decodeHtmlEntities(descMatcher.group(1));
            }

            boolean isLive = tableHtml.contains("live.gif");

            String sport = normalizeSport(alt, desc);
            String tournament = extractTournament(alt, desc);
            String time = extractTime(desc);

            String fullUrl = BASE_URL + path;
            result.add(new LiveEvent(eid, title, time, tournament, sport, "", fullUrl, isLive));
        }

        return result;
    }

    private static String normalizeSport(String alt, String desc) {
        String combined = (alt + " " + desc).toLowerCase();
        if (combined.contains("fútbol") || combined.contains("futbol") || combined.contains("soccer") 
                || combined.contains("mls") || combined.contains("serie b") || combined.contains("concacaf") 
                || combined.contains("libertadores") || combined.contains("champions")) {
            return "Fútbol";
        }
        if (combined.contains("baloncesto") || combined.contains("basket") || combined.contains("nba") 
                || combined.contains("lnbp") || combined.contains("lbp")) {
            return "Baloncesto";
        }
        if (combined.contains("tenis") || combined.contains("tennis") || combined.contains("atp") 
                || combined.contains("wta") || combined.contains("wtt")) {
            return "Tenis";
        }
        if (combined.contains("hockey") || combined.contains("nhl")) {
            return "Hockey";
        }
        if (combined.contains("combate") || combined.contains("box") || combined.contains("mma") 
                || combined.contains("ufc") || combined.contains("judo")) {
            return "Combate";
        }
        if (combined.contains("béisbol") || combined.contains("beisbol") || combined.contains("baseball") 
                || combined.contains("mlb")) {
            return "Béisbol";
        }
        if (combined.contains("voleibol") || combined.contains("voley")) {
            return "Voleibol";
        }
        if (combined.contains("motor") || combined.contains("f1") || combined.contains("fórmula") 
                || combined.contains("formula") || combined.contains("moto")) {
            return "Motor";
        }
        if (combined.contains("balonmano") || combined.contains("handball")) {
            return "Balonmano";
        }
        if (combined.contains("críquet") || combined.contains("cricket")) {
            return "Críquet";
        }
        if (!alt.isEmpty() && alt.contains(".")) {
            return alt.split("\\.")[0].trim();
        }
        return "Otros Deportes";
    }

    private static String extractTournament(String alt, String desc) {
        if (!alt.isEmpty() && alt.contains(".")) {
            String[] parts = alt.split("\\.", 2);
            if (parts.length > 1) {
                return parts[1].trim();
            }
        }
        if (!desc.isEmpty()) {
            Matcher m = Pattern.compile("\\((.*?)\\)").matcher(desc);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return "";
    }

    private static String extractTime(String desc) {
        if (desc.isEmpty()) return "";
        Matcher m = Pattern.compile("(\\d+:\\d+)").matcher(desc);
        if (m.find()) {
            return m.group(1);
        }
        return desc.replaceAll("<[^>]+>", "").trim();
    }

    private static String decodeHtmlEntities(String input) {
        if (input == null) return "";
        return input.replace("&#250;", "ú")
                    .replace("&#237;", "í")
                    .replace("&#243;", "ó")
                    .replace("&#225;", "á")
                    .replace("&#233;", "é")
                    .replace("&#241;", "ñ")
                    .replace("&ntilde;", "ñ")
                    .replace("&nbsp;", " ")
                    .replace("&ndash;", "-")
                    .replace("&mdash;", "-");
    }

    private static List<LiveEventLink> parseEventLinks(String html) {
        List<LiveEventLink> links = new ArrayList<>();
        Pattern rowPattern = Pattern.compile("<tr[^>]*>.*?webplayer2\\.php.*?</tr>", Pattern.DOTALL);
        Matcher rowMatcher = rowPattern.matcher(html);

        while (rowMatcher.find()) {
            String row = rowMatcher.group();

            Matcher urlMatcher = Pattern.compile("href=[\"']([^\"']*webplayer2\\.php\\?[^\"']*)[\"']").matcher(row);
            if (!urlMatcher.find()) continue;

            String linkPath = urlMatcher.group(1);
            if (!linkPath.startsWith("/")) linkPath = "/" + linkPath;
            String fullLinkUrl = BASE_URL + linkPath;

            String flagId = "";
            Matcher flagMatcher = Pattern.compile("linkflag/(\\d+)\\.png").matcher(row);
            if (flagMatcher.find()) {
                flagId = flagMatcher.group(1);
            }

            String language = "";
            Matcher langMatcher = Pattern.compile("<img[^>]*linkflag/[^>]*title=[\"']([^\"']+)[\"']").matcher(row);
            if (langMatcher.find()) {
                language = langMatcher.group(1);
            }

            String bitrate = "";
            Matcher bitMatcher = Pattern.compile("class=[\"']bitrate[\"'][^>]*>([^<]+)<").matcher(row);
            if (bitMatcher.find()) {
                bitrate = bitMatcher.group(1).trim();
            }

            String quality = "";
            Matcher rateMatcher = Pattern.compile("id=[\"']rali\\d+[\"'][^>]*>(.*?)</div>", Pattern.DOTALL).matcher(row);
            if (rateMatcher.find()) {
                quality = rateMatcher.group(1).replaceAll("<[^>]+>", "").replace("&nbsp;", "").trim();
            }

            String playerType = "Web";
            if (fullLinkUrl.contains("t=acestream")) {
                playerType = "AceStream";
            }

            links.add(new LiveEventLink(fullLinkUrl, flagId, language, bitrate, quality, playerType));
        }

        return links;
    }
}
