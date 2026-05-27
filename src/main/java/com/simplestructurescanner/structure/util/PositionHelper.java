package com.simplestructurescanner.structure.util;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.util.math.BlockPos;


/**
 * Utility class for common position-related operations used by structure providers.
 * Contains methods for sorting positions by distance and other spatial operations.
 */
public final class PositionHelper {

    public static final class FilteredPositionResult {
        private final BlockPos position;
        private final int totalMatches;

        private FilteredPositionResult(BlockPos position, int totalMatches) {
            this.position = position;
            this.totalMatches = totalMatches;
        }

        public BlockPos getPosition() {
            return position;
        }

        public int getTotalMatches() {
            return totalMatches;
        }
    }

    private PositionHelper() {
    }

    /**
     * Sort a list of positions by horizontal distance (X/Z only) from a reference position.
     * <p>
     * Uses squared distance comparison to avoid expensive sqrt calculations.
     * Uses long arithmetic to prevent integer overflow for large distances.
     *
     * @param positions the list to sort (modified in-place)
     * @param from the reference position
     */
    public static void sortByHorizontalDistance(List<BlockPos> positions, BlockPos from) {
        final int px = from.getX();
        final int pz = from.getZ();

        positions.sort((a, b) -> {
            long dxA = a.getX() - px;
            long dzA = a.getZ() - pz;
            long dxB = b.getX() - px;
            long dzB = b.getZ() - pz;
            long distA = dxA * dxA + dzA * dzA;
            long distB = dxB * dxB + dzB * dzB;

            return Long.compare(distA, distB);
        });
    }

    /**
     * Sort a list of positions by 3D distance from a reference position.
     * <p>
     * Uses squared distance comparison to avoid expensive sqrt calculations.
     * Uses long arithmetic to prevent integer overflow for large distances.
     *
     * @param positions the list to sort (modified in-place)
     * @param from the reference position
     */
    public static void sortByDistance3D(List<BlockPos> positions, BlockPos from) {
        final int px = from.getX();
        final int py = from.getY();
        final int pz = from.getZ();

        positions.sort((a, b) -> {
            long dxA = a.getX() - px;
            long dyA = a.getY() - py;
            long dzA = a.getZ() - pz;
            long dxB = b.getX() - px;
            long dyB = b.getY() - py;
            long dzB = b.getZ() - pz;
            long distA = dxA * dxA + dyA * dyA + dzA * dzA;
            long distB = dxB * dxB + dyB * dyB + dzB * dzB;

            return Long.compare(distA, distB);
        });
    }

    /**
     * Create a comparator that sorts positions by horizontal distance from a reference position.
     *
     * @param from the reference position
     * @return a comparator for sorting by horizontal distance
     */
    public static Comparator<BlockPos> horizontalDistanceComparator(BlockPos from) {
        final int px = from.getX();
        final int pz = from.getZ();

        return (a, b) -> {
            long dxA = a.getX() - px;
            long dzA = a.getZ() - pz;
            long dxB = b.getX() - px;
            long dzB = b.getZ() - pz;
            long distA = dxA * dxA + dzA * dzA;
            long distB = dxB * dxB + dzB * dzB;

            return Long.compare(distA, distB);
        };
    }

    /**
     * Walk a distance-sorted candidate list once, applying the optional filter while keeping
     * the caller's skip index and the total visible matches in sync.
     */
    @Nullable
    public static FilteredPositionResult selectFilteredPosition(List<BlockPos> sortedPositions, int skipCount,
            @Nullable Predicate<BlockPos> filter) {
        int validIndex = 0;
        int totalValid = 0;
        BlockPos target = null;

        for (BlockPos candidate : sortedPositions) {
            if (filter != null && !filter.test(candidate)) continue;

            if (validIndex == skipCount && target == null) target = candidate;

            validIndex++;
            totalValid++;
        }

        if (target == null) return null;

        return new FilteredPositionResult(target, totalValid);
    }

    /**
     * Calculate the squared horizontal distance between two positions.
     *
     * @param a the first position
     * @param b the second position
     * @return the squared distance (X/Z only)
     */
    public static long horizontalDistanceSquared(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dz = a.getZ() - b.getZ();

        return dx * dx + dz * dz;
    }

    /**
     * Calculate the squared 3D distance between two positions.
     *
     * @param a the first position
     * @param b the second position
     * @return the squared distance
     */
    public static long distanceSquared3D(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dy = a.getY() - b.getY();
        long dz = a.getZ() - b.getZ();

        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Check if a position is within a horizontal radius of another position.
     *
     * @param pos the position to check
     * @param center the center position
     * @param radius the radius (in blocks)
     * @return true if the position is within the radius
     */
    public static boolean isWithinHorizontalRadius(BlockPos pos, BlockPos center, int radius) {
        return horizontalDistanceSquared(pos, center) <= (long) radius * radius;
    }

    /**
     * Check if a position is within a 3D radius of another position.
     *
     * @param pos the position to check
     * @param center the center position
     * @param radius the radius (in blocks)
     * @return true if the position is within the radius
     */
    public static boolean isWithin3DRadius(BlockPos pos, BlockPos center, int radius) {
        return distanceSquared3D(pos, center) <= (long) radius * radius;
    }
}
