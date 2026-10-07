package com.tvpy.app;

public class LiveEvent {
    private String id;
    private String title;
    private String time;
    private String tournament;
    private String flagUrl;
    private String eventUrl;
    private boolean isLive;

    public LiveEvent(String id, String title, String time, String tournament, String flagUrl, String eventUrl, boolean isLive) {
        this.id = id;
        this.title = title;
        this.time = time;
        this.tournament = tournament;
        this.flagUrl = flagUrl;
        this.eventUrl = eventUrl;
        this.isLive = isLive;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getTime() { return time; }
    public String getTournament() { return tournament; }
    public String getFlagUrl() { return flagUrl; }
    public String getEventUrl() { return eventUrl; }
    public boolean isLive() { return isLive; }
}
