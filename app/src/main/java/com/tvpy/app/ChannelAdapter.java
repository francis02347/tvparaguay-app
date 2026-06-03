package com.tvpy.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.android.material.card.MaterialCardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder> {

    private List<Channel> channels;
    private Set<String> favoriteUrls = new HashSet<>();
    private Set<String> selectedUrls = new HashSet<>();
    private OnChannelClickListener listener;
    private OnSelectionListener selectionListener;

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
    }

    public interface OnSelectionListener {
        void onSelectionChanged(int count);
    }

    interface SelectionToggler {
        void toggle(Channel channel);
    }

    public ChannelAdapter(List<Channel> channels, OnChannelClickListener listener) {
        this.channels = channels;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_channel, parent, false);
        return new ChannelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        Channel channel = channels.get(position);
        boolean isFav = favoriteUrls.contains(channel.getUrl());
        boolean isSelected = selectedUrls.contains(channel.getUrl());
        holder.bind(channel, isFav, isSelected, listener, this::toggleSelection, !selectedUrls.isEmpty());
    }

    public void setSelectionListener(OnSelectionListener listener) {
        this.selectionListener = listener;
    }

    public void clearSelection() {
        selectedUrls.clear();
        notifyDataSetChanged();
        if (selectionListener != null) selectionListener.onSelectionChanged(0);
    }

    public int getSelectedCount() {
        return selectedUrls.size();
    }

    public Set<String> getSelectedUrls() {
        return new HashSet<>(selectedUrls);
    }

    private void toggleSelection(Channel channel) {
        String url = channel.getUrl();
        if (selectedUrls.contains(url)) {
            selectedUrls.remove(url);
        } else {
            selectedUrls.add(url);
        }
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedUrls.size());
        }
    }

    @Override
    public int getItemCount() { return channels.size(); }

    public void updateChannels(List<Channel> newChannels, Set<String> favUrls) {
        this.channels = newChannels;
        this.favoriteUrls = favUrls != null ? favUrls : new HashSet<>();
        notifyDataSetChanged();
    }

    // Mantener compatibilidad con llamadas sin favUrls
    public void updateChannels(List<Channel> newChannels) {
        updateChannels(newChannels, this.favoriteUrls);
    }

    static class ChannelViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView tvEmoji;
        TextView tvName;
        TextView tvCategory;
        TextView tvFavBadge;

        ChannelViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView   = itemView.findViewById(R.id.cardView);
            tvEmoji    = itemView.findViewById(R.id.tvEmoji);
            tvName     = itemView.findViewById(R.id.tvName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvFavBadge = itemView.findViewById(R.id.tvFavBadge);
        }

        void bind(Channel channel, boolean isFavorite, boolean isSelected,
                  OnChannelClickListener clickListener, SelectionToggler selectionToggler,
                  boolean isInSelectionMode) {
            tvEmoji.setText(channel.getEmoji());
            tvName.setText(ChannelDeduplicator.cleanName(channel.getName()));
            tvCategory.setText(channel.getCategory());
            
            // Set uniform deep dark background for Option C
            cardView.setCardBackgroundColor(0xFF0D111C);
            
            // Set dynamic category outline color
            int strokeColor = 0xFF334155; // Default Slate
            String category = channel.getCategory() != null ? channel.getCategory().toLowerCase() : "";
            if (category.contains("deport")) {
                strokeColor = 0xFF10B981; // Emerald/Green
            } else if (category.contains("noticia")) {
                strokeColor = 0xFF3B82F6; // Blue
            } else if (category.contains("entretenimiento") || category.contains("cine") || category.contains("serie") || category.contains("película") || category.contains("pelicula")) {
                strokeColor = 0xFF8B5CF6; // Violet
            } else if (category.contains("radio") || category.contains("música") || category.contains("musica")) {
                strokeColor = 0xFFF59E0B; // Amber/Orange
            } else {
                int bg = channel.getBackgroundColor();
                if (bg != 0xFF1C2333 && bg != 0) {
                    strokeColor = bg; // M3U dynamic colors
                }
            }
            cardView.setStrokeColor(strokeColor);
            
            tvFavBadge.setVisibility(isFavorite ? View.VISIBLE : View.GONE);

            // Visual feedback for selection: opacity
            itemView.setAlpha(isSelected ? 0.4f : 1.0f);

            itemView.setOnClickListener(v -> {
                if (isInSelectionMode) {
                    selectionToggler.toggle(channel);
                } else {
                    clickListener.onChannelClick(channel);
                }
            });

            itemView.setOnLongClickListener(v -> {
                selectionToggler.toggle(channel);
                return true;
            });

            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(8f).setDuration(150).start();
                    cardView.setCardElevation(12f);
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150).start();
                    cardView.setCardElevation(4f);
                }
            });
        }
    }
}
