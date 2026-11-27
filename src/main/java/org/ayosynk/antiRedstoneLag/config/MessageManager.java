package org.ayosynk.antiRedstoneLag.config;

import eu.okaeri.configs.serdes.commons.SerdesCommons;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    private final JavaPlugin plugin;
    private MessagesConfig messagesConfig;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
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
        return parseComponent(message);
    }

    public static Component parseComponent(String message) {
        if (message == null || message.isBlank()) return Component.empty();

        String formatted = message;
        if (formatted.contains("&#")) {
            Matcher matcher = HEX_PATTERN.matcher(formatted);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(sb, "<#" + matcher.group(1) + ">");
            }
            matcher.appendTail(sb);
            formatted = sb.toString();
        }

        if (formatted.contains("<") && formatted.contains(">")) {
            formatted = legacyToMiniMessage(formatted);
            try {
                return MINI_MESSAGE.deserialize(formatted);
            } catch (Exception ignored) {
            }
        }

        return LEGACY_SERIALIZER.deserialize(message);
    }

    public boolean sendIfNotBlank(Audience target, String rawTemplate, Component rendered) {
        if (target == null || rawTemplate == null || rawTemplate.isBlank()) return false;
        target.sendMessage(rendered);
        return true;
    }

    public boolean sendIfNotBlank(Audience target, String rawMessage) {
        if (target == null || rawMessage == null || rawMessage.isBlank()) return false;
        target.sendMessage(parseMessage(rawMessage));
        return true;
    }

    private static String legacyToMiniMessage(String input) {
        Matcher matcher = LEGACY_CODE_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder(input.length());
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String tag = LEGACY_TAGS.get(code);
            matcher.appendReplacement(sb, tag != null ? tag : matcher.group(0));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String getMessageString(String message) {
        return LEGACY_SERIALIZER.serialize(parseMessage(message));
    }
}