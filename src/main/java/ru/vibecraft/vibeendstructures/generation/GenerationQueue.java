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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

public final class GenerationQueue {

    private final VibeEndStructuresPlugin plugin;
    private final StructureGenerator generator;

    private final Deque<SpawnChunkCollector.SpawnCandidate> queue = new ArrayDeque<>();
    private CommandSender reporter;
    private World world;
    private BukkitTask task;
    private List<StructureDefinition> structures = List.of();
    private ChunkScanner scanner;
    private int effectiveRadius;
    private int placed;
    private int skipped;
    private int notApplicable;
    private int foundCandidates;
    private int tickCounter;
    private boolean running;
    private final EnumMap<GenerationResult, Integer> resultBreakdown = new EnumMap<>(GenerationResult.class);

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

        List<StructureDefinition> enabled = new ArrayList<>(generator.enabledStructures());
        if (enabled.isEmpty()) {
            sender.sendMessage(Component.text("Нет включённых структур для генерации.", NamedTextColor.RED));
            return;
        }

        int requestedRadius = radiusBlocks > 0 ? radiusBlocks : defaultRadius(world);
        int maxRadius = Math.max(512, plugin.getPluginConfig().getMaxGenerateRadius());
        effectiveRadius = Math.min(requestedRadius, maxRadius);
        if (effectiveRadius < requestedRadius) {
            sender.sendMessage(Component.text(
                    "Радиус ограничен до " + effectiveRadius + " блоков. Измени generation.max-radius, если нужно больше.",
                    NamedTextColor.YELLOW
            ));
        }

        Collections.shuffle(enabled, new Random(world.getSeed() ^ effectiveRadius ^ 0x51A7E5EEDL));
        this.reporter = sender;
        this.world = world;
        this.structures = List.copyOf(enabled);
        this.scanner = new ChunkScanner(SpawnChunkCollector.WorldBounds.fromRadius(effectiveRadius));
        this.queue.clear();
        this.placed = 0;
        this.skipped = 0;
        this.notApplicable = 0;
        this.foundCandidates = 0;
        this.tickCounter = 0;
        this.resultBreakdown.clear();
        this.running = true;

        sender.sendMessage(Component.text(
                "Запущена генерация: сканирование " + scanner.totalChunks()
                        + " чанков, радиус " + effectiveRadius
                        + ", структур " + structures.size()
                        + ", мир " + world.getName(),
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
                "Генерация остановлена. placed=" + placed
                        + ", skipped=" + skipped
                        + ", queued=" + queue.size()
                        + ", scanned=" + scannedProgress(),
                NamedTextColor.YELLOW
        ));
    }

    private void processTick() {
        fillQueue();
        processPlacements();

        tickCounter++;
        int progressInterval = Math.max(20, plugin.getPluginConfig().getProgressIntervalTicks());
        if (tickCounter % progressInterval == 0 && running) {
            reportProgress(false);
        }

        if (scanner.isDone() && queue.isEmpty()) {
            reportProgress(true);
            stopTask();
            running = false;
        }
    }

    private void fillQueue() {
        int maxQueued = Math.max(1, plugin.getPluginConfig().getMaxQueuedCandidates());
        int scanBudget = Math.max(1, plugin.getPluginConfig().getScanChunksPerTick());
        int scannedThisTick = 0;
        while (!scanner.isDone() && queue.size() < maxQueued && scannedThisTick < scanBudget) {
            ChunkPos pos = scanner.next();
            scannedThisTick++;
            if (pos == null || !isInsideRadius(pos.chunkX(), pos.chunkZ())) {
                continue;
            }
            List<SpawnChunkCollector.SpawnCandidate> candidates = candidatesFor(pos.chunkX(), pos.chunkZ());
            if (candidates.isEmpty()) {
                continue;
            }
            foundCandidates += candidates.size();
            queue.addAll(candidates);
        }
    }

    private void processPlacements() {
        int budget = Math.max(1, plugin.getPluginConfig().getPlacementsPerTick());
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
                case NOT_APPLICABLE -> notApplicable++;
                default -> {
                    if (result.isSkipped()) {
                        skipped++;
                    } else {
                        notApplicable++;
                    }
                }
            }
            resultBreakdown.merge(result, 1, Integer::sum);
            processed++;
        }
    }

    private List<SpawnChunkCollector.SpawnCandidate> candidatesFor(int chunkX, int chunkZ) {
        List<StructureDefinition> shuffled = new ArrayList<>(structures);
        Collections.shuffle(shuffled, new Random(world.getSeed() ^ ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L)));

        List<SpawnChunkCollector.SpawnCandidate> result = new ArrayList<>();
        int maxPerChunk = Math.max(1, plugin.getPluginConfig().getMaxStructuresPerChunk());
        for (StructureDefinition definition : shuffled) {
            if (!PlacementEngine.isSpawnChunk(
                    world.getSeed(),
                    chunkX,
                    chunkZ,
                    definition.spacing(),
                    definition.separation(),
                    definition.salt()
            )) {
                continue;
            }
            result.add(new SpawnChunkCollector.SpawnCandidate(chunkX, chunkZ, definition.id()));
            if (result.size() >= maxPerChunk) {
                break;
            }
        }
        return result;
    }

    private boolean isInsideRadius(int chunkX, int chunkZ) {
        long centerX = (long) chunkX * 16L + 8L;
        long centerZ = (long) chunkZ * 16L + 8L;
        long radius = effectiveRadius;
        return centerX * centerX + centerZ * centerZ <= radius * radius;
    }

    private int defaultRadius(World world) {
        int configured = Math.max(512, plugin.getPluginConfig().getDefaultGenerateRadius());
        double borderRadius = world.getWorldBorder().getSize() / 2.0;
        if (Double.isFinite(borderRadius) && borderRadius > 0 && borderRadius < configured) {
            return Math.max(512, (int) borderRadius);
        }
        return configured;
    }

    private void reportProgress(boolean finished) {
        if (reporter == null) {
            return;
        }

        String message = "VibeEnd generate [" + world.getName() + "]: scanned=" + scannedProgress()
                + ", found=" + foundCandidates
                + ", placed=" + placed
                + ", skipped=" + skipped
                + skipBreakdown()
                + ", not-applicable=" + notApplicable
                + ", queued=" + queue.size();

        NamedTextColor color = finished ? NamedTextColor.GREEN : NamedTextColor.AQUA;
        reporter.sendMessage(Component.text(message, color));
        plugin.getLogger().info(message + (finished ? " — done" : ""));
    }

    private String scannedProgress() {
        if (scanner == null) {
            return "0/0";
        }
        return scanner.scannedChunks() + "/" + scanner.totalChunks() + " (" + scanner.percent() + "%)";
    }

    private String skipBreakdown() {
        if (skipped <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(" {");
        boolean first = true;
        for (GenerationResult result : GenerationResult.values()) {
            if (!result.isSkipped()) {
                continue;
            }
            int count = resultBreakdown.getOrDefault(result, 0);
            if (count <= 0) {
                continue;
            }
            if (!first) {
                builder.append(", ");
            }
            builder.append(result.reasonKey()).append("=").append(count);
            first = false;
        }
        builder.append("}");
        return first ? "" : builder.toString();
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private record ChunkPos(int chunkX, int chunkZ) {
    }

    private static final class ChunkScanner {
        private final int minChunkX;
        private final int maxChunkX;
        private final int minChunkZ;
        private final int maxChunkZ;
        private final long totalChunks;
        private int chunkX;
        private int chunkZ;
        private long scannedChunks;
        private boolean done;

        private ChunkScanner(SpawnChunkCollector.WorldBounds bounds) {
            this.minChunkX = Math.floorDiv(bounds.minBlockX(), 16);
            this.maxChunkX = Math.floorDiv(bounds.maxBlockX(), 16);
            this.minChunkZ = Math.floorDiv(bounds.minBlockZ(), 16);
            this.maxChunkZ = Math.floorDiv(bounds.maxBlockZ(), 16);
            this.chunkX = minChunkX;
            this.chunkZ = minChunkZ;
            this.totalChunks = (long) (maxChunkX - minChunkX + 1) * (long) (maxChunkZ - minChunkZ + 1);
        }

        private ChunkPos next() {
            if (done) {
                return null;
            }
            ChunkPos pos = new ChunkPos(chunkX, chunkZ);
            scannedChunks++;
            if (chunkZ >= maxChunkZ) {
                chunkZ = minChunkZ;
                chunkX++;
            } else {
                chunkZ++;
            }
            if (chunkX > maxChunkX) {
                done = true;
            }
            return pos;
        }

        private boolean isDone() {
            return done;
        }

        private long scannedChunks() {
            return scannedChunks;
        }

        private long totalChunks() {
            return totalChunks;
        }

        private long percent() {
            if (totalChunks <= 0) {
                return 100;
            }
            return Math.min(100, scannedChunks * 100L / totalChunks);
        }
    }
}
