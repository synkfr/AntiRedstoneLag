package org.ayosynk.antiRedstoneLag.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.config.ConfigManager;
import org.ayosynk.antiRedstoneLag.config.MessageManager;
import org.ayosynk.antiRedstoneLag.config.PluginConfig;
import org.ayosynk.antiRedstoneLag.diagnostics.SnapshotRecord;
import org.ayosynk.antiRedstoneLag.engine.AdaptiveMonitor;
import org.ayosynk.antiRedstoneLag.engine.RedstoneEngine;
import org.ayosynk.antiRedstoneLag.listener.RedstoneListener;
import org.ayosynk.antiRedstoneLag.manager.ClearLaggManager;
import org.ayosynk.antiRedstoneLag.manager.LogManager;
import org.ayosynk.antiRedstoneLag.util.BlockKeyCodec;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Native Paper Brigadier command tree for /arl (alias /antiredstonelag).
 */
@SuppressWarnings("UnstableApiUsage")
public class ArlCommandTree {
    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AntiRedstoneLag plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final LogManager logManager;
    private final RedstoneEngine engine;

    public ArlCommandTree(AntiRedstoneLag plugin,
                          ConfigManager configManager,
                          MessageManager messageManager,
                          LogManager logManager,
                          RedstoneEngine engine) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.logManager = logManager;
        this.engine = engine;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arl")
                .executes(ctx -> {
                    sendHelp(ctx.getSource().getSender());
                    return 1;
                });

        root.then(Commands.literal("help")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.use"))
                .executes(ctx -> {
                    sendHelp(ctx.getSource().getSender());
                    return 1;
                }));

        root.then(Commands.literal("gui")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.gui"))
                .executes(ctx -> openGui(ctx.getSource().getSender())));

        root.then(Commands.literal("menu")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.gui"))
                .executes(ctx -> openGui(ctx.getSource().getSender())));

        root.then(buildRedstoneNode());
        root.then(buildAlertsNode());
        root.then(buildInspectNode());
        root.then(buildHotspotsNode());
        root.then(buildSnapshotNode());
        root.then(buildClearNode());
        root.then(buildUnfreezeNode());
        root.then(buildUnlockNode());
        root.then(buildWhitelistNode());

        root.then(Commands.literal("stats")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.stats"))
                .executes(ctx -> {
                    sendStats(ctx.getSource().getSender());
                    return 1;
                }));

        root.then(buildLogsNode());

        root.then(Commands.literal("reload")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.reload"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    plugin.reloadPlugin();
                    messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getReloadSuccess());
                    logManager.logToFile("COMMAND", sender.getName() + " executed reload command", null);
                    return 1;
                }));

        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildRedstoneNode() {
        return Commands.literal("redstone")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.redstone"))
                .executes(ctx -> sendRedstoneStatus(ctx.getSource().getSender()))
                .then(Commands.literal("status").executes(ctx -> sendRedstoneStatus(ctx.getSource().getSender())))
                .then(Commands.literal("on").executes(ctx -> setRedstoneMode(ctx.getSource().getSender(), "on")))
                .then(Commands.literal("off").executes(ctx -> setRedstoneMode(ctx.getSource().getSender(), "off")))
                .then(Commands.literal("notify").executes(ctx -> setRedstoneMode(ctx.getSource().getSender(), "notify")));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildAlertsNode() {
        return Commands.literal("alerts")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.alerts"))
                .executes(ctx -> sendAlertsStatus(ctx.getSource().getSender()))
                .then(Commands.literal("status").executes(ctx -> sendAlertsStatus(ctx.getSource().getSender())))
                .then(Commands.literal("on").executes(ctx -> setAlertsMode(ctx.getSource().getSender(), true)))
                .then(Commands.literal("off").executes(ctx -> setAlertsMode(ctx.getSource().getSender(), false)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildInspectNode() {
        return Commands.literal("inspect")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.inspect"))
                .executes(ctx -> toggleInspect(ctx.getSource().getSender(), null))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 300))
                        .suggests((ctx, builder) -> suggestStrings(builder, List.of("15", "30", "60", "120")))
                        .executes(ctx -> toggleInspect(ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "seconds"))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildHotspotsNode() {
        return Commands.literal("hotspots")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.hotspots"))
                .executes(ctx -> {
                    plugin.getHotspotService().sendHotspots(ctx.getSource().getSender(), 1);
                    return 1;
                })
                .then(Commands.literal("export").executes(ctx -> {
                    plugin.getHotspotService().exportHotspots(ctx.getSource().getSender());
                    return 1;
                }))
                .then(Commands.argument("page", IntegerArgumentType.integer(1, 50))
                        .suggests((ctx, builder) -> suggestStrings(builder, List.of("1", "2", "3", "4", "5")))
                        .executes(ctx -> {
                            plugin.getHotspotService().sendHotspots(ctx.getSource().getSender(),
                                    IntegerArgumentType.getInteger(ctx, "page"));
                            return 1;
                        }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildSnapshotNode() {
        return Commands.literal("snapshot")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.snapshot"))
                .executes(ctx -> {
                    plugin.getSnapshotService().sendSnapshotList(ctx.getSource().getSender(), 1);
                    return 1;
                })
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            plugin.getSnapshotService().sendSnapshotList(ctx.getSource().getSender(), 1);
                            return 1;
                        })
                        .then(Commands.argument("page", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> {
                                    plugin.getSnapshotService().sendSnapshotList(ctx.getSource().getSender(),
                                            IntegerArgumentType.getInteger(ctx, "page"));
                                    return 1;
                                })))
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            plugin.getSnapshotService().clearSnapshots();
                            messageManager.sendIfNotBlank(ctx.getSource().getSender(),
                                    messageManager.getMessagesConfig().getCommands().getSnapshotCleared());
                            return 1;
                        }))
                .then(Commands.literal("view")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(this::suggestSnapshotIds)
                                .executes(ctx -> {
                                    plugin.getSnapshotService().sendSnapshotView(
                                            ctx.getSource().getSender(), StringArgumentType.getString(ctx, "id"));
                                    return 1;
                                })))
                .then(Commands.literal("tp")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(this::suggestSnapshotIds)
                                .executes(ctx -> {
                                    plugin.getSnapshotService().teleportToSnapshot(
                                            ctx.getSource().getSender(), StringArgumentType.getString(ctx, "id"));
                                    return 1;
                                })))
                .then(Commands.literal("highlight")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(this::suggestSnapshotIds)
                                .executes(ctx -> {
                                    plugin.getSnapshotService().highlightSnapshot(
                                            ctx.getSource().getSender(), StringArgumentType.getString(ctx, "id"));
                                    return 1;
                                })))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(this::suggestSnapshotIds)
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    CommandSender sender = ctx.getSource().getSender();
                                    if (plugin.getSnapshotService().deleteSnapshot(id)) {
                                        messageManager.sendIfNotBlank(sender,
                                                messageManager.getMessagesConfig().getCommands().getSnapshotDeleted(),
                                                Map.of("id", id.toUpperCase()));
                                    } else {
                                        messageManager.sendIfNotBlank(sender,
                                                messageManager.getMessagesConfig().getCommands().getSnapshotNotFound(),
                                                Map.of("id", id));
                                    }
                                    return 1;
                                })))
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(this::suggestSnapshotIds)
                        .executes(ctx -> {
                            plugin.getSnapshotService().sendSnapshotView(
                                    ctx.getSource().getSender(), StringArgumentType.getString(ctx, "id"));
                            return 1;
                        }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildClearNode() {
        return Commands.literal("clear")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.clear"))
                .executes(ctx -> runClear(ClearLaggManager.ClearType.ITEMS))
                .then(Commands.literal("items").executes(ctx -> runClear(ClearLaggManager.ClearType.ITEMS)))
                .then(Commands.literal("all").executes(ctx -> runClear(ClearLaggManager.ClearType.ALL)))
                .then(Commands.literal("mobs").executes(ctx -> runClear(ClearLaggManager.ClearType.MOBS)))
                .then(Commands.literal("projectiles").executes(ctx -> runClear(ClearLaggManager.ClearType.PROJECTILES)))
                .then(Commands.literal("xp").executes(ctx -> runClear(ClearLaggManager.ClearType.XP)))
                .then(Commands.literal("vehicles").executes(ctx -> runClear(ClearLaggManager.ClearType.VEHICLES)))
                .then(Commands.literal("cancel").executes(ctx -> {
                    plugin.getClearLaggManager().cancelScheduledClear();
                    return 1;
                }))
                .then(Commands.literal("pause").executes(ctx -> {
                    plugin.getClearLaggManager().pauseTimer();
                    messageManager.sendIfNotBlank(ctx.getSource().getSender(),
                            messageManager.getMessagesConfig().getClearlagg().getPaused());
                    return 1;
                }))
                .then(Commands.literal("resume").executes(ctx -> {
                    plugin.getClearLaggManager().resumeTimer();
                    messageManager.sendIfNotBlank(ctx.getSource().getSender(),
                            messageManager.getMessagesConfig().getClearlagg().getResumed());
                    return 1;
                }))
                .then(Commands.literal("timer").executes(ctx -> sendClearTimer(ctx.getSource().getSender())))
                .then(Commands.literal("count").executes(ctx -> sendClearCounts(ctx.getSource().getSender())));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildUnfreezeNode() {
        return Commands.literal("unfreeze")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.control"))
                .executes(ctx -> {
                    messageManager.sendIfNotBlank(ctx.getSource().getSender(),
                            messageManager.getMessagesConfig().getCommands().getUsageUnfreeze());
                    return 1;
                })
                .then(Commands.literal("all").executes(ctx -> {
                    int count = engine.unfreezeAll();
                    sendUnfreezeResult(ctx.getSource().getSender(), count);
                    return 1;
                }))
                .then(Commands.literal("here").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getMessages().getPlayerOnly());
                        return 1;
                    }
                    Chunk chunk = player.getLocation().getChunk();
                    int count = engine.unfreezeChunk(chunk.getWorld().getUID(), BlockKeyCodec.packChunk(chunk.getX(), chunk.getZ()));
                    sendUnfreezeResult(player, count);
                    return 1;
                }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildUnlockNode() {
        return Commands.literal("unlock")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.control"))
                .executes(ctx -> {
                    messageManager.sendIfNotBlank(ctx.getSource().getSender(),
                            messageManager.getMessagesConfig().getCommands().getUsageUnlock());
                    return 1;
                })
                .then(Commands.literal("all").executes(ctx -> {
                    int count = engine.unlockAll();
                    sendUnlockResult(ctx.getSource().getSender(), count);
                    return 1;
                }))
                .then(Commands.literal("here").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getMessages().getPlayerOnly());
                        return 1;
                    }
                    Chunk chunk = player.getLocation().getChunk();
                    boolean unlocked = engine.unlockChunk(chunk.getWorld().getUID(), BlockKeyCodec.packChunk(chunk.getX(), chunk.getZ()));
                    sendUnlockResult(player, unlocked ? 1 : 0);
                    return 1;
                }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildWhitelistNode() {
        return Commands.literal("whitelist")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.control"))
                .executes(ctx -> {
                    messageManager.sendIfNotBlank(ctx.getSource().getSender(),
                            messageManager.getMessagesConfig().getCommands().getUsageWhitelist());
                    return 1;
                })
                .then(Commands.literal("list").executes(ctx -> sendWhitelistList(ctx.getSource().getSender())))
                .then(Commands.literal("add")
                        .executes(ctx -> modifyWhitelistHere(ctx.getSource().getSender(), true))
                        .then(Commands.argument("world", StringArgumentType.word())
                                .suggests(this::suggestWorlds)
                                .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                .executes(ctx -> modifyWhitelist(
                                                        ctx.getSource().getSender(),
                                                        StringArgumentType.getString(ctx, "world"),
                                                        IntegerArgumentType.getInteger(ctx, "chunkX"),
                                                        IntegerArgumentType.getInteger(ctx, "chunkZ"),
                                                        true))))))
                .then(Commands.literal("remove")
                        .executes(ctx -> modifyWhitelistHere(ctx.getSource().getSender(), false))
                        .then(Commands.argument("world", StringArgumentType.word())
                                .suggests(this::suggestWorlds)
                                .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                .executes(ctx -> modifyWhitelist(
                                                        ctx.getSource().getSender(),
                                                        StringArgumentType.getString(ctx, "world"),
                                                        IntegerArgumentType.getInteger(ctx, "chunkX"),
                                                        IntegerArgumentType.getInteger(ctx, "chunkZ"),
                                                        false))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildLogsNode() {
        return Commands.literal("logs")
                .requires(src -> hasPerm(src.getSender(), "antiredstonelag.logs"))
                .executes(ctx -> {
                    sendLogsInfo(ctx.getSource().getSender());
                    return 1;
                })
                .then(Commands.literal("download").executes(ctx -> {
                    sendLatestLogInfo(ctx.getSource().getSender());
                    return 1;
                }))
                .then(Commands.literal("latest").executes(ctx -> {
                    sendLatestLogInfo(ctx.getSource().getSender());
                    return 1;
                }))
                .then(Commands.literal("tail")
                        .executes(ctx -> {
                            sendLogsTail(ctx.getSource().getSender(), 15);
                            return 1;
                        })
                        .then(Commands.argument("lines", IntegerArgumentType.integer(1, 50))
                                .suggests((ctx, builder) -> suggestStrings(builder, List.of("10", "15", "25", "50")))
                                .executes(ctx -> {
                                    sendLogsTail(ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "lines"));
                                    return 1;
                                })));
    }

    private int openGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getMessages().getPlayerOnly());
            return 1;
        }
        plugin.getStaffGuiManager().openMain(player);
        return 1;
    }

    private int sendRedstoneStatus(CommandSender sender) {
        String status = !configManager.isRedstoneEnabled()
                ? "<color:#E20000>OFF"
                : (configManager.getRemovalAction() == PluginConfig.RemovalAction.NOTIFY
                ? "<color:#E2B700>NOTIFY ONLY"
                : "<color:#00E262>ON");
        String alerts = configManager.isAlertsEnabled() ? "<color:#00E262>ON" : "<color:#E20000>OFF";
        String raw = messageManager.getMessagesConfig().getCommands().getRedstoneStatus();
        messageManager.sendIfNotBlank(sender, raw, Map.of(
                "status", status,
                "action", configManager.getRemovalAction().name(),
                "alerts", alerts
        ));
        return 1;
    }

    private int setRedstoneMode(CommandSender sender, String mode) {
        RedstoneListener listener = plugin.getRedstoneListener();
        switch (mode) {
            case "on" -> {
                configManager.setRedstoneEnabled(true);
                if (configManager.getRemovalAction() == PluginConfig.RemovalAction.NOTIFY) {
                    configManager.setRemovalAction(PluginConfig.RemovalAction.FREEZE);
                }
                if (listener != null) listener.refreshCache();
                String rawOn = messageManager.getMessagesConfig().getCommands().getRedstoneOn();
                messageManager.sendIfNotBlank(sender, rawOn, Map.of("action", configManager.getRemovalAction().name()));
                logManager.logToFile("COMMAND", sender.getName() + " set redstone protection ON (" + configManager.getRemovalAction().name() + ")", null);
            }
            case "off" -> {
                configManager.setRedstoneEnabled(false);
                engine.clearActiveBlocksAndLockdowns();
                if (listener != null) listener.refreshCache();
                messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getRedstoneOff());
                logManager.logToFile("COMMAND", sender.getName() + " set redstone protection OFF", null);
            }
            case "notify" -> {
                configManager.setRedstoneEnabled(true);
                configManager.setRemovalAction(PluginConfig.RemovalAction.NOTIFY);
                engine.clearActiveBlocksAndLockdowns();
                if (listener != null) listener.refreshCache();
                messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getRedstoneNotify());
                logManager.logToFile("COMMAND", sender.getName() + " set redstone protection to NOTIFY", null);
            }
            default -> messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getUsageRedstone());
        }
        return 1;
    }

    private int sendAlertsStatus(CommandSender sender) {
        String raw = messageManager.getMessagesConfig().getCommands().getAlertsStatus();
        messageManager.sendIfNotBlank(sender, raw, Map.of(
                "status", configManager.isAlertsEnabled() ? "<color:#00E262>ON" : "<color:#E20000>OFF",
                "tps_lag", configManager.isNotifyOnTpsLag() ? "<color:#00E262>ON" : "<color:#E20000>OFF",
                "only_lag", configManager.isOnlyWhenLagging() ? "<color:#E2B700>ON" : "<color:#A0A0A0>OFF"
        ));
        return 1;
    }

    private int setAlertsMode(CommandSender sender, boolean enabled) {
        configManager.setAlertsEnabled(enabled);
        String raw = enabled
                ? messageManager.getMessagesConfig().getCommands().getAlertsOn()
                : messageManager.getMessagesConfig().getCommands().getAlertsOff();
        messageManager.sendIfNotBlank(sender, raw);
        logManager.logToFile("COMMAND", sender.getName() + (enabled ? " enabled" : " disabled") + " admin alerts", null);
        return 1;
    }

    private int toggleInspect(CommandSender sender, Integer seconds) {
        if (!(sender instanceof Player player)) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getMessages().getPlayerOnly());
            return 1;
        }
        plugin.getInspectorService().toggle(player, seconds);
        return 1;
    }

    private int runClear(ClearLaggManager.ClearType type) {
        plugin.getClearLaggManager().clearEntitiesAsync(type);
        return 1;
    }

    private int sendClearTimer(CommandSender sender) {
        ClearLaggManager clm = plugin.getClearLaggManager();
        PluginConfig.ClearLaggConfig cfg = configManager.getPluginConfig().getClearlagg();
        if (!cfg.isEnabled() || cfg.getIntervalSeconds() <= 0) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getClearlagg().getTimerDisabled());
        } else {
            String state = clm.isPaused() ? "<color:#E2B700>Paused" : "<color:#00E262>Running";
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getClearlagg().getTimerStatus(), Map.of(
                    "seconds", String.valueOf(clm.getSecondsUntilClear()),
                    "state", state
            ));
        }
        return 1;
    }

    private int sendClearCounts(CommandSender sender) {
        ClearLaggManager.EntityCounts counts = plugin.getClearLaggManager().countEntities();
        String raw = messageManager.getMessagesConfig().getClearlagg().getCount();
        messageManager.sendIfNotBlank(sender, raw, Map.of(
                "items", String.valueOf(counts.items),
                "projectiles", String.valueOf(counts.projectiles),
                "monsters", String.valueOf(counts.monsters),
                "xp_orbs", String.valueOf(counts.xpOrbs),
                "vehicles", String.valueOf(counts.vehicles)
        ));
        return 1;
    }

    private void sendUnfreezeResult(CommandSender sender, int count) {
        if (count > 0) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getUnfreezeSuccess(),
                    Map.of("count", String.valueOf(count)));
        } else {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getUnfreezeNone());
        }
    }

    private void sendUnlockResult(CommandSender sender, int count) {
        if (count > 0) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getUnlockSuccess(),
                    Map.of("count", String.valueOf(count)));
        } else {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getUnlockNone());
        }
    }

    private int modifyWhitelistHere(CommandSender sender, boolean add) {
        if (!(sender instanceof Player player)) {
            messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getMessages().getPlayerOnly());
            return 1;
        }
        Chunk c = player.getLocation().getChunk();
        return modifyWhitelist(sender, c.getWorld().getName(), c.getX(), c.getZ(), add);
    }

    private int modifyWhitelist(CommandSender sender, String worldName, int chunkX, int chunkZ, boolean add) {
        String entry = worldName + ":" + chunkX + ":" + chunkZ;
        var cmds = messageManager.getMessagesConfig().getCommands();
        if (add) {
            boolean added = configManager.addWhitelistedChunk(worldName, chunkX, chunkZ);
            messageManager.sendIfNotBlank(sender, added ? cmds.getWhitelistAdded() : cmds.getWhitelistExists(),
                    Map.of("entry", entry));
        } else {
            boolean removed = configManager.removeWhitelistedChunk(worldName, chunkX, chunkZ);
            messageManager.sendIfNotBlank(sender, removed ? cmds.getWhitelistRemoved() : cmds.getWhitelistMissing(),
                    Map.of("entry", entry));
        }
        return 1;
    }

    private int sendWhitelistList(CommandSender sender) {
        List<String> list = configManager.getWhitelistedChunksList();
        var cmds = messageManager.getMessagesConfig().getCommands();
        String enabledTag = configManager.isWhitelistEnabled() ? "<color:#00E262>ON" : "<color:#E20000>OFF";
        messageManager.sendIfNotBlank(sender, cmds.getWhitelistListHeader(), Map.of(
                "enabled", enabledTag,
                "count", String.valueOf(list.size())
        ));
        if (list.isEmpty()) {
            messageManager.sendIfNotBlank(sender, cmds.getWhitelistListEmpty());
            return 1;
        }
        for (String entry : list) {
            messageManager.sendIfNotBlank(sender, cmds.getWhitelistListRow(), Map.of("entry", entry));
        }
        return 1;
    }

    private void sendStats(CommandSender sender) {
        int perfNum = engine.getAdaptiveMonitor().getPerformancePercent();
        String perf = perfNum < 0 ? "N/A" : String.valueOf(perfNum);
        var stats = engine.getStats();

        String raw = messageManager.getMessagesConfig().getCommands().getStats();
        messageManager.sendIfNotBlank(sender, raw, Map.of(
                "chunks", String.valueOf(engine.getChunksMonitored()),
                "blocks", String.valueOf(engine.getBlocksMonitored()),
                "freezes", String.valueOf(engine.getActiveFreezeCount()),
                "lockdowns", String.valueOf(engine.getActiveLockdownCount()),
                "total_removals", String.valueOf(stats.getTotalRemovals()),
                "today_removals", String.valueOf(stats.getRemovalsToday()),
                "entities_cleared", String.valueOf(stats.getTotalEntitiesCleared()),
                "performance", perf
        ));
    }

    private void sendLogsInfo(CommandSender sender) {
        String folderPath = logManager.getLogsFolder() != null
                ? logManager.getLogsFolder().getAbsolutePath()
                : new File(plugin.getDataFolder(), "logs").getAbsolutePath();
        String status = logManager.isEnabled() ? "<color:#00E262>Enabled" : "<color:#E20000>Disabled";
        messageManager.sendIfNotBlank(sender, messageManager.getMessagesConfig().getCommands().getLogsInfo(), Map.of(
                "status", status,
                "folder", folderPath
        ));
    }

    private void sendLatestLogInfo(CommandSender sender) {
        var cmds = messageManager.getMessagesConfig().getCommands();
        File logsFolder = logManager.getLogsFolder();
        if (logsFolder == null || !logsFolder.exists()) {
            messageManager.sendIfNotBlank(sender, cmds.getLogsNoFolder());
            return;
        }
        File latest = logManager.getLatestLogFile();
        if (latest == null) {
            messageManager.sendIfNotBlank(sender, cmds.getLogsEmpty());
            return;
        }
        messageManager.sendIfNotBlank(sender, cmds.getLogsFileInfo(), Map.of(
                "filename", latest.getName(),
                "size", formatFileSize(latest.length()),
                "modified", DATE_TIME_FMT.format(Instant.ofEpochMilli(latest.lastModified())),
                "path", latest.getAbsolutePath()
        ));
        logManager.logToFile("COMMAND", sender.getName() + " viewed log file info: " + latest.getName(), null);
    }

    private void sendLogsTail(CommandSender sender, int lines) {
        var cmds = messageManager.getMessagesConfig().getCommands();
        List<String> entries = logManager.readTail(lines);
        if (entries.isEmpty()) {
            messageManager.sendIfNotBlank(sender, cmds.getLogsTailEmpty());
            return;
        }
        messageManager.sendIfNotBlank(sender, cmds.getLogsTailHeader(), Map.of("count", String.valueOf(entries.size())));
        for (String line : entries) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(line,
                    net.kyori.adventure.text.format.TextColor.color(0xA0A0A0)));
        }
    }

    private void sendHelp(CommandSender sender) {
        String raw = messageManager.getMessagesConfig().getCommands().getHelp();
        messageManager.sendIfNotBlank(sender, raw, Map.of("version", plugin.getPluginMeta().getVersion()));
    }

    private CompletableFuture<Suggestions> suggestSnapshotIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (SnapshotRecord snap : plugin.getSnapshotService().getSnapshotsNewestFirst()) {
            if (snap.id.toLowerCase().startsWith(remaining)) {
                builder.suggest(snap.id);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestWorlds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (World w : Bukkit.getWorlds()) {
            if (w.getName().toLowerCase().startsWith(remaining)) {
                builder.suggest(w.getName());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestStrings(SuggestionsBuilder builder, List<String> options) {
        String remaining = builder.getRemainingLowerCase();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(remaining)) {
                builder.suggest(opt);
            }
        }
        return builder.buildFuture();
    }

    private static boolean hasPerm(CommandSender sender, String perm) {
        return sender.isOp() || sender.hasPermission(perm);
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
