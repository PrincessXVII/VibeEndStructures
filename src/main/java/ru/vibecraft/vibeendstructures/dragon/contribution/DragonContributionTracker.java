package ru.vibecraft.vibeendstructures.dragon.contribution;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class DragonContributionTracker {

    private final ContributionCalculator calculator = new ContributionCalculator();
    private final Map<String, ActiveFightContribution> activeFights = new HashMap<>();
    private final Map<String, ContributionSnapshot> lastSnapshots = new HashMap<>();
    private final File storageFile;
    private final Logger logger;

    public DragonContributionTracker(JavaPlugin plugin) {
        this.storageFile = new File(plugin.getDataFolder(), "dragon-contributions.yml");
        this.logger = plugin.getLogger();
    }

    public void load() {
        lastSnapshots.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection arenas = yaml.getConfigurationSection("arenas");
        if (arenas == null) {
            return;
        }
        for (String arenaId : arenas.getKeys(false)) {
            ConfigurationSection section = arenas.getConfigurationSection(arenaId);
            if (section == null) {
                continue;
            }
            List<ContributionResult> results = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("results")) {
                UUID uuid = parseUuid(String.valueOf(raw.get("uuid")));
                if (uuid == null) {
                    continue;
                }
                Object rawName = raw.get("name");
                results.add(new ContributionResult(
                        uuid,
                        rawName == null ? "unknown" : String.valueOf(rawName),
                        readDouble(raw.get("damage")),
                        readDouble(raw.get("healing")),
                        readDouble(raw.get("cc")),
                        readInt(raw.get("blocks")),
                        readInt(raw.get("revives")),
                        readInt(raw.get("deaths")),
                        readDouble(raw.get("score")),
                        readDouble(raw.get("contribution"))
                ));
            }
            lastSnapshots.put(arenaId, new ContributionSnapshot(
                    arenaId,
                    section.getString("dragon-id", ""),
                    parseInstant(section.getString("finished-at")),
                    results
            ));
        }
    }

    public void startFight(DragonArena arena, DragonDefinition definition) {
        activeFights.put(arena.id(), new ActiveFightContribution(arena.id(), definition.id()));
    }

    public ContributionSnapshot finishFight(String arenaId) {
        ActiveFightContribution active = activeFights.remove(arenaId);
        if (active == null) {
            return lastSnapshots.getOrDefault(arenaId, ContributionSnapshot.empty(arenaId, ""));
        }
        ContributionSnapshot snapshot = new ContributionSnapshot(
                active.arenaId(),
                active.dragonId(),
                Instant.now(),
                calculator.calculate(active.contributions().values())
        );
        lastSnapshots.put(arenaId, snapshot);
        save();
        return snapshot;
    }

    public void clearActiveFight(String arenaId) {
        activeFights.remove(arenaId);
    }

    public void recordDamage(String arenaId, Player player, double amount) {
        contribution(arenaId, player).ifPresent(data -> data.addDamage(amount));
    }

    public void recordHealing(String arenaId, Player player, double amount) {
        contribution(arenaId, player).ifPresent(data -> data.addHealing(amount));
    }

    public void recordCcTime(String arenaId, Player player, double seconds) {
        contribution(arenaId, player).ifPresent(data -> data.addCcTime(seconds));
    }

    public void recordBlockPlaced(String arenaId, Player player) {
        contribution(arenaId, player).ifPresent(ContributionData::addBlockPlaced);
    }

    public void recordDeath(String arenaId, Player player) {
        contribution(arenaId, player).ifPresent(ContributionData::addDeath);
    }

    public Optional<String> findActiveArenaAt(Location location, Iterable<DragonArena> arenas) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        for (DragonArena arena : arenas) {
            if (!activeFights.containsKey(arena.id())) {
                continue;
            }
            if (isInsideArena(location, arena)) {
                return Optional.of(arena.id());
            }
        }
        return Optional.empty();
    }

    public Optional<ContributionSnapshot> currentSnapshot(String arenaId) {
        ActiveFightContribution active = activeFights.get(arenaId);
        if (active == null) {
            return Optional.empty();
        }
        return Optional.of(new ContributionSnapshot(
                active.arenaId(),
                active.dragonId(),
                Instant.now(),
                calculator.calculate(active.contributions().values())
        ));
    }

    public Optional<ContributionSnapshot> lastSnapshot(String arenaId) {
        return Optional.ofNullable(lastSnapshots.get(arenaId));
    }

    public Optional<ContributionSnapshot> bestSnapshot(String arenaId) {
        return currentSnapshot(arenaId).or(() -> lastSnapshot(arenaId));
    }

    private Optional<ContributionData> contribution(String arenaId, Player player) {
        ActiveFightContribution active = activeFights.get(arenaId);
        if (active == null) {
            return Optional.empty();
        }
        ContributionData data = active.contributions().computeIfAbsent(
                player.getUniqueId(),
                uuid -> new ContributionData(uuid, player.getName())
        );
        data.playerName(player.getName());
        return Optional.of(data);
    }

    private boolean isInsideArena(Location location, DragonArena arena) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        double dx = location.getX() - (arena.centerX() + 0.5);
        double dz = location.getZ() - (arena.centerZ() + 0.5);
        return dx * dx + dz * dz <= arena.radius() * arena.radius();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ContributionSnapshot snapshot : lastSnapshots.values()) {
            String base = "arenas." + snapshot.arenaId() + ".";
            yaml.set(base + "dragon-id", snapshot.dragonId());
            yaml.set(base + "finished-at", snapshot.finishedAt().toString());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ContributionResult result : snapshot.results()) {
                Map<String, Object> row = new HashMap<>();
                row.put("uuid", result.playerUuid().toString());
                row.put("name", result.playerName());
                row.put("damage", result.damageDealt());
                row.put("healing", result.healingDone());
                row.put("cc", result.ccTimeApplied());
                row.put("blocks", result.blocksPlaced());
                row.put("revives", result.revives());
                row.put("deaths", result.deaths());
                row.put("score", result.score());
                row.put("contribution", result.contribution());
                rows.add(row);
            }
            yaml.set(base + "results", rows);
        }
        try {
            yaml.save(storageFile);
        } catch (IOException ex) {
            logger.warning("Failed to save dragon-contributions.yml: " + ex.getMessage());
        }
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (IllegalArgumentException ex) {
            return Instant.now();
        }
    }

    private double readDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private int readInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private record ActiveFightContribution(
            String arenaId,
            String dragonId,
            Map<UUID, ContributionData> contributions
    ) {
        private ActiveFightContribution(String arenaId, String dragonId) {
            this(arenaId, dragonId, new HashMap<>());
        }
    }
}
