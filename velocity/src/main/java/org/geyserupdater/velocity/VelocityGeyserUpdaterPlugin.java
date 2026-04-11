package org.geyserupdater.velocity;

import com.google.inject.Inject;
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
import org.geyserupdater.core.UpdateOutcomeSummary;
import org.geyserupdater.core.UpdaterService;
import org.geyserupdater.core.VersionCompatibility;
import org.geyserupdater.core.logging.LogAdapter;
import org.geyserupdater.core.util.JarMigrationUtil;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Plugin(
        id = "geyserupdater",
        name = "GeyserUpdater",
        version = "1.0.1",
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
    public void onProxyInitialize(com.velocitypowered.api.event.proxy.ProxyInitializeEvent event) {
        try {
            java.nio.file.Files.createDirectories(dataDir);
        } catch (Exception ex) {
            logger.severe("Could not create data directory: " + ex.getMessage());
        }

        this.cfgMgr = new ConfigManager(dataDir);
        this.cfg = cfgMgr.loadOrCreateDefault();

        JarMigrationUtil.migrateNestedPluginsIfNeeded(dataDir.getParent(), new VelocityLogger());

        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("geyserupdate").build(),
                new UpdateCommand()
        );

        if (!cfg.enabled) {
            logger.info("[GeyserUpdater] disabled by config");
            return;
        }

        logRuntimeCompatibility();

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

    private String detectRuntimeVersion() {
        try {
            return proxy.getVersion().getVersion();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private void logRuntimeCompatibility() {
        String rawVersion = detectRuntimeVersion();
        VersionCompatibility.CompatibilityResult check = VersionCompatibility.evaluate(Platform.VELOCITY, rawVersion);

        logger.info("Detected platform runtime: " + Platform.VELOCITY.displayName() + " (version=" + rawVersion + ")");
        if (check.checked() && !check.compatible()) {
            logger.warning("Compatibility warning: " + check.detail() + " Minimum recommended: "
                    + check.minimumVersion() + ", detected: " + check.parsedVersion());
        } else if (!check.checked()) {
            logger.warning("Compatibility check skipped: " + check.detail());
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

            Path pluginsDir = dataDir.getParent();
            List<UpdaterService.UpdateOutcome> results = service.checkAndUpdate(Platform.VELOCITY, pluginsDir);
            UpdateOutcomeSummary.Summary summary = UpdateOutcomeSummary.summarize(cfg, results);

            for (String message : summary.messages()) {
                msg(sender, message);
            }

            if (summary.anyUpdated()) {
                logger.info(cfg.messages.promptRestart);
                if (cfg.postUpdate.runRestartCommand && cfg.postUpdate.restartCommand != null && !cfg.postUpdate.restartCommand.isBlank()) {
                    proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), cfg.postUpdate.restartCommand);
                }
            }

            msg(sender, cfg.messages.done);
        }).schedule();
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        if (!cfg.enabled || !cfg.adminLogin.enabled) {
            return;
        }

        if (event.getPlayer().hasPermission(cfg.adminLogin.permission)) {
            logger.info(cfg.messages.adminLoginCheck);
            runAsyncCheck(false, event.getPlayer());
        }
    }

    private void msg(CommandSource sender, String message) {
        if (sender != null) {
            send(sender, cfg.messages.prefix + message);
        } else {
            logger.info(message);
        }
    }

    private void send(CommandSource sender, String message) {
        if (sender != null) {
            sender.sendMessage(Component.text(message));
        } else {
            logger.info(message);
        }
    }

    private class UpdateCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!source.hasPermission(cfg.adminLogin.permission)) {
                send(source, cfg.messages.prefix + cfg.messages.noPermission);
                return;
            }
            runAsyncCheck(true, source);
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission(cfg.adminLogin.permission);
        }
    }

    private class VelocityLogger implements LogAdapter {
        @Override
        public void info(String msg) {
            logger.info(msg);
        }

        @Override
        public void warn(String msg) {
            logger.warning(msg);
        }

        @Override
        public void error(String msg, Throwable t) {
            logger.severe(msg + " : " + t.getMessage());
        }
    }
}
