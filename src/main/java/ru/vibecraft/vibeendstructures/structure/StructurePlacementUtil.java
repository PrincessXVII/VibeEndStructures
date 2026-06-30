package ru.vibecraft.vibeendstructures.structure;

import org.bukkit.Location;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;

import java.util.List;
import java.util.Random;

public final class StructurePlacementUtil {

  private StructurePlacementUtil() {
  }

  public static void placeBlocks(
          Structure structure,
          Location anchor,
          StructureRotation rotation,
          Mirror mirror,
          Random random
  ) {
    structure.place(
            anchor,
            false,
            rotation,
            mirror,
            -1,
            1.0f,
            random,
            List.of(StructureAirFilter.create(structure, anchor, rotation, mirror)),
            List.of()
    );
  }
}
