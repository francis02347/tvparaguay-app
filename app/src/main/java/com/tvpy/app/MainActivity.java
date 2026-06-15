package com.tvpy.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChannelAdapter adapter;
    private List<Channel> allChannels;
    private List<Channel> filteredChannels = new ArrayList<>();
    private EditText searchBar;
    private TextView tvNoResults;
    private TextView tvM3uBadge;
    private LinearLayout filterChipsContainer; // Fila única de chips de filtro
    private LinearLayout alphaBar;
    private TextView       tvAlphaPopup;
    private View           deleteBar;
    private TextView       tvDeleteCount;

    private final List<Character> alphaLetters = new ArrayList<>();

    private String activeCountry = "Todos";
    private boolean showingFavorites = false;
    private boolean showingMundial = false;
    private String activeGenre = "";

    private static final String ALL  = "Todos";
    private static final String FAVS = "❤️";

    private static final Set<String> MUNDIAL_CHANNELS = new HashSet<>(Arrays.asList(
        "Azteca Internacional (México)",
        "Canal 5 (Televisa) (México)",
        "FOX (Estados Unidos)",
        "FS1 (Estados Unidos)",
        "Gen",
        "Las Estrellas (México) [Geo-blocked]",
        "Popu TV",
        "RTVE Canal 24 Horas",
        "RTVE La 1",
        "Teledeporte",
        "Telefe Internacional (Argentina)",
        "Telemundo (Estados Unidos)",
        "Trece",
        "TV Globo Bahia (Brasil)",
        "Unicanal",
        "Universo (Estados Unidos)"
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView  = findViewById(R.id.recyclerView);
        searchBar     = findViewById(R.id.searchBar);
        tvNoResults   = findViewById(R.id.tvNoResults);
        tvM3uBadge    = findViewById(R.id.tvM3uBadge);
        filterChipsContainer = findViewById(R.id.filterChipsContainer);
        alphaBar      = findViewById(R.id.alphaBar);
        tvAlphaPopup  = findViewById(R.id.tvAlphaPopup);
        deleteBar     = findViewById(R.id.deleteBar);
        tvDeleteCount = findViewById(R.id.tvDeleteCount);

        int spanCount = isAndroidTV() ? 3 : 1;
        recyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));

        adapter = new ChannelAdapter(new ArrayList<>(), channel -> {
            int index = filteredChannels.indexOf(channel);
            if (index < 0) index = 0;
            ChannelSession.set(filteredChannels, index);
            startActivity(new Intent(this, PlayerActivity.class));
        });
        recyclerView.setAdapter(adapter);

        View btnImportM3u = findViewById(R.id.btnImportM3u);
        if (btnImportM3u != null) {
            btnImportM3u.setOnClickListener(v ->
                startActivity(new Intent(this, ImportM3uActivity.class)));
            btnImportM3u.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            });
        }

        View btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        if (BuildConfig.IS_PLAY_STORE) {
            if (btnCheckUpdate != null) btnCheckUpdate.setVisibility(View.GONE);
        } else {
            if (btnCheckUpdate != null) {
                btnCheckUpdate.setOnClickListener(v -> new UpdateManager(this).checkForUpdates(true));
                btnCheckUpdate.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                    }
                });
            }
        }

        // ─── Verificar actualizaciones OTA (Solo variante de sitio web) ──
        if (!BuildConfig.IS_PLAY_STORE) {
            new UpdateManager(this).checkForUpdates();
        }

        // ── Modo selección / eliminación ──────────────────────────────
        adapter.setSelectionListener(count -> {
            if (count == 0) {
                deleteBar.setVisibility(View.GONE);
            } else {
                deleteBar.setVisibility(View.VISIBLE);
                tvDeleteCount.setText(count + (count == 1 ? " seleccionado" : " seleccionados"));
            }
        });

        findViewById(R.id.btnCancelDelete).setOnClickListener(v -> {
            adapter.clearSelection();
            deleteBar.setVisibility(View.GONE);
        });

        findViewById(R.id.btnConfirmDelete).setOnClickListener(v -> confirmDelete());

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });
    }

    // ─── Eliminar canales seleccionados ──────────────────────────────────────

    private void confirmDelete() {
        int count = adapter.getSelectedCount();
        if (count == 0) return;

        new AlertDialog.Builder(this)
            .setTitle("Eliminar canales")
            .setMessage("¿Eliminás " + count + (count == 1 ? " canal?" : " canales?")
                + "\nEsta acción no se puede deshacer.")
            .setPositiveButton("Eliminar", (d, w) -> {
                java.util.Set<String> toDelete = adapter.getSelectedUrls();

                // Solo se pueden eliminar canales M3U (los hardcodeados no están en el store)
                List<Channel> m3u = ChannelStore.loadM3uChannels(this);
                m3u.removeIf(ch -> toDelete.contains(ch.getUrl()));
                ChannelStore.saveM3uChannels(this, m3u);

                adapter.clearSelection();
                deleteBar.setVisibility(View.GONE);
                loadAllChannels();
                buildFilterChips();
                applyFilter();
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) { adapter.clearSelection(); deleteBar.setVisibility(View.GONE); }
        loadAllChannels();
        buildFilterChips();
        applyFilter();
    }

    private void loadAllChannels() {
        allChannels = new ArrayList<>();
        allChannels.addAll(ChannelData.getChannels(this));
        List<Channel> m3u = ChannelStore.loadM3uChannels(this);
        allChannels.addAll(m3u);

        // Eliminar duplicados de calidad (ej: "GO TV" y "GO TV (1080p)" → queda el mejor)
        allChannels = ChannelDeduplicator.deduplicate(allChannels);

        // Ordenar toda la lista combinada alfabéticamente por nombre
        allChannels.sort((a, b) ->
            a.getName().compareToIgnoreCase(b.getName()));

        if (!m3u.isEmpty()) {
            tvM3uBadge.setVisibility(View.VISIBLE);
            tvM3uBadge.setText(m3u.size() + " M3U");
        } else {
            tvM3uBadge.setVisibility(View.GONE);
        }
    }

    // ─── Chips fila 1: Todos | ❤️ | países ────────────────────────────────────

    private void buildFilterChips() {
        filterChipsContainer.removeAllViews();

        // 1. Chip "Todos"
        boolean isTodosSelected = !showingFavorites && !showingMundial && ALL.equals(activeCountry) && activeGenre.isEmpty();
        addFilterChip(ALL, isTodosSelected, v -> {
            showingFavorites = false;
            showingMundial = false;
            activeCountry = ALL;
            activeGenre = "";
            buildFilterChips();
            applyFilter();
        });

        // 2. Chip "❤️" (Favoritos)
        addFilterChip(FAVS, showingFavorites, v -> {
            showingFavorites = true;
            showingMundial = false;
            activeCountry = ALL;
            activeGenre = "";
            buildFilterChips();
            applyFilter();
        });

        // 2b. Chip "🏆 Mundial 2026"
        addFilterChip("🏆 Mundial 2026", showingMundial, v -> {
            showingFavorites = false;
            showingMundial = true;
            activeCountry = ALL;
            activeGenre = "";
            buildFilterChips();
            applyFilter();
        });

        // 3. Chips de Países
        Set<String> countries = new LinkedHashSet<>();
        for (Channel ch : allChannels) {
            if (ch.getCountry() != null && !ch.getCountry().isEmpty()) {
                countries.add(ch.getCountry());
            }
        }
        for (String co : countries) {
            boolean isCountrySelected = !showingFavorites && !showingMundial && co.equals(activeCountry) && activeGenre.isEmpty();
            addFilterChip(co, isCountrySelected, v -> {
                showingFavorites = false;
                showingMundial = false;
                activeCountry = co;
                activeGenre = "";
                buildFilterChips();
                applyFilter();
            });
        }

        // 4. Chips de Géneros
        Set<String> genres = new LinkedHashSet<>();
        for (Channel ch : allChannels) {
            if (ch.getCategory() != null && !ch.getCategory().isEmpty()) {
                genres.add(ch.getCategory());
            }
        }
        for (String genre : genres) {
            boolean isGenreSelected = !showingFavorites && !showingMundial && ALL.equals(activeCountry) && genre.equals(activeGenre);
            addFilterChip(genre, isGenreSelected, v -> {
                showingFavorites = false;
                showingMundial = false;
                activeCountry = ALL;
                activeGenre = genre;
                buildFilterChips();
                applyFilter();
            });
        }
    }

    private void addFilterChip(String label, boolean selected, View.OnClickListener clickListener) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextColor(0xFFFFFFFF);
        chip.setTextSize(12f);
        chip.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setBackground(getDrawable(R.drawable.chip_filter));
        chip.setSelected(selected);
        chip.setGravity(Gravity.CENTER);
        chip.setAlpha(selected ? 1f : 0.55f);

        int ph = dp(12), pv = dp(7);
        chip.setPadding(ph, pv, ph, pv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(5));
        chip.setLayoutParams(lp);

        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start();
                v.setAlpha(1.0f);
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                v.setAlpha(selected ? 1.0f : 0.55f);
            }
        });

        chip.setOnClickListener(clickListener);

        filterChipsContainer.addView(chip);
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ─── Filtrado ─────────────────────────────────────────────────────────────

    private void applyFilter() {
        if (allChannels == null) return;
        String q = searchBar.getText().toString().toLowerCase().trim();
        Set<String> favUrls = FavoriteStore.loadFavorites(this);

        filteredChannels = new ArrayList<>();
        for (Channel ch : allChannels) {
            boolean matchSearch  = ch.getName().toLowerCase().contains(q)
                                 || ch.getCategory().toLowerCase().contains(q);
            boolean matchFav     = !showingFavorites || favUrls.contains(ch.getUrl());
            boolean matchCountry = showingFavorites
                                 || ALL.equals(activeCountry)
                                 || activeCountry.equals(ch.getCountry());
            boolean matchGenre   = activeGenre.isEmpty()
                                 || activeGenre.equals(ch.getCategory());
            String cleanName = ChannelDeduplicator.cleanName(ch.getName());
            boolean matchMundial = !showingMundial
                                 || MUNDIAL_CHANNELS.contains(cleanName)
                                 || MUNDIAL_CHANNELS.contains(ch.getName());

            if (matchSearch && matchFav && matchCountry && matchGenre && matchMundial) filteredChannels.add(ch);
        }

        adapter.updateChannels(filteredChannels, favUrls);
        boolean empty = filteredChannels.isEmpty();
        tvNoResults.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) tvNoResults.setText(showingFavorites
            ? "❤️ Todavía no tenés canales favoritos"
            : "😕 No se encontraron canales");

        buildAlphaBar();
    }

    // ─── Barra alfabética (puntitos + popup flotante) ────────────────────────

    private void buildAlphaBar() {
        alphaBar.removeAllViews();
        alphaLetters.clear();

        if (isAndroidTV() || !searchBar.getText().toString().isEmpty() || filteredChannels.isEmpty()) {
            alphaBar.setVisibility(View.GONE);
            tvAlphaPopup.setVisibility(View.GONE);
            return;
        }

        TreeSet<Character> lettersSet = new TreeSet<>();
        for (Channel ch : filteredChannels) {
            String name = ch.getName().trim();
            if (!name.isEmpty()) {
                char first = Character.toUpperCase(name.charAt(0));
                if (Character.isLetter(first)) lettersSet.add(first);
            }
        }

        if (lettersSet.isEmpty()) { alphaBar.setVisibility(View.GONE); return; }

        alphaBar.setVisibility(View.VISIBLE);
        alphaLetters.addAll(lettersSet);

        // Dibujar un puntito por cada letra — pequeños, equiespaciados
        for (int i = 0; i < alphaLetters.size(); i++) {
            TextView dot = new TextView(this);
            dot.setText("•");
            dot.setTextColor(0x66FFFFFF);   // blanco semitransparente
            dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 7f);
            dot.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            dot.setLayoutParams(lp);
            alphaBar.addView(dot);
        }

        alphaBar.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                int barH = alphaBar.getHeight();
                if (barH <= 0 || alphaLetters.isEmpty()) return true;
                float ratio = Math.max(0f, Math.min(0.9999f, event.getY() / barH));
                int idx = (int) (ratio * alphaLetters.size());
                char letter = alphaLetters.get(idx);

                // Resaltar el puntito activo
                for (int i = 0; i < alphaBar.getChildCount(); i++) {
                    View child = alphaBar.getChildAt(i);
                    if (child instanceof TextView) {
                        ((TextView) child).setTextColor(i == idx ? 0xFFFFFFFF : 0x66FFFFFF);
                        ((TextView) child).setTextSize(TypedValue.COMPLEX_UNIT_SP, i == idx ? 10f : 7f);
                    }
                }

                // Mostrar letra flotante centrada
                tvAlphaPopup.setText(String.valueOf(letter));
                tvAlphaPopup.setVisibility(View.VISIBLE);

                scrollToLetter(letter);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                // Restablecer puntitos y ocultar popup
                for (int i = 0; i < alphaBar.getChildCount(); i++) {
                    View child = alphaBar.getChildAt(i);
                    if (child instanceof TextView) {
                        ((TextView) child).setTextColor(0x66FFFFFF);
                        ((TextView) child).setTextSize(TypedValue.COMPLEX_UNIT_SP, 7f);
                    }
                }
                tvAlphaPopup.setVisibility(View.GONE);
            }
            return true;
        });
    }

    private void scrollToLetter(char letter) {
        for (int i = 0; i < filteredChannels.size(); i++) {
            String name = filteredChannels.get(i).getName().trim();
            if (!name.isEmpty() && Character.toUpperCase(name.charAt(0)) == letter) {
                ((GridLayoutManager) recyclerView.getLayoutManager())
                    .scrollToPositionWithOffset(i, 0);
                return;
            }
        }
    }

    private boolean isAndroidTV() {
        android.app.UiModeManager uiModeManager = (android.app.UiModeManager) getSystemService(UI_MODE_SERVICE);
        return uiModeManager != null && uiModeManager.getCurrentModeType() == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
    }
}
