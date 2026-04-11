package org.geyserupdater.core.util;

import org.geyserupdater.core.logging.LogAdapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.stream.Stream;

public final class JarMigrationUtil {
    private JarMigrationUtil() {
    }

    public static void migrateNestedPluginsIfNeeded(Path correctPluginsDir, LogAdapter log) {
        if (correctPluginsDir == null) {
            return;
        }

        Path nested = correctPluginsDir.resolve("plugins");
        if (!Files.isDirectory(nested)) {
            return;
        }

        try (Stream<Path> stream = Files.list(nested)) {
            stream.filter(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".jar") && (name.contains("geyser") || name.contains("floodgate"));
            }).forEach(path -> {
                try {
                    Path dest = correctPluginsDir.resolve(path.getFileName().toString());
                    Files.move(path, dest, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Migrated nested plugin jar: " + path.getFileName());
                } catch (Exception ex) {
                    log.warn("Failed to migrate nested jar " + path + ": " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            log.warn("Nested plugins migration scan failed: " + ex.getMessage());
        }
    }
}
