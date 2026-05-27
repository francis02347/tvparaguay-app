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
    private LinearLayout countryChips;  // fila 1: Todos | ❤️ | países
    private LinearLayout genreChips;    // fila 2: géneros
    private LinearLayout alphaBar;
    private TextView       tvAlphaPopup;
    private View           deleteBar;
    private TextView       tvDeleteCount;

    private final List<Character> alphaLetters = new ArrayList<>();

    private String activeCountry = "Todos";
    private boolean showingFavorites = false;
    private String activeGenre = "";

    private static final String ALL  = "Todos";
    private static final String FAVS = "❤️";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView  = findViewById(R.id.recyclerView);
        searchBar     = findViewById(R.id.searchBar);
        tvNoResults   = findViewById(R.id.tvNoResults);
        tvM3uBadge    = findViewById(R.id.tvM3uBadge);
        countryChips  = findViewById(R.id.countryChips);
        genreChips    = findViewById(R.id.genreChips);
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

        findViewById(R.id.btnImportM3u).setOnClickListener(v ->
            startActivity(new Intent(this, ImportM3uActivity.class)));

        // ─── Verificar actualizaciones OTA ──────────────────────────────
        new UpdateManager(this).checkForUpdates();

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
                buildChips();
                buildGenreChips();
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
        buildChips();
        buildGenreChips();
        applyFilter();
    }

    private void loadAllChannels() {
        allChannels = new ArrayList<>();
        allChannels.addAll(ChannelData.getChannels());
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

    private void buildChips() {
        Set<String> countries = new LinkedHashSet<>();
        for (Channel ch : allChannels) {
            if (ch.getCountry() != null && !ch.getCountry().isEmpty())
                countries.add(ch.getCountry());
        }

        countryChips.removeAllViews();

        // "Todos"
        addChip(ALL,  !showingFavorites && ALL.equals(activeCountry));
        // "❤️ Favoritos"
        addChip(FAVS, showingFavorites);
        // países
        for (String co : countries)
            addChip(co, !showingFavorites && co.equals(activeCountry));
    }

    private void addChip(String label, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextColor(0xFFFFFFFF);
        chip.setTextSize(12f);
        chip.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setBackground(getDrawable(R.drawable.chip_filter));
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

        chip.setOnClickListener(v -> {
            if (FAVS.equals(label)) {
                showingFavorites = true;
            } else {
                showingFavorites = false;
                activeCountry = label;
            }
            activeGenre = ""; // reset género al cambiar país
            buildChips();
            buildGenreChips();
            applyFilter();
        });

        countryChips.addView(chip);
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }


    // ─── Chips fila 2: géneros ────────────────────────────────────────────────

    private void buildGenreChips() {
        // Recopilar géneros presentes en los canales actualmente visibles según filtro de país
        java.util.LinkedHashSet<String> genres = new java.util.LinkedHashSet<>();
        for (Channel ch : allChannels) {
            // Aplicar filtro de país/favorito para mostrar solo géneros relevantes
            boolean matchCountry = showingFavorites
                    || ALL.equals(activeCountry)
                    || activeCountry.equals(ch.getCountry());
            if (matchCountry && ch.getCategory() != null && !ch.getCategory().isEmpty()) {
                genres.add(ch.getCategory());
            }
        }

        genreChips.removeAllViews();

        // Chip "Todos" (sin filtro de género)
        addGenreChip("", "Todos", activeGenre.isEmpty());

        for (String genre : genres) {
            addGenreChip(genre, genre, genre.equals(activeGenre));
        }
    }

    private void addGenreChip(String genreValue, String label, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextColor(0xFFFFFFFF);
        chip.setTextSize(12f);
        chip.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        chip.setBackground(getDrawable(R.drawable.chip_filter));
        chip.setGravity(android.view.Gravity.CENTER);
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

        chip.setOnClickListener(v -> {
            activeGenre = genreValue;
            buildGenreChips();
            applyFilter();
        });

        genreChips.addView(chip);
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

            if (matchSearch && matchFav && matchCountry && matchGenre) filteredChannels.add(ch);
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
