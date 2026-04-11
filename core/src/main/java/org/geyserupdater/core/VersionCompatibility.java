package org.geyserupdater.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionCompatibility {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");
    private static final Map<Platform, String> MINIMUMS = Map.of(
            Platform.PAPER, "26.1.1",
            Platform.SPIGOT, "1.16.5",
            Platform.VELOCITY, "3.3.0"
    );

    private VersionCompatibility() {
    }

    public static Optional<String> minimumVersion(Platform platform) {
        return Optional.ofNullable(MINIMUMS.get(platform));
    }

    public static CompatibilityResult evaluate(Platform platform, String rawVersion) {
        String normalizedRaw = rawVersion == null ? "" : rawVersion.trim();
        Optional<String> minimum = minimumVersion(platform);
        Optional<String> detected = extractVersion(normalizedRaw);

        if (minimum.isEmpty()) {
            return new CompatibilityResult(platform, normalizedRaw, detected.orElse("unknown"), "none", true, false,
                    "No minimum version check configured for this platform.");
        }

        if (detected.isEmpty()) {
            return new CompatibilityResult(platform, normalizedRaw, "unknown", minimum.get(), true, false,
                    "Could not parse runtime version; continuing in compatibility mode.");
        }

        boolean compatible = compareVersions(detected.get(), minimum.get()) >= 0;
        String message = compatible
                ? "Runtime version is compatible with the updater policy."
                : "Runtime version is below the recommended minimum; updates may fail until platform is upgraded.";

        return new CompatibilityResult(platform, normalizedRaw, detected.get(), minimum.get(), compatible, true, message);
    }

    private static Optional<String> extractVersion(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = VERSION_PATTERN.matcher(input.toLowerCase(Locale.ROOT));
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private static int compareVersions(String left, String right) {
        List<Integer> l = splitVersion(left);
        List<Integer> r = splitVersion(right);
        int max = Math.max(l.size(), r.size());
        for (int i = 0; i < max; i++) {
            int lv = i < l.size() ? l.get(i) : 0;
            int rv = i < r.size() ? r.get(i) : 0;
            if (lv != rv) {
                return Integer.compare(lv, rv);
            }
        }
        return 0;
    }

    private static List<Integer> splitVersion(String version) {
        String[] parts = version.split("\\.");
        List<Integer> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            try {
                out.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                out.add(0);
            }
        }
        return out;
    }

    public record CompatibilityResult(
            Platform platform,
            String rawVersion,
            String parsedVersion,
            String minimumVersion,
            boolean compatible,
            boolean checked,
            String detail
    ) {
    }
}
