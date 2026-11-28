package org.ayosynk.antiRedstoneLag.listener;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.scheduler.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Checks Modrinth releases asynchronously on startup and notifies staff on join.
 */
public class UpdateChecker implements Listener {
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/";
    private static final String UPDATE_PERMISSION = "antiredstonelag.admin";

    private final AntiRedstoneLag plugin;
    private final Scheduler scheduler;
    private final String projectId;
    private final String projectUrl;
    private volatile String latestVersion;
    private volatile boolean updateAvailable = false;

    public UpdateChecker(AntiRedstoneLag plugin, Scheduler scheduler, String projectId, String projectUrl) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.projectId = projectId;
        this.projectUrl = projectUrl;
    }

    public void checkForUpdates() {
        checkForUpdates(version -> {
            String currentVersion = plugin.getPluginMeta().getVersion();
            if (isRemoteNewer(version, currentVersion)) {
                latestVersion = version;
                updateAvailable = true;
                var msgs = plugin.getMessageManager().getMessagesConfig().getMessages();
                String msg = msgs.getUpdateAvailableConsole()
                        .replace("{version}", version)
                        .replace("{current}", currentVersion);
                String download = msgs.getUpdateDownloadConsole()
                        .replace("{url}", projectUrl);
                plugin.getLogger().info(msg);
                plugin.getLogger().info(download);
            }
        });
    }

    public static boolean isRemoteNewer(String remote, String current) {
        if (remote == null || current == null) return false;
        String cleanRemote = stripPrefix(remote);
        String cleanCurrent = stripPrefix(current);

        String[] remoteBase = cleanRemote.split("-", 2);
        String[] currentBase = cleanCurrent.split("-", 2);

        String[] rParts = remoteBase[0].split("\\.");
        String[] cParts = currentBase[0].split("\\.");
        int len = Math.max(rParts.length, cParts.length);

        for (int i = 0; i < len; i++) {
            int rNum = i < rParts.length ? parseIntSafe(rParts[i]) : 0;
            int cNum = i < cParts.length ? parseIntSafe(cParts[i]) : 0;
            if (rNum != cNum) {
                return rNum > cNum;
            }
        }

        boolean rPre = remoteBase.length > 1;
        boolean cPre = currentBase.length > 1;
        return !rPre && cPre;
    }

    private static String stripPrefix(String version) {
        String trimmed = version.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            return trimmed.substring(1);
        }
        return trimmed;
    }

    private static int parseIntSafe(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void checkForUpdates(Consumer<String> callback) {
        scheduler.runTaskAsynchronously(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = URI.create(MODRINTH_API_URL + projectId + "/version").toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "AyoSynk/AntiRedstoneLag/" + plugin.getPluginMeta().getVersion());
                connection.setRequestProperty("Accept", "application/json");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        JsonArray versions = JsonParser.parseReader(reader).getAsJsonArray();
                        String chosen = pickLatestRelease(versions);
                        if (chosen != null && !chosen.isEmpty()) {
                            callback.accept(chosen);
                        }
                    }
                }
            } catch (Exception e) {
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String pickLatestRelease(JsonArray versions) {
        if (versions == null || versions.isEmpty()) return null;
        for (JsonElement el : versions) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            String type = obj.has("version_type") ? obj.get("version_type").getAsString() : "release";
            if ("release".equalsIgnoreCase(type) && obj.has("version_number")) {
                return obj.get("version_number").getAsString();
            }
        }
        JsonObject first = versions.get(0).getAsJsonObject();
        return first.has("version_number") ? first.get("version_number").getAsString() : null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!updateAvailable || latestVersion == null) return;

        Player player = event.getPlayer();
        if (!player.hasPermission(UPDATE_PERMISSION)) return;

        scheduler.runTaskLater(player, () -> {
            if (player.isOnline()) {
                var mm = plugin.getMessageManager();
                var messages = mm.getMessagesConfig().getMessages();
                mm.sendIfNotBlank(player, messages.getUpdateAvailablePlayer(), Map.of(
                        "version", latestVersion,
                        "current", plugin.getPluginMeta().getVersion()
                ));
                mm.sendIfNotBlank(player, messages.getUpdateDownloadPlayer(), Map.of("url", projectUrl));
            }
        }, 40L);
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}
