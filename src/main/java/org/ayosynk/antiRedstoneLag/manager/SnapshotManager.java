package org.ayosynk.antiRedstoneLag.manager;

import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SnapshotManager {
    private static final long SNAPSHOT_COOLDOWN_MS = 60000L;

    public static class BlockEntry {
        public final int relX, relY, relZ;
        public final Material material;
        public final UUID ownerUuid;

        public BlockEntry(int relX, int relY, int relZ, Material material, UUID ownerUuid) {
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.material = material;
            this.ownerUuid = ownerUuid;
        }
    }

    public static class Snapshot {
        public final String id;
        public final long timestamp;
        public final String worldName;
        public final int x, y, z;
        public final Material triggerMaterial;
        public final int chunkUpdates;
        public final int blockUpdates;
        public final UUID culpritUuid;
        public final String culpritName;
        public final String reason;
        public final Map<Material, Integer> componentCounts;
        public final List<BlockEntry> blockEntries;

        public Snapshot(String id, long timestamp, String worldName, int x, int y, int z,
                        Material triggerMaterial, int chunkUpdates, int blockUpdates,
                        UUID culpritUuid, String culpritName, String reason,
                        Map<Material, Integer> componentCounts, List<BlockEntry> blockEntries) {
            this.id = id;
            this.timestamp = timestamp;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.triggerMaterial = triggerMaterial;
            this.chunkUpdates = chunkUpdates;
            this.blockUpdates = blockUpdates;
            this.culpritUuid = culpritUuid;
            this.culpritName = culpritName;
            this.reason = reason;
            this.componentCounts = componentCounts;
            this.blockEntries = blockEntries;
        }

        public int getTotalComponents() {
            int sum = 0;
            for (int count : componentCounts.values()) {
                sum += count;
            }
            return sum;
        }
    }

    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Map<String, Snapshot> snapshots = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<Long, Long> snapshotCooldowns = new ConcurrentHashMap<>();
    private final AtomicInteger snapshotCounter = new AtomicInteger(1);
    private final File snapshotDir;

    public SnapshotManager(AntiRedstoneLag plugin, ConfigManager configManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.snapshotDir = new File(plugin.getDataFolder(), "logs/snapshots");
    }

    public Snapshot takeSnapshot(Block centerBlock, Material triggerMaterial, int chunkUpdates,
                                 int blockUpdates, UUID culpritUuid, String reason) {
        if (!configManager.getPluginConfig().getSnapshot().isEnabled()) return null;
        if (centerBlock == null || centerBlock.getWorld() == null) return null;

        int centerX = centerBlock.getX();
        int centerY = centerBlock.getY();
        int centerZ = centerBlock.getZ();
        long blockKey = CounterManager.packBlock(centerX, centerY, centerZ);

        long now = System.currentTimeMillis();
        Long lastSnap = snapshotCooldowns.get(blockKey);
        if (lastSnap != null && now - lastSnap < SNAPSHOT_COOLDOWN_MS) {
            return null;
        }
        snapshotCooldowns.put(blockKey, now);

        World world = centerBlock.getWorld();
        int radius = configManager.getPluginConfig().getSnapshot().getRadius();
        String snapshotId = "SNP-" + snapshotCounter.getAndIncrement();

        String culpritName = "Unknown";
        if (culpritUuid != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(culpritUuid);
            if (offlinePlayer.getName() != null) {
                culpritName = offlinePlayer.getName();
            }
        } else {
            Player nearest = findNearestPlayer(world, centerX, centerY, centerZ, 48);
            if (nearest != null) {
                culpritUuid = nearest.getUniqueId();
                culpritName = nearest.getName() + " (Nearby)";
            }
        }

        Map<Material, Integer> componentCounts = new EnumMap<>(Material.class);
        List<BlockEntry> blockEntries = new ArrayList<>();

        int minY = Math.max(world.getMinHeight(), centerY - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, centerY + radius);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = minY; y <= maxY; y++) {
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    Block block = world.getBlockAt(x, y, z);
                    Material mat = block.getType();
                    if (configManager.getRedstoneMaterials().contains(mat)) {
                        componentCounts.merge(mat, 1, Integer::sum);
                        UUID owner = plugin.getRedstoneListener() != null ?
                                plugin.getRedstoneListener().getBlockOwner(world.getUID(), x, y, z) : null;
                        blockEntries.add(new BlockEntry(dx, y - centerY, dz, mat, owner));
                    }
                }
            }
        }

        Snapshot snapshot = new Snapshot(snapshotId, now, world.getName(), centerX, centerY, centerZ,
                triggerMaterial, chunkUpdates, blockUpdates, culpritUuid, culpritName, reason,
                componentCounts, blockEntries);

        synchronized (snapshots) {
            snapshots.put(snapshotId, snapshot);
            int maxSnapshots = configManager.getPluginConfig().getSnapshot().getMaxSnapshots();
            while (snapshots.size() > maxSnapshots) {
                String oldestKey = snapshots.keySet().iterator().next();
                snapshots.remove(oldestKey);
            }
        }

        if (configManager.getPluginConfig().getSnapshot().isSaveToDisk()) {
            final Snapshot snapToSave = snapshot;
            plugin.getScheduler().runTaskAsynchronously(() -> saveSnapshotToDisk(snapToSave));
        }

        final String finalCulprit = culpritName;
        plugin.getScheduler().runTaskAsynchronously(() -> {
            net.kyori.adventure.text.Component alert = messageManager.parseMessage(
                    messageManager.getMessagesConfig().getCommands().getSnapshotCaptured())
                    .replaceText(t -> t.matchLiteral("{id}").replacement(snapshotId))
                    .replaceText(t -> t.matchLiteral("{material}").replacement(triggerMaterial.toString()))
                    .replaceText(t -> t.matchLiteral("{x}").replacement(String.valueOf(centerX)))
                    .replaceText(t -> t.matchLiteral("{y}").replacement(String.valueOf(centerY)))
                    .replaceText(t -> t.matchLiteral("{z}").replacement(String.valueOf(centerZ)))
                    .replaceText(t -> t.matchLiteral("{culprit}").replacement(finalCulprit));

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("antiredstonelag.snapshot")) {
                    player.sendMessage(alert);
                }
            }
            if (configManager.isLogToConsole()) {
                Bukkit.getConsoleSender().sendMessage(alert);
            }
        });

        return snapshot;
    }

    private Player findNearestPlayer(World world, int x, int y, int z, double maxDistance) {
        double maxDistSq = maxDistance * maxDistance;
        Player nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Player player : world.getPlayers()) {
            Location loc = player.getLocation();
            double dx = loc.getX() - x;
            double dy = loc.getY() - y;
            double dz = loc.getZ() - z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq <= maxDistSq && distSq < nearestDistSq) {
                nearest = player;
                nearestDistSq = distSq;
            }
        }
        return nearest;
    }

    private void saveSnapshotToDisk(Snapshot snap) {
        if (!snapshotDir.exists()) {
            snapshotDir.mkdirs();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        File file = new File(snapshotDir, "snapshot-" + snap.id + ".txt");

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("=================================================");
            pw.println("   AntiRedstoneLag Forensic Snapshot: " + snap.id);
            pw.println("=================================================");
            pw.println("Captured At : " + sdf.format(new Date(snap.timestamp)));
            pw.println("Location    : " + snap.x + ", " + snap.y + ", " + snap.z + " in world '" + snap.worldName + "'");
            pw.println("Culprit     : " + snap.culpritName + " (" + (snap.culpritUuid != null ? snap.culpritUuid : "N/A") + ")");
            pw.println("Trigger     : " + snap.triggerMaterial);
            pw.println("Updates     : " + snap.chunkUpdates + " chunk UPS | " + snap.blockUpdates + " block UPS");
            pw.println("Reason      : " + snap.reason);
            pw.println("Total Redstone Blocks: " + snap.getTotalComponents());
            pw.println();
            pw.println("--- Component Breakdown ---");
            for (Map.Entry<Material, Integer> entry : snap.componentCounts.entrySet()) {
                pw.printf("  • %-24s : %d%n", entry.getKey().name(), entry.getValue());
            }
            pw.println();
            pw.println("--- Block Layout Detail (Relative to center) ---");
            pw.printf("%-8s %-8s %-8s %-24s %s%n", "RelX", "RelY", "RelZ", "Material", "Owner UUID");
            pw.println("---------------------------------------------------------------");
            for (BlockEntry be : snap.blockEntries) {
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

    public List<Snapshot> getSnapshots() {
        synchronized (snapshots) {
            return new ArrayList<>(snapshots.values());
        }
    }

    public Snapshot getSnapshot(String id) {
        synchronized (snapshots) {
            return snapshots.get(id.toUpperCase());
        }
    }

    public void clearSnapshots() {
        synchronized (snapshots) {
            snapshots.clear();
        }
        snapshotCooldowns.clear();
    }
}
