package ru.vibecraft.vibeendstructures.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.generation.PlacementAnchorResolver;
import ru.vibecraft.vibeendstructures.model.StructureDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class VibeEndCommand implements CommandExecutor, TabCompleter {

    private final VibeEndStructuresPlugin plugin;

    public VibeEndCommand(VibeEndStructuresPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("vibeend.admin")) {
            sender.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(Component.text("VibeEndStructures перезагружен.", NamedTextColor.GREEN));
            }
            case "list" -> {
                List<String> ids = plugin.getRegistry().getDefinitions().stream()
                        .map(StructureDefinition::id)
                        .sorted()
                        .toList();
                sender.sendMessage(Component.text("Структуры (" + ids.size() + "): " + String.join(", ", ids), NamedTextColor.AQUA));
            }
            case "paste" -> handlePaste(sender, args);
            case "generate" -> handleGenerate(sender, args);
            case "generatecancel", "cancelgenerate" -> plugin.getGenerationQueue().cancel(sender);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handlePaste(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Только для игроков.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /vibeend paste <structure>", NamedTextColor.YELLOW));
            return;
        }

        String id = args[1].toLowerCase();
        StructureDefinition definition = plugin.getRegistry().getDefinition(id).orElse(null);
        if (definition == null) {
            sender.sendMessage(Component.text("Структура не найдена: " + id, NamedTextColor.RED));
            return;
        }

        Random random = new Random();
        Location anchor = PlacementAnchorResolver.resolveAt(
                player.getWorld(),
                player.getLocation(),
                definition,
                random
        ).orElse(null);

        if (anchor == null) {
            sender.sendMessage(Component.text("Не удалось найти точку размещения.", NamedTextColor.RED));
            return;
        }

        var result = plugin.getPlacer().placeAt(
                anchor,
                definition,
                StructureRotation.NONE,
                Mirror.NONE
        );

        if (result.success()) {
            sender.sendMessage(Component.text("Структура " + id + " установлена.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Не удалось установить структуру. Смотри консоль сервера.", NamedTextColor.RED));
        }
    }

    private void handleGenerate(CommandSender sender, String[] args) {
        World world = resolveWorld(sender, args);
        if (world == null) {
            return;
        }

        int radius = 0;
        if (args.length >= 3) {
            try {
                radius = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text("Радиус должен быть числом.", NamedTextColor.RED));
                return;
            }
            if (radius < 0) {
                sender.sendMessage(Component.text("Радиус не может быть отрицательным.", NamedTextColor.RED));
                return;
            }
        }

        plugin.getGenerationQueue().start(sender, world, radius);
    }

    private @Nullable World resolveWorld(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            World world = Bukkit.getWorld(args[1]);
            if (world == null) {
                sender.sendMessage(Component.text("Мир не найден: " + args[1], NamedTextColor.RED));
                return null;
            }
            return world;
        }

        if (sender instanceof Player player) {
            return player.getWorld();
        }

        List<String> configured = plugin.getPluginConfig().getWorlds();
        if (!configured.isEmpty()) {
            World world = Bukkit.getWorld(configured.getFirst());
            if (world != null) {
                return world;
            }
        }

        sender.sendMessage(Component.text("Укажите мир: /vibeend generate <world> [radius]", NamedTextColor.YELLOW));
        return null;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Использование:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibeend list", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibeend paste <structure>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibeend generate [world] [radius]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibeend generatecancel", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibeend reload", NamedTextColor.GRAY));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("vibeend.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("list", "paste", "generate", "generatecancel", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("paste")) {
            return filter(plugin.getRegistry().getDefinitions().stream().map(StructureDefinition::id).sorted().toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("generate")) {
            return filter(Bukkit.getWorlds().stream().map(World::getName).toList(), args[1]);
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
}
