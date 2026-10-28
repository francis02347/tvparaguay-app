package com.tvpy.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Random;

public class RemoteControlManager {
    private static final String TAG = "RemoteControlManager";
    private static RemoteControlManager instance;

    private String pin;
    private WebView headlessWebView;
    private boolean isClientConnected = false;
    private RemoteCommandListener currentListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface RemoteCommandListener {
        void onRemoteCommand(String action, JSONObject data);
        void onClientConnected();
        void onClientDisconnected();
    }

    public static synchronized RemoteControlManager getInstance() {
        if (instance == null) {
            instance = new RemoteControlManager();
        }
        return instance;
    }

    private RemoteControlManager() {
        int randomPin = 1000 + new Random().nextInt(9000);
        this.pin = String.valueOf(randomPin);
    }

    public String getPin() {
        return pin;
    }

    public boolean isConnected() {
        return isClientConnected;
    }

    public void init(Context context) {
        if (headlessWebView != null) return;

        mainHandler.post(() -> {
            try {
                headlessWebView = new WebView(context.getApplicationContext());
                WebSettings settings = headlessWebView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setAllowFileAccess(true);
                settings.setAllowContentAccess(true);

                headlessWebView.addJavascriptInterface(new Object() {
                    @JavascriptInterface
                    public void onBridgeReady(String assignedPin) {
                        Log.d(TAG, "Bridge ready with PIN: " + assignedPin);
                    }

                    @JavascriptInterface
                    public void onClientConnected() {
                        Log.d(TAG, "Mobile remote client connected!");
                        isClientConnected = true;
                        mainHandler.post(() -> {
                            if (currentListener != null) currentListener.onClientConnected();
                        });
                    }

                    @JavascriptInterface
                    public void onClientDisconnected() {
                        Log.d(TAG, "Mobile remote client disconnected");
                        isClientConnected = false;
                        mainHandler.post(() -> {
                            if (currentListener != null) currentListener.onClientDisconnected();
                        });
                    }

                    @JavascriptInterface
                    public void onCommand(String jsonStr) {
                        Log.d(TAG, "Remote command received: " + jsonStr);
                        mainHandler.post(() -> handleCommand(jsonStr, context));
                    }
                }, "AndroidBridge");

                headlessWebView.setWebViewClient(new WebViewClient());
                String bridgeUrl = "https://tvv.lat/remote_bridge.html?pin=" + pin;
                headlessWebView.loadUrl(bridgeUrl);
                Log.d(TAG, "Headless bridge WebView initialized with URL: " + bridgeUrl);
            } catch (Exception e) {
                Log.e(TAG, "Error initializing headless WebView", e);
            }
        });
    }

    public void setListener(RemoteCommandListener listener) {
        this.currentListener = listener;
    }

    public void removeListener(RemoteCommandListener listener) {
        if (this.currentListener == listener) {
            this.currentListener = null;
        }
    }

    public void notifyNowPlaying(String channelName, int channelNumber) {
        if (headlessWebView != null && isClientConnected) {
            mainHandler.post(() -> {
                try {
                    String script = String.format(
                        "if (window.sendToRemote) { window.sendToRemote({ type: 'NOW_PLAYING', channelName: '%s', channelNumber: %d }); }",
                        channelName.replace("'", "\\'"), channelNumber
                    );
                    headlessWebView.evaluateJavascript(script, null);
                } catch (Exception e) {
                    Log.e(TAG, "Error sending now playing info", e);
                }
            });
        }
    }

    private void handleCommand(String jsonStr, Context context) {
        try {
            JSONObject data = new JSONObject(jsonStr);
            String action = data.optString("action", "");

            if ("VOL_UP".equals(action)) {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                }
            } else if ("VOL_DOWN".equals(action)) {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                }
            } else if ("MUTE".equals(action)) {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                }
            }

            if (currentListener != null) {
                currentListener.onRemoteCommand(action, data);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing remote command: " + jsonStr, e);
        }
    }

    public void showPairingDialog(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (24 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        layout.setBackgroundColor(Color.parseColor("#0e131f"));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("📱 Control Remoto Móvil");
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText("Apunta con la cámara de tu celular para vincular");
        tvSub.setTextSize(13);
        tvSub.setTextColor(Color.parseColor("#94a3b8"));
        tvSub.setGravity(Gravity.CENTER);
        int subMargin = (int) (6 * activity.getResources().getDisplayMetrics().density);
        tvSub.setPadding(0, subMargin, 0, (int) (16 * activity.getResources().getDisplayMetrics().density));
        layout.addView(tvSub);

        int qrSize = (int) (210 * activity.getResources().getDisplayMetrics().density);
        LinearLayout qrBox = new LinearLayout(activity);
        qrBox.setGravity(Gravity.CENTER);
        qrBox.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.bottomMargin = (int) (16 * activity.getResources().getDisplayMetrics().density);
        qrBox.setLayoutParams(qrParams);
        qrBox.setPadding(12, 12, 12, 12);

        ImageView imgQr = new ImageView(activity);
        imgQr.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        ProgressBar pb = new ProgressBar(activity);
        qrBox.addView(pb);
        layout.addView(qrBox);

        TextView tvPin = new TextView(activity);
        tvPin.setText("PIN: " + pin);
        tvPin.setTextSize(26);
        tvPin.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        tvPin.setTextColor(Color.parseColor("#00f0ff"));
        tvPin.setGravity(Gravity.CENTER);
        layout.addView(tvPin);

        TextView tvStatus = new TextView(activity);
        tvStatus.setText(isClientConnected ? "🟢 Celular Conectado" : "Esperando conexión...");
        tvStatus.setTextSize(13);
        tvStatus.setTextColor(isClientConnected ? Color.parseColor("#10b981") : Color.parseColor("#f59e0b"));
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, (int) (10 * activity.getResources().getDisplayMetrics().density), 0, (int) (18 * activity.getResources().getDisplayMetrics().density));
        layout.addView(tvStatus);

        Button btnClose = new Button(activity);
        btnClose.setText("Cerrar");
        btnClose.setBackgroundColor(Color.parseColor("#1e293b"));
        btnClose.setTextColor(Color.WHITE);
        layout.addView(btnClose);

        builder.setView(layout);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        new Thread(() -> {
            try {
                String remoteUrl = "https://tvv.lat/remote.html?pin=" + pin;
                String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=220x220&margin=8&data=" + URLEncoder.encode(remoteUrl, "UTF-8");
                HttpURLConnection conn = (HttpURLConnection) new URL(qrUrl).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.connect();
                InputStream is = conn.getInputStream();
                Bitmap bmp = BitmapFactory.decodeStream(is);
                activity.runOnUiThread(() -> {
                    qrBox.removeView(pb);
                    imgQr.setImageBitmap(bmp);
                    qrBox.addView(imgQr);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading QR", e);
            }
        }).start();
    }
}
