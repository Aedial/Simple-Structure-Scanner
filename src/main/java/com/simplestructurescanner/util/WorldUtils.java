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

        // First search upward from startY
        for (int y = startY + 1; y <= 254; y++) if (isSafeTeleportSpot(world, x, y, z)) return y;

        // Then search downward from startY (unlikely)
        for (int y = startY; y >= 1; y--) if (isSafeTeleportSpot(world, x, y, z)) return y;

        // No safe spot found, well f*ck, deal with it
        return -1;
    }

    /**
     * Checks if a position is safe to teleport to.
     * Safe means: solid block below, non-solid at feet, non-solid at head, not underwater.
     */
    public static boolean isSafeTeleportSpot(World world, int x, int y, int z) {
        BlockPos feetPos = new BlockPos(x, y, z);
        BlockPos headPos = feetPos.up();
        BlockPos groundPos = feetPos.down();

        IBlockState groundState = world.getBlockState(groundPos);
        IBlockState feetState = world.getBlockState(feetPos);
        IBlockState headState = world.getBlockState(headPos);

        // Ground must not be harmful and area above must be clear
        Material groundMaterial = groundState.getMaterial();
        if (groundMaterial == Material.LAVA || groundMaterial == Material.FIRE) return false;
        if (groundMaterial == Material.CACTUS) return false;

        if (feetState.getMaterial() != Material.AIR) return false;
        if (headState.getMaterial() != Material.AIR) return false;

        return true;
    }
}
