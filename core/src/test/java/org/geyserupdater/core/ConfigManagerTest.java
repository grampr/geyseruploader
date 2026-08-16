package org.geyserupdater.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class ConfigManagerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

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
