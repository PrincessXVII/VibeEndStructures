package ru.vibecraft.vibeendstructures.dragon.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.vibecraft.vibeendstructures.dragon.model.DragonAbility;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;
import ru.vibecraft.vibeendstructures.dragon.model.DragonPhase;
import ru.vibecraft.vibeendstructures.dragon.model.DragonType;
import ru.vibecraft.vibeendstructures.dragon.model.GeneralDragonConfig;
import ru.vibecraft.vibeendstructures.dragon.model.RewardTier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class DragonConfig {

    private final File configFile;
    private final Logger logger;

    private Map<String, DragonDefinition> dragonDefinitions = new HashMap<>();
    private Map<String, DragonArena> arenas = new HashMap<>();
    private GeneralDragonConfig generalConfig;

    public DragonConfig(File dataFolder, Logger logger) {
        this.configFile = new File(dataFolder, "dragons.yml");
        this.logger = logger;
    }

    public void load() {
        if (!configFile.exists()) {
            saveDefaultConfig();
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        migrateMissingDefaults(yaml);

        dragonDefinitions = new HashMap<>();
        arenas = new HashMap<>();
        loadDragons(yaml);
        loadArenas(yaml);
        loadGeneral(yaml);

        logger.info("Loaded " + dragonDefinitions.size() + " dragon types and " + arenas.size() + " arenas");
    }

    private void loadDragons(YamlConfiguration yaml) {
        ConfigurationSection dragonsSection = yaml.getConfigurationSection("dragons");
        if (dragonsSection == null) {
            return;
        }

        for (String key : dragonsSection.getKeys(false)) {
            ConfigurationSection ds = dragonsSection.getConfigurationSection(key);
            if (ds == null) continue;

            String id = ds.getString("id", key);
            String displayName = ds.getString("display-name", id);
            DragonType type = parseDragonType(ds.getString("type", "NORMAL"));
            boolean enabled = ds.getBoolean("enabled", true);
            double health = ds.getDouble("health", 200);
            double damage = ds.getDouble("damage", 10);
            double armor = ds.getDouble("armor", 0);
            double knockbackResistance = ds.getDouble("knockback-resistance", 1);
            int followRange = ds.getInt("follow-range", 100);
            double movementSpeed = ds.getDouble("movement-speed", 0.25);
            double flyingSpeed = ds.getDouble("flying-speed", 0.4);

            List<DragonAbility> abilities = parseAbilities(ds.getStringList("abilities"));

            List<DragonPhase> phases = new ArrayList<>();
            for (Map<?, ?> rawPhase : ds.getMapList("phases")) {
                double threshold = readDouble(rawPhase.get("threshold"), 0);
                List<DragonAbility> phaseAbilities = parseAbilities(readStringList(rawPhase.get("abilities")));
                phases.add(new DragonPhase(threshold, phaseAbilities));
            }

            double eggDropChance = ds.getDouble("egg-drop-chance", 0.1);

            List<RewardTier> rewardTiers = new ArrayList<>();
            for (Map<?, ?> rawTier : ds.getMapList("reward-tiers")) {
                double minContribution = readDouble(rawTier.get("min-contribution"), 0);
                String lootTable = readString(rawTier.get("loot-table"), "");
                List<String> commands = readStringList(rawTier.get("commands"));
                rewardTiers.add(new RewardTier(minContribution, lootTable, commands));
            }

            List<String> titles = ds.getStringList("titles");
            List<String> prefixes = ds.getStringList("prefixes");

            DragonDefinition def = new DragonDefinition(
                id, displayName, type, enabled, health, damage, armor, knockbackResistance,
                followRange, movementSpeed, flyingSpeed, abilities, phases, eggDropChance,
                rewardTiers, titles, prefixes
            );

            dragonDefinitions.put(id, def);
        }
    }

    private List<DragonAbility> parseAbilities(List<String> abilityStrings) {
        List<DragonAbility> result = new ArrayList<>();
        if (abilityStrings == null) return result;

        for (String s : abilityStrings) {
            try {
                result.add(DragonAbility.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown dragon ability: " + s);
            }
        }
        return result;
    }

    private DragonType parseDragonType(String raw) {
        try {
            return DragonType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            logger.warning("Invalid dragon type in dragons.yml: " + raw + ", using NORMAL");
            return DragonType.NORMAL;
        }
    }

    private double readDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String readString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private List<String> readStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : rawList) {
            if (entry != null) {
                result.add(String.valueOf(entry));
            }
        }
        return result;
    }

    private void loadArenas(YamlConfiguration yaml) {
        ConfigurationSection arenasSection = yaml.getConfigurationSection("arenas");
        if (arenasSection == null) {
            return;
        }

        for (String key : arenasSection.getKeys(false)) {
            ConfigurationSection as = arenasSection.getConfigurationSection(key);
            if (as == null) continue;

            String id = as.getString("id", key);
            String name = as.getString("name", id);
            String dragonTypeId = as.getString("dragon-type", "");
            int centerX = as.getInt("center-x", 0);
            int centerZ = as.getInt("center-z", 0);
            int radius = as.getInt("radius", 100);
            int height = as.getInt("height", 80);
            long cooldownHours = as.getLong("cooldown-hours", 24);
            String spawnStructure = as.getString("spawn-structure", "");
            boolean enabled = as.getBoolean("enabled", true);
            UUID activeDragonUuid = parseUuid(as.getString("active-dragon-uuid", null));
            long cooldownUntil = as.getLong("cooldown-until", 0);
            DragonArena.ArenaState state = parseArenaState(as.getString("state", enabled ? "IDLE" : "DISABLED"));

            DragonArena arena = new DragonArena(
                id, name, dragonTypeId, centerX, centerZ, radius, height,
                cooldownHours, spawnStructure, enabled, activeDragonUuid, cooldownUntil, state
            );

            arenas.put(id, arena);
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            logger.warning("Invalid active dragon UUID in dragons.yml: " + raw);
            return null;
        }
    }

    private DragonArena.ArenaState parseArenaState(String raw) {
        try {
            return DragonArena.ArenaState.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            logger.warning("Invalid arena state in dragons.yml: " + raw);
            return DragonArena.ArenaState.IDLE;
        }
    }

    private void loadGeneral(YamlConfiguration yaml) {
        ConfigurationSection general = yaml.getConfigurationSection("general");
        if (general == null) {
            generalConfig = new GeneralDragonConfig(
                1000, 0.001, 0.01, 0.08, "PURPLE", "PROGRESS", true, true,
                "&d&lДракон %dragon% &r&7побежден! Топ-урон: %top_player% (%top_dmg%)",
                "&d&lДракон %dragon% &r&7пробудился на арене &b%arena%&7!"
            );
            return;
        }

        generalConfig = new GeneralDragonConfig(
            general.getInt("min-distance-from-origin", 1000),
            general.getDouble("contribution-decay-per-second", 0.001),
            general.getDouble("min-contribution-for-reward", 0.01),
            general.getDouble("scheduled-egg-drop-chance", 0.08),
            general.getString("boss-bar-color", "PURPLE"),
            general.getString("boss-bar-style", "PROGRESS"),
            general.getBoolean("announce-spawn", true),
            general.getBoolean("announce-death", true),
            general.getString("death-message", "&d&lДракон %dragon% &r&7побежден! Топ-урон: %top_player% (%top_dmg%)"),
            general.getString("spawn-message", "&d&lДракон %dragon% &r&7пробудился на арене &b%arena%&7!")
        );
    }

    private void saveDefaultConfig() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("dragons.yml")) {
            if (in != null) {
                Files.copy(in, configFile.toPath());
            }
        } catch (IOException e) {
            logger.severe("Failed to save default dragons.yml: " + e.getMessage());
        }
    }

    private void migrateMissingDefaults(YamlConfiguration yaml) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("dragons.yml")) {
            if (in == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            boolean changed = false;

            if (!yaml.isConfigurationSection("dragons")) {
                yaml.set("dragons", defaults.get("dragons"));
                changed = true;
            }
            if (!yaml.isConfigurationSection("general")) {
                yaml.set("general", defaults.get("general"));
                changed = true;
            } else {
                changed |= fillMissing(yaml, defaults, "general.scheduled-egg-drop-chance");
            }

            ConfigurationSection defaultDragons = defaults.getConfigurationSection("dragons");
            ConfigurationSection currentDragons = yaml.getConfigurationSection("dragons");
            if (defaultDragons != null) {
                if (currentDragons == null) {
                    yaml.set("dragons", defaults.get("dragons"));
                    changed = true;
                } else {
                    for (String dragonId : defaultDragons.getKeys(false)) {
                        changed |= mergeDragonSpellDefaults(yaml, defaults, dragonId);
                    }
                }
            }

            ConfigurationSection defaultArenas = defaults.getConfigurationSection("arenas");
            ConfigurationSection currentArenas = yaml.getConfigurationSection("arenas");
            if (defaultArenas != null) {
                if (currentArenas == null) {
                    yaml.set("arenas", defaults.get("arenas"));
                    changed = true;
                } else {
                    for (String arenaId : defaultArenas.getKeys(false)) {
                        String base = "arenas." + arenaId + ".";
                        if (!currentArenas.isConfigurationSection(arenaId)) {
                            yaml.set("arenas." + arenaId, defaults.get("arenas." + arenaId));
                            changed = true;
                            continue;
                        }
                        changed |= fillBlank(yaml, defaults, base + "id");
                        changed |= fillBlank(yaml, defaults, base + "name");
                        changed |= fillBlank(yaml, defaults, base + "dragon-type");
                        changed |= fillMissing(yaml, defaults, base + "center-x");
                        changed |= fillMissing(yaml, defaults, base + "center-z");
                        changed |= fillMissing(yaml, defaults, base + "radius");
                        changed |= fillMissing(yaml, defaults, base + "height");
                        changed |= fillMissing(yaml, defaults, base + "cooldown-hours");
                        changed |= fillMissing(yaml, defaults, base + "spawn-structure");
                        changed |= fillMissing(yaml, defaults, base + "enabled");
                    }
                }
            }

            if (changed) {
                yaml.save(configFile);
                logger.info("Migrated missing dragon defaults into dragons.yml");
            }
        } catch (IOException ex) {
            logger.warning("Failed to migrate dragons.yml defaults: " + ex.getMessage());
        }
    }

    private boolean mergeDragonSpellDefaults(YamlConfiguration yaml, YamlConfiguration defaults, String dragonId) {
        String base = "dragons." + dragonId + ".";
        if (!yaml.isConfigurationSection("dragons." + dragonId)) {
            yaml.set("dragons." + dragonId, defaults.get("dragons." + dragonId));
            return true;
        }

        boolean changed = false;
        List<String> currentAbilities = yaml.getStringList(base + "abilities");
        List<String> defaultAbilities = defaults.getStringList(base + "abilities");
        if (currentAbilities == null || currentAbilities.isEmpty()) {
            if (!defaultAbilities.isEmpty()) {
                yaml.set(base + "abilities", defaultAbilities);
                changed = true;
            }
        } else {
            List<String> merged = mergeUniqueStrings(currentAbilities, defaultAbilities);
            if (!merged.equals(currentAbilities)) {
                yaml.set(base + "abilities", merged);
                changed = true;
            }
        }

        List<Map<?, ?>> currentPhases = yaml.getMapList(base + "phases");
        List<Map<?, ?>> defaultPhases = defaults.getMapList(base + "phases");
        if (currentPhases == null || currentPhases.isEmpty()) {
            if (!defaultPhases.isEmpty()) {
                yaml.set(base + "phases", defaultPhases);
                changed = true;
            }
            return changed;
        }
        if (defaultPhases.isEmpty()) {
            return changed;
        }

        List<Map<String, Object>> mergedPhases = new ArrayList<>();
        for (int i = 0; i < defaultPhases.size(); i++) {
            Map<?, ?> defaultPhase = defaultPhases.get(i);
            Map<?, ?> currentPhase = i < currentPhases.size() ? currentPhases.get(i) : null;
            double threshold = currentPhase != null
                    ? readDouble(currentPhase.get("threshold"), readDouble(defaultPhase.get("threshold"), 1.0))
                    : readDouble(defaultPhase.get("threshold"), 1.0);
            List<String> defaultPhaseAbilities = readStringList(defaultPhase.get("abilities"));
            List<String> currentPhaseAbilities = currentPhase == null
                    ? List.of()
                    : readStringList(currentPhase.get("abilities"));

            Map<String, Object> phase = new java.util.LinkedHashMap<>();
            phase.put("threshold", threshold);
            if (currentPhaseAbilities.isEmpty()) {
                phase.put("abilities", defaultPhaseAbilities);
                changed = true;
            } else {
                List<String> mergedAbilities = mergeUniqueStrings(currentPhaseAbilities, defaultPhaseAbilities);
                phase.put("abilities", mergedAbilities);
                if (!mergedAbilities.equals(currentPhaseAbilities)) {
                    changed = true;
                }
            }
            mergedPhases.add(phase);
        }
        for (int i = defaultPhases.size(); i < currentPhases.size(); i++) {
            Map<?, ?> extra = currentPhases.get(i);
            Map<String, Object> phase = new java.util.LinkedHashMap<>();
            phase.put("threshold", readDouble(extra.get("threshold"), 1.0));
            phase.put("abilities", readStringList(extra.get("abilities")));
            mergedPhases.add(phase);
        }
        if (changed) {
            yaml.set(base + "phases", mergedPhases);
        }
        return changed;
    }

    private List<String> mergeUniqueStrings(List<String> current, List<String> defaults) {
        List<String> merged = new ArrayList<>(current);
        for (String value : defaults) {
            if (value != null && !value.isBlank() && !merged.contains(value)) {
                merged.add(value);
            }
        }
        return merged;
    }

    private boolean fillMissing(YamlConfiguration yaml, YamlConfiguration defaults, String path) {
        if (yaml.contains(path)) {
            return false;
        }
        yaml.set(path, defaults.get(path));
        return true;
    }

    private boolean fillBlank(YamlConfiguration yaml, YamlConfiguration defaults, String path) {
        String value = yaml.getString(path);
        if (value != null && !value.isBlank()) {
            return false;
        }
        yaml.set(path, defaults.get(path));
        return true;
    }

    public Map<String, DragonDefinition> getDragonDefinitions() {
        return dragonDefinitions;
    }

    public DragonDefinition getDragon(String id) {
        return dragonDefinitions.get(id);
    }

    public Map<String, DragonArena> getArenas() {
        return arenas;
    }

    public DragonArena getArena(String id) {
        return arenas.get(id);
    }

    public GeneralDragonConfig getGeneralConfig() {
        return generalConfig;
    }

    public void saveArenas() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        for (DragonArena arena : arenas.values()) {
            String base = "arenas." + arena.id() + ".";
            yaml.set(base + "id", arena.id());
            yaml.set(base + "name", arena.name());
            yaml.set(base + "dragon-type", arena.dragonTypeId());
            yaml.set(base + "center-x", arena.centerX());
            yaml.set(base + "center-z", arena.centerZ());
            yaml.set(base + "radius", arena.radius());
            yaml.set(base + "height", arena.height());
            yaml.set(base + "cooldown-hours", arena.cooldownHours());
            yaml.set(base + "spawn-structure", arena.spawnStructure());
            yaml.set(base + "enabled", arena.enabled());
            yaml.set(base + "cooldown-until", arena.cooldownUntil());
            yaml.set(base + "state", arena.state().name());
            yaml.set(base + "active-dragon-uuid", arena.activeDragonUuid() != null ? arena.activeDragonUuid().toString() : null);
        }

        try {
            yaml.save(configFile);
        } catch (IOException e) {
            logger.severe("Failed to save dragons.yml: " + e.getMessage());
        }
    }

    public void updateArena(DragonArena arena) {
        arenas.put(arena.id(), arena);
        saveArenas();
    }
}
