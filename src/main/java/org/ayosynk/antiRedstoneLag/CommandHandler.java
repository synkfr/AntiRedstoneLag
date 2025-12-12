package org.ayosynk.antiRedstoneLag;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * Handles all plugin commands for AntiRedstoneLag.
 * Supports reload, stats, logs, and help subcommands.
 */
public class CommandHandler implements CommandExecutor {
    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final LogManager logManager;
    private final CounterManager counterManager;

    public CommandHandler(AntiRedstoneLag plugin, ConfigManager configManager, MessageManager messageManager, LogManager logManager, CounterManager counterManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
        this.counterManager = counterManager;
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

        // Refresh cached config values in listener
        RedstoneListener listener = plugin.getRedstoneListener();
        if (listener != null) {
            listener.refreshCache();
        }

        String reloadMessage = messageManager.getMessage("commands.reload-success", "&#4ECDC4✓ &aConfiguration and messages reloaded successfully!");
        sender.sendMessage(reloadMessage);
        logManager.logToFile("COMMAND", sender.getName() + " executed reload command", null);
    }

    private void statsCommand(CommandSender sender) {
        String statsMessage = messageManager.getMessage("commands.stats",
                        "&#4ECDC4┌─ &bAntiRedstoneLag Statistics &7─┐\n" +
                                "&7Chunks monitored: &e{chunks}\n" +
                                "&7Blocks monitored: &e{blocks}\n" +
                                "&7Total removals: &e{total_removals}\n" +
                                "&7Removals today: &e{today_removals}\n" +
                                "&7Performance: &a{performance}%\n" +
                                "&#4ECDC4└────────────────────────┘")
                .replace("{chunks}", String.valueOf(counterManager.getChunksMonitored()))
                .replace("{blocks}", String.valueOf(counterManager.getBlocksMonitored()))
                .replace("{total_removals}", String.valueOf(counterManager.getTotalRemovals()))
                .replace("{today_removals}", String.valueOf(counterManager.getRemovalsToday()))
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
        File logsFolder = logManager.getLogsFolder();
        if (logsFolder == null || !logsFolder.exists()) {
            player.sendMessage(messageManager.getMessage("commands.logs-no-folder", "&#FF6B6B✗ &cLogs folder not found!"));
            return;
        }

        File[] logFiles = logsFolder.listFiles((dir, name) -> name.startsWith("redstone-logs-") && name.endsWith(".log"));
        if (logFiles == null || logFiles.length == 0) {
            player.sendMessage(messageManager.getMessage("commands.logs-empty", "&#FF6B6B✗ &cNo log files found!"));
            return;
        }

        // Sort by last modified (newest first)
        Arrays.sort(logFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        File latestLog = logFiles[0];

        // Send log file info to player
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String fileSize = formatFileSize(latestLog.length());
        String lastModified = sdf.format(new Date(latestLog.lastModified()));

        String logInfo = messageManager.getMessage("commands.logs-file-info",
                        "&#4ECDC4┌─ &bLatest Log File &7─┐\n" +
                                "&7File: &e{filename}\n" +
                                "&7Size: &e{size}\n" +
                                "&7Modified: &e{modified}\n" +
                                "&7Path: &e{path}\n" +
                                "&#4ECDC4└────────────────────┘")
                .replace("{filename}", latestLog.getName())
                .replace("{size}", fileSize)
                .replace("{modified}", lastModified)
                .replace("{path}", latestLog.getAbsolutePath());

        player.sendMessage(logInfo);
        logManager.logToFile("COMMAND", player.getName() + " viewed log file info: " + latestLog.getName(), null);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
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