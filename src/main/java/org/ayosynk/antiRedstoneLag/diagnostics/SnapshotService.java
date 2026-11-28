package org.ayosynk.antiRedstoneLag.diagnostics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.engine.RedstoneEngine;
import org.ayosynk.antiRedstoneLag.scheduler.TaskHandle;
import org.ayosynk.antiRedstoneLag.storage.StorageService;
import org.ayosynk.antiRedstoneLag.util.BlockKeyCodec;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures, persists, renders, and highlights 3D forensic redstone snapshots.
 */
public class SnapshotService {
    private static final long SNAPSHOT_COOLDOWN_MS = 60_000L;
    private static final int SNAPSHOT_PAGE_SIZE = 8;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter SNAPSHOT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter SNAPSHOT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final RedstoneEngine engine;
    private final StorageService storageService;
    private final File snapshotDir;

    private final Map<String, SnapshotRecord> snapshots = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<UUID, Map<Long, Long>> snapshotCooldowns = new ConcurrentHashMap<>();
    private final AtomicLong lastCooldownPrune = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger snapshotCounter = new AtomicInteger(1);

    public SnapshotService(AntiRedstoneLag plugin,
                           ConfigManager configManager,
                           MessageManager messageManager,
                           RedstoneEngine engine,
                           StorageService storageService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.engine = engine;
        this.storageService = storageService;
        this.snapshotDir = new File(plugin.getDataFolder(), "logs/snapshots");
        loadPersistedSnapshots();
    }

    private void loadPersistedSnapshots() {
        if (!configManager.getPluginConfig().getStorage().isPersistSnapshots()) return;
        int limit = configManager.getPluginConfig().getSnapshot().getMaxSnapshots();
        List<SnapshotRecord> loaded = storageService.loadSnapshots(limit);
        int maxId = 0;
        synchronized (snapshots) {
            snapshots.clear();
            // Iterate oldest to newest so insertion order stays chronological.
            for (int i = loaded.size() - 1; i >= 0; i--) {
                SnapshotRecord rec = loaded.get(i);
                snapshots.put(rec.id.toUpperCase(), rec);
                maxId = Math.max(maxId, parseNumericSuffix(rec.id));
            }
        }
        if (maxId >= snapshotCounter.get()) {
            snapshotCounter.set(maxId + 1);
        }
    }

    private static int parseNumericSuffix(String id) {
        if (id == null || !id.startsWith("SNP-")) return 0;
        try {
            return Integer.parseInt(id.substring(4));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public SnapshotRecord takeSnapshot(Block centerBlock, Material triggerMaterial, int chunkUpdates,
                                       int blockUpdates, UUID culpritUuid, String reason) {
        if (!configManager.getPluginConfig().getSnapshot().isEnabled()) return null;
        if (centerBlock == null || centerBlock.getWorld() == null) return null;

        int centerX = centerBlock.getX();
        int centerY = centerBlock.getY();
        int centerZ = centerBlock.getZ();
        long blockKey = BlockKeyCodec.packBlock(centerX, centerY, centerZ);

        long now = System.currentTimeMillis();
        pruneCooldowns(now);

        World world = centerBlock.getWorld();
        Map<Long, Long> worldCooldowns = snapshotCooldowns.computeIfAbsent(world.getUID(), k -> new ConcurrentHashMap<>());
        Long lastSnap = worldCooldowns.get(blockKey);
        if (lastSnap != null && now - lastSnap < SNAPSHOT_COOLDOWN_MS) {
            return null;
        }
        worldCooldowns.put(blockKey, now);

        int radius = configManager.getPluginConfig().getSnapshot().getRadius();
        String snapshotId = "SNP-" + snapshotCounter.getAndIncrement();

        String culpritName = "Unknown";
        if (culpritUuid != null) {
            Player online = Bukkit.getPlayer(culpritUuid);
            if (online != null) {
                culpritName = online.getName();
            } else {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(culpritUuid);
                if (offline.getName() != null) {
                    culpritName = offline.getName();
                }
            }
        } else {
            Player nearest = findNearestPlayer(world, centerX, centerY, centerZ, 48.0);
            if (nearest != null) {
                culpritUuid = nearest.getUniqueId();
                culpritName = nearest.getName() + " (Nearby)";
            }
        }

        Map<Material, Integer> componentCounts = new EnumMap<>(Material.class);
        List<SnapshotRecord.BlockEntry> blockEntries = new ArrayList<>();

        int minY = Math.max(world.getMinHeight(), centerY - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, centerY + radius);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int cx = x >> 4;
                int cz = z >> 4;
                if (!world.isChunkLoaded(cx, cz)) continue;
                try {
                    if (!Bukkit.isOwnedByCurrentRegion(world, cx, cz)) continue;
                } catch (Throwable ignored) {
                }
                long ck = BlockKeyCodec.packChunk(cx, cz);
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material mat = block.getType();
                    if (configManager.getRedstoneMaterials().contains(mat)) {
                        componentCounts.merge(mat, 1, Integer::sum);
                        UUID owner = engine.getOwner(world.getUID(), ck, BlockKeyCodec.packBlock(x, y, z));
                        blockEntries.add(new SnapshotRecord.BlockEntry(dx, y - centerY, dz, mat, owner));
                    }
                }
            }
        }

        SnapshotRecord snapshot = new SnapshotRecord(
                snapshotId, now, world.getName(), centerX, centerY, centerZ,
                triggerMaterial, chunkUpdates, blockUpdates, culpritUuid, culpritName, reason,
                componentCounts, blockEntries
        );

        synchronized (snapshots) {
            snapshots.put(snapshotId, snapshot);
            int maxSnapshots = configManager.getPluginConfig().getSnapshot().getMaxSnapshots();
            while (snapshots.size() > maxSnapshots) {
                String oldestKey = snapshots.keySet().iterator().next();
                snapshots.remove(oldestKey);
                if (configManager.getPluginConfig().getStorage().isPersistSnapshots()) {
                    storageService.deleteSnapshotAsync(oldestKey);
                }
            }
        }

        if (configManager.getPluginConfig().getStorage().isPersistSnapshots()) {
            storageService.saveSnapshotAsync(snapshot);
        }

        if (configManager.getPluginConfig().getSnapshot().isSaveToDisk()) {
            plugin.getScheduler().runTaskAsynchronously(() -> saveSnapshotToDisk(snapshot));
        }

        if (configManager.isAlertsEnabled()) {
            String raw = messageManager.getMessagesConfig().getCommands().getSnapshotCaptured();
            if (raw != null && !raw.isBlank()) {
                final String finalCulprit = culpritName;
                plugin.getScheduler().runTaskAsynchronously(() -> {
                    Component alert = messageManager.parseMessage(raw, Map.of(
                            "id", snapshotId,
                            "material", triggerMaterial.name(),
                            "x", String.valueOf(centerX),
                            "y", String.valueOf(centerY),
                            "z", String.valueOf(centerZ),
                            "culprit", finalCulprit
                    ));
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.hasPermission("antiredstonelag.snapshot")) {
                            player.sendMessage(alert);
                        }
                    }
                    if (configManager.isLogToConsole()) {
                        Bukkit.getConsoleSender().sendMessage(alert);
                    }
                });
            }
        }

        if (engine.getWebhookService() != null) {
            engine.getWebhookService().sendAlert("SNAPSHOT", "Forensic Snapshot Captured: " + snapshotId,
                    String.format("Location: %d, %d, %d (%s)\nTrigger: %s | Culprit: %s\nUpdates: %d chunk / %d block",
                            centerX, centerY, centerZ, world.getName(), triggerMaterial.name(), culpritName,
                            chunkUpdates, blockUpdates));
        }

        return snapshot;
    }

    public List<SnapshotRecord> getSnapshotsNewestFirst() {
        synchronized (snapshots) {
            List<SnapshotRecord> list = new ArrayList<>(snapshots.values());
            Collections.reverse(list);
            return list;
        }
    }

    public SnapshotRecord getSnapshot(String id) {
        if (id == null) return null;
        synchronized (snapshots) {
            return snapshots.get(id.toUpperCase());
        }
    }

    public boolean deleteSnapshot(String id) {
        if (id == null) return false;
        SnapshotRecord removed;
        synchronized (snapshots) {
            removed = snapshots.remove(id.toUpperCase());
        }
        if (removed != null) {
            if (configManager.getPluginConfig().getStorage().isPersistSnapshots()) {
                storageService.deleteSnapshotAsync(removed.id);
            }
            return true;
        }
        return false;
    }

    public void clearSnapshots() {
        synchronized (snapshots) {
            snapshots.clear();
        }
        snapshotCooldowns.clear();
        if (configManager.getPluginConfig().getStorage().isPersistSnapshots()) {
            storageService.clearSnapshotsAsync();
        }
    }

    public void sendSnapshotList(CommandSender sender, int page) {
        List<SnapshotRecord> list = getSnapshotsNewestFirst();
        if (list.isEmpty()) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getSnapshotEmpty());
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(list.size() / (double) SNAPSHOT_PAGE_SIZE));
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int start = (currentPage - 1) * SNAPSHOT_PAGE_SIZE;
        int end = Math.min(start + SNAPSHOT_PAGE_SIZE, list.size());

        String headerRaw = messageManager.getMessagesConfig().getCommands().getSnapshotListHeader();
        messageManager.sendIfNotBlank(sender, headerRaw, Map.of("count", String.valueOf(list.size())));

        String rowRaw = messageManager.getMessagesConfig().getCommands().getSnapshotListRow();
        for (int i = start; i < end; i++) {
            SnapshotRecord snap = list.get(i);
            Component row = messageManager.parseMessage(rowRaw, Map.of(
                    "id", snap.id,
                    "time", SNAPSHOT_TIME_FORMAT.format(Instant.ofEpochMilli(snap.timestamp)),
                    "world", snap.worldName,
                    "x", String.valueOf(snap.x),
                    "y", String.valueOf(snap.y),
                    "z", String.valueOf(snap.z),
                    "material", snap.triggerMaterial.name(),
                    "culprit", snap.culpritName
            ))
                    .append(Component.text(" "))
                    .append(MM.deserialize("<color:#00E262><click:run_command:'/arl snapshot view " + snap.id + "'>[View]</click>"))
                    .append(Component.text(" "))
                    .append(MM.deserialize("<color:#00E262><click:run_command:'/arl snapshot tp " + snap.id + "'>[TP]</click>"))
                    .append(Component.text(" "))
                    .append(MM.deserialize("<color:#00E262><click:run_command:'/arl snapshot highlight " + snap.id + "'>[Highlight]</click>"));
            sender.sendMessage(row);
        }

        if (totalPages > 1) {
            Component nav = Component.empty();
            if (currentPage > 1) {
                nav = nav.append(MM.deserialize("<color:#00E262><click:run_command:'/arl snapshot list " + (currentPage - 1) + "'>[Prev]</click>")).append(Component.text("  "));
            }
            nav = nav.append(MM.deserialize("<color:#A0A0A0>Page <color:#FFFFFF>" + currentPage + "<color:#A0A0A0>/<color:#FFFFFF>" + totalPages));
            if (currentPage < totalPages) {
                nav = nav.append(Component.text("  ")).append(MM.deserialize("<color:#00E262><click:run_command:'/arl snapshot list " + (currentPage + 1) + "'>[Next]</click>"));
            }
            sender.sendMessage(nav);
        }
    }

    public void sendSnapshotView(CommandSender sender, String id) {
        SnapshotRecord snap = getSnapshot(id);
        if (snap == null) {
            String raw = messageManager.getMessagesConfig().getCommands().getSnapshotNotFound();
            messageManager.sendIfNotBlank(sender, raw, Map.of("id", id));
            return;
        }

        String viewRaw = messageManager.getMessagesConfig().getCommands().getSnapshotView();
        messageManager.sendIfNotBlank(sender, viewRaw, Map.ofEntries(
                Map.entry("id", snap.id),
                Map.entry("date", SNAPSHOT_DATE_FORMAT.format(Instant.ofEpochMilli(snap.timestamp))),
                Map.entry("x", String.valueOf(snap.x)),
                Map.entry("y", String.valueOf(snap.y)),
                Map.entry("z", String.valueOf(snap.z)),
                Map.entry("world", snap.worldName),
                Map.entry("culprit", snap.culpritName),
                Map.entry("uuid", snap.culpritUuid != null ? snap.culpritUuid.toString() : "N/A"),
                Map.entry("material", snap.triggerMaterial.name()),
                Map.entry("chunk_count", String.valueOf(snap.chunkUpdates)),
                Map.entry("block_count", String.valueOf(snap.blockUpdates)),
                Map.entry("reason", snap.reason),
                Map.entry("total", String.valueOf(snap.getTotalComponents()))
        ));

        String compRaw = messageManager.getMessagesConfig().getCommands().getSnapshotViewComponent();
        for (Map.Entry<Material, Integer> entry : snap.componentCounts.entrySet()) {
            messageManager.sendIfNotBlank(sender, compRaw, Map.of(
                    "material", entry.getKey().name(),
                    "count", String.valueOf(entry.getValue())
            ));
        }

        Component actions = MM.deserialize("<color:#00E262><click:run_command:'/arl snapshot tp " + snap.id + "'>[Teleport]</click>")
                .append(Component.text("  "))
                .append(MM.deserialize("<color:#00E262><click:run_command:'/arl snapshot highlight " + snap.id + "'>[Highlight]</click>"))
                .append(Component.text("  "))
                .append(MM.deserialize("<color:#E20000><click:run_command:'/arl snapshot delete " + snap.id + "'>[Delete]</click>"));
        sender.sendMessage(actions);
    }

    public void teleportToSnapshot(CommandSender sender, String id) {
        if (!(sender instanceof Player player)) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getMessages().getPlayerOnly());
            return;
        }

        SnapshotRecord snap = getSnapshot(id);
        if (snap == null) {
            String raw = messageManager.getMessagesConfig().getCommands().getSnapshotNotFound();
            messageManager.sendIfNotBlank(sender, raw, Map.of("id", id));
            return;
        }

        World world = Bukkit.getWorld(snap.worldName);
        if (world == null) {
            String raw = messageManager.getMessagesConfig().getCommands().getSnapshotWorldUnloaded();
            messageManager.sendIfNotBlank(player, raw, Map.of("world", snap.worldName));
            return;
        }

        Location loc = new Location(world, snap.x + 0.5, snap.y + 1.0, snap.z + 0.5);
        player.teleportAsync(loc);
        String raw = messageManager.getMessagesConfig().getCommands().getSnapshotTpSuccess();
        messageManager.sendIfNotBlank(player, raw, Map.of(
                "id", snap.id,
                "x", String.valueOf(snap.x),
                "y", String.valueOf(snap.y),
                "z", String.valueOf(snap.z),
                "world", snap.worldName
        ));
    }

    public void highlightSnapshot(CommandSender sender, String id) {
        if (!(sender instanceof Player player)) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getMessages().getPlayerOnly());
            return;
        }

        SnapshotRecord snap = getSnapshot(id);
        if (snap == null) {
            String raw = messageManager.getMessagesConfig().getCommands().getSnapshotNotFound();
            messageManager.sendIfNotBlank(sender, raw, Map.of("id", id));
            return;
        }

        World world = Bukkit.getWorld(snap.worldName);
        if (world == null) {
            String raw = messageManager.getMessagesConfig().getCommands().getSnapshotWorldUnloaded();
            messageManager.sendIfNotBlank(player, raw, Map.of("world", snap.worldName));
            return;
        }

        String raw = messageManager.getMessagesConfig().getCommands().getSnapshotHighlighted();
        messageManager.sendIfNotBlank(player, raw, Map.of("id", snap.id));

        final long endAt = System.currentTimeMillis() + 15_000L;
        final Particle.DustOptions centerDust = new Particle.DustOptions(Color.fromRGB(226, 0, 0), 1.4f);
        final Particle.DustOptions partDust = new Particle.DustOptions(Color.fromRGB(226, 183, 0), 1.0f);

        final TaskHandle[] ref = new TaskHandle[1];
        ref[0] = plugin.getScheduler().runTaskTimer(() -> {
            if (!player.isOnline() || System.currentTimeMillis() > endAt) {
                if (ref[0] != null) ref[0].cancel();
                return;
            }
            plugin.getScheduler().runTask(player, () -> {
                if (!player.isOnline() || !player.getWorld().equals(world)) return;
                player.spawnParticle(Particle.DUST, snap.x + 0.5, snap.y + 0.5, snap.z + 0.5, 4, 0.25, 0.25, 0.25, centerDust);
                for (SnapshotRecord.BlockEntry be : snap.blockEntries) {
                    player.spawnParticle(Particle.DUST,
                            snap.x + be.relX + 0.5,
                            snap.y + be.relY + 0.5,
                            snap.z + be.relZ + 0.5,
                            1, 0.1, 0.1, 0.1, partDust);
                }
            });
        }, 1L, 10L);
    }

    private Player findNearestPlayer(World world, int x, int y, int z, double maxDistance) {
        Location loc = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        double maxDistSq = maxDistance * maxDistance;
        Player nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        try {
            Collection<Player> nearby = world.getNearbyPlayers(loc, maxDistance, maxDistance, maxDistance);
            for (Player player : nearby) {
                Location pLoc = player.getLocation();
                double dx = pLoc.getX() - loc.getX();
                double dy = pLoc.getY() - loc.getY();
                double dz = pLoc.getZ() - loc.getZ();
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= maxDistSq && distSq < nearestDistSq) {
                    nearest = player;
                    nearestDistSq = distSq;
                }
            }
        } catch (Throwable ignored) {
        }
        return nearest;
    }

    private void saveSnapshotToDisk(SnapshotRecord snap) {
        if (!snapshotDir.exists()) {
            snapshotDir.mkdirs();
        }
        File file = new File(snapshotDir, "snapshot-" + snap.id + ".txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))) {
            pw.println("=================================================");
            pw.println("   AntiRedstoneLag Forensic Snapshot: " + snap.id);
            pw.println("=================================================");
            pw.println("Captured At : " + SNAPSHOT_DATE_FORMAT.format(Instant.ofEpochMilli(snap.timestamp)));
            pw.println("Location    : " + snap.x + ", " + snap.y + ", " + snap.z + " in world '" + snap.worldName + "'");
            pw.println("Culprit     : " + snap.culpritName + " (" + (snap.culpritUuid != null ? snap.culpritUuid : "N/A") + ")");
            pw.println("Trigger     : " + snap.triggerMaterial);
            pw.println("Updates     : " + snap.chunkUpdates + " chunk UPS | " + snap.blockUpdates + " block UPS");
            pw.println("Reason      : " + snap.reason);
            pw.println("Total Redstone Blocks: " + snap.getTotalComponents());
            pw.println();
            pw.println("--- Component Breakdown ---");
            for (Map.Entry<Material, Integer> entry : snap.componentCounts.entrySet()) {
                pw.printf("  - %-24s : %d%n", entry.getKey().name(), entry.getValue());
            }
            pw.println();
            pw.println("--- Block Layout Detail (Relative to center) ---");
            pw.printf("%-8s %-8s %-8s %-24s %s%n", "RelX", "RelY", "RelZ", "Material", "Owner UUID");
            pw.println("---------------------------------------------------------------");
            for (SnapshotRecord.BlockEntry be : snap.blockEntries) {
                pw.printf("%-8d %-8d %-8d %-24s %s%n",
                        be.relX, be.relY, be.relZ, be.material.name(),
                        be.ownerUuid != null ? be.ownerUuid.toString() : "unknown");
            }
            pw.println();
            pw.println("Teleport command: /tp @s " + snap.x + " " + snap.y + " " + snap.z);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save forensic snapshot to disk: " + e.getMessage());
        }
    }

    private void pruneCooldowns(long now) {
        long prev = lastCooldownPrune.get();
        if (now - prev < SNAPSHOT_COOLDOWN_MS || !lastCooldownPrune.compareAndSet(prev, now)) return;
        for (Map<Long, Long> worldCooldowns : snapshotCooldowns.values()) {
            worldCooldowns.values().removeIf(time -> now - time >= SNAPSHOT_COOLDOWN_MS);
        }
    }
}
