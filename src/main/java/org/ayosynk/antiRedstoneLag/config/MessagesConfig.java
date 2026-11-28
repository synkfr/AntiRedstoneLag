package org.ayosynk.antiRedstoneLag.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.annotation.Header;

@Header({
        "AntiRedstoneLag v2 Messages",
        "Clean, minimal MiniMessage hex palette"
})
public class MessagesConfig extends OkaeriConfig {

    @CustomKey("messages-version")
    private int messagesVersion = 2;

    @CustomKey("messages")
    private MessagesSection messages = new MessagesSection();

    @CustomKey("alerts")
    private AlertsSection alerts = new AlertsSection();

    @CustomKey("clearlagg")
    private ClearLaggSection clearlagg = new ClearLaggSection();

    @CustomKey("commands")
    private CommandsSection commands = new CommandsSection();

    @CustomKey("gui")
    private GuiSection gui = new GuiSection();

    public int getMessagesVersion() {
        return messagesVersion;
    }

    public void setMessagesVersion(int messagesVersion) {
        this.messagesVersion = messagesVersion;
    }

    public MessagesSection getMessages() {
        return messages;
    }

    public AlertsSection getAlerts() {
        return alerts;
    }

    public ClearLaggSection getClearlagg() {
        return clearlagg;
    }

    public CommandsSection getCommands() {
        return commands;
    }

    public GuiSection getGui() {
        return gui;
    }

    public static class MessagesSection extends OkaeriConfig {
        @CustomKey("prefix")
        private String prefix = "<color:#A0A0A0>[<color:#00E262>AntiRedstoneLag<color:#A0A0A0>] ";

        @CustomKey("enabled")
        private String enabled = "{prefix}<color:#00E262>Enabled v<color:#FFFFFF>{version}<color:#00E262>.";

        @CustomKey("disabled")
        private String disabled = "{prefix}<color:#E20000>Plugin disabled.";

        @CustomKey("player-only")
        private String playerOnly = "{prefix}<color:#E20000>You must be a player in-game to run this action.";

        @CustomKey("update-available-console")
        private String updateAvailableConsole = "A new version is available: v{version} (current: v{current})";

        @CustomKey("update-download-console")
        private String updateDownloadConsole = "Download at: {url}";

        @CustomKey("update-available-player")
        private String updateAvailablePlayer = "{prefix}<color:#E2B700>Update available: <color:#00E262>v{version} <color:#A0A0A0>(current: <color:#FFFFFF>v{current}<color:#A0A0A0>)";

        @CustomKey("update-download-player")
        private String updateDownloadPlayer = "{prefix}<color:#A0A0A0>Download: <color:#00E262><underlined><click:open_url:'{url}'>{url}</click></underlined>";

        public String getPrefix() {
            return prefix;
        }

        public String getEnabled() {
            return enabled;
        }

        public String getDisabled() {
            return disabled;
        }

        public String getPlayerOnly() {
            return playerOnly;
        }

        public String getUpdateAvailableConsole() {
            return updateAvailableConsole;
        }

        public String getUpdateDownloadConsole() {
            return updateDownloadConsole;
        }

        public String getUpdateAvailablePlayer() {
            return updateAvailablePlayer;
        }

        public String getUpdateDownloadPlayer() {
            return updateDownloadPlayer;
        }
    }

    public static class AlertsSection extends OkaeriConfig {
        @CustomKey("chunk-lockdown-admin")
        private String chunkLockdownAdmin = "{prefix}<bold><color:#E20000>CHUNK LOCKDOWN</bold> <color:#A0A0A0>World: <color:#FFFFFF>{world} <color:#A0A0A0>Chunk: <color:#FFFFFF>{chunkX}, {chunkZ} <color:#A0A0A0>Duration: <color:#E2B700>{duration}s";

        @CustomKey("chunk-lockdown-local")
        private String chunkLockdownLocal = "{prefix}<color:#E20000>Redstone in this chunk is locked for <color:#E2B700>{duration}s <color:#E20000>due to severe lag.";

        @CustomKey("clock-frozen")
        private String clockFrozen = "{prefix}<color:#E2B700>Clock at <color:#FFFFFF>{x}, {y}, {z} <color:#E2B700>paused for <color:#FFFFFF>{duration}s <color:#A0A0A0>(high frequency).";

        @CustomKey("clock-resumed")
        private String clockResumed = "{prefix}<color:#00E262>Clock at <color:#FFFFFF>{x}, {y}, {z} <color:#00E262>has resumed operation.";

        @CustomKey("redstone-notify")
        private String redstoneNotify = "{prefix}<color:#E2B700>High activity at <color:#FFFFFF>{x}, {y}, {z} <color:#A0A0A0>({world}) | Block: <color:#FFFFFF>{material} <color:#A0A0A0>| Chunk: <color:#FFFFFF>{chunk_count} <color:#A0A0A0>Block: <color:#FFFFFF>{block_count} <color:#A0A0A0>| Culprit: <color:#00E262>{culprit}";

        @CustomKey("tps-lag-culprit")
        private String tpsLagCulprit = "{prefix}<bold><color:#E20000>CRITICAL TPS LAG</bold> <color:#A0A0A0>TPS: <color:#E20000>{tps} <color:#A0A0A0>MSPT: <color:#E20000>{mspt}ms <color:#A0A0A0>| Location: <color:#FFFFFF>{x}, {y}, {z} ({world}) <color:#A0A0A0>| Culprit: <color:#00E262>{culprit}";

        @CustomKey("component-paused")
        private String componentPaused = "{prefix}<color:#E2B700>This component is paused for <color:#FFFFFF>{duration}s <color:#E2B700>due to high frequency.";

        @CustomKey("chunk-locked-interact")
        private String chunkLockedInteract = "{prefix}<color:#E20000>Redstone in this chunk is locked for <color:#E2B700>{duration}s <color:#E20000>due to lag.";

        @CustomKey("redstone-removed")
        private String redstoneRemoved = "<color:#E2B700>Lag Machine Mitigated\n" +
                "<color:#A0A0A0>Location: <color:#FFFFFF>{x}, {y}, {z} ({world})\n" +
                "<color:#A0A0A0>Block: <color:#FFFFFF>{material} <color:#A0A0A0>| Action: <color:#E20000>{action}\n" +
                "<color:#A0A0A0>Updates: <color:#FFFFFF>{chunk_count} chunk <color:#A0A0A0>/ <color:#FFFFFF>{block_count} block";

        @CustomKey("redstone-warning")
        private String redstoneWarning = "{prefix}<color:#E2B700>Warning: Redstone at <color:#FFFFFF>{x}, {y}, {z} <color:#E2B700>is at <color:#E20000>{percent}% <color:#E2B700>of threshold.";

        @CustomKey("minecart-cluster")
        private String minecartCluster = "{prefix}<color:#E2B700>Cleared <color:#FFFFFF>{count} <color:#E2B700>stacked minecarts at <color:#FFFFFF>{x}, {y}, {z} <color:#A0A0A0>({world}).";

        @CustomKey("hopper-throttled")
        private String hopperThrottled = "{prefix}<color:#E2B700>Hopper loop throttled at <color:#FFFFFF>{x}, {y}, {z} <color:#A0A0A0>({world}).";

        @CustomKey("crafter-throttled")
        private String crafterThrottled = "{prefix}<color:#E2B700>Crafter loop throttled at <color:#FFFFFF>{x}, {y}, {z} <color:#A0A0A0>({world}).";

        public String getChunkLockdownAdmin() {
            return chunkLockdownAdmin;
        }

        public String getChunkLockdownLocal() {
            return chunkLockdownLocal;
        }

        public String getClockFrozen() {
            return clockFrozen;
        }

        public String getClockResumed() {
            return clockResumed;
        }

        public String getRedstoneNotify() {
            return redstoneNotify;
        }

        public String getTpsLagCulprit() {
            return tpsLagCulprit;
        }

        public String getComponentPaused() {
            return componentPaused;
        }

        public String getChunkLockedInteract() {
            return chunkLockedInteract;
        }

        public String getRedstoneRemoved() {
            return redstoneRemoved;
        }

        public String getRedstoneWarning() {
            return redstoneWarning;
        }

        public String getMinecartCluster() {
            return minecartCluster;
        }

        public String getHopperThrottled() {
            return hopperThrottled;
        }

        public String getCrafterThrottled() {
            return crafterThrottled;
        }
    }

    public static class ClearLaggSection extends OkaeriConfig {
        @CustomKey("warning-chat")
        private String warningChat = "{prefix}<color:#E2B700>Ground items and lag entities will be cleared in <color:#FFFFFF>{seconds}s<color:#E2B700>.";

        @CustomKey("warning-actionbar")
        private String warningActionbar = "<color:#E2B700>⚠ Clearing ground items in <color:#FFFFFF>{seconds}s";

        @CustomKey("warning-title")
        private String warningTitle = "<color:#E2B700>ClearLagg";

        @CustomKey("warning-subtitle")
        private String warningSubtitle = "<color:#A0A0A0>Clearing items in <color:#FFFFFF>{seconds}s";

        @CustomKey("cleared")
        private String cleared = "{prefix}<color:#00E262>Cleared <color:#FFFFFF>{count} <color:#00E262>items and lag entities across all worlds.";

        @CustomKey("cancelled")
        private String cancelled = "{prefix}<color:#E2B700>Scheduled entity clear has been cancelled.";

        @CustomKey("paused")
        private String paused = "{prefix}<color:#E2B700>Automatic ClearLagg timer paused.";

        @CustomKey("resumed")
        private String resumed = "{prefix}<color:#00E262>Automatic ClearLagg timer resumed.";

        @CustomKey("timer-disabled")
        private String timerDisabled = "{prefix}<color:#E20000>Automatic ClearLagg timer is currently disabled.";

        @CustomKey("timer-status")
        private String timerStatus = "{prefix}<color:#A0A0A0>Next scheduled clear in: <color:#FFFFFF>{seconds}s <color:#A0A0A0>({state}<color:#A0A0A0>)";

        @CustomKey("count")
        private String count = "<color:#E2B700>ClearLagg Entity Count\n" +
                "<color:#A0A0A0>Ground items: <color:#FFFFFF>{items}\n" +
                "<color:#A0A0A0>Projectiles: <color:#FFFFFF>{projectiles}\n" +
                "<color:#A0A0A0>Monsters: <color:#FFFFFF>{monsters}\n" +
                "<color:#A0A0A0>XP Orbs: <color:#FFFFFF>{xp_orbs}\n" +
                "<color:#A0A0A0>Vehicles: <color:#FFFFFF>{vehicles}";

        public String getWarningChat() {
            return warningChat;
        }

        public String getWarningActionbar() {
            return warningActionbar;
        }

        public String getWarningTitle() {
            return warningTitle;
        }

        public String getWarningSubtitle() {
            return warningSubtitle;
        }

        public String getCleared() {
            return cleared;
        }

        public String getCancelled() {
            return cancelled;
        }

        public String getPaused() {
            return paused;
        }

        public String getResumed() {
            return resumed;
        }

        public String getTimerDisabled() {
            return timerDisabled;
        }

        public String getTimerStatus() {
            return timerStatus;
        }

        public String getCount() {
            return count;
        }
    }

    public static class CommandsSection extends OkaeriConfig {
        @CustomKey("no-permission")
        private String noPermission = "{prefix}<color:#E20000>You do not have permission to execute this command.";

        @CustomKey("reload-success")
        private String reloadSuccess = "{prefix}<color:#00E262>Configuration, messages, and detectors reloaded.";

        @CustomKey("usage-redstone")
        private String usageRedstone = "{prefix}<color:#E20000>Invalid usage. Use: <color:#FFFFFF>/arl redstone <on|off|notify|status>";

        @CustomKey("usage-alerts")
        private String usageAlerts = "{prefix}<color:#E20000>Invalid usage. Use: <color:#FFFFFF>/arl alerts <on|off|status>";

        @CustomKey("usage-unfreeze")
        private String usageUnfreeze = "{prefix}<color:#E20000>Invalid usage. Use: <color:#FFFFFF>/arl unfreeze <all|here>";

        @CustomKey("usage-unlock")
        private String usageUnlock = "{prefix}<color:#E20000>Invalid usage. Use: <color:#FFFFFF>/arl unlock <all|here>";

        @CustomKey("usage-whitelist")
        private String usageWhitelist = "{prefix}<color:#E20000>Invalid usage. Use: <color:#FFFFFF>/arl whitelist <add|remove|list> [world chunkX chunkZ]";

        @CustomKey("redstone-on")
        private String redstoneOn = "{prefix}<color:#00E262>You have enabled redstone protection <color:#A0A0A0>(action: <color:#FFFFFF>{action}<color:#A0A0A0>).";

        @CustomKey("redstone-off")
        private String redstoneOff = "{prefix}<color:#E2B700>You have disabled redstone protection. Active freezes and lockdowns were cleared.";

        @CustomKey("redstone-notify")
        private String redstoneNotify = "{prefix}<color:#00E262>Redstone protection set to notification-only mode.";

        @CustomKey("redstone-status")
        private String redstoneStatus = "{prefix}<color:#A0A0A0>Protection: {status} <color:#A0A0A0>| Action: <color:#FFFFFF>{action} <color:#A0A0A0>| Alerts: {alerts}";

        @CustomKey("alerts-on")
        private String alertsOn = "{prefix}<color:#00E262>You have enabled staff redstone and lag alerts.";

        @CustomKey("alerts-off")
        private String alertsOff = "{prefix}<color:#E2B700>You have muted staff redstone and lag alerts.";

        @CustomKey("alerts-status")
        private String alertsStatus = "{prefix}<color:#A0A0A0>Alerts: {status} <color:#A0A0A0>| TPS Lag: {tps_lag} <color:#A0A0A0>| Only When Lagging: {only_lag}";

        @CustomKey("stats")
        private String stats = "<color:#E2B700>AntiRedstoneLag Statistics\n" +
                "<color:#A0A0A0>Chunks monitored: <color:#FFFFFF>{chunks}\n" +
                "<color:#A0A0A0>Blocks monitored: <color:#FFFFFF>{blocks}\n" +
                "<color:#A0A0A0>Active freezes: <color:#E2B700>{freezes} <color:#A0A0A0>| Active lockdowns: <color:#E20000>{lockdowns}\n" +
                "<color:#A0A0A0>Total mitigations: <color:#FFFFFF>{total_removals} <color:#A0A0A0>(Today: <color:#FFFFFF>{today_removals}<color:#A0A0A0>)\n" +
                "<color:#A0A0A0>Entities cleared: <color:#FFFFFF>{entities_cleared}\n" +
                "<color:#A0A0A0>Performance: <color:#00E262>{performance}%";

        @CustomKey("logs-info")
        private String logsInfo = "<color:#E2B700>Logging System\n" +
                "<color:#A0A0A0>Status: {status}\n" +
                "<color:#A0A0A0>Folder: <color:#FFFFFF>{folder}\n" +
                "<color:#A0A0A0>Use <color:#00E262><click:run_command:'/arl logs download'>/arl logs latest</click> <color:#A0A0A0>or <color:#00E262><click:run_command:'/arl logs tail 15'>/arl logs tail</click>";

        @CustomKey("logs-download")
        private String logsDownload = "{prefix}<color:#00E262>Latest log details displayed.";

        @CustomKey("logs-no-folder")
        private String logsNoFolder = "{prefix}<color:#E20000>Logs folder does not exist.";

        @CustomKey("logs-empty")
        private String logsEmpty = "{prefix}<color:#E20000>No log files found.";

        @CustomKey("logs-file-info")
        private String logsFileInfo = "<color:#E2B700>Latest Log File\n" +
                "<color:#A0A0A0>File: <color:#FFFFFF>{filename}\n" +
                "<color:#A0A0A0>Size: <color:#FFFFFF>{size}\n" +
                "<color:#A0A0A0>Modified: <color:#FFFFFF>{modified}\n" +
                "<color:#A0A0A0>Path: <color:#FFFFFF>{path}";

        @CustomKey("logs-tail-header")
        private String logsTailHeader = "<color:#E2B700>Recent Log Entries <color:#A0A0A0>({count} lines)";

        @CustomKey("logs-tail-empty")
        private String logsTailEmpty = "{prefix}<color:#A0A0A0>No recent log lines available.";

        @CustomKey("hotspots-header")
        private String hotspotsHeader = "<color:#E2B700>Redstone Hotspots <color:#A0A0A0>({count} clusters)";

        @CustomKey("hotspots-row")
        private String hotspotsRow = "<color:#E2B700>#{rank} <color:#FFFFFF>{world} <color:#A0A0A0>[<color:#FFFFFF>{x}, ~, {z}<color:#A0A0A0>] | Activity: <color:#00E262>{activity} <color:#A0A0A0>| Chunks: <color:#FFFFFF>{chunks} <color:#A0A0A0>| Status: {state}";

        @CustomKey("hotspots-row-hover")
        private String hotspotsRowHover = "<color:#A0A0A0>World: <color:#FFFFFF>{world}\n<color:#A0A0A0>Center: <color:#FFFFFF>{x}, ~, {z}\n<color:#A0A0A0>Updates: <color:#FFFFFF>{activity}\n<color:#A0A0A0>Chunks: <color:#FFFFFF>{chunks}\n<color:#A0A0A0>State: {state}\n<color:#00E262>Click to suggest teleport command";

        @CustomKey("hotspots-none")
        private String hotspotsNone = "{prefix}<color:#00E262>No active redstone hotspots detected.";

        @CustomKey("hotspots-exported")
        private String hotspotsExported = "{prefix}<color:#00E262>Exported hotspot map to <color:#FFFFFF>{path}<color:#00E262>.";

        @CustomKey("hotspots-export-empty")
        private String hotspotsExportEmpty = "{prefix}<color:#E20000>Cannot export hotspots because no redstone activity is tracked.";

        @CustomKey("hotspots-export-failed")
        private String hotspotsExportFailed = "{prefix}<color:#E20000>Failed to write hotspot export file. Check server logs.";

        @CustomKey("inspect-enabled")
        private String inspectEnabled = "{prefix}<color:#00E262>Visual redstone inspector enabled for <color:#FFFFFF>{duration}s<color:#00E262>.";

        @CustomKey("inspect-disabled")
        private String inspectDisabled = "{prefix}<color:#E2B700>Visual redstone inspector disabled.";

        @CustomKey("inspect-bossbar")
        private String inspectBossbar = "<color:#E2B700>Inspector <color:#A0A0A0>| Chunk: <color:#FFFFFF>{ups} UPS <color:#A0A0A0>| MSPT: <color:#FFFFFF>{mspt}ms <color:#A0A0A0>| TPS: <color:#00E262>{tps}";

        @CustomKey("inspect-bossbar-folia")
        private String inspectBossbarFolia = "<color:#E2B700>Inspector <color:#A0A0A0>| Chunk: <color:#FFFFFF>{ups} UPS <color:#A0A0A0>| Ratio: <color:#FFFFFF>{percent}% <color:#A0A0A0>| <color:#00E262>Folia";

        @CustomKey("inspect-actionbar")
        private String inspectActionbar = "<color:#00E262>★ <color:#A0A0A0>Chunk: <color:#FFFFFF>{ups}/{threshold} UPS <color:#A0A0A0>| Frozen nearby: <color:#E2B700>{frozen}";

        @CustomKey("inspect-actionbar-locked")
        private String inspectActionbarLocked = "<color:#E20000>⚠ CHUNK LOCKED <color:#A0A0A0>| Remaining: <color:#E2B700>{remaining}s <color:#A0A0A0>| Chunk: <color:#FFFFFF>{ups} UPS";

        @CustomKey("snapshot-captured")
        private String snapshotCaptured = "{prefix}<color:#E2B700>Captured snapshot <color:#FFFFFF>{id} <color:#A0A0A0>for <color:#FFFFFF>{material} <color:#A0A0A0>at <color:#FFFFFF>{x}, {y}, {z} <color:#A0A0A0>(Culprit: <color:#00E262>{culprit}<color:#A0A0A0>)";

        @CustomKey("snapshot-list-header")
        private String snapshotListHeader = "<color:#E2B700>Forensic Snapshots <color:#A0A0A0>({count})";

        @CustomKey("snapshot-list-row")
        private String snapshotListRow = "<color:#E2B700>{id} <color:#A0A0A0>{time} | <color:#FFFFFF>{world} [{x}, {y}, {z}] <color:#A0A0A0>| <color:#E20000>{material} <color:#A0A0A0>| Culprit: <color:#00E262>{culprit}";

        @CustomKey("snapshot-view")
        private String snapshotView = "<color:#E2B700>Forensic Report: <color:#FFFFFF>{id}\n" +
                "<color:#A0A0A0>Captured: <color:#FFFFFF>{date}\n" +
                "<color:#A0A0A0>Location: <color:#FFFFFF>{x}, {y}, {z} ({world})\n" +
                "<color:#A0A0A0>Culprit: <color:#00E262>{culprit} <color:#A0A0A0>({uuid})\n" +
                "<color:#A0A0A0>Trigger: <color:#E20000>{material} <color:#A0A0A0>| Updates: <color:#FFFFFF>{chunk_count} chunk / {block_count} block\n" +
                "<color:#A0A0A0>Reason: <color:#FFFFFF>{reason}\n" +
                "<color:#A0A0A0>Components in cluster: <color:#FFFFFF>{total}";

        @CustomKey("snapshot-view-component")
        private String snapshotViewComponent = "  <color:#A0A0A0>{material}: <color:#FFFFFF>{count}";

        @CustomKey("snapshot-empty")
        private String snapshotEmpty = "{prefix}<color:#A0A0A0>No forensic snapshots recorded.";

        @CustomKey("snapshot-cleared")
        private String snapshotCleared = "{prefix}<color:#00E262>All forensic snapshots cleared.";

        @CustomKey("snapshot-deleted")
        private String snapshotDeleted = "{prefix}<color:#00E262>Deleted snapshot <color:#FFFFFF>{id}<color:#00E262>.";

        @CustomKey("snapshot-highlighted")
        private String snapshotHighlighted = "{prefix}<color:#00E262>Highlighting snapshot <color:#FFFFFF>{id} <color:#00E262>for 15s.";

        @CustomKey("snapshot-not-found")
        private String snapshotNotFound = "{prefix}<color:#E20000>Snapshot not found: <color:#FFFFFF>{id}";

        @CustomKey("snapshot-tp-success")
        private String snapshotTpSuccess = "{prefix}<color:#00E262>Teleported to snapshot <color:#FFFFFF>{id} <color:#A0A0A0>at <color:#FFFFFF>{x}, {y}, {z} ({world})<color:#00E262>.";

        @CustomKey("snapshot-world-unloaded")
        private String snapshotWorldUnloaded = "{prefix}<color:#E20000>World <color:#FFFFFF>{world} <color:#E20000>is not currently loaded.";

        @CustomKey("unfreeze-success")
        private String unfreezeSuccess = "{prefix}<color:#00E262>Unfroze <color:#FFFFFF>{count} <color:#00E262>redstone component(s).";

        @CustomKey("unfreeze-none")
        private String unfreezeNone = "{prefix}<color:#A0A0A0>No frozen components found to unfreeze.";

        @CustomKey("unlock-success")
        private String unlockSuccess = "{prefix}<color:#00E262>Unlocked <color:#FFFFFF>{count} <color:#00E262>locked chunk(s).";

        @CustomKey("unlock-none")
        private String unlockNone = "{prefix}<color:#A0A0A0>No locked chunks found to unlock.";

        @CustomKey("whitelist-added")
        private String whitelistAdded = "{prefix}<color:#00E262>Added chunk <color:#FFFFFF>{entry} <color:#00E262>to whitelist.";

        @CustomKey("whitelist-exists")
        private String whitelistExists = "{prefix}<color:#E2B700>Chunk <color:#FFFFFF>{entry} <color:#E2B700>is already whitelisted.";

        @CustomKey("whitelist-removed")
        private String whitelistRemoved = "{prefix}<color:#00E262>Removed chunk <color:#FFFFFF>{entry} <color:#00E262>from whitelist.";

        @CustomKey("whitelist-missing")
        private String whitelistMissing = "{prefix}<color:#E20000>Chunk <color:#FFFFFF>{entry} <color:#E20000>is not in the whitelist.";

        @CustomKey("whitelist-list-header")
        private String whitelistListHeader = "<color:#E2B700>Whitelisted Chunks <color:#A0A0A0>(Enabled: {enabled}<color:#A0A0A0>, Count: <color:#FFFFFF>{count}<color:#A0A0A0>)";

        @CustomKey("whitelist-list-empty")
        private String whitelistListEmpty = "{prefix}<color:#A0A0A0>No whitelisted chunks configured.";

        @CustomKey("whitelist-list-row")
        private String whitelistListRow = "  <color:#A0A0A0>- <color:#FFFFFF>{entry}";

        @CustomKey("help")
        private String help = "<color:#E2B700>AntiRedstoneLag <color:#A0A0A0>v{version}\n" +
                "<color:#00E262>/arl gui <color:#A0A0A0>- Open staff control panel\n" +
                "<color:#00E262>/arl redstone <on|off|notify|status> <color:#A0A0A0>- Manage protection mode\n" +
                "<color:#00E262>/arl alerts <on|off|status> <color:#A0A0A0>- Manage staff lag alerts\n" +
                "<color:#00E262>/arl inspect [seconds] <color:#A0A0A0>- Toggle live in-world inspector\n" +
                "<color:#00E262>/arl hotspots [page|export] <color:#A0A0A0>- View or export active clusters\n" +
                "<color:#00E262>/arl snapshot [list|view|tp|highlight|delete|clear] <color:#A0A0A0>- Manage forensic snapshots\n" +
                "<color:#00E262>/arl clear [type|count|cancel|timer|pause|resume] <color:#A0A0A0>- Entity & item cleaner\n" +
                "<color:#00E262>/arl unfreeze <all|here> <color:#A0A0A0>- Unfreeze throttled clocks\n" +
                "<color:#00E262>/arl unlock <all|here> <color:#A0A0A0>- Unlock locked chunks\n" +
                "<color:#00E262>/arl whitelist <add|remove|list> [world cx cz] <color:#A0A0A0>- Edit chunk whitelist\n" +
                "<color:#00E262>/arl stats <color:#A0A0A0>- View plugin statistics\n" +
                "<color:#00E262>/arl logs [latest|tail] <color:#A0A0A0>- View log files and entries\n" +
                "<color:#00E262>/arl reload <color:#A0A0A0>- Reload config and messages";

        public String getNoPermission() {
            return noPermission;
        }

        public String getReloadSuccess() {
            return reloadSuccess;
        }

        public String getUsageRedstone() {
            return usageRedstone;
        }

        public String getUsageAlerts() {
            return usageAlerts;
        }

        public String getUsageUnfreeze() {
            return usageUnfreeze;
        }

        public String getUsageUnlock() {
            return usageUnlock;
        }

        public String getUsageWhitelist() {
            return usageWhitelist;
        }

        public String getRedstoneOn() {
            return redstoneOn;
        }

        public String getRedstoneOff() {
            return redstoneOff;
        }

        public String getRedstoneNotify() {
            return redstoneNotify;
        }

        public String getRedstoneStatus() {
            return redstoneStatus;
        }

        public String getAlertsOn() {
            return alertsOn;
        }

        public String getAlertsOff() {
            return alertsOff;
        }

        public String getAlertsStatus() {
            return alertsStatus;
        }

        public String getStats() {
            return stats;
        }

        public String getLogsInfo() {
            return logsInfo;
        }

        public String getLogsDownload() {
            return logsDownload;
        }

        public String getLogsNoFolder() {
            return logsNoFolder;
        }

        public String getLogsEmpty() {
            return logsEmpty;
        }

        public String getLogsFileInfo() {
            return logsFileInfo;
        }

        public String getLogsTailHeader() {
            return logsTailHeader;
        }

        public String getLogsTailEmpty() {
            return logsTailEmpty;
        }

        public String getHotspotsHeader() {
            return hotspotsHeader;
        }

        public String getHotspotsRow() {
            return hotspotsRow;
        }

        public String getHotspotsRowHover() {
            return hotspotsRowHover;
        }

        public String getHotspotsNone() {
            return hotspotsNone;
        }

        public String getHotspotsExported() {
            return hotspotsExported;
        }

        public String getHotspotsExportEmpty() {
            return hotspotsExportEmpty;
        }

        public String getHotspotsExportFailed() {
            return hotspotsExportFailed;
        }

        public String getInspectEnabled() {
            return inspectEnabled;
        }

        public String getInspectDisabled() {
            return inspectDisabled;
        }

        public String getInspectBossbar() {
            return inspectBossbar;
        }

        public String getInspectBossbarFolia() {
            return inspectBossbarFolia;
        }

        public String getInspectActionbar() {
            return inspectActionbar;
        }

        public String getInspectActionbarLocked() {
            return inspectActionbarLocked;
        }

        public String getSnapshotCaptured() {
            return snapshotCaptured;
        }

        public String getSnapshotListHeader() {
            return snapshotListHeader;
        }

        public String getSnapshotListRow() {
            return snapshotListRow;
        }

        public String getSnapshotView() {
            return snapshotView;
        }

        public String getSnapshotViewComponent() {
            return snapshotViewComponent;
        }

        public String getSnapshotEmpty() {
            return snapshotEmpty;
        }

        public String getSnapshotCleared() {
            return snapshotCleared;
        }

        public String getSnapshotDeleted() {
            return snapshotDeleted;
        }

        public String getSnapshotHighlighted() {
            return snapshotHighlighted;
        }

        public String getSnapshotNotFound() {
            return snapshotNotFound;
        }

        public String getSnapshotTpSuccess() {
            return snapshotTpSuccess;
        }

        public String getSnapshotWorldUnloaded() {
            return snapshotWorldUnloaded;
        }

        public String getUnfreezeSuccess() {
            return unfreezeSuccess;
        }

        public String getUnfreezeNone() {
            return unfreezeNone;
        }

        public String getUnlockSuccess() {
            return unlockSuccess;
        }

        public String getUnlockNone() {
            return unlockNone;
        }

        public String getWhitelistAdded() {
            return whitelistAdded;
        }

        public String getWhitelistExists() {
            return whitelistExists;
        }

        public String getWhitelistRemoved() {
            return whitelistRemoved;
        }

        public String getWhitelistMissing() {
            return whitelistMissing;
        }

        public String getWhitelistListHeader() {
            return whitelistListHeader;
        }

        public String getWhitelistListEmpty() {
            return whitelistListEmpty;
        }

        public String getWhitelistListRow() {
            return whitelistListRow;
        }

        public String getHelp() {
            return help;
        }
    }

    public static class GuiSection extends OkaeriConfig {
        @CustomKey("disabled")
        private String disabled = "{prefix}<color:#E20000>The Staff Inventory GUI is disabled in config.yml.";

        @CustomKey("titles")
        private TitlesSection titles = new TitlesSection();

        public String getDisabled() {
            return disabled;
        }

        public TitlesSection getTitles() {
            return titles;
        }

        public static class TitlesSection extends OkaeriConfig {
            @CustomKey("main")
            private String main = "<color:#1F1F1F>AntiRedstoneLag Staff Panel";

            @CustomKey("hotspots")
            private String hotspots = "<color:#1F1F1F>Redstone Hotspots ({page}/{pages})";

            @CustomKey("snapshots")
            private String snapshots = "<color:#1F1F1F>Forensic Snapshots ({page}/{pages})";

            @CustomKey("locked")
            private String locked = "<color:#1F1F1F>Frozen & Locked ({page}/{pages})";

            @CustomKey("clearlagg")
            private String clearlagg = "<color:#1F1F1F>ClearLagg Controls";

            public String getMain() {
                return main;
            }

            public String getHotspots() {
                return hotspots;
            }

            public String getSnapshots() {
                return snapshots;
            }

            public String getLocked() {
                return locked;
            }

            public String getClearlagg() {
                return clearlagg;
            }
        }
    }
}
