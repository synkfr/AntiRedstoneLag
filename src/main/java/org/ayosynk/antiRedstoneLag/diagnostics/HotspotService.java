package org.ayosynk.antiRedstoneLag.diagnostics;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.engine.RedstoneEngine;
import org.ayosynk.antiRedstoneLag.manager.LogManager;
import org.ayosynk.antiRedstoneLag.util.BlockKeyCodec;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes adjacent-chunk redstone activity clusters, renders paginated reports, and exports hotspot files.
 */
public class HotspotService {
    private static final int HOTSPOT_MAX = 50;
    private static final int HOTSPOT_PAGE = 10;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FILE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(ZoneId.systemDefault());

    public record HotspotGroup(
            String worldName,
            UUID worldId,
            int centerChunkX,
            int centerChunkZ,
            int centerBlockX,
            int centerBlockZ,
            int totalActivity,
            int chunkCount,
            boolean hasLockedChunk
    ) {
    }

    private final AntiRedstoneLag plugin;
    private final RedstoneEngine engine;
    private final MessageManager messageManager;
    private final LogManager logManager;

    public HotspotService(AntiRedstoneLag plugin,
                          RedstoneEngine engine,
                          MessageManager messageManager,
                          LogManager logManager) {
        this.plugin = plugin;
        this.engine = engine;
        this.messageManager = messageManager;
        this.logManager = logManager;
    }

    public List<HotspotGroup> getHotspots(int maxGroups) {
        Map<UUID, Long2IntOpenHashMap> snapshot = engine.snapshotChunkActivity();
        List<HotspotGroup> groups = new ArrayList<>();

        for (Map.Entry<UUID, Long2IntOpenHashMap> worldEntry : snapshot.entrySet()) {
            UUID worldId = worldEntry.getKey();
            Long2IntOpenHashMap chunkMap = worldEntry.getValue();

            World world = Bukkit.getWorld(worldId);
            String worldName = world != null ? world.getName() : worldId.toString();

            LongOpenHashSet visited = new LongOpenHashSet(chunkMap.size() * 2);

            for (long startKey : new LongOpenHashSet(chunkMap.keySet())) {
                if (!visited.add(startKey)) continue;

                ArrayDeque<Long> queue = new ArrayDeque<>();
                List<Long> cluster = new ArrayList<>();
                queue.add(startKey);
                cluster.add(startKey);

                while (!queue.isEmpty()) {
                    long key = queue.poll();
                    int cx = BlockKeyCodec.unpackChunkX(key);
                    int cz = BlockKeyCodec.unpackChunkZ(key);

                    long[] neighbors = {
                            BlockKeyCodec.packChunk(cx + 1, cz), BlockKeyCodec.packChunk(cx - 1, cz),
                            BlockKeyCodec.packChunk(cx, cz + 1), BlockKeyCodec.packChunk(cx, cz - 1)
                    };
                    for (long nb : neighbors) {
                        if (chunkMap.containsKey(nb) && visited.add(nb)) {
                            queue.add(nb);
                            cluster.add(nb);
                        }
                    }
                }

                long weightedSumX = 0;
                long weightedSumZ = 0;
                long totalActivity = 0;
                boolean anyLocked = false;
                for (long key : cluster) {
                    int cx = BlockKeyCodec.unpackChunkX(key);
                    int cz = BlockKeyCodec.unpackChunkZ(key);
                    int activity = chunkMap.get(key);
                    weightedSumX += (long) cx * activity;
                    weightedSumZ += (long) cz * activity;
                    totalActivity += activity;
                    if (!anyLocked && engine.isChunkLocked(worldId, key)) {
                        anyLocked = true;
                    }
                }

                if (totalActivity <= 0) continue;

                int centreChunkX = (int) (weightedSumX / totalActivity);
                int centreChunkZ = (int) (weightedSumZ / totalActivity);
                int centreBlockX = centreChunkX * 16 + 8;
                int centreBlockZ = centreChunkZ * 16 + 8;

                groups.add(new HotspotGroup(
                        worldName, worldId,
                        centreChunkX, centreChunkZ,
                        centreBlockX, centreBlockZ,
                        (int) Math.min(totalActivity, Integer.MAX_VALUE),
                        cluster.size(),
                        anyLocked
                ));
            }
        }

        groups.sort((a, b) -> Integer.compare(b.totalActivity(), a.totalActivity()));
        return groups.size() > maxGroups ? new ArrayList<>(groups.subList(0, maxGroups)) : groups;
    }

    public void sendHotspots(CommandSender sender, int requestedPage) {
        List<HotspotGroup> hotspots = getHotspots(HOTSPOT_MAX);
        if (hotspots.isEmpty()) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getHotspotsNone());
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(hotspots.size() / (double) HOTSPOT_PAGE));
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int start = (page - 1) * HOTSPOT_PAGE;
        int end = Math.min(start + HOTSPOT_PAGE, hotspots.size());

        String headerRaw = messageManager.getMessagesConfig().getCommands().getHotspotsHeader();
        messageManager.sendIfNotBlank(sender, headerRaw, Map.of("count", String.valueOf(hotspots.size())));

        String rowRaw = messageManager.getMessagesConfig().getCommands().getHotspotsRow();
        String hoverRaw = messageManager.getMessagesConfig().getCommands().getHotspotsRowHover();

        for (int i = start; i < end; i++) {
            HotspotGroup g = hotspots.get(i);
            int rank = i + 1;
            String stateTag = g.hasLockedChunk()
                    ? "<color:#E20000>LOCKED"
                    : "<color:#00E262>Active";

            Map<String, String> vars = Map.of(
                    "rank", String.valueOf(rank),
                    "world", g.worldName(),
                    "x", String.valueOf(g.centerBlockX()),
                    "z", String.valueOf(g.centerBlockZ()),
                    "activity", String.valueOf(g.totalActivity()),
                    "chunks", String.valueOf(g.chunkCount()),
                    "state", stateTag
            );

            Component row = messageManager.parseMessage(rowRaw, vars)
                    .clickEvent(ClickEvent.suggestCommand("/tp @s " + g.centerBlockX() + " ~ " + g.centerBlockZ()))
                    .hoverEvent(HoverEvent.showText(messageManager.parseMessage(hoverRaw, vars)));
            sender.sendMessage(row);
        }

        Component nav = Component.empty();
        if (page > 1) {
            nav = nav.append(MM.deserialize("<color:#00E262><click:run_command:'/arl hotspots " + (page - 1) + "'>[Prev]</click>"))
                    .append(Component.text("  "));
        }
        nav = nav.append(MM.deserialize("<color:#A0A0A0>Page <color:#FFFFFF>" + page + "<color:#A0A0A0>/<color:#FFFFFF>" + totalPages));
        if (page < totalPages) {
            nav = nav.append(Component.text("  "))
                    .append(MM.deserialize("<color:#00E262><click:run_command:'/arl hotspots " + (page + 1) + "'>[Next]</click>"));
        }
        nav = nav.append(Component.text("  "))
                .append(MM.deserialize("<color:#00E262><click:run_command:'/arl hotspots export'>[Export]</click>"));
        sender.sendMessage(nav);
    }

    public void exportHotspots(CommandSender sender) {
        List<HotspotGroup> hotspots = getHotspots(HOTSPOT_MAX);
        if (hotspots.isEmpty()) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getHotspotsExportEmpty());
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
            pw.printf("%-4s  %-24s  %12s  %12s  %10s  %7s  %s%n",
                    "Rank", "World", "Centre X", "Centre Z", "Activity", "Chunks", "State");
            pw.println("-".repeat(90));

            for (int i = 0; i < hotspots.size(); i++) {
                HotspotGroup g = hotspots.get(i);
                pw.printf("%-4d  %-24s  %12d  %12d  %10d  %7d  %s%n",
                        i + 1, g.worldName(), g.centerBlockX(), g.centerBlockZ(),
                        g.totalActivity(), g.chunkCount(), g.hasLockedChunk() ? "LOCKED" : "ACTIVE");
            }

            pw.println();
            pw.println("Teleport commands:");
            for (int i = 0; i < hotspots.size(); i++) {
                HotspotGroup g = hotspots.get(i);
                pw.printf("#%-3d  /tp @s %d ~ %d  (world: %s)%n",
                        i + 1, g.centerBlockX(), g.centerBlockZ(), g.worldName());
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write hotspot export: " + e.getMessage());
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getHotspotsExportFailed());
            return;
        }

        logManager.logToFile("HOTSPOT_EXPORT",
                "Exported " + hotspots.size() + " hotspots to " + exportFile.getName(), null);

        String raw = messageManager.getMessagesConfig().getCommands().getHotspotsExported();
        messageManager.sendIfNotBlank(sender, raw, Map.of("path", exportFile.getAbsolutePath()));
    }
}
