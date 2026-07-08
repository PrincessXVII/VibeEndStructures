package ru.vibecraft.vibeendstructures.dragon.runtime;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class DragonScheduleService {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final List<LocalTime> AUTO_SPAWN_TIMES = List.of(LocalTime.of(16, 0), LocalTime.of(21, 0));

    private final VibeEndStructuresPlugin plugin;
    private final File file;
    private BukkitTask task;
    private boolean endOpen = true;
    private long endOpensAt;
    private boolean firstDragonSpawned = true;
    private String lastAutoSpawnKey = "";

    public DragonScheduleService(VibeEndStructuresPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dragon-schedule.yml");
    }

    public void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        endOpen = yaml.getBoolean("end-open", true);
        endOpensAt = yaml.getLong("end-opens-at", 0);
        firstDragonSpawned = yaml.getBoolean("first-dragon-spawned", true);
        lastAutoSpawnKey = yaml.getString("last-auto-spawn-key", "");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("end-open", endOpen);
        yaml.set("end-opens-at", endOpensAt);
        yaml.set("first-dragon-spawned", firstDragonSpawned);
        yaml.set("last-auto-spawn-key", lastAutoSpawnKey);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save dragon-schedule.yml: " + ex.getMessage());
        }
    }

    public void start() {
        if (task != null) {
            return;
        }
        load();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L * 30L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        save();
    }

    public void startWipeCountdown(Duration delay) {
        endOpen = false;
        endOpensAt = System.currentTimeMillis() + delay.toMillis();
        firstDragonSpawned = false;
        lastAutoSpawnKey = "";
        save();
        Bukkit.broadcastMessage("§5End откроется через " + delay.toHours() + " ч. После открытия дракон появится через 5 минут.");
    }

    public void forceOpen() {
        endOpen = true;
        if (endOpensAt <= 0) {
            endOpensAt = System.currentTimeMillis();
        }
        save();
        Bukkit.broadcastMessage("§5End открыт. Первый дракон появится через 5 минут, если ещё не был призван.");
    }

    public boolean isEndOpen() {
        if (endOpen) {
            return true;
        }
        return endOpensAt > 0 && System.currentTimeMillis() >= endOpensAt;
    }

    public long endOpensAt() {
        return endOpensAt;
    }

    public boolean firstDragonSpawned() {
        return firstDragonSpawned;
    }

    public String status() {
        String opens = endOpensAt <= 0 ? "-" : Instant.ofEpochMilli(endOpensAt).toString();
        return "endOpen=" + isEndOpen()
                + ", opensAt=" + opens
                + ", firstDragonSpawned=" + firstDragonSpawned
                + ", lastAutoSpawn=" + (lastAutoSpawnKey.isBlank() ? "-" : lastAutoSpawnKey);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (!endOpen && endOpensAt > 0 && now >= endOpensAt) {
            endOpen = true;
            save();
            Bukkit.broadcastMessage("§5End открыт. Дракон появится через 5 минут.");
        }

        if (endOpen && !firstDragonSpawned && endOpensAt > 0 && now >= endOpensAt + Duration.ofMinutes(5).toMillis()) {
            if (spawnDefaultDragon(true, "первый дракон после открытия End")) {
                firstDragonSpawned = true;
                save();
            }
        }

        ZonedDateTime moscowNow = ZonedDateTime.now(MOSCOW);
        for (LocalTime spawnTime : AUTO_SPAWN_TIMES) {
            if (isWithinCurrentMinute(moscowNow, spawnTime)) {
                String key = LocalDate.now(MOSCOW) + "-" + spawnTime;
                if (!key.equals(lastAutoSpawnKey) && endOpen && spawnDefaultDragon(true, "авто-спавн " + spawnTime + " МСК")) {
                    lastAutoSpawnKey = key;
                    save();
                }
            }
        }
    }

    private boolean isWithinCurrentMinute(ZonedDateTime now, LocalTime target) {
        return now.getHour() == target.getHour() && now.getMinute() == target.getMinute();
    }

    private boolean spawnDefaultDragon(boolean force, String reason) {
        Optional<DragonArena> arena = plugin.getDragonConfig().getArenas().values().stream()
                .filter(DragonArena::enabled)
                .min(Comparator.comparing(arenaValue -> !"ender_arena".equals(arenaValue.id())));
        if (arena.isEmpty()) {
            plugin.getLogger().warning("Cannot spawn scheduled dragon: no enabled arenas");
            return false;
        }
        DragonSpawnResult result = plugin.getDragonFightService().spawn(arena.get().id(), arena.get().dragonTypeId(), force);
        if (result.success()) {
            Bukkit.broadcastMessage("§5Запущен " + reason + ": " + arena.get().name());
            return true;
        }
        plugin.getLogger().info("Scheduled dragon skipped: " + result.message());
        return false;
    }

    public Optional<World> dragonWorld() {
        return plugin.getDragonFightService().resolveDragonWorld();
    }
}
