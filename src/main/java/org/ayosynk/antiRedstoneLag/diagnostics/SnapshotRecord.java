package org.ayosynk.antiRedstoneLag.diagnostics;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable forensic lag snapshot record persisted across restarts.
 */
public class SnapshotRecord {

    public static class BlockEntry {
        public final int relX;
        public final int relY;
        public final int relZ;
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

    public final String id;
    public final long timestamp;
    public final String worldName;
    public final int x;
    public final int y;
    public final int z;
    public final Material triggerMaterial;
    public final int chunkUpdates;
    public final int blockUpdates;
    public final UUID culpritUuid;
    public final String culpritName;
    public final String reason;
    public final Map<Material, Integer> componentCounts;
    public final List<BlockEntry> blockEntries;

    public SnapshotRecord(String id, long timestamp, String worldName, int x, int y, int z,
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
        this.componentCounts = componentCounts != null ? Map.copyOf(componentCounts) : Map.of();
        this.blockEntries = blockEntries != null ? List.copyOf(blockEntries) : List.of();
    }

    public int getTotalComponents() {
        int sum = 0;
        for (int count : componentCounts.values()) {
            sum += count;
        }
        return sum;
    }
}
