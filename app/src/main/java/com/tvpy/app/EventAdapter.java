package com.tvpy.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventClick(LiveEvent event);
    }

    private List<LiveEvent> events;
    private final OnEventClickListener listener;

    public EventAdapter(List<LiveEvent> events, OnEventClickListener listener) {
        this.events = events;
        this.listener = listener;
    }

    public void updateEvents(List<LiveEvent> newEvents) {
        this.events = newEvents;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        LiveEvent event = events.get(position);
        holder.bind(event, listener);
    }

    @Override
    public int getItemCount() {
        return events != null ? events.size() : 0;
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardEvent;
        TextView tvEventIcon;
        TextView tvEventTitle;
        TextView tvEventTournament;
        TextView tvEventTime;
        TextView tvLiveBadge;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            cardEvent = itemView.findViewById(R.id.cardEvent);
            tvEventIcon = itemView.findViewById(R.id.tvEventIcon);
            tvEventTitle = itemView.findViewById(R.id.tvEventTitle);
            tvEventTournament = itemView.findViewById(R.id.tvEventTournament);
            tvEventTime = itemView.findViewById(R.id.tvEventTime);
            tvLiveBadge = itemView.findViewById(R.id.tvLiveBadge);
        }

        void bind(LiveEvent event, OnEventClickListener listener) {
            tvEventTitle.setText(event.getTitle());
            tvEventTournament.setText(event.getTournament().isEmpty() ? "Fútbol Internacional" : event.getTournament());
            tvEventTime.setText(event.getTime());

            if (event.isLive()) {
                tvLiveBadge.setVisibility(View.VISIBLE);
                cardEvent.setStrokeColor(0xFFEF4444); // Red stroke for live match
            } else {
                tvLiveBadge.setVisibility(View.GONE);
                cardEvent.setStrokeColor(0xFF10B981); // Emerald green for upcoming
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onEventClick(event);
            });

            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(8f).setDuration(150).start();
                    cardEvent.setCardElevation(12f);
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150).start();
                    cardEvent.setCardElevation(2f);
                }
            });
        }
    }
}
