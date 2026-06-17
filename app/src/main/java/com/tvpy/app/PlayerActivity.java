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

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
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

    private View sidePanel;
    private RecyclerView rvSideChannels;
    private ChannelAdapter sideAdapter;

    // ─── Estado ───────────────────────────────────────────────────────────────
    private ExoPlayer player;
    private List<Channel> channelList;
    private int currentIndex = 0;
    private GestureDetector gestureDetector;
    private MapHeaderDataSourceFactory dataSourceFactory;

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

        channelList  = ChannelSession.getChannels();
        currentIndex = ChannelSession.getStartIndex();

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

        findViewById(R.id.btnRetry).setOnClickListener(v -> loadChannel(currentIndex));

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
                    player.play();
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
                            if (found.contains("rds3gen") && found.contains("k=")) {
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
                    conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

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
                            "\"hlsManifestUrl\"\\s*:\\s*\"([^\"]+)\""
                        );
                        java.util.regex.Matcher matcher = pattern.matcher(html);
                        if (matcher.find()) {
                            String hlsUrl = matcher.group(1);
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

    private void playResolvedUrl(String streamUrl, String cookieStr, String referer, Channel ch) {
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
    }

    private void hideErrorScreen() {
        errorScreen.setVisibility(View.GONE);
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
        if (player != null) player.pause();
        anim1.pause(); anim2.pause(); anim3.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        if (player != null && player.getPlaybackState() == Player.STATE_READY) {
            player.play();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        anim1.cancel(); anim2.cancel(); anim3.cancel();
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
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
}
