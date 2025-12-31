package com.simplestructurescanner.structure;

import net.minecraft.util.math.BlockPos;


/**
 * Represents the location of a found structure.
 */
public class StructureLocation {
    private final BlockPos position;
    private final int index;
    private final int totalFound;
    private final boolean yAgnostic;

    public StructureLocation(BlockPos position, int index, int totalFound) {
        this(position, index, totalFound, false);
    }

    public StructureLocation(BlockPos position, int index, int totalFound, boolean yAgnostic) {
        this.position = position;
        this.index = index;
        this.totalFound = totalFound;
        this.yAgnostic = yAgnostic;
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
     * Whether the Y coordinate is unknown/irrelevant for this structure.
     * When true, distance calculations use horizontal distance only.
     */
    public boolean isYAgnostic() {
        return yAgnostic;
    }

    /**
     * Get distance from a position.
     * Uses horizontal distance only if this location is y-agnostic.
     */
    public double getDistanceFrom(BlockPos from) {
        if (yAgnostic) {
            double dx = position.getX() - from.getX();
            double dz = position.getZ() - from.getZ();
            return Math.sqrt(dx * dx + dz * dz);
        }

        return Math.sqrt(position.distanceSq(from));
    }
}
