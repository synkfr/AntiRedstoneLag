package org.ayosynk.antiRedstoneLag.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.annotation.Header;
import eu.okaeri.configs.annotation.Names;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.NameModifier;

@Header({
    "AntiRedstoneLag Messages",
    "Supports & color codes, MiniMessage tags, and &#RRGGBB hex colors"
})
@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class MessagesConfig extends OkaeriConfig {

    @CustomKey("alerts")
    private AlertsSection alerts = new AlertsSection();

    @CustomKey("commands")
    private CommandsSection commands = new CommandsSection();

    @CustomKey("messages")
    private MessagesSection messages = new MessagesSection();

    public static class AlertsSection extends OkaeriConfig {
        @CustomKey("redstone-removed")
        private String redstoneRemoved = "&#FF6B6B┌─ &c⚠ Lag Machine Detected &7─┐\n" +
                "&7│ &6Coordinates: &e{x}, {y}, {z} &7│\n" +
                "&7│ &6World: &e{world} &7│\n" +
                "&7│ &6Block: &e{material} &7│\n" +
                "&7│ &6Chunk Updates: &e{chunk_count} &7│ &6Block Updates: &e{block_count} &7│\n" +
                "&#FF6B6B└─ &7Removed for server performance ─┘";

        @CustomKey("redstone-warning")
        private String redstoneWarning = "&#FFD93D⚠ &eWarning: &7Redstone activity at &e{x}, {y}, {z} &7is at &c{percent}% &7of threshold!";

        public String getRedstoneRemoved() {
            return redstoneRemoved;
        }

        public String getRedstoneWarning() {
            return redstoneWarning;
        }
    }

    public static class CommandsSection extends OkaeriConfig {
        @CustomKey("reload-success")
        private String reloadSuccess = "&#4ECDC4✓ &aConfiguration and messages reloaded successfully!";

        @CustomKey("stats")
        private String stats = "&#4ECDC4┌─ &bAntiRedstoneLag Statistics &7─┐\n" +
                "&7Chunks monitored: &e{chunks}\n" +
                "&7Blocks monitored: &e{blocks}\n" +
                "&7Total removals: &e{total_removals}\n" +
                "&7Removals today: &e{today_removals}\n" +
                "&7Performance: &a{performance}%\n" +
                "&#4ECDC4└────────────────────────┘";

        @CustomKey("logs-info")
        private String logsInfo = "&#4ECDC4Logging System Information:\n" +
                "&7Status: {status}\n" +
                "&7Log folder: &e{folder}\n" +
                "&7Use &6/arl logs download &7to get latest log file";

        @CustomKey("logs-download")
        private String logsDownload = "&#4ECDC4✓ &aLog file has been prepared for download!";

        @CustomKey("logs-no-folder")
        private String logsNoFolder = "&#FF6B6B✗ &cLogs folder not found!";

        @CustomKey("logs-empty")
        private String logsEmpty = "&#FF6B6B✗ &cNo log files found!";

        @CustomKey("logs-file-info")
        private String logsFileInfo = "&#4ECDC4┌─ &bLatest Log File &7─┐\n" +
                "&7File: &e{filename}\n" +
                "&7Size: &e{size}\n" +
                "&7Modified: &e{modified}\n" +
                "&7Path: &e{path}\n" +
                "&#4ECDC4└────────────────────┘";

        @CustomKey("no-permission")
        private String noPermission = "&#FF6B6B✗ &cYou don't have permission to use this command!";

        @CustomKey("help")
        private String help = "&#FFD93D┌─ &6AntiRedstoneLag &7v{version} ──────┐\n" +
                "&6/arl reload &7- Reload configuration and messages\n" +
                "&6/arl stats &7- View plugin statistics\n" +
                "&6/arl logs &7- View or download logs\n" +
                "&6/arl help &7- Show this help message\n" +
                "&#FFD93D└────────────────────────────┘";

        public String getReloadSuccess() {
            return reloadSuccess;
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

        public String getNoPermission() {
            return noPermission;
        }

        public String getHelp() {
            return help;
        }
    }

    public static class MessagesSection extends OkaeriConfig {
        @CustomKey("prefix")
        private String prefix = "&#4ECDC4[ARL]&r ";

        @CustomKey("enabled")
        private String enabled = "&#4ECDC4✓ &aAntiRedstoneLag v{version} has been enabled!";

        @CustomKey("disabled")
        private String disabled = "&#FF6B6B✗ &cAntiRedstoneLag has been disabled!";

        @CustomKey("update-available-console")
        private String updateAvailableConsole = "A new version is available: v{version} (current: v{current})";

        @CustomKey("update-download-console")
        private String updateDownloadConsole = "Download at: {url}";

        @CustomKey("update-available-player")
        private String updateAvailablePlayer = "<gold>[AntiRedstoneLag] <yellow>A new version is available: <green>v{version}";

        @CustomKey("update-download-player")
        private String updateDownloadPlayer = "<gold>[AntiRedstoneLag] <gray>Download at: <aqua><click:open_url:'{url}'>{url}</click>";

        public String getPrefix() {
            return prefix;
        }

        public String getEnabled() {
            return enabled;
        }

        public String getDisabled() {
            return disabled;
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

    public AlertsSection getAlerts() {
        return alerts;
    }

    public CommandsSection getCommands() {
        return commands;
    }

    public MessagesSection getMessages() {
        return messages;
    }
}
