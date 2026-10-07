package com.tvpy.app;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

public class WebPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_SUBTITLE = "extra_subtitle";

    private WebView webView;
    private FrameLayout customViewContainer;
    private View topBarLayout;
    private ProgressBar progressBar;
    private TextView tvWebTitle;
    private TextView tvWebSubtitle;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideTopBarRunnable = this::hideTopBar;
    private boolean isTopBarVisible = true;

    // Dominios conocidos de publicidad invasiva en transmisiones deportivas
    private static final List<String> AD_DOMAINS = Arrays.asList(
        "ads.", "pop.", "doubleclick", "googletagmanager", "cpxinteractive",
        "propeller", "popcash", "adkeeper", "refpa", "getbanner",
        "ltv_popup", "traffic", "1xbet", "bet365", "betway", "whoscored",
        "monetag", "adsterra", "hilltopads", "clickadu", "exoclick"
    );

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_web_player);

        applyImmersiveMode();

        String streamUrl = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String subtitle = getIntent().getStringExtra(EXTRA_SUBTITLE);

        webView = findViewById(R.id.webView);
        customViewContainer = findViewById(R.id.customViewContainer);
        topBarLayout = findViewById(R.id.topBarLayout);
        progressBar = findViewById(R.id.progressBar);
        tvWebTitle = findViewById(R.id.tvWebTitle);
        tvWebSubtitle = findViewById(R.id.tvWebSubtitle);

        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageButton btnReload = findViewById(R.id.btnReload);

        if (title != null && !title.isEmpty()) {
            tvWebTitle.setText(title);
        } else {
            tvWebTitle.setText("Transmisión en Vivo");
        }

        if (subtitle != null && !subtitle.isEmpty()) {
            tvWebSubtitle.setText(subtitle);
        }

        btnBack.setOnClickListener(v -> finish());
        btnReload.setOnClickListener(v -> {
            if (webView != null) {
                progressBar.setVisibility(View.VISIBLE);
                webView.reload();
            }
        });

        findViewById(R.id.rootContainer).setOnClickListener(v -> toggleTopBar());

        setupWebView();

        if (streamUrl != null && !streamUrl.isEmpty()) {
            webView.loadUrl(streamUrl);
        }

        scheduleHideTopBar();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false); // Bloquea window.open popups
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        );

        webView.setBackgroundColor(Color.BLACK);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                customViewContainer.addView(view);
                customViewContainer.setVisibility(View.VISIBLE);
                hideTopBar();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                customViewContainer.removeView(customView);
                customView = null;
                customViewContainer.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                // Bloquea totalmente la creación de nuevas ventanas emergentes (anti-popup)
                return false;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 85) {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                // Proceder para no bloquear reproductores de streaming con certificados no estándares o autofirmados
                handler.proceed();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
                String path = uri.getPath() != null ? uri.getPath().toLowerCase() : "";

                for (String ad : AD_DOMAINS) {
                    if (host.contains(ad) || path.contains(ad)) {
                        // Devolver respuesta vacía para cancelar la publicidad
                        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String targetUrl = request.getUrl().toString();
                // Permitir navegación solo en el dominio del stream
                if (targetUrl.contains("livetv") || targetUrl.contains("apl") || targetUrl.contains("azplay") || targetUrl.contains("aliez")) {
                    return false;
                }
                // Bloquear redirecciones externas a sitios de apuestas / popups
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                injectCleanerScript(view);
            }
        });
    }

    private void injectCleanerScript(WebView view) {
        // Inyección JS para limpiar overlays, trampas de clic y ajustar el reproductor a 100% pantalla completa
        String js = "javascript:(function() {" +
            "var removeIds = ['localpp', 'ads', 'adbtm', 'stpd', 'ban1', 'ban2'];" +
            "for (var i = 0; i < removeIds.length; i++) {" +
            "   var el = document.getElementById(removeIds[i]);" +
            "   if (el) el.remove();" +
            "}" +
            "var headers = document.querySelectorAll('table[background*=\"bg_p\"], #bugoverlay');" +
            "headers.forEach(function(h) { h.remove(); });" +
            "var player = document.getElementById('adv-player') || document.querySelector('video') || document.querySelector('iframe');" +
            "if (player) {" +
            "   player.style.position = 'fixed';" +
            "   player.style.top = '0';" +
            "   player.style.left = '0';" +
            "   player.style.width = '100vw';" +
            "   player.style.height = '100vh';" +
            "   player.style.zIndex = '999999';" +
            "   player.style.background = '#000000';" +
            "}" +
            "document.body.style.background = '#000000';" +
            "document.body.style.overflow = 'hidden';" +
            "})()";

        view.evaluateJavascript(js, null);
    }

    private void toggleTopBar() {
        if (isTopBarVisible) {
            hideTopBar();
        } else {
            showTopBar();
        }
    }

    private void showTopBar() {
        isTopBarVisible = true;
        topBarLayout.animate().alpha(1.0f).translationY(0).setDuration(200).withStartAction(() -> {
            topBarLayout.setVisibility(View.VISIBLE);
        }).start();
        scheduleHideTopBar();
    }

    private void hideTopBar() {
        isTopBarVisible = false;
        topBarLayout.animate().alpha(0.0f).translationY(-topBarLayout.getHeight()).setDuration(250).withEndAction(() -> {
            topBarLayout.setVisibility(View.GONE);
        }).start();
    }

    private void scheduleHideTopBar() {
        hideHandler.removeCallbacks(hideTopBarRunnable);
        hideHandler.postDelayed(hideTopBarRunnable, 3500);
    }

    private void applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                if (webView.getWebChromeClient() != null) {
                    ((WebChromeClient) webView.getWebChromeClient()).onHideCustomView();
                }
                return true;
            }
            finish();
            return true;
        }
        showTopBar();
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
