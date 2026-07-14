package ru.vibecraft.vibeendstructures.dragon.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class DragonScheduleService {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final List<LocalTime> AUTO_SPAWN_TIMES = List.of(LocalTime.of(16, 0), LocalTime.of(21, 0));

    private final VibeEndStructuresPlugin plugin;
    private final File file;
    private final Random random = new Random();
    private BukkitTask task;
    private boolean endOpen = true;
    private long endOpensAt;
    private boolean firstDragonSpawned = true;
    private String lastAutoSpawnKey = "";
    private BossBar scheduleBossBar;

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
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        hideScheduleBossBar();
        save();
    }

    public void startWipeCountdown(Duration delay) {
        endOpen = false;
        endOpensAt = System.currentTimeMillis() + delay.toMillis();
        firstDragonSpawned = false;
        lastAutoSpawnKey = "";
        hideScheduleBossBar();
        save();
        Bukkit.broadcast(Component.text("§5End откроется через " + delay.toHours() + " ч. После открытия дракон появится через 5 минут."));
    }

    public void forceOpen() {
        endOpen = true;
        endOpensAt = System.currentTimeMillis();
        firstDragonSpawned = false;
        save();
        Bukkit.broadcast(Component.text("§5End открыт. Первый дракон появится через 5 минут, если ещё не был призван."));
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

    public void markFirstDragonSpawned() {
        if (!firstDragonSpawned) {
            firstDragonSpawned = true;
            save();
        }
    }

    public String status() {
        String opens = endOpensAt <= 0 ? "-" : Instant.ofEpochMilli(endOpensAt).toString();
        return "endOpen=" + isEndOpen()
                + ", opensAt=" + opens
                + ", firstDragonSpawned=" + firstDragonSpawned
                + ", lastAutoSpawn=" + (lastAutoSpawnKey.isBlank() ? "-" : lastAutoSpawnKey);
    }

    public void syncPlayerBossBar(Player player) {
        if (scheduleBossBar == null) {
            return;
        }
        if (isInEnd(player)) {
            if (!scheduleBossBar.getPlayers().contains(player)) {
                scheduleBossBar.addPlayer(player);
            }
        } else {
            scheduleBossBar.removePlayer(player);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (!endOpen && endOpensAt > 0 && now >= endOpensAt) {
            endOpen = true;
            save();
            Bukkit.broadcast(Component.text("§5End открыт. Дракон появится через 5 минут."));
        }

        updateScheduleBossBar(now);

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
                if (key.equals(lastAutoSpawnKey) || !endOpen) {
                    continue;
                }
                if (!hasPlayersInEnd()) {
                    lastAutoSpawnKey = key;
                    save();
                    plugin.getLogger().info("Авто-спавн " + spawnTime + " МСК пропущен: в Энде нет игроков");
                    continue;
                }
                if (spawnRandomScheduledDragon(true, "авто-спавн " + spawnTime + " МСК")) {
                    lastAutoSpawnKey = key;
                    firstDragonSpawned = true;
                    save();
                }
            }
        }
    }

    private boolean hasPlayersInEnd() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isInEnd(player)) {
                return true;
            }
        }
        return false;
    }

    private void updateScheduleBossBar(long now) {
        if (plugin.getDragonFightService().hasOngoingDragonEncounter()) {
            hideScheduleBossBar();
            return;
        }

        ScheduleBarMode mode = resolveBarMode(now);
        if (mode == ScheduleBarMode.NONE) {
            hideScheduleBossBar();
            return;
        }

        ensureScheduleBossBar();
        if (mode == ScheduleBarMode.FIRST_SPAWN) {
            long spawnAt = endOpensAt + Duration.ofMinutes(5).toMillis();
            long remainingMillis = Math.max(0, spawnAt - now);
            scheduleBossBar.setTitle(formatCountdown("§dДо спавна дракона: ", remainingMillis));
            scheduleBossBar.setProgress(Math.max(0.0, Math.min(1.0, remainingMillis / (double) Duration.ofMinutes(5).toMillis())));
        } else {
            ZonedDateTime moscowNow = ZonedDateTime.now(MOSCOW);
            ZonedDateTime next = nextAutoSpawnTime(moscowNow);
            ZonedDateTime previous = previousAutoSpawnTime(moscowNow);
            long remainingMillis = Math.max(0, Duration.between(moscowNow, next).toMillis());
            long totalMillis = Math.max(1, Duration.between(previous, next).toMillis());
            scheduleBossBar.setTitle(formatCountdown("§dСледующий дракон появится: ", remainingMillis));
            scheduleBossBar.setProgress(Math.max(0.0, Math.min(1.0, remainingMillis / (double) totalMillis)));
        }

        syncBossBarToEndPlayers();
    }

    private ScheduleBarMode resolveBarMode(long now) {
        if (!isEndOpen()) {
            return ScheduleBarMode.NONE;
        }
        if (!firstDragonSpawned && endOpensAt > 0) {
            long spawnAt = endOpensAt + Duration.ofMinutes(5).toMillis();
            if (now < spawnAt) {
                return ScheduleBarMode.FIRST_SPAWN;
            }
        }
        return ScheduleBarMode.NEXT_SCHEDULED;
    }

    private void ensureScheduleBossBar() {
        if (scheduleBossBar == null) {
            scheduleBossBar = Bukkit.createBossBar("§dДракон", BarColor.PURPLE, BarStyle.SOLID);
            scheduleBossBar.setVisible(true);
        }
    }

    private void syncBossBarToEndPlayers() {
        if (scheduleBossBar == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isInEnd(player)) {
                if (!scheduleBossBar.getPlayers().contains(player)) {
                    scheduleBossBar.addPlayer(player);
                }
            } else if (scheduleBossBar.getPlayers().contains(player)) {
                scheduleBossBar.removePlayer(player);
            }
        }
    }

    private void hideScheduleBossBar() {
        if (scheduleBossBar == null) {
            return;
        }
        scheduleBossBar.removeAll();
        scheduleBossBar.setVisible(false);
        scheduleBossBar = null;
    }

    private boolean isInEnd(Player player) {
        World world = player.getWorld();
        return world != null && world.getEnvironment() == World.Environment.THE_END;
    }

    private String formatCountdown(String prefix, long remainingMillis) {
        long totalSeconds = Math.max(0, remainingMillis / 1000L);
        if (totalSeconds >= 3600) {
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            return String.format("%s%d:%02d:%02d", prefix, hours, minutes, seconds);
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%s%02d:%02d", prefix, minutes, seconds);
    }

    private ZonedDateTime nextAutoSpawnTime(ZonedDateTime now) {
        LocalDate date = now.toLocalDate();
        for (LocalTime time : AUTO_SPAWN_TIMES) {
            ZonedDateTime candidate = ZonedDateTime.of(date, time, MOSCOW);
            if (candidate.isAfter(now)) {
                return candidate;
            }
        }
        return ZonedDateTime.of(date.plusDays(1), AUTO_SPAWN_TIMES.getFirst(), MOSCOW);
    }

    private ZonedDateTime previousAutoSpawnTime(ZonedDateTime now) {
        LocalDate date = now.toLocalDate();
        ZonedDateTime last = null;
        for (LocalTime time : AUTO_SPAWN_TIMES) {
            ZonedDateTime candidate = ZonedDateTime.of(date, time, MOSCOW);
            if (!candidate.isAfter(now)) {
                last = candidate;
            }
        }
        if (last != null) {
            return last;
        }
        return ZonedDateTime.of(date.minusDays(1), AUTO_SPAWN_TIMES.getLast(), MOSCOW);
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
            Bukkit.broadcast(Component.text("§5Запущен " + reason + ": " + arena.get().name()));
            return true;
        }
        plugin.getLogger().info("Scheduled dragon skipped: " + result.message());
        return false;
    }

    private boolean spawnRandomScheduledDragon(boolean force, String reason) {
        List<DragonDefinition> dragons = plugin.getDragonConfig().getDragonDefinitions().values().stream()
                .filter(DragonDefinition::enabled)
                .toList();
        if (dragons.isEmpty()) {
            plugin.getLogger().warning("Cannot spawn scheduled dragon: no enabled dragon types");
            return false;
        }

        List<DragonDefinition> shuffled = new ArrayList<>(dragons);
        Collections.shuffle(shuffled, random);
        for (DragonDefinition definition : shuffled) {
            Optional<DragonArena> arena = plugin.getDragonConfig().getArenas().values().stream()
                    .filter(DragonArena::enabled)
                    .filter(value -> value.dragonTypeId().equals(definition.id()))
                    .findFirst();
            if (arena.isEmpty()) {
                continue;
            }
            DragonSpawnResult result = plugin.getDragonFightService().spawnScheduled(arena.get().id(), definition.id(), force);
            if (result.success()) {
                Bukkit.broadcast(Component.text("§5Запущен " + reason + ": " + definition.displayName() + " (" + arena.get().name() + ")"));
                return true;
            }
            plugin.getLogger().info("Scheduled dragon skipped for " + definition.id() + ": " + result.message());
        }
        return false;
    }

    public Optional<World> dragonWorld() {
        return plugin.getDragonFightService().resolveDragonWorld();
    }

    private enum ScheduleBarMode {
        NONE,
        FIRST_SPAWN,
        NEXT_SCHEDULED
    }
}
