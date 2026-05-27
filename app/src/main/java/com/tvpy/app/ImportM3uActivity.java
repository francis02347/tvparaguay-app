package com.tvpy.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImportM3uActivity extends AppCompatActivity {

    // { etiqueta visible, código ISO }
    private static final String[][] COUNTRIES = {
        { "🇵🇾 Paraguay",  "py" }, { "🇦🇷 Argentina", "ar" }, { "🇧🇷 Brasil",    "br" },
        { "🇧🇴 Bolivia",   "bo" }, { "🇨🇱 Chile",      "cl" }, { "🇨🇴 Colombia",  "co" },
        { "🇵🇪 Perú",      "pe" }, { "🇺🇾 Uruguay",    "uy" }, { "🇻🇪 Venezuela", "ve" },
        { "🇲🇽 México",    "mx" }, { "🇺🇸 USA",        "us" }, { "🇪🇸 España",    "es" },
        { "🇬🇧 UK",        "gb" }, { "🇫🇷 Francia",    "fr" }, { "🇩🇪 Alemania",  "de" },
        { "🇮🇹 Italia",    "it" }, { "🇵🇹 Portugal",   "pt" }, { "🇯🇵 Japón",     "jp" },
        { "🇰🇷 Corea",     "kr" }, { "🇮🇳 India",      "in" }, { "🇷🇺 Rusia",     "ru" },
        { "🇹🇷 Turquía",   "tr" }, { "🇸🇦 Arabia",     "sa" }, { "🇿🇦 Sudáfrica", "za" },
    };

    private static final String BASE_URL = "https://iptv-org.github.io/iptv/countries/";

    private EditText etUrl, etPaste;
    private Button btnImportUrl, btnImportPaste, btnImportFile, btnClear;
    private ProgressBar progressBar;
    private TextView tvStatus, tvChannelCount;
    private LinearLayout countryGrid;

    private ActivityResultLauncher<String[]> filePickerLauncher;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_m3u);

        // Registrar el launcher para abrir archivos .m3u / .m3u8
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri == null) return;
                readFileAndImport(uri);
            });

        etUrl          = findViewById(R.id.etUrl);
        etPaste        = findViewById(R.id.etPaste);
        btnImportUrl   = findViewById(R.id.btnImportUrl);
        btnImportFile  = findViewById(R.id.btnImportFile);
        btnImportPaste = findViewById(R.id.btnImportPaste);
        btnClear       = findViewById(R.id.btnClear);
        progressBar    = findViewById(R.id.progressBar);
        tvStatus       = findViewById(R.id.tvStatus);
        tvChannelCount = findViewById(R.id.tvChannelCount);
        countryGrid    = findViewById(R.id.countryGrid);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        refreshCount();
        buildCountryButtons();

        btnImportFile.setOnClickListener(v ->
            filePickerLauncher.launch(new String[]{
                "*/*"   // aceptamos cualquier tipo; filtramos por extensión al leer
            }));

        btnImportUrl.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) { Toast.makeText(this, "Ingresá una URL", Toast.LENGTH_SHORT).show(); return; }
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
            importFromUrl(url, "");
        });

        btnImportPaste.setOnClickListener(v -> {
            String content = etPaste.getText().toString().trim();
            if (content.isEmpty()) { Toast.makeText(this, "Pegá el contenido M3U", Toast.LENGTH_SHORT).show(); return; }
            processM3uContent(content, "");
        });

        btnClear.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("¿Borrar canales M3U?")
                .setMessage("Se eliminarán todos los canales importados. Los canales predeterminados no se verán afectados.")
                .setPositiveButton("Borrar", (d, w) -> {
                    ChannelStore.clearM3uChannels(this);
                    refreshCount();
                    tvStatus.setText("✅ Canales M3U eliminados");
                })
                .setNegativeButton("Cancelar", null).show()
        );
    }

    // ─── Botones de países ───────────────────────────────────────────────────

    private void buildCountryButtons() {
        countryGrid.removeAllViews();
        LinearLayout row = null;

        for (int i = 0; i < COUNTRIES.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, dp(7));
                row.setLayoutParams(rowLp);
                countryGrid.addView(row);
            }

            final String label   = COUNTRIES[i][0];
            final String isoCode = COUNTRIES[i][1];
            // Nombre limpio sin emoji para asignarlo como país en los canales importados
            final String countryName = label.replaceAll("[^\\p{L}\\p{N} ]", "").trim();

            TextView btn = new TextView(this);
            btn.setText(label);
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(11.5f);
            btn.setGravity(Gravity.CENTER);
            btn.setTypeface(null, Typeface.BOLD);
            btn.setPadding(dp(4), dp(10), dp(4), dp(10));
            btn.setBackground(getDrawable(R.drawable.chip_filter));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(i % 3 == 2 ? 0 : dp(6));
            btn.setLayoutParams(lp);

            btn.setFocusable(true);
            btn.setClickable(true);
            btn.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start();
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            });

            btn.setOnClickListener(v ->
                importFromUrl(BASE_URL + isoCode + ".m3u", countryName));

            row.addView(btn);
        }

        // Rellenar última fila incompleta con espacio vacío
        int rem = COUNTRIES.length % 3;
        if (rem != 0 && row != null) {
            for (int j = rem; j < 3; j++) {
                View sp = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMarginEnd(j == 2 ? 0 : dp(6));
                sp.setLayoutParams(lp);
                row.addView(sp);
            }
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ─── Leer archivo local ──────────────────────────────────────────────────

    private void readFileAndImport(Uri uri) {
        // Obtener nombre del archivo para validar extensión
        String fileName = "";
        try (android.database.Cursor c = getContentResolver().query(
                uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) fileName = c.getString(idx);
            }
        } catch (Exception ignored) {}

        String lower = fileName.toLowerCase();
        if (!lower.endsWith(".m3u") && !lower.endsWith(".m3u8") && !lower.endsWith(".txt")) {
            // Avisamos pero dejamos continuar — el contenido lo valida processM3uContent
            tvStatus.setText("⚠️ El archivo no tiene extensión .m3u — se intentará igualmente");
        }

        setLoading(true, "Leyendo archivo...");

        executor.execute(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) throw new Exception("No se pudo abrir el archivo");

                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append("\n");
                }

                final String content = sb.toString();
                handler.post(() -> processM3uContent(content, ""));

            } catch (Exception e) {
                handler.post(() -> {
                    setLoading(false, null);
                    tvStatus.setText("❌ Error al leer el archivo: " + e.getMessage());
                });
            }
        });
    }

    // ─── Descarga ────────────────────────────────────────────────────────────

    /**
     * @param countryName nombre del país a asignar a los canales que no tengan tvg-country.
     *                    Vacío si se importa desde URL manual.
     */
    private void importFromUrl(String urlStr, String countryName) {
        String msg = countryName.isEmpty()
            ? "Descargando lista M3U..."
            : "Descargando " + countryName + "...";
        setLoading(true, msg);

        executor.execute(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    handler.post(() -> { setLoading(false, null);
                        tvStatus.setText("❌ Error al descargar: código " + code); });
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append("\n");
                }
                conn.disconnect();

                final String content = sb.toString();
                final String cn = countryName;
                handler.post(() -> processM3uContent(content, cn));

            } catch (Exception e) {
                handler.post(() -> { setLoading(false, null);
                    tvStatus.setText("❌ Error de conexión: " + e.getMessage()); });
            }
        });
    }

    // ─── Procesamiento ───────────────────────────────────────────────────────

    private void processM3uContent(String content, String countryName) {
        if (!content.contains("#EXTM3U") && !content.contains("#EXTINF")) {
            tvStatus.setText("❌ El contenido no parece ser una lista M3U válida");
            return;
        }
        setLoading(true, "Procesando canales...");

        executor.execute(() -> {
            // Pasar el nombre del país como fallback para canales sin tvg-country
            List<Channel> parsed = M3uParser.parse(content, countryName);

            handler.post(() -> {
                setLoading(false, null);
                if (parsed.isEmpty()) {
                    tvStatus.setText("⚠️ No se encontraron canales");
                    return;
                }
                List<Channel> existing = ChannelStore.loadM3uChannels(this);
                int added = 0;
                for (Channel n : parsed) {
                    boolean dup = false;
                    for (Channel e : existing) { if (e.getUrl().equals(n.getUrl())) { dup = true; break; } }
                    if (!dup) { existing.add(n); added++; }
                }
                ChannelStore.saveM3uChannels(this, existing);
                refreshCount();
                String prefix = countryName.isEmpty() ? "" : countryName + ": ";
                tvStatus.setText("✅ " + prefix + added + " canales importados ("
                    + (parsed.size() - added) + " duplicados omitidos)");
                etPaste.setText(""); etUrl.setText("");
            });
        });
    }

    private void refreshCount() {
        tvChannelCount.setText("Canales M3U guardados: " + ChannelStore.loadM3uChannels(this).size());
    }

    private void setLoading(boolean on, String msg) {
        progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
        btnImportUrl.setEnabled(!on);
        btnImportFile.setEnabled(!on);
        btnImportPaste.setEnabled(!on);
        for (int i = 0; i < countryGrid.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) countryGrid.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); j++) row.getChildAt(j).setEnabled(!on);
        }
        if (msg != null) tvStatus.setText(msg);
    }

    @Override
    protected void onDestroy() { super.onDestroy(); executor.shutdown(); }
}
