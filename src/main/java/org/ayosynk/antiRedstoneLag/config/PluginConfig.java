package org.ayosynk.antiRedstoneLag.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.annotation.Header;
import eu.okaeri.configs.annotation.Names;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.NameModifier;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;

@Header({
    "AntiRedstoneLag Configuration",
    "Advanced redstone lag prevention system"
})
@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class PluginConfig extends OkaeriConfig {

    public enum RemovalAction {
        FREEZE,
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

    @CustomKey("config-version")
    @Comment("Internal configuration version - do not modify")
    private String configVersion = "26.2";

    @CustomKey("chunk-threshold")
    @Comment("Maximum redstone updates per chunk per reset interval")
    private int chunkThreshold = 500;

    @CustomKey("block-threshold")
    @Comment("Updates per block before being considered a lag machine")
    private int blockThreshold = 15;

    @CustomKey("reset-interval-ticks")
    @Comment("Counter reset interval in ticks (20 ticks = 1 second)")
    private int resetIntervalTicks = 20;

    @CustomKey("debug")
    @Comment("Enable debug mode for verbose logging")
    private boolean debug = false;

    @CustomKey("removal-action")
    @Comment({
        "Action to take when redstone exceeds threshold",
        "Options: FREEZE (temporarily pause clock), DISABLE (cancel signal), DROP (break and drop item), REMOVE (set to air)"
    })
    private RemovalAction removalAction = RemovalAction.FREEZE;

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
    @Comment("Clock fingerprinting - distinguishes periodic clocks from bursty sorting systems")
    private ClockFingerprintConfig fingerprint = new ClockFingerprintConfig();

    @CustomKey("proximity")
    @Comment("Player proximity throttling - enforces stricter limits on unattended redstone")
    private ProximityConfig proximity = new ProximityConfig();

    @CustomKey("lockdown")
    @Comment({
        "Lockdown system (EMP) - Prevents players from repeatedly triggering lag machines",
        "Duration in seconds to lock down the chunk when the threshold is exceeded"
    })
    private LockdownConfig lockdown = new LockdownConfig();

    @CustomKey("warning")
    @Comment("Warning system - warn players before removing their redstone")
    private WarningConfig warning = new WarningConfig();

    @CustomKey("enabled-worlds")
    @Comment("Worlds where the plugin is active (use * for all worlds)")
    private List<String> enabledWorlds = List.of("*");

    @CustomKey("whitelist")
    @Comment({
        "Whitelist mode - only monitor specific chunks instead of all chunks",
        "When enabled, only chunks in the whitelist will be monitored"
    })
    private WhitelistConfig whitelist = new WhitelistConfig();

    @CustomKey("alerts")
    @Comment("Alert settings")
    private AlertsConfig alerts = new AlertsConfig();

    @CustomKey("logging")
    @Comment("Advanced logging system")
    private LoggingConfig logging = new LoggingConfig();

    @CustomKey("redstone-components")
    @Comment("Redstone components to monitor")
    private List<Material> redstoneComponents = Arrays.asList(
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
    );

    public static class ClearLaggConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("interval-seconds")
        @Comment("Automatic clear interval in seconds (300 = 5 minutes, 0 = disabled)")
        private int intervalSeconds = 300;

        @CustomKey("countdown-seconds")
        @Comment("Seconds before clear to broadcast countdown warnings")
        private List<Integer> countdownSeconds = Arrays.asList(60, 30, 10, 5, 4, 3, 2, 1);

        @CustomKey("broadcast-mode")
        @Comment("Broadcast display type: CHAT, ACTION_BAR, TITLE, SUBTITLE, ALL, NONE")
        private BroadcastMode broadcastMode = BroadcastMode.CHAT;

        @CustomKey("clear-ground-items")
        private boolean clearGroundItems = true;

        @CustomKey("remove-entities")
        @Comment({
            "Entities to remove during clear (supports conditions: onGround, !isMounted, !hasName, !isTamed, !isLeashed, liveTime>100)",
            "Examples: Arrow onGround, Boat !isMounted, Minecart !isMounted, Minecart_Tnt, Snowball, Egg"
        })
        private List<String> removeEntities = Arrays.asList(
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
                "Boat !isMounted",
                "Chest_Boat !isMounted",
                "Minecart !isMounted",
                "Minecart_Chest !isMounted",
                "Minecart_Hopper !isMounted",
                "Minecart_Tnt",
                "Minecart_Furnace",
                "Tnt liveTime>100"
        );

        @CustomKey("entity-whitelist")
        @Comment({
            "Entities that are 100% immune from clearing (SMP safe)",
            "Supports rules: Villager, Iron_Golem, Armor_Stand, Item_Frame, * hasName, * isTamed, * isLeashed, * isMounted"
        })
        private List<String> entityWhitelist = Arrays.asList(
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
                "* hasName",
                "* isTamed",
                "* isLeashed",
                "* isMounted"
        );

        @CustomKey("item-blacklist")
        @Comment("Items that will NEVER be cleared when dropped on ground")
        private List<Material> itemBlacklist = Arrays.asList(
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
        );

        @CustomKey("tps-emergency-threshold")
        @Comment("Emergency auto-clear when server TPS drops below this value (0.0 to disable)")
        private double tpsEmergencyThreshold = 0.0;

        public boolean isEnabled() {
            return enabled;
        }

        public int getIntervalSeconds() {
            return Math.max(0, intervalSeconds);
        }

        public List<Integer> getCountdownSeconds() {
            return countdownSeconds;
        }

        public BroadcastMode getBroadcastMode() {
            return broadcastMode != null ? broadcastMode : BroadcastMode.CHAT;
        }

        public boolean isClearGroundItems() {
            return clearGroundItems;
        }

        public List<String> getRemoveEntities() {
            return removeEntities;
        }

        public List<String> getEntityWhitelist() {
            return entityWhitelist;
        }

        public List<Material> getItemBlacklist() {
            return itemBlacklist;
        }

        public double getTpsEmergencyThreshold() {
            return tpsEmergencyThreshold;
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

        public int getRadius() {
            return Math.max(1, Math.min(10, radius));
        }

        public int getMaxSnapshots() {
            return Math.max(5, maxSnapshots);
        }

        public boolean isSaveToDisk() {
            return saveToDisk;
        }
    }

    public static class FreezeConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("duration-seconds")
        private int durationSeconds = 15;

        @CustomKey("max-freeze-attempts")
        private int maxFreezeAttempts = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public int getDurationSeconds() {
            return Math.max(1, durationSeconds);
        }

        public int getMaxFreezeAttempts() {
            return Math.max(1, maxFreezeAttempts);
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

        public double getTargetMspt() {
            return targetMspt;
        }

        public double getMinTps() {
            return minTps;
        }

        public double getHealthyHeadroomMultiplier() {
            return Math.max(1.0, healthyHeadroomMultiplier);
        }
    }

    public static class ClockFingerprintConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("clock-strictness")
        private double clockStrictness = 0.7;

        public boolean isEnabled() {
            return enabled;
        }

        public double getClockStrictness() {
            return Math.min(1.0, Math.max(0.1, clockStrictness));
        }
    }

    public static class ProximityConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("player-radius")
        private int playerRadius = 96;

        @CustomKey("unattended-strict-multiplier")
        private double unattendedStrictMultiplier = 0.6;

        public boolean isEnabled() {
            return enabled;
        }

        public int getPlayerRadius() {
            return Math.max(16, playerRadius);
        }

        public double getUnattendedStrictMultiplier() {
            return Math.min(1.0, Math.max(0.1, unattendedStrictMultiplier));
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

        public int getDurationSeconds() {
            return Math.max(1, durationSeconds);
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

        public int getThresholdPercent() {
            return thresholdPercent;
        }
    }

    public static class WhitelistConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = false;

        @CustomKey("chunks")
        @Comment("List chunks as \"world:chunkX:chunkZ\" (e.g., \"world:10:15\")")
        private List<String> chunks = List.of("world:0:0");

        public boolean isEnabled() {
            return enabled;
        }

        public List<String> getChunks() {
            return chunks;
        }
    }

    public static class AlertsConfig extends OkaeriConfig {
        @CustomKey("enabled")
        private boolean enabled = true;

        @CustomKey("log-to-console")
        private boolean logToConsole = true;

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isLogToConsole() {
            return logToConsole;
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

        public boolean isConsoleMirror() {
            return consoleMirror;
        }

        public int getMaxFiles() {
            return maxFiles;
        }

        public long getMaxSizeMb() {
            return maxSizeMb;
        }

        public boolean isPerformanceStats() {
            return performanceStats;
        }
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public int getChunkThreshold() {
        return Math.max(1, chunkThreshold);
    }

    public int getBlockThreshold() {
        return Math.max(1, blockThreshold);
    }

    public int getResetIntervalTicks() {
        return Math.max(1, resetIntervalTicks);
    }

    public boolean isDebug() {
        return debug;
    }

    public RemovalAction getRemovalAction() {
        return removalAction != null ? removalAction : RemovalAction.FREEZE;
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

    public ProximityConfig getProximity() {
        return proximity;
    }

    public LockdownConfig getLockdown() {
        return lockdown;
    }

    public WarningConfig getWarning() {
        return warning;
    }

    public List<String> getEnabledWorlds() {
        return enabledWorlds;
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

    public List<Material> getRedstoneComponents() {
        return redstoneComponents;
    }
}
