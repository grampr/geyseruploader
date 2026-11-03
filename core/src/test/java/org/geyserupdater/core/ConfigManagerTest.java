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

    private Config loadRestartConfig(String content) throws Exception {
        Path folder = temporaryFolder.newFolder().toPath();
        Files.writeString(folder.resolve("config.yml"), content, StandardCharsets.UTF_8);
        Config cfg = new ConfigManager(folder).loadOrCreateDefault();
        assertEquals(content, Files.readString(folder.resolve("config.yml")));
        return cfg;
    }

    @Test
    public void preservesLegacyDelayAndNewDelayTakesPrecedenceIncludingZero() throws Exception {
        String legacy = "postUpdate:\n  restartDelaySeconds: 60\n";
        assertEquals(60, loadRestartConfig(legacy).restart.delay);
        assertEquals(120, loadRestartConfig(legacy + "restart:\n  delay: 120\n").restart.delay);
        assertEquals(0, loadRestartConfig(legacy + "restart:\n  delay: 0\n").restart.delay);
        assertEquals(60, loadRestartConfig(legacy + "restart:\n  notifications:\n    enabled: false\n").restart.delay);
        assertEquals(0, loadRestartConfig("enabled: true\n").restart.delay);
    }

    @Test
    public void loadsCustomCountdownAndSanitizesTimes() throws Exception {
        Config cfg = loadRestartConfig("restart:\n  delay: 30\n  notifications:\n    enabled: true\n"
                + "    times: [30, 5, 60, 5, -1, 0, abc, null, 2.5, true, '1']\n"
                + "    message: '&eRestart in {seconds} seconds'\n");
        assertEquals(java.util.List.of(30, 5, 1), cfg.restart.notifications.times);
        assertEquals("&eRestart in {seconds} seconds", cfg.restart.notifications.message);
        assertTrue(cfg.restart.notifications.enabled);
    }

    @Test
    public void invalidDelayFallsBackWithoutOverflowAndNegativeDelayDisablesWaiting() throws Exception {
        String legacy = "postUpdate:\n  restartDelaySeconds: 60\nrestart:\n  delay: ";
        for (String invalid : java.util.List.of("oops", "9223372036854775808", "4294967296", "2.5", "true", "[10]")) {
            assertEquals(60, loadRestartConfig(legacy + invalid + "\n").restart.delay);
        }
        assertEquals(0, loadRestartConfig(legacy + "-10\n").restart.delay);
    }

    @Test
    public void wrongNotificationTypesKeepDefaultsAndEmptyTimesDisableMessages() throws Exception {
        Config cfg = loadRestartConfig("restart:\n  delay: 120\n  notifications:\n"
                + "    enabled: invalid\n    times: invalid\n    message: [invalid]\n");
        assertTrue(cfg.restart.notifications.enabled);
        assertEquals(new Config.Notifications().times, cfg.restart.notifications.times);
        assertEquals(new Config.Notifications().message, cfg.restart.notifications.message);
        cfg = loadRestartConfig("restart:\n  notifications:\n    enabled: false\n    times: []\n");
        assertFalse(cfg.restart.notifications.enabled);
        assertTrue(cfg.restart.notifications.times.isEmpty());
    }

    @Test
    public void malformedYamlDoesNotCrashAndDisablesAutomaticWork() throws Exception {
        Config cfg = loadRestartConfig("restart: [unclosed\n");
        assertFalse(cfg.enabled);
        assertFalse(cfg.postUpdate.runRestartCommand);
    }
}
