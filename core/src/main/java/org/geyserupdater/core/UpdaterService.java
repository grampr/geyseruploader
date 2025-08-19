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
        return targets;
    }

    private UpdateOutcome updateOne(Project project, Platform platform, Path pluginsDir) {
        try {
            Path existing = findExistingJar(project, pluginsDir);
            String downloadUrl = BASE + "/" + project.apiName() + "/versions/latest/builds/latest/downloads/" + platform.apiName();

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
            Path dest = (existing != null) ? existing : defaultDestination(project, platform, pluginsDir);
            // Move atomically
            FileUtils.atomicMove(tmp, dest);

            return new UpdateOutcome(project, true, false, Optional.empty());
        } catch (Exception ex) {
            return new UpdateOutcome(project, false, false, Optional.of(ex.getMessage()));
        }
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

    private Path findExistingJar(Project project, Path pluginsDir) throws IOException {
        if (!Files.exists(pluginsDir)) return null;
        try {
            List<Path> matches = Files.list(pluginsDir)
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

    private Path defaultDestination(Project project, Platform platform, Path pluginsDir) {
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
            default:
                filename = "plugin.jar";
        }
        return pluginsDir.resolve(filename);
    }
}