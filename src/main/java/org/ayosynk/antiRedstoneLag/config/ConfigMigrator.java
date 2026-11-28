package org.ayosynk.antiRedstoneLag.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Handles v1 -> v2 detection, backup to config.yml.bk, and automatic migration of compatible settings.
 */
public final class ConfigMigrator {
    public static final int CURRENT_CONFIG_VERSION = 2;

    private ConfigMigrator() {
    }

    public static boolean needsMigration(File configFile) {
        if (configFile == null || !configFile.exists()) {
            return false;
        }
        YamlConfiguration raw = YamlConfiguration.loadConfiguration(configFile);
        if (!raw.isSet("config-version")) {
            return true;
        }
        if (raw.isInt("config-version")) {
            return raw.getInt("config-version") != CURRENT_CONFIG_VERSION;
        }
        String rawStr = raw.getString("config-version", "").trim();
        return !"2".equals(rawStr) && !"2.0".equals(rawStr) && !"2.0.0".equals(rawStr);
    }

    public static YamlConfiguration backupAndReadOld(File configFile, Logger logger) throws IOException {
        File backupFile = new File(configFile.getParentFile(), configFile.getName() + ".bk");
        Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        if (logger != null) {
            logger.info("Created v1 safety backup: " + configFile.getName() + " -> " + backupFile.getName());
        }
        YamlConfiguration old = YamlConfiguration.loadConfiguration(configFile);
        Files.deleteIfExists(configFile.toPath());
        return old;
    }

    public static void applyCompatibleSettings(YamlConfiguration old, PluginConfig target, Logger logger) {
        if (old == null || target == null) {
            return;
        }

        if (old.isInt("chunk-threshold")) target.setChunkThreshold(old.getInt("chunk-threshold"));
        if (old.isInt("block-threshold")) target.setBlockThreshold(old.getInt("block-threshold"));
        if (old.isInt("reset-interval-ticks")) target.setResetIntervalTicks(old.getInt("reset-interval-ticks"));
        if (old.isBoolean("debug")) target.setDebug(old.getBoolean("debug"));
        if (old.isBoolean("redstone-enabled")) target.setRedstoneEnabled(old.getBoolean("redstone-enabled"));

        if (old.isString("removal-action")) {
            PluginConfig.RemovalAction action = parseEnum(
                    old.getString("removal-action"), PluginConfig.RemovalAction.class, target.getRemovalAction());
            target.setRemovalAction(action);
        }

        if (old.isList("enabled-worlds")) {
            List<String> worlds = old.getStringList("enabled-worlds");
            if (!worlds.isEmpty()) {
                target.setEnabledWorlds(new ArrayList<>(worlds));
            }
        }

        if (old.isList("redstone-components")) {
            List<Material> materials = parseMaterialList(old.getStringList("redstone-components"), logger);
            if (!materials.isEmpty()) {
                target.setRedstoneComponents(materials);
            }
        }

        ConfigurationSection cl = old.getConfigurationSection("clearlagg");
        if (cl != null) {
            var tc = target.getClearlagg();
            if (cl.isBoolean("enabled")) tc.setEnabled(cl.getBoolean("enabled"));
            if (cl.isInt("interval-seconds")) tc.setIntervalSeconds(cl.getInt("interval-seconds"));
            if (cl.isList("countdown-seconds")) tc.setCountdownSeconds(new ArrayList<>(cl.getIntegerList("countdown-seconds")));
            if (cl.isString("broadcast-mode")) {
                tc.setBroadcastMode(parseEnum(cl.getString("broadcast-mode"), PluginConfig.BroadcastMode.class, tc.getBroadcastMode()));
            }
            if (cl.isBoolean("clear-ground-items")) tc.setClearGroundItems(cl.getBoolean("clear-ground-items"));
            if (cl.isList("remove-entities")) tc.setRemoveEntities(new ArrayList<>(cl.getStringList("remove-entities")));
            if (cl.isList("entity-whitelist")) tc.setEntityWhitelist(new ArrayList<>(cl.getStringList("entity-whitelist")));
            if (cl.isList("item-blacklist")) {
                List<Material> blacklisted = parseMaterialList(cl.getStringList("item-blacklist"), logger);
                if (!blacklisted.isEmpty()) tc.setItemBlacklist(blacklisted);
            }
            if (cl.isSet("tps-emergency-threshold")) tc.setTpsEmergencyThreshold(cl.getDouble("tps-emergency-threshold"));
        }

        ConfigurationSection snap = old.getConfigurationSection("snapshot");
        if (snap != null) {
            var ts = target.getSnapshot();
            if (snap.isBoolean("enabled")) ts.setEnabled(snap.getBoolean("enabled"));
            if (snap.isInt("radius")) ts.setRadius(snap.getInt("radius"));
            if (snap.isInt("max-snapshots")) ts.setMaxSnapshots(snap.getInt("max-snapshots"));
            if (snap.isBoolean("save-to-disk")) ts.setSaveToDisk(snap.getBoolean("save-to-disk"));
        }

        ConfigurationSection freeze = old.getConfigurationSection("freeze");
        if (freeze != null) {
            var tf = target.getFreeze();
            if (freeze.isBoolean("enabled")) tf.setEnabled(freeze.getBoolean("enabled"));
            if (freeze.isInt("duration-seconds")) tf.setDurationSeconds(freeze.getInt("duration-seconds"));
            if (freeze.isInt("max-freeze-attempts")) tf.setMaxFreezeAttempts(freeze.getInt("max-freeze-attempts"));
        }

        ConfigurationSection adaptive = old.getConfigurationSection("adaptive");
        if (adaptive != null) {
            var ta = target.getAdaptive();
            if (adaptive.isBoolean("enabled")) ta.setEnabled(adaptive.getBoolean("enabled"));
            if (adaptive.isSet("target-mspt")) ta.setTargetMspt(adaptive.getDouble("target-mspt"));
            if (adaptive.isSet("min-tps")) ta.setMinTps(adaptive.getDouble("min-tps"));
            if (adaptive.isSet("healthy-headroom-multiplier")) {
                ta.setHealthyHeadroomMultiplier(adaptive.getDouble("healthy-headroom-multiplier"));
            }
        }

        ConfigurationSection fp = old.getConfigurationSection("fingerprint");
        if (fp != null) {
            var tfp = target.getFingerprint();
            if (fp.isBoolean("enabled")) tfp.setEnabled(fp.getBoolean("enabled"));
            if (fp.isSet("clock-strictness")) tfp.setClockStrictness(fp.getDouble("clock-strictness"));
        }

        ConfigurationSection prox = old.getConfigurationSection("proximity");
        if (prox != null) {
            var tp = target.getProximity();
            if (prox.isBoolean("enabled")) tp.setEnabled(prox.getBoolean("enabled"));
            if (prox.isInt("player-radius")) tp.setPlayerRadius(prox.getInt("player-radius"));
            if (prox.isSet("unattended-strict-multiplier")) {
                tp.setUnattendedStrictMultiplier(prox.getDouble("unattended-strict-multiplier"));
            }
        }

        ConfigurationSection ld = old.getConfigurationSection("lockdown");
        if (ld != null) {
            var tld = target.getLockdown();
            if (ld.isBoolean("enabled")) tld.setEnabled(ld.getBoolean("enabled"));
            if (ld.isInt("duration-seconds")) tld.setDurationSeconds(ld.getInt("duration-seconds"));
        }

        ConfigurationSection warn = old.getConfigurationSection("warning");
        if (warn != null) {
            var tw = target.getWarning();
            if (warn.isBoolean("enabled")) tw.setEnabled(warn.getBoolean("enabled"));
            if (warn.isInt("threshold-percent")) tw.setThresholdPercent(warn.getInt("threshold-percent"));
        }

        ConfigurationSection wl = old.getConfigurationSection("whitelist");
        if (wl != null) {
            var twl = target.getWhitelist();
            if (wl.isBoolean("enabled")) twl.setEnabled(wl.getBoolean("enabled"));
            if (wl.isList("chunks")) twl.setChunks(new ArrayList<>(wl.getStringList("chunks")));
        }

        ConfigurationSection alerts = old.getConfigurationSection("alerts");
        if (alerts != null) {
            var tal = target.getAlerts();
            if (alerts.isBoolean("enabled")) tal.setEnabled(alerts.getBoolean("enabled"));
            if (alerts.isBoolean("log-to-console")) tal.setLogToConsole(alerts.getBoolean("log-to-console"));
            if (alerts.isBoolean("notify-on-tps-lag")) tal.setNotifyOnTpsLag(alerts.getBoolean("notify-on-tps-lag"));
            if (alerts.isBoolean("only-when-lagging")) tal.setOnlyWhenLagging(alerts.getBoolean("only-when-lagging"));
        }

        ConfigurationSection logging = old.getConfigurationSection("logging");
        if (logging != null) {
            var tlog = target.getLogging();
            if (logging.isBoolean("enabled")) tlog.setEnabled(logging.getBoolean("enabled"));
            if (logging.isBoolean("console-mirror")) tlog.setConsoleMirror(logging.getBoolean("console-mirror"));
            if (logging.isInt("max-files")) tlog.setMaxFiles(logging.getInt("max-files"));
            if (logging.isSet("max-size-mb")) tlog.setMaxSizeMb(logging.getLong("max-size-mb"));
            if (logging.isBoolean("performance-stats")) tlog.setPerformanceStats(logging.getBoolean("performance-stats"));
        }

        target.setConfigVersion(CURRENT_CONFIG_VERSION);
        if (logger != null) {
            logger.info("Migrated compatible settings from v1 config.yml into v2 config.yml.");
        }
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type, E fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static List<Material> parseMaterialList(List<String> entries, Logger logger) {
        List<Material> list = new ArrayList<>();
        for (String name : entries) {
            if (name == null || name.isBlank()) continue;
            Material mat = Material.matchMaterial(name.trim());
            if (mat != null) {
                list.add(mat);
            } else if (logger != null) {
                logger.warning("Ignoring unrecognized material during config migration: " + name);
            }
        }
        return list;
    }
}
