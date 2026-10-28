package org.geyserupdater.core;

import org.junit.Test;

import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UpdaterServiceTest {
    @Test
    public void bungeeViaUpdatePreservesExistingJar() throws Exception {
        Config config = new Config();
        config.targets.geyser = false;
        config.targets.floodgate = false;
        config.targets.viaversion = true;
        org.geyserupdater.core.logging.LogAdapter log = new org.geyserupdater.core.logging.LogAdapter() {
            public void info(String message) { }
            public void warn(String message) { }
            public void error(String message, Throwable error) { fail(message); }
        };
        Path plugins = java.nio.file.Files.createTempDirectory("via-bungee-test");
        Path existing = plugins.resolve("ViaVersion.jar");
        try {
            java.nio.file.Files.writeString(existing, "existing Bungee integration");
            java.util.List<UpdaterService.UpdateOutcome> outcomes =
                    new UpdaterService(log, config).checkAndUpdate(Platform.BUNGEECORD, plugins);
            assertEquals(1, outcomes.size());
            assertFalse(outcomes.get(0).updated);
            assertTrue(outcomes.get(0).error.orElseThrow().contains("BungeeCord"));
            assertEquals("existing Bungee integration", java.nio.file.Files.readString(existing));
        } finally {
            java.nio.file.Files.deleteIfExists(existing);
            java.nio.file.Files.deleteIfExists(plugins);
        }
    }

    @Test
    public void matchesViaPluginsWithoutMixingRelatedPlugins() {
        for (Platform platform : Platform.values()) {
            for (Project project : java.util.List.of(Project.VIAVERSION, Project.VIABACKWARDS, Project.VIAREWIND)) {
                assertTrue(UpdaterService.matchesProjectJarName(project, platform, project.apiName() + ".jar"));
                assertTrue(UpdaterService.matchesProjectJarName(project, platform, project.apiName() + "-5.12.2.jar"));
                assertFalse(UpdaterService.matchesProjectJarName(project, platform, project.apiName() + "addon.jar"));
                assertFalse(UpdaterService.matchesProjectJarName(project, platform, "viafabricplus.jar"));
            }
            assertFalse(UpdaterService.matchesProjectJarName(Project.VIAVERSION, platform, "viabackwards-5.12.2.jar"));
        }
    }

    @Test
    public void matchesOnlyGeyserJarsForTheCurrentPlatform() {
        assertTrue(UpdaterService.matchesProjectJarName(
                Project.GEYSER, Platform.VELOCITY, "geyser-velocity.jar"));
        assertTrue(UpdaterService.matchesProjectJarName(
                Project.GEYSER, Platform.VELOCITY, "geyser-velocity-2.8.2.jar"));
        assertTrue(UpdaterService.matchesProjectJarName(
                Project.GEYSER, Platform.VELOCITY, "geyser.jar"));

        assertFalse(UpdaterService.matchesProjectJarName(
                Project.GEYSER, Platform.VELOCITY, "geyser-spigot.jar"));
        assertFalse(UpdaterService.matchesProjectJarName(
                Project.GEYSER, Platform.VELOCITY, "geyserupdater-velocity.jar"));
        assertFalse(UpdaterService.matchesProjectJarName(
                Project.GEYSER, Platform.VELOCITY, "geyser-another-plugin.jar"));
    }

    @Test
    public void rejectsUpdaterJarEvenWhenRenamedLikeGeyser() throws Exception {
        Path jarPath = java.nio.file.Files.createTempFile("geyser-velocity-1.0.2", ".jar");
        try {
            try (JarOutputStream jar = new JarOutputStream(java.nio.file.Files.newOutputStream(jarPath))) {
                jar.putNextEntry(new JarEntry("velocity-plugin.json"));
                jar.write("{\"id\":\"geyserupdater\",\"name\":\"GeyserUpdater\"}".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
                jar.closeEntry();
            }

            Path renamed = jarPath.resolveSibling("Geyser-Velocity-1.0.2.jar");
            java.nio.file.Files.move(jarPath, renamed);
            jarPath = renamed;

            assertFalse(UpdaterService.isProjectJarCandidate(Project.GEYSER, Platform.VELOCITY, jarPath));
        } finally {
            java.nio.file.Files.deleteIfExists(jarPath);
        }
    }

    @Test
    public void resolvesVelocityExtensionDirectory() {
        Path plugins = Path.of("server", "plugins");

        assertEquals(
                plugins.resolve("Geyser-Velocity").resolve("extensions"),
                UpdaterService.geyserExtensionsDirectory(Platform.VELOCITY, plugins)
        );
    }
}
