package ru.vibecraft.vibeendstructures.generation;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import ru.vibecraft.vibeendstructures.model.PlacementType;
import ru.vibecraft.vibeendstructures.model.StructureDefinition;

import java.util.Optional;
import java.util.Random;

public final class PlacementAnchorResolver {

    private PlacementAnchorResolver() {
    }

    public static Optional<Location> resolve(World world, int chunkX, int chunkZ, StructureDefinition definition, Random random) {
        int x = chunkX * 16 + 4 + random.nextInt(8);
        int z = chunkZ * 16 + 4 + random.nextInt(8);
        int minY = definition.minY();

        if (definition.placementType() == PlacementType.GROUND) {
            Optional<Block> surface = EndSurfaceFinder.findSurfaceAt(world, x, z, minY);
            if (surface.isEmpty()) {
                surface = EndSurfaceFinder.findSurface(world, chunkX, chunkZ, minY, random);
            }
            if (surface.isEmpty()) {
                return Optional.empty();
            }

            Block ground = surface.get();
            int y = definition.startHeight().resolveGroundY(ground.getY(), random);
            return Optional.of(new Location(world, ground.getX(), y, ground.getZ()));
        }

        int y = definition.startHeight().resolveAirY(random);
        return Optional.of(new Location(world, x, y, z));
    }

    public static Optional<Location> resolveAt(World world, Location base, StructureDefinition definition, Random random) {
        if (definition.placementType() == PlacementType.GROUND) {
            int minY = definition.minY();
            Optional<Block> surface = EndSurfaceFinder.findSurfaceAt(world, base.getBlockX(), base.getBlockZ(), minY);
            if (surface.isEmpty()) {
                return Optional.empty();
            }
            Block ground = surface.get();
            int y = definition.startHeight().resolveGroundY(ground.getY(), random);
            return Optional.of(new Location(world, ground.getX(), y, ground.getZ()));
        }

        int y = definition.startHeight().resolveAirY(random);
        return Optional.of(new Location(world, base.getBlockX(), y, base.getBlockZ()));
    }
}
