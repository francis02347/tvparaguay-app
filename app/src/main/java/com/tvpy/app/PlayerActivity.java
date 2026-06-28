package com.tvpy.app;

import android.animation.AnimatorInflater;
import android.net.Uri;
import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.TextView;
import android.app.PictureInPictureParams;
import android.util.Rational;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.app.RemoteAction;
import android.app.PendingIntent;
import android.annotation.TargetApi;
import android.content.res.Configuration;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.NonNull;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.media3.ui.PlayerView;

import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    // Cambiar a true una vez que configures AdMob y quieras activar la función premium
    private static final boolean ENABLE_PREMIUM_BG_AUDIO_PLAYSTORE = false;

    // ─── Vistas ──────────────────────────────────────────────────────────────
    private PlayerView playerView;
    private View loadingContainer;
    private View dot1, dot2, dot3;
    private AnimatorSet anim1, anim2, anim3;

    private TextView tvChannelName;
    private View topBar;
    private TextView btnFavorite;

    private View overlayContainer;
    private TextView overlayEmoji, overlayName, overlayCategory, overlayHint;
    private View errorScreen;
    private TextView tvErrorChannelName;
    private android.widget.Button btnRetry;
    private int autoRetryCount = 0;
    private Runnable autoRetryRunnable;
    private static final int AUTO_RETRY_DELAY_MS = 3000;

    private View sidePanel;
    private RecyclerView rvSideChannels;
    private ChannelAdapter sideAdapter;

    // ─── Estado ───────────────────────────────────────────────────────────────
    private ExoPlayer player;
    private List<Channel> channelList;
    private int currentIndex = 0;
    private GestureDetector gestureDetector;
    private MapHeaderDataSourceFactory dataSourceFactory;
    private BroadcastReceiver backgroundAudioReceiver;
    private String activeStreamUrl;
    private String activeCookie;
    private String activeReferer;
    private String activeUserAgent;

    // AdMob y Reproducción en Segundo Plano Premium
    private RewardedAd rewardedAd;
    private boolean isLoadingAd = false;
    private boolean pendingUnlockBgAudio = false;
    private android.app.AlertDialog adLoadingDialog;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideOverlayRunnable;
    private Runnable hideTopBarRunnable;

    private static final int  SWIPE_MIN_DISTANCE  = 100;
    private static final int  SWIPE_MAX_OFF_PATH  = 200;
    private static final int  SWIPE_THRESHOLD_VEL = 150;
    private static final long OVERLAY_DURATION_MS = 2500;
    private static final long TOPBAR_AUTOHIDE_MS  = 3000;

    // ─── onCreate ─────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);
        enterImmersiveMode();
        checkNotificationPermission();

        if (BuildConfig.IS_PLAY_STORE && ENABLE_PREMIUM_BG_AUDIO_PLAYSTORE) {
            loadRewardedAd();
        }

        channelList  = ChannelSession.getChannels();
        currentIndex = ChannelSession.getStartIndex();

        if (channelList == null || channelList.isEmpty()) {
            finishAndGoHome();
            return;
        }

        stopService(new Intent(this, BackgroundAudioService.class));

        playerView         = findViewById(R.id.playerView);
        loadingContainer   = findViewById(R.id.loadingContainer);
        dot1               = findViewById(R.id.dot1);
        dot2               = findViewById(R.id.dot2);
        dot3               = findViewById(R.id.dot3);
        tvChannelName      = findViewById(R.id.tvChannelName);
        topBar             = findViewById(R.id.topBar);
        btnFavorite        = findViewById(R.id.btnFavorite);
        overlayContainer   = findViewById(R.id.overlayContainer);
        overlayEmoji       = findViewById(R.id.overlayEmoji);
        overlayName        = findViewById(R.id.overlayName);
        overlayCategory    = findViewById(R.id.overlayCategory);
        overlayHint        = findViewById(R.id.overlayHint);
        errorScreen        = findViewById(R.id.errorScreen);
        tvErrorChannelName = findViewById(R.id.tvErrorChannelName);
        btnRetry           = findViewById(R.id.btnRetry);
        sidePanel          = findViewById(R.id.sidePanel);
        rvSideChannels     = findViewById(R.id.rvSideChannels);

        if (rvSideChannels != null) {
            rvSideChannels.setLayoutManager(new LinearLayoutManager(this));
            sideAdapter = new ChannelAdapter(channelList, channel -> {
                int idx = channelList.indexOf(channel);
                if (idx >= 0) {
                    loadChannel(idx);
                }
                hideSidePanel();
            });
            rvSideChannels.setAdapter(sideAdapter);
        }

        // Animaciones de puntos
        anim1 = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.anim.dot_pulse_1);
        anim2 = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.anim.dot_pulse_2);
        anim3 = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.anim.dot_pulse_3);
        anim1.setTarget(dot1);
        anim2.setTarget(dot2);
        anim3.setTarget(dot3);

        // Botones
        View btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finishAndGoHome());
        btnBack.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).start();
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }
        });

        btnRetry.setOnClickListener(v -> {
            if (autoRetryRunnable != null) {
                handler.removeCallbacks(autoRetryRunnable);
                autoRetryRunnable = null;
            }
            loadChannel(currentIndex);
        });

        btnFavorite.setOnClickListener(v -> toggleFavorite());
        btnFavorite.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).start();
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }
        });

        // Gestos (tap → topBar; fling → navegar)
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (isSidePanelVisible()) {
                    hideSidePanel();
                } else {
                    toggleTopBar();
                }
                return true;
            }
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (isSidePanelVisible()) {
                    hideSidePanel();
                    return true;
                }
                if (e1 == null || e2 == null) return false;
                float dy = Math.abs(e2.getY() - e1.getY());
                float dx = e2.getX() - e1.getX();
                if (dy > SWIPE_MAX_OFF_PATH) return false;
                if (Math.abs(dx) > SWIPE_MIN_DISTANCE && Math.abs(vx) > SWIPE_THRESHOLD_VEL) {
                    navigateToChannel(dx < 0 ? currentIndex + 1 : currentIndex - 1);
                    return true;
                }
                return false;
            }
        });

        playerView.setUseController(false);
        playerView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

        setupPlayer();
        registerBackgroundAudioReceiver();
        if (!channelList.isEmpty()) loadChannel(currentIndex);
    }

    private void finishAndGoHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    // ─── ExoPlayer ────────────────────────────────────────────────────────────

    private void setupPlayer() {
        DataSource.Factory baseFactory = new DefaultDataSource.Factory(this);
        dataSourceFactory = new MapHeaderDataSourceFactory(baseFactory);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                .build();

        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    showLoading();
                } else if (state == Player.STATE_READY) {
                    hideLoading();
                    hideErrorScreen();
                    autoRetryCount = 0;
                    player.play();
                    updatePipParams();
                } else if (state == Player.STATE_ENDED) {
                    loadChannel(currentIndex);
                } else if (state == Player.STATE_IDLE) {
                    hideLoading();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                hideLoading();
                String name = channelList != null && currentIndex < channelList.size()
                        ? channelList.get(currentIndex).getName() : "";
                showErrorScreen(name);
            }
        });
    }

    private void loadChannel(int index) {
        if (channelList == null || index < 0 || index >= channelList.size()) return;
        currentIndex = index;
        Channel ch = channelList.get(index);

        LastChannelManager.saveLastChannel(this, ch.getUrl());

        hideErrorScreen();
        tvChannelName.setText(ChannelDeduplicator.cleanName(ch.getName()));

        showLoading();
        showChannelOverlay(ch, index);
        updateFavoriteButton(ch.getUrl());

        String rawUrl = ch.getUrl();
        if (rawUrl == null) rawUrl = "";

        String cleanUrl = rawUrl;
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        int pipeIdx = rawUrl.indexOf('|');
        if (pipeIdx >= 0) {
            cleanUrl = rawUrl.substring(0, pipeIdx).trim();
            String headersPart = rawUrl.substring(pipeIdx + 1).trim();
            String[] pairs = headersPart.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    headers.put(Uri.decode(kv[0]), Uri.decode(kv[1]));
                }
            }
        }

        if (cleanUrl.startsWith("dailymotion://")) {
            String videoId = cleanUrl.substring("dailymotion://".length()).trim();
            resolveDailymotionAndPlay(videoId, ch);
            return;
        }

        if (cleanUrl.startsWith("desdeparaguay://")) {
            String channelId = cleanUrl.substring("desdeparaguay://".length()).trim();
            resolveDesdeParaguayAndPlay(channelId, ch);
            return;
        }

        if (cleanUrl.startsWith("youtube://")) {
            String channelPath = cleanUrl.substring("youtube://".length()).trim();
            resolveYouTubeAndPlay(channelPath, ch);
            return;
        }

        activeStreamUrl = cleanUrl;
        activeCookie = null;
        activeReferer = null;
        activeUserAgent = null;
        for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
            if ("cookie".equalsIgnoreCase(entry.getKey())) {
                activeCookie = entry.getValue();
            } else if ("referer".equalsIgnoreCase(entry.getKey())) {
                activeReferer = entry.getValue();
            } else if ("user-agent".equalsIgnoreCase(entry.getKey())) {
                activeUserAgent = entry.getValue();
            }
        }

        if (dataSourceFactory != null) {
            dataSourceFactory.setHeaders(headers);
        }

        boolean validScheme = cleanUrl.startsWith("http://")
                || cleanUrl.startsWith("https://")
                || cleanUrl.startsWith("rtmp://")
                || cleanUrl.startsWith("rtsp://");

        if (!validScheme || cleanUrl.isEmpty()) {
            showErrorScreen(ch.getName());
            return;
        }

        try {
            Uri uri = Uri.parse(cleanUrl);
            MediaItem mediaItem = MediaItem.fromUri(uri);
            player.stop();
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true);
        } catch (Exception e) {
            hideLoading();
            showErrorScreen(ch.getName());
        }
    }

    private void resolveDailymotionAndPlay(final String videoIdInput, final Channel ch) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String videoId = videoIdInput;
                    String referer = "https://www.abc.com.py/"; // default referer
                    String embedder = null;

                    int qIdx = videoId.indexOf('?');
                    if (qIdx >= 0) {
                        String query = videoId.substring(qIdx + 1);
                        videoId = videoId.substring(0, qIdx);
                        String[] pairs = query.split("&");
                        for (String pair : pairs) {
                            String[] kv = pair.split("=", 2);
                            if (kv.length == 2) {
                                String key = Uri.decode(kv[0]);
                                String val = Uri.decode(kv[1]);
                                if ("referer".equalsIgnoreCase(key)) {
                                    referer = val;
                                } else if ("embedder".equalsIgnoreCase(key)) {
                                    embedder = val;
                                }
                            }
                        }
                    }

                    String metadataUrl = "https://www.dailymotion.com/player/metadata/video/" + videoId;
                    if (embedder != null && !embedder.isEmpty()) {
                        metadataUrl += "?embedder=" + Uri.encode(embedder);
                    }

                    java.net.URL url = new java.net.URL(metadataUrl);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    conn.setRequestProperty("Referer", referer);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        // Extract cookies
                        StringBuilder cookieBuilder = new StringBuilder();
                        java.util.List<String> cookieHeaders = conn.getHeaderFields().get("Set-Cookie");
                        if (cookieHeaders != null) {
                            for (String cookie : cookieHeaders) {
                                int semiIdx = cookie.indexOf(';');
                                String pair = (semiIdx >= 0) ? cookie.substring(0, semiIdx) : cookie;
                                if (cookieBuilder.length() > 0) {
                                    cookieBuilder.append("; ");
                                }
                                cookieBuilder.append(pair);
                            }
                        }
                        final String cookieStr = cookieBuilder.toString();

                        java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            sb.append(line);
                        }
                        in.close();

                        org.json.JSONObject json = new org.json.JSONObject(sb.toString());
                        org.json.JSONObject qualities = json.optJSONObject("qualities");
                        if (qualities != null) {
                            org.json.JSONArray autoArray = qualities.optJSONArray("auto");
                            if (autoArray != null && autoArray.length() > 0) {
                                org.json.JSONObject autoObj = autoArray.getJSONObject(0);
                                final String streamUrl = autoObj.optString("url");
                                if (streamUrl != null && !streamUrl.isEmpty()) {
                                    final String finalReferer = referer;
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            playResolvedUrl(streamUrl, cookieStr, finalReferer, ch);
                                        }
                                    });
                                    return;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        hideLoading();
                        showErrorScreen(ch.getName());
                    }
                });
            }
        }).start();
    }

    private void resolveDesdeParaguayAndPlay(final String channelId, final Channel ch) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String urlStr = "https://gentv.desdepylabs.com/External/heinetwork/" + channelId;
                    java.net.URL url = new java.net.URL(urlStr);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    conn.setRequestProperty("Origin", "https://gen.com.py");
                    conn.setRequestProperty("Referer", "https://gen.com.py/");

                    if (conn.getResponseCode() == java.net.HttpURLConnection.HTTP_OK) {
                        java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(), "UTF-8")
                        );
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        reader.close();

                        String html = sb.toString();
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                            "(https?://[^\\s\"'\\>]+?\\.m3u8[^\\s\"'\\>]*)"
                        );
                        java.util.regex.Matcher matcher = pattern.matcher(html);
                        String resolvedUrl = null;
                        while (matcher.find()) {
                            String found = matcher.group(1);
                            if (found.contains("desdeparaguay.net") && found.contains("k=")) {
                                resolvedUrl = found;
                                break;
                            }
                        }

                        if (resolvedUrl != null) {
                            resolvedUrl = resolvedUrl.replace("&amp;", "&");
                            final String finalUrl = resolvedUrl;
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    playResolvedUrl(finalUrl, null, "https://gen.com.py/", ch);
                                }
                            });
                            return;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        hideLoading();
                        showErrorScreen(ch.getName());
                    }
                });
            }
        }).start();
    }

    private void resolveYouTubeAndPlay(final String youtubePath, final Channel ch) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String urlStr = "https://www.youtube.com/" + youtubePath + "/live";
                    java.net.URL url = new java.net.URL(urlStr);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8");

                    if (conn.getResponseCode() == java.net.HttpURLConnection.HTTP_OK) {
                        java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(), "UTF-8")
                        );
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        reader.close();

                        String html = sb.toString();
                        String hlsUrl = extractValue(html, "\"hlsManifestUrl\"");
                        
                        if (hlsUrl == null || hlsUrl.isEmpty()) {
                            // Try fallback: extract videoId and fetch watch page
                            String videoId = extractValue(html, "\"videoId\"");
                            if (videoId != null && !videoId.isEmpty()) {
                                String watchUrlStr = "https://www.youtube.com/watch?v=" + videoId;
                                java.net.URL watchUrl = new java.net.URL(watchUrlStr);
                                java.net.HttpURLConnection watchConn = (java.net.HttpURLConnection) watchUrl.openConnection();
                                watchConn.setConnectTimeout(8000);
                                watchConn.setReadTimeout(8000);
                                watchConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                                watchConn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8");
                                
                                if (watchConn.getResponseCode() == java.net.HttpURLConnection.HTTP_OK) {
                                    java.io.BufferedReader watchReader = new java.io.BufferedReader(
                                        new java.io.InputStreamReader(watchConn.getInputStream(), "UTF-8")
                                    );
                                    StringBuilder watchSb = new StringBuilder();
                                    String watchLine;
                                    while ((watchLine = watchReader.readLine()) != null) {
                                        watchSb.append(watchLine).append("\n");
                                    }
                                    watchReader.close();
                                    hlsUrl = extractValue(watchSb.toString(), "\"hlsManifestUrl\"");
                                }
                            }
                        }

                        if (hlsUrl != null && !hlsUrl.isEmpty()) {
                            hlsUrl = hlsUrl.replace("\\/", "/");
                            final String finalUrl = hlsUrl;
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    playResolvedUrl(finalUrl, null, null, ch);
                                }
                            });
                            return;
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                // Fallback for Telemundo if offline or fails
                if (youtubePath.contains("noticias") || (ch.getName() != null && ch.getName().contains("Telemundo"))) {
                    final String fallbackUrl = "https://nbculocallive.akamaized.net/hls/live/2037499/puertorico/stream1/master.m3u8";
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            playResolvedUrl(fallbackUrl, null, null, ch);
                        }
                    });
                    return;
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        hideLoading();
                        showErrorScreen(ch.getName());
                    }
                });
            }
        }).start();
    }

    private static String extractValue(String html, String key) {
        int index = html.indexOf(key);
        if (index >= 0) {
            int colonIndex = html.indexOf(":", index + key.length());
            if (colonIndex >= 0) {
                int startQuote = html.indexOf("\"", colonIndex + 1);
                if (startQuote >= 0 && startQuote < colonIndex + 10) {
                    int endQuote = html.indexOf("\"", startQuote + 1);
                    if (endQuote > startQuote) {
                        return html.substring(startQuote + 1, endQuote);
                    }
                }
            }
        }
        return null;
    }

    private void playResolvedUrl(String streamUrl, String cookieStr, String referer, Channel ch) {
        activeStreamUrl = streamUrl;
        activeCookie = cookieStr;
        activeReferer = referer;
        activeUserAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, como Gecko) Chrome/112.0.0.0 Mobile Safari/537.36";

        if (dataSourceFactory != null) {
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, como Gecko) Chrome/112.0.0.0 Mobile Safari/537.36");
            if (referer != null && !referer.isEmpty()) {
                headers.put("Referer", referer);
            }
            if (cookieStr != null && !cookieStr.isEmpty()) {
                headers.put("Cookie", cookieStr);
            }
            dataSourceFactory.setHeaders(headers);
        }
        try {
            Uri uri = Uri.parse(streamUrl);
            MediaItem mediaItem = MediaItem.fromUri(uri);
            player.stop();
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true);
        } catch (Exception e) {
            hideLoading();
            showErrorScreen(ch.getName());
        }
    }

    private void toggleFavorite() {
        if (channelList == null || currentIndex >= channelList.size()) return;
        String url = channelList.get(currentIndex).getUrl();
        FavoriteStore.toggle(this, url);
        updateFavoriteButton(url);
    }

    private void updateFavoriteButton(String url) {
        boolean isFav = FavoriteStore.isFavorite(this, url);
        btnFavorite.setText(isFav ? "❤️" : "🤍");
    }

    private void showLoading() {
        loadingContainer.setVisibility(View.VISIBLE);
        if (!anim1.isRunning()) anim1.start();
        if (!anim2.isRunning()) anim2.start();
        if (!anim3.isRunning()) anim3.start();
    }

    private void hideLoading() {
        loadingContainer.setVisibility(View.GONE);
        anim1.cancel(); anim2.cancel(); anim3.cancel();
        dot1.setTranslationY(0); dot2.setTranslationY(0); dot3.setTranslationY(0);
        dot1.setAlpha(1f); dot2.setAlpha(1f); dot3.setAlpha(1f);
    }

    private void enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            //noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void toggleTopBar() {
        if (topBar.getVisibility() == View.VISIBLE) hideTopBarNow();
        else showTopBarBriefly();
    }

    private void showTopBarBriefly() {
        if (hideTopBarRunnable != null) handler.removeCallbacks(hideTopBarRunnable);
        topBar.setVisibility(View.VISIBLE);
        topBar.animate().alpha(1f).setDuration(200).start();
        if (channelList != null && currentIndex < channelList.size()) {
            updateFavoriteButton(channelList.get(currentIndex).getUrl());
        }
        btnFavorite.requestFocus();
        hideTopBarRunnable = this::hideTopBarNow;
        handler.postDelayed(hideTopBarRunnable, TOPBAR_AUTOHIDE_MS);
    }

    private void hideTopBarNow() {
        topBar.animate().alpha(0f).setDuration(400)
            .withEndAction(() -> topBar.setVisibility(View.GONE)).start();
    }

    private void showSidePanel() {
        if (sidePanel != null) {
            if (hideTopBarRunnable != null) handler.removeCallbacks(hideTopBarRunnable);
            hideTopBarNow();
            
            sidePanel.setVisibility(View.VISIBLE);
            
            if (sideAdapter != null) {
                sideAdapter.updateChannels(channelList, FavoriteStore.loadFavorites(this));
                
                LinearLayoutManager lm = (LinearLayoutManager) rvSideChannels.getLayoutManager();
                if (lm != null) {
                    lm.scrollToPositionWithOffset(currentIndex, 0);
                }
                
                rvSideChannels.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        rvSideChannels.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        RecyclerView.ViewHolder holder = rvSideChannels.findViewHolderForAdapterPosition(currentIndex);
                        if (holder != null) {
                            holder.itemView.requestFocus();
                        } else {
                            rvSideChannels.requestFocus();
                        }
                    }
                });
            } else {
                sidePanel.requestFocus();
            }
        }
    }

    private void hideSidePanel() {
        if (sidePanel != null && sidePanel.getVisibility() == View.VISIBLE) {
            sidePanel.setVisibility(View.GONE);
            playerView.requestFocus();
        }
    }

    private boolean isSidePanelVisible() {
        return sidePanel != null && sidePanel.getVisibility() == View.VISIBLE;
    }

    private void navigateToChannel(int index) {
        if (channelList == null || channelList.isEmpty()) return;
        if (index < 0) index = channelList.size() - 1;
        if (index >= channelList.size()) index = 0;
        currentIndex = index;
        loadChannel(currentIndex);
    }

    private void showErrorScreen(String name) {
        hideLoading();
        tvErrorChannelName.setText(ChannelDeduplicator.cleanName(name));
        errorScreen.setVisibility(View.VISIBLE);
        if (hideOverlayRunnable != null) handler.removeCallbacks(hideOverlayRunnable);
        overlayContainer.setVisibility(View.GONE);

        if (isTelevision()) {
            startAutomaticRetry();
        }
    }

    private void startAutomaticRetry() {
        if (autoRetryRunnable != null) {
            handler.removeCallbacks(autoRetryRunnable);
        }
        autoRetryCount++;
        if (btnRetry != null) {
            btnRetry.setText("Reintentando... (" + autoRetryCount + ")");
        }
        autoRetryRunnable = new Runnable() {
            @Override
            public void run() {
                if (errorScreen.getVisibility() == View.VISIBLE) {
                    if (btnRetry != null) {
                        btnRetry.setText("Intentando...");
                    }
                    loadChannel(currentIndex);
                }
            }
        };
        handler.postDelayed(autoRetryRunnable, AUTO_RETRY_DELAY_MS);
    }

    private void hideErrorScreen() {
        errorScreen.setVisibility(View.GONE);
        if (autoRetryRunnable != null) {
            handler.removeCallbacks(autoRetryRunnable);
            autoRetryRunnable = null;
        }
        if (btnRetry != null) {
            btnRetry.setText("Intentar de nuevo");
        }
    }

    private boolean isTelevision() {
        android.app.UiModeManager uiModeManager = (android.app.UiModeManager) getSystemService(UI_MODE_SERVICE);
        return uiModeManager != null && uiModeManager.getCurrentModeType() == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private void showChannelOverlay(Channel ch, int index) {
        if (hideOverlayRunnable != null) handler.removeCallbacks(hideOverlayRunnable);
        overlayEmoji.setText(ch.getEmoji());
        overlayName.setText(ChannelDeduplicator.cleanName(ch.getName()));
        overlayCategory.setText(ch.getCategory());
        if (channelList.size() > 1) {
            int prev = index > 0 ? index - 1 : channelList.size() - 1;
            int next = index < channelList.size() - 1 ? index + 1 : 0;
            overlayHint.setText("◀ " + ChannelDeduplicator.cleanName(channelList.get(prev).getName())
                + "   |   " + ChannelDeduplicator.cleanName(channelList.get(next).getName()) + " ▶");
        } else overlayHint.setText("");
        overlayContainer.setVisibility(View.VISIBLE);
        overlayContainer.setAlpha(1f);
        hideOverlayRunnable = () ->
            overlayContainer.animate().alpha(0f).setDuration(400)
                .withEndAction(() -> overlayContainer.setVisibility(View.GONE)).start();
        handler.postDelayed(hideOverlayRunnable, OVERLAY_DURATION_MS);
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (isSidePanelVisible()) {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                hideSidePanel();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            finishAndGoHome();
            return true;
        }

        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
            case android.view.KeyEvent.KEYCODE_CHANNEL_DOWN:
                navigateToChannel(currentIndex - 1);
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
            case android.view.KeyEvent.KEYCODE_DPAD_UP:
            case android.view.KeyEvent.KEYCODE_CHANNEL_UP:
                navigateToChannel(currentIndex + 1);
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
            case android.view.KeyEvent.KEYCODE_ENTER:
                showSidePanel();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        gestureDetector.onTouchEvent(e);
        return super.onTouchEvent(e);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    @Override
    public void onBackPressed() {
        if (isSidePanelVisible()) {
            hideSidePanel();
        } else {
            finishAndGoHome();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        boolean isPip = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isPip = isInPictureInPictureMode();
        }
        if (!isPip) {
            if (player != null) player.pause();
            anim1.pause(); anim2.pause(); anim3.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isTelevision() && player != null && player.isPlaying()) {
                enterPipModeCustom();
            }
        }
    }

    private void updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
            builder.setAspectRatio(new Rational(16, 9));

            // Configurar setSourceRectHint para una transición fluida (Android 8.0+)
            android.graphics.Rect rect = new android.graphics.Rect();
            playerView.getGlobalVisibleRect(rect);
            builder.setSourceRectHint(rect);

            // Habilitar autoEnterEnabled para ingresar a PiP automáticamente en Android 12+ (Home / Recents)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true);
            }

            // Se muestra el botón "Solo Audio" si no es Play Store, o si la función premium está habilitada en Play Store
            if (!BuildConfig.IS_PLAY_STORE || ENABLE_PREMIUM_BG_AUDIO_PLAYSTORE) {
                Intent broadcastIntent = new Intent("ACTION_BACKGROUND_AUDIO");
                broadcastIntent.setPackage(getPackageName());
                
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        this, 
                        0, 
                        broadcastIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                int iconRes = getResources().getIdentifier("ic_headphones", "drawable", getPackageName());
                if (iconRes == 0) {
                    iconRes = android.R.drawable.ic_lock_silent_mode;
                }
                Icon icon = Icon.createWithResource(this, iconRes);
                
                RemoteAction remoteAction = new RemoteAction(
                        icon,
                        "Solo Audio",
                        "Escuchar en segundo plano",
                        pendingIntent
                );

                java.util.List<RemoteAction> actions = new java.util.ArrayList<>();
                actions.add(remoteAction);
                builder.setActions(actions);
            }

            setPictureInPictureParams(builder.build());
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void enterPipModeCustom() {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
        Rational aspectRatio = new Rational(16, 9);
        builder.setAspectRatio(aspectRatio);

        // Configurar setSourceRectHint para una transición fluida (Android 8.0+)
        android.graphics.Rect rect = new android.graphics.Rect();
        playerView.getGlobalVisibleRect(rect);
        builder.setSourceRectHint(rect);

        // Habilitar autoEnterEnabled para ingresar a PiP automáticamente en Android 12+ (Home / Recents)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true);
        }

        // Se muestra el botón "Solo Audio" si no es Play Store, o si la función premium está habilitada en Play Store
        if (!BuildConfig.IS_PLAY_STORE || ENABLE_PREMIUM_BG_AUDIO_PLAYSTORE) {
            Intent broadcastIntent = new Intent("ACTION_BACKGROUND_AUDIO");
            broadcastIntent.setPackage(getPackageName());
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this, 
                    0, 
                    broadcastIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            int iconRes = getResources().getIdentifier("ic_headphones", "drawable", getPackageName());
            if (iconRes == 0) {
                iconRes = android.R.drawable.ic_lock_silent_mode;
            }
            Icon icon = Icon.createWithResource(this, iconRes);
            
            RemoteAction remoteAction = new RemoteAction(
                    icon,
                    "Solo Audio",
                    "Escuchar en segundo plano",
                    pendingIntent
            );

            java.util.List<RemoteAction> actions = new java.util.ArrayList<>();
            actions.add(remoteAction);
            builder.setActions(actions);
        }

        enterPictureInPictureMode(builder.build());
    }

    private void registerBackgroundAudioReceiver() {
        if (backgroundAudioReceiver == null) {
            backgroundAudioReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ("ACTION_BACKGROUND_AUDIO".equals(intent.getAction())) {
                        startBackgroundAudio();
                    }
                }
            };
            IntentFilter filter = new IntentFilter("ACTION_BACKGROUND_AUDIO");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(backgroundAudioReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(backgroundAudioReceiver, filter);
            }
        }
    }

    private void unregisterBackgroundAudioReceiver() {
        if (backgroundAudioReceiver != null) {
            unregisterReceiver(backgroundAudioReceiver);
            backgroundAudioReceiver = null;
        }
    }

    private void startBackgroundAudio() {
        if (BuildConfig.IS_PLAY_STORE) {
            if (!ENABLE_PREMIUM_BG_AUDIO_PLAYSTORE) {
                // Característica desactivada temporalmente en Play Store
                return;
            }
            if (!isBackgroundAudioUnlocked()) {
                pendingUnlockBgAudio = true;
                // Regresar a pantalla completa para mostrar el anuncio
                Intent resumeIntent = new Intent(this, PlayerActivity.class);
                resumeIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(resumeIntent);
                return;
            }
        }

        if (currentIndex < 0 || currentIndex >= channelList.size()) return;
        Channel currentChannel = channelList.get(currentIndex);
        
        String playUrl = activeStreamUrl;
        if (playUrl == null || playUrl.isEmpty()) {
            playUrl = currentChannel.getUrl();
            if (player != null && player.getCurrentMediaItem() != null && player.getCurrentMediaItem().localConfiguration != null) {
                playUrl = player.getCurrentMediaItem().localConfiguration.uri.toString();
            }
        }
        
        Intent serviceIntent = new Intent(this, BackgroundAudioService.class);
        serviceIntent.putExtra("channel_name", currentChannel.getName());
        serviceIntent.putExtra("stream_url", playUrl);
        serviceIntent.putExtra("raw_url", currentChannel.getUrl());
        if (activeCookie != null) {
            serviceIntent.putExtra("cookie", activeCookie);
        }
        if (activeReferer != null) {
            serviceIntent.putExtra("referer", activeReferer);
        }
        if (activeUserAgent != null) {
            serviceIntent.putExtra("user_agent", activeUserAgent);
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        finish();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            if (topBar != null) topBar.setVisibility(View.GONE);
            if (sidePanel != null) sidePanel.setVisibility(View.GONE);
            if (overlayContainer != null) overlayContainer.setVisibility(View.GONE);
        } else {
            enterImmersiveMode();
            if (pendingUnlockBgAudio) {
                pendingUnlockBgAudio = false;
                showUnlockDialog();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        if (player != null && player.getPlaybackState() == Player.STATE_READY) {
            player.play();
        }
        updatePipParams();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        anim1.cancel(); anim2.cancel(); anim3.cancel();
        unregisterBackgroundAudioReceiver();
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private void checkNotificationPermission() {
        if (BuildConfig.IS_PLAY_STORE && !ENABLE_PREMIUM_BG_AUDIO_PLAYSTORE) return; // No se requiere permiso de notificación en Play Store si está desactivado el segundo plano
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.widget.Toast.makeText(this, "💡 Recordá permitir las notificaciones para habilitar la reproducción en segundo plano", android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    private static class MapHeaderDataSourceFactory implements DataSource.Factory {
        private final DataSource.Factory delegateFactory;
        private final java.util.Map<String, String> currentHeaders = new java.util.HashMap<>();

        public MapHeaderDataSourceFactory(DataSource.Factory delegateFactory) {
            this.delegateFactory = delegateFactory;
        }

        public void setHeaders(java.util.Map<String, String> headers) {
            synchronized (currentHeaders) {
                currentHeaders.clear();
                if (headers != null) {
                    currentHeaders.putAll(headers);
                }
            }
        }

        @Override
        public DataSource createDataSource() {
            DataSource dataSource = delegateFactory.createDataSource();
            return new DataSource() {
                @Override
                public void addTransferListener(TransferListener transferListener) {
                    dataSource.addTransferListener(transferListener);
                }

                @Override
                public long open(DataSpec dataSpec) throws java.io.IOException {
                    synchronized (currentHeaders) {
                        if (!currentHeaders.isEmpty()) {
                            java.util.Map<String, String> combinedHeaders = new java.util.HashMap<>(dataSpec.httpRequestHeaders);
                            combinedHeaders.putAll(currentHeaders);
                            dataSpec = dataSpec.buildUpon().setHttpRequestHeaders(combinedHeaders).build();
                        }
                    }
                    return dataSource.open(dataSpec);
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
                    return dataSource.read(buffer, offset, length);
                }

                @Nullable
                @Override
                public Uri getUri() {
                    return dataSource.getUri();
                }

                @Override
                public java.util.Map<String, java.util.List<String>> getResponseHeaders() {
                    return dataSource.getResponseHeaders();
                }

                @Override
                public void close() throws java.io.IOException {
                    dataSource.close();
                }
            };
        }
    }

    private boolean isBackgroundAudioUnlocked() {
        if (!BuildConfig.IS_PLAY_STORE) return true;
        android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        long unlockedUntil = prefs.getLong("bg_audio_unlocked_until", 0);
        return System.currentTimeMillis() < unlockedUntil;
    }

    private void loadRewardedAd() {
        if (!BuildConfig.IS_PLAY_STORE) return;
        if (rewardedAd != null || isLoadingAd) return;

        isLoadingAd = true;
        AdRequest adRequest = new AdRequest.Builder().build();
        // ID de anuncio bonificado de prueba oficial de Google
        String adUnitId = "ca-app-pub-3940256099942544/5224354917";

        RewardedAd.load(this, adUnitId, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                rewardedAd = null;
                isLoadingAd = false;
            }

            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                rewardedAd = ad;
                isLoadingAd = false;
            }
        });
    }

    private void showUnlockDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Reproducción en Segundo Plano")
                .setMessage("Esta es una característica Premium.\n\nPara desbloquear la reproducción en segundo plano (audio con la pantalla apagada) gratis por las próximas 24 horas, puedes ver un breve anuncio de video.")
                .setPositiveButton("Ver Video", (dialog, which) -> {
                    if (rewardedAd != null) {
                        showRewardedAd();
                    } else {
                        // El anuncio no está cargado, lo cargamos mostrando un diálogo de carga
                        showAdLoadingDialog();
                        // Intentar cargar
                        AdRequest adRequest = new AdRequest.Builder().build();
                        String adUnitId = "ca-app-pub-3940256099942544/5224354917";
                        isLoadingAd = true;
                        RewardedAd.load(PlayerActivity.this, adUnitId, adRequest, new RewardedAdLoadCallback() {
                            @Override
                            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                                isLoadingAd = false;
                                dismissAdLoadingDialog();
                                android.widget.Toast.makeText(PlayerActivity.this, "No se pudo cargar el anuncio. Intenta de nuevo más tarde.", android.widget.Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onAdLoaded(@NonNull RewardedAd ad) {
                                isLoadingAd = false;
                                rewardedAd = ad;
                                dismissAdLoadingDialog();
                                showRewardedAd();
                            }
                        });
                    }
                })
                .setNegativeButton("Ahora no", null)
                .setCancelable(true)
                .show();
    }

    private void showRewardedAd() {
        if (rewardedAd != null) {
            rewardedAd.show(this, new OnUserEarnedRewardListener() {
                @Override
                public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                    unlockBackgroundAudio();
                }
            });
            rewardedAd = null;
            loadRewardedAd();
        }
    }

    private void unlockBackgroundAudio() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        long unlockTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000); // 24 horas
        prefs.edit().putLong("bg_audio_unlocked_until", unlockTime).apply();

        android.widget.Toast.makeText(this, "🎉 ¡Segundo plano desbloqueado por 24 horas!", android.widget.Toast.LENGTH_LONG).show();

        // Verificar permisos de notificación
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(
                        this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        102
                );
                return;
            }
        }
        startBackgroundAudio();
    }

    private void showAdLoadingDialog() {
        if (adLoadingDialog == null) {
            android.widget.ProgressBar progressBar = new android.widget.ProgressBar(this);
            progressBar.setIndeterminate(true);
            
            android.widget.FrameLayout container = new android.widget.FrameLayout(this);
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.gravity = android.view.Gravity.CENTER;
            int padding = (int) (16 * getResources().getDisplayMetrics().density);
            container.setPadding(padding, padding, padding, padding);
            container.addView(progressBar, params);

            adLoadingDialog = new android.app.AlertDialog.Builder(this)
                    .setTitle("Cargando anuncio...")
                    .setMessage("Por favor, espera un momento.")
                    .setView(container)
                    .setCancelable(false)
                    .create();
        }
        adLoadingDialog.show();
    }

    private void dismissAdLoadingDialog() {
        if (adLoadingDialog != null && adLoadingDialog.isShowing()) {
            adLoadingDialog.dismiss();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 102) {
            if (grantResults.length > 0 && grantResults[0] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.widget.Toast.makeText(this, "💡 Sin el permiso de notificaciones, no verás los controles para detener el audio.", android.widget.Toast.LENGTH_LONG).show();
            }
            startBackgroundAudio();
        }
    }
}
