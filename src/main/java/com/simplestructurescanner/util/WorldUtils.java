package com.simplestructurescanner.util;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;


/**
 * Utility methods for world-related operations.
 */
public class WorldUtils {

    /**
     * Gets a unique identifier for the current world.
     * Uses the actual seed for singleplayer, or a hash of server address + spawn for multiplayer.
     */
    public static long getWorldIdentifier() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return 0L;

        // Singleplayer: use the actual seed from integrated server
        if (mc.isSingleplayer() && mc.getIntegratedServer() != null && mc.player != null) {
            return mc.getIntegratedServer().getWorld(mc.player.dimension).getSeed();
        }

        // Multiplayer: use a hash of server address + world spawn position
        if (mc.getCurrentServerData() != null) {
            String serverAddr = mc.getCurrentServerData().serverIP;
            BlockPos spawn = mc.world.getSpawnPoint();
            // Combine server address and spawn to create a unique ID per world on the server
            return serverAddr.hashCode() * 31L + spawn.toLong();
        }

        // Fallback: use spawn point hash
        return mc.world.getSpawnPoint().toLong();
    }

    /**
     * Finds a safe Y coordinate to teleport to at the given X, Z position.
     * Returns a Y value where the player can stand without drowning or suffocating.
     * Returns -1 if no safe spot is found.
     *
     * @param world The world to check
     * @param x X coordinate
     * @param z Z coordinate
     * @param startY Y coordinate to start searching from (search down then up)
     * @return Safe Y coordinate, or -1 if none found
     */
    public static int findSafeTeleportY(World world, int x, int z, int startY) {
        // Clamp startY to valid range
        startY = Math.max(1, Math.min(startY, 255));

        // Starting in air? Go down to find ground. Otherwise, go up to find surface.
        BlockPos feetPos = new BlockPos(x, startY, z);
        boolean inAir = (world.getBlockState(feetPos).getMaterial() == Material.AIR)
            && (world.getBlockState(feetPos.up()).getMaterial() == Material.AIR);

        if (inAir) {
            for (int y = startY; y >= 1; y--) if (canTeleport(world, x, y, z, true)) return y;
            for (int y = startY + 1; y <= 254; y++) if (canTeleport(world, x, y, z)) return y;
        } else {
            for (int y = startY + 1; y <= 254; y++) if (canTeleport(world, x, y, z)) return y;
            for (int y = startY; y >= 1; y--) if (canTeleport(world, x, y, z)) return y;
        }

        // No safe spot found, well f*ck, deal with it
        return -1;
    }

    /**
     * Checks if the player can teleport to the given position safely.
     * @param world The world to check
     * @param x X coordinate
     * @param y Y coordinate (feet position)
     * @param z Z coordinate
     * @param needsGround Whether to require solid block below feet
     */
    public static boolean canTeleport(World world, int x, int y, int z, boolean needsGround) {
        BlockPos feetPos = new BlockPos(x, y, z);
        BlockPos headPos = feetPos.up();
        BlockPos groundPos = feetPos.down();

        IBlockState feetState = world.getBlockState(feetPos);
        IBlockState headState = world.getBlockState(headPos);
        IBlockState groundState = world.getBlockState(groundPos);

        // Feet and head must be passable (not just air - also allows tall grass, etc.)
        if (!isPassable(feetState)) return false;
        if (!isPassable(headState)) return false;

        // Try to get something (semi) solid to stand on
        if (needsGround && isPassable(groundState) && !isFluid(groundState)) return false;

        return true;
    }

    /**
     * Checks if a block state is passable (player can occupy this space).
     */
    private static boolean isPassable(IBlockState state) {
        Material mat = state.getMaterial();

        // These materials are passable
        if (mat == Material.AIR) return true;
        if (mat == Material.PLANTS) return true;
        if (mat == Material.VINE) return true;
        if (mat == Material.SNOW) return true;
        if (mat == Material.CARPET) return true;
        if (mat == Material.CIRCUITS) return true;  // Redstone, etc.
        if (mat == Material.WEB) return true;  // Passable but slow
        if (mat == Material.FIRE) return false;  // Passable but harmful
        if (mat == Material.PORTAL) return true;

        // Check if block has no collision box
        return !state.getMaterial().blocksMovement();
    }

    /**
     * Checks if a block state is a fluid.
     */
    private static boolean isFluid(IBlockState state) {
        return state.getMaterial().isLiquid();
    }

    public static boolean canTeleport(World world, int x, int y, int z) {
        return canTeleport(world, x, y, z, true);
    }
}
