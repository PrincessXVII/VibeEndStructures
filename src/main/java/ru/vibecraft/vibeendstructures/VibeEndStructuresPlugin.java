package ru.vibecraft.vibeendstructures;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.vibecraft.vibeendstructures.command.VibeEndCommand;
import ru.vibecraft.vibeendstructures.generation.ChunkStructureListener;
import ru.vibecraft.vibeendstructures.generation.GenerationQueue;
import ru.vibecraft.vibeendstructures.generation.StructureGenerator;
import ru.vibecraft.vibeendstructures.generation.StructureOccupancy;
import ru.vibecraft.vibeendstructures.loot.LootDatapackInstaller;
import ru.vibecraft.vibeendstructures.structure.JigsawMetadataRegistry;
import ru.vibecraft.vibeendstructures.structure.StructureFinalizeService;
import ru.vibecraft.vibeendstructures.structure.StructureMetaRegistry;
import ru.vibecraft.vibeendstructures.structure.StructurePlacer;
import ru.vibecraft.vibeendstructures.structure.StructureRegistry;

public final class VibeEndStructuresPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private StructureRegistry registry;
    private JigsawMetadataRegistry jigsawMetadata;
    private StructureMetaRegistry metaRegistry;
    private StructureFinalizeService finalizeService;
    private StructurePlacer placer;
    private StructureGenerator generator;
    private StructureOccupancy occupancy;
    private GenerationQueue generationQueue;
    private LootDatapackInstaller lootInstaller;

    @Override
    public void onEnable() {
        pluginConfig = new PluginConfig(this);
        pluginConfig.load();

        registry = new StructureRegistry(this);
        registry.load();

        jigsawMetadata = new JigsawMetadataRegistry(this);
        jigsawMetadata.load();

        metaRegistry = new StructureMetaRegistry(this);
        metaRegistry.load();

        finalizeService = new StructureFinalizeService(getLogger());
        placer = new StructurePlacer(this, registry, jigsawMetadata, metaRegistry, finalizeService, getLogger());
        occupancy = new StructureOccupancy(this);
        generator = new StructureGenerator(this, registry, placer, occupancy);
        generationQueue = new GenerationQueue(this, generator);
        lootInstaller = new LootDatapackInstaller(this);

        Bukkit.getPluginManager().registerEvents(new ChunkStructureListener(generator), this);

        VibeEndCommand command = new VibeEndCommand(this);
        var cmd = getCommand("vibeend");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        if (pluginConfig.isInstallLootDatapack()) {
            lootInstaller.installForConfiguredWorlds(pluginConfig.getWorlds());
        }

        getLogger().info("VibeEndStructures enabled — loaded " + registry.getDefinitions().size() + " structure types.");
        getLogger().info("Structures directory: " + registry.getStructuresRoot().getAbsolutePath());
    }

    @Override
    public void onDisable() {
        getLogger().info("VibeEndStructures disabled.");
    }

    public void reload() {
        pluginConfig.load();
        registry.load();
        jigsawMetadata.load();
        metaRegistry.load();
        if (pluginConfig.isInstallLootDatapack()) {
            lootInstaller.installForConfiguredWorlds(pluginConfig.getWorlds());
        }
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public StructureRegistry getRegistry() {
        return registry;
    }

    public StructurePlacer getPlacer() {
        return placer;
    }

    public StructureGenerator getGenerator() {
        return generator;
    }

    public GenerationQueue getGenerationQueue() {
        return generationQueue;
    }
}
