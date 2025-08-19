package org.geyserupdater.core;

public enum Project {
    GEYSER("geyser", "geyser"),
    FLOODGATE("floodgate", "floodgate");

    private final String apiName;
    private final String fileHint;

    Project(String apiName, String fileHint) {
        this.apiName = apiName;
        this.fileHint = fileHint;
    }

    public String apiName() { return apiName; }

    public String fileHint() { return fileHint; }
}