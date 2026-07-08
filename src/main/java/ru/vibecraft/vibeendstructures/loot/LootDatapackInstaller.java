package ru.vibecraft.vibeendstructures.loot;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.loot.LootTable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class LootDatapackInstaller {

    private static final String DATAPACK_FOLDER = "vibeendstructures_loot";

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final AtomicBoolean reloadScheduled = new AtomicBoolean(false);

    public LootDatapackInstaller(org.bukkit.plugin.java.JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void install() {
        Path datapackRoot = resolvePrimaryDatapackRoot();
        try {
            Files.createDirectories(datapackRoot.resolve("data/vibeend/loot_table"));
            Files.createDirectories(datapackRoot.resolve("data/vibedragon/loot_table"));
            writePackMeta(datapackRoot);
            copyLootTables(datapackRoot.resolve("data/vibeend/loot_table"));
            copyDragonLootTables(datapackRoot.resolve("data/vibedragon/loot_table"));
            scheduleReload();
            plugin.getLogger().info("Installed VibeEndStructures loot datapack at " + datapackRoot);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to install loot datapack: " + ex.getMessage());
        }
    }

    public void installForConfiguredWorlds(java.util.List<String> worldNames) {
        install();
    }

    private Path resolvePrimaryDatapackRoot() {
        World mainWorld = Bukkit.getWorld("world");
        if (mainWorld == null && !Bukkit.getWorlds().isEmpty()) {
            mainWorld = Bukkit.getWorlds().getFirst();
        }
        if (mainWorld == null) {
            throw new IllegalStateException("No worlds loaded to install loot datapack");
        }
        return mainWorld.getWorldFolder().toPath().resolve("datapacks").resolve(DATAPACK_FOLDER);
    }

    private void scheduleReload() {
        if (!reloadScheduled.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                enableDatapack();
                Bukkit.getServer().reloadData();
                verifyLootTables();
                plugin.getLogger().info("Reloaded server datapacks (VibeEndStructures loot active).");
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to reload data packs", ex);
            } finally {
                reloadScheduled.set(false);
            }
        });
    }

    private void enableDatapack() {
        String selector = "file/" + DATAPACK_FOLDER;
        boolean enabled = Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "datapack enable \"" + selector + "\""
        );
        if (!enabled) {
            plugin.getLogger().info("Datapack enable returned false (may already be enabled): " + selector);
        }
    }

    private void verifyLootTables() {
        String[] required = {
                "vibeend:mega_ship_barrel",
                "vibeend:mega_ship_crate",
                "vibeend:mega_ship_treasure",
                "vibedragon:ender_dragon/common",
                "vibedragon:fire_dragon/common",
                "vibedragon:frost_dragon/common",
                "minecraft:chests/end_city_treasure"
        };
        int missing = 0;
        for (String id : required) {
            NamespacedKey key = NamespacedKey.fromString(id);
            if (key == null) {
                continue;
            }
            LootTable table = Bukkit.getLootTable(key);
            if (table == null) {
                missing++;
                plugin.getLogger().warning("Loot table missing after reload: " + id);
            }
        }
        if (missing == 0) {
            plugin.getLogger().info("Verified VibeEndStructures loot tables are registered.");
        } else {
            plugin.getLogger().warning("Missing " + missing + " loot table(s) — restart server if datapack was just added.");
        }
    }

    private void writePackMeta(Path root) throws IOException {
        String meta = """
                {
                  "pack": {
                    "description": "",
                    "min_format": [94, 1],
                    "max_format": [94, 1]
                  }
                }
                """;
        Files.writeString(root.resolve("pack.mcmeta"), meta);
    }

    private void copyLootTables(Path targetDir) throws IOException {
        String[] knownTables = {
                "abandoned.json", "cart.json", "cartographer_tower.json", "crystal.json", "empty.json",
                "floating_islands.json", "general.json", "houses_books.json", "houses_common.json",
                "houses_desert.json", "houses_flower.json", "houses_rare.json", "houses_uncommon.json",
                "jungle_tower.json", "large_carts.json", "large_carts_2.json", "mega_ship_barrel.json",
                "mega_ship_crate.json", "mega_ship_treasure.json", "mushroom_pond.json", "swamps.json"
        };

        for (String fileName : knownTables) {
            try (InputStream in = plugin.getResource("loot_tables/" + fileName)) {
                if (in == null) {
                    continue;
                }
                Path out = targetDir.resolve(fileName);
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out)) {
                    in.transferTo(os);
                }
            }
        }
    }

    private void copyDragonLootTables(Path targetDir) throws IOException {
        String[] dragonTables = {
                "ender_dragon/common.json", "ender_dragon/uncommon.json", "ender_dragon/rare.json", "ender_dragon/epic.json", "ender_dragon/legendary.json",
                "fire_dragon/common.json", "fire_dragon/uncommon.json", "fire_dragon/rare.json", "fire_dragon/epic.json", "fire_dragon/legendary.json",
                "frost_dragon/common.json", "frost_dragon/uncommon.json", "frost_dragon/rare.json", "frost_dragon/epic.json", "frost_dragon/legendary.json"
        };

        for (String fileName : dragonTables) {
            try (InputStream in = plugin.getResource("data/vibedragon/loot_table/" + fileName)) {
                if (in == null) {
                    continue;
                }
                Path out = targetDir.resolve(fileName);
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out)) {
                    in.transferTo(os);
                }
            }
        }
    }
}
