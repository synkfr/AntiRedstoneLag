package org.ayosynk.antiRedstoneLag.gui;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;
import org.ayosynk.antiRedstoneLag.diagnostics.HotspotService;
import org.ayosynk.antiRedstoneLag.diagnostics.SnapshotRecord;
import org.ayosynk.antiRedstoneLag.diagnostics.SnapshotService;
import org.ayosynk.antiRedstoneLag.engine.AdaptiveMonitor;
import org.ayosynk.antiRedstoneLag.engine.RedstoneEngine;
import org.ayosynk.antiRedstoneLag.listener.RedstoneListener;
import org.ayosynk.antiRedstoneLag.manager.ClearLaggManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interactive Staff Inventory GUI styled with #1F1F1F titles and Paper 1.21+ DataComponentTypes.
 */
@SuppressWarnings("UnstableApiUsage")
public class StaffGuiManager {
    private static final int PAGE_ITEMS = 28;
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public StaffGuiManager(AntiRedstoneLag plugin, ConfigManager configManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    public void openMain(Player player) {
        if (!configManager.getPluginConfig().getGui().isEnabled()) {
            messageManager.sendIfNotBlank(player, messageManager.getMessagesConfig().getGui().getDisabled());
            return;
        }
        String rawTitle = messageManager.getMessagesConfig().getGui().getTitles().getMain();
        ArlMenuHolder holder = new ArlMenuHolder(ArlMenuHolder.MenuPage.MAIN, 1);
        Inventory inv = Bukkit.createInventory(holder, 45, messageManager.parseMessage(rawTitle));
        holder.bindInventory(inv);
        fillBorder(inv, 45);

        RedstoneEngine engine = plugin.getRedstoneEngine();
        boolean enabled = configManager.isRedstoneEnabled();
        PluginConfig.RemovalAction action = configManager.getRemovalAction();
        boolean alerts = configManager.isAlertsEnabled();
        boolean inspecting = plugin.getInspectorService().isInspecting(player.getUniqueId());

        // Slot 11: Redstone mode cycler (ON -> NOTIFY -> OFF -> ON)
        Material modeMat = !enabled ? Material.RED_CONCRETE
                : (action == PluginConfig.RemovalAction.NOTIFY ? Material.YELLOW_CONCRETE : Material.LIME_CONCRETE);
        String modeLabel = !enabled ? "<color:#E20000>OFF"
                : (action == PluginConfig.RemovalAction.NOTIFY ? "<color:#E2B700>NOTIFY ONLY" : "<color:#00E262>ACTIVE (" + action.name() + ")");
        inv.setItem(11, buildItem(modeMat,
                "<color:#E2B700>Redstone Protection",
                List.of(
                        "<color:#A0A0A0>State: " + modeLabel,
                        "<color:#A0A0A0>Action: <color:#FFFFFF>" + action.name(),
                        "",
                        "<color:#00E262>Click to cycle ON / NOTIFY / OFF"
                ),
                enabled));
        holder.setAction(11, event -> {
            if (!player.hasPermission("antiredstonelag.redstone")) {
                messageManager.sendIfNotBlank(player, messageManager.getMessagesConfig().getCommands().getNoPermission());
                return;
            }
            RedstoneListener listener = plugin.getRedstoneListener();
            if (!configManager.isRedstoneEnabled()) {
                configManager.setRedstoneEnabled(true);
                if (configManager.getRemovalAction() == PluginConfig.RemovalAction.NOTIFY) {
                    configManager.setRemovalAction(PluginConfig.RemovalAction.FREEZE);
                }
            } else if (configManager.getRemovalAction() != PluginConfig.RemovalAction.NOTIFY) {
                configManager.setRemovalAction(PluginConfig.RemovalAction.NOTIFY);
                engine.clearActiveBlocksAndLockdowns();
            } else {
                configManager.setRedstoneEnabled(false);
                engine.clearActiveBlocksAndLockdowns();
            }
            if (listener != null) listener.refreshCache();
            openMain(player);
        });

        // Slot 13: Alerts toggle
        inv.setItem(13, buildItem(alerts ? Material.BELL : Material.GRAY_DYE,
                "<color:#E2B700>Staff Lag Alerts",
                List.of(
                        "<color:#A0A0A0>Status: " + (alerts ? "<color:#00E262>Enabled" : "<color:#E20000>Muted"),
                        "<color:#A0A0A0>TPS Alerts: " + (configManager.isNotifyOnTpsLag() ? "<color:#00E262>ON" : "<color:#E20000>OFF"),
                        "",
                        "<color:#00E262>Click to toggle alerts"
                ),
                alerts));
        holder.setAction(13, event -> {
            if (!player.hasPermission("antiredstonelag.alerts")) {
                messageManager.sendIfNotBlank(player, messageManager.getMessagesConfig().getCommands().getNoPermission());
                return;
            }
            configManager.setAlertsEnabled(!configManager.isAlertsEnabled());
            openMain(player);
        });

        // Slot 15: Visual Inspector toggle
        inv.setItem(15, buildItem(inspecting ? Material.ENDER_EYE : Material.SPYGLASS,
                "<color:#E2B700>Visual Inspector",
                List.of(
                        "<color:#A0A0A0>Status: " + (inspecting ? "<color:#00E262>Running" : "<color:#A0A0A0>Idle"),
                        "<color:#A0A0A0>Highlights hot/frozen blocks & chunk bounds.",
                        "",
                        "<color:#00E262>Click to toggle inspector"
                ),
                inspecting));
        holder.setAction(15, event -> {
            if (!player.hasPermission("antiredstonelag.inspect")) {
                messageManager.sendIfNotBlank(player, messageManager.getMessagesConfig().getCommands().getNoPermission());
                return;
            }
            plugin.getInspectorService().toggle(player, null);
            openMain(player);
        });

        // Slot 20: Hotspots browser
        int hotspotCount = plugin.getHotspotService().getHotspots(50).size();
        inv.setItem(20, buildItem(Material.COMPASS,
                "<color:#E2B700>Redstone Hotspots",
                List.of(
                        "<color:#A0A0A0>Active clusters: <color:#FFFFFF>" + hotspotCount,
                        "<color:#A0A0A0>Inspect busiest chunks and teleport.",
                        "",
                        "<color:#00E262>Click to open hotspots"
                ),
                false));
        holder.setAction(20, event -> openHotspots(player, 1));

        // Slot 22: Forensic Snapshots browser
        int snapCount = plugin.getSnapshotService().getSnapshotsNewestFirst().size();
        inv.setItem(22, buildItem(Material.WRITABLE_BOOK,
                "<color:#E2B700>Forensic Snapshots",
                List.of(
                        "<color:#A0A0A0>Recorded snapshots: <color:#FFFFFF>" + snapCount,
                        "<color:#A0A0A0>Storage: <color:#FFFFFF>" + plugin.getStorageService().backendName(),
                        "",
                        "<color:#00E262>Click to open snapshots"
                ),
                false));
        holder.setAction(22, event -> openSnapshots(player, 1));

        // Slot 24: Frozen & Locked Chunks
        int freezes = engine.getActiveFreezeCount();
        int lockdowns = engine.getActiveLockdownCount();
        inv.setItem(24, buildItem(Material.ICE,
                "<color:#E2B700>Frozen & Locked Regions",
                List.of(
                        "<color:#A0A0A0>Frozen clocks: <color:#E2B700>" + freezes,
                        "<color:#A0A0A0>Locked chunks: <color:#E20000>" + lockdowns,
                        "",
                        "<color:#00E262>Click to manage & unfreeze/unlock"
                ),
                freezes > 0 || lockdowns > 0));
        holder.setAction(24, event -> openLocked(player, 1));

        // Slot 30: ClearLagg Panel
        inv.setItem(30, buildItem(Material.HOPPER,
                "<color:#E2B700>ClearLagg Console",
                List.of(
                        "<color:#A0A0A0>Next clear: <color:#FFFFFF>" + plugin.getClearLaggManager().getSecondsUntilClear() + "s",
                        "<color:#A0A0A0>Total cleared: <color:#FFFFFF>" + engine.getStats().getTotalEntitiesCleared(),
                        "",
                        "<color:#00E262>Click to open ClearLagg controls"
                ),
                false));
        holder.setAction(30, event -> openClearLagg(player));

        // Slot 32: Live Server Telemetry
        String tpsStr = AdaptiveMonitor.isFolia() ? "Folia" : String.format("%.1f", engine.getAdaptiveMonitor().getCachedTps());
        String msptStr = AdaptiveMonitor.isFolia() ? "N/A" : String.format("%.1fms", engine.getAdaptiveMonitor().getCachedMspt());
        inv.setItem(32, buildItem(Material.REDSTONE_TORCH,
                "<color:#E2B700>Server Telemetry",
                List.of(
                        "<color:#A0A0A0>TPS: <color:#00E262>" + tpsStr + " <color:#A0A0A0>| MSPT: <color:#FFFFFF>" + msptStr,
                        "<color:#A0A0A0>Monitored: <color:#FFFFFF>" + engine.getChunksMonitored() + " chunks / " + engine.getBlocksMonitored() + " blocks",
                        "<color:#A0A0A0>Mitigations: <color:#FFFFFF>" + engine.getStats().getTotalRemovals() + " total (" + engine.getStats().getRemovalsToday() + " today)"
                ),
                false));

        // Slot 34: Reload Config
        inv.setItem(34, buildItem(Material.NETHER_STAR,
                "<color:#00E262>Reload Configuration",
                List.of(
                        "<color:#A0A0A0>Reload config.yml, messages.yml,",
                        "<color:#A0A0A0>detectors, and scheduler intervals.",
                        "",
                        "<color:#00E262>Click to reload"
                ),
                false));
        holder.setAction(34, event -> {
            if (!player.hasPermission("antiredstonelag.reload")) {
                messageManager.sendIfNotBlank(player, messageManager.getMessagesConfig().getCommands().getNoPermission());
                return;
            }
            plugin.reloadPlugin();
            messageManager.sendIfNotBlank(player, messageManager.getMessagesConfig().getCommands().getReloadSuccess());
            openMain(player);
        });

        player.openInventory(inv);
    }

    public void openHotspots(Player player, int page) {
        List<HotspotService.HotspotGroup> hotspots = plugin.getHotspotService().getHotspots(50);
        int totalPages = Math.max(1, (int) Math.ceil(hotspots.size() / (double) PAGE_ITEMS));
        int curPage = Math.max(1, Math.min(page, totalPages));

        String title = messageManager.getMessagesConfig().getGui().getTitles().getHotspots();
        ArlMenuHolder holder = new ArlMenuHolder(ArlMenuHolder.MenuPage.HOTSPOTS, curPage);
        Inventory inv = Bukkit.createInventory(holder, 54, messageManager.parseMessage(title, Map.of(
                "page", String.valueOf(curPage),
                "pages", String.valueOf(totalPages)
        )));
        holder.bindInventory(inv);
        fillBorder(inv, 54);

        int start = (curPage - 1) * PAGE_ITEMS;
        int end = Math.min(start + PAGE_ITEMS, hotspots.size());
        int[] slots = innerSlots54();
        for (int i = start; i < end; i++) {
            HotspotService.HotspotGroup g = hotspots.get(i);
            int slot = slots[i - start];
            inv.setItem(slot, buildItem(
                    g.hasLockedChunk() ? Material.REDSTONE_BLOCK : Material.REPEATER,
                    "<color:#E2B700>Cluster #" + (i + 1) + " <color:#FFFFFF>" + g.worldName(),
                    List.of(
                            "<color:#A0A0A0>Center: <color:#FFFFFF>" + g.centerBlockX() + ", ~, " + g.centerBlockZ(),
                            "<color:#A0A0A0>Activity: <color:#00E262>" + g.totalActivity() + " UPS",
                            "<color:#A0A0A0>Chunks: <color:#FFFFFF>" + g.chunkCount(),
                            "<color:#A0A0A0>Status: " + (g.hasLockedChunk() ? "<color:#E20000>LOCKED" : "<color:#00E262>Active"),
                            "",
                            "<color:#00E262>Left-click to teleport"
                    ),
                    g.hasLockedChunk()));
            holder.setAction(slot, event -> {
                World world = Bukkit.getWorld(g.worldId());
                if (world != null) {
                    int safeY = Math.max(world.getMinHeight() + 1, world.getHighestBlockYAt(g.centerBlockX(), g.centerBlockZ()) + 1);
                    player.closeInventory();
                    player.teleportAsync(new Location(world, g.centerBlockX() + 0.5, safeY, g.centerBlockZ() + 0.5));
                }
            });
        }

        addNavFooter(inv, holder, curPage, totalPages,
                p -> openHotspots(player, p),
                () -> openMain(player));
        player.openInventory(inv);
    }

    public void openSnapshots(Player player, int page) {
        SnapshotService svc = plugin.getSnapshotService();
        List<SnapshotRecord> list = svc.getSnapshotsNewestFirst();
        int totalPages = Math.max(1, (int) Math.ceil(list.size() / (double) PAGE_ITEMS));
        int curPage = Math.max(1, Math.min(page, totalPages));

        String title = messageManager.getMessagesConfig().getGui().getTitles().getSnapshots();
        ArlMenuHolder holder = new ArlMenuHolder(ArlMenuHolder.MenuPage.SNAPSHOTS, curPage);
        Inventory inv = Bukkit.createInventory(holder, 54, messageManager.parseMessage(title, Map.of(
                "page", String.valueOf(curPage),
                "pages", String.valueOf(totalPages)
        )));
        holder.bindInventory(inv);
        fillBorder(inv, 54);

        int start = (curPage - 1) * PAGE_ITEMS;
        int end = Math.min(start + PAGE_ITEMS, list.size());
        int[] slots = innerSlots54();
        for (int i = start; i < end; i++) {
            SnapshotRecord snap = list.get(i);
            int slot = slots[i - start];
            Material icon = snap.triggerMaterial.isItem() ? snap.triggerMaterial : Material.OBSERVER;
            inv.setItem(slot, buildItem(
                    icon,
                    "<color:#E2B700>" + snap.id + " <color:#A0A0A0>(" + DATE_FMT.format(Instant.ofEpochMilli(snap.timestamp)) + ")",
                    List.of(
                            "<color:#A0A0A0>Location: <color:#FFFFFF>" + snap.x + ", " + snap.y + ", " + snap.z + " (" + snap.worldName + ")",
                            "<color:#A0A0A0>Trigger: <color:#E20000>" + snap.triggerMaterial.name(),
                            "<color:#A0A0A0>Culprit: <color:#00E262>" + snap.culpritName,
                            "<color:#A0A0A0>Updates: <color:#FFFFFF>" + snap.chunkUpdates + " chunk / " + snap.blockUpdates + " block",
                            "",
                            "<color:#00E262>Left-click: View & Highlight",
                            "<color:#00E262>Right-click: Teleport",
                            "<color:#E20000>Shift-Right-click: Delete"
                    ),
                    false));
            holder.setAction(slot, event -> {
                ClickType click = event.getClick();
                if (click == ClickType.SHIFT_RIGHT) {
                    svc.deleteSnapshot(snap.id);
                    openSnapshots(player, curPage);
                } else if (click.isRightClick()) {
                    player.closeInventory();
                    svc.teleportToSnapshot(player, snap.id);
                } else {
                    player.closeInventory();
                    svc.sendSnapshotView(player, snap.id);
                    svc.highlightSnapshot(player, snap.id);
                }
            });
        }

        addNavFooter(inv, holder, curPage, totalPages,
                p -> openSnapshots(player, p),
                () -> openMain(player));
        player.openInventory(inv);
    }

    public void openLocked(Player player, int page) {
        RedstoneEngine engine = plugin.getRedstoneEngine();
        List<RedstoneEngine.LockedChunkInfo> lockdowns = engine.getActiveLockdowns();
        List<RedstoneEngine.FrozenBlockInfo> freezes = engine.getActiveFreezes();
        int totalItems = lockdowns.size() + freezes.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) PAGE_ITEMS));
        int curPage = Math.max(1, Math.min(page, totalPages));

        String title = messageManager.getMessagesConfig().getGui().getTitles().getLocked();
        ArlMenuHolder holder = new ArlMenuHolder(ArlMenuHolder.MenuPage.LOCKED, curPage);
        Inventory inv = Bukkit.createInventory(holder, 54, messageManager.parseMessage(title, Map.of(
                "page", String.valueOf(curPage),
                "pages", String.valueOf(totalPages)
        )));
        holder.bindInventory(inv);
        fillBorder(inv, 54);

        int[] slots = innerSlots54();
        int start = (curPage - 1) * PAGE_ITEMS;
        int end = Math.min(start + PAGE_ITEMS, totalItems);
        for (int idx = start; idx < end; idx++) {
            int slot = slots[idx - start];
            if (idx < lockdowns.size()) {
                RedstoneEngine.LockedChunkInfo lc = lockdowns.get(idx);
                inv.setItem(slot, buildItem(
                        Material.BARRIER,
                        "<color:#E20000>Locked Chunk <color:#FFFFFF>" + lc.chunkX() + ", " + lc.chunkZ(),
                        List.of(
                                "<color:#A0A0A0>World: <color:#FFFFFF>" + lc.worldName(),
                                "<color:#A0A0A0>Remaining: <color:#E2B700>" + lc.remainingSeconds() + "s",
                                "",
                                "<color:#00E262>Left-click: Unlock chunk",
                                "<color:#00E262>Right-click: Teleport to chunk"
                        ),
                        true));
                holder.setAction(slot, event -> {
                    if (event.getClick().isRightClick()) {
                        World w = Bukkit.getWorld(lc.worldId());
                        if (w != null) {
                            int bx = lc.chunkX() * 16 + 8;
                            int bz = lc.chunkZ() * 16 + 8;
                            player.closeInventory();
                            player.teleportAsync(new Location(w, bx + 0.5, w.getHighestBlockYAt(bx, bz) + 1, bz + 0.5));
                        }
                    } else if (player.hasPermission("antiredstonelag.control")) {
                        engine.unlockChunk(lc.worldId(), lc.chunkKey());
                        openLocked(player, curPage);
                    }
                });
            } else {
                RedstoneEngine.FrozenBlockInfo fb = freezes.get(idx - lockdowns.size());
                inv.setItem(slot, buildItem(
                        Material.PACKED_ICE,
                        "<color:#E2B700>Frozen Block <color:#FFFFFF>" + fb.material().name(),
                        List.of(
                                "<color:#A0A0A0>World: <color:#FFFFFF>" + fb.worldName(),
                                "<color:#A0A0A0>Location: <color:#FFFFFF>" + fb.x() + ", " + fb.y() + ", " + fb.z(),
                                "<color:#A0A0A0>Remaining: <color:#E2B700>" + fb.remainingSeconds() + "s",
                                "",
                                "<color:#00E262>Left-click: Unfreeze block",
                                "<color:#00E262>Right-click: Teleport to block"
                        ),
                        false));
                holder.setAction(slot, event -> {
                    if (event.getClick().isRightClick()) {
                        World w = Bukkit.getWorld(fb.worldId());
                        if (w != null) {
                            player.closeInventory();
                            player.teleportAsync(new Location(w, fb.x() + 0.5, fb.y() + 1.0, fb.z() + 0.5));
                        }
                    } else if (player.hasPermission("antiredstonelag.control")) {
                        engine.unfreezeBlock(fb.worldId(), fb.blockKey());
                        openLocked(player, curPage);
                    }
                });
            }
        }

        // Slot 52: Clear all freezes and lockdowns
        inv.setItem(52, buildItem(Material.MILK_BUCKET,
                "<color:#00E262>Unfreeze & Unlock All",
                List.of("<color:#A0A0A0>Click to clear all active freezes and chunk locks"),
                false));
        holder.setAction(52, event -> {
            if (player.hasPermission("antiredstonelag.control")) {
                engine.unfreezeAll();
                engine.unlockAll();
                openLocked(player, 1);
            }
        });

        addNavFooter(inv, holder, curPage, totalPages,
                p -> openLocked(player, p),
                () -> openMain(player));
        player.openInventory(inv);
    }

    public void openClearLagg(Player player) {
        ClearLaggManager clm = plugin.getClearLaggManager();
        ClearLaggManager.EntityCounts counts = clm.countEntities();

        String title = messageManager.getMessagesConfig().getGui().getTitles().getClearlagg();
        ArlMenuHolder holder = new ArlMenuHolder(ArlMenuHolder.MenuPage.CLEARLAGG, 1);
        Inventory inv = Bukkit.createInventory(holder, 36, messageManager.parseMessage(title));
        holder.bindInventory(inv);
        fillBorder(inv, 36);

        inv.setItem(10, buildItem(Material.GOLD_INGOT, "<color:#00E262>Clear Ground Items",
                List.of("<color:#A0A0A0>Queued items: <color:#FFFFFF>" + counts.items, "", "<color:#00E262>Click to clear items"), false));
        holder.setAction(10, e -> triggerClear(player, ClearLaggManager.ClearType.ITEMS));

        inv.setItem(11, buildItem(Material.ARROW, "<color:#00E262>Clear Projectiles",
                List.of("<color:#A0A0A0>Projectiles: <color:#FFFFFF>" + counts.projectiles, "", "<color:#00E262>Click to clear projectiles"), false));
        holder.setAction(11, e -> triggerClear(player, ClearLaggManager.ClearType.PROJECTILES));

        inv.setItem(12, buildItem(Material.ZOMBIE_HEAD, "<color:#00E262>Clear Monsters",
                List.of("<color:#A0A0A0>Monsters: <color:#FFFFFF>" + counts.monsters, "", "<color:#00E262>Click to clear monsters"), false));
        holder.setAction(12, e -> triggerClear(player, ClearLaggManager.ClearType.MOBS));

        inv.setItem(14, buildItem(Material.MINECART, "<color:#00E262>Clear Empty Vehicles",
                List.of("<color:#A0A0A0>Vehicles: <color:#FFFFFF>" + counts.vehicles, "", "<color:#00E262>Click to clear vehicles"), false));
        holder.setAction(14, e -> triggerClear(player, ClearLaggManager.ClearType.VEHICLES));

        inv.setItem(15, buildItem(Material.EXPERIENCE_BOTTLE, "<color:#00E262>Clear XP Orbs",
                List.of("<color:#A0A0A0>XP Orbs: <color:#FFFFFF>" + counts.xpOrbs, "", "<color:#00E262>Click to clear XP orbs"), false));
        holder.setAction(15, e -> triggerClear(player, ClearLaggManager.ClearType.XP));

        inv.setItem(16, buildItem(Material.TNT, "<color:#E20000>Clear All Matching Entities",
                List.of("<color:#A0A0A0>Total tracked: <color:#FFFFFF>" + counts.getTotal(), "", "<color:#E20000>Click to run full clear"), true));
        holder.setAction(16, e -> triggerClear(player, ClearLaggManager.ClearType.ALL));

        boolean isPaused = clm.isPaused();
        inv.setItem(22, buildItem(isPaused ? Material.LIME_DYE : Material.CLOCK,
                "<color:#E2B700>Auto-Clear Timer",
                List.of(
                        "<color:#A0A0A0>Next clear: <color:#FFFFFF>" + clm.getSecondsUntilClear() + "s",
                        "<color:#A0A0A0>State: " + (isPaused ? "<color:#E2B700>Paused" : "<color:#00E262>Running"),
                        "",
                        "<color:#00E262>Left-click: Toggle Pause/Resume",
                        "<color:#E2B700>Right-click: Reset Countdown"
                ),
                false));
        holder.setAction(22, e -> {
            if (!player.hasPermission("antiredstonelag.clear")) return;
            if (e.getClick().isRightClick()) {
                clm.cancelScheduledClear();
            } else {
                if (clm.isPaused()) clm.resumeTimer();
                else clm.pauseTimer();
            }
            openClearLagg(player);
        });

        inv.setItem(31, buildItem(Material.ARROW, "<color:#00E262>Back to Dashboard", List.of(), false));
        holder.setAction(31, e -> openMain(player));

        player.openInventory(inv);
    }

    private void triggerClear(Player player, ClearLaggManager.ClearType type) {
        if (!player.hasPermission("antiredstonelag.clear")) {
            messageManager.sendIfNotBlank(player, messageManager.getMessagesConfig().getCommands().getNoPermission());
            return;
        }
        plugin.getClearLaggManager().clearEntitiesAsync(type).thenRun(() ->
                plugin.getScheduler().runTask(player, () -> {
                    if (player.isOnline()) openClearLagg(player);
                }));
    }

    private void addNavFooter(Inventory inv, ArlMenuHolder holder, int page, int totalPages,
                              java.util.function.IntConsumer openPage, Runnable back) {
        if (page > 1) {
            inv.setItem(45, buildItem(Material.SPECTRAL_ARROW, "<color:#00E262>Previous Page",
                    List.of("<color:#A0A0A0>Page " + (page - 1) + "/" + totalPages), false));
            holder.setAction(45, e -> openPage.accept(page - 1));
        }
        inv.setItem(49, buildItem(Material.ARROW, "<color:#00E262>Back to Dashboard", List.of(), false));
        holder.setAction(49, e -> back.run());
        if (page < totalPages) {
            inv.setItem(53, buildItem(Material.SPECTRAL_ARROW, "<color:#00E262>Next Page",
                    List.of("<color:#A0A0A0>Page " + (page + 1) + "/" + totalPages), false));
            holder.setAction(53, e -> openPage.accept(page + 1));
        }
    }

    private ItemStack buildItem(Material material, String nameTemplate, List<String> loreTemplates, boolean glint) {
        ItemStack item = ItemStack.of(material);
        item.setData(DataComponentTypes.ITEM_NAME, messageManager.parseMessage(nameTemplate));
        if (loreTemplates != null && !loreTemplates.isEmpty()) {
            List<Component> loreLines = new ArrayList<>(loreTemplates.size());
            for (String line : loreTemplates) {
                loreLines.add(line.isEmpty() ? Component.empty() : messageManager.parseMessage(line));
            }
            item.setData(DataComponentTypes.LORE, ItemLore.lore(loreLines));
        }
        if (glint) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.ENCHANTMENTS)
                .build());
        return item;
    }

    private static void fillBorder(Inventory inv, int size) {
        ItemStack pane = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        pane.setData(DataComponentTypes.ITEM_NAME, Component.empty());
        pane.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inv.setItem(slot, pane);
            }
        }
    }

    private static int[] innerSlots54() {
        return new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
    }
}
