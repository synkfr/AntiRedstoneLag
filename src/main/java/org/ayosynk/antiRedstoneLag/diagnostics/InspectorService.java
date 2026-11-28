package org.ayosynk.antiRedstoneLag.diagnostics;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.engine.AdaptiveMonitor;
import org.ayosynk.antiRedstoneLag.engine.RedstoneEngine;
import org.ayosynk.antiRedstoneLag.scheduler.TaskHandle;
import org.ayosynk.antiRedstoneLag.util.BlockKeyCodec;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live BossBar, ActionBar, and in-world particle inspector for staff diagnosing active redstone.
 */
public class InspectorService {
    private static final Particle.DustOptions DUST_HOT = new Particle.DustOptions(Color.fromRGB(226, 183, 0), 1.1f);
    private static final Particle.DustOptions DUST_FROZEN = new Particle.DustOptions(Color.fromRGB(226, 0, 0), 1.3f);
    private static final Particle.DustOptions DUST_BORDER_OK = new Particle.DustOptions(Color.fromRGB(0, 226, 98), 1.0f);
    private static final Particle.DustOptions DUST_BORDER_LOCKED = new Particle.DustOptions(Color.fromRGB(226, 0, 0), 1.2f);

    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final RedstoneEngine engine;

    private final Map<UUID, Long> activeInspectors = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();
    private TaskHandle tickHandle;

    public InspectorService(AntiRedstoneLag plugin,
                            ConfigManager configManager,
                            MessageManager messageManager,
                            RedstoneEngine engine) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.engine = engine;
        startTask();
    }

    public boolean toggle(Player player, Integer requestedSeconds) {
        UUID uuid = player.getUniqueId();
        if (activeInspectors.containsKey(uuid)) {
            disable(player);
            return false;
        }

        var cfg = configManager.getPluginConfig().getInspector();
        int duration = requestedSeconds != null ? requestedSeconds : cfg.getDefaultDurationSeconds();
        duration = Math.max(5, Math.min(cfg.getMaxDurationSeconds(), duration));

        activeInspectors.put(uuid, System.currentTimeMillis() + (duration * 1000L));
        String raw = messageManager.getMessagesConfig().getCommands().getInspectEnabled();
        messageManager.sendIfNotBlank(player, raw, Map.of("duration", String.valueOf(duration)));
        return true;
    }

    public boolean isInspecting(UUID uuid) {
        return activeInspectors.containsKey(uuid);
    }

    public void disable(Player player) {
        UUID uuid = player.getUniqueId();
        activeInspectors.remove(uuid);
        BossBar bar = activeBossBars.remove(uuid);
        if (bar != null) {
            player.hideBossBar(bar);
        }
        messageManager.sendIfNotBlank(player, messageManager.getMessagesConfig().getCommands().getInspectDisabled());
    }

    public void cleanup() {
        if (tickHandle != null) {
            tickHandle.cancel();
            tickHandle = null;
        }
        for (Map.Entry<UUID, BossBar> entry : activeBossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.hideBossBar(entry.getValue());
            }
        }
        activeBossBars.clear();
        activeInspectors.clear();
    }

    private void startTask() {
        tickHandle = plugin.getScheduler().runTaskTimer(() -> {
            if (activeInspectors.isEmpty()) return;
            long now = System.currentTimeMillis();
            boolean folia = AdaptiveMonitor.isFolia();
            double mspt = folia ? 0.0 : engine.getAdaptiveMonitor().getCachedMspt();
            double tps = folia ? 20.0 : engine.getAdaptiveMonitor().getCachedTps();
            var cfg = configManager.getPluginConfig().getInspector();
            var cmds = messageManager.getMessagesConfig().getCommands();

            for (Map.Entry<UUID, Long> entry : activeInspectors.entrySet()) {
                UUID uuid = entry.getKey();
                long expiry = entry.getValue();
                Player player = Bukkit.getPlayer(uuid);

                if (player == null || !player.isOnline() || now > expiry) {
                    activeInspectors.remove(uuid);
                    BossBar removed = activeBossBars.remove(uuid);
                    if (removed != null && player != null && player.isOnline()) {
                        player.hideBossBar(removed);
                        messageManager.sendIfNotBlank(player, cmds.getInspectDisabled());
                    }
                    continue;
                }

                plugin.getScheduler().runTask(player, () -> {
                    if (!player.isOnline() || !activeInspectors.containsKey(uuid)) return;
                    Location pLoc = player.getLocation();
                    World world = pLoc.getWorld();
                    if (world == null) return;

                    UUID worldId = world.getUID();
                    int cx = pLoc.getBlockX() >> 4;
                    int cz = pLoc.getBlockZ() >> 4;
                    long chunkKey = BlockKeyCodec.packChunk(cx, cz);
                    int chunkUps = engine.getDisplayChunkUpdates(worldId, chunkKey);
                    int threshold = Math.max(1, configManager.getChunkThreshold(world.getName()));
                    double ratio = Math.min(1.0, (double) chunkUps / threshold);
                    int percent = (int) Math.round(ratio * 100.0);

                    BossBar.Color barColor;
                    float progress;
                    Component title;

                    if (folia) {
                        barColor = ratio < 0.5 ? BossBar.Color.GREEN : (ratio < 0.85 ? BossBar.Color.YELLOW : BossBar.Color.RED);
                        progress = (float) ratio;
                        title = messageManager.parseMessage(cmds.getInspectBossbarFolia(), Map.of(
                                "ups", String.valueOf(chunkUps),
                                "percent", String.valueOf(percent)
                        ));
                    } else {
                        barColor = mspt <= 35.0 ? BossBar.Color.GREEN : (mspt <= 45.0 ? BossBar.Color.YELLOW : BossBar.Color.RED);
                        progress = (float) Math.min(1.0, Math.max(0.0, mspt / 50.0));
                        title = messageManager.parseMessage(cmds.getInspectBossbar(), Map.of(
                                "ups", String.valueOf(chunkUps),
                                "mspt", String.format("%.1f", mspt),
                                "tps", String.format("%.1f", tps)
                        ));
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

                    boolean chunkLocked = engine.isChunkLocked(worldId, chunkKey);
                    int nearbyFrozen = 0;

                    if (cfg.isShowBlockParticles()) {
                        List<Long> activeBlocks = engine.getActiveBlockKeysNear(worldId, cx, cz, 1);
                        int drawn = 0;
                        for (long bk : activeBlocks) {
                            if (drawn++ >= 64) break;
                            int bx = BlockKeyCodec.unpackBlockX(bk);
                            int by = BlockKeyCodec.unpackBlockY(bk);
                            int bz = BlockKeyCodec.unpackBlockZ(bk);
                            if (Math.abs(by - pLoc.getBlockY()) > 32) continue;
                            boolean frozen = engine.isBlockFrozen(worldId, bk);
                            if (frozen) nearbyFrozen++;
                            player.spawnParticle(Particle.DUST, bx + 0.5, by + 0.65, bz + 0.5,
                                    1, 0.15, 0.15, 0.15, frozen ? DUST_FROZEN : DUST_HOT);
                        }
                    }

                    if (cfg.isShowChunkBorders()) {
                        int minX = cx << 4;
                        int minZ = cz << 4;
                        int maxX = minX + 16;
                        int maxZ = minZ + 16;
                        double baseY = pLoc.getY();
                        Particle.DustOptions borderDust = chunkLocked ? DUST_BORDER_LOCKED : DUST_BORDER_OK;
                        for (double dy = 0; dy <= 3.0; dy += 1.0) {
                            double py = baseY + dy;
                            player.spawnParticle(Particle.DUST, minX, py, minZ, 1, 0, 0, 0, borderDust);
                            player.spawnParticle(Particle.DUST, maxX, py, minZ, 1, 0, 0, 0, borderDust);
                            player.spawnParticle(Particle.DUST, minX, py, maxZ, 1, 0, 0, 0, borderDust);
                            player.spawnParticle(Particle.DUST, maxX, py, maxZ, 1, 0, 0, 0, borderDust);
                        }
                    }

                    if (cfg.isShowActionbar()) {
                        if (chunkLocked) {
                            long rem = Math.max(1L, engine.getLockdownRemainingMillis(worldId, chunkKey) / 1000L);
                            String rawAction = cmds.getInspectActionbarLocked();
                            if (rawAction != null && !rawAction.isBlank()) {
                                player.sendActionBar(messageManager.parseMessage(rawAction, Map.of(
                                        "remaining", String.valueOf(rem),
                                        "ups", String.valueOf(chunkUps)
                                )));
                            }
                        } else {
                            String rawAction = cmds.getInspectActionbar();
                            if (rawAction != null && !rawAction.isBlank()) {
                                player.sendActionBar(messageManager.parseMessage(rawAction, Map.of(
                                        "ups", String.valueOf(chunkUps),
                                        "threshold", String.valueOf(threshold),
                                        "frozen", String.valueOf(nearbyFrozen)
                                )));
                            }
                        }
                    }
                });
            }
        }, 10L, 10L);
    }
}
