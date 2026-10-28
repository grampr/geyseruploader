package org.geyserupdater.core;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

final class ViaJarCompatibility {
    private ViaJarCompatibility() { }

    static String installedVersion(Path path, Platform platform) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            String descriptor = platform == Platform.VELOCITY ? "velocity-plugin.json" : "plugin.yml";
            var entry = jar.getJarEntry(descriptor);
            if (entry == null) throw new IOException("Cannot read installed Via version: " + path.getFileName());
            try (var input = jar.getInputStream(entry)) {
                Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
                if (parsed instanceof Map<?, ?> map && map.get("version") instanceof String version
                        && version.matches("\\d+\\.\\d+\\.\\d+(?:[-+].*)?")) {
                    return version;
                }
                throw new IOException("Unknown installed Via version in " + path.getFileName());
            } catch (RuntimeException e) {
                throw new IOException("Malformed Via plugin descriptor", e);
            }
        }
    }

    static void verify(Path path, Platform platform, int javaVersion) throws IOException {
        try (JarFile jar = new JarFile(path.toFile(), false, ZipFile.OPEN_READ, Runtime.Version.parse("" + javaVersion))) {
            String descriptor = platform == Platform.VELOCITY ? "velocity-plugin.json" : "plugin.yml";
            if (jar.getJarEntry(descriptor) == null) {
                throw new IOException("Via JAR does not support " + platform.displayName());
            }
            int classes = 0;
            try (var entries = jar.versionedStream()) {
                var iterator = entries.iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    if (!entry.getName().endsWith(".class") || entry.getName().endsWith("module-info.class")) continue;
                    classes++;
                    try (var input = new DataInputStream(jar.getInputStream(entry))) {
                        if (input.readInt() != 0xCAFEBABE) throw new IOException("Invalid Via class file");
                        int minor = input.readUnsignedShort();
                        int major = input.readUnsignedShort();
                        if (minor == 65535 || major > javaVersion + 44) {
                            throw new IOException("Via JAR requires Java " + (major - 44)
                                    + " or preview features; running Java " + javaVersion + ". Existing JAR preserved.");
                        }
                    }
                }
            }
            if (classes == 0) throw new IOException("Via JAR contains no classes");
        }
    }
}
