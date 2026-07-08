package ru.vibecraft.vibeendstructures;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.vibecraft.vibeendstructures.command.VibeEndCommand;
import ru.vibecraft.vibeendstructures.command.VibeDragonCommand;
import ru.vibecraft.vibeendstructures.dragon.contribution.DragonContributionTracker;
import ru.vibecraft.vibeendstructures.dragon.config.DragonConfig;
import ru.vibecraft.vibeendstructures.dragon.listener.ContributionListener;
import ru.vibecraft.vibeendstructures.dragon.listener.DragonEggListener;
import ru.vibecraft.vibeendstructures.dragon.listener.DragonFightListener;
import ru.vibecraft.vibeendstructures.dragon.listener.EndAccessListener;
import ru.vibecraft.vibeendstructures.dragon.reward.DragonEggManager;
import ru.vibecraft.vibeendstructures.dragon.reward.RewardDistributor;
import ru.vibecraft.vibeendstructures.dragon.reward.TitleManager;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonAbilityExecutor;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonEggGlowTask;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonFightService;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonKeys;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonPhaseController;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonScheduleService;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonSpawner;
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
    private DragonConfig dragonConfig;
    private DragonKeys dragonKeys;
    private DragonFightService dragonFightService;
    private DragonPhaseController dragonPhaseController;
    private DragonContributionTracker dragonContributionTracker;
    private TitleManager titleManager;
    private RewardDistributor rewardDistributor;
    private DragonEggManager dragonEggManager;
    private DragonEggGlowTask dragonEggGlowTask;
    private DragonScheduleService dragonScheduleService;

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

        dragonConfig = new DragonConfig(getDataFolder(), getLogger());
        dragonConfig.load();
        dragonKeys = new DragonKeys(this);
        dragonContributionTracker = new DragonContributionTracker(this);
        dragonContributionTracker.load();
        titleManager = new TitleManager(this);
        titleManager.load();
        dragonEggManager = new DragonEggManager(this, dragonKeys);
        rewardDistributor = new RewardDistributor(this, titleManager, dragonEggManager);
        DragonSpawner dragonSpawner = new DragonSpawner(this, dragonKeys);
        dragonFightService = new DragonFightService(this, dragonSpawner, dragonContributionTracker);
        DragonAbilityExecutor dragonAbilityExecutor = new DragonAbilityExecutor(this);
        dragonPhaseController = new DragonPhaseController(this, dragonFightService, dragonAbilityExecutor);
        dragonFightService.setPhaseController(dragonPhaseController);
        dragonPhaseController.start();
        dragonEggGlowTask = new DragonEggGlowTask(this, dragonEggManager);
        dragonEggGlowTask.start();
        dragonScheduleService = new DragonScheduleService(this);
        dragonScheduleService.start();

        Bukkit.getPluginManager().registerEvents(new ChunkStructureListener(generator), this);
        Bukkit.getPluginManager().registerEvents(new DragonFightListener(this, dragonKeys, dragonFightService, rewardDistributor), this);
        Bukkit.getPluginManager().registerEvents(new ContributionListener(this, dragonKeys, dragonContributionTracker), this);
        Bukkit.getPluginManager().registerEvents(new DragonEggListener(this), this);
        Bukkit.getPluginManager().registerEvents(new EndAccessListener(this), this);

        VibeEndCommand command = new VibeEndCommand(this);
        var cmd = getCommand("vibeend");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
        VibeDragonCommand dragonCommand = new VibeDragonCommand(this);
        var dragonCmd = getCommand("vibedragon");
        if (dragonCmd != null) {
            dragonCmd.setExecutor(dragonCommand);
            dragonCmd.setTabCompleter(dragonCommand);
        }

        if (pluginConfig.isInstallLootDatapack()) {
            lootInstaller.installForConfiguredWorlds(pluginConfig.getWorlds());
        }

        getLogger().info("VibeEndStructures enabled — loaded " + registry.getDefinitions().size() + " structure types, " + dragonConfig.getDragonDefinitions().size() + " dragon types.");
        getLogger().info("Structures directory: " + registry.getStructuresRoot().getAbsolutePath());
    }

    @Override
    public void onDisable() {
        if (dragonPhaseController != null) {
            dragonPhaseController.stop();
        }
        if (dragonEggGlowTask != null) {
            dragonEggGlowTask.stop();
        }
        if (dragonScheduleService != null) {
            dragonScheduleService.stop();
        }
        if (dragonConfig != null) {
            dragonConfig.saveArenas();
        }
        if (dragonContributionTracker != null) {
            dragonContributionTracker.save();
        }
        getLogger().info("VibeEndStructures disabled.");
    }

    public void reload() {
        pluginConfig.load();
        registry.load();
        jigsawMetadata.load();
        metaRegistry.load();
        dragonConfig.load();
        dragonContributionTracker.load();
        titleManager.load();
        dragonScheduleService.load();
        if (dragonFightService != null) {
            dragonFightService.checkActiveArenas();
        }
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

    public StructureOccupancy getOccupancy() {
        return occupancy;
    }

    public GenerationQueue getGenerationQueue() {
        return generationQueue;
    }

    public DragonConfig getDragonConfig() {
        return dragonConfig;
    }

    public DragonFightService getDragonFightService() {
        return dragonFightService;
    }

    public DragonContributionTracker getDragonContributionTracker() {
        return dragonContributionTracker;
    }

    public RewardDistributor getRewardDistributor() {
        return rewardDistributor;
    }

    public DragonEggManager getDragonEggManager() {
        return dragonEggManager;
    }

    public DragonScheduleService getDragonScheduleService() {
        return dragonScheduleService;
    }
}
