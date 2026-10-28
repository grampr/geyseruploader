package org.geyserupdater.core;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Official Hangar release with an explicit match for the running platform version. */
record ViaRelease(String version, String url, String sha256) {
    private static final Pattern RUNTIME = Pattern.compile("^(\\d+\\.\\d+(?:\\.\\d+)?)(?:$|[-+])");

    static String runtimeVersion(String raw) throws IOException {
        Matcher matcher = RUNTIME.matcher(raw == null ? "" : raw.trim());
        if (!matcher.find()) {
            throw new IOException("Cannot determine exact runtime version: " + raw);
        }
        return matcher.group(1);
    }

    static Optional<ViaRelease> select(Project project, Platform platform, String runtimeVersion, String json) throws IOException {
        if (!project.isViaPlugin()) {
            throw new IOException("Not a Via project: " + project);
        }
        if (platform == Platform.BUNGEECORD) return Optional.empty();
        String current = runtimeVersion(runtimeVersion);
        String platformKey = platform == Platform.VELOCITY ? "VELOCITY" : "PAPER";
        Object parsed;
        try {
            // JSON is a YAML subset; use the existing safe parser rather than matching JSON with regex.
            parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(json);
        } catch (RuntimeException e) {
            throw new IOException("Malformed Hangar release metadata", e);
        }
        if (!(parsed instanceof Map<?, ?> page) || !(page.get("result") instanceof List<?> releases)) {
            throw new IOException("Missing Hangar release list");
        }
        ViaRelease selected = null;
        for (Object item : releases) {
            if (!(item instanceof Map<?, ?> release)
                    || !"public".equals(release.get("visibility"))
                    || !(release.get("channel") instanceof Map<?, ?> channel)
                    || !"Release".equals(channel.get("name"))
                    || !(release.get("name") instanceof String version) || !version.matches("\\d+\\.\\d+\\.\\d+")
                    || !(release.get("platformDependencies") instanceof Map<?, ?> dependencies)
                    || !(dependencies.get(platformKey) instanceof List<?> supported)
                    || supported.stream().noneMatch(current::equals)
                    || !(release.get("downloads") instanceof Map<?, ?> downloads)
                    || !(downloads.get(platformKey) instanceof Map<?, ?> asset)
                    || !(asset.get("fileInfo") instanceof Map<?, ?> file)) {
                continue;
            }
            String prefix = "https://hangarcdn.papermc.io/plugins/ViaVersion/" + project.fileHint() + "/versions/";
            if (!(asset.get("downloadUrl") instanceof String url) || !url.startsWith(prefix)
                    || !(file.get("name") instanceof String name)
                    || !name.equals(project.fileHint() + "-" + version + ".jar")
                    || !(file.get("sha256Hash") instanceof String sha256) || !sha256.matches("[0-9a-fA-F]{64}")) {
                throw new IOException("Invalid Hangar download metadata for " + project.fileHint());
            }
            if (selected == null || compareVersions(version, selected.version()) > 0) {
                selected = new ViaRelease(version, url, sha256);
            }
        }
        return Optional.ofNullable(selected);
    }

    static int compareVersions(String left, String right) {
        String[] a = left.split("[.\\-+]"), b = right.split("[.\\-+]");
        for (int i = 0; i < 3; i++) {
            int comparison = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
            if (comparison != 0) return comparison;
        }
        return 0;
    }
}
