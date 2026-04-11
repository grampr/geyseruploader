package org.geyserupdater.core;

import java.util.List;

public enum Platform {
    SPIGOT("spigot", "Spigot", List.of("spigot")),
    PAPER("paper", "Paper", List.of("paper", "spigot")),
    BUNGEECORD("bungeecord", "BungeeCord", List.of("bungeecord", "bungee")),
    VELOCITY("velocity", "Velocity", List.of("velocity"));

    private final String apiName;
    private final String displayName;
    private final List<String> downloadKeyCandidates;

    Platform(String apiName, String displayName, List<String> downloadKeyCandidates) {
        this.apiName = apiName;
        this.displayName = displayName;
        this.downloadKeyCandidates = downloadKeyCandidates;
    }

    public String apiName() {
        return apiName;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> downloadKeyCandidates() {
        return downloadKeyCandidates;
    }
}
