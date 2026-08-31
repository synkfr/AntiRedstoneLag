package org.ayosynk.antiRedstoneLag.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.listener.RedstoneListener;
import org.ayosynk.antiRedstoneLag.manager.CounterManager;
import org.ayosynk.antiRedstoneLag.manager.CounterManager.HotspotGroup;
import org.ayosynk.antiRedstoneLag.manager.LogManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CommandHandler implements BasicCommand {
    private static final int HOTSPOT_MAX = 50;
    private static final int HOTSPOT_PAGE = 10;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final LogManager logManager;
    private final CounterManager counterManager;
    private final TabCompleteHandler tabCompleteHandler;
    private final Map<UUID, Long> activeInspectors = new ConcurrentHashMap<>();

    public CommandHandler(AntiRedstoneLag plugin, ConfigManager configManager,
                          MessageManager messageManager, LogManager logManager,
                          CounterManager counterManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
        this.counterManager = counterManager;
        this.tabCompleteHandler = new TabCompleteHandler();
        startInspectorTask();
    }

    private void startInspectorTask() {
        plugin.getScheduler().runTaskTimer(() -> {
            if (activeInspectors.isEmpty()) return;
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : activeInspectors.entrySet()) {
                UUID uuid = entry.getKey();
                long expiry = entry.getValue();
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline() || now > expiry) {
                    activeInspectors.remove(uuid);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(messageManager.parseMessage(messageManager.getMessagesConfig().getCommands().getInspectDisabled()));
                    }
                    continue;
                }

                Location pLoc = player.getLocation();
                long chunkKey = CounterManager.packChunk(pLoc.getBlockX() >> 4, pLoc.getBlockZ() >> 4);
                int chunkUps = counterManager.getChunkUpdates(pLoc.getWorld().getUID(), chunkKey);
                double mspt = Bukkit.getAverageTickTime();

                player.sendActionBar(MM.deserialize(
                        "<gold>ARL Inspect <dark_gray>| <yellow>Chunk: <aqua>" + chunkUps + " UPS" +
                        " <dark_gray>| <yellow>MSPT: <green>" + String.format("%.1f", mspt) + "ms" +
                        " <dark_gray>| <yellow>TPS: <green>" + String.format("%.1f", Bukkit.getTPS()[0])));

                player.spawnParticle(Particle.DUST, pLoc.clone().add(0, 0.5, 0), 2, 0.5, 0.5, 0.5,
                        new Particle.DustOptions(Color.fromRGB(78, 205, 196), 1.0f));
            }
        }, 10L, 10L);
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
            case "hotspots":
                if (!hasPermission(sender, "antiredstonelag.hotspots")) return;
                hotspotsCommand(sender, args);
                break;
            case "inspect":
                if (!hasPermission(sender, "antiredstonelag.inspect")) return;
                inspectCommand(sender, args);
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

    private void inspectCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Only players can use the visual inspector.</red>"));
            return;
        }

        UUID uuid = player.getUniqueId();
        if (activeInspectors.containsKey(uuid)) {
            activeInspectors.remove(uuid);
            player.sendMessage(messageManager.parseMessage(messageManager.getMessagesConfig().getCommands().getInspectDisabled()));
            return;
        }

        int durationSeconds = 30;
        if (args.length > 1) {
            try {
                durationSeconds = Math.max(5, Math.min(300, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
            }
        }

        final int finalDuration = durationSeconds;
        activeInspectors.put(uuid, System.currentTimeMillis() + (finalDuration * 1000L));
        player.sendMessage(messageManager.parseMessage(messageManager.getMessagesConfig().getCommands().getInspectEnabled())
                .replaceText(t -> t.matchLiteral("{duration}").replacement(String.valueOf(finalDuration))));
    }

    private void hotspotsCommand(CommandSender sender, String[] args) {
        boolean isExport = args.length > 1 && args[1].equalsIgnoreCase("export");
        if (isExport) {
            exportHotspots(sender);
            return;
        }

        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        List<HotspotGroup> hotspots = counterManager.getHotspots(HOTSPOT_MAX);

        int totalPages = Math.max(1, (int) Math.ceil(hotspots.size() / (double) HOTSPOT_PAGE));
        page = Math.max(1, Math.min(page, totalPages));
        int start = (page - 1) * HOTSPOT_PAGE;
        int end = Math.min(start + HOTSPOT_PAGE, hotspots.size());

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

        for (int i = start; i < end; i++) {
            HotspotGroup g = hotspots.get(i);
            int rank = i + 1;

            String prefix;
            if (rank == 1) prefix = "<gold>🥇</gold> ";
            else if (rank == 2) prefix = "<gray>🥈</gray> ";
            else if (rank == 3) prefix = "<#CD7F32>🥉</#CD7F32> ";
            else prefix = "<dark_gray>#" + rank + "</dark_gray> ";

            Component row = MM.deserialize(
                    prefix +
                    "<white>" + g.worldName + "</white> " +
                    "<aqua>[" + g.centerBlockX + ", ~, " + g.centerBlockZ + "]</aqua>" +
                    " <dark_gray>·</dark_gray> Act: <green>" + g.totalActivity + "</green>" +
                    " <dark_gray>·</dark_gray> <dark_gray>Chunks: " + g.chunkCount + "</dark_gray>"
            )
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
        File exportFile = new File(logsDir, "hotspot-export-" + timestamp + ".txt");

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

        sender.sendMessage(MM.deserialize(
                "<green>✓ Exported <white>" + hotspots.size() + "</white> hotspot" +
                (hotspots.size() == 1 ? "" : "s") + " to:</green>"));
        sender.sendMessage(MM.deserialize(
                "<aqua>" + exportFile.getAbsolutePath() + "</aqua>"));
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