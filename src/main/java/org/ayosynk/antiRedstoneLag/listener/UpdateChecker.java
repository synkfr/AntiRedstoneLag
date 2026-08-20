package org.ayosynk.antiRedstoneLag.listener;

import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;
import org.ayosynk.antiRedstoneLag.scheduler.Scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

/**
 * Checks for plugin updates from SpigotMC.
 * Notifies admins when a new version is available.
 */
public class UpdateChecker implements Listener {
    private static final String SPIGOT_API_URL = "https://api.spigotmc.org/legacy/update.php?resource=";
    private static final String UPDATE_PERMISSION = "antiredstonelag.admin";

    private final AntiRedstoneLag plugin;
    private final Scheduler scheduler;
    private final int resourceId;
    private String latestVersion;
    private boolean updateAvailable = false;

    public UpdateChecker(AntiRedstoneLag plugin, Scheduler scheduler, int resourceId) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.resourceId = resourceId;
    }

    /**
     * Check for updates asynchronously.
     */
    public void checkForUpdates() {
        checkForUpdates(version -> {
            String currentVersion = plugin.getDescription().getVersion();
            if (!currentVersion.equalsIgnoreCase(version)) {
                latestVersion = version;
                updateAvailable = true;
                plugin.getLogger().info("A new version is available: v" + version + " (current: v" + currentVersion + ")");
                plugin.getLogger().info("Download at: https://www.spigotmc.org/resources/" + resourceId);
            }
        });
    }

    private void checkForUpdates(Consumer<String> callback) {
        scheduler.runTaskAsynchronously(() -> {
            try {
                URL url = new URL(SPIGOT_API_URL + resourceId);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("GET");

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String version = reader.readLine();
                    if (version != null && !version.isEmpty()) {
                        callback.accept(version);
                    }
                }
            } catch (Exception e) {
                // Silently fail - update check is not critical
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Notify player about available update on join.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!updateAvailable) return;

        Player player = event.getPlayer();
        if (!player.hasPermission(UPDATE_PERMISSION)) return;

        // Delay message slightly so it appears after other join messages
        scheduler.runTaskLater(player, () -> {
            if (player.isOnline()) {
                net.kyori.adventure.text.minimessage.MiniMessage mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
                player.sendMessage(mm.deserialize("<gold>[AntiRedstoneLag] <yellow>A new version is available: <green>v" + latestVersion));
                player.sendMessage(mm.deserialize("<gold>[AntiRedstoneLag] <gray>Download at: <aqua>https://www.spigotmc.org/resources/" + resourceId));
            }
        }, 40L); // 2 second delay
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}
