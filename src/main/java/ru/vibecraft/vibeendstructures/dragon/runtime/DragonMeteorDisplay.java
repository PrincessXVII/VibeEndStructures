package ru.vibecraft.vibeendstructures.dragon.runtime;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Lightweight 3x3 meteor visual using a single scaled {@link BlockDisplay}.
 */
public final class DragonMeteorDisplay {

    private static final Vector3f CUBE_SCALE = new Vector3f(3f, 3f, 3f);
    private static final Vector3f CUBE_TRANSLATION = new Vector3f(-1.5f, -1.5f, -1.5f);
    private static final Transformation CUBE_TRANSFORMATION = new Transformation(
            CUBE_TRANSLATION,
            new AxisAngle4f(0f, 0f, 0f, 1f),
            CUBE_SCALE,
            new AxisAngle4f(0f, 0f, 0f, 1f)
    );

    private DragonMeteorDisplay() {
    }

    public static BlockDisplay spawn(World world, Location center, Material block) {
        if (world == null || center == null) {
            return null;
        }
        return world.spawn(center, BlockDisplay.class, entity -> {
            entity.setBlock(block.createBlockData());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setPersistent(false);
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(1);
            entity.setViewRange(64.0f);
            entity.setShadowRadius(0.0f);
            entity.setShadowStrength(0.0f);
            entity.setTransformation(CUBE_TRANSFORMATION);
            entity.addScoreboardTag("vibedragon:meteor");
        });
    }

    public static void move(BlockDisplay display, Location center) {
        if (display == null || !display.isValid() || center == null) {
            return;
        }
        display.teleport(center);
    }

    public static void remove(BlockDisplay display) {
        if (display != null && display.isValid()) {
            display.remove();
        }
    }
}
