package org.geyserupdater.core;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import static org.junit.Assert.*;

public class ViaJarCompatibilityTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private Path jar(String descriptor, String version, int javaVersion, boolean preview) throws Exception {
        Path path = temp.newFile().toPath();
        try (var jar = new JarOutputStream(Files.newOutputStream(path))) {
            jar.putNextEntry(new JarEntry(descriptor));
            String contents = descriptor.endsWith(".json") ? "{\"version\":\"" + version + "\"}" : "version: " + version;
            jar.write(contents.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("com/viaversion/Plugin.class"));
            var out = new DataOutputStream(jar);
            out.writeInt(0xCAFEBABE);
            out.writeShort(preview ? 65535 : 0);
            out.writeShort(javaVersion + 44);
            jar.closeEntry();
        }
        return path;
    }

    @Test
    public void acceptsSupportedJavaAndReadsInstalledVersion() throws Exception {
        Path path = jar("plugin.yml", "5.12.2", 17, false);
        ViaJarCompatibility.verify(path, Platform.PAPER, 21);
        assertEquals("5.12.2", ViaJarCompatibility.installedVersion(path, Platform.PAPER));
        Path velocity = jar("velocity-plugin.json", "5.12.2-SNAPSHOT", 17, false);
        ViaJarCompatibility.verify(velocity, Platform.VELOCITY, 17);
        assertEquals("5.12.2-SNAPSHOT", ViaJarCompatibility.installedVersion(velocity, Platform.VELOCITY));
    }

    @Test
    public void rejectsHigherJavaPreviewAndWrongPlatformWithoutTouchingJar() throws Exception {
        Path path = jar("plugin.yml", "5.12.2", 25, false);
        byte[] before = Files.readAllBytes(path);
        assertThrows(IOException.class, () -> ViaJarCompatibility.verify(path, Platform.PAPER, 21));
        assertArrayEquals(before, Files.readAllBytes(path));
        assertThrows(IOException.class, () -> ViaJarCompatibility.verify(path, Platform.VELOCITY, 26));
        Path preview = jar("plugin.yml", "5.12.2", 21, true);
        assertThrows(IOException.class, () -> ViaJarCompatibility.verify(preview, Platform.PAPER, 21));
        Path unknown = jar("plugin.yml", "custom-build", 17, false);
        assertThrows(IOException.class, () -> ViaJarCompatibility.installedVersion(unknown, Platform.PAPER));
    }
}
