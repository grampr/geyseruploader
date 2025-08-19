package org.geyserupdater.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.geyserupdater.core.Config;
import org.geyserupdater.core.ConfigManager;
import org.geyserupdater.core.Platform;
import org.geyserupdater.core.UpdaterService;
import org.geyserupdater.core.logging.LogAdapter;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Plugin(
        id = "geyserupdater",
        name = "GeyserUpdater",
        version = "1.0.0",
        description = "Auto-updater for GeyserMC and Floodgate"
)
public class VelocityGeyserUpdaterPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;

    private ConfigManager cfgMgr;
    private Config cfg;

    @Inject
    public VelocityGeyserUpdaterPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onProxyInitialize(com.velocitypowered.api.event.proxy.ProxyInitializeEvent e) {
        try {
            java.nio.file.Files.createDirectories(dataDir);
        } catch (Exception ex) {
            logger.severe("Could not create data directory: " + ex.getMessage());
        }
        this.cfgMgr = new ConfigManager(dataDir);
        this.cfg = cfgMgr.loadOrCreateDefault();

        // コマンド登録
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("geyserupdate").build(),
                new UpdateCommand()
        );

        // 注意: メインインスタンスは自動でイベント登録されるため、明示的な register は不要
        // 例: proxy.getEventManager().register(this, this); は呼び出さない

        if (!cfg.enabled) {
            logger.info("[GeyserUpdater] disabled by config");
            return;
        }

        if (cfg.checkOnStartup) {
            logger.info(cfg.messages.startUpCheck);
            runAsyncCheck(false, null);
        }
        if (cfg.periodic.enabled && cfg.periodic.intervalHours > 0) {
            logger.info(cfg.messages.periodicCheck.replace("{hours}", String.valueOf(cfg.periodic.intervalHours)));
            proxy.getScheduler().buildTask(this, () -> runAsyncCheck(false, null))
                    .delay(5, TimeUnit.MINUTES)
                    .repeat(cfg.periodic.intervalHours, TimeUnit.HOURS)
                    .schedule();
        }
    }

    private void runAsyncCheck(boolean manual, CommandSource sender) {
        proxy.getScheduler().buildTask(this, () -> {
            UpdaterService service = new UpdaterService(new VelocityLogger(), cfg);
            if (manual) {
                send(sender, cfg.messages.prefix + cfg.messages.manualTriggered);
            } else {
                logger.info(cfg.messages.checking);
            }
            Path pluginsDir = dataDir.getParent().resolve("plugins");
            List<UpdaterService.UpdateOutcome> results = service.checkAndUpdate(Platform.VELOCITY, pluginsDir);

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
                logger.info(cfg.messages.promptRestart);
                if (cfg.postUpdate.runRestartCommand && cfg.postUpdate.restartCommand != null && !cfg.postUpdate.restartCommand.isBlank()) {
                    proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), cfg.postUpdate.restartCommand);
                }
            }
            msg(sender, cfg.messages.done);
        }).schedule();
    }

    private class UpdateCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource src = invocation.source();
            if (!src.hasPermission("geyserupdater.admin")) {
                send(src, cfg.messages.prefix + "権限がありません。");
                return;
            }
            runAsyncCheck(true, src);
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("geyserupdater.admin");
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent e) {
        if (!cfg.enabled || !cfg.adminLogin.enabled) return;
        if (e.getPlayer().hasPermission(cfg.adminLogin.permission)) {
            logger.info(cfg.messages.adminLoginCheck);
            runAsyncCheck(false, e.getPlayer());
        }
    }

    private void msg(CommandSource sender, String msg) {
        if (sender != null) send(sender, cfg.messages.prefix + msg);
        else logger.info(msg);
    }

    private void send(CommandSource sender, String msg) {
        if (sender != null) sender.sendMessage(Component.text(msg));
        else logger.info(msg);
    }

    private class VelocityLogger implements LogAdapter {
        @Override public void info(String msg) { logger.info(msg); }
        @Override public void warn(String msg) { logger.warning(msg); }
        @Override public void error(String msg, Throwable t) { logger.severe(msg + " : " + t.getMessage()); }
    }
}