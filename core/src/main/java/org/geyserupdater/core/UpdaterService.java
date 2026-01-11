package org.geyserupdater.core;

import org.geyserupdater.core.logging.LogAdapter;
import org.geyserupdater.core.util.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class UpdaterService {
    private static final String BASE = "https://download.geysermc.org/v2/projects";
    private static final String MODRINTH_BASE = "https://api.modrinth.com/v2/project";
    private final HttpClient http;
    private final LogAdapter log;
    private final Config cfg;

    public UpdaterService(LogAdapter log, Config cfg) {
        this.log = log;
        this.cfg = cfg;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public static class UpdateOutcome {
        public final Project project;
        public final boolean updated;
        public final boolean skippedNoChange;
        public final Optional<String> error;

        public UpdateOutcome(Project project, boolean updated, boolean skippedNoChange, Optional<String> error) {
            this.project = project;
            this.updated = updated;
            this.skippedNoChange = skippedNoChange;
            this.error = error;
        }
    }

    public List<UpdateOutcome> checkAndUpdate(Platform platform, Path pluginsDir) {
        List<Project> targets = collectTargets();
        if (targets.isEmpty()) {
            return Collections.singletonList(new UpdateOutcome(Project.GEYSER, false, false,
                    Optional.of("No targets enabled")));
        }
        List<UpdateOutcome> results = new ArrayList<>();
        for (Project p : targets) {
            results.add(updateOne(p, platform, pluginsDir));
        }
        return results;
    }

    private List<Project> collectTargets() {
        List<Project> targets = new ArrayList<>();
        if (cfg.targets.geyser) targets.add(Project.GEYSER);
        if (cfg.targets.floodgate) targets.add(Project.FLOODGATE);
        if (cfg.targets.mcxboxbroadcast) targets.add(Project.MCXBOXBROADCAST);
        return targets;
    }

    private UpdateOutcome updateOne(Project project, Platform platform, Path pluginsDir) {
        try {
            Path baseDir = resolveProjectDirectory(project, pluginsDir);
            Path existing = findExistingJar(project, baseDir);
            String downloadUrl = resolveDownloadUrl(project, platform);

            Path tmp = Files.createTempFile("geyserupdater-" + project.apiName(), ".jar");
            try {
                downloadTo(downloadUrl, tmp);
            } catch (IOException e) {
                return new UpdateOutcome(project, false, false, Optional.of("Download failed: " + e.getMessage()));
            }

            // If existing file exists, compare hashes
            if (existing != null && Files.exists(existing)) {
                try {
                    String newSha = FileUtils.sha256(tmp);
                    String oldSha = FileUtils.sha256(existing);
                    if (newSha.equalsIgnoreCase(oldSha)) {
                        Files.deleteIfExists(tmp);
                        return new UpdateOutcome(project, false, true, Optional.empty());
                    }
                } catch (IOException e) {
                    // proceed to overwrite if cannot hash
                    log.warn("ハッシュ比較に失敗しました。上書き更新を継続します: " + e.getMessage());
                }
            }

            // Determine destination
            Path dest = (existing != null) ? existing : defaultDestination(project, platform, baseDir);
            // Move atomically
            FileUtils.atomicMove(tmp, dest);

            return new UpdateOutcome(project, true, false, Optional.empty());
        } catch (Exception ex) {
            return new UpdateOutcome(project, false, false, Optional.of(ex.getMessage()));
        }
    }

    private String resolveDownloadUrl(Project project, Platform platform) throws IOException {
        if (project == Project.MCXBOXBROADCAST) {
            return fetchModrinthLatestUrl(project.apiName());
        }
        return BASE + "/" + project.apiName() + "/versions/latest/builds/latest/downloads/" + platform.apiName();
    }

    private void downloadTo(String url, Path target) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                try (InputStream in = resp.body()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                throw new IOException("HTTP " + resp.statusCode() + " when downloading " + url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private Path findExistingJar(Project project, Path baseDir) throws IOException {
        if (!Files.exists(baseDir)) return null;
        try {
            List<Path> matches = Files.list(baseDir)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".jar") &&
                                name.contains(project.fileHint());
                    })
                    .collect(Collectors.toList());
            if (matches.isEmpty()) return null;
            // Prefer jars that also contain platform hint words, but fallback to first
            Optional<Path> preferred = matches.stream().filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.contains("spigot") || n.contains("paper") || n.contains("bungee") || n.contains("velocity");
            }).findFirst();
            return preferred.orElse(matches.get(0));
        } catch (IOException e) {
            throw e;
        }
    }

    private Path defaultDestination(Project project, Platform platform, Path baseDir) {
        String filename;
        switch (project) {
            case GEYSER:
                switch (platform) {
                    case SPIGOT: filename = "Geyser-Spigot.jar"; break;
                    case BUNGEECORD: filename = "Geyser-BungeeCord.jar"; break;
                    case VELOCITY: filename = "Geyser-Velocity.jar"; break;
                    default: filename = "Geyser.jar";
                }
                break;
            case FLOODGATE:
                switch (platform) {
                    case SPIGOT: filename = "floodgate-spigot.jar"; break;
                    case BUNGEECORD: filename = "floodgate-bungee.jar"; break;
                    case VELOCITY: filename = "floodgate-velocity.jar"; break;
                    default: filename = "floodgate.jar";
                }
                break;
            case MCXBOXBROADCAST:
                filename = "mcxboxbroadcast.jar";
                break;
            default:
                filename = "plugin.jar";
        }
        return baseDir.resolve(filename);
    }

    private Path resolveProjectDirectory(Project project, Path pluginsDir) {
        if (project == Project.MCXBOXBROADCAST) {
            return pluginsDir.resolve("Geyser-Spigot").resolve("extensions");
        }
        return pluginsDir;
    }

    private String fetchModrinthLatestUrl(String projectSlug) throws IOException {
        String url = MODRINTH_BASE + "/" + projectSlug + "/version";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "GeyserUpdater/1.0")
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return extractModrinthFileUrl(resp.body());
            }
            throw new IOException("HTTP " + resp.statusCode() + " when fetching " + url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private String extractModrinthFileUrl(String json) throws IOException {
        int filesKey = json.indexOf("\"files\"");
        if (filesKey < 0) throw new IOException("No files array in Modrinth response");
        int arrayStart = json.indexOf('[', filesKey);
        if (arrayStart < 0) throw new IOException("Malformed files array in Modrinth response");
        int arrayEnd = findMatchingBracket(json, arrayStart, '[', ']');
        if (arrayEnd < 0) throw new IOException("Unclosed files array in Modrinth response");
        String filesArray = json.substring(arrayStart + 1, arrayEnd);

        List<String> fileObjects = splitTopLevelObjects(filesArray);
        String firstUrl = null;
        for (String obj : fileObjects) {
            String url = extractJsonStringValue(obj, "\"url\"");
            if (firstUrl == null && url != null) {
                firstUrl = url;
            }
            if (obj.contains("\"primary\":true") && url != null) {
                return url;
            }
        }
        if (firstUrl != null) return firstUrl;
        throw new IOException("No file url found in Modrinth response");
    }

    private int findMatchingBracket(String s, int start, char open, char close) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private List<String> splitTopLevelObjects(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(s.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return out;
    }

    private String extractJsonStringValue(String obj, String key) {
        int keyIdx = obj.indexOf(key);
        if (keyIdx < 0) return null;
        int colon = obj.indexOf(':', keyIdx + key.length());
        if (colon < 0) return null;
        int quoteStart = obj.indexOf('"', colon + 1);
        if (quoteStart < 0) return null;
        StringBuilder sb = new StringBuilder();
        boolean escaping = false;
        for (int i = quoteStart + 1; i < obj.length(); i++) {
            char c = obj.charAt(i);
            if (escaping) {
                sb.append(c);
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return null;
    }
}
