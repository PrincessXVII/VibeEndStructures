package ru.vibecraft.vibeendstructures.structure;

import org.bukkit.Location;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Palette;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockTransformer;
import org.bukkit.util.BlockVector;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

final class StructureAirFilter {

    private StructureAirFilter() {
    }

    static BlockTransformer create(
            Structure structure,
            Location anchor,
            StructureRotation rotation,
            Mirror mirror
    ) {
        BlockVector size = structure.getSize();
        Set<Long> solids = buildSolidSet(structure);
        Set<Long> exteriorAir = buildSideExteriorAir(size, solids);

        int anchorX = anchor.getBlockX();
        int anchorY = anchor.getBlockY();
        int anchorZ = anchor.getBlockZ();

        return (region, x, y, z, current, state) -> {
            Material material = current.getType();
            if (material == Material.STRUCTURE_VOID) {
                return state.getWorld();
            }
            if (!isAirLike(material)) {
                return current;
            }

            BlockVector local = worldOffsetToLocal(
                    x - anchorX,
                    y - anchorY,
                    z - anchorZ,
                    size,
                    mirror,
                    rotation
            );
            if (local == null || exteriorAir.contains(pack(local))) {
                return state.getWorld();
            }
            return current;
        };
    }

    private static Set<Long> buildSolidSet(Structure structure) {
        Set<Long> solids = new HashSet<>();
        if (structure.getPaletteCount() == 0) {
            return solids;
        }
        Palette palette = structure.getPalettes().getFirst();
        for (BlockState block : palette.getBlocks()) {
            Location location = block.getLocation();
            if (!isAirLike(block.getType())) {
                solids.add(pack(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            }
        }
        return solids;
    }

    private static Set<Long> buildSideExteriorAir(BlockVector size, Set<Long> solids) {
        int sizeX = size.getBlockX();
        int sizeY = size.getBlockY();
        int sizeZ = size.getBlockZ();
        Set<Long> exterior = new HashSet<>();
        ArrayDeque<BlockVector> queue = new ArrayDeque<>();

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                enqueueAirIfAbsent(new BlockVector(0, y, z), solids, exterior, queue);
                enqueueAirIfAbsent(new BlockVector(sizeX - 1, y, z), solids, exterior, queue);
            }
            for (int x = 1; x < sizeX - 1; x++) {
                enqueueAirIfAbsent(new BlockVector(x, y, 0), solids, exterior, queue);
                enqueueAirIfAbsent(new BlockVector(x, y, sizeZ - 1), solids, exterior, queue);
            }
        }

        while (!queue.isEmpty()) {
            BlockVector pos = queue.removeFirst();
            long key = pack(pos);
            if (!exterior.add(key)) {
                continue;
            }
            visitNeighbor(pos.getBlockX() + 1, pos.getBlockY(), pos.getBlockZ(), size, solids, exterior, queue);
            visitNeighbor(pos.getBlockX() - 1, pos.getBlockY(), pos.getBlockZ(), size, solids, exterior, queue);
            visitNeighbor(pos.getBlockX(), pos.getBlockY() + 1, pos.getBlockZ(), size, solids, exterior, queue);
            visitNeighbor(pos.getBlockX(), pos.getBlockY() - 1, pos.getBlockZ(), size, solids, exterior, queue);
            visitNeighbor(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ() + 1, size, solids, exterior, queue);
            visitNeighbor(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ() - 1, size, solids, exterior, queue);
        }

        return exterior;
    }

    private static void enqueueAirIfAbsent(
            BlockVector pos,
            Set<Long> solids,
            Set<Long> exterior,
            ArrayDeque<BlockVector> queue
    ) {
        long key = pack(pos);
        if (!solids.contains(key) && !exterior.contains(key)) {
            queue.add(pos);
        }
    }

    private static void visitNeighbor(
            int x,
            int y,
            int z,
            BlockVector size,
            Set<Long> solids,
            Set<Long> exterior,
            ArrayDeque<BlockVector> queue
    ) {
        if (x < 0 || y < 0 || z < 0) {
            return;
        }
        if (x >= size.getBlockX() || y >= size.getBlockY() || z >= size.getBlockZ()) {
            return;
        }
        long key = pack(x, y, z);
        if (!solids.contains(key) && !exterior.contains(key)) {
            queue.add(new BlockVector(x, y, z));
        }
    }

    private static BlockVector worldOffsetToLocal(
            int dx,
            int dy,
            int dz,
            BlockVector size,
            Mirror mirror,
            StructureRotation rotation
    ) {
        int sizeX = size.getBlockX();
        int sizeY = size.getBlockY();
        int sizeZ = size.getBlockZ();
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    BlockVector transformed = JigsawMath.transformRelativePos(
                            new BlockVector(x, y, z),
                            size,
                            mirror,
                            rotation
                    );
                    if (transformed.getBlockX() == dx
                            && transformed.getBlockY() == dy
                            && transformed.getBlockZ() == dz) {
                        return new BlockVector(x, y, z);
                    }
                }
            }
        }
        return null;
    }

    private static boolean isAirLike(Material material) {
        return material.isAir() || material == Material.STRUCTURE_VOID;
    }

    private static long pack(BlockVector pos) {
        return pack(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
    }

    private static long pack(int x, int y, int z) {
        return ((long) x << 20) | ((long) y << 10) | (z & 0x3FF);
    }
}
