package org.ayosynk.antiRedstoneLag;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandHandler implements CommandExecutor {
    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;

    public CommandHandler(AntiRedstoneLag plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            configManager.reloadConfig();
            sender.sendMessage("AntiRedstoneLag configuration reloaded!");
            return true;
        }
        return false;
    }
}