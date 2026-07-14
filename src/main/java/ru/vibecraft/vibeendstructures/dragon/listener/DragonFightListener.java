package ru.vibecraft.vibeendstructures.dragon.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.dragon.contribution.ContributionSnapshot;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;
import ru.vibecraft.vibeendstructures.dragon.reward.RewardDistributor;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonDeathRitual;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonFightService;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonKeys;

public final class DragonFightListener implements Listener {

    private final VibeEndStructuresPlugin plugin;
    private final DragonKeys keys;
    private final DragonFightService fightService;
    private final RewardDistributor rewardDistributor;

    public DragonFightListener(VibeEndStructuresPlugin plugin, DragonKeys keys, DragonFightService fightService, RewardDistributor rewardDistributor) {
        this.plugin = plugin;
        this.keys = keys;
        this.fightService = fightService;
        this.rewardDistributor = rewardDistributor;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        String arenaId = dragon.getPersistentDataContainer().get(keys.arenaId(), PersistentDataType.STRING);
        String dragonId = dragon.getPersistentDataContainer().get(keys.dragonId(), PersistentDataType.STRING);
        Byte eggEligible = dragon.getPersistentDataContainer().get(keys.eggDropEligible(), PersistentDataType.BYTE);
        if (arenaId == null || dragonId == null) {
            return;
        }

        // Custom loot/egg replace vanilla XP/drops and portal.egg handling.
        event.setDroppedExp(0);
        event.getDrops().clear();
        suppressVanillaBattle(dragon.getWorld());

        var arena = plugin.getDragonConfig().getArena(arenaId);
        ContributionSnapshot snapshot = fightService.completeFight(arenaId, true);
        DragonDefinition definition = plugin.getDragonConfig().getDragon(dragonId);
        Location deathLocation = dragon.getLocation().clone();
        World world = deathLocation.getWorld();
        boolean canDropEgg = eggEligible == null || eggEligible == (byte) 1;
        boolean scheduledSpawn = dragon.getScoreboardTags().contains("vibedragon:scheduled_spawn");

        plugin.getLogger().info("Dragon death handled for arena=" + arenaId + " dragon=" + dragonId
                + " at " + formatLoc(deathLocation) + "; starting death ritual then loot.");

        DragonDeathRitual.play(plugin, deathLocation, () -> {
            suppressVanillaBattle(world);
            try {
                if (definition != null && arena != null && world != null) {
                    rewardDistributor.distribute(
                            snapshot,
                            definition,
                            arena,
                            world,
                            deathLocation,
                            plugin.getDragonConfig().getGeneralConfig().minContributionForReward(),
                            canDropEgg,
                            scheduledSpawn
                    );
                } else {
                    plugin.getLogger().warning("Skipped dragon rewards for arena=" + arenaId
                            + " dragon=" + dragonId
                            + " (definition=" + (definition != null) + ", arena=" + (arena != null)
                            + ", world=" + (world != null) + ")");
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("Failed to distribute dragon rewards for arena=" + arenaId
                        + " dragon=" + dragonId + ": " + ex.getMessage());
                ex.printStackTrace();
            }
            announceDeath(definition, snapshot);
        });
    }

    /**
     * Any non-plugin End dragon (vanilla respawn leftover, natural spawn, etc.) is removed.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragonSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            if (isVibeDragon(dragon)) {
                suppressVanillaBattle(dragon.getWorld());
            }
            return;
        }
        // Delay one tick: vanilla battle may attach metadata after spawn declaration.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!dragon.isValid() || dragon.isDead()) {
                return;
            }
            if (isVibeDragon(dragon)) {
                suppressVanillaBattle(dragon.getWorld());
                return;
            }
            plugin.getLogger().info("Removing non-plugin EnderDragon spawn reason=" + event.getSpawnReason()
                    + " at " + formatLoc(dragon.getLocation()));
            dragon.remove();
            suppressVanillaBattle(dragon.getWorld());
        });
    }

    private boolean isVibeDragon(Entity entity) {
        if (!(entity instanceof EnderDragon dragon)) {
            return false;
        }
        return dragon.getPersistentDataContainer().has(keys.dragonId(), PersistentDataType.STRING)
                || dragon.getScoreboardTags().contains("vibedragon");
    }

    private void suppressVanillaBattle(World world) {
        if (world == null) {
            return;
        }
        DragonBattle battle = world.getEnderDragonBattle();
        if (battle == null) {
            return;
        }
        battle.setPreviouslyKilled(true);
        if (battle.getRespawnPhase() != DragonBattle.RespawnPhase.NONE) {
            battle.setRespawnPhase(DragonBattle.RespawnPhase.NONE);
        }
        if (battle.getBossBar() != null) {
            battle.getBossBar().removeAll();
            battle.getBossBar().setVisible(false);
        }
    }

    private void announceDeath(DragonDefinition definition, ContributionSnapshot snapshot) {
        if (definition == null || !plugin.getDragonConfig().getGeneralConfig().announceDeath()) {
            return;
        }
        String topPlayer = snapshot.top().map(result -> result.playerName()).orElse("-");
        String topDamage = snapshot.top().map(result -> String.valueOf(Math.round(result.damageDealt()))).orElse("0");
        Bukkit.broadcastMessage(color(plugin.getDragonConfig().getGeneralConfig().deathMessage()
                .replace("%dragon%", definition.displayName())
                .replace("%top_player%", topPlayer)
                .replace("%top_dmg%", topDamage)));
    }

    private String formatLoc(Location location) {
        if (location.getWorld() == null) {
            return "null";
        }
        return location.getWorld().getName() + " "
                + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private String color(String message) {
        return message.replace('&', '§');
    }
}
