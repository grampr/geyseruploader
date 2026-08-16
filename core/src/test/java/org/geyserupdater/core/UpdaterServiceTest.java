package org.geyserupdater.core;

import org.junit.Test;

import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdaterServiceTest {
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
