package ru.vibecraft.vibeendstructures.dragon.reward;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.vibecraft.vibeendstructures.dragon.model.RewardTier;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

public final class TitleManager {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public TitleManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-titles.yml");
    }

    public void load() {
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void executeRewardCommands(UUID playerUuid, String playerName, RewardTier tier) {
        for (String rawCommand : tier.commands()) {
            if (rawCommand == null || rawCommand.isBlank()) {
                continue;
            }
            String permissionNode = extractVibeDragonNode(rawCommand);
            if (permissionNode != null && hasGranted(playerUuid, permissionNode)) {
                continue;
            }

            String command = rawCommand.replace("%player%", playerName);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (permissionNode != null) {
                markGranted(playerUuid, playerName, permissionNode);
            }
        }
        save();
    }

    private String extractVibeDragonNode(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        int titleIndex = lower.indexOf("vibedragon.title.");
        int prefixIndex = lower.indexOf("vibedragon.prefix.");
        int index = titleIndex >= 0 ? titleIndex : prefixIndex;
        if (index < 0) {
            return null;
        }
        String tail = command.substring(index).trim();
        int end = tail.indexOf(' ');
        return end < 0 ? tail : tail.substring(0, end);
    }

    private boolean hasGranted(UUID playerUuid, String permissionNode) {
        return yaml.getStringList("players." + playerUuid + ".nodes").contains(permissionNode);
    }

    private void markGranted(UUID playerUuid, String playerName, String permissionNode) {
        String base = "players." + playerUuid;
        yaml.set(base + ".name", playerName);
        var nodes = yaml.getStringList(base + ".nodes");
        if (!nodes.contains(permissionNode)) {
            nodes.add(permissionNode);
            yaml.set(base + ".nodes", nodes);
        }
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save player-titles.yml: " + ex.getMessage());
        }
    }
}
