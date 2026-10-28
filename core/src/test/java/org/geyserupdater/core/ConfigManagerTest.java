package org.geyserupdater.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigManagerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadsViaTargetsIndependentlyWithoutChangingExistingConfig() throws Exception {
        Path dataFolder = temporaryFolder.newFolder("via-config").toPath();
        String content = "targets:\n  geyser: false\n  floodgate: false\n"
                + "  viaversion: true\n  viabackwards: false\n  viarewind: true\n";
        Files.writeString(dataFolder.resolve("config.yml"), content, StandardCharsets.UTF_8);

        Config config = new ConfigManager(dataFolder).loadOrCreateDefault();

        assertTrue(config.targets.viaversion);
        assertFalse(config.targets.viabackwards);
        assertTrue(config.targets.viarewind);
        assertEquals(java.util.List.of(Project.VIAVERSION, Project.VIAREWIND),
                new UpdaterService(null, config).collectTargets());
        assertEquals(content, Files.readString(dataFolder.resolve("config.yml")));
    }

    @Test
    public void oldConfigDoesNotEnableViaUpdates() throws Exception {
        Path dataFolder = temporaryFolder.newFolder("old-config").toPath();
        Files.writeString(dataFolder.resolve("config.yml"), "targets:\n  geyser: true\n  floodgate: true\n");

        Config config = new ConfigManager(dataFolder).loadOrCreateDefault();

        assertFalse(config.targets.viaversion);
        assertFalse(config.targets.viabackwards);
        assertFalse(config.targets.viarewind);
        assertEquals(java.util.List.of(Project.GEYSER, Project.FLOODGATE),
                new UpdaterService(null, config).collectTargets());
    }

    @Test
    public void loadsRestartDelaySeconds() throws Exception {
        Path dataFolder = temporaryFolder.newFolder("config").toPath();
        Files.writeString(dataFolder.resolve("config.yml"), String.join("\n",
                "postUpdate:",
                "  runRestartCommand: true",
                "  restartCommand: restart",
                "  restartDelaySeconds: 60",
                ""), StandardCharsets.UTF_8);

        Config config = new ConfigManager(dataFolder).loadOrCreateDefault();

        assertEquals(60, config.postUpdate.restartDelaySeconds);
    }

    @Test
    public void clampsNegativeRestartDelayToZero() throws Exception {
        Path dataFolder = temporaryFolder.newFolder("negative-config").toPath();
        Files.writeString(dataFolder.resolve("config.yml"), String.join("\n",
                "postUpdate:",
                "  restartDelaySeconds: -1",
                ""), StandardCharsets.UTF_8);

        Config config = new ConfigManager(dataFolder).loadOrCreateDefault();

        assertEquals(0, config.postUpdate.restartDelaySeconds);
    }
}
