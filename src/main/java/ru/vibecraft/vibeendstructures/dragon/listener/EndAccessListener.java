package ru.vibecraft.vibeendstructures.dragon.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class EndAccessListener implements Listener {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm dd.MM.yyyy")
            .withZone(ZoneId.of("Europe/Kyiv"));

    private final VibeEndStructuresPlugin plugin;

    public EndAccessListener(VibeEndStructuresPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (event.getTo().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        if (event.getPlayer().hasPermission("vibedragon.admin") || plugin.getDragonScheduleService().isEndOpen()) {
            return;
        }
        event.setCancelled(true);
        long opensAt = plugin.getDragonScheduleService().endOpensAt();
        String when = opensAt <= 0 ? "позже" : FORMAT.format(Instant.ofEpochMilli(opensAt));
        event.getPlayer().sendMessage(Component.text("End ещё закрыт после вайпа. Открытие: " + when + ".", NamedTextColor.LIGHT_PURPLE));
    }
}
