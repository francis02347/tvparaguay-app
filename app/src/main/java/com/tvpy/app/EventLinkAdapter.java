package com.tvpy.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class EventLinkAdapter extends RecyclerView.Adapter<EventLinkAdapter.LinkViewHolder> {

    public interface OnLinkClickListener {
        void onLinkClick(LiveEventLink link);
    }

    private final List<LiveEventLink> links;
    private final OnLinkClickListener listener;

    public EventLinkAdapter(List<LiveEventLink> links, OnLinkClickListener listener) {
        this.links = links;
        this.listener = listener;
    }

    @NonNull
    @Override
    public LinkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event_link, parent, false);
        return new LinkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LinkViewHolder holder, int position) {
        LiveEventLink link = links.get(position);
        holder.bind(link, position + 1, listener);
    }

    @Override
    public int getItemCount() {
        return links != null ? links.size() : 0;
    }

    static class LinkViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardLink;
        TextView tvLinkFlag;
        TextView tvLinkLanguage;
        TextView tvLinkType;
        TextView tvLinkBitrate;
        TextView tvLinkQuality;

        LinkViewHolder(@NonNull View itemView) {
            super(itemView);
            cardLink = itemView.findViewById(R.id.cardLink);
            tvLinkFlag = itemView.findViewById(R.id.tvLinkFlag);
            tvLinkLanguage = itemView.findViewById(R.id.tvLinkLanguage);
            tvLinkType = itemView.findViewById(R.id.tvLinkType);
            tvLinkBitrate = itemView.findViewById(R.id.tvLinkBitrate);
            tvLinkQuality = itemView.findViewById(R.id.tvLinkQuality);
        }

        void bind(LiveEventLink link, int signalIndex, OnLinkClickListener listener) {
            tvLinkFlag.setText(link.getFlagEmoji());
            tvLinkLanguage.setText(link.getDisplayLanguage() + " (Señal " + signalIndex + ")");
            tvLinkType.setText(link.getPlayerType().equals("AceStream") ? "Ace Stream P2P" : "Web Player");

            if (!link.getBitrate().isEmpty()) {
                tvLinkBitrate.setVisibility(View.VISIBLE);
                tvLinkBitrate.setText("• " + link.getBitrate());
            } else {
                tvLinkBitrate.setVisibility(View.GONE);
            }

            if (!link.getQuality().isEmpty() && !link.getQuality().equals("nbsp")) {
                tvLinkQuality.setVisibility(View.VISIBLE);
                tvLinkQuality.setText(link.getQuality().contains("%") ? link.getQuality() : link.getQuality() + "%");
            } else {
                tvLinkQuality.setVisibility(View.GONE);
            }

            if (link.getPlayerType().equals("AceStream")) {
                cardLink.setStrokeColor(0xFF8B5CF6); // Purple for AceStream
            } else {
                cardLink.setStrokeColor(0xFF2563EB); // Blue for Web Player
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onLinkClick(link);
            });

            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.03f).scaleY(1.03f).translationZ(6f).setDuration(150).start();
                    cardLink.setCardElevation(10f);
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150).start();
                    cardLink.setCardElevation(2f);
                }
            });
        }
    }
}
