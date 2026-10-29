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
    private LocalRemoteServer localServer;
    private boolean isClientConnected = false;
    private RemoteCommandListener currentListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String lastChannelName = "";
    private int lastChannelNumber = 1;
    private Context appContext;
    private java.lang.ref.WeakReference<Activity> activeActivityRef;

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

    public String getLastChannelName() {
        return lastChannelName;
    }

    public int getLastChannelNumber() {
        return lastChannelNumber;
    }

    public void init(Context context) {
        this.appContext = context.getApplicationContext();

        // Iniciar servidor HTTP local para control por Wi-Fi
        if (localServer == null) {
            localServer = new LocalRemoteServer(appContext);
            localServer.start();
            Log.d(TAG, "Local Wi-Fi Remote Server started.");
        }
    }

    public void setListener(RemoteCommandListener listener) {
        this.currentListener = listener;
    }

    public void setActiveActivity(Activity activity) {
        this.activeActivityRef = new java.lang.ref.WeakReference<>(activity);
    }

    public Activity getActiveActivity() {
        return activeActivityRef != null ? activeActivityRef.get() : null;
    }

    public void removeListener(RemoteCommandListener listener) {
        if (this.currentListener == listener) {
            this.currentListener = null;
        }
    }

    public void notifyNowPlaying(String channelName, int channelNumber) {
        this.lastChannelName = channelName != null ? channelName : "";
        this.lastChannelNumber = channelNumber;
    }

    public void dispatchDirectCommand(String action, JSONObject data) {
        isClientConnected = true;
        mainHandler.post(() -> {
            try {
                if (appContext != null) {
                    if ("VOL_UP".equals(action)) {
                        AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) {
                            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                        }
                    } else if ("VOL_DOWN".equals(action)) {
                        AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) {
                            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                        }
                    } else if ("MUTE".equals(action)) {
                        AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) {
                            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                        }
                    }
                }

                if ("CHECK_UPDATE".equals(action) || "UPDATE_APP".equals(action)) {
                    Activity act = getActiveActivity();
                    if (act != null && !act.isFinishing()) {
                        new UpdateManager(act).checkForUpdates(true);
                        return;
                    }
                }

                if (currentListener != null) {
                    currentListener.onRemoteCommand(action, data);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error executing direct remote command: " + action, e);
            }
        });
    }

    public void showPairingDialog(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        String wifiIp = LocalRemoteServer.getWifiIpAddress(activity);
        String localRemoteUrl = "http://" + wifiIp + ":" + LocalRemoteServer.PORT;
        String cloudRemoteUrl = "https://tvv.lat/remote.html?pin=" + pin;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (22 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        layout.setBackgroundColor(Color.parseColor("#0e131f"));

        // Título
        TextView tvTitle = new TextView(activity);
        tvTitle.setText("📱 Control Remoto Móvil");
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvTitle);

        // Subtítulo
        TextView tvSub = new TextView(activity);
        tvSub.setText("Apunta con la cámara de tu celular al código QR");
        tvSub.setTextSize(13);
        tvSub.setTextColor(Color.parseColor("#94a3b8"));
        tvSub.setGravity(Gravity.CENTER);
        int subMargin = (int) (6 * activity.getResources().getDisplayMetrics().density);
        tvSub.setPadding(0, subMargin, 0, (int) (14 * activity.getResources().getDisplayMetrics().density));
        layout.addView(tvSub);

        // Contenedor QR
        int qrSize = (int) (210 * activity.getResources().getDisplayMetrics().density);
        LinearLayout qrBox = new LinearLayout(activity);
        qrBox.setGravity(Gravity.CENTER);
        qrBox.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.bottomMargin = (int) (14 * activity.getResources().getDisplayMetrics().density);
        qrBox.setLayoutParams(qrParams);
        qrBox.setPadding(12, 12, 12, 12);

        ImageView imgQr = new ImageView(activity);
        imgQr.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        ProgressBar pb = new ProgressBar(activity);
        qrBox.addView(pb);
        layout.addView(qrBox);

        // Dirección Web Local directa (Recomendada)
        TextView tvLocalUrlLabel = new TextView(activity);
        tvLocalUrlLabel.setText("Opción 1: En tu celular abre en el navegador:");
        tvLocalUrlLabel.setTextSize(12);
        tvLocalUrlLabel.setTextColor(Color.parseColor("#94a3b8"));
        tvLocalUrlLabel.setGravity(Gravity.CENTER);
        layout.addView(tvLocalUrlLabel);

        TextView tvLocalUrl = new TextView(activity);
        tvLocalUrl.setText(localRemoteUrl);
        tvLocalUrl.setTextSize(18);
        tvLocalUrl.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        tvLocalUrl.setTextColor(Color.parseColor("#00f0ff"));
        tvLocalUrl.setGravity(Gravity.CENTER);
        int urlMargin = (int) (4 * activity.getResources().getDisplayMetrics().density);
        tvLocalUrl.setPadding(0, urlMargin, 0, (int) (8 * activity.getResources().getDisplayMetrics().density));
        layout.addView(tvLocalUrl);

        // Opción 2: tvv.lat/remote
        TextView tvCloudLabel = new TextView(activity);
        tvCloudLabel.setText("Opción 2: Entra a tvv.lat/remote e ingresa la IP o el PIN: " + pin);
        tvCloudLabel.setTextSize(11);
        tvCloudLabel.setTextColor(Color.parseColor("#cbd5e1"));
        tvCloudLabel.setGravity(Gravity.CENTER);
        tvCloudLabel.setPadding(0, 0, 0, (int) (10 * activity.getResources().getDisplayMetrics().density));
        layout.addView(tvCloudLabel);

        // Estado
        TextView tvStatus = new TextView(activity);
        tvStatus.setText("● Wi-Fi Local Activo (" + wifiIp + ":8088)");
        tvStatus.setTextSize(12);
        tvStatus.setTextColor(Color.parseColor("#10b981"));
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 0, 0, (int) (16 * activity.getResources().getDisplayMetrics().density));
        layout.addView(tvStatus);

        // Botón Cerrar
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

        // Generar QR apuntando directamente al servidor local Wi-Fi
        new Thread(() -> {
            try {
                String targetUrl = localRemoteUrl;
                String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=220x220&margin=8&data=" + URLEncoder.encode(targetUrl, "UTF-8");
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
