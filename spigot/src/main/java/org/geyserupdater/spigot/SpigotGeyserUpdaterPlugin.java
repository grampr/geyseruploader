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
import org.geyserupdater.core.UpdaterService;
import org.geyserupdater.core.logging.LogAdapter;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SpigotGeyserUpdaterPlugin extends JavaPlugin implements Listener {
    private ConfigManager cfgMgr;
    private Config cfg;

    @Override
    public void onEnable() {
        saveDefaultConfigFile(); // ensure folder exists
        this.cfgMgr = new ConfigManager(getDataFolder().toPath());
        this.cfg = cfgMgr.loadOrCreateDefault();

        getServer().getPluginManager().registerEvents(this, this);

        if (!cfg.enabled) {
            getLogger().info("[GeyserUpdater] disabled by config");
            return;
        }

        if (cfg.checkOnStartup) {
            info(cfg.messages.startUpCheck);
            runAsyncCheck(false, null);
        }

        if (cfg.periodic.enabled && cfg.periodic.intervalHours > 0) {
            info(cfg.messages.periodicCheck.replace("{hours}", String.valueOf(cfg.periodic.intervalHours)));
            long ticks = TimeUnit.HOURS.toSeconds(cfg.periodic.intervalHours) * 20L;
            // initial delay = 5 minutes
            long delay = TimeUnit.MINUTES.toSeconds(5) * 20L;
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> runAsyncCheck(false, null), delay, ticks);
        }
    }

    private void saveDefaultConfigFile() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        // We use our own config manager; nothing to save here
    }

    private void runAsyncCheck(boolean manual, CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            UpdaterService service = new UpdaterService(new SpigotLogger(), cfg);
            if (manual) {
                sendTo(sender, cfg.messages.prefix + cfg.messages.manualTriggered);
            } else {
                info(cfg.messages.checking);
            }
            List<UpdaterService.UpdateOutcome> results =
                    service.checkAndUpdate(Platform.SPIGOT, getDataFolder().toPath().getParent().resolve("plugins"));

            boolean anyUpdated = false;
            for (UpdaterService.UpdateOutcome r : results) {
                if (r.error.isPresent()) {
                    msg(sender, cfg.messages.failed.replace("{project}", r.project.name().toLowerCase()).replace("{error}", r.error.get()));
                } else if (r.skippedNoChange) {
                    msg(sender, cfg.messages.upToDate.replace("{project}", r.project.name().toLowerCase()));
                } else if (r.updated) {
                    anyUpdated = true;
                    msg(sender, cfg.messages.updated.replace("{project}", r.project.name().toLowerCase()));
                }
            }
            if (anyUpdated) {
                info(cfg.messages.promptRestart);
                if (cfg.postUpdate.runRestartCommand && cfg.postUpdate.restartCommand != null && !cfg.postUpdate.restartCommand.isBlank()) {
                    Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cfg.postUpdate.restartCommand));
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

    private void info(String m) {
        getLogger().info(m);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!cfg.enabled) return;
        if (!cfg.adminLogin.enabled) return;
        Player p = e.getPlayer();
        if (p.hasPermission(cfg.adminLogin.permission)) {
            info(cfg.messages.adminLoginCheck);
            runAsyncCheck(false, p);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("geyserupdate")) return false;
        if (!sender.hasPermission("geyserupdater.admin")) {
            sender.sendMessage(cfg.messages.prefix + "権限がありません。");
            return true;
        }
        runAsyncCheck(true, sender);
        return true;
    }

    private class SpigotLogger implements LogAdapter {
        @Override public void info(String msg) { getLogger().info(msg); }
        @Override public void warn(String msg) { getLogger().warning(msg); }
        @Override public void error(String msg, Throwable t) { getLogger().severe(msg + " : " + t.getMessage()); }
    }
}