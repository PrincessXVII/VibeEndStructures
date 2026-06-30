package ru.vibecraft.vibeendstructures.generation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.model.StructureDefinition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class GenerationQueue {

    private final VibeEndStructuresPlugin plugin;
    private final StructureGenerator generator;

    private final Deque<SpawnChunkCollector.SpawnCandidate> queue = new ArrayDeque<>();
    private CommandSender reporter;
    private World world;
    private BukkitTask task;
    private int placed;
    private int skipped;
    private int notApplicable;
    private int tickCounter;
    private boolean running;

    public GenerationQueue(VibeEndStructuresPlugin plugin, StructureGenerator generator) {
        this.plugin = plugin;
        this.generator = generator;
    }

    public boolean isRunning() {
        return running;
    }

    public void start(CommandSender sender, World world, int radiusBlocks) {
        if (running) {
            sender.sendMessage(Component.text("Генерация уже выполняется.", NamedTextColor.RED));
            return;
        }

        if (world.getEnvironment() != World.Environment.THE_END) {
            sender.sendMessage(Component.text("Генерация доступна только для мира Энда.", NamedTextColor.RED));
            return;
        }

        List<StructureDefinition> structures = generator.enabledStructures();
        if (structures.isEmpty()) {
            sender.sendMessage(Component.text("Нет включённых структур для генерации.", NamedTextColor.RED));
            return;
        }

        int effectiveRadius = radiusBlocks > 0
                ? radiusBlocks
                : (int) (world.getWorldBorder().getSize() / 2.0);

        List<SpawnChunkCollector.SpawnCandidate> candidates = SpawnChunkCollector.collect(
                SpawnChunkCollector.WorldBounds.fromRadius(effectiveRadius),
                world.getSeed(),
                structures
        );

        if (candidates.isEmpty()) {
            sender.sendMessage(Component.text("Не найдено точек размещения в радиусе " + effectiveRadius + ".", NamedTextColor.YELLOW));
            return;
        }

        this.reporter = sender;
        this.world = world;
        this.queue.clear();
        this.queue.addAll(candidates);
        this.placed = 0;
        this.skipped = 0;
        this.notApplicable = 0;
        this.tickCounter = 0;
        this.running = true;

        sender.sendMessage(Component.text(
                "Запущена генерация: " + candidates.size() + " кандидатов, радиус " + effectiveRadius + ", мир " + world.getName(),
                NamedTextColor.GREEN
        ));

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::processTick, 1L, 1L);
    }

    public void cancel(CommandSender sender) {
        if (!running) {
            sender.sendMessage(Component.text("Генерация не запущена.", NamedTextColor.YELLOW));
            return;
        }
        stopTask();
        running = false;
        sender.sendMessage(Component.text(
                "Генерация остановлена. placed=" + placed + ", skipped=" + skipped + ", remaining=" + queue.size(),
                NamedTextColor.YELLOW
        ));
    }

    private void processTick() {
        int budget = plugin.getPluginConfig().getPlacementsPerTick();
        int processed = 0;

        while (processed < budget && !queue.isEmpty()) {
            SpawnChunkCollector.SpawnCandidate candidate = queue.poll();
            GenerationResult result = generator.attemptPlacement(
                    world,
                    candidate.chunkX(),
                    candidate.chunkZ(),
                    world.getSeed(),
                    candidate.structureId(),
                    false
            );

            switch (result) {
                case PLACED -> placed++;
                case SKIPPED -> skipped++;
                case NOT_APPLICABLE -> notApplicable++;
            }
            processed++;
        }

        tickCounter++;
        int progressInterval = plugin.getPluginConfig().getProgressIntervalTicks();
        if (tickCounter % progressInterval == 0 && running) {
            reportProgress(false);
        }

        if (queue.isEmpty()) {
            reportProgress(true);
            stopTask();
            running = false;
        }
    }

    private void reportProgress(boolean finished) {
        if (reporter == null) {
            return;
        }

        int remaining = queue.size();
        String message = "VibeEnd generate [" + world.getName() + "]: placed=" + placed
                + ", skipped=" + skipped
                + ", not-applicable=" + notApplicable
                + ", remaining=" + remaining;

        NamedTextColor color = finished ? NamedTextColor.GREEN : NamedTextColor.AQUA;
        reporter.sendMessage(Component.text(message, color));
        plugin.getLogger().info(message + (finished ? " — done" : ""));
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
