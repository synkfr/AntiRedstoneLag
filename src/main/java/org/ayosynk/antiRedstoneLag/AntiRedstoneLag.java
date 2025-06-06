package org.ayosynk.antiRedstoneLag;

import org.bukkit.plugin.java.JavaPlugin;

public class AntiRedstoneLag extends JavaPlugin {
    private CounterManager counterManager;
    private RedstoneListener redstoneListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigManager configManager = new ConfigManager(this);
        counterManager = new CounterManager(configManager);
        redstoneListener = new RedstoneListener(counterManager, configManager);

        getServer().getPluginManager().registerEvents(redstoneListener, this);
        getServer().getScheduler().runTaskTimer(this, counterManager::resetCounters, 20, 20);
        getLogger().info("AntiRedstoneLag enabled!");
        getCommand("arl-reload").setExecutor(new CommandHandler(this, configManager));
    }

    @Override
    public void onDisable() {
        getLogger().info("AntiRedstoneLag disabled!");
    }
}