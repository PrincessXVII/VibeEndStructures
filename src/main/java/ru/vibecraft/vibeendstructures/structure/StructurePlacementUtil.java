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
    try {
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
    } catch (IllegalArgumentException ex) {
      if (ex.getMessage() != null && ex.getMessage().contains("not in the region")) {
        structure.place(anchor, false, rotation, mirror, -1, 1.0f, random, List.of(), List.of());
        return;
      }
      throw ex;
    }
  }
}
