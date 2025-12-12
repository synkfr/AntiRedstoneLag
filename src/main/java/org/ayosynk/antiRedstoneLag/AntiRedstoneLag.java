package org.ayosynk.antiRedstoneLag;

import org.bukkit.plugin.java.JavaPlugin;

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

    // SpigotMC resource ID for update checking (replace with actual ID when published)
    private static final int SPIGOT_RESOURCE_ID = 0; // TODO: Set actual resource ID

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        logManager = new LogManager(this);
        metricsManager = new MetricsManager(this);

        counterManager = new CounterManager(configManager, messageManager, logManager, getDataFolder());
        redstoneListener = new RedstoneListener(counterManager, configManager, messageManager, logManager);

        getServer().getPluginManager().registerEvents(redstoneListener, this);
        int resetInterval = configManager.getResetInterval();
        getServer().getScheduler().runTaskTimer(this, counterManager::resetCounters, resetInterval, resetInterval);

        // Start cleanup task for old logs
        getServer().getScheduler().runTaskTimerAsynchronously(this, logManager::cleanupOldLogs, TICKS_PER_HOUR, TICKS_PER_DAY);

        getLogger().info("AntiRedstoneLag enabled!");
        getCommand("arl").setExecutor(new CommandHandler(this, configManager, messageManager, logManager, counterManager));
        getCommand("arl").setTabCompleter(new TabCompleteHandler());

        // Send enabled message
        String enabledMsg = messageManager.getMessage("messages.enabled", "&#4ECDC4✓ &aAntiRedstoneLag v{version} has been enabled!")
                .replace("{version}", getDescription().getVersion());
        getLogger().info(net.md_5.bungee.api.ChatColor.stripColor(enabledMsg));

        // Check for updates (only if resource ID is set)
        if (SPIGOT_RESOURCE_ID > 0) {
            updateChecker = new UpdateChecker(this, SPIGOT_RESOURCE_ID);
            getServer().getPluginManager().registerEvents(updateChecker, this);
            updateChecker.checkForUpdates();
        }
    }

    @Override
    public void onDisable() {
        String disabledMsg = messageManager.getMessage("messages.disabled", "&#FF6B6B✗ &cAntiRedstoneLag has been disabled!");
        getLogger().info(net.md_5.bungee.api.ChatColor.stripColor(disabledMsg));

        // Save statistics before shutdown
        if (counterManager != null) {
            counterManager.saveStats();
        }

        if (logManager != null) {
            logManager.logToFile("PLUGIN_DISABLED", "Plugin disabled", null);
            logManager.close();
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
}