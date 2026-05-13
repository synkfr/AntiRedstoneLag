package org.ayosynk.antiRedstoneLag;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.ayosynk.antiRedstoneLag.command.*;
import org.ayosynk.antiRedstoneLag.config.*;
import org.ayosynk.antiRedstoneLag.listener.*;
import org.ayosynk.antiRedstoneLag.manager.*;
import org.ayosynk.antiRedstoneLag.scheduler.*;

/**
 * Main plugin class for AntiRedstoneLag.
 * Manages initialization, shutdown, and provides access to managers.
 */
public class AntiRedstoneLag extends JavaPlugin {
    // Time constants
    private static final long TICKS_PER_HOUR = 20L * 60 * 60;
    private static final long TICKS_PER_DAY = TICKS_PER_HOUR * 24;

    private CounterManager counterManager;
    private RedstoneListener redstoneListener;
    private MessageManager messageManager;
    private LogManager logManager;
    private ConfigManager configManager;
    @SuppressWarnings("unused") // Kept for bStats integration
    private MetricsManager metricsManager;
    private UpdateChecker updateChecker;
    private net.kyori.adventure.platform.bukkit.BukkitAudiences aventura;
    private Scheduler scheduler;

    // SpigotMC resource ID for update checking (replace with actual ID when
    // published)
    private static final int SPIGOT_RESOURCE_ID = 130753; // TODO: Set actual resource ID

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        logManager = new LogManager(this);
        metricsManager = new MetricsManager(this);
        aventura = net.kyori.adventure.platform.bukkit.BukkitAudiences.create(this);

        // Platform-aware scheduler initialization
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

        // Start cleanup task for old logs
        scheduler.runTaskTimerAsynchronously(logManager::cleanupOldLogs, TICKS_PER_HOUR, TICKS_PER_DAY);

        getLogger().info("AntiRedstoneLag enabled!");
        PluginCommand arlCommand = getCommand("arl");
        if (arlCommand != null) {
            arlCommand.setExecutor(new CommandHandler(this, configManager, messageManager, logManager, counterManager));
            arlCommand.setTabCompleter(new TabCompleteHandler());
        } else {
            getLogger().severe("Command 'arl' not found in plugin.yml! Commands will not work.");
        }

        // Send enabled message
        getLogger().info("AntiRedstoneLag v" + getDescription().getVersion() + " has been enabled!");

        // Check for updates (only if resource ID is set)
        if (SPIGOT_RESOURCE_ID > 0) {
            updateChecker = new UpdateChecker(this, scheduler, SPIGOT_RESOURCE_ID);
            getServer().getPluginManager().registerEvents(updateChecker, this);
            updateChecker.checkForUpdates();
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("AntiRedstoneLag has been disabled!");

        // Save statistics before shutdown
        if (counterManager != null) {
            counterManager.saveStats();
        }

        if (logManager != null) {
            logManager.logToFile("PLUGIN_DISABLED", "Plugin disabled", null);
            logManager.close();
        }

        if (aventura != null) {
            aventura.close();
            aventura = null;
        }
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public RedstoneListener getRedstoneListener() {
        return redstoneListener;
    }

    public CounterManager getCounterManager() {
        return counterManager;
    }

    public net.kyori.adventure.platform.bukkit.BukkitAudiences adventure() {
        return aventura;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }
}