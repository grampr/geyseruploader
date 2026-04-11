package org.geyserupdater.spigot;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.geyserupdater.core.Config;
import org.geyserupdater.core.ConfigManager;
import org.geyserupdater.core.Platform;
import org.geyserupdater.core.UpdateOutcomeSummary;
import org.geyserupdater.core.UpdaterService;
import org.geyserupdater.core.VersionCompatibility;
import org.geyserupdater.core.logging.LogAdapter;
import org.geyserupdater.core.util.JarMigrationUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class SpigotGeyserUpdaterPlugin extends JavaPlugin implements Listener {
    private ConfigManager cfgMgr;
    private Config cfg;
    private Platform runtimePlatform;

    @Override
    public void onEnable() {
        saveDefaultConfigFile();
        this.cfgMgr = new ConfigManager(getDataFolder().toPath());
        this.cfg = cfgMgr.loadOrCreateDefault();
        this.runtimePlatform = detectRuntimePlatform();

        JarMigrationUtil.migrateNestedPluginsIfNeeded(getDataFolder().toPath().getParent(), new SpigotLogger());
        applyStagedExtensionUpdates();

        getServer().getPluginManager().registerEvents(this, this);

        if (!cfg.enabled) {
            info("[GeyserUpdater] disabled by config");
            return;
        }

        logRuntimeCompatibility();

        if (cfg.checkOnStartup) {
            info(cfg.messages.startUpCheck);
            runAsyncCheck(false, null);
        }

        if (cfg.periodic.enabled && cfg.periodic.intervalHours > 0) {
            info(cfg.messages.periodicCheck.replace("{hours}", String.valueOf(cfg.periodic.intervalHours)));
            long ticks = TimeUnit.HOURS.toSeconds(cfg.periodic.intervalHours) * 20L;
            long delay = TimeUnit.MINUTES.toSeconds(5) * 20L;
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> runAsyncCheck(false, null), delay, ticks);
        }
    }

    private void saveDefaultConfigFile() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
    }

    private Platform detectRuntimePlatform() {
        String serverName = Bukkit.getName().toLowerCase(Locale.ROOT);
        String version = Bukkit.getVersion().toLowerCase(Locale.ROOT);
        if (serverName.contains("paper") || version.contains("paper")) {
            return Platform.PAPER;
        }
        return Platform.SPIGOT;
    }

    private String detectRuntimeVersion() {
        try {
            return Bukkit.getBukkitVersion();
        } catch (Throwable ignored) {
            return Bukkit.getVersion();
        }
    }

    private void logRuntimeCompatibility() {
        String rawVersion = detectRuntimeVersion();
        VersionCompatibility.CompatibilityResult check = VersionCompatibility.evaluate(runtimePlatform, rawVersion);

        info("Detected platform runtime: " + runtimePlatform.displayName() + " (version=" + rawVersion + ")");
        if (check.checked() && !check.compatible()) {
            warn("Compatibility warning: " + check.detail() + " Minimum recommended: " + check.minimumVersion()
                    + ", detected: " + check.parsedVersion());
        } else if (!check.checked()) {
            warn("Compatibility check skipped: " + check.detail());
        }
    }

    private void applyStagedExtensionUpdates() {
        Path pluginsDir = getDataFolder().toPath().getParent();
        Path extensionsDir = pluginsDir.resolve("Geyser-Spigot").resolve("extensions");
        Path stagedDir = extensionsDir.resolve("update");

        if (!Files.isDirectory(stagedDir)) {
            return;
        }

        try (Stream<Path> stream = Files.list(stagedDir)) {
            stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .forEach(path -> {
                        try {
                            Path dest = extensionsDir.resolve(path.getFileName().toString());
                            Files.createDirectories(extensionsDir);
                            Files.move(path, dest, StandardCopyOption.REPLACE_EXISTING);
                            info("Applied staged extension update: " + path.getFileName());
                        } catch (Exception ex) {
                            warn("Failed to apply staged extension update " + path.getFileName() + ": " + ex.getMessage());
                        }
                    });
        } catch (Exception ex) {
            warn("Failed to scan staged extension updates: " + ex.getMessage());
        }
    }

    private void runAsyncCheck(boolean manual, CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            UpdaterService service = new UpdaterService(new SpigotLogger(), cfg);
            if (manual) {
                sendTo(sender, cfg.messages.prefix + cfg.messages.manualTriggered);
            } else {
                info(cfg.messages.checking);
            }

            Path pluginsDir = getDataFolder().toPath().getParent();
            List<UpdaterService.UpdateOutcome> results = service.checkAndUpdate(runtimePlatform, pluginsDir);
            UpdateOutcomeSummary.Summary summary = UpdateOutcomeSummary.summarize(cfg, results);

            for (String message : summary.messages()) {
                msg(sender, message);
            }

            if (summary.anyUpdated()) {
                info(cfg.messages.promptRestart);
                if (cfg.postUpdate.runRestartCommand && cfg.postUpdate.restartCommand != null && !cfg.postUpdate.restartCommand.isBlank()) {
                    Bukkit.getScheduler().runTask(this,
                            () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cfg.postUpdate.restartCommand));
                }
            }

            msg(sender, cfg.messages.done);
        });
    }

    private void msg(CommandSender sender, String message) {
        if (sender != null) {
            sender.sendMessage(cfg.messages.prefix + message);
        } else {
            info(message);
        }
    }

    private void sendTo(CommandSender sender, String message) {
        if (sender != null) {
            sender.sendMessage(message);
        } else {
            info(message);
        }
    }

    private void info(String message) {
        getLogger().info(message);
    }

    private void warn(String message) {
        getLogger().warning(message);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!cfg.enabled || !cfg.adminLogin.enabled) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission(cfg.adminLogin.permission)) {
            info(cfg.messages.adminLoginCheck);
            runAsyncCheck(false, player);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("geyserupdate")) {
            return false;
        }

        if (!sender.hasPermission(cfg.adminLogin.permission)) {
            sender.sendMessage(cfg.messages.prefix + cfg.messages.noPermission);
            return true;
        }

        runAsyncCheck(true, sender);
        return true;
    }

    private class SpigotLogger implements LogAdapter {
        @Override
        public void info(String msg) {
            getLogger().info(msg);
        }

        @Override
        public void warn(String msg) {
            getLogger().warning(msg);
        }

        @Override
        public void error(String msg, Throwable t) {
            getLogger().severe(msg + " : " + t.getMessage());
        }
    }
}
