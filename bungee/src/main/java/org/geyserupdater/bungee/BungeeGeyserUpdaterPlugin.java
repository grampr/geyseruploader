package org.geyserupdater.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.geyserupdater.core.Config;
import org.geyserupdater.core.ConfigManager;
import org.geyserupdater.core.Platform;
import org.geyserupdater.core.UpdateOutcomeSummary;
import org.geyserupdater.core.UpdaterService;
import org.geyserupdater.core.VersionCompatibility;
import org.geyserupdater.core.logging.LogAdapter;
import org.geyserupdater.core.util.JarMigrationUtil;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BungeeGeyserUpdaterPlugin extends Plugin implements Listener {
    private ConfigManager cfgMgr;
    private Config cfg;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        this.cfgMgr = new ConfigManager(getDataFolder().toPath());
        this.cfg = cfgMgr.loadOrCreateDefault();

        JarMigrationUtil.migrateNestedPluginsIfNeeded(getDataFolder().toPath().getParent(), new BungeeLogger());

        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new UpdateCommand(cfg.adminLogin.permission));

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
            long initialDelay = TimeUnit.MINUTES.toSeconds(5);
            long interval = TimeUnit.HOURS.toSeconds(cfg.periodic.intervalHours);
            getProxy().getScheduler().schedule(this, () -> runAsyncCheck(false, null), initialDelay, interval, TimeUnit.SECONDS);
        }
    }

    private String detectRuntimeVersion() {
        try {
            return getProxy().getVersion();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private void logRuntimeCompatibility() {
        String rawVersion = detectRuntimeVersion();
        VersionCompatibility.CompatibilityResult check = VersionCompatibility.evaluate(Platform.BUNGEECORD, rawVersion);

        info("Detected platform runtime: " + Platform.BUNGEECORD.displayName() + " (version=" + rawVersion + ")");
        if (!check.checked()) {
            info("Compatibility check info: " + check.detail());
        } else if (!check.compatible()) {
            getLogger().warning("Compatibility warning: " + check.detail());
        }
    }

    private void runAsyncCheck(boolean manual, CommandSender sender) {
        ProxyServer.getInstance().getScheduler().runAsync(this, () -> {
            UpdaterService service = new UpdaterService(new BungeeLogger(), cfg);
            if (manual) {
                send(sender, cfg.messages.prefix + cfg.messages.manualTriggered);
            } else {
                info(cfg.messages.checking);
            }

            Path pluginsDir = getDataFolder().toPath().getParent();
            List<UpdaterService.UpdateOutcome> results = service.checkAndUpdate(Platform.BUNGEECORD, pluginsDir);
            UpdateOutcomeSummary.Summary summary = UpdateOutcomeSummary.summarize(cfg, results);

            for (String message : summary.messages()) {
                msg(sender, message);
            }

            if (summary.anyUpdated()) {
                info(cfg.messages.promptRestart);
                if (cfg.postUpdate.runRestartCommand && cfg.postUpdate.restartCommand != null && !cfg.postUpdate.restartCommand.isBlank()) {
                    ProxyServer.getInstance().getScheduler().runAsync(this,
                            () -> ProxyServer.getInstance().getPluginManager().dispatchCommand(
                                    ProxyServer.getInstance().getConsole(),
                                    cfg.postUpdate.restartCommand
                            )
                    );
                }
            }

            msg(sender, cfg.messages.done);
        });
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        if (!cfg.enabled || !cfg.adminLogin.enabled) {
            return;
        }

        ProxiedPlayer player = event.getPlayer();
        if (player.hasPermission(cfg.adminLogin.permission)) {
            info(cfg.messages.adminLoginCheck);
            runAsyncCheck(false, player);
        }
    }

    private void msg(CommandSender sender, String message) {
        if (sender != null) {
            send(sender, cfg.messages.prefix + message);
        } else {
            info(message);
        }
    }

    private void send(CommandSender sender, String message) {
        if (sender != null) {
            sender.sendMessage(new TextComponent(message));
        } else {
            info(message);
        }
    }

    private void info(String message) {
        getLogger().info(message);
    }

    private class UpdateCommand extends Command {
        private UpdateCommand(String permission) {
            super("geyserupdate", permission, new String[0]);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!sender.hasPermission(cfg.adminLogin.permission)) {
                send(sender, cfg.messages.prefix + cfg.messages.noPermission);
                return;
            }
            runAsyncCheck(true, sender);
        }
    }

    private class BungeeLogger implements LogAdapter {
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
