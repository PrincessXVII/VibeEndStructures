package ru.vibecraft.vibeendstructures.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.dragon.contribution.ContributionResult;
import ru.vibecraft.vibeendstructures.dragon.contribution.ContributionSnapshot;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;
import ru.vibecraft.vibeendstructures.dragon.runtime.DragonSpawnResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.Duration;

public final class VibeDragonCommand implements CommandExecutor, TabCompleter {

    private final VibeEndStructuresPlugin plugin;

    public VibeDragonCommand(VibeEndStructuresPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!canUseDragonCommand(sender)) {
            sender.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn" -> handleSpawn(sender, args);
            case "despawn" -> handleDespawn(sender, args);
            case "list" -> handleList(sender);
            case "arena" -> handleArena(sender, args);
            case "cooldown" -> handleCooldown(sender, args);
            case "wipe" -> handleWipe(sender, args);
            case "seed" -> handleSeed(sender);
            case "contribute", "contribution" -> handleContribute(sender, args);
            case "reward" -> handleReward(sender, args);
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(Component.text("VibeDragon конфиг перезагружен.", NamedTextColor.GREEN));
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /vibedragon spawn <type> [arena]", NamedTextColor.YELLOW));
            return;
        }
        String typeId = args[1].toLowerCase();
        String arenaId = args.length >= 3 ? args[2].toLowerCase() : findArenaFor(typeId);
        if (arenaId == null) {
            sender.sendMessage(Component.text("Не найдена арена для дракона: " + typeId, NamedTextColor.RED));
            return;
        }

        DragonSpawnResult result = plugin.getDragonFightService().spawn(arenaId, typeId, false);
        sender.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void handleDespawn(CommandSender sender, String[] args) {
        String arenaId = args.length >= 2 ? args[1].toLowerCase() : defaultArenaId();
        if (arenaId == null) {
            sender.sendMessage(Component.text("Арены не найдены.", NamedTextColor.RED));
            return;
        }
        DragonSpawnResult result = plugin.getDragonFightService().despawn(arenaId);
        sender.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private String findArenaFor(String typeId) {
        return plugin.getDragonConfig().getArenas().values().stream()
                .filter(DragonArena::enabled)
                .filter(arena -> arena.dragonTypeId().equalsIgnoreCase(typeId))
                .map(DragonArena::id)
                .sorted()
                .findFirst()
                .orElseGet(() -> plugin.getDragonConfig().getArenas().values().stream()
                        .filter(DragonArena::enabled)
                        .map(DragonArena::id)
                        .sorted()
                        .findFirst()
                        .orElse(null));
    }

    private void handleList(CommandSender sender) {
        List<String> dragons = plugin.getDragonConfig().getDragonDefinitions().values().stream()
                .sorted(Comparator.comparing(DragonDefinition::id))
                .map(def -> def.id() + (def.enabled() ? "" : " (off)") + " hp=" + def.health())
                .toList();
        List<String> arenas = plugin.getDragonConfig().getArenas().values().stream()
                .sorted(Comparator.comparing(DragonArena::id))
                .map(arena -> arena.id() + " [" + arena.state() + "] type=" + arena.dragonTypeId()
                        + " center=" + arena.centerX() + "," + arena.height() + "," + arena.centerZ())
                .toList();

        sender.sendMessage(Component.text("Драконы (" + dragons.size() + "): " + String.join(", ", dragons), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Арены (" + arenas.size() + "): " + String.join(", ", arenas), NamedTextColor.AQUA));
    }

    private void handleArena(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            handleArenaList(sender);
            return;
        }
        if (args[1].equalsIgnoreCase("set")) {
            handleArenaSet(sender, args);
            return;
        }
        sender.sendMessage(Component.text("Использование: /vibedragon arena list | /vibedragon arena set <id> [radius]", NamedTextColor.YELLOW));
    }

    private void handleArenaList(CommandSender sender) {
        for (DragonArena arena : plugin.getDragonConfig().getArenas().values().stream()
                .sorted(Comparator.comparing(DragonArena::id))
                .toList()) {
            String cooldown = arena.cooldownUntil() <= 0 ? "-" : String.valueOf(Math.max(0, (arena.cooldownUntil() - System.currentTimeMillis()) / 1000));
            sender.sendMessage(Component.text(arena.id()
                    + " state=" + arena.state()
                    + " type=" + arena.dragonTypeId()
                    + " center=" + arena.centerX() + "," + arena.height() + "," + arena.centerZ()
                    + " radius=" + arena.radius()
                    + " cooldownSec=" + cooldown, NamedTextColor.GRAY));
        }
    }

    private void handleArenaSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Только игрок может задать арену по текущей позиции.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Использование: /vibedragon arena set <id> [radius]", NamedTextColor.YELLOW));
            return;
        }
        String arenaId = args[2].toLowerCase();
        int radius = args.length >= 4 ? parsePositiveInt(sender, args[3], "Радиус") : 120;
        if (radius < 0) {
            return;
        }
        Location loc = player.getLocation();
        DragonArena current = plugin.getDragonConfig().getArena(arenaId);
        DragonArena updated;
        if (current == null) {
            String dragonType = plugin.getDragonConfig().getDragonDefinitions().keySet().stream().sorted().findFirst().orElse("ender_dragon");
            updated = new DragonArena(arenaId, arenaId, dragonType, loc.getBlockX(), loc.getBlockZ(), radius, loc.getBlockY(), 24, "", true, null, 0, DragonArena.ArenaState.IDLE);
        } else {
            updated = current.withPosition(loc.getBlockX(), loc.getBlockZ(), loc.getBlockY(), radius);
        }
        plugin.getDragonConfig().updateArena(updated);
        sender.sendMessage(Component.text("Арена сохранена: " + arenaId, NamedTextColor.GREEN));
    }

    private void handleCooldown(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Использование: /vibedragon cooldown <arena> <hours>", NamedTextColor.YELLOW));
            return;
        }
        DragonArena arena = plugin.getDragonConfig().getArena(args[1].toLowerCase());
        if (arena == null) {
            sender.sendMessage(Component.text("Арена не найдена: " + args[1], NamedTextColor.RED));
            return;
        }
        int hours = parsePositiveInt(sender, args[2], "Cooldown");
        if (hours < 0) {
            return;
        }
        plugin.getDragonConfig().updateArena(arena.withCooldownHours(hours));
        sender.sendMessage(Component.text("Cooldown арены " + arena.id() + " обновлён: " + hours + " ч.", NamedTextColor.GREEN));
    }

    private void handleWipe(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(Component.text("Dragon schedule: " + plugin.getDragonScheduleService().status(), NamedTextColor.AQUA));
            return;
        }
        if (args[1].equalsIgnoreCase("start")) {
            int hours = args.length >= 3 ? parsePositiveInt(sender, args[2], "Часы") : 6;
            if (hours < 0) {
                return;
            }
            plugin.getDragonScheduleService().startWipeCountdown(Duration.ofHours(hours));
            sender.sendMessage(Component.text("Отсчёт после вайпа запущен: End откроется через " + hours + " ч.", NamedTextColor.GREEN));
            return;
        }
        if (args[1].equalsIgnoreCase("open")) {
            plugin.getDragonScheduleService().forceOpen();
            sender.sendMessage(Component.text("End открыт вручную.", NamedTextColor.GREEN));
            return;
        }
        sender.sendMessage(Component.text("Использование: /vibedragon wipe start [hours] | status | open", NamedTextColor.YELLOW));
    }

    private void handleSeed(CommandSender sender) {
        World world = plugin.getDragonFightService().resolveDragonWorld().orElse(null);
        if (world == null) {
            sender.sendMessage(Component.text("End-мир не найден.", NamedTextColor.RED));
            return;
        }
        DragonBattle battle = world.getEnderDragonBattle();
        Location portal = battle == null ? null : battle.getEndPortalLocation();
        sender.sendMessage(Component.text("World: " + world.getName() + ", seed=" + world.getSeed(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("End portal: " + formatLocation(portal), NamedTextColor.GRAY));
        for (DragonArena arena : plugin.getDragonConfig().getArenas().values().stream()
                .sorted(Comparator.comparing(DragonArena::id))
                .toList()) {
            sender.sendMessage(Component.text(arena.id() + " center=" + arena.centerX() + "," + arena.height() + "," + arena.centerZ()
                    + " radius=" + arena.radius() + " type=" + arena.dragonTypeId(), NamedTextColor.GRAY));
        }
    }

    private void handleContribute(CommandSender sender, String[] args) {
        String arenaId = args.length >= 2 ? args[1].toLowerCase() : defaultArenaId();
        if (arenaId == null) {
            sender.sendMessage(Component.text("Арены не найдены.", NamedTextColor.RED));
            return;
        }
        ContributionSnapshot snapshot = plugin.getDragonContributionTracker().bestSnapshot(arenaId).orElse(null);
        if (snapshot == null || snapshot.results().isEmpty()) {
            sender.sendMessage(Component.text("Нет данных вклада для арены: " + arenaId, NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("Вклад арены " + arenaId + ":", NamedTextColor.AQUA));
        int limit = Math.min(5, snapshot.results().size());
        for (int i = 0; i < limit; i++) {
            ContributionResult result = snapshot.results().get(i);
            String line = (i + 1) + ". " + result.playerName()
                    + " — " + percent(result.contribution())
                    + " dmg=" + round(result.damageDealt())
                    + " heal=" + round(result.healingDone())
                    + " blocks=" + result.blocksPlaced()
                    + " deaths=" + result.deaths();
            sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
    }

    private void handleReward(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Использование: /vibedragon reward <player> <type> [tier]", NamedTextColor.YELLOW));
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage(Component.text("Игрок должен быть онлайн для ручной выдачи предметов: " + args[1], NamedTextColor.RED));
            return;
        }
        DragonDefinition definition = plugin.getDragonConfig().getDragon(args[2].toLowerCase());
        if (definition == null) {
            sender.sendMessage(Component.text("Тип дракона не найден: " + args[2], NamedTextColor.RED));
            return;
        }
        String tier = args.length >= 4 ? args[3].toLowerCase() : "";
        boolean rewarded = plugin.getRewardDistributor().rewardManually(player, definition, tier);
        sender.sendMessage(Component.text(rewarded ? "Награда выдана." : "Награда не найдена.", rewarded ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private String defaultArenaId() {
        return plugin.getDragonConfig().getArenas().keySet().stream().sorted().findFirst().orElse(null);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Использование:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibedragon spawn <type> [arena]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibedragon despawn [arena]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibedragon list", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibedragon arena list", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibedragon arena set <id> [radius]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibedragon cooldown <arena> <hours>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibedragon wipe start [hours] | status | open", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibedragon seed", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibedragon contribute [arena]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibedragon reward <player> <type> [tier]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibedragon reload", NamedTextColor.GRAY));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!canUseDragonCommand(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("spawn", "despawn", "list", "arena", "cooldown", "wipe", "seed", "contribute", "reward", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("wipe")) {
            return filter(List.of("start", "status", "open"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("despawn")) {
            return filter(plugin.getDragonConfig().getArenas().keySet().stream().sorted().toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("arena")) {
            return filter(List.of("list", "set"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("set")) {
            return filter(plugin.getDragonConfig().getArenas().keySet().stream().sorted().toList(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cooldown")) {
            return filter(plugin.getDragonConfig().getArenas().keySet().stream().sorted().toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return filter(plugin.getDragonConfig().getDragonDefinitions().keySet().stream().sorted().toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) {
            return filter(plugin.getDragonConfig().getArenas().keySet().stream().sorted().toList(), args[2]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("contribute") || args[0].equalsIgnoreCase("contribution"))) {
            return filter(plugin.getDragonConfig().getArenas().keySet().stream().sorted().toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reward")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reward")) {
            return filter(plugin.getDragonConfig().getDragonDefinitions().keySet().stream().sorted().toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("reward")) {
            return filter(List.of("common", "uncommon", "rare", "epic", "legendary"), args[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    private boolean canUseDragonCommand(CommandSender sender) {
        return sender.hasPermission("vibedragon.admin") || sender.hasPermission("vibedragon.tester");
    }

    private String percent(double value) {
        return String.format(java.util.Locale.US, "%.1f%%", value * 100.0);
    }

    private String formatLocation(Location location) {
        if (location == null) {
            return "-";
        }
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private long round(double value) {
        return Math.round(value);
    }

    private int parsePositiveInt(CommandSender sender, String raw, String label) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                sender.sendMessage(Component.text(label + " не может быть отрицательным.", NamedTextColor.RED));
                return -1;
            }
            return value;
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text(label + " должен быть числом.", NamedTextColor.RED));
            return -1;
        }
    }
}
