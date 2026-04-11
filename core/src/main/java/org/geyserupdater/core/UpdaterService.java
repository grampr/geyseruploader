package org.geyserupdater.core;

import org.geyserupdater.core.logging.LogAdapter;
import org.geyserupdater.core.util.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class UpdaterService {
    private static final String BASE = "https://download.geysermc.org/v2/projects";
    private static final String MODRINTH_BASE = "https://api.modrinth.com/v2/project";
    private static final String USER_AGENT = "GeyserUpdater/1.1";

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

    private static class DownloadSpec {
        final String url;
        final String downloadKey;
        final String version;
        final int build;
        final String expectedSha256;

        DownloadSpec(String url, String downloadKey, String version, int build, String expectedSha256) {
            this.url = url;
            this.downloadKey = downloadKey;
            this.version = version;
            this.build = build;
            this.expectedSha256 = expectedSha256;
        }
    }

    private static class BuildMetadata {
        final String version;
        final int build;
        final Map<String, DownloadInfo> downloads;

        BuildMetadata(String version, int build, Map<String, DownloadInfo> downloads) {
            this.version = version;
            this.build = build;
            this.downloads = downloads;
        }
    }

    private static class DownloadInfo {
        final String name;
        final String sha256;

        DownloadInfo(String name, String sha256) {
            this.name = name;
            this.sha256 = sha256;
        }
    }

    private static class NoCompatibleBuildException extends IOException {
        NoCompatibleBuildException(String message) {
            super(message);
        }
    }

    public List<UpdateOutcome> checkAndUpdate(Platform platform, Path pluginsDir) {
        List<Project> targets = collectTargets();
        if (targets.isEmpty()) {
            log.info("No update targets are enabled in config.");
            return Collections.emptyList();
        }

        log.info("Starting update check for " + platform.displayName() + " (targets=" + targets + ")");

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
        Path tmp = null;
        try {
            Path baseDir = resolveProjectDirectory(project, pluginsDir);
            Files.createDirectories(baseDir);

            Path existing = findExistingJar(project, baseDir);
            DownloadSpec spec = resolveDownloadSpec(project, platform);

            log.info("Resolved latest " + project.apiName() + " version " + spec.version + " build #" + spec.build
                    + " for " + platform.displayName() + " using download key '" + spec.downloadKey + "'.");

            if (existing != null && Files.exists(existing) && spec.expectedSha256 != null && !spec.expectedSha256.isBlank()) {
                try {
                    String currentHash = FileUtils.sha256(existing);
                    if (spec.expectedSha256.equalsIgnoreCase(currentHash)) {
                        log.info(project.apiName() + " is already up to date (matched upstream SHA-256). Skipping download.");
                        return new UpdateOutcome(project, false, true, Optional.empty());
                    }
                } catch (IOException hashError) {
                    log.warn("Could not hash existing file for " + project.apiName() + ": " + hashError.getMessage());
                }
            }

            tmp = Files.createTempFile("geyserupdater-" + project.apiName(), ".jar");
            long bytes = downloadTo(spec.url, tmp);
            log.info("Downloaded " + project.apiName() + " build artifact (" + bytes + " bytes).");

            if (spec.expectedSha256 != null && !spec.expectedSha256.isBlank()) {
                String actual = FileUtils.sha256(tmp);
                if (!spec.expectedSha256.equalsIgnoreCase(actual)) {
                    String err = "Checksum mismatch from upstream metadata";
                    log.warn(project.apiName() + " " + err + " (expected=" + spec.expectedSha256 + ", actual=" + actual + ")");
                    return new UpdateOutcome(project, false, false, Optional.of(err));
                }
            }

            if (existing != null && Files.exists(existing)) {
                try {
                    String newSha = FileUtils.sha256(tmp);
                    String oldSha = FileUtils.sha256(existing);
                    if (newSha.equalsIgnoreCase(oldSha)) {
                        log.info(project.apiName() + " downloaded artifact is identical to existing file.");
                        return new UpdateOutcome(project, false, true, Optional.empty());
                    }
                } catch (IOException e) {
                    log.warn("Hash compare failed for " + project.apiName() + "; continuing with overwrite: " + e.getMessage());
                }
            }

            Path dest = (existing != null) ? existing : defaultDestination(project, platform, baseDir);
            try {
                FileUtils.atomicMove(tmp, dest);
                tmp = null;
                log.info("Updated " + project.apiName() + " at " + dest + ".");
                return new UpdateOutcome(project, true, false, Optional.empty());
            } catch (IOException moveError) {
                if (isLikelyFileLock(moveError)) {
                    Path stagedPath = stageForNextRestart(project, platform, pluginsDir, dest, tmp);
                    if (stagedPath != null) {
                        tmp = null;
                        log.warn("Destination file appears locked; staged update for next restart at: " + stagedPath);
                        return new UpdateOutcome(project, true, false, Optional.empty());
                    }
                }
                throw moveError;
            }
        } catch (NoCompatibleBuildException noBuild) {
            String message = noBuild.getMessage();
            log.warn("No compatible build for " + project.apiName() + ": " + message);
            return new UpdateOutcome(project, false, false, Optional.of(message));
        } catch (Exception ex) {
            log.error("Unexpected update failure for " + project.apiName(), ex);
            return new UpdateOutcome(project, false, false, Optional.of(ex.getMessage()));
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // ignore temp cleanup failures
                }
            }
        }
    }

    private DownloadSpec resolveDownloadSpec(Project project, Platform platform) throws IOException {
        if (project == Project.MCXBOXBROADCAST) {
            String url = fetchModrinthLatestUrl(project.apiName());
            return new DownloadSpec(url, "modrinth", "latest", 0, null);
        }

        BuildMetadata metadata = fetchLatestBuildMetadata(project);
        String downloadKey = resolveDownloadKey(metadata, platform, project);
        DownloadInfo selected = metadata.downloads.get(downloadKey);

        String url = BASE + "/" + project.apiName()
                + "/versions/" + metadata.version
                + "/builds/" + metadata.build
                + "/downloads/" + downloadKey;

        return new DownloadSpec(url, downloadKey, metadata.version, metadata.build, selected == null ? null : selected.sha256);
    }

    private String resolveDownloadKey(BuildMetadata metadata, Platform platform, Project project) throws NoCompatibleBuildException {
        for (String key : platform.downloadKeyCandidates()) {
            if (metadata.downloads.containsKey(key)) {
                return key;
            }
        }

        String available = metadata.downloads.keySet().stream().collect(Collectors.joining(", "));
        throw new NoCompatibleBuildException(
                "No compatible " + project.apiName() + " build available for " + platform.displayName()
                        + " yet. Available downloads: [" + available + "]"
        );
    }

    private BuildMetadata fetchLatestBuildMetadata(Project project) throws IOException {
        String url = BASE + "/" + project.apiName() + "/versions/latest/builds/latest";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IOException("HTTP " + resp.statusCode() + " when fetching " + url);
            }
            return parseBuildMetadata(resp.body(), project);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private BuildMetadata parseBuildMetadata(String json, Project project) throws IOException {
        String version = extractJsonStringValue(json, "\"version\"");
        Integer build = extractJsonIntValue(json, "\"build\"");
        String downloadsBody = extractJsonObjectBody(json, "\"downloads\"");

        if (version == null || build == null || downloadsBody == null) {
            throw new IOException("Malformed build metadata response for " + project.apiName());
        }

        Map<String, String> downloadObjects = extractTopLevelObjectEntries(downloadsBody);
        Map<String, DownloadInfo> downloads = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : downloadObjects.entrySet()) {
            String name = extractJsonStringValue(entry.getValue(), "\"name\"");
            String sha256 = extractJsonStringValue(entry.getValue(), "\"sha256\"");
            downloads.put(entry.getKey(), new DownloadInfo(name, sha256));
        }

        return new BuildMetadata(version, build, downloads);
    }

    private long downloadTo(String url, Path target) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        log.info("Downloading from: " + url);

        try {
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                try (InputStream in = resp.body()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return Files.size(target);
            }
            throw new IOException("HTTP " + resp.statusCode() + " when downloading " + url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private Path findExistingJar(Project project, Path baseDir) throws IOException {
        if (!Files.exists(baseDir)) {
            return null;
        }

        try {
            List<Path> matches = Files.list(baseDir)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".jar") && name.contains(project.fileHint());
                    })
                    .collect(Collectors.toList());
            if (matches.isEmpty()) {
                return null;
            }

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
                    case SPIGOT:
                    case PAPER:
                        filename = "Geyser-Spigot.jar";
                        break;
                    case BUNGEECORD:
                        filename = "Geyser-BungeeCord.jar";
                        break;
                    case VELOCITY:
                        filename = "Geyser-Velocity.jar";
                        break;
                    default:
                        filename = "Geyser.jar";
                }
                break;
            case FLOODGATE:
                switch (platform) {
                    case SPIGOT:
                    case PAPER:
                        filename = "floodgate-spigot.jar";
                        break;
                    case BUNGEECORD:
                        filename = "floodgate-bungee.jar";
                        break;
                    case VELOCITY:
                        filename = "floodgate-velocity.jar";
                        break;
                    default:
                        filename = "floodgate.jar";
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
                .header("User-Agent", USER_AGENT)
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
            String fileUrl = extractJsonStringValue(obj, "\"url\"");
            if (firstUrl == null && fileUrl != null) {
                firstUrl = fileUrl;
            }
            if (obj.contains("\"primary\":true") && fileUrl != null) {
                return fileUrl;
            }
        }
        if (firstUrl != null) return firstUrl;
        throw new IOException("No file url found in Modrinth response");
    }

    private Integer extractJsonIntValue(String obj, String key) {
        int keyIdx = obj.indexOf(key);
        if (keyIdx < 0) return null;
        int colon = obj.indexOf(':', keyIdx + key.length());
        if (colon < 0) return null;

        int i = colon + 1;
        while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < obj.length() && Character.isDigit(obj.charAt(i))) {
            i++;
        }
        if (start == i) return null;

        try {
            return Integer.parseInt(obj.substring(start, i));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractJsonObjectBody(String obj, String key) {
        int keyIdx = obj.indexOf(key);
        if (keyIdx < 0) return null;
        int colon = obj.indexOf(':', keyIdx + key.length());
        if (colon < 0) return null;

        int braceStart = obj.indexOf('{', colon + 1);
        if (braceStart < 0) return null;

        int braceEnd = findMatchingBracket(obj, braceStart, '{', '}');
        if (braceEnd < 0) return null;

        return obj.substring(braceStart + 1, braceEnd);
    }

    private Map<String, String> extractTopLevelObjectEntries(String objectBody) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        int i = 0;
        while (i < objectBody.length()) {
            while (i < objectBody.length()) {
                char c = objectBody.charAt(i);
                if (c == ',' || Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                break;
            }
            if (i >= objectBody.length()) {
                break;
            }
            if (objectBody.charAt(i) != '"') {
                throw new IOException("Malformed JSON object entry");
            }

            int keyEnd = findStringEnd(objectBody, i + 1);
            if (keyEnd < 0) {
                throw new IOException("Malformed JSON key in downloads map");
            }
            String key = objectBody.substring(i + 1, keyEnd);
            i = keyEnd + 1;

            while (i < objectBody.length() && Character.isWhitespace(objectBody.charAt(i))) {
                i++;
            }
            if (i >= objectBody.length() || objectBody.charAt(i) != ':') {
                throw new IOException("Malformed JSON object entry");
            }
            i++;

            while (i < objectBody.length() && Character.isWhitespace(objectBody.charAt(i))) {
                i++;
            }
            if (i >= objectBody.length() || objectBody.charAt(i) != '{') {
                throw new IOException("Expected JSON object value for key " + key);
            }

            int valueStart = i;
            int valueEnd = findMatchingBracket(objectBody, valueStart, '{', '}');
            if (valueEnd < 0) {
                throw new IOException("Malformed JSON object for key " + key);
            }

            out.put(key, objectBody.substring(valueStart, valueEnd + 1));
            i = valueEnd + 1;
        }
        return out;
    }

    private int findMatchingBracket(String s, int start, char open, char close) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                i = findStringEnd(s, i + 1);
                if (i < 0) return -1;
                continue;
            }
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private int findStringEnd(String s, int start) {
        boolean escaping = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (c == '\\') {
                escaping = true;
                continue;
            }
            if (c == '"') {
                return i;
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

    private boolean isLikelyFileLock(IOException e) {
        if (e instanceof AccessDeniedException) {
            return true;
        }
        if (e instanceof FileSystemException) {
            String reason = ((FileSystemException) e).getReason();
            if (reason != null) {
                String r = reason.toLowerCase(Locale.ROOT);
                if (r.contains("used by another process") || r.contains("access is denied") || r.contains("resource busy")) {
                    return true;
                }
            }
        }
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String m = message.toLowerCase(Locale.ROOT);
        return m.contains("used by another process") || m.contains("access is denied") || m.contains("resource busy");
    }

    private Path stageForNextRestart(Project project, Platform platform, Path pluginsDir, Path dest, Path downloadedTmp) throws IOException {
        if (platform != Platform.SPIGOT && platform != Platform.PAPER) {
            return null;
        }

        Path stagedDir;
        if (project == Project.GEYSER || project == Project.FLOODGATE) {
            // Spigot/Paper applies jars from plugins/update on startup.
            stagedDir = pluginsDir.resolve("update");
        } else if (project == Project.MCXBOXBROADCAST) {
            // Geyser extensions have no built-in updater folder, so we stage in extensions/update.
            stagedDir = resolveProjectDirectory(project, pluginsDir).resolve("update");
        } else {
            return null;
        }

        Files.createDirectories(stagedDir);
        Path staged = stagedDir.resolve(dest.getFileName().toString());
        FileUtils.atomicMove(downloadedTmp, staged);
        return staged;
    }
}
