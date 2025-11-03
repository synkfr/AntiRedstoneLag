package org.ayosynk.antiRedstoneLag;

import org.bukkit.plugin.java.JavaPlugin;

public class AntiRedstoneLag extends JavaPlugin {
    private CounterManager counterManager;
    private RedstoneListener redstoneListener;
    private MessageManager messageManager;
    private LogManager logManager;
    private MetricsManager metricsManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        ConfigManager configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        logManager = new LogManager(this);
        metricsManager = new MetricsManager(this);

        counterManager = new CounterManager(configManager, messageManager, logManager);
        redstoneListener = new RedstoneListener(counterManager, configManager, messageManager, logManager);

        getServer().getPluginManager().registerEvents(redstoneListener, this);
        getServer().getScheduler().runTaskTimer(this, counterManager::resetCounters, 20, 20);

        // Start cleanup task for old logs
        getServer().getScheduler().runTaskTimerAsynchronously(this, logManager::cleanupOldLogs, 20L * 60 * 60, 20L * 60 * 60 * 24);

        getLogger().info("AntiRedstoneLag enabled!");
        getCommand("arl").setExecutor(new CommandHandler(this, configManager, messageManager, logManager));
        getCommand("arl").setTabCompleter(new TabCompleteHandler());

        // Send enabled message
        String enabledMsg = messageManager.getMessage("messages.enabled", "&#4ECDC4✓ &aAntiRedstoneLag v{version} has been enabled!")
                .replace("{version}", getDescription().getVersion());
        getLogger().info(net.md_5.bungee.api.ChatColor.stripColor(enabledMsg));
    }

    @Override
    public void onDisable() {
        String disabledMsg = messageManager.getMessage("messages.disabled", "&#FF6B6B✗ &cAntiRedstoneLag has been disabled!");
        getLogger().info(net.md_5.bungee.api.ChatColor.stripColor(disabledMsg));

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
}