package com.tvpy.app;

public class LiveEvent {
    private String id;
    private String title;
    private String time;
    private String tournament;
    private String sport;
    private String flagUrl;
    private String eventUrl;
    private boolean isLive;

    public LiveEvent(String id, String title, String time, String tournament, String sport, String flagUrl, String eventUrl, boolean isLive) {
        this.id = id;
        this.title = title;
        this.time = time;
        this.tournament = tournament;
        this.sport = sport != null && !sport.isEmpty() ? sport : "Fútbol";
        this.flagUrl = flagUrl;
        this.eventUrl = eventUrl;
        this.isLive = isLive;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getTime() { return time; }
    public String getTournament() { return tournament; }
    public String getSport() { return sport; }
    public String getFlagUrl() { return flagUrl; }
    public String getEventUrl() { return eventUrl; }
    public boolean isLive() { return isLive; }

    public String getSportEmoji() {
        if (sport == null) return "🏆";
        String s = sport.toLowerCase();
        if (s.contains("fútbol") || s.contains("futbol") || s.contains("soccer")) return "⚽";
        if (s.contains("baloncesto") || s.contains("basket") || s.contains("nba")) return "🏀";
        if (s.contains("tenis") || s.contains("tennis") || s.contains("atp") || s.contains("wta")) return "🎾";
        if (s.contains("hockey") || s.contains("nhl")) return "🏒";
        if (s.contains("combate") || s.contains("box") || s.contains("ufc") || s.contains("mma") || s.contains("judo")) return "🥊";
        if (s.contains("motor") || s.contains("f1") || s.contains("fórmula") || s.contains("moto")) return "🏎️";
        if (s.contains("voleibol") || s.contains("voley")) return "🏐";
        if (s.contains("balonmano") || s.contains("handball")) return "🤾";
        if (s.contains("béisbol") || s.contains("beisbol") || s.contains("baseball") || s.contains("mlb")) return "⚾";
        if (s.contains("fútbol americano") || s.contains("nfl")) return "🏈";
        if (s.contains("rugby")) return "🏉";
        if (s.contains("críquet") || s.contains("cricket")) return "🏏";
        return "🏆";
    }
}
