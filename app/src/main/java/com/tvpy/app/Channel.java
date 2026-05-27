package com.tvpy.app;

public class Channel {
    private String name;
    private String url;
    private String emoji;
    private String category;
    private String country;      // ← nuevo campo
    private int backgroundColor;

    // Constructor completo (con país)
    public Channel(String name, String url, String emoji, String category, String country, int backgroundColor) {
        this.name = name;
        this.url = url;
        this.emoji = emoji;
        this.category = sanitizeCategory(category);
        this.country = country != null ? country : "";
        this.backgroundColor = backgroundColor;
    }

    // Constructor legacy sin país (compatibilidad con ChannelData existente)
    public Channel(String name, String url, String emoji, String category, int backgroundColor) {
        this(name, url, emoji, category, "", backgroundColor);
    }

    private static String sanitizeCategory(String cat) {
        if (cat == null) return "";
        cat = cat.trim();
        if (cat.contains(";")) {
            String[] parts = cat.split(";");
            if (parts.length > 0) {
                cat = parts[0].trim();
            }
        }
        return translateCategory(cat);
    }

    private static String translateCategory(String cat) {
        if (cat == null) return "";
        String lower = cat.toLowerCase().trim();
        switch (lower) {
            case "news":
            case "noticias":
                return "Noticias";
            case "sports":
            case "sport":
            case "deportes":
            case "deporte":
                return "Deportes";
            case "movies":
            case "movie":
            case "películas":
            case "película":
                return "Películas";
            case "music":
            case "música":
                return "Música";
            case "kids":
            case "kid":
            case "infantil":
            case "niños":
            case "niño":
                return "Infantil";
            case "documentary":
            case "documentaries":
            case "documentales":
            case "documental":
                return "Documentales";
            case "religious":
            case "religion":
            case "religioso":
            case "religión":
                return "Religioso";
            case "entertainment":
            case "entretenimiento":
                return "Entretenimiento";
            case "general":
                return "General";
            case "lifestyle":
            case "estilo de vida":
                return "Estilo de vida";
            case "relax":
            case "relajación":
                return "Relajación";
            case "business":
            case "negocios":
            case "negocio":
                return "Negocios";
            case "family":
            case "familiar":
            case "familia":
                return "Familiar";
            case "comedy":
            case "comedia":
                return "Comedia";
            case "education":
            case "educación":
                return "Educación";
            case "science":
            case "ciencia":
                return "Ciencia";
            case "travel":
            case "viajes":
            case "viaje":
                return "Viajes";
            case "auto":
            case "automovilismo":
                return "Automovilismo";
            case "classic":
            case "classics":
            case "clásicos":
            case "clásico":
                return "Clásicos";
            case "outdoor":
            case "outdoors":
            case "aire libre":
                return "Aire Libre";
            case "series":
                return "Series";
            case "legislative":
            case "legislativo":
                return "Legislativo";
            case "culture":
            case "cultura":
                return "Cultura";
            case "weather":
            case "clima":
                return "Clima";
            case "shop":
            case "shopping":
            case "compras":
                return "Compras";
            case "animation":
            case "animación":
                return "Animación";
            case "cooking":
            case "kitchen":
            case "cocina":
                return "Cocina";
            case "diy":
            case "bricolaje":
                return "Bricolaje";
            case "food":
            case "comida":
                return "Comida";
            case "game":
            case "games":
            case "juegos":
            case "juego":
                return "Juegos";
            case "health":
            case "salud":
                return "Salud";
            case "history":
            case "historia":
                return "Historia";
            case "hobby":
            case "hobbies":
            case "pasatiempos":
                return "Pasatiempos";
            case "home":
            case "hogar":
                return "Hogar";
            case "military":
            case "militar":
                return "Militar";
            case "mystery":
            case "misterio":
                return "Misterio";
            case "nature":
            case "naturaleza":
                return "Naturaleza";
            case "quiz":
            case "concursos":
                return "Concursos";
            case "reality":
            case "telerrealidad":
                return "Telerrealidad";
            case "sci-fi":
            case "science fiction":
            case "ciencia ficción":
                return "Ciencia Ficción";
            case "spiritual":
            case "espiritual":
                return "Espiritual";
            case "talent":
            case "talentos":
                return "Talentos";
            case "tech":
            case "technology":
            case "tecnología":
                return "Tecnología";
            case "youth":
            case "juvenil":
                return "Juvenil";
            default:
                if (cat.length() > 0) {
                    return cat.substring(0, 1).toUpperCase() + cat.substring(1);
                }
                return cat;
        }
    }

    public String getName()           { return name; }
    public String getUrl()            { return url; }
    public String getEmoji()          { return emoji; }
    public String getCategory()       { return category; }
    public String getCountry()        { return country; }
    public int    getBackgroundColor(){ return backgroundColor; }
}
