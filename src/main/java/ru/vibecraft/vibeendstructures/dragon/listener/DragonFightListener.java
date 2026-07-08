package ru.vibecraft.vibeendstructures.dragon.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

    @EventHandler
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

        var arena = plugin.getDragonConfig().getArena(arenaId);
        ContributionSnapshot snapshot = fightService.completeFight(arenaId, true);
        DragonDefinition definition = plugin.getDragonConfig().getDragon(dragonId);
        Location deathLocation = dragon.getLocation().clone();
        boolean canDropEgg = eggEligible == null || eggEligible == (byte) 1;
        DragonDeathRitual.play(plugin, deathLocation, () -> {
            if (definition != null && arena != null) {
                rewardDistributor.distribute(
                        snapshot,
                        definition,
                        arena,
                        deathLocation.getWorld(),
                        plugin.getDragonConfig().getGeneralConfig().minContributionForReward(),
                        canDropEgg
                );
            }
            announceDeath(definition, snapshot);
        });
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

    private String color(String message) {
        return message.replace('&', '§');
    }
}
