package org.geyserupdater.core;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class ViaReleaseTest {
    private String release(Project project, String version, String platform, String runtime, String channel) {
        return """
                {"name":"%s","visibility":"public","channel":{"name":"%s"},
                 "platformDependencies":{"%s":["%s"]},
                 "downloads":{"%s":{"fileInfo":{"name":"%s-%s.jar","sha256Hash":"%s"},
                 "downloadUrl":"https://hangarcdn.papermc.io/plugins/ViaVersion/%s/versions/%s/PAPER/%s-%s.jar"}}}
                """.formatted(version, channel, platform, runtime, platform, project.fileHint(), version,
                "a".repeat(64), project.fileHint(), version, project.fileHint(), version);
    }

    private String page(String... releases) {
        return "{\"result\":[" + String.join(",", releases) + "]}";
    }

    @Test
    public void selectsNewestCompatibleStableInsteadOfNewestOverall() throws Exception {
        for (Project project : new Project[]{Project.VIAVERSION, Project.VIABACKWARDS, Project.VIAREWIND}) {
            String json = page(
                    release(project, "5.9.0", "PAPER", "1.21", "Release"),
                    release(project, "6.0.0", "PAPER", "1.21.1", "Release"),
                    release(project, "5.13.0", "PAPER", "1.21", "Snapshot"),
                    release(project, "5.12.2", "PAPER", "1.21", "Release"));
            ViaRelease selected = ViaRelease.select(project, Platform.PAPER, "1.21-R0.1-SNAPSHOT", json).orElseThrow();
            assertEquals("5.12.2", selected.version());
            assertEquals("a".repeat(64), selected.sha256());
            assertTrue(selected.url().endsWith(project.fileHint() + "-5.12.2.jar"));
        }
    }

    @Test
    public void doesNotConfusePatchVersionsOrClientVersionsWithRuntime() throws Exception {
        String json = page(release(Project.VIAVERSION, "5.12.2", "PAPER", "1.21.1", "Release"));
        assertTrue(ViaRelease.select(Project.VIAVERSION, Platform.PAPER, "1.21", json).isEmpty());
        assertTrue(ViaRelease.select(Project.VIAVERSION, Platform.PAPER, "1.21.1", json).isPresent());
        assertTrue(ViaRelease.select(Project.VIAVERSION, Platform.PAPER, "1.21.2", json).isEmpty());
        assertThrows(IOException.class, () -> ViaRelease.runtimeVersion("git-Paper-100 (MC: 1.21)"));
    }

    @Test
    public void usesVelocityRuntimeRatherThanMinecraftVersion() throws Exception {
        String json = page(release(Project.VIAVERSION, "5.12.2", "VELOCITY", "3.4.0", "Release"));
        assertTrue(ViaRelease.select(Project.VIAVERSION, Platform.VELOCITY, "3.4.0-SNAPSHOT+abc", json).isPresent());
        assertTrue(ViaRelease.select(Project.VIAVERSION, Platform.VELOCITY, "3.3.0", json).isEmpty());
        assertTrue(ViaRelease.select(Project.VIAVERSION, Platform.PAPER, "1.21", json).isEmpty());
        assertTrue(ViaRelease.select(Project.VIAVERSION, Platform.BUNGEECORD, "1.21", json).isEmpty());
    }

    @Test
    public void rejectsMissingMetadataUnknownRuntimeAndBadDownloads() throws Exception {
        String json = page(release(Project.VIAVERSION, "5.12.2", "PAPER", "1.21", "Release"));
        assertTrue(ViaRelease.select(Project.VIAVERSION, Platform.PAPER, "1.21", json.replace("platformDependencies", "unknown")).isEmpty());
        assertTrue(ViaRelease.select(Project.VIAVERSION, Platform.PAPER, "1.21", json.replace("\"public\"", "\"hidden\"")).isEmpty());
        assertThrows(IOException.class, () -> ViaRelease.select(Project.VIAVERSION, Platform.PAPER, "unknown", json));
        assertThrows(IOException.class, () -> ViaRelease.select(Project.VIAVERSION, Platform.PAPER, "1.21", json.replace("https://hangarcdn.papermc.io/", "https://example.com/")));
        assertThrows(IOException.class, () -> ViaRelease.select(Project.VIAVERSION, Platform.PAPER, "1.21", json.replace("a".repeat(64), "invalid")));
        assertThrows(IOException.class, () -> ViaRelease.select(Project.VIAVERSION, Platform.PAPER, "1.21", "not json"));
        assertTrue(ViaRelease.select(Project.VIAVERSION, Platform.PAPER, "1.21", page()).isEmpty());
    }

    @Test
    public void comparesNumericVersionsToPreventDowngrades() {
        assertTrue(ViaRelease.compareVersions("5.12.2", "5.9.0") > 0);
        assertTrue(ViaRelease.compareVersions("6.0.0", "5.12.2") > 0);
        assertEquals(0, ViaRelease.compareVersions("5.12.2-SNAPSHOT", "5.12.2"));
    }

    @Test
    public void paper121IsNotIncorrectlyWarnedAsOlderThanUpdaterMinimum() {
        assertTrue(VersionCompatibility.evaluate(Platform.PAPER, "1.21-R0.1-SNAPSHOT").compatible());
        assertFalse(VersionCompatibility.evaluate(Platform.PAPER, "1.18.2-R0.1-SNAPSHOT").compatible());
    }
}
