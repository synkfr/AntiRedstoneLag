package org.ayosynk.antiRedstoneLag.config;

import eu.okaeri.configs.serdes.commons.SerdesCommons;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class MessageManager {
    private final JavaPlugin plugin;
    private MessagesConfig messagesConfig;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .extractUrls()
            .build();

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadMessages();
    }

    public void reloadMessages() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        handleBackup(messagesFile);

        this.messagesConfig = eu.okaeri.configs.ConfigManager.create(MessagesConfig.class, it -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer(), new SerdesBukkit(), new SerdesCommons());
            it.withBindFile(messagesFile);
            it.withRemoveOrphans(true);
            it.saveDefaults();
            it.load(true);
        });
    }

    private void handleBackup(File file) {
        if (!file.exists()) return;
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!content.contains("AntiRedstoneLag Messages")) {
                File backupFile = new File(file.getParentFile(), file.getName() + ".bk");
                Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Created safety backup: " + file.getName() + " -> " + backupFile.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check or create backup for " + file.getName() + ": " + e.getMessage());
        }
    }

    public MessagesConfig getMessagesConfig() {
        return messagesConfig;
    }

    public Component parseMessage(String message) {
        if (message == null || message.isEmpty()) return Component.empty();
        if (message.contains("&") || message.contains("§")) {
            return legacySerializer.deserialize(message.replace("&#", "#"));
        }
        return miniMessage.deserialize(message);
    }

    public String getMessageString(String message) {
        return legacySerializer.serialize(parseMessage(message));
    }
}