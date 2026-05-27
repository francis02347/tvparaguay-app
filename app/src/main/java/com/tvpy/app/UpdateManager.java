package com.tvpy.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gestor de Actualizaciones OTA (Over-The-Air) Autohospedado para TVParaguay.
 * Verifica versiones mediante un JSON remoto, descarga el APK y abre el instalador nativo.
 */
public class UpdateManager {

    // URL predeterminada de ejemplo (deberá ser reemplazada por tu servidor de producción)
    private static final String UPDATE_JSON_URL = "https://raw.githubusercontent.com/francis02347/tvparaguay-app/refs/heads/main/update.json";
    
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AlertDialog progressDialog;
    private ProgressBar progressBar;
    private TextView tvProgressPercent;

    public UpdateManager(Context context) {
        this.context = context;
    }

    /**
     * Inicia la comprobación de actualizaciones en segundo plano.
     */
    public void checkForUpdates() {
        executor.execute(() -> {
            try {
                // 1. Obtener versión local instalada
                PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                long localVersionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P 
                        ? pInfo.getLongVersionCode() 
                        : pInfo.versionCode;
                
                // 2. Descargar archivo JSON remoto
                URL url = new URL(UPDATE_JSON_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    conn.disconnect();
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                conn.disconnect();

                // 3. Procesar datos del JSON
                JSONObject json = new JSONObject(sb.toString());
                int remoteVersionCode = json.getInt("versionCode");
                String remoteVersionName = json.getString("versionName");
                String apkUrl = json.getString("apkUrl");
                String releaseNotes = json.optString("releaseNotes", "Mejoras de estabilidad y rendimiento.");

                // 4. Comparar versiones
                if (remoteVersionCode > localVersionCode) {
                    mainHandler.post(() -> showUpdateDialog(remoteVersionName, apkUrl, releaseNotes));
                }
            } catch (Exception e) {
                // Silencioso en producción para no interrumpir la experiencia de TV
                e.printStackTrace();
            }
        });
    }

    /**
     * Muestra el diálogo de confirmación de actualización en pantalla.
     */
    private void showUpdateDialog(String versionName, String apkUrl, String releaseNotes) {
        new AlertDialog.Builder(context)
                .setTitle("📢 ¡Actualización Disponible!")
                .setMessage("Se ha detectado una nueva versión de TV Paraguay (v" + versionName + ")."
                        + "\n\nNovedades:\n" + releaseNotes
                        + "\n\n¿Querés descargar e instalar la actualización ahora?")
                .setPositiveButton("Actualizar", (dialog, which) -> {
                    // Verificar y solicitar permisos para fuentes desconocidas antes de descargar (Android 8.0+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (!context.getPackageManager().canRequestPackageInstalls()) {
                            Toast.makeText(context, "Por favor, habilitá la instalación para TVParaguay en los ajustes de tu TV.", Toast.LENGTH_LONG).show();
                            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                            settingsIntent.setData(Uri.parse("package:" + context.getPackageName()));
                            context.startActivity(settingsIntent);
                            return;
                        }
                    }
                    startApkDownload(apkUrl);
                })
                .setNegativeButton("Más tarde", null)
                .show();
    }

    /**
     * Inicia la descarga del APK del servidor remoto.
     */
    private void startApkDownload(String apkUrl) {
        showDownloadProgressDialog();

        executor.execute(() -> {
            File apkFile = new File(context.getCacheDir(), "app-release.apk");
            if (apkFile.exists()) {
                apkFile.delete();
            }

            try {
                URL url = new URL(apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Error del servidor: código " + conn.getResponseCode());
                }

                int fileLength = conn.getContentLength();
                
                try (InputStream input = conn.getInputStream();
                     FileOutputStream output = new FileOutputStream(apkFile)) {

                    byte[] data = new byte[4096];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        // Publicar el progreso
                        if (fileLength > 0) {
                            final int progress = (int) (total * 100 / fileLength);
                            mainHandler.post(() -> updateProgress(progress));
                        }
                        output.write(data, 0, count);
                    }
                }
                conn.disconnect();

                // Descarga finalizada, proceder a instalar en el hilo principal
                mainHandler.post(() -> {
                    dismissProgressDialog();
                    installApk(apkFile);
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    dismissProgressDialog();
                    Toast.makeText(context, "❌ Error al descargar actualización: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Muestra un diálogo personalizado con barra de progreso apto para pantallas de TV.
     */
    private void showDownloadProgressDialog() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        
        int padding = dpToPx(20);
        layout.setPadding(padding, padding, padding, padding);

        TextView tvTitle = new TextView(context);
        tvTitle.setText("Descargando actualización...");
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setPadding(0, 0, 0, dpToPx(15));
        layout.addView(tvTitle);

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        progressBar.setLayoutParams(lp);
        layout.addView(progressBar);

        tvProgressPercent = new TextView(context);
        tvProgressPercent.setText("0%");
        tvProgressPercent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvProgressPercent.setTextColor(0xAAFFFFFF);
        tvProgressPercent.setPadding(0, dpToPx(8), 0, 0);
        layout.addView(tvProgressPercent);

        progressDialog = new AlertDialog.Builder(context)
                .setView(layout)
                .setCancelable(false)
                .create();

        progressDialog.show();
    }

    private void updateProgress(int progress) {
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
        if (tvProgressPercent != null) {
            tvProgressPercent.setText(progress + "%");
        }
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    /**
     * Lanza el instalador nativo de Android de forma segura mediante FileProvider.
     */
    private void installApk(File file) {
        try {
            Uri apkUri = FileProvider.getUriForFile(
                    context, 
                    context.getPackageName() + ".fileprovider", 
                    file
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "❌ Error al iniciar instalación: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
