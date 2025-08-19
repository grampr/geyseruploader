package org.geyserupdater.core;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class ConfigManager {
    private final Path dataFolder;
    private final Path configPath;

    public ConfigManager(Path dataFolder) {
        this.dataFolder = dataFolder;
        this.configPath = dataFolder.resolve("config.yml");
    }

    @SuppressWarnings("unchecked")
    public Config loadOrCreateDefault() {
        try {
            if (!Files.exists(dataFolder)) Files.createDirectories(dataFolder);
            if (!Files.exists(configPath)) {
                // 初回は resources/config.yml をコピー（型タグなし）
                try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configPath);
                    } else {
                        Files.writeString(configPath, "enabled: true\n", StandardCharsets.UTF_8);
                    }
                }
            }

            String content = Files.readString(configPath, StandardCharsets.UTF_8);
            // 旧版で先頭に型タグが入っていた場合は除去
            if (content.startsWith("!!org.geyserupdater.core.Config")) {
                int idx = content.indexOf('\n');
                content = (idx >= 0) ? content.substring(idx + 1) : "";
                Files.writeString(configPath, content, StandardCharsets.UTF_8);
            }

            LoaderOptions options = new LoaderOptions();
            Yaml yaml = new Yaml(new SafeConstructor(options));

            Object obj = yaml.load(content);
            Config cfg = new Config();
            if (!(obj instanceof Map<?, ?> map)) {
                return cfg; // 既定値
            }

            // 1階層
            cfg.enabled = asBool(map, "enabled", cfg.enabled);
            cfg.checkOnStartup = asBool(map, "checkOnStartup", cfg.checkOnStartup);

            // periodic
            Map<String, Object> periodic = asMap(map, "periodic");
            cfg.periodic.enabled = asBool(periodic, "enabled", cfg.periodic.enabled);
            cfg.periodic.intervalHours = asInt(periodic, "intervalHours", cfg.periodic.intervalHours);

            // adminLogin
            Map<String, Object> adminLogin = asMap(map, "adminLogin");
            cfg.adminLogin.enabled = asBool(adminLogin, "enabled", cfg.adminLogin.enabled);
            cfg.adminLogin.permission = asStr(adminLogin, "permission", cfg.adminLogin.permission);

            // targets
            Map<String, Object> targets = asMap(map, "targets");
            cfg.targets.geyser = asBool(targets, "geyser", cfg.targets.geyser);
            cfg.targets.floodgate = asBool(targets, "floodgate", cfg.targets.floodgate);

            // postUpdate
            Map<String, Object> postUpdate = asMap(map, "postUpdate");
            cfg.postUpdate.notifyConsole = asBool(postUpdate, "notifyConsole", cfg.postUpdate.notifyConsole);
            cfg.postUpdate.notifyPlayersWithPermission = asBool(postUpdate, "notifyPlayersWithPermission", cfg.postUpdate.notifyPlayersWithPermission);
            cfg.postUpdate.runRestartCommand = asBool(postUpdate, "runRestartCommand", cfg.postUpdate.runRestartCommand);
            cfg.postUpdate.restartCommand = asStr(postUpdate, "restartCommand", cfg.postUpdate.restartCommand);

            // messages
            Map<String, Object> messages = asMap(map, "messages");
            cfg.messages.prefix = asStr(messages, "prefix", cfg.messages.prefix);
            cfg.messages.checking = asStr(messages, "checking", cfg.messages.checking);
            cfg.messages.upToDate = asStr(messages, "upToDate", cfg.messages.upToDate);
            cfg.messages.updated = asStr(messages, "updated", cfg.messages.updated);
            cfg.messages.noTarget = asStr(messages, "noTarget", cfg.messages.noTarget);
            cfg.messages.failed = asStr(messages, "failed", cfg.messages.failed);
            cfg.messages.promptRestart = asStr(messages, "promptRestart", cfg.messages.promptRestart);
            cfg.messages.startUpCheck = asStr(messages, "startUpCheck", cfg.messages.startUpCheck);
            cfg.messages.periodicCheck = asStr(messages, "periodicCheck", cfg.messages.periodicCheck);
            cfg.messages.adminLoginCheck = asStr(messages, "adminLoginCheck", cfg.messages.adminLoginCheck);
            cfg.messages.manualTriggered = asStr(messages, "manualTriggered", cfg.messages.manualTriggered);
            cfg.messages.nothingToDo = asStr(messages, "nothingToDo", cfg.messages.nothingToDo);
            cfg.messages.done = asStr(messages, "done", cfg.messages.done);

            return cfg;
        } catch (IOException e) {
            e.printStackTrace();
            return new Config();
        }
    }

    private static Map<String, Object> asMap(Map<?, ?> map, String key) {
        Object o = map.get(key);
        if (o instanceof Map<?, ?> m) {
            // Unsafe キャストを許容（YAML上は String キー想定）
            return (Map<String, Object>) (Map<?, ?>) m;
        }
        return Collections.emptyMap();
    }

    private static String asStr(Map<?, ?> map, String key, String def) {
        Object o = map.get(key);
        return o == null ? def : String.valueOf(o);
    }

    private static boolean asBool(Map<?, ?> map, String key, boolean def) {
        Object o = map.get(key);
        if (o instanceof Boolean b) return b;
        if (o == null) return def;
        return Boolean.parseBoolean(String.valueOf(o));
    }

    private static int asInt(Map<?, ?> map, String key, int def) {
        Object o = map.get(key);
        if (o instanceof Number n) return n.intValue();
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    public Path getConfigPath() {
        return configPath;
    }
}
