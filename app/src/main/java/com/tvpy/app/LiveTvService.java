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
    private static final String UPCOMING_URL = "https://livetv.sx/es/allupcomingsports/1/";
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final String USER_AGENT = 
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static void getTopMatches(Callback<List<LiveEvent>> callback) {
        EXECUTOR.execute(() -> {
            try {
                String html = fetchHtml(UPCOMING_URL);
                List<LiveEvent> events = parseTopMatches(html);
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

    private static List<LiveEvent> parseTopMatches(String html) {
        List<LiveEvent> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        int pos = html.toLowerCase().indexOf("top matches today");
        if (pos == -1) return result;

        int endPos = Math.min(pos + 25000, html.length());
        String section = html.substring(pos, endPos);

        Pattern pattern = Pattern.compile(
            "href=[\"'](/es/eventinfo/(\\d+)[^\"']*)[\"'][^>]*>(.*?)</a>.*?" +
            "(?:<img[^>]*live\\.gif[^>]*>)?.*?" +
            "<span class=\"evdesc\">(.*?)</span>",
            Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(section);
        while (matcher.find()) {
            String path = matcher.group(1);
            String eid = matcher.group(2);
            String rawTitle = matcher.group(3);
            String desc = matcher.group(4);

            if (seenIds.contains(eid)) continue;
            seenIds.add(eid);

            String title = rawTitle.replaceAll("&ndash;|&mdash;", "-")
                                   .replaceAll("<[^>]+>", "")
                                   .trim();

            boolean isLive = false;
            int eidIdx = section.indexOf(eid);
            if (eidIdx != -1) {
                int checkRange = Math.min(eidIdx + 500, section.length());
                String snippet = section.substring(eidIdx, checkRange);
                isLive = snippet.contains("live.gif");
            }

            String time = "";
            String tournament = "";
            if (desc != null) {
                String cleanDesc = desc.replaceAll("<br\\s*/?>", "\n").trim();
                String[] lines = cleanDesc.split("\n");
                if (lines.length > 0) time = lines[0].trim();
                if (lines.length > 1) {
                    tournament = lines[1].trim()
                                         .replaceAll("^\\(|\\)$", "")
                                         .trim();
                }
            }

            String fullUrl = BASE_URL + path;
            result.add(new LiveEvent(eid, title, time, tournament, "", fullUrl, isLive));
        }

        return result;
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
