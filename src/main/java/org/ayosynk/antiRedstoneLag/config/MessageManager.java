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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("&x(&[A-Fa-f0-9]){6}");

    private final JavaPlugin plugin;
    private MessagesConfig messagesConfig;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
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

        String formatted = message;
        if (formatted.contains("&#")) {
            Matcher matcher = HEX_PATTERN.matcher(formatted);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String hex = matcher.group(1);
                matcher.appendReplacement(sb, "<#" + hex + ">");
            }
            matcher.appendTail(sb);
            formatted = sb.toString();
        }

        if (formatted.contains("<") && formatted.contains(">")) {
            formatted = legacyToMiniMessage(formatted);
            try {
                return miniMessage.deserialize(formatted);
            } catch (Exception ignored) {
            }
        }

        return legacySerializer.deserialize(message);
    }

    private String legacyToMiniMessage(String input) {
        return input.replace("&0", "<black>")
                    .replace("&1", "<dark_blue>")
                    .replace("&2", "<dark_green>")
                    .replace("&3", "<dark_aqua>")
                    .replace("&4", "<dark_red>")
                    .replace("&5", "<dark_purple>")
                    .replace("&6", "<gold>")
                    .replace("&7", "<gray>")
                    .replace("&8", "<dark_gray>")
                    .replace("&9", "<blue>")
                    .replace("&a", "<green>")
                    .replace("&b", "<aqua>")
                    .replace("&c", "<red>")
                    .replace("&d", "<light_purple>")
                    .replace("&e", "<yellow>")
                    .replace("&f", "<white>")
                    .replace("&l", "<bold>")
                    .replace("&m", "<strikethrough>")
                    .replace("&n", "<underlined>")
                    .replace("&o", "<italic>")
                    .replace("&r", "<reset>");
    }

    public String getMessageString(String message) {
        return legacySerializer.serialize(parseMessage(message));
    }
}