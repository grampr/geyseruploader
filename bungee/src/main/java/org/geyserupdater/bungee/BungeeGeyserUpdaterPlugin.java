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
import org.geyserupdater.core.UpdaterService;
import org.geyserupdater.core.logging.LogAdapter;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BungeeGeyserUpdaterPlugin extends Plugin implements Listener {
    private ConfigManager cfgMgr;
    private Config cfg;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        this.cfgMgr = new ConfigManager(getDataFolder().toPath());
        this.cfg = cfgMgr.loadOrCreateDefault();

        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new UpdateCommand());

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
            long initialDelay = TimeUnit.MINUTES.toSeconds(5);
            long interval = TimeUnit.HOURS.toSeconds(cfg.periodic.intervalHours);
            getProxy().getScheduler().schedule(this, () -> runAsyncCheck(false, null), initialDelay, interval, TimeUnit.SECONDS);
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
            Path pluginsDir = getDataFolder().toPath().getParent().resolve("plugins");
            List<UpdaterService.UpdateOutcome> results = service.checkAndUpdate(Platform.BUNGEECORD, pluginsDir);

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
                    ProxyServer.getInstance().getScheduler().runAsync(this, () -> ProxyServer.getInstance().getPluginManager()
                            .dispatchCommand(ProxyServer.getInstance().getConsole(), cfg.postUpdate.restartCommand));
                }
            }
            msg(sender, cfg.messages.done);
        });
    }

    private class UpdateCommand extends Command {
        public UpdateCommand() { super("geyserupdate", "geyserupdater.admin", new String[0]); }

        @Override
        public void execute(CommandSender sender, String[] args) {
            runAsyncCheck(true, sender);
        }
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent e) {
        if (!cfg.enabled || !cfg.adminLogin.enabled) return;
        ProxiedPlayer p = e.getPlayer();
        if (p.hasPermission(cfg.adminLogin.permission)) {
            info(cfg.messages.adminLoginCheck);
            runAsyncCheck(false, p);
        }
    }

    private void msg(CommandSender sender, String msg) {
        if (sender != null) send(sender, cfg.messages.prefix + msg);
        else info(msg);
    }

    private void send(CommandSender sender, String msg) {
        if (sender != null) sender.sendMessage(new TextComponent(msg));
        else info(msg);
    }

    private void info(String msg) {
        getLogger().info(msg);
    }

    private class BungeeLogger implements LogAdapter {
        @Override public void info(String msg) { getLogger().info(msg); }
        @Override public void warn(String msg) { getLogger().warning(msg); }
        @Override public void error(String msg, Throwable t) { getLogger().severe(msg + " : " + t.getMessage()); }
    }
}