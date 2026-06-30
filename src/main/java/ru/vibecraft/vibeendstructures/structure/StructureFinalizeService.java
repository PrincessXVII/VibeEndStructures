package ru.vibecraft.vibeendstructures.structure;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import ru.vibecraft.vibeendstructures.model.LootContainer;
import ru.vibecraft.vibeendstructures.model.StructureEntitySpawn;
import ru.vibecraft.vibeendstructures.model.StructureItemFrame;
import ru.vibecraft.vibeendstructures.model.StructureMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

public final class StructureFinalizeService {

    private final Logger logger;
    private final Set<String> missingLootTables = new HashSet<>();

    public StructureFinalizeService(Logger logger) {
        this.logger = logger;
    }

    public void finalizePiece(
            World world,
            Location anchor,
            StructureRotation rotation,
            Mirror mirror,
            StructureMeta meta,
            Random random
    ) {
        if (meta == null) {
            return;
        }

        BlockVector pieceSize = JigsawMath.toSizeVector(meta.size());
        StructureBounds bounds = StructureBounds.fromAnchor(anchor, pieceSize, rotation, mirror);
        cleanTechnical(world, anchor, meta, pieceSize, rotation, mirror);
        removeVanillaItemFrames(world, bounds);
        applyLoot(world, anchor, meta, pieceSize, rotation, mirror, random);
        spawnItemFrames(world, anchor, meta, pieceSize, rotation, mirror);
        spawnAllowedEntities(world, anchor, meta, pieceSize, rotation, mirror);
    }

    public void maybePlaceShipLimeShulkerBox(
            World world,
            List<ShipPieceRef> pieces,
            StructureMetaRegistry metaRegistry,
            StructureRotation rotation,
            Mirror mirror,
            Random random
    ) {
        if (!random.nextBoolean()) {
            return;
        }

        List<Block> containers = new ArrayList<>();
        for (ShipPieceRef piece : pieces) {
            metaRegistry.get(piece.resourcePath()).ifPresent(meta -> {
                BlockVector pieceSize = JigsawMath.toSizeVector(meta.size());
                BlockVector anchorVec = toAnchorVector(piece.anchor());
                for (LootContainer loot : meta.lootContainers()) {
                    Vector pos = transformedPos(anchorVec, loot.pos(), pieceSize, rotation, mirror);
                    Block block = world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
                    if (isLootContainerMaterial(block.getType())) {
                        containers.add(block);
                    }
                }
            });
        }

        if (containers.isEmpty()) {
            return;
        }

        Block block = containers.get(random.nextInt(containers.size()));
        BlockState state = block.getState();
        if (state instanceof Container container) {
            container.getInventory().addItem(new ItemStack(Material.LIME_SHULKER_BOX, 1));
        }
    }

    public void refinePiece(
            World world,
            Location anchor,
            StructureRotation rotation,
            Mirror mirror,
            StructureMeta meta,
            Random random
    ) {
        if (meta == null) {
            return;
        }

        BlockVector pieceSize = JigsawMath.toSizeVector(meta.size());
        StructureBounds bounds = StructureBounds.fromAnchor(anchor, pieceSize, rotation, mirror);
        cleanTechnical(world, anchor, meta, pieceSize, rotation, mirror);
        sweepTechnicalBounds(world, bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
        removeVanillaItemFrames(world, bounds);
        applyLoot(world, anchor, meta, pieceSize, rotation, mirror, random);
        spawnItemFrames(world, anchor, meta, pieceSize, rotation, mirror);
    }

    public void sweepTechnicalBounds(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    replaceTechnicalBlock(world, x, y, z);
                }
            }
        }
    }

    public void removeDisallowedEntities(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Location center = new Location(world, (minX + maxX) / 2.0 + 0.5, (minY + maxY) / 2.0 + 0.5, (minZ + maxZ) / 2.0 + 0.5);
        double radiusX = (maxX - minX) / 2.0 + 4;
        double radiusY = (maxY - minY) / 2.0 + 4;
        double radiusZ = (maxZ - minZ) / 2.0 + 4;
        for (Entity entity : world.getNearbyEntities(center, radiusX, radiusY, radiusZ)) {
            if (entity instanceof ItemFrame || entity instanceof GlowItemFrame || entity instanceof Shulker || entity instanceof Enderman) {
                continue;
            }
            if (entity instanceof Player) {
                continue;
            }
            if (entity instanceof LivingEntity) {
                entity.remove();
            }
        }
    }

    private void cleanTechnical(
            World world,
            Location anchor,
            StructureMeta meta,
            BlockVector pieceSize,
            StructureRotation rotation,
            Mirror mirror
    ) {
        BlockVector anchorVec = toAnchorVector(anchor);
        for (int[] technical : meta.technicalBlocks()) {
            Vector pos = transformedPos(anchorVec, technical, pieceSize, rotation, mirror);
            replaceTechnicalBlock(world, pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
        }
    }

    private void applyLoot(
            World world,
            Location anchor,
            StructureMeta meta,
            BlockVector pieceSize,
            StructureRotation rotation,
            Mirror mirror,
            Random random
    ) {
        BlockVector anchorVec = toAnchorVector(anchor);
        for (LootContainer container : meta.lootContainers()) {
            Vector pos = transformedPos(anchorVec, container.pos(), pieceSize, rotation, mirror);
            Block block = world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
            Material type = block.getType();
            if (!isLootContainerMaterial(type)) {
                continue;
            }
            BlockState state = block.getState();
            if (!(state instanceof Lootable lootable)) {
                continue;
            }
            LootTable table = resolveLootTable(container.lootTable());
            if (table == null) {
                if (missingLootTables.add(container.lootTable())) {
                    logger.warning("Loot table not found: " + container.lootTable());
                }
                continue;
            }
            long seed = random.nextLong();
            lootable.setLootTable(table, seed);
            state.update(false, false);

            if (state instanceof Container containerBlock) {
                Inventory inventory = containerBlock.getInventory();
                inventory.clear();
                LootContext context = new LootContext.Builder(
                        new Location(world, pos.getX(), pos.getY(), pos.getZ())
                ).build();
                table.fillInventory(inventory, random, context);
            }
        }
    }

    private void spawnItemFrames(
            World world,
            Location anchor,
            StructureMeta meta,
            BlockVector pieceSize,
            StructureRotation rotation,
            Mirror mirror
    ) {
        BlockVector anchorVec = toAnchorVector(anchor);
        for (StructureItemFrame frameMeta : meta.itemFrames()) {
            Vector attachPos = transformedPos(anchorVec, frameMeta.pos(), pieceSize, rotation, mirror);
            Block attach = world.getBlockAt(attachPos.getBlockX(), attachPos.getBlockY(), attachPos.getBlockZ());
            if (attach.getType().isAir()) {
                continue;
            }
            BlockFace facing = parseFacing(frameMeta.facing(), rotation, mirror);
            Location spawnLoc = frameLocation(attach, facing);
            EntityType type = "glow_item_frame".equals(frameMeta.type())
                    ? EntityType.GLOW_ITEM_FRAME
                    : EntityType.ITEM_FRAME;
            if (type == EntityType.GLOW_ITEM_FRAME) {
                world.spawn(spawnLoc, GlowItemFrame.class, frame -> configureFrame(frame, frameMeta, facing));
            } else {
                world.spawn(spawnLoc, ItemFrame.class, frame -> configureFrame(frame, frameMeta, facing));
            }
        }
    }

    private void configureFrame(ItemFrame frame, StructureItemFrame frameMeta, BlockFace facing) {
        frame.setFacingDirection(facing, true);
        frame.setFixed(true);
        frame.setInvulnerable(true);
        frame.setVisible(!frameMeta.invisible());
        if (frameMeta.item() != null) {
            Material material = Material.matchMaterial(frameMeta.item().id());
            if (material != null) {
                frame.setItem(new ItemStack(material, Math.max(1, frameMeta.item().count())));
            }
        }
    }

    private void spawnAllowedEntities(
            World world,
            Location anchor,
            StructureMeta meta,
            BlockVector pieceSize,
            StructureRotation rotation,
            Mirror mirror
    ) {
        BlockVector anchorVec = toAnchorVector(anchor);
        for (StructureEntitySpawn spawn : meta.entities()) {
            int baseX = (int) Math.floor(spawn.pos()[0]);
            int baseY = (int) Math.floor(spawn.pos()[1]);
            int baseZ = (int) Math.floor(spawn.pos()[2]);
            Vector transformed = transformedPos(anchorVec, new int[]{baseX, baseY, baseZ}, pieceSize, rotation, mirror);
            Location loc = new Location(
                    world,
                    transformed.getX() + (spawn.pos()[0] - baseX),
                    transformed.getY() + (spawn.pos()[1] - baseY),
                    transformed.getZ() + (spawn.pos()[2] - baseZ),
                    spawn.yaw(),
                    spawn.pitch()
            );

            if ("minecraft:shulker".equals(spawn.type())) {
                Location grounded = adjustShulkerSpawn(world, loc);
                if (grounded == null) {
                    continue;
                }
                world.spawn(grounded, Shulker.class, shulker -> {
                    shulker.setAI(true);
                    shulker.setPersistent(true);
                });
            } else if ("minecraft:enderman".equals(spawn.type())) {
                world.spawn(loc, Enderman.class, enderman -> {
                    enderman.setAI(true);
                    enderman.setPersistent(true);
                });
            }
        }
    }

    private static BlockVector toAnchorVector(Location anchor) {
        return new BlockVector(anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ());
    }

    private static Vector transformedPos(
            BlockVector anchorVec,
            int[] relative,
            BlockVector pieceSize,
            StructureRotation rotation,
            Mirror mirror
    ) {
        return anchorVec.clone().add(JigsawMath.transformRelativePos(
                JigsawMath.toBlockVector(relative),
                pieceSize,
                mirror,
                rotation
        ));
    }

    private static boolean isLootContainerMaterial(Material type) {
        return type == Material.CHEST || type == Material.BARREL || type == Material.TRAPPED_CHEST;
    }

    private static Location adjustShulkerSpawn(World world, Location original) {
        int x = original.getBlockX();
        int z = original.getBlockZ();
        int y = original.getBlockY();

        if (!world.getBlockAt(x, y - 1, z).getType().isAir()) {
            return original;
        }
        int minY = world.getMinHeight();
        for (int dy = 1; dy <= 8 && y - dy > minY; dy++) {
            if (!world.getBlockAt(x, y - dy - 1, z).getType().isAir()) {
                return new Location(world, x + 0.5, y - dy, z + 0.5, original.getYaw(), original.getPitch());
            }
        }
        return null;
    }

    private LootTable resolveLootTable(String lootTableId) {
        NamespacedKey key = NamespacedKey.fromString(lootTableId);
        if (key == null) {
            return null;
        }
        LootTable table = Bukkit.getLootTable(key);
        if (table != null) {
            return table;
        }
        return null;
    }

    private static void removeVanillaItemFrames(World world, StructureBounds bounds) {
        Location center = new Location(
                world,
                (bounds.minX() + bounds.maxX()) / 2.0 + 0.5,
                (bounds.minY() + bounds.maxY()) / 2.0 + 0.5,
                (bounds.minZ() + bounds.maxZ()) / 2.0 + 0.5
        );
        double radiusX = (bounds.maxX() - bounds.minX()) / 2.0 + 2;
        double radiusY = (bounds.maxY() - bounds.minY()) / 2.0 + 2;
        double radiusZ = (bounds.maxZ() - bounds.minZ()) / 2.0 + 2;
        for (Entity entity : world.getNearbyEntities(center, radiusX, radiusY, radiusZ)) {
            if (entity instanceof ItemFrame frame && !frame.isFixed()) {
                frame.remove();
            } else if (entity instanceof GlowItemFrame glowFrame && !glowFrame.isFixed()) {
                glowFrame.remove();
            }
        }
    }

    private static void replaceTechnicalBlock(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();
        if (type == Material.JIGSAW || type == Material.STRUCTURE_BLOCK) {
            block.setType(Material.AIR, false);
        }
    }

    private static BlockFace parseFacing(String facing, StructureRotation rotation, Mirror mirror) {
        BlockFace base = switch (facing.toLowerCase()) {
            case "south" -> BlockFace.SOUTH;
            case "west" -> BlockFace.WEST;
            case "east" -> BlockFace.EAST;
            case "up" -> BlockFace.UP;
            case "down" -> BlockFace.DOWN;
            default -> BlockFace.NORTH;
        };
        Vector dir = JigsawMath.rotateFacing(new BlockVector(
                base.getModX(), base.getModY(), base.getModZ()
        ), rotation, mirror);
        if (dir.getX() == 1) return BlockFace.EAST;
        if (dir.getX() == -1) return BlockFace.WEST;
        if (dir.getY() == 1) return BlockFace.UP;
        if (dir.getY() == -1) return BlockFace.DOWN;
        if (dir.getZ() == 1) return BlockFace.SOUTH;
        return BlockFace.NORTH;
    }

    private static Location frameLocation(Block attach, BlockFace facing) {
        Location loc = attach.getLocation().add(0.5, 0.5, 0.5);
        return switch (facing) {
            case EAST -> loc.add(0.5 - 0.03125, 0, 0);
            case WEST -> loc.add(-0.5 + 0.03125, 0, 0);
            case UP -> loc.add(0, 0.5 - 0.03125, 0);
            case DOWN -> loc.add(0, -0.5 + 0.03125, 0);
            case SOUTH -> loc.add(0, 0, 0.5 - 0.03125);
            default -> loc.add(0, 0, -0.5 + 0.03125);
        };
    }
}
