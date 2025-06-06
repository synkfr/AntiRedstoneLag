package org.ayosynk.antiRedstoneLag;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public class ConfigManager {
    private final JavaPlugin plugin;
    private int chunkThreshold;
    private int blockThreshold;
    private Set<Material> redstoneMaterials;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        chunkThreshold = config.getInt("chunk-threshold", 500);
        blockThreshold = config.getInt("block-threshold", 15);

        redstoneMaterials = new HashSet<>();
        for (String materialName : config.getStringList("redstone-components")) {
            try {
                redstoneMaterials.add(Material.valueOf(materialName));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material: " + materialName);
            }
        }
    }

    public int getChunkThreshold() {
        return chunkThreshold;
    }

    public int getBlockThreshold() {
        return blockThreshold;
    }

    public Set<Material> getRedstoneMaterials() {
        return redstoneMaterials;
    }
}