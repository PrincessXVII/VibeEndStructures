package ru.vibecraft.vibeendstructures.dragon.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.dragon.model.DragonAbility;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;
import ru.vibecraft.vibeendstructures.dragon.model.DragonPhase;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DragonPhaseController {

    private final VibeEndStructuresPlugin plugin;
    private final DragonFightService fightService;
    private final DragonAbilityExecutor abilityExecutor;
    private final Map<UUID, ActiveDragon> activeDragons = new HashMap<>();
    private BukkitTask task;

    public DragonPhaseController(VibeEndStructuresPlugin plugin, DragonFightService fightService, DragonAbilityExecutor abilityExecutor) {
        this.plugin = plugin;
        this.fightService = fightService;
        this.abilityExecutor = abilityExecutor;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        activeDragons.clear();
    }

    public void track(EnderDragon dragon, DragonArena arena, DragonDefinition definition) {
        hideVanillaBossBar(dragon);
        activeDragons.put(dragon.getUniqueId(), new ActiveDragon(arena.id(), definition.id(), ""));
    }

    public void untrack(UUID dragonUuid) {
        activeDragons.remove(dragonUuid);
        abilityExecutor.clear(dragonUuid);
    }

    private void tick() {
        fightService.checkActiveArenas();
        for (DragonArena arena : plugin.getDragonConfig().getArenas().values()) {
            if (arena.state() != DragonArena.ArenaState.ACTIVE || arena.activeDragonUuid() == null) {
                continue;
            }
            EnderDragon dragon = fightService.findDragon(arena.activeDragonUuid(), arena).orElse(null);
            ActiveDragon active = activeDragons.get(arena.activeDragonUuid());
            String dragonId = active == null ? arena.dragonTypeId() : active.dragonId();
            DragonDefinition definition = plugin.getDragonConfig().getDragon(dragonId);
            if (dragon == null || definition == null) {
                continue;
            }
            if (!dragon.getUniqueId().equals(arena.activeDragonUuid())) {
                plugin.getDragonConfig().updateArena(arena.withActiveDragon(dragon.getUniqueId()));
            }
            fightService.rememberDragonLocation(dragon);
            activeDragons.computeIfAbsent(dragon.getUniqueId(), uuid -> new ActiveDragon(arena.id(), definition.id(), ""));
            tickDragon(dragon, arena, definition, activeDragons.get(dragon.getUniqueId()));
        }
    }

    private void tickDragon(EnderDragon dragon, DragonArena arena, DragonDefinition definition, ActiveDragon active) {
        double healthPercent = Math.max(0.0, Math.min(1.0, dragon.getHealth() / definition.health()));
        DragonPhase phase = selectPhase(definition.phases(), healthPercent);
        String phaseKey = phase == null ? "base" : String.valueOf(phase.threshold());
        List<DragonAbility> abilities = phase == null ? definition.abilities() : phase.abilities();

        if (!phaseKey.equals(active.phaseKey())) {
            active.phaseKey(phaseKey);
            announcePhaseChange(dragon, arena, definition, phaseKey);
        }

        hideVanillaBossBar(dragon);
        sendActionBar(dragon.getLocation(), arena.radius(), definition, healthPercent, phaseKey);
        abilityExecutor.tick(dragon, arena, definition, abilities);
    }

    private void sendActionBar(Location dragonLocation, int arenaRadius, DragonDefinition definition, double healthPercent, String phaseKey) {
        Component action = Component.text(definition.displayName()
                + " | " + phaseLabel(phaseKey)
                + " | HP " + Math.round(healthPercent * 100) + "%");
        World world = dragonLocation.getWorld();
        if (world == null) {
            return;
        }
        double viewRadiusSquared = Math.pow(arenaRadius + 96.0, 2);
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(dragonLocation) <= viewRadiusSquared) {
                player.sendActionBar(action);
            }
        }
    }

    private DragonPhase selectPhase(List<DragonPhase> phases, double healthPercent) {
        if (phases == null || phases.isEmpty()) {
            return null;
        }
        List<DragonPhase> sorted = phases.stream()
                .sorted(Comparator.comparingDouble(DragonPhase::threshold))
                .toList();
        return sorted.stream()
                .filter(phase -> healthPercent <= phase.threshold())
                .findFirst()
                .orElse(sorted.getLast());
    }

    private String phaseLabel(String phaseKey) {
        if ("base".equals(phaseKey)) {
            return "Фаза I";
        }
        return switch (phaseKey) {
            case "1.0" -> "Фаза I";
            case "0.6" -> "Фаза II";
            case "0.3" -> "Фаза III";
            default -> "Фаза " + phaseKey;
        };
    }

    private void announcePhaseChange(EnderDragon dragon, DragonArena arena, DragonDefinition definition, String phaseKey) {
        Location location = dragon.getLocation();
        DragonParticles.spawn(plugin, location.getWorld(), Particle.DRAGON_BREATH, location.clone().add(0, 2.5, 0), 120, 6.0, 2.5, 6.0, 0.08);
        DragonParticles.spawn(plugin, location.getWorld(), Particle.END_ROD, location.clone().add(0, 4.0, 0), 40, 2.5, 2.0, 2.5, 0.06);
        location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 3.0f, 0.8f);
        Component message = Component.text(definition.displayName() + ": " + phaseLabel(phaseKey));
        double radiusSquared = arena.radius() * arena.radius();
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= radiusSquared) {
                player.sendMessage(message);
            }
        }
    }

    private void hideVanillaBossBar(EnderDragon dragon) {
        var battle = dragon.getWorld().getEnderDragonBattle();
        if (battle == null) {
            return;
        }
        if (battle.getBossBar() != null) {
            battle.getBossBar().removeAll();
            battle.getBossBar().setVisible(false);
        }
        // Abort any vanilla crystal respawn that may have been started externally.
        if (battle.getRespawnPhase() != org.bukkit.boss.DragonBattle.RespawnPhase.NONE) {
            battle.setRespawnPhase(org.bukkit.boss.DragonBattle.RespawnPhase.NONE);
        }
        battle.setPreviouslyKilled(true);
    }

    private static final class ActiveDragon {
        private final String arenaId;
        private final String dragonId;
        private String phaseKey;

        private ActiveDragon(String arenaId, String dragonId, String phaseKey) {
            this.arenaId = arenaId;
            this.dragonId = dragonId;
            this.phaseKey = phaseKey;
        }

        private String phaseKey() {
            return phaseKey;
        }

        private void phaseKey(String phaseKey) {
            this.phaseKey = phaseKey;
        }

        private String dragonId() {
            return dragonId;
        }
    }
}
