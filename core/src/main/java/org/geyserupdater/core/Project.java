package org.geyserupdater.core;

public enum Project {
    GEYSER("geyser", "geyser"),
    FLOODGATE("floodgate", "floodgate"),
    MCXBOXBROADCAST("mcxboxbroadcast", "mcxboxbroadcast"),
    VIAVERSION("viaversion", "ViaVersion"),
    VIABACKWARDS("viabackwards", "ViaBackwards"),
    VIAREWIND("viarewind", "ViaRewind");

    private final String apiName;
    private final String fileHint;

    Project(String apiName, String fileHint) {
        this.apiName = apiName;
        this.fileHint = fileHint;
    }

    public String apiName() { return apiName; }

    public String fileHint() { return fileHint; }

    public boolean isViaPlugin() {
        return this == VIAVERSION || this == VIABACKWARDS || this == VIAREWIND;
    }
}
