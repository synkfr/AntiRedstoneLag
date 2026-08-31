package org.ayosynk.antiRedstoneLag.listener;

import com.google.gson.JsonArray;
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
import java.util.function.Consumer;

public class UpdateChecker implements Listener {
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/";
    private static final String UPDATE_PERMISSION = "antiredstonelag.admin";

    private final AntiRedstoneLag plugin;
    private final Scheduler scheduler;
    private final String projectId;
    private final String projectUrl;
    private String latestVersion;
    private boolean updateAvailable = false;

    public UpdateChecker(AntiRedstoneLag plugin, Scheduler scheduler, String projectId, String projectUrl) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.projectId = projectId;
        this.projectUrl = projectUrl;
    }

    public void checkForUpdates() {
        checkForUpdates(version -> {
            String currentVersion = plugin.getPluginMeta().getVersion();
            if (!currentVersion.equalsIgnoreCase(version)) {
                latestVersion = version;
                updateAvailable = true;
                String msg = plugin.getMessageManager().getMessagesConfig().getMessages().getUpdateAvailableConsole()
                        .replace("{version}", version)
                        .replace("{current}", currentVersion);
                String download = plugin.getMessageManager().getMessagesConfig().getMessages().getUpdateDownloadConsole()
                        .replace("{url}", projectUrl);
                plugin.getLogger().info(msg);
                plugin.getLogger().info(download);
            }
        });
    }

    private void checkForUpdates(Consumer<String> callback) {
        scheduler.runTaskAsynchronously(() -> {
            try {
                URL url = URI.create(MODRINTH_API_URL + projectId + "/version").toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "AyoSynk/AntiRedstoneLag/" + plugin.getPluginMeta().getVersion());
                connection.setRequestProperty("Accept", "application/json");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        JsonArray versions = JsonParser.parseReader(reader).getAsJsonArray();
                        if (!versions.isEmpty()) {
                            JsonObject latest = versions.get(0).getAsJsonObject();
                            if (latest.has("version_number")) {
                                String version = latest.get("version_number").getAsString();
                                if (version != null && !version.isEmpty()) {
                                    callback.accept(version);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
                }
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!updateAvailable) return;

        Player player = event.getPlayer();
        if (!player.hasPermission(UPDATE_PERMISSION)) return;

        scheduler.runTaskLater(player, () -> {
            if (player.isOnline()) {
                String playerMsg = plugin.getMessageManager().getMessagesConfig().getMessages().getUpdateAvailablePlayer()
                        .replace("{version}", latestVersion);
                String playerDownload = plugin.getMessageManager().getMessagesConfig().getMessages().getUpdateDownloadPlayer()
                        .replace("{url}", projectUrl);
                player.sendMessage(plugin.getMessageManager().parseMessage(playerMsg));
                player.sendMessage(plugin.getMessageManager().parseMessage(playerDownload));
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
