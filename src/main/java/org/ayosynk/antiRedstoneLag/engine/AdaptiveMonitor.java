package org.ayosynk.antiRedstoneLag.engine;

import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;
import org.bukkit.Bukkit;

/**
 * Tracks server TPS/MSPT and computes adaptive threshold multipliers on Paper/Purpur.
 */
public class AdaptiveMonitor {
    private static final boolean FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;
    }

    private final ConfigManager configManager;
    private volatile double cachedAdaptiveMultiplier = 1.0;
    private volatile boolean cachedServerLagging = false;
    private volatile double cachedTps = 20.0;
    private volatile double cachedMspt = 0.0;

    public AdaptiveMonitor(ConfigManager configManager) {
        this.configManager = configManager;
        updateSnapshot();
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public void updateSnapshot() {
        if (FOLIA || configManager == null) {
            cachedAdaptiveMultiplier = 1.0;
            cachedServerLagging = false;
            return;
        }
        try {
            double mspt = Bukkit.getAverageTickTime();
            double tps = Bukkit.getTPS()[0];
            cachedMspt = mspt;
            cachedTps = tps;

            PluginConfig.AdaptiveConfig adaptive = configManager.getPluginConfig().getAdaptive();
            double minTps = adaptive.getMinTps();
            double targetMspt = adaptive.getTargetMspt();
            cachedServerLagging = (tps < minTps) || (mspt > targetMspt);

            if (!adaptive.isEnabled()) {
                cachedAdaptiveMultiplier = 1.0;
                return;
            }
            if (tps < minTps || mspt >= 50.0) {
                cachedAdaptiveMultiplier = 0.5;
            } else if (mspt > targetMspt) {
                cachedAdaptiveMultiplier = 0.75;
            } else if (mspt <= 35.0 && tps >= 19.5) {
                cachedAdaptiveMultiplier = adaptive.getHealthyHeadroomMultiplier();
            } else {
                cachedAdaptiveMultiplier = 1.0;
            }
        } catch (Throwable ignored) {
            cachedAdaptiveMultiplier = 1.0;
            cachedServerLagging = false;
        }
    }

    public double getAdaptiveMultiplier() {
        return cachedAdaptiveMultiplier;
    }

    public boolean isServerLagging() {
        return cachedServerLagging;
    }

    public double getCachedTps() {
        return cachedTps;
    }

    public double getCachedMspt() {
        return cachedMspt;
    }

    public int getPerformancePercent() {
        if (FOLIA) {
            return -1;
        }
        return Math.min(100, Math.max(0, (int) Math.round((cachedTps / 20.0) * 100.0)));
    }
}
