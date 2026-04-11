package org.ayosynk.antiRedstoneLag.config;

import org.ayosynk.antiRedstoneLag.AntiRedstoneLag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class MessageManager {
    private final JavaPlugin plugin;
    private File messagesFile;
    private FileConfiguration messagesConfig;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .extractUrls()
            .build();

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

    public Component getMessage(String path) {
        String message = messagesConfig.getString(path);
        if (message == null) {
            plugin.getLogger().warning("Message path '" + path + "' not found in messages.yml!");
            return Component.text("Message not found: " + path);
        }
        return parseMessage(message);
    }

    public Component getMessage(String path, String defaultValue) {
        String message = messagesConfig.getString(path, defaultValue);
        return parseMessage(message);
    }

    /**
     * Legacy support: returns translated string for legacy Bukkit methods
     */
    public String getMessageString(String path, String defaultValue) {
        return legacySerializer.serialize(getMessage(path, defaultValue));
    }
    
    public String getMessageString(String path) {
        return legacySerializer.serialize(getMessage(path));
    }

    private Component parseMessage(String message) {
        if (message == null) return Component.empty();
        
        // Convert legacy &#RRGGBB to <color:#RRGGBB> for MiniMessage
        // Also handle & color codes if they come from old config
        if (message.contains("&") || message.contains("§")) {
            return legacySerializer.deserialize(message.replace("&#", "#"));
        }
        
        return miniMessage.deserialize(message);
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