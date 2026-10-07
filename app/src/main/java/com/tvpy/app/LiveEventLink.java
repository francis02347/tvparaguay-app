package com.tvpy.app;

public class LiveEventLink {
    private String url;
    private String flagId;
    private String language;
    private String bitrate;
    private String quality;
    private String playerType;

    public LiveEventLink(String url, String flagId, String language, String bitrate, String quality, String playerType) {
        this.url = url;
        this.flagId = flagId != null ? flagId : "";
        this.language = language != null ? language : "";
        this.bitrate = bitrate != null ? bitrate : "";
        this.quality = quality != null ? quality : "";
        this.playerType = playerType != null ? playerType : "Web";
    }

    public String getUrl() { return url; }
    public String getFlagId() { return flagId; }
    public String getLanguage() { return language; }
    public String getBitrate() { return bitrate; }
    public String getQuality() { return quality; }
    public String getPlayerType() { return playerType; }

    public String getDisplayLanguage() {
        if (language != null && !language.isEmpty() && !language.equalsIgnoreCase("Desconocido")) {
            return language;
        }
        switch (flagId) {
            case "1": return "Ruso";
            case "2": return "Inglés";
            case "3": return "Alemán";
            case "4": return "Francés";
            case "5": return "Italiano";
            case "6": return "Español";
            case "7": return "Portugués";
            case "8": return "Holandés";
            case "9": return "Ucraniano";
            case "11": return "Griego";
            case "16": return "Polaco";
            case "29": return "Checo";
            default: return "Internacional";
        }
    }

    public String getFlagEmoji() {
        switch (flagId) {
            case "1": return "🇷🇺";
            case "2": return "🇬🇧";
            case "3": return "🇩🇪";
            case "4": return "🇫🇷";
            case "5": return "🇮🇹";
            case "6": return "🇪🇸";
            case "7": return "🇧🇷";
            case "8": return "🇳🇱";
            case "9": return "🇺🇦";
            case "11": return "🇬🇷";
            case "16": return "🇵🇱";
            default: return "🌐";
        }
    }
}
