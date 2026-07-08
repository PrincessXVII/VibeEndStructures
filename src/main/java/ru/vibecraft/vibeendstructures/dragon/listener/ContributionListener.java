package ru.vibecraft.vibeendstructures.dragon.listener;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.dragon.contribution.DragonContributionTracker;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonKeys;

import java.util.Optional;

public final class ContributionListener implements Listener {

    private final VibeEndStructuresPlugin plugin;
    private final DragonKeys keys;
    private final DragonContributionTracker tracker;

    public ContributionListener(VibeEndStructuresPlugin plugin, DragonKeys keys, DragonContributionTracker tracker) {
        this.plugin = plugin;
        this.keys = keys;
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        String arenaId = dragon.getPersistentDataContainer().get(keys.arenaId(), PersistentDataType.STRING);
        if (arenaId == null) {
            return;
        }
        resolvePlayer(event.getDamager()).ifPresent(player ->
                tracker.recordDamage(arenaId, player, event.getFinalDamage()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        tracker.findActiveArenaAt(player.getLocation(), plugin.getDragonConfig().getArenas().values())
                .ifPresent(arenaId -> tracker.recordHealing(arenaId, player, event.getAmount()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        tracker.findActiveArenaAt(event.getBlockPlaced().getLocation(), plugin.getDragonConfig().getArenas().values())
                .ifPresent(arenaId -> tracker.recordBlockPlaced(arenaId, player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        tracker.findActiveArenaAt(player.getLocation(), plugin.getDragonConfig().getArenas().values())
                .ifPresent(arenaId -> tracker.recordDeath(arenaId, player));
    }

    private Optional<Player> resolvePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return Optional.of(player);
        }
        if (damager instanceof Projectile projectile) {
            return resolveSource(projectile.getShooter());
        }
        if (damager instanceof AreaEffectCloud cloud) {
            return resolveSource(cloud.getSource());
        }
        return Optional.empty();
    }

    private Optional<Player> resolveSource(ProjectileSource source) {
        if (source instanceof Player player) {
            return Optional.of(player);
        }
        return Optional.empty();
    }
}
