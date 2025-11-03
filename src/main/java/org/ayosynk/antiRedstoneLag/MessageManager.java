package org.ayosynk.antiRedstoneLag;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager {
    private final JavaPlugin plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    // Hex color pattern
    private final Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupMessages();
    }

    private void setupMessages() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
            plugin.getLogger().info("messages.yml has been created!");
        }

        reloadMessages();
    }

    public void reloadMessages() {
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public String getMessage(String path) {
        String message = messagesConfig.getString(path);
        if (message == null) {
            plugin.getLogger().warning("Message path '" + path + "' not found in messages.yml!");
            return "Message not found: " + path;
        }
        return translateHexColors(message);
    }

    public String getMessage(String path, String defaultValue) {
        String message = messagesConfig.getString(path, defaultValue);
        return translateHexColors(message);
    }

    /**
     * Translates both traditional color codes and hex color codes in a string
     */
    private String translateHexColors(String message) {
        if (message == null) return null;

        // Translate hex colors first
        Matcher matcher = hexPattern.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(buffer);

        // Then translate traditional color codes
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public boolean saveMessages() {
        try {
            messagesConfig.save(messagesFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save messages.yml!");
            e.printStackTrace();
            return false;
        }
    }
}