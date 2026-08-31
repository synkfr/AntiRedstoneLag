package org.ayosynk.antiRedstoneLag.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.manager.CounterManager;
import org.ayosynk.antiRedstoneLag.manager.LogManager;
import org.ayosynk.antiRedstoneLag.listener.RedstoneListener;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

public class CommandHandler implements BasicCommand {
    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final LogManager logManager;
    private final CounterManager counterManager;
    private final TabCompleteHandler tabCompleteHandler;

    public CommandHandler(AntiRedstoneLag plugin, ConfigManager configManager, MessageManager messageManager, LogManager logManager, CounterManager counterManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
        this.counterManager = counterManager;
        this.tabCompleteHandler = new TabCompleteHandler();
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!hasPermission(sender, "antiredstonelag.reload")) return;
                reloadCommand(sender);
                break;

            case "stats":
                if (!hasPermission(sender, "antiredstonelag.stats")) return;
                statsCommand(sender);
                break;

            case "logs":
                if (!hasPermission(sender, "antiredstonelag.logs")) return;
                logsCommand(sender, args);
                break;

            case "help":
            default:
                showHelp(sender);
                break;
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return tabCompleteHandler.onTabComplete(source.getSender(), args);
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission("antiredstonelag.use");
    }

    @Override
    public @Nullable String permission() {
        return "antiredstonelag.use";
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(messageManager.parseMessage(messageManager.getMessagesConfig().getCommands().getHelp())
                .replaceText(t -> t.matchLiteral("{version}").replacement(plugin.getPluginMeta().getVersion())));
    }

    private void reloadCommand(CommandSender sender) {
        configManager.reloadConfig();
        messageManager.reloadMessages();

        RedstoneListener listener = plugin.getRedstoneListener();
        if (listener != null) {
            listener.refreshCache();
        }

        sender.sendMessage(messageManager.parseMessage(messageManager.getMessagesConfig().getCommands().getReloadSuccess()));
        logManager.logToFile("COMMAND", sender.getName() + " executed reload command", null);
    }

    private void statsCommand(CommandSender sender) {
        sender.sendMessage(messageManager.parseMessage(messageManager.getMessagesConfig().getCommands().getStats())
                .replaceText(t -> t.matchLiteral("{chunks}").replacement(String.valueOf(counterManager.getChunksMonitored())))
                .replaceText(t -> t.matchLiteral("{blocks}").replacement(String.valueOf(counterManager.getBlocksMonitored())))
                .replaceText(t -> t.matchLiteral("{total_removals}").replacement(String.valueOf(counterManager.getTotalRemovals())))
                .replaceText(t -> t.matchLiteral("{today_removals}").replacement(String.valueOf(counterManager.getRemovalsToday())))
                .replaceText(t -> t.matchLiteral("{performance}").replacement("100")));
    }

    private void logsCommand(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("download") && sender instanceof Player) {
            provideLogFile((Player) sender);
        } else {
            sender.sendMessage(messageManager.parseMessage(messageManager.getMessagesConfig().getCommands().getLogsInfo())
                    .replaceText(t -> t.matchLiteral("{status}").replacement(logManager.isEnabled() ? "Enabled" : "Disabled"))
                    .replaceText(t -> t.matchLiteral("{folder}").replacement(logManager.getLogsFolder().getAbsolutePath())));
        }
    }

    private void provideLogFile(Player player) {
        File logsFolder = logManager.getLogsFolder();
        if (logsFolder == null || !logsFolder.exists()) {
            player.sendMessage(messageManager.parseMessage(messageManager.getMessagesConfig().getCommands().getLogsNoFolder()));
            return;
        }

        File[] logFiles = logsFolder.listFiles((dir, name) -> name.startsWith("redstone-logs-") && name.endsWith(".log"));
        if (logFiles == null || logFiles.length == 0) {
            player.sendMessage(messageManager.parseMessage(messageManager.getMessagesConfig().getCommands().getLogsEmpty()));
            return;
        }

        Arrays.sort(logFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        File latestLog = logFiles[0];

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String fileSize = formatFileSize(latestLog.length());
        String lastModified = sdf.format(new Date(latestLog.lastModified()));

        player.sendMessage(messageManager.parseMessage(messageManager.getMessagesConfig().getCommands().getLogsFileInfo())
                .replaceText(t -> t.matchLiteral("{filename}").replacement(latestLog.getName()))
                .replaceText(t -> t.matchLiteral("{size}").replacement(fileSize))
                .replaceText(t -> t.matchLiteral("{modified}").replacement(lastModified))
                .replaceText(t -> t.matchLiteral("{path}").replacement(latestLog.getAbsolutePath())));
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
        sender.sendMessage(messageManager.parseMessage(messageManager.getMessagesConfig().getCommands().getNoPermission()));
        return false;
    }
}