package org.ayosynk.antiRedstoneLag.command;

import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.manager.CounterManager;
import org.ayosynk.antiRedstoneLag.manager.CounterManager.HotspotGroup;
import org.ayosynk.antiRedstoneLag.manager.LogManager;
import org.ayosynk.antiRedstoneLag.listener.RedstoneListener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Handles all plugin commands for AntiRedstoneLag.
 * Supports reload, stats, logs, hotspots, and help subcommands.
 */
public class CommandHandler implements CommandExecutor {
    private static final int HOTSPOT_MAX   = 50;
    private static final int HOTSPOT_PAGE  = 10;
    private static final MiniMessage MM    = MiniMessage.miniMessage();

    private final AntiRedstoneLag plugin;
    private final ConfigManager   configManager;
    private final MessageManager  messageManager;
    private final LogManager      logManager;
    private final CounterManager  counterManager;

    public CommandHandler(AntiRedstoneLag plugin, ConfigManager configManager,
                          MessageManager messageManager, LogManager logManager,
                          CounterManager counterManager) {
        this.plugin         = plugin;
        this.configManager  = configManager;
        this.messageManager = messageManager;
        this.logManager     = logManager;
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
            case "hotspots":
                if (!hasPermission(sender, "antiredstonelag.hotspots")) return true;
                hotspotsCommand(sender, args);
                break;
            case "help":
            default:
                showHelp(sender);
                break;
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Hotspots command
    // -----------------------------------------------------------------------

    /**
     * /arl hotspots [page]   – paginated in-game list
     * /arl hotspots export   – write full list to a timestamped file in logs/
     */
    private void hotspotsCommand(CommandSender sender, String[] args) {
        boolean isExport = args.length > 1 && args[1].equalsIgnoreCase("export");
        if (isExport) {
            exportHotspots(sender);
            return;
        }

        int page = 1;
        if (args.length > 1) {
            try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }

        // Compute hotspots (off-hot-path; runs in the command thread)
        List<HotspotGroup> hotspots = counterManager.getHotspots(HOTSPOT_MAX);

        int totalPages = Math.max(1, (int) Math.ceil(hotspots.size() / (double) HOTSPOT_PAGE));
        page = Math.max(1, Math.min(page, totalPages));
        int start = (page - 1) * HOTSPOT_PAGE;
        int end   = Math.min(start + HOTSPOT_PAGE, hotspots.size());

        // ── Header ──────────────────────────────────────────────────────────
        sender.sendMessage(MM.deserialize(
                "<gold>━━━ <yellow>🔥 Redstone Hotspots</yellow>" +
                " <dark_gray>(" + hotspots.size() + " group" + (hotspots.size() == 1 ? "" : "s") + ")" +
                " <gold>━━━"));

        if (hotspots.isEmpty()) {
            sender.sendMessage(MM.deserialize(
                    "<gray>No redstone activity is tracked yet. Counters reset every interval.</gray>"));
            return;
        }

        sender.sendMessage(MM.deserialize(
                "<dark_gray>Adj. chunks merged · Weighted centre · Top " + HOTSPOT_MAX + "</dark_gray>"));

        // ── Rows ─────────────────────────────────────────────────────────────
        for (int i = start; i < end; i++) {
            HotspotGroup g    = hotspots.get(i);
            int          rank = i + 1;

            // Medal prefix for top 3
            String prefix;
            if      (rank == 1) prefix = "<gold>🥇</gold> ";
            else if (rank == 2) prefix = "<gray>🥈</gray> ";
            else if (rank == 3) prefix = "<#CD7F32>🥉</#CD7F32> ";
            else                prefix = "<dark_gray>#" + rank + "</dark_gray> ";

            // Row text
            Component row = MM.deserialize(
                    prefix +
                    "<white>" + g.worldName + "</white> " +
                    "<aqua>[" + g.centerBlockX + ", ~, " + g.centerBlockZ + "]</aqua>" +
                    " <dark_gray>·</dark_gray> Act: <green>" + g.totalActivity + "</green>" +
                    " <dark_gray>·</dark_gray> <dark_gray>Chunks: " + g.chunkCount + "</dark_gray>"
            )
            // Click → suggest /tp so player can fill in a Y level
            .clickEvent(ClickEvent.suggestCommand(
                    "/tp @s " + g.centerBlockX + " ~ " + g.centerBlockZ))
            .hoverEvent(HoverEvent.showText(MM.deserialize(
                    "<gray>World: <white>" + g.worldName + "</white>\n" +
                    "Centre: <white>" + g.centerBlockX + ", ~, " + g.centerBlockZ + "</white>\n" +
                    "Activity: <green>" + g.totalActivity + "</green> updates\n" +
                    "Cluster size: <green>" + g.chunkCount + "</green> chunk(s)\n" +
                    "<yellow>Click to suggest teleport command</yellow>")));

            sender.sendMessage(row);
        }

        // ── Navigation bar ───────────────────────────────────────────────────
        Component nav = Component.empty();

        if (page > 1) {
            nav = nav.append(MM.deserialize(
                    "<gold>[<click:run_command:'/arl hotspots " + (page - 1) + "'>◄ Prev</click>]</gold>"))
                    .append(Component.text("  "));
        }

        nav = nav.append(MM.deserialize(
                "<gray>Page <white>" + page + "</white> / <white>" + totalPages + "</white></gray>"));

        if (page < totalPages) {
            nav = nav.append(Component.text("  "))
                    .append(MM.deserialize(
                            "<gold>[<click:run_command:'/arl hotspots " + (page + 1) + "'>Next ►</click>]</gold>"));
        }

        nav = nav.append(Component.text("  "))
                .append(MM.deserialize(
                        "<dark_gray>[<hover:show_text:'<gray>Export all " + hotspots.size() +
                        " hotspots to a log file'><click:run_command:'/arl hotspots export'>" +
                        "📄 Export</click></hover>]</dark_gray>"));

        sender.sendMessage(nav);
    }

    /**
     * Writes the full hotspot list (up to {@link #HOTSPOT_MAX}) to a timestamped
     * text file inside the plugin's {@code logs/} directory.
     */
    private void exportHotspots(CommandSender sender) {
        List<HotspotGroup> hotspots = counterManager.getHotspots(HOTSPOT_MAX);

        if (hotspots.isEmpty()) {
            sender.sendMessage(MM.deserialize(
                    "<red>✗ No hotspot data to export – no redstone activity tracked yet.</red>"));
            return;
        }

        File logsDir = logManager.getLogsFolder();
        if (logsDir == null) {
            logsDir = new File(plugin.getDataFolder(), "logs");
            logsDir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        File exportFile  = new File(logsDir, "hotspot-export-" + timestamp + ".txt");

        try (PrintWriter pw = new PrintWriter(new FileWriter(exportFile))) {
            pw.println("=== AntiRedstoneLag – Redstone Hotspot Export ===");
            pw.println("Generated : " + new Date());
            pw.println("Groups    : " + hotspots.size());
            pw.println("Algorithm : Adjacent-chunk BFS, activity-weighted centre");
            pw.println();
            pw.printf("%-4s  %-24s  %12s  %12s  %10s  %7s%n",
                    "Rank", "World", "Centre X", "Centre Z", "Activity", "Chunks");
            pw.println("─".repeat(80));

            for (int i = 0; i < hotspots.size(); i++) {
                HotspotGroup g = hotspots.get(i);
                pw.printf("%-4d  %-24s  %12d  %12d  %10d  %7d%n",
                        i + 1, g.worldName, g.centerBlockX, g.centerBlockZ,
                        g.totalActivity, g.chunkCount);
            }

            pw.println();
            pw.println("Teleport commands:");
            for (int i = 0; i < hotspots.size(); i++) {
                HotspotGroup g = hotspots.get(i);
                pw.printf("#%-3d  /tp @s %d ~ %d  (world: %s)%n",
                        i + 1, g.centerBlockX, g.centerBlockZ, g.worldName);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write hotspot export: " + e.getMessage());
            sender.sendMessage(MM.deserialize(
                    "<red>✗ Failed to write export file – check server logs.</red>"));
            return;
        }

        logManager.logToFile("HOTSPOT_EXPORT",
                "Exported " + hotspots.size() + " hotspots to " + exportFile.getName(),
                null);

        // Make the file path clickable in console; show path to players
        sender.sendMessage(MM.deserialize(
                "<green>✓ Exported <white>" + hotspots.size() + "</white> hotspot" +
                (hotspots.size() == 1 ? "" : "s") + " to:</green>"));
        sender.sendMessage(MM.deserialize(
                "<aqua>" + exportFile.getAbsolutePath() + "</aqua>"));
    }

    // -----------------------------------------------------------------------
    // Existing subcommands (unchanged)
    // -----------------------------------------------------------------------

    private void showHelp(CommandSender sender) {
        sender.sendMessage(messageManager.getMessage("commands.help",
                        "&#FFD93D┌─ &6AntiRedstoneLag &7v{version} ──────────┐\n" +
                                "&6/arl reload    &7- Reload configuration and messages\n" +
                                "&6/arl stats     &7- View plugin statistics\n" +
                                "&6/arl logs      &7- View or download logs\n" +
                                "&6/arl hotspots  &7- View top redstone hotspots\n" +
                                "&6/arl help      &7- Show this help message\n" +
                                "&#FFD93D└──────────────────────────────────────┘")
                .replaceText(t -> t.matchLiteral("{version}").replacement(plugin.getDescription().getVersion())));
    }

    private void reloadCommand(CommandSender sender) {
        configManager.reloadConfig();
        messageManager.reloadMessages();
        RedstoneListener listener = plugin.getRedstoneListener();
        if (listener != null) listener.refreshCache();
        sender.sendMessage(messageManager.getMessage("commands.reload-success",
                "&#4ECDC4✓ &aConfiguration and messages reloaded successfully!"));
        logManager.logToFile("COMMAND", sender.getName() + " executed reload command", null);
    }

    private void statsCommand(CommandSender sender) {
        sender.sendMessage(messageManager.getMessage("commands.stats",
                        "&#4ECDC4┌─ &bAntiRedstoneLag Statistics &7─┐\n" +
                                "&7Chunks monitored: &e{chunks}\n" +
                                "&7Blocks monitored: &e{blocks}\n" +
                                "&7Total removals: &e{total_removals}\n" +
                                "&7Removals today: &e{today_removals}\n" +
                                "&7Performance: &a{performance}%\n" +
                                "&#4ECDC4└────────────────────────┘")
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
            sender.sendMessage(messageManager.getMessage("commands.logs-info",
                            "&#4ECDC4Logging Information:\n" +
                                    "&7Status: {status}\n" +
                                    "&7Log folder: &e{folder}\n" +
                                    "&7Use &6/arl logs download &7to get latest log file")
                    .replaceText(t -> t.matchLiteral("{status}").replacement(logManager.isEnabled() ? "Enabled" : "Disabled"))
                    .replaceText(t -> t.matchLiteral("{folder}").replacement(logManager.getLogsFolder().getAbsolutePath())));
        }
    }

    private void provideLogFile(Player player) {
        File logsFolder = logManager.getLogsFolder();
        if (logsFolder == null || !logsFolder.exists()) {
            player.sendMessage(messageManager.getMessage("commands.logs-no-folder",
                    "&#FF6B6B✗ &cLogs folder not found!"));
            return;
        }
        File[] logFiles = logsFolder.listFiles(
                (dir, name) -> name.startsWith("redstone-logs-") && name.endsWith(".log"));
        if (logFiles == null || logFiles.length == 0) {
            player.sendMessage(messageManager.getMessage("commands.logs-empty",
                    "&#FF6B6B✗ &cNo log files found!"));
            return;
        }
        Arrays.sort(logFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        File latestLog = logFiles[0];

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        player.sendMessage(messageManager.getMessage("commands.logs-file-info",
                        "&#4ECDC4┌─ &bLatest Log File &7─┐\n" +
                                "&7File: &e{filename}\n" +
                                "&7Size: &e{size}\n" +
                                "&7Modified: &e{modified}\n" +
                                "&7Path: &e{path}\n" +
                                "&#4ECDC4└────────────────────┘")
                .replaceText(t -> t.matchLiteral("{filename}").replacement(latestLog.getName()))
                .replaceText(t -> t.matchLiteral("{size}").replacement(formatFileSize(latestLog.length())))
                .replaceText(t -> t.matchLiteral("{modified}").replacement(sdf.format(new Date(latestLog.lastModified()))))
                .replaceText(t -> t.matchLiteral("{path}").replacement(latestLog.getAbsolutePath())));
        logManager.logToFile("COMMAND",
                player.getName() + " viewed log file info: " + latestLog.getName(), null);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage(messageManager.getMessage("commands.no-permission",
                "&#FF6B6B✗ &cYou don't have permission to use this command!"));
        return false;
    }
}