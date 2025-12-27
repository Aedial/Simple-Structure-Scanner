package com.simplestructurescanner.structure;

import net.minecraft.util.math.BlockPos;


/**
 * Represents the location of a found structure.
 */
public class StructureLocation {
    private final BlockPos position;
    private final int index;
    private final int totalFound;

    public StructureLocation(BlockPos position, int index, int totalFound) {
        this.position = position;
        this.index = index;
        this.totalFound = totalFound;
    }

    public BlockPos getPosition() {
        return position;
    }

    /**
     * The index of this structure in the search results (0-based).
     */
    public int getIndex() {
        return index;
    }

    /**
     * Total number of structures found in the search.
     */
    public int getTotalFound() {
        return totalFound;
    }

    /**
     * Get distance from a position.
     */
    public double getDistanceFrom(BlockPos from) {
        return Math.sqrt(position.distanceSq(from));
    }
}
