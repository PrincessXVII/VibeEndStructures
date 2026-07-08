package ru.vibecraft.vibeendstructures.dragon.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonSpawnResult;

import java.util.Comparator;

public final class DragonEggListener implements Listener {

    private final VibeEndStructuresPlugin plugin;

    public DragonEggListener(VibeEndStructuresPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEggPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!plugin.getDragonEggManager().isRenewableEgg(item)) {
            return;
        }
        Player player = event.getPlayer();
        Block against = event.getBlockAgainst();
        DragonArena arena = nearestCenterArena(event.getBlockPlaced().getX(), event.getBlockPlaced().getZ());
        if (arena == null || against.getType() != Material.BEDROCK || !isNearCenter(event.getBlockPlaced(), arena)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Яйцо дракона нужно поставить на бедрок в центре 0 0.", NamedTextColor.RED));
            return;
        }

        event.getBlockPlaced().setType(Material.AIR);
        DragonSpawnResult result = plugin.getDragonFightService().spawnFromEgg(arena.id(), arena.dragonTypeId());
        if (!result.success()) {
            event.setCancelled(true);
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Яйцо пробудило дракона. Этот дракон новое яйцо не оставит.", NamedTextColor.LIGHT_PURPLE));
    }

    private DragonArena nearestCenterArena(int x, int z) {
        return plugin.getDragonConfig().getArenas().values().stream()
                .filter(DragonArena::enabled)
                .min(Comparator.comparingDouble(arena -> {
                    double dx = x - arena.centerX();
                    double dz = z - arena.centerZ();
                    return dx * dx + dz * dz;
                }))
                .orElse(null);
    }

    private boolean isNearCenter(Block block, DragonArena arena) {
        return Math.abs(block.getX() - arena.centerX()) <= 3
                && Math.abs(block.getZ() - arena.centerZ()) <= 3;
    }
}
