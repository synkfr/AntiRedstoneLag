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
