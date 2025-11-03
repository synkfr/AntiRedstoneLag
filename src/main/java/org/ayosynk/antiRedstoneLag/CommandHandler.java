package org.ayosynk.antiRedstoneLag;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.file.Files;

public class CommandHandler implements CommandExecutor {
    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final LogManager logManager;

    public CommandHandler(AntiRedstoneLag plugin, ConfigManager configManager, MessageManager messageManager, LogManager logManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!hasPermission(sender, "antiredstonelag.reload")) return true;
                reloadCommand(sender);
                break;

            case "stats":
                if (!hasPermission(sender, "antiredstonelag.stats")) return true;
                statsCommand(sender);
                break;

            case "logs":
                if (!hasPermission(sender, "antiredstonelag.logs")) return true;
                logsCommand(sender, args);
                break;

            case "help":
            default:
                showHelp(sender);
                break;
        }
        return true;
    }

    private void showHelp(CommandSender sender) {
        String helpMessage = messageManager.getMessage("commands.help",
                        "&#FFD93D┌─ &6AntiRedstoneLag &7v{version} ──────┐\n" +
                                "&6/arl reload &7- Reload configuration and messages\n" +
                                "&6/arl stats &7- View plugin statistics\n" +
                                "&6/arl logs &7- View or download logs\n" +
                                "&6/arl help &7- Show this help message\n" +
                                "&#FFD93D└────────────────────────────┘")
                .replace("{version}", plugin.getDescription().getVersion());
        sender.sendMessage(helpMessage);
    }

    private void reloadCommand(CommandSender sender) {
        configManager.reloadConfig();
        messageManager.reloadMessages();
        String reloadMessage = messageManager.getMessage("commands.reload-success", "&#4ECDC4✓ &aConfiguration and messages reloaded successfully!");
        sender.sendMessage(reloadMessage);
        logManager.logToFile("COMMAND", sender.getName() + " executed reload command", null);
    }

    private void statsCommand(CommandSender sender) {
        // This would integrate with CounterManager stats
        String statsMessage = messageManager.getMessage("commands.stats",
                        "&#4ECDC4┌─ &bAntiRedstoneLag Statistics &7─┐\n" +
                                "&7Chunks monitored: &e{chunks}\n" +
                                "&7Blocks monitored: &e{blocks}\n" +
                                "&7Removals today: &e{removals}\n" +
                                "&7Performance: &a{performance}%\n" +
                                "&#4ECDC4└────────────────────────┘")
                .replace("{chunks}", "0")
                .replace("{blocks}", "0")
                .replace("{removals}", "0")
                .replace("{performance}", "100");
        sender.sendMessage(statsMessage);
    }

    private void logsCommand(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("download") && sender instanceof Player) {
            // Provide log file to player
            provideLogFile((Player) sender);
        } else {
            String logInfo = messageManager.getMessage("commands.logs-info",
                            "&#4ECDC4Logging Information:\n" +
                                    "&7Status: {status}\n" +
                                    "&7Log folder: &e{folder}\n" +
                                    "&7Use &6/arl logs download &7to get latest log file")
                    .replace("{status}", logManager.isEnabled() ? "&aEnabled" : "&cDisabled")
                    .replace("{folder}", logManager.getLogsFolder().getAbsolutePath());
            sender.sendMessage(logInfo);
        }
    }

    private void provideLogFile(Player player) {
        // Implementation for providing log file to player
        // This would typically involve sending the file via chat or download link
        player.sendMessage(messageManager.getMessage("commands.logs-download", "&#FF6B6B✗ &cLog download feature not implemented yet"));
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        String noPerm = messageManager.getMessage("commands.no-permission", "&#FF6B6B✗ &cYou don't have permission to use this command!");
        sender.sendMessage(noPerm);
        return false;
    }
}