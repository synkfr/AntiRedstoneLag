package org.ayosynk.antiRedstoneLag;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.ayosynk.antiRedstoneLag.command.ArlCommandTree;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.diagnostics.HotspotService;
import org.ayosynk.antiRedstoneLag.diagnostics.InspectorService;
import org.ayosynk.antiRedstoneLag.diagnostics.SnapshotService;
import org.ayosynk.antiRedstoneLag.engine.RedstoneEngine;
import org.ayosynk.antiRedstoneLag.gui.StaffGuiManager;
import org.ayosynk.antiRedstoneLag.integration.PapiExpansion;
import org.ayosynk.antiRedstoneLag.integration.WebhookService;
import org.ayosynk.antiRedstoneLag.listener.GuiListener;
import org.ayosynk.antiRedstoneLag.listener.RedstoneListener;
import org.ayosynk.antiRedstoneLag.listener.UpdateChecker;
import org.ayosynk.antiRedstoneLag.manager.ClearLaggManager;
import org.ayosynk.antiRedstoneLag.manager.LogManager;
import org.ayosynk.antiRedstoneLag.manager.MetricsManager;
import org.ayosynk.antiRedstoneLag.scheduler.BukkitSchedulerImpl;
import org.ayosynk.antiRedstoneLag.scheduler.FoliaSchedulerImpl;
import org.ayosynk.antiRedstoneLag.scheduler.Scheduler;
import org.ayosynk.antiRedstoneLag.scheduler.TaskHandle;
import org.ayosynk.antiRedstoneLag.storage.PluginStats;
import org.ayosynk.antiRedstoneLag.storage.StorageFactory;
import org.ayosynk.antiRedstoneLag.storage.StorageService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * AntiRedstoneLag v2 plugin entry point and service coordinator.
 */
@SuppressWarnings("UnstableApiUsage")
public class AntiRedstoneLag extends JavaPlugin {
    private static final long TICKS_PER_HOUR = 20L * 60 * 60;
    private static final long TICKS_PER_DAY = TICKS_PER_HOUR * 24;
    private static final String MODRINTH_PROJECT_ID = "5UOt11Yc";
    private static final String MODRINTH_PROJECT_URL = "https://modrinth.com/plugin/antiredstonelag";

    private Scheduler scheduler;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private LogManager logManager;
    private StorageService storageService;
    private PluginStats pluginStats;
    private WebhookService webhookService;
    private RedstoneEngine redstoneEngine;
    private SnapshotService snapshotService;
    private HotspotService hotspotService;
    private InspectorService inspectorService;
    private ClearLaggManager clearLaggManager;
    private StaffGuiManager staffGuiManager;
    private RedstoneListener redstoneListener;
    private UpdateChecker updateChecker;
    private PapiExpansion papiExpansion;

    private TaskHandle resetTaskHandle;
    private TaskHandle cleanupTaskHandle;

    @Override
    public void onEnable() {
        try {
            Bukkit.getGlobalRegionScheduler();
            scheduler = new FoliaSchedulerImpl(this);
            getLogger().info("Using Paper/Folia regional scheduler.");
        } catch (Throwable e) {
            scheduler = new BukkitSchedulerImpl(this);
        }

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        logManager = new LogManager(this, configManager);

        storageService = StorageFactory.create(getDataFolder(), configManager.getPluginConfig().getStorage(), getLogger());
        pluginStats = storageService.loadStats();
        getLogger().info("Persistent storage initialized (" + storageService.backendName() + ").");

        webhookService = new WebhookService(configManager, getLogger());
        redstoneEngine = new RedstoneEngine(this, configManager, messageManager, logManager, storageService, pluginStats, webhookService);
        snapshotService = new SnapshotService(this, configManager, messageManager, redstoneEngine, storageService);
        redstoneEngine.setSnapshotService(snapshotService);
        hotspotService = new HotspotService(this, redstoneEngine, messageManager, logManager);
        inspectorService = new InspectorService(this, configManager, messageManager, redstoneEngine);
        clearLaggManager = new ClearLaggManager(this, configManager, messageManager, storageService, pluginStats);
        staffGuiManager = new StaffGuiManager(this, configManager, messageManager);

        redstoneListener = new RedstoneListener(this, redstoneEngine, configManager, messageManager);
        getServer().getPluginManager().registerEvents(redstoneListener, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        scheduleResetTask();
        cleanupTaskHandle = scheduler.runTaskTimerAsynchronously(() -> {
            logManager.cleanupOldLogs();
            storageService.pruneExpiredOwnersAsync(configManager.getPluginConfig().getStorage().getOwnerRetentionDays());
        }, TICKS_PER_HOUR, TICKS_PER_DAY);

        ArlCommandTree commandTree = new ArlCommandTree(this, configManager, messageManager, logManager, redstoneEngine);
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(
                    getPluginMeta(),
                    commandTree.build(),
                    "Main AntiRedstoneLag command",
                    List.of("antiredstonelag")
            );
        });

        new MetricsManager(this);
        registerPlaceholderApi();

        String enabledMsg = messageManager.getMessagesConfig().getMessages().getEnabled();
        messageManager.sendIfNotBlank(getServer().getConsoleSender(), enabledMsg,
                Map.of("version", getPluginMeta().getVersion()));

        updateChecker = new UpdateChecker(this, scheduler, MODRINTH_PROJECT_ID, MODRINTH_PROJECT_URL);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        updateChecker.checkForUpdates();
    }

    public void reloadPlugin() {
        configManager.reloadConfig();
        messageManager.reloadMessages();
        logManager.reload();
        if (clearLaggManager != null) {
            clearLaggManager.reloadRules();
        }
        if (redstoneListener != null) {
            redstoneListener.refreshCache();
        }
        if (redstoneEngine != null) {
            redstoneEngine.getAdaptiveMonitor().updateSnapshot();
        }
        scheduleResetTask();
    }

    private synchronized void scheduleResetTask() {
        if (resetTaskHandle != null) {
            resetTaskHandle.cancel();
        }
        int interval = configManager.getResetInterval();
        resetTaskHandle = scheduler.runTaskTimer(redstoneEngine::resetIntervalCounters, interval, interval);
    }

    private void registerPlaceholderApi() {
        if (!configManager.getPluginConfig().getIntegrations().getPlaceholderApi().isEnabled()) return;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                papiExpansion = new PapiExpansion(this);
                papiExpansion.register();
                getLogger().info("PlaceholderAPI expansion registered (%arl_*%).");
            } catch (Throwable t) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        if (resetTaskHandle != null) resetTaskHandle.cancel();
        if (cleanupTaskHandle != null) cleanupTaskHandle.cancel();
        if (inspectorService != null) inspectorService.cleanup();
        if (clearLaggManager != null) clearLaggManager.shutdown();

        if (papiExpansion != null) {
            try {
                papiExpansion.unregister();
            } catch (Throwable ignored) {
            }
        }

        if (messageManager != null && messageManager.getMessagesConfig() != null) {
            messageManager.sendIfNotBlank(getServer().getConsoleSender(),
                    messageManager.getMessagesConfig().getMessages().getDisabled());
        }

        if (storageService != null) {
            if (pluginStats != null) {
                storageService.saveStats(pluginStats);
            }
            storageService.close();
        }

        if (logManager != null) {
            logManager.logToFile("PLUGIN_DISABLED", "Plugin disabled", null);
            logManager.close();
        }
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public StorageService getStorageService() {
        return storageService;
    }

    public RedstoneEngine getRedstoneEngine() {
        return redstoneEngine;
    }

    public SnapshotService getSnapshotService() {
        return snapshotService;
    }

    public HotspotService getHotspotService() {
        return hotspotService;
    }

    public InspectorService getInspectorService() {
        return inspectorService;
    }

    public ClearLaggManager getClearLaggManager() {
        return clearLaggManager;
    }

    public StaffGuiManager getStaffGuiManager() {
        return staffGuiManager;
    }

    public RedstoneListener getRedstoneListener() {
        return redstoneListener;
    }
}
