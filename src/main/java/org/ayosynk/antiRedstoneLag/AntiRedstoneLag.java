package org.ayosynk.antiRedstoneLag;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import org.ayosynk.antiRedstoneLag.command.CommandHandler;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.listener.RedstoneListener;
import org.ayosynk.antiRedstoneLag.listener.UpdateChecker;
import org.ayosynk.antiRedstoneLag.manager.CounterManager;
import org.ayosynk.antiRedstoneLag.manager.LogManager;
import org.ayosynk.antiRedstoneLag.manager.MetricsManager;
import org.ayosynk.antiRedstoneLag.manager.SnapshotManager;
import org.ayosynk.antiRedstoneLag.scheduler.BukkitSchedulerImpl;
import org.ayosynk.antiRedstoneLag.scheduler.FoliaSchedulerImpl;
import org.ayosynk.antiRedstoneLag.scheduler.Scheduler;

import java.util.List;

public class AntiRedstoneLag extends JavaPlugin {
    private static final long TICKS_PER_HOUR = 20L * 60 * 60;
    private static final long TICKS_PER_DAY = TICKS_PER_HOUR * 24;
    private static final String MODRINTH_PROJECT_ID = "5UOt11Yc";
    private static final String MODRINTH_PROJECT_URL = "https://modrinth.com/plugin/antiredstonelag";

    private CounterManager counterManager;
    private RedstoneListener redstoneListener;
    private MessageManager messageManager;
    private LogManager logManager;
    private SnapshotManager snapshotManager;
    private ConfigManager configManager;
    @SuppressWarnings("unused")
    private MetricsManager metricsManager;
    private UpdateChecker updateChecker;
    private Scheduler scheduler;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        logManager = new LogManager(this);
        metricsManager = new MetricsManager(this);
        snapshotManager = new SnapshotManager(this, configManager, messageManager);

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionConfiguration");
            scheduler = new FoliaSchedulerImpl(this);
            getLogger().info("Detected Folia environment. Using regional schedulers.");
        } catch (ClassNotFoundException e) {
            scheduler = new BukkitSchedulerImpl(this);
        }

        counterManager = new CounterManager(this, configManager, messageManager, logManager, getDataFolder());
        redstoneListener = new RedstoneListener(counterManager, configManager);

        getServer().getPluginManager().registerEvents(redstoneListener, this);
        int resetInterval = configManager.getResetInterval();
        scheduler.runTaskTimer(() -> {
            counterManager.resetCounters();
            redstoneListener.clearDisabledBlocks();
        }, resetInterval, resetInterval);

        scheduler.runTaskTimerAsynchronously(logManager::cleanupOldLogs, TICKS_PER_HOUR, TICKS_PER_DAY);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(
                "arl",
                "Main AntiRedstoneLag command",
                List.of(),
                new CommandHandler(this, configManager, messageManager, logManager, counterManager)
            );
        });

        String enabledMsg = messageManager.getMessagesConfig().getMessages().getEnabled()
                .replace("{version}", getPluginMeta().getVersion());
        getServer().getConsoleSender().sendMessage(messageManager.parseMessage(enabledMsg));

        updateChecker = new UpdateChecker(this, scheduler, MODRINTH_PROJECT_ID, MODRINTH_PROJECT_URL);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        updateChecker.checkForUpdates();
    }

    @Override
    public void onDisable() {
        if (messageManager != null && messageManager.getMessagesConfig() != null) {
            String disabledMsg = messageManager.getMessagesConfig().getMessages().getDisabled();
            getServer().getConsoleSender().sendMessage(messageManager.parseMessage(disabledMsg));
        }

        if (counterManager != null) {
            counterManager.saveStats();
        }

        if (logManager != null) {
            logManager.logToFile("PLUGIN_DISABLED", "Plugin disabled", null);
            logManager.close();
        }
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

    public SnapshotManager getSnapshotManager() {
        return snapshotManager;
    }

    public RedstoneListener getRedstoneListener() {
        return redstoneListener;
    }

    public CounterManager getCounterManager() {
        return counterManager;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }
}