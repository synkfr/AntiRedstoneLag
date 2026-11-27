package org.ayosynk.antiRedstoneLag.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;
import org.ayosynk.antiRedstoneLag.listener.RedstoneListener;
import org.ayosynk.antiRedstoneLag.manager.ClearLaggManager;
import org.ayosynk.antiRedstoneLag.manager.CounterManager;
import org.ayosynk.antiRedstoneLag.manager.CounterManager.HotspotGroup;
import org.ayosynk.antiRedstoneLag.manager.LogManager;
import org.ayosynk.antiRedstoneLag.manager.SnapshotManager;
import org.ayosynk.antiRedstoneLag.manager.SnapshotManager.Snapshot;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CommandHandler implements BasicCommand {
    private static final int HOTSPOT_MAX = 50;
    private static final int HOTSPOT_PAGE = 10;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FILE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(ZoneId.systemDefault());

    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final LogManager logManager;
    private final CounterManager counterManager;
    private final TabCompleteHandler tabCompleteHandler;
    private final Map<UUID, Long> activeInspectors = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();

    public CommandHandler(AntiRedstoneLag plugin, ConfigManager configManager,
                          MessageManager messageManager, LogManager logManager,
                          CounterManager counterManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
        this.counterManager = counterManager;
        this.tabCompleteHandler = new TabCompleteHandler(plugin);
        startInspectorTask();
    }

    private void startInspectorTask() {
        plugin.getScheduler().runTaskTimer(() -> {
            if (activeInspectors.isEmpty()) return;
            long now = System.currentTimeMillis();

            final boolean folia = CounterManager.isFolia();
            final double mspt;
            final double tps;
            if (!folia) {
                double m = 0.0;
                double t = 20.0;
                try {
                    m = Bukkit.getAverageTickTime();
                    t = Bukkit.getTPS()[0];
                } catch (Throwable ignored) {
                }
                mspt = m;
                tps = t;
            } else {
                mspt = 0.0;
                tps = 20.0;
            }

            for (Map.Entry<UUID, Long> entry : activeInspectors.entrySet()) {
                UUID uuid = entry.getKey();
                long expiry = entry.getValue();
                Player player = Bukkit.getPlayer(uuid);

                if (player == null || !player.isOnline() || now > expiry) {
                    activeInspectors.remove(uuid);
                    BossBar removed = activeBossBars.remove(uuid);
                    if (removed != null && player != null && player.isOnline()) {
                        player.hideBossBar(removed);
                    }
                    if (player != null && player.isOnline()) {
                        messageManager.sendIfNotBlank(player,
                                messageManager.getMessagesConfig().getCommands().getInspectDisabled());
                    }
                    continue;
                }

                plugin.getScheduler().runTask(player, () -> {
                    if (!player.isOnline() || !activeInspectors.containsKey(uuid)) return;
                    Location pLoc = player.getLocation();
                    long chunkKey = CounterManager.packChunk(pLoc.getBlockX() >> 4, pLoc.getBlockZ() >> 4);
                    int chunkUps = counterManager.getChunkUpdates(pLoc.getWorld().getUID(), chunkKey);

                    BossBar.Color barColor;
                    float progress;
                    Component title;

                    if (folia) {
                        int threshold = Math.max(1, configManager.getChunkThreshold());
                        double ratio = Math.min(1.0, (double) chunkUps / threshold);
                        barColor = ratio < 0.5 ? BossBar.Color.GREEN : (ratio < 0.85 ? BossBar.Color.YELLOW : BossBar.Color.RED);
                        progress = (float) ratio;
                        title = MM.deserialize(
                                "<gold><b>ARL Inspect</b></gold> <dark_gray>|</dark_gray> <yellow>Chunk:</yellow> <aqua>" + chunkUps + " UPS</aqua>" +
                                " <dark_gray>|</dark_gray> <yellow>Mode:</yellow> <green>Folia</green>");
                    } else {
                        barColor = mspt <= 35.0 ? BossBar.Color.GREEN : (mspt <= 45.0 ? BossBar.Color.YELLOW : BossBar.Color.RED);
                        progress = (float) Math.min(1.0, Math.max(0.0, mspt / 50.0));
                        title = MM.deserialize(
                                "<gold><b>ARL Inspect</b></gold> <dark_gray>|</dark_gray> <yellow>Chunk:</yellow> <aqua>" + chunkUps + " UPS</aqua>" +
                                " <dark_gray>|</dark_gray> <yellow>MSPT:</yellow> <green>" + String.format("%.1f", mspt) + "ms</green>" +
                                " <dark_gray>|</dark_gray> <yellow>TPS:</yellow> <green>" + String.format("%.1f", tps) + "</green>");
                    }

                    BossBar bossBar = activeBossBars.get(uuid);
                    if (bossBar == null) {
                        bossBar = BossBar.bossBar(title, progress, barColor, BossBar.Overlay.PROGRESS);
                        activeBossBars.put(uuid, bossBar);
                        player.showBossBar(bossBar);
                    } else {
                        bossBar.name(title);
                        bossBar.progress(progress);
                        bossBar.color(barColor);
                    }

                    player.spawnParticle(Particle.DUST, pLoc.clone().add(0, 0.5, 0), 2, 0.5, 0.5, 0.5,
                            new Particle.DustOptions(Color.fromRGB(78, 205, 196), 1.0f));
                });
            }
        }, 10L, 10L);
    }

    public void cleanup() {
        for (Map.Entry<UUID, BossBar> entry : activeBossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.hideBossBar(entry.getValue());
            }
        }
        activeBossBars.clear();
        activeInspectors.clear();
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "redstone":
                if (!hasPermission(sender, "antiredstonelag.redstone")) return;
                redstoneCommand(sender, args);
                break;
            case "alerts":
                if (!hasPermission(sender, "antiredstonelag.alerts")) return;
                alertsCommand(sender, args);
                break;
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
            case "snapshot":
                if (!hasPermission(sender, "antiredstonelag.snapshot")) return;
                snapshotCommand(sender, args);
                break;
            case "clear":
            case "clearlagg":
                if (!hasPermission(sender, "antiredstonelag.clear")) return;
                clearLaggCommand(sender, args);
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
        return true;
    }

    @Override
    public @Nullable String permission() {
        return null;
    }

    private void redstoneCommand(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            String status = !configManager.isRedstoneEnabled()
                    ? "&cOFF"
                    : (configManager.getRemovalAction() == PluginConfig.RemovalAction.NOTIFY ? "&eNOTIFY ONLY" : "&aON");
            String alerts = configManager.isAlertsEnabled() ? "&aON" : "&cOFF";
            String raw = messageManager.getMessagesConfig().getCommands().getRedstoneStatus();
            Component msg = messageManager.parseMessage(raw)
                    .replaceText(t -> t.matchLiteral("{status}").replacement(status))
                    .replaceText(t -> t.matchLiteral("{action}").replacement(configManager.getRemovalAction().name()))
                    .replaceText(t -> t.matchLiteral("{alerts}").replacement(alerts));
            messageManager.sendIfNotBlank(sender, raw, msg);
            return;
        }

        String mode = args[1].toLowerCase();
        RedstoneListener listener = plugin.getRedstoneListener();

        switch (mode) {
            case "on":
                configManager.setRedstoneEnabled(true);
                if (configManager.getRemovalAction() == PluginConfig.RemovalAction.NOTIFY) {
                    configManager.setRemovalAction(PluginConfig.RemovalAction.FREEZE);
                }
                if (listener != null) listener.refreshCache();
                String rawOn = messageManager.getMessagesConfig().getCommands().getRedstoneOn();
                messageManager.sendIfNotBlank(sender, rawOn,
                        messageManager.parseMessage(rawOn).replaceText(t -> t.matchLiteral("{action}").replacement(configManager.getRemovalAction().name())));
                logManager.logToFile("COMMAND", sender.getName() + " set redstone protection ON (" + configManager.getRemovalAction().name() + ")", null);
                break;
            case "off":
                configManager.setRedstoneEnabled(false);
                counterManager.clearActiveBlocksAndLockdowns();
                if (listener != null) {
                    listener.clearDisabledBlocks();
                    listener.refreshCache();
                }
                messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getRedstoneOff());
                logManager.logToFile("COMMAND", sender.getName() + " set redstone protection OFF", null);
                break;
            case "notify":
                configManager.setRedstoneEnabled(true);
                configManager.setRemovalAction(PluginConfig.RemovalAction.NOTIFY);
                counterManager.clearActiveBlocksAndLockdowns();
                if (listener != null) {
                    listener.clearDisabledBlocks();
                    listener.refreshCache();
                }
                messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getRedstoneNotify());
                logManager.logToFile("COMMAND", sender.getName() + " set redstone protection to NOTIFY", null);
                break;
            default:
                sender.sendMessage(MM.deserialize("<red>Usage: /arl redstone <on|off|notify|status></red>"));
                break;
        }
    }

    private void alertsCommand(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            String raw = messageManager.getMessagesConfig().getCommands().getAlertsStatus();
            Component msg = messageManager.parseMessage(raw)
                    .replaceText(t -> t.matchLiteral("{status}").replacement(configManager.isAlertsEnabled() ? "&aON" : "&cOFF"))
                    .replaceText(t -> t.matchLiteral("{tps_lag}").replacement(configManager.isNotifyOnTpsLag() ? "&aON" : "&cOFF"))
                    .replaceText(t -> t.matchLiteral("{only_lag}").replacement(configManager.isOnlyWhenLagging() ? "&eON" : "&7OFF"));
            messageManager.sendIfNotBlank(sender, raw, msg);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "on":
                configManager.setAlertsEnabled(true);
                messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getAlertsOn());
                logManager.logToFile("COMMAND", sender.getName() + " enabled admin alerts", null);
                break;
            case "off":
                configManager.setAlertsEnabled(false);
                messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getAlertsOff());
                logManager.logToFile("COMMAND", sender.getName() + " disabled admin alerts", null);
                break;
            default:
                sender.sendMessage(MM.deserialize("<red>Usage: /arl alerts <on|off|status></red>"));
                break;
        }
    }

    private void clearLaggCommand(CommandSender sender, String[] args) {
        ClearLaggManager clm = plugin.getClearLaggManager();
        if (clm == null) return;

        if (args.length == 1) {
            clm.clearEntities(ClearLaggManager.ClearType.ITEMS);
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "all":
                clm.clearEntities(ClearLaggManager.ClearType.ALL);
                break;
            case "items":
                clm.clearEntities(ClearLaggManager.ClearType.ITEMS);
                break;
            case "mobs":
                clm.clearEntities(ClearLaggManager.ClearType.MOBS);
                break;
            case "projectiles":
                clm.clearEntities(ClearLaggManager.ClearType.PROJECTILES);
                break;
            case "xp":
                clm.clearEntities(ClearLaggManager.ClearType.XP);
                break;
            case "vehicles":
                clm.clearEntities(ClearLaggManager.ClearType.VEHICLES);
                break;
            case "cancel":
                clm.cancelScheduledClear();
                break;
            case "timer":
                PluginConfig.ClearLaggConfig cfg = configManager.getPluginConfig().getClearlagg();
                if (!cfg.isEnabled() || cfg.getIntervalSeconds() <= 0) {
                    sender.sendMessage(MM.deserialize("<gold>[ClearLagg] <gray>Automatic clear timer is <red>Disabled</red>."));
                } else {
                    sender.sendMessage(MM.deserialize("<gold>[ClearLagg] <yellow>Next scheduled clear in: <aqua>" + clm.getSecondsUntilClear() + "s</aqua>"));
                }
                break;
            case "count":
            default:
                ClearLaggManager.EntityCounts counts = clm.countEntities();
                String raw = messageManager.getMessagesConfig().getClearlagg().getCount();
                Component msg = messageManager.parseMessage(raw)
                        .replaceText(t -> t.matchLiteral("{items}").replacement(String.valueOf(counts.items)))
                        .replaceText(t -> t.matchLiteral("{projectiles}").replacement(String.valueOf(counts.projectiles)))
                        .replaceText(t -> t.matchLiteral("{monsters}").replacement(String.valueOf(counts.monsters)))
                        .replaceText(t -> t.matchLiteral("{xp_orbs}").replacement(String.valueOf(counts.xpOrbs)))
                        .replaceText(t -> t.matchLiteral("{vehicles}").replacement(String.valueOf(counts.vehicles)));
                messageManager.sendIfNotBlank(sender, raw, msg);
                break;
        }
    }

    private void snapshotCommand(CommandSender sender, String[] args) {
        SnapshotManager sm = plugin.getSnapshotManager();
        if (sm == null) return;

        if (args.length == 1 || (args.length == 2 && args[1].equalsIgnoreCase("list"))) {
            listSnapshots(sender);
            return;
        }

        String sub = args[1].toLowerCase();
        if (sub.equals("clear")) {
            sm.clearSnapshots();
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getSnapshotCleared());
            return;
        }

        if (sub.equals("view") && args.length > 2) {
            viewSnapshot(sender, args[2]);
            return;
        }

        if (sub.equals("tp") && args.length > 2) {
            tpSnapshot(sender, args[2]);
            return;
        }

        viewSnapshot(sender, args[1]);
    }

    private void listSnapshots(CommandSender sender) {
        SnapshotManager sm = plugin.getSnapshotManager();
        List<Snapshot> snapshots = sm.getSnapshots();

        if (snapshots.isEmpty()) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getSnapshotEmpty());
            return;
        }

        sender.sendMessage(MM.deserialize("<gold>--- <yellow>Forensic Lag Snapshots</yellow> <dark_gray>(" + snapshots.size() + ")</dark_gray> <gold>---"));

        for (Snapshot snap : snapshots) {
            String timeStr = TIME_FMT.format(Instant.ofEpochMilli(snap.timestamp));
            Component row = MM.deserialize(
                    "<gold>[" + snap.id + "]</gold> <gray>" + timeStr + "</gray> <dark_gray>|</dark_gray> " +
                    "<white>" + snap.worldName + "</white> <aqua>[" + snap.x + ", " + snap.y + ", " + snap.z + "]</aqua> " +
                    "<red>" + snap.triggerMaterial.name() + "</red> <gray>(Culprit: <yellow>" + snap.culpritName + "</yellow>)</gray> "
            )
            .append(MM.deserialize("<green>[<click:run_command:'/arl snapshot view " + snap.id + "'>View</click>]</green> "))
            .append(MM.deserialize("<aqua>[<click:run_command:'/arl snapshot tp " + snap.id + "'>TP</click>]</aqua>"));

            sender.sendMessage(row);
        }
    }

    private void viewSnapshot(CommandSender sender, String id) {
        SnapshotManager sm = plugin.getSnapshotManager();
        Snapshot snap = sm.getSnapshot(id);

        if (snap == null) {
            String raw = messageManager.getMessagesConfig().getCommands().getSnapshotNotFound();
            messageManager.sendIfNotBlank(sender, raw,
                    messageManager.parseMessage(raw).replaceText(t -> t.matchLiteral("{id}").replacement(id)));
            return;
        }

        sender.sendMessage(MM.deserialize("<gold>--- <yellow>Forensic Report: " + snap.id + "</yellow> <gold>---"));
        sender.sendMessage(MM.deserialize("<gray>Captured At: <white>" + DATE_TIME_FMT.format(Instant.ofEpochMilli(snap.timestamp)) + "</white>"));
        sender.sendMessage(MM.deserialize("<gray>Location: <aqua>" + snap.x + ", " + snap.y + ", " + snap.z + "</aqua> in <white>" + snap.worldName + "</white>"));
        sender.sendMessage(MM.deserialize("<gray>Culprit: <yellow>" + snap.culpritName + "</yellow> <dark_gray>(" + (snap.culpritUuid != null ? snap.culpritUuid : "N/A") + ")</dark_gray>"));
        sender.sendMessage(MM.deserialize("<gray>Trigger: <red>" + snap.triggerMaterial.name() + "</red> <dark_gray>|</dark_gray> Updates: <aqua>" + snap.chunkUpdates + " chunk / " + snap.blockUpdates + " block</aqua>"));
        sender.sendMessage(MM.deserialize("<gray>Total Redstone Components in Cluster: <green>" + snap.getTotalComponents() + "</green>"));
        sender.sendMessage(MM.deserialize("<yellow>Component Breakdown:</yellow>"));

        for (Map.Entry<Material, Integer> entry : snap.componentCounts.entrySet()) {
            sender.sendMessage(MM.deserialize("  <dark_gray>-</dark_gray> <gray>" + entry.getKey().name() + ": <white>" + entry.getValue() + "</white>"));
        }

        sender.sendMessage(MM.deserialize("<gold>[<click:run_command:'/arl snapshot tp " + snap.id + "'>Click here to Teleport to Snapshot Site</click>]</gold>"));
    }

    private void tpSnapshot(CommandSender sender, String id) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Only players can teleport to snapshot coordinates.</red>"));
            return;
        }

        SnapshotManager sm = plugin.getSnapshotManager();
        Snapshot snap = sm.getSnapshot(id);

        if (snap == null) {
            String raw = messageManager.getMessagesConfig().getCommands().getSnapshotNotFound();
            messageManager.sendIfNotBlank(sender, raw,
                    messageManager.parseMessage(raw).replaceText(t -> t.matchLiteral("{id}").replacement(id)));
            return;
        }

        World world = Bukkit.getWorld(snap.worldName);
        if (world == null) {
            player.sendMessage(MM.deserialize("<red>World '" + snap.worldName + "' is not currently loaded.</red>"));
            return;
        }

        Location loc = new Location(world, snap.x + 0.5, snap.y + 1, snap.z + 0.5);
        player.teleportAsync(loc);
        player.sendMessage(MM.deserialize("<green>✓ Teleported to snapshot <yellow>" + snap.id + "</yellow> at <aqua>" + snap.x + ", " + snap.y + ", " + snap.z + "</aqua> in world <white>" + snap.worldName + "</white>."));
    }

    private void inspectCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Only players can use the visual inspector.</red>"));
            return;
        }

        UUID uuid = player.getUniqueId();
        if (activeInspectors.containsKey(uuid)) {
            activeInspectors.remove(uuid);
            BossBar bar = activeBossBars.remove(uuid);
            if (bar != null) {
                player.hideBossBar(bar);
            }
            messageManager.sendIfNotBlank(player, messageManager.getMessagesConfig().getCommands().getInspectDisabled());
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
        String raw = messageManager.getMessagesConfig().getCommands().getInspectEnabled();
        messageManager.sendIfNotBlank(player, raw,
                messageManager.parseMessage(raw).replaceText(t -> t.matchLiteral("{duration}").replacement(String.valueOf(finalDuration))));
    }

    private void hotspotsCommand(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("export")) {
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

        if (hotspots.isEmpty()) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getHotspotsNone());
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(hotspots.size() / (double) HOTSPOT_PAGE));
        page = Math.max(1, Math.min(page, totalPages));
        int start = (page - 1) * HOTSPOT_PAGE;
        int end = Math.min(start + HOTSPOT_PAGE, hotspots.size());

        String headerRaw = messageManager.getMessagesConfig().getCommands().getHotspotsHeader();
        messageManager.sendIfNotBlank(sender, headerRaw,
                messageManager.parseMessage(headerRaw).replaceText(t -> t.matchLiteral("{count}").replacement(String.valueOf(hotspots.size()))));

        for (int i = start; i < end; i++) {
            HotspotGroup g = hotspots.get(i);
            int rank = i + 1;

            String rankColor = rank == 1 ? "<gold>" : (rank == 2 ? "<yellow>" : (rank == 3 ? "<aqua>" : "<dark_gray>"));
            String rankClose = rank == 1 ? "</gold>" : (rank == 2 ? "</yellow>" : (rank == 3 ? "</aqua>" : "</dark_gray>"));

            Component row = MM.deserialize(
                    rankColor + "#" + rank + rankClose + " " +
                    "<white>" + g.worldName + "</white> " +
                    "<aqua>[" + g.centerBlockX + ", ~, " + g.centerBlockZ + "]</aqua>" +
                    " <dark_gray>|</dark_gray> Act: <green>" + g.totalActivity + "</green>" +
                    " <dark_gray>|</dark_gray> <dark_gray>Chunks: " + g.chunkCount + "</dark_gray>"
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
                    "<gold>[<click:run_command:'/arl hotspots " + (page - 1) + "'>&lt; Prev</click>]</gold>"))
                    .append(Component.text("  "));
        }

        nav = nav.append(MM.deserialize(
                "<gray>Page <white>" + page + "</white> / <white>" + totalPages + "</white></gray>"));

        if (page < totalPages) {
            nav = nav.append(Component.text("  "))
                    .append(MM.deserialize(
                            "<gold>[<click:run_command:'/arl hotspots " + (page + 1) + "'>Next &gt;</click>]</gold>"));
        }

        nav = nav.append(Component.text("  "))
                .append(MM.deserialize(
                        "<dark_gray>[<hover:show_text:'<gray>Export all " + hotspots.size() +
                        " hotspots to a log file'><click:run_command:'/arl hotspots export'>" +
                        "Export</click></hover>]</dark_gray>"));

        sender.sendMessage(nav);
    }

    private void exportHotspots(CommandSender sender) {
        List<HotspotGroup> hotspots = counterManager.getHotspots(HOTSPOT_MAX);

        if (hotspots.isEmpty()) {
            sender.sendMessage(MM.deserialize(
                    "<red>✗ No hotspot data to export - no redstone activity tracked yet.</red>"));
            return;
        }

        File logsDir = logManager.getLogsFolder();
        if (logsDir == null) {
            logsDir = new File(plugin.getDataFolder(), "logs");
        }
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }

        String timestamp = FILE_TIME_FMT.format(Instant.now());
        File exportFile = new File(logsDir, "hotspot-export-" + timestamp + ".txt");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(exportFile.toPath(), StandardCharsets.UTF_8))) {
            pw.println("=== AntiRedstoneLag - Redstone Hotspot Export ===");
            pw.println("Generated : " + DATE_TIME_FMT.format(Instant.now()));
            pw.println("Groups    : " + hotspots.size());
            pw.println("Algorithm : Adjacent-chunk BFS, activity-weighted centre");
            pw.println();
            pw.printf("%-4s  %-24s  %12s  %12s  %10s  %7s%n",
                    "Rank", "World", "Centre X", "Centre Z", "Activity", "Chunks");
            pw.println("-".repeat(80));

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
                    "<red>✗ Failed to write export file - check server logs.</red>"));
            return;
        }

        logManager.logToFile("HOTSPOT_EXPORT",
                "Exported " + hotspots.size() + " hotspots to " + exportFile.getName(),
                null);

        String raw = messageManager.getMessagesConfig().getCommands().getHotspotsExported();
        messageManager.sendIfNotBlank(sender, raw,
                messageManager.parseMessage(raw).replaceText(t -> t.matchLiteral("{path}").replacement(exportFile.getAbsolutePath())));
    }

    private void showHelp(CommandSender sender) {
        String raw = messageManager.getMessagesConfig().getCommands().getHelp();
        messageManager.sendIfNotBlank(sender, raw,
                messageManager.parseMessage(raw)
                        .replaceText(t -> t.matchLiteral("{version}").replacement(plugin.getPluginMeta().getVersion())));
    }

    private void reloadCommand(CommandSender sender) {
        configManager.reloadConfig();
        messageManager.reloadMessages();

        if (plugin.getClearLaggManager() != null) {
            plugin.getClearLaggManager().reloadRules();
        }

        RedstoneListener listener = plugin.getRedstoneListener();
        if (listener != null) {
            listener.refreshCache();
        }

        counterManager.updatePerformanceSnapshot();

        messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getReloadSuccess());
        logManager.logToFile("COMMAND", sender.getName() + " executed reload command", null);
    }

    private void statsCommand(CommandSender sender) {
        String perf = CounterManager.isFolia()
                ? "N/A"
                : String.valueOf(Math.min(100, Math.max(0, (int) Math.round((counterManager.getCachedTps() / 20.0) * 100.0))));

        String raw = messageManager.getMessagesConfig().getCommands().getStats();
        Component msg = messageManager.parseMessage(raw)
                .replaceText(t -> t.matchLiteral("{chunks}").replacement(String.valueOf(counterManager.getChunksMonitored())))
                .replaceText(t -> t.matchLiteral("{blocks}").replacement(String.valueOf(counterManager.getBlocksMonitored())))
                .replaceText(t -> t.matchLiteral("{total_removals}").replacement(String.valueOf(counterManager.getTotalRemovals())))
                .replaceText(t -> t.matchLiteral("{today_removals}").replacement(String.valueOf(counterManager.getRemovalsToday())))
                .replaceText(t -> t.matchLiteral("{performance}").replacement(perf));
        messageManager.sendIfNotBlank(sender, raw, msg);
    }

    private void logsCommand(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("download")) {
            provideLogFile(sender);
        } else {
            String folderPath = logManager.getLogsFolder() != null
                    ? logManager.getLogsFolder().getAbsolutePath()
                    : new File(plugin.getDataFolder(), "logs").getAbsolutePath();
            String raw = messageManager.getMessagesConfig().getCommands().getLogsInfo();
            Component msg = messageManager.parseMessage(raw)
                    .replaceText(t -> t.matchLiteral("{status}").replacement(logManager.isEnabled() ? "Enabled" : "Disabled"))
                    .replaceText(t -> t.matchLiteral("{folder}").replacement(folderPath));
            messageManager.sendIfNotBlank(sender, raw, msg);
        }
    }

    private void provideLogFile(CommandSender sender) {
        File logsFolder = logManager.getLogsFolder();
        if (logsFolder == null || !logsFolder.exists()) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getLogsNoFolder());
            return;
        }

        File[] logFiles = logsFolder.listFiles((dir, name) -> name.startsWith("redstone-logs-") && name.endsWith(".log"));
        if (logFiles == null || logFiles.length == 0) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getLogsEmpty());
            return;
        }

        Arrays.sort(logFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        File latestLog = logFiles[0];

        String fileSize = formatFileSize(latestLog.length());
        String lastModified = DATE_TIME_FMT.format(Instant.ofEpochMilli(latestLog.lastModified()));

        String raw = messageManager.getMessagesConfig().getCommands().getLogsFileInfo();
        Component msg = messageManager.parseMessage(raw)
                .replaceText(t -> t.matchLiteral("{filename}").replacement(latestLog.getName()))
                .replaceText(t -> t.matchLiteral("{size}").replacement(fileSize))
                .replaceText(t -> t.matchLiteral("{modified}").replacement(lastModified))
                .replaceText(t -> t.matchLiteral("{path}").replacement(latestLog.getAbsolutePath()));
        messageManager.sendIfNotBlank(sender, raw, msg);
        logManager.logToFile("COMMAND", sender.getName() + " viewed log file info: " + latestLog.getName(), null);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.isOp() || sender.hasPermission(permission)) {
            return true;
        }
        messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getNoPermission());
        return false;
    }
}