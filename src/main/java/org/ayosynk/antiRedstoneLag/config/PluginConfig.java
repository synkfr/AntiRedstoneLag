package org.ayosynk.antiRedstoneLag.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.annotation.Header;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Header({
        "AntiRedstoneLag v2 Configuration",
        "Redstone lag prevention, forensic diagnostics, Staff GUI, and ClearLagg"
})
public class PluginConfig extends OkaeriConfig {

    public enum RemovalAction {
        FREEZE,
        NOTIFY,
        DISABLE,
        DROP,
        REMOVE
    }

    public enum BroadcastMode {
        CHAT,
        ACTION_BAR,
        TITLE,
        SUBTITLE,
        ALL,
        NONE
    }

    public enum StorageBackend {
        AUTO,
        SQLITE,
        JSON
    }

    public enum MinecartClusterAction {
        FREEZE_OR_REMOVE,
        REMOVE_EXCESS,
        NOTIFY
    }

    @CustomKey("config-version")
    @Comment("Internal configuration version - do not modify")
    private int configVersion = 2;

    @CustomKey("chunk-threshold")
    @Comment("Maximum redstone updates per chunk per reset interval")
    private int chunkThreshold = 800;

    @CustomKey("block-threshold")
    @Comment("Updates per block before being considered a lag machine")
    private int blockThreshold = 30;

    @CustomKey("reset-interval-ticks")
    @Comment("Counter reset interval in ticks (20 ticks = 1 second)")
    private int resetIntervalTicks = 20;

    @CustomKey("debug")
    @Comment("Enable debug mode for verbose logging")
    private boolean debug = false;

    @CustomKey("redstone-enabled")
    @Comment("Enable redstone protection and monitoring (toggleable via /arl redstone on|off|notify)")
    private boolean redstoneEnabled = true;

    @CustomKey("removal-action")
    @Comment({
            "Action to take when redstone exceeds threshold",
            "Options: FREEZE (pause clock temporarily), NOTIFY (alert staff only), DISABLE (cancel signal temporarily), DROP (break and drop item), REMOVE (set to air)"
    })
    private RemovalAction removalAction = RemovalAction.FREEZE;

    @CustomKey("storage")
    @Comment("Persistent storage for block owners, forensic snapshots, and statistics")
    private StorageConfig storage = new StorageConfig();

    @CustomKey("clearlagg")
    @Comment("High-performance ground item & entity cleaner with countdown broadcasts")
    private ClearLaggConfig clearlagg = new ClearLaggConfig();

    @CustomKey("snapshot")
    @Comment("Forensic snapshot system - records 3D structure and culprit metadata when lag machines are detected")
    private SnapshotConfig snapshot = new SnapshotConfig();

    @CustomKey("freeze")
    @Comment("Non-destructive freezing system - temporarily pauses clocks before escalating")
    private FreezeConfig freeze = new FreezeConfig();

    @CustomKey("adaptive")
    @Comment("Adaptive performance scaling - adjusts thresholds dynamically based on MSPT and TPS")
    private AdaptiveConfig adaptive = new AdaptiveConfig();

    @CustomKey("fingerprint")
    @Comment("Multi-pattern clock fingerprinting - distinguishes periodic clocks from bursty sorting systems")
    private ClockFingerprintConfig fingerprint = new ClockFingerprintConfig();

    @CustomKey("detectors")
    @Comment("Specialized detectors for pistons, hoppers, crafters, and entity collisions")
    private DetectorsConfig detectors = new DetectorsConfig();

    @CustomKey("proximity")
    @Comment("Player proximity throttling - enforces stricter limits on unattended redstone")
    private ProximityConfig proximity = new ProximityConfig();

    @CustomKey("lockdown")
    @Comment("Lockdown system (EMP) - temporarily locks a chunk when extreme lag persists")
    private LockdownConfig lockdown = new LockdownConfig();

    @CustomKey("warning")
    @Comment("Warning system - warn block owners before throttling or removing their redstone")
    private WarningConfig warning = new WarningConfig();

    @CustomKey("inspector")
    @Comment("Visual in-world redstone inspector (/arl inspect)")
    private InspectorConfig inspector = new InspectorConfig();

    @CustomKey("gui")
    @Comment("Interactive Staff Inventory GUI (/arl gui)")
    private GuiConfig gui = new GuiConfig();

    @CustomKey("enabled-worlds")
    @Comment("Worlds where the plugin is active (use * for all worlds)")
    private List<String> enabledWorlds = new ArrayList<>(List.of("*"));

    @CustomKey("worlds")
    @Comment({
            "Optional per-world threshold and action overrides",
            "Example:",
            "  world_nether:",
            "    chunk-threshold: 350",
            "    block-threshold: 12",
            "    removal-action: FREEZE",
            "    clearlagg-enabled: true"
    })
    private Map<String, WorldOverrideConfig> worlds = new LinkedHashMap<>();

    @CustomKey("whitelist")
    @Comment("Whitelist mode - only monitor specific chunks when enabled")
    private WhitelistConfig whitelist = new WhitelistConfig();

    @CustomKey("alerts")
    @Comment("Alert and TPS lag notification settings")
    private AlertsConfig alerts = new AlertsConfig();

    @CustomKey("logging")
    @Comment("Async rotating file logging system")
    private LoggingConfig logging = new LoggingConfig();

    @CustomKey("integrations")
    @Comment("External integrations (PlaceholderAPI & Discord Webhooks)")
    private IntegrationsConfig integrations = new IntegrationsConfig();

    @CustomKey("redstone-components")
    @Comment("Redstone components to monitor")
    private List<Material> redstoneComponents = new ArrayList<>(Arrays.asList(
            Material.REDSTONE_WIRE,
            Material.REPEATER,
            Material.COMPARATOR,
            Material.OBSERVER,
            Material.PISTON,
            Material.STICKY_PISTON,
            Material.REDSTONE_TORCH,
            Material.REDSTONE_WALL_TORCH,
            Material.LEVER,
            Material.DAYLIGHT_DETECTOR,
            Material.TARGET,
            Material.TRAPPED_CHEST,
            Material.DROPPER,
            Material.DISPENSER,
            Material.HOPPER,
            Material.CRAFTER,
            Material.COPPER_BULB,
            Material.EXPOSED_COPPER_BULB,
            Material.WEATHERED_COPPER_BULB,
            Material.OXIDIZED_COPPER_BULB,
            Material.WAXED_COPPER_BULB,
            Material.WAXED_EXPOSED_COPPER_BULB,
            Material.WAXED_WEATHERED_COPPER_BULB,
            Material.WAXED_OXIDIZED_COPPER_BULB,
            Material.SCULK_SENSOR,
            Material.CALIBRATED_SCULK_SENSOR,
            Material.CHISELED_BOOKSHELF
    ));

    public static class StorageConfig extends OkaeriConfig {
        @CustomKey("backend")
        @Comment("Storage backend: AUTO (SQLite with JSON fallback), SQLITE, or JSON")
        private StorageBackend backend = StorageBackend.AUTO;

        @CustomKey("sqlite-file")
        private String sqliteFile = "data/antiredstonelag.db";

        @CustomKey("persist-owners")
        private boolean persistOwners = true;

        @CustomKey("persist-snapshots")
        private boolean persistSnapshots = true;

        @CustomKey("owner-retention-days")
        private int ownerRetentionDays = 30;

        public StorageBackend getBackend() {
            return backend != null ? backend : StorageBackend.AUTO;
        }

        public String getSqliteFile() {
            return sqliteFile != null && !sqliteFile.isBlank() ? sqliteFile : "data/antiredstonelag.db";
        }

        public boolean isPersistOwners() {
            return persistOwners;
        }

        public boolean isPersistSnapshots() {
            return persistSnapshots;
        }

        public int getOwnerRetentionDays() {
            return Math.max(1, ownerRetentionDays);
        }
    }

    public static class ClearLaggConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("interval-seconds")
        @Comment("Automatic clear interval in seconds (300 = 5 minutes, 0 = disabled)")
        private int intervalSeconds = 300;

        @CustomKey("countdown-seconds")
        @Comment("Seconds before clear to broadcast countdown warnings")
        private List<Integer> countdownSeconds = new ArrayList<>(Arrays.asList(60, 30, 10, 5, 4, 3, 2, 1));

        @CustomKey("broadcast-mode")
        @Comment("Broadcast display type: CHAT, ACTION_BAR, TITLE, SUBTITLE, ALL, NONE")
        private BroadcastMode broadcastMode = BroadcastMode.CHAT;

        @CustomKey("clear-ground-items")
        private boolean clearGroundItems = true;

        @CustomKey("min-item-age-seconds")
        @Comment("Protect recently dropped items younger than this many seconds (0 = disabled)")
        private int minItemAgeSeconds = 30;

        @CustomKey("merge-items")
        @Comment("Merge nearby identical ground item stacks before clearing")
        private boolean mergeItems = false;

        @CustomKey("chunk-entity-caps")
        private ChunkEntityCapsConfig chunkEntityCaps = new ChunkEntityCapsConfig();

        @CustomKey("remove-entities")
        @Comment("Entities to remove during clear (supports conditions: onGround, !isMounted, !hasName, !isTamed, !isLeashed, liveTime>100)")
        private List<String> removeEntities = new ArrayList<>(Arrays.asList(
                "Arrow onGround",
                "Spectral_Arrow onGround",
                "Trident onGround",
                "Snowball",
                "Egg",
                "Ender_Pearl",
                "Experience_Bottle",
                "Fireball",
                "Small_Fireball",
                "Dragon_Fireball",
                "Wither_Skull",
                "Shulker_Bullet",
                "Tnt_Minecart",
                "Tnt liveTime>100"
        ));

        @CustomKey("entity-whitelist")
        @Comment("Entities that are 100% immune from clearing (SMP safe)")
        private List<String> entityWhitelist = new ArrayList<>(Arrays.asList(
                "Player",
                "Villager",
                "Iron_Golem",
                "Allay",
                "Armor_Stand",
                "Item_Frame",
                "Glow_Item_Frame",
                "Painting",
                "Leash_Knot",
                "Interaction",
                "Text_Display",
                "Block_Display",
                "Item_Display",
                "Hopper_Minecart",
                "Chest_Minecart",
                "Furnace_Minecart",
                "* hasName",
                "* isTamed",
                "* isLeashed",
                "* isMounted"
        ));

        @CustomKey("item-blacklist")
        @Comment("Items that will NEVER be cleared when dropped on ground")
        private List<Material> itemBlacklist = new ArrayList<>(Arrays.asList(
                Material.NETHER_STAR,
                Material.BEACON,
                Material.TOTEM_OF_UNDYING,
                Material.ELYTRA,
                Material.DIAMOND,
                Material.DIAMOND_BLOCK,
                Material.NETHERITE_INGOT,
                Material.NETHERITE_BLOCK,
                Material.NETHERITE_SWORD,
                Material.NETHERITE_PICKAXE,
                Material.NETHERITE_AXE,
                Material.NETHERITE_SHOVEL,
                Material.NETHERITE_HOE,
                Material.NETHERITE_HELMET,
                Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS,
                Material.NETHERITE_BOOTS
        ));

        @CustomKey("tps-emergency-threshold")
        @Comment("Emergency auto-clear when server TPS drops below this value (0.0 to disable)")
        private double tpsEmergencyThreshold = 0.0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getIntervalSeconds() {
            return Math.max(0, intervalSeconds);
        }

        public void setIntervalSeconds(int intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }

        public List<Integer> getCountdownSeconds() {
            return countdownSeconds;
        }

        public void setCountdownSeconds(List<Integer> countdownSeconds) {
            this.countdownSeconds = countdownSeconds;
        }

        public BroadcastMode getBroadcastMode() {
            return broadcastMode != null ? broadcastMode : BroadcastMode.CHAT;
        }

        public void setBroadcastMode(BroadcastMode broadcastMode) {
            this.broadcastMode = broadcastMode;
        }

        public boolean isClearGroundItems() {
            return clearGroundItems;
        }

        public void setClearGroundItems(boolean clearGroundItems) {
            this.clearGroundItems = clearGroundItems;
        }

        public int getMinItemAgeSeconds() {
            return Math.max(0, minItemAgeSeconds);
        }

        public boolean isMergeItems() {
            return mergeItems;
        }

        public ChunkEntityCapsConfig getChunkEntityCaps() {
            return chunkEntityCaps;
        }

        public List<String> getRemoveEntities() {
            return removeEntities;
        }

        public void setRemoveEntities(List<String> removeEntities) {
            this.removeEntities = removeEntities;
        }

        public List<String> getEntityWhitelist() {
            return entityWhitelist;
        }

        public void setEntityWhitelist(List<String> entityWhitelist) {
            this.entityWhitelist = entityWhitelist;
        }

        public List<Material> getItemBlacklist() {
            return itemBlacklist;
        }

        public void setItemBlacklist(List<Material> itemBlacklist) {
            this.itemBlacklist = itemBlacklist;
        }

        public double getTpsEmergencyThreshold() {
            return tpsEmergencyThreshold;
        }

        public void setTpsEmergencyThreshold(double tpsEmergencyThreshold) {
            this.tpsEmergencyThreshold = tpsEmergencyThreshold;
        }
    }

    public static class ChunkEntityCapsConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = false;

        @CustomKey("max-mobs-per-chunk")
        private int maxMobsPerChunk = 40;

        @CustomKey("max-items-per-chunk")
        private int maxItemsPerChunk = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public int getMaxMobsPerChunk() {
            return Math.max(1, maxMobsPerChunk);
        }

        public int getMaxItemsPerChunk() {
            return Math.max(1, maxItemsPerChunk);
        }
    }

    public static class SnapshotConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("radius")
        private int radius = 3;

        @CustomKey("max-snapshots")
        private int maxSnapshots = 25;

        @CustomKey("save-to-disk")
        private boolean saveToDisk = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRadius() {
            return Math.max(1, Math.min(10, radius));
        }

        public void setRadius(int radius) {
            this.radius = radius;
        }

        public int getMaxSnapshots() {
            return Math.max(5, maxSnapshots);
        }

        public void setMaxSnapshots(int maxSnapshots) {
            this.maxSnapshots = maxSnapshots;
        }

        public boolean isSaveToDisk() {
            return saveToDisk;
        }

        public void setSaveToDisk(boolean saveToDisk) {
            this.saveToDisk = saveToDisk;
        }
    }

    public static class FreezeConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("duration-seconds")
        private int durationSeconds = 15;

        @CustomKey("max-freeze-attempts")
        private int maxFreezeAttempts = 3;

        @CustomKey("notify-on-resume")
        private boolean notifyOnResume = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDurationSeconds() {
            return Math.max(1, durationSeconds);
        }

        public void setDurationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        public int getMaxFreezeAttempts() {
            return Math.max(1, maxFreezeAttempts);
        }

        public void setMaxFreezeAttempts(int maxFreezeAttempts) {
            this.maxFreezeAttempts = maxFreezeAttempts;
        }

        public boolean isNotifyOnResume() {
            return notifyOnResume;
        }
    }

    public static class AdaptiveConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("target-mspt")
        private double targetMspt = 40.0;

        @CustomKey("min-tps")
        private double minTps = 18.0;

        @CustomKey("healthy-headroom-multiplier")
        private double healthyHeadroomMultiplier = 1.5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getTargetMspt() {
            return Math.max(5.0, targetMspt);
        }

        public void setTargetMspt(double targetMspt) {
            this.targetMspt = targetMspt;
        }

        public double getMinTps() {
            return Math.max(1.0, Math.min(20.0, minTps));
        }

        public void setMinTps(double minTps) {
            this.minTps = minTps;
        }

        public double getHealthyHeadroomMultiplier() {
            return Math.max(1.0, healthyHeadroomMultiplier);
        }

        public void setHealthyHeadroomMultiplier(double healthyHeadroomMultiplier) {
            this.healthyHeadroomMultiplier = healthyHeadroomMultiplier;
        }
    }

    public static class ClockFingerprintConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("clock-strictness")
        private double clockStrictness = 0.85;

        @CustomKey("history-size")
        private int historySize = 8;

        @CustomKey("max-interval-ms")
        private long maxIntervalMs = 500L;

        @CustomKey("variance-tolerance-ms")
        private long varianceToleranceMs = 45L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getClockStrictness() {
            return Math.min(1.0, Math.max(0.1, clockStrictness));
        }

        public void setClockStrictness(double clockStrictness) {
            this.clockStrictness = clockStrictness;
        }

        public int getHistorySize() {
            return Math.max(4, Math.min(32, historySize));
        }

        public long getMaxIntervalMs() {
            return Math.max(100L, maxIntervalMs);
        }

        public long getVarianceToleranceMs() {
            return Math.max(10L, varianceToleranceMs);
        }
    }

    public static class DetectorsConfig extends OkaeriConfig {
        @CustomKey("piston-loop-threshold")
        @Comment("Maximum piston extend cycles per block per reset interval")
        private int pistonLoopThreshold = 16;

        @CustomKey("hopper-limiter")
        private HopperLimiterConfig hopperLimiter = new HopperLimiterConfig();

        @CustomKey("crafter-limiter")
        private CrafterLimiterConfig crafterLimiter = new CrafterLimiterConfig();

        @CustomKey("minecart-cluster")
        private MinecartClusterConfig minecartCluster = new MinecartClusterConfig();

        public int getPistonLoopThreshold() {
            return Math.max(2, pistonLoopThreshold);
        }

        public HopperLimiterConfig getHopperLimiter() {
            return hopperLimiter;
        }

        public CrafterLimiterConfig getCrafterLimiter() {
            return crafterLimiter;
        }

        public MinecartClusterConfig getMinecartCluster() {
            return minecartCluster;
        }
    }

    public static class HopperLimiterConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = false;

        @CustomKey("max-transfers-per-interval")
        private int maxTransfersPerInterval = 64;

        public boolean isEnabled() {
            return enabled;
        }

        public int getMaxTransfersPerInterval() {
            return Math.max(4, maxTransfersPerInterval);
        }
    }

    public static class CrafterLimiterConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("max-crafts-per-interval")
        private int maxCraftsPerInterval = 40;

        public boolean isEnabled() {
            return enabled;
        }

        public int getMaxCraftsPerInterval() {
            return Math.max(2, maxCraftsPerInterval);
        }
    }

    public static class MinecartClusterConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("max-minecarts-per-block")
        private int maxMinecartsPerBlock = 16;

        @CustomKey("action")
        private MinecartClusterAction action = MinecartClusterAction.REMOVE_EXCESS;

        public boolean isEnabled() {
            return enabled;
        }

        public int getMaxMinecartsPerBlock() {
            return Math.max(2, maxMinecartsPerBlock);
        }

        public MinecartClusterAction getAction() {
            return action != null ? action : MinecartClusterAction.REMOVE_EXCESS;
        }
    }

    public static class ProximityConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("player-radius")
        private int playerRadius = 96;

        @CustomKey("unattended-strict-multiplier")
        private double unattendedStrictMultiplier = 0.8;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPlayerRadius() {
            return Math.max(16, playerRadius);
        }

        public void setPlayerRadius(int playerRadius) {
            this.playerRadius = playerRadius;
        }

        public double getUnattendedStrictMultiplier() {
            return Math.min(1.0, Math.max(0.1, unattendedStrictMultiplier));
        }

        public void setUnattendedStrictMultiplier(double unattendedStrictMultiplier) {
            this.unattendedStrictMultiplier = unattendedStrictMultiplier;
        }
    }

    public static class LockdownConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("duration-seconds")
        private int durationSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDurationSeconds() {
            return Math.max(1, durationSeconds);
        }

        public void setDurationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
        }
    }

    public static class WarningConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("threshold-percent")
        @Comment("Percentage of threshold at which to warn (e.g., 80 = warn at 80% of threshold)")
        private int thresholdPercent = 80;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getThresholdPercent() {
            return Math.max(1, Math.min(100, thresholdPercent));
        }

        public void setThresholdPercent(int thresholdPercent) {
            this.thresholdPercent = thresholdPercent;
        }
    }

    public static class InspectorConfig extends OkaeriConfig {
        @CustomKey("default-duration-seconds")
        private int defaultDurationSeconds = 30;

        @CustomKey("max-duration-seconds")
        private int maxDurationSeconds = 300;

        @CustomKey("show-block-particles")
        private boolean showBlockParticles = true;

        @CustomKey("show-chunk-borders")
        private boolean showChunkBorders = true;

        @CustomKey("show-actionbar")
        private boolean showActionbar = true;

        public int getDefaultDurationSeconds() {
            return Math.max(5, defaultDurationSeconds);
        }

        public int getMaxDurationSeconds() {
            return Math.max(getDefaultDurationSeconds(), maxDurationSeconds);
        }

        public boolean isShowBlockParticles() {
            return showBlockParticles;
        }

        public boolean isShowChunkBorders() {
            return showChunkBorders;
        }

        public boolean isShowActionbar() {
            return showActionbar;
        }
    }

    public static class GuiConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("refresh-ticks")
        private int refreshTicks = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public int getRefreshTicks() {
            return Math.max(5, refreshTicks);
        }
    }

    public static class WorldOverrideConfig extends OkaeriConfig {
        @CustomKey("chunk-threshold")
        private Integer chunkThreshold;

        @CustomKey("block-threshold")
        private Integer blockThreshold;

        @CustomKey("removal-action")
        private RemovalAction removalAction;

        @CustomKey("clearlagg-enabled")
        private Boolean clearlaggEnabled;

        public Integer getChunkThreshold() {
            return chunkThreshold;
        }

        public Integer getBlockThreshold() {
            return blockThreshold;
        }

        public RemovalAction getRemovalAction() {
            return removalAction;
        }

        public Boolean getClearlaggEnabled() {
            return clearlaggEnabled;
        }
    }

    public static class WhitelistConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = false;

        @CustomKey("chunks")
        @Comment("List chunks as \"world:chunkX:chunkZ\" (e.g., \"world:10:15\")")
        private List<String> chunks = new ArrayList<>(List.of("world:0:0"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getChunks() {
            return chunks;
        }

        public void setChunks(List<String> chunks) {
            this.chunks = chunks;
        }
    }

    public static class AlertsConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("log-to-console")
        private boolean logToConsole = true;

        @CustomKey("notify-on-tps-lag")
        private boolean notifyOnTpsLag = true;

        @CustomKey("only-when-lagging")
        private boolean onlyWhenLagging = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLogToConsole() {
            return logToConsole;
        }

        public void setLogToConsole(boolean logToConsole) {
            this.logToConsole = logToConsole;
        }

        public boolean isNotifyOnTpsLag() {
            return notifyOnTpsLag;
        }

        public void setNotifyOnTpsLag(boolean notifyOnTpsLag) {
            this.notifyOnTpsLag = notifyOnTpsLag;
        }

        public boolean isOnlyWhenLagging() {
            return onlyWhenLagging;
        }

        public void setOnlyWhenLagging(boolean onlyWhenLagging) {
            this.onlyWhenLagging = onlyWhenLagging;
        }
    }

    public static class LoggingConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("console-mirror")
        private boolean consoleMirror = false;

        @CustomKey("max-files")
        private int maxFiles = 10;

        @CustomKey("max-size-mb")
        private long maxSizeMb = 10;

        @CustomKey("performance-stats")
        private boolean performanceStats = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isConsoleMirror() {
            return consoleMirror;
        }

        public void setConsoleMirror(boolean consoleMirror) {
            this.consoleMirror = consoleMirror;
        }

        public int getMaxFiles() {
            return Math.max(1, maxFiles);
        }

        public void setMaxFiles(int maxFiles) {
            this.maxFiles = maxFiles;
        }

        public long getMaxSizeMb() {
            return Math.max(1, maxSizeMb);
        }

        public void setMaxSizeMb(long maxSizeMb) {
            this.maxSizeMb = maxSizeMb;
        }

        public boolean isPerformanceStats() {
            return performanceStats;
        }

        public void setPerformanceStats(boolean performanceStats) {
            this.performanceStats = performanceStats;
        }
    }

    public static class IntegrationsConfig extends OkaeriConfig {
        @CustomKey("placeholderapi")
        private PlaceholderApiConfig placeholderApi = new PlaceholderApiConfig();

        @CustomKey("discord")
        private DiscordWebhookConfig discord = new DiscordWebhookConfig();

        public PlaceholderApiConfig getPlaceholderApi() {
            return placeholderApi;
        }

        public DiscordWebhookConfig getDiscord() {
            return discord;
        }
    }

    public static class PlaceholderApiConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }
    }

    public static class DiscordWebhookConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = false;

        @CustomKey("webhook-url")
        private String webhookUrl = "";

        @CustomKey("cooldown-seconds")
        private int cooldownSeconds = 30;

        @CustomKey("events")
        private List<String> events = new ArrayList<>(Arrays.asList("LOCKDOWN", "TPS_LAG", "REMOVAL", "SNAPSHOT"));

        public boolean isEnabled() {
            return enabled;
        }

        public String getWebhookUrl() {
            return webhookUrl != null ? webhookUrl : "";
        }

        public int getCooldownSeconds() {
            return Math.max(1, cooldownSeconds);
        }

        public List<String> getEvents() {
            return events;
        }
    }

    public int getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(int configVersion) {
        this.configVersion = configVersion;
    }

    public int getChunkThreshold() {
        return Math.max(1, chunkThreshold);
    }

    public void setChunkThreshold(int chunkThreshold) {
        this.chunkThreshold = chunkThreshold;
    }

    public int getBlockThreshold() {
        return Math.max(1, blockThreshold);
    }

    public void setBlockThreshold(int blockThreshold) {
        this.blockThreshold = blockThreshold;
    }

    public int getResetIntervalTicks() {
        return Math.max(1, resetIntervalTicks);
    }

    public void setResetIntervalTicks(int resetIntervalTicks) {
        this.resetIntervalTicks = resetIntervalTicks;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isRedstoneEnabled() {
        return redstoneEnabled;
    }

    public void setRedstoneEnabled(boolean redstoneEnabled) {
        this.redstoneEnabled = redstoneEnabled;
    }

    public RemovalAction getRemovalAction() {
        return removalAction != null ? removalAction : RemovalAction.FREEZE;
    }

    public void setRemovalAction(RemovalAction removalAction) {
        this.removalAction = removalAction != null ? removalAction : RemovalAction.FREEZE;
    }

    public StorageConfig getStorage() {
        return storage;
    }

    public ClearLaggConfig getClearlagg() {
        return clearlagg;
    }

    public SnapshotConfig getSnapshot() {
        return snapshot;
    }

    public FreezeConfig getFreeze() {
        return freeze;
    }

    public AdaptiveConfig getAdaptive() {
        return adaptive;
    }

    public ClockFingerprintConfig getFingerprint() {
        return fingerprint;
    }

    public DetectorsConfig getDetectors() {
        return detectors;
    }

    public ProximityConfig getProximity() {
        return proximity;
    }

    public LockdownConfig getLockdown() {
        return lockdown;
    }

    public WarningConfig getWarning() {
        return warning;
    }

    public InspectorConfig getInspector() {
        return inspector;
    }

    public GuiConfig getGui() {
        return gui;
    }

    public List<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public void setEnabledWorlds(List<String> enabledWorlds) {
        this.enabledWorlds = enabledWorlds;
    }

    public Map<String, WorldOverrideConfig> getWorlds() {
        return worlds;
    }

    public WhitelistConfig getWhitelist() {
        return whitelist;
    }

    public AlertsConfig getAlerts() {
        return alerts;
    }

    public LoggingConfig getLogging() {
        return logging;
    }

    public IntegrationsConfig getIntegrations() {
        return integrations;
    }

    public List<Material> getRedstoneComponents() {
        return redstoneComponents;
    }

    public void setRedstoneComponents(List<Material> redstoneComponents) {
        this.redstoneComponents = redstoneComponents;
    }
}
