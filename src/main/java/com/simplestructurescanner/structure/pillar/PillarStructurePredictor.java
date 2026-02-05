package com.simplestructurescanner.structure.pillar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.FMLCommonHandler;

import com.simplestructurescanner.validation.StructureValidationWorld;
import com.simplestructurescanner.validation.ValidationContextManager;

/**
 * Predicts Pillar structure locations by replicating Pillar's generation algorithm.
 * <p>
 * This class simulates Pillar's structure generation process without requiring
 * chunk generation. It uses Forge's chunk random seeding formula to predict
 * where structures will spawn.
 * <p>
 */
public class PillarStructurePredictor {

    // Check if verification phase is disabled (for testing)
    private static final boolean DISABLE_VERIFICATION_PHASE = false;

    /**
     * Check if verification phase is disabled (for testing).
     * @return true if verification is disabled
     */
    public static boolean isVerificationDisabled() {
        return DISABLE_VERIFICATION_PHASE;
    }

    /**
     * Get the actual world seed, handling both client and server worlds.
     * Client worlds often return 0 for getSeed(), so we need to get the real seed.
     * <p>
     * IMPORTANT: For mods like Advanced Rocketry that use dimension-specific seeds
     * (e.g., WorldProviderPlanet.getSeed() returns baseSeed + dimensionId),
     * we must get the seed from the correct dimension's WorldServer, not always the overworld.
     */
    private static long getActualWorldSeed(World world) {
        long seed = world.getSeed();

        // Check if this is an AR dimension
        boolean isARDimension = isAdvancedRocketryDimension(world);

        // If seed is 0, we might be on the client side - try to get the real seed
        // Also ALWAYS get server seed for AR dimensions (client side returns wrong seed)
        if (seed == 0 || isARDimension) {
            try {
                // Try to get the server world seed
                MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
                if (server != null && server.worlds != null && server.worlds.length > 0) {
                    // Get the dimension ID from the input world
                    int dimension = world.provider.getDimension();

                    // Find the server world for this specific dimension
                    // This is critical for mods like Advanced Rocketry that use dimension-specific seeds
                    WorldServer serverWorld = null;
                    for (net.minecraft.world.WorldServer ws : server.worlds) {
                        if (ws.provider.getDimension() == dimension) {
                            serverWorld = ws;
                            break;
                        }
                    }

                    // Fallback to first world if not found (shouldn't happen, but safety net)
                    if (serverWorld == null) {
                        serverWorld = server.worlds[0];
                    }

                    if (serverWorld != null) {
                        long serverSeed = serverWorld.getSeed();

                        // Only use server seed if it's different and valid
                        if (serverSeed != 0 && serverSeed != seed) {
                            seed = serverSeed;
                        }
                    }
                }
            } catch (Exception e) {
                // Silently handle exception
            }
        }

        return seed;
    }

    /**
     * Recreates the exact Random that Forge provides to IWorldGenerators.
     * This formula is from GameRegistry.generateWorld() in Forge 1.12.2.
     *
     * @param worldSeed The world's seed (world.getSeed())
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return A Random in the exact state Forge would provide
     */
    public static Random getChunkRandom(long worldSeed, int chunkX, int chunkZ) {
        Random random = new Random(worldSeed);
        long xSeed = random.nextLong() >> 2 + 1L;
        long zSeed = random.nextLong() >> 2 + 1L;
        long chunkSeed = (xSeed * chunkX + zSeed * chunkZ) ^ worldSeed;
        random.setSeed(chunkSeed);

        return random;
    }

    // ================================================================================
    // ADVANCED ROCKETRY COMPATIBILITY CODE
    // ================================================================================
    // The following methods are specific to Advanced Rocketry dimension compatibility.
    // AR uses a different chunk seeding formula than standard Forge, which requires
    // special handling to correctly predict structure locations.

    /**
     * Detects if the given world is an Advanced Rocketry dimension.
     * <p>
     * AR dimensions can be identified by checking if the WorldProvider class name
     * contains "WorldProviderPlanet" or "WorldProviderSpace" (AR's custom providers).
     *
     * @param world The world to check
     * @return true if this is an AR dimension, false otherwise
     */
    private static boolean isAdvancedRocketryDimension(World world) {
        if (world == null || world.provider == null) {
            return false;
        }

        String providerClassName = world.provider.getClass().getSimpleName();
        return providerClassName.equals("WorldProviderPlanet") ||
               providerClassName.equals("WorldProviderSpace") ||
               providerClassName.equals("WorldProviderAsteroid") ||
               providerClassName.contains("WorldProviderPlanet") ||
               providerClassName.contains("zmaster587");  // AR's package prefix
    }

    /**
     * Recreates the Random that Advanced Rocketry provides to IWorldGenerators.
     * <p>
     * AR uses a DIFFERENT seeding formula than standard Forge. This method replicates
     * AR's formula from ChunkProviderPlanet.populate() (lines 491-494):
     * <pre>
     * this.rand.setSeed(this.worldObj.getSeed());
     * long k = this.rand.nextLong() / 2L * 2L + 1L;
     * long l = this.rand.nextLong() / 2L * 2L + 1L;
     * this.rand.setSeed((long) x * k + (long) z * l ^ this.worldObj.getSeed());
     * </pre>
     * <p>
     * Key differences from Forge's formula:
     * <ul>
     * <li>Uses / 2L * 2L + 1L (ensures odd number) instead of >> 2 + 1L (right shift)</li>
     * <li>Uses (x * k + z * l ^ seed) instead of (xSeed * x + zSeed * z ^ seed)</li>
     * </ul>
     *
     * @param worldSeed The world's seed (world.getSeed())
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return A Random in the exact state AR would provide
     */
    private static Random getChunkRandomForAR(long worldSeed, int chunkX, int chunkZ) {
        Random random = new Random(worldSeed);
        long k = random.nextLong() / 2L * 2L + 1L;
        long l = random.nextLong() / 2L * 2L + 1L;
        long chunkSeed = (long) chunkX * k + (long) chunkZ * l ^ worldSeed;
        random.setSeed(chunkSeed);

        return random;
    }

    // ================================================================================
    // END ADVANCED ROCKETRY COMPATIBILITY CODE
    // ================================================================================

    /**
     * Predict whether a specific structure would spawn in the given chunk.
     * <p>
     * This method replicates Pillar's generateStructure() logic, but only
     * returns predictions for structures matching the target name.
     *
     * @param world The world
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param targetStructureName The structure name to predict for
     * @param knownPositions Previously found positions of this structure type
     *                       (for minDistanceToSameTypeStructures check)
     * @return Predicted BlockPos (with Y=0 as placeholder), or null if structure
     *         would not spawn in this chunk
     */
    @Nullable
    public static BlockPos predictStructureInChunk(
            World world,
            int chunkX,
            int chunkZ,
            String targetStructureName,
            Collection<BlockPos> knownPositions) {

        // OPTION 10: Get schemas in CAPTURED iteration order
        // This is the key to matching Pillar without modifying Pillar.
        List<PillarSchemaProxy> schemaList = PillarIntegration.getSchemasInOrder();

        if (schemaList == null || schemaList.isEmpty()) {
            return null;
        }

        // Create the seeded Random for this chunk
        long actualSeed = getActualWorldSeed(world);

        // Detect if this is an Advanced Rocketry dimension
        boolean isARDimension = isAdvancedRocketryDimension(world);

        // ========================================================================
        // ADVANCED ROCKETRY COMPATIBILITY: Use correct seeding formula
        // ========================================================================
        // AR dimensions use Forge's standard IWorldGenerator seeding formula
        // Note: AR's internal populate() uses a different formula (/ 2L * 2L + 1L),
        // but IWorldGenerators (like Pillar) receive their Random from GameRegistry.generateWorld()
        // which is called AFTER populate() completes and uses Forge's standard formula (>> 2 + 1L).
        // The world seed is already modified by AR (baseSeed + dimensionId) via world.getSeed().
        Random random;
        if (isARDimension) {
            // Use Forge's standard formula for IWorldGenerators (same as standard dimensions)
            random = getChunkRandom(actualSeed, chunkX, chunkZ);
        } else {
            // Use standard Forge seeding formula
            random = getChunkRandom(actualSeed, chunkX, chunkZ);
        }
        // ========================================================================
        // END ADVANCED ROCKETRY COMPATIBILITY
        // ========================================================================

        // OPTION 10: Use captured order directly for shuffling
        // This matches Pillar's natural behavior without sorting
        List<PillarSchemaProxy> shuffledSchemas = new ArrayList<>(schemaList);
        Collections.shuffle(shuffledSchemas, random);

        int structuresGenerated = 0;
        int maxStructures = PillarIntegration.getMaxStructuresInOneChunk();

        // OPTIMIZATION: Only perform expensive verification for target structure
        // For non-target structures, just check rarity and consume Random calls
        // This assumes we don't care about inter-structure interactions
        boolean isTargetStructure = false;

        // Try to generate each structure (up to maxStructuresInOneChunk)
        for (int i = 0; i < shuffledSchemas.size(); i++) {
            PillarSchemaProxy schema = shuffledSchemas.get(i);
            isTargetStructure = schema.structureName.equals(targetStructureName);

            BlockPos result;
            if (isTargetStructure) {
                // TARGET: Full verification including Y-coordinate and spawn conditions
                result = predictSingleStructure(
                        schema, random, world, chunkX, chunkZ, knownPositions);
            } else {
                // NON-TARGET: Just check rarity and consume Random calls (no expensive operations)
                result = predictNonTargetStructure(schema, random);
            }

            if (result != null) {
                // This structure would spawn here
                if (isTargetStructure) {
                    // This is our target structure!
                    return result;
                }

                // Not our target, but it counts toward the quota
                structuresGenerated++;
                if (structuresGenerated >= maxStructures) {
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Predict whether a single structure schema would spawn in the given chunk.
     * <p>
     * This replicates the logic from WorldGenerator.generateStructure().
     * Now uses a fake world to predict accurate Y-coordinates and perform terrain validation.
     *
     * @param schema The structure schema
     * @param random The seeded Random (will be modified)
     * @param world The world
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param knownPositions Previously found positions of this structure type
     * @return Predicted BlockPos with accurate Y-coordinate, or null if structure
     *         would not spawn
     */
    @Nullable
    private static BlockPos predictSingleStructure(
            PillarSchemaProxy schema,
            Random random,
            World world,
            int chunkX,
            int chunkZ,
            Collection<BlockPos> knownPositions) {

        // Step 1: Skip if generator type is NONE
        if (schema.generatorType == PillarGeneratorType.NONE) {
            return null;
        }

        // Step 2: Rarity check
        int rarity = (int) (schema.rarity * PillarIntegration.getRarityMultiplier());
        int rarityRoll = random.nextInt(rarity);

        if (rarity > 0 && rarityRoll != 0) {
            return null;
        }

        // Step 3: Determine X/Z position within chunk
        int x = chunkX * 16 + random.nextInt(16);
        int z = chunkZ * 16 + random.nextInt(16);

        BlockPos pos;

        if (DISABLE_VERIFICATION_PHASE) {
            // PREDICTION ONLY (like old branch): Skip terrain generation, use Y=0 placeholder
            // Consume random calls that GeneratorType.getGenerationPosition() would make
            consumeGeneratorTypeRandom(schema, random);
            // Create position with Y=0 (we can't predict Y without terrain data)
            pos = new BlockPos(x, 0, z);
        } else {
            // VERIFICATION ENABLED: Get Y-coordinate using fake world with terrain generation
            BlockPos xzPos = new BlockPos(x, 0, z);

            StructureValidationWorld validationWorld = ValidationContextManager.getValidationWorld(world);

            pos = getYCoordinateForGeneratorType(schema, random, validationWorld, xzPos, world);

            if (pos == null) {
                // Terrain validation failed
                return null;
            }
        }

        // Step 5: Check spawn conditions (biome, dimension, distance)
        boolean canSpawn = canSpawnInPosition(schema, world, pos, knownPositions);

        if (!canSpawn) {
            return null;
        }

        // This structure would spawn here with accurate Y-coordinate
        return pos;
    }

    /**
     * Lightweight check for non-target structures.
     * <p>
     * This method only checks rarity and consumes Random calls to maintain
     * correct seeding. It skips expensive operations like Y-coordinate determination
     * and spawn condition checks, since we don't care about non-target structures.
     * <p>
     * This assumes we don't need to accurately model inter-structure interactions.
     *
     * @param schema The structure schema
     * @param random The seeded Random (will be modified)
     * @return A placeholder BlockPos if structure would spawn, or null otherwise
     */
    @Nullable
    private static BlockPos predictNonTargetStructure(
            PillarSchemaProxy schema,
            Random random) {

        // Step 1: Skip if generator type is NONE
        if (schema.generatorType == PillarGeneratorType.NONE) {
            return null;
        }

        // Step 2: Rarity check
        int rarity = (int) (schema.rarity * PillarIntegration.getRarityMultiplier());
        int rarityRoll = random.nextInt(rarity);

        if (rarity > 0 && rarityRoll != 0) {
            return null;
        }

        // Step 3: Consume Random calls for X/Z position
        random.nextInt(16); // X offset
        random.nextInt(16); // Z offset

        // Step 4: Consume Random calls for Y-coordinate (but don't actually determine it)
        consumeGeneratorTypeRandom(schema, random);

        // This structure would spawn (return placeholder - Y=0 since we didn't verify)
        // We return a non-null result to indicate it counts toward the quota
        return new BlockPos(0, 0, 0);
    }

    /**
     * Determines the Y-coordinate for a structure using Pillar's GeneratorType logic.
     * <p>
     * This replicates the behavior of GeneratorType.getGenerationPosition() from Pillar,
     * using a fake world to perform terrain validation and determine the accurate Y-coordinate.
     *
     * @param schema The structure schema
     * @param random The seeded Random (will be modified)
     * @param validationWorld The validation world with terrain data
     * @param xzPos The XZ position (Y coordinate is ignored)
     * @param realWorld The real world (for comparison debugging)
     * @return The BlockPos with accurate Y-coordinate, or null if terrain validation fails
     */
    @Nullable
    private static BlockPos getYCoordinateForGeneratorType(
            PillarSchemaProxy schema,
            Random random,
            StructureValidationWorld validationWorld,
            BlockPos xzPos,
            World realWorld) {

        switch (schema.generatorType) {
            case SURFACE:
                // SURFACE: Uses world.getTopSolidOrLiquidBlock()
                // Then validates: X != 0, not liquid, Y in bounds
                return getSurfacePosition(schema, validationWorld, xzPos, realWorld);

            case UNDERGROUND:
                // UNDERGROUND: Random Y between 0-60, must not see sky
                return getUndergroundPosition(schema, random, validationWorld, xzPos);

            case UNDERWATER:
                // UNDERWATER: Uses getTopSolidBlock(), must have liquid above
                return getUnderwaterPosition(schema, validationWorld, xzPos);

            case ABOVE_WATER:
                // ABOVE_WATER: Uses getTopLiquidBlock(), Y in bounds
                return getAboveWaterPosition(schema, validationWorld, xzPos);

            case SKY:
                // SKY: Random Y between 128-256, must see sky and be air
                return getSkyPosition(schema, random, validationWorld, xzPos);

            case ANYWHERE:
                // ANYWHERE: Random Y between 1-256, no terrain checks
                return getAnywherePosition(schema, random, xzPos);

            case NONE:
                // NONE: Never spawns
                return null;

            default:
                return null;
        }
    }

    /**
     * SURFACE generator type logic.
     * Uses world.getTopSolidOrLiquidBlock() and validates.
     */
    @Nullable
    private static BlockPos getSurfacePosition(PillarSchemaProxy schema, StructureValidationWorld validationWorld, BlockPos xzPos, World realWorld) {
        BlockPos pos = validationWorld.getTopSolidOrLiquidBlock(xzPos);
        net.minecraft.block.state.IBlockState state = validationWorld.getBlockState(pos);

        // Validate: X != 0, not liquid, Y in bounds (matches Pillar's validation)
        if (pos.getX() == 0 || state.getBlock() instanceof net.minecraft.block.BlockLiquid) {
            return null;
        }

        if (!isInYBounds(schema, pos.getY())) {
            return null;
        }

        return pos;
    }

    /**
     * UNDERGROUND generator type logic.
     * Random Y between 0-60, must not see sky.
     */
    @Nullable
    private static BlockPos getUndergroundPosition(
            PillarSchemaProxy schema, Random random, StructureValidationWorld world, BlockPos xzPos) {

        int y = getYFromBounds(schema, random, 0, 60);
        BlockPos pos = new BlockPos(xzPos.getX(), y, xzPos.getZ());

        if (world.canBlockSeeSky(pos)) {
            return null;
        }

        return pos;
    }

    /**
     * UNDERWATER generator type logic.
     * Uses getTopSolidBlock(), must have liquid above.
     */
    @Nullable
    private static BlockPos getUnderwaterPosition(PillarSchemaProxy schema, StructureValidationWorld world, BlockPos xzPos) {
        BlockPos pos = world.getTopSolidBlock(xzPos);
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos.up());

        if (pos.getX() == 0 || !(state.getBlock() instanceof net.minecraft.block.BlockLiquid)) {
            return null;
        }

        return pos;
    }

    /**
     * ABOVE_WATER generator type logic.
     * Uses getTopLiquidBlock(), Y in bounds.
     */
    @Nullable
    private static BlockPos getAboveWaterPosition(PillarSchemaProxy schema, StructureValidationWorld world, BlockPos xzPos) {
        BlockPos pos = world.getTopLiquidBlock(xzPos);

        if (pos.getX() == 0 || !isInYBounds(schema, pos.getY())) {
            return null;
        }

        return pos;
    }

    /**
     * SKY generator type logic.
     * Random Y between 128-256, must see sky and be air.
     */
    @Nullable
    private static BlockPos getSkyPosition(
            PillarSchemaProxy schema, Random random, StructureValidationWorld world, BlockPos xzPos) {

        int y = getYFromBounds(schema, random, 128, 256);
        BlockPos pos = new BlockPos(xzPos.getX(), y, xzPos.getZ());
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);

        if (!world.canBlockSeeSky(pos) || !state.getBlock().isAir(state, world, pos)) {
            return null;
        }

        return pos;
    }

    /**
     * ANYWHERE generator type logic.
     * Random Y between 1-256, no terrain checks.
     */
    @Nullable
    private static BlockPos getAnywherePosition(
            PillarSchemaProxy schema, Random random, BlockPos xzPos) {

        int y = getYFromBounds(schema, random, 1, 256);
        BlockPos pos = new BlockPos(xzPos.getX(), y, xzPos.getZ());

        return pos;
    }

    /**
     * Checks if Y is within schema bounds.
     * From GeneratorType.isInYBounds().
     */
    private static boolean isInYBounds(PillarSchemaProxy schema, int y) {
        if (schema.maxY > -1 && y > schema.maxY) return false;
        return schema.minY <= -1 || y >= schema.minY;
    }

    /**
     * Gets random Y within bounds.
     * From GeneratorType.getYFromBounds().
     */
    private static int getYFromBounds(PillarSchemaProxy schema, Random rand, int defaultMin, int defaultMax) {
        int maxY = schema.maxY;
        int minY = schema.minY;

        if (maxY < 0) maxY = defaultMax;
        if (minY < 0) minY = defaultMin;

        if (minY > maxY) {
            int temp = maxY;
            maxY = minY;
            minY = temp;
        }

        int diff = maxY - minY;
        return rand.nextInt(diff) + minY;
    }

    /**
     * @deprecated Replaced by getYCoordinateForGeneratorType which performs actual Y determination
     */
    @Deprecated
    private static void consumeGeneratorTypeRandom(PillarSchemaProxy schema, Random random) {
        switch (schema.generatorType) {
            case SURFACE:
                // Uses world.getTopSolidOrLiquidBlock() - no random consumed
                break;
            case UNDERGROUND:
                // Calls getYFromBounds() which uses random.nextInt()
                random.nextInt(getYRange(schema));
                break;
            case UNDERWATER:
                // Uses world scanning - no random consumed
                break;
            case ABOVE_WATER:
                // Uses world scanning - no random consumed
                break;
            case SKY:
                // Calls getYFromBounds() which uses random.nextInt()
                random.nextInt(getYRange(schema));
                break;
            case ANYWHERE:
                // Calls getYFromBounds() which uses random.nextInt()
                random.nextInt(getYRange(schema));
                break;
            case NONE:
                // Returns null immediately - no random consumed
                break;
        }
    }

    /**
     * Calculate the Y range for getYFromBounds().
     * <p>
     * From GeneratorType.getYFromBounds():
     * - If maxY < 0: use defaultMax
     * - If minY < 0: use defaultMin
     * - Then: rand.nextInt(maxY - minY) + minY
     * <p>
     * So the range is (maxY - minY).
     */
    private static int getYRange(PillarSchemaProxy schema) {
        int maxY = schema.maxY;
        int minY = schema.minY;

        if (maxY < 0) maxY = 256; // Default max varies by GeneratorType
        if (minY < 0) minY = 0;   // Default min varies by GeneratorType

        if (minY > maxY) {
            int temp = maxY;
            maxY = minY;
            minY = temp;
        }

        return maxY - minY;
    }

    /**
     * Check if a structure can spawn at the given position.
     * <p>
     * This method checks all spawn conditions including:
     * - generateEverywhere flag
     * - Dimension whitelist/blacklist
     * - Biome whitelist/blacklist (name and tags)
     * - minDistanceToSameTypeStructures (distance check)
     *
     * @param schema The structure schema
     * @param world The world
     * @param pos The position to check
     * @param knownPositions Previously found positions of this structure type
     * @return true if structure could spawn here
     */
    private static boolean canSpawnInPosition(
            PillarSchemaProxy schema,
            World world,
            BlockPos pos,
            Collection<BlockPos> knownPositions) {

        // generateEverywhere check - skip all other checks
        if (schema.generateEverywhere) {
            return true;
        }

        // Dimension check
        if (!schema.dimensionSpawns.isEmpty()) {
            int dim = world.provider.getDimension();
            if (schema.isDimensionSpawnsBlacklist && schema.dimensionSpawns.contains(dim)) {
                return false;
            }
            if (!schema.isDimensionSpawnsBlacklist && !schema.dimensionSpawns.contains(dim)) {
                return false;
            }
        }

        // Biome name check
        Biome biome = world.getBiome(pos);
        if (biome == null) {
            return false;
        }

        String biomeName = biome.getRegistryName().toString();
        if (!schema.biomeNameSpawns.isEmpty()) {
            if (schema.isBiomeNameSpawnsBlacklist && schema.biomeNameSpawns.contains(biomeName)) {
                return false;
            }
            if (!schema.isBiomeNameSpawnsBlacklist && !schema.biomeNameSpawns.contains(biomeName)) {
                return false;
            }
        }

        // minDistanceToSameTypeStructures check
        if (schema.minDistanceToSameTypeStructures > 0) {
            int minDistSq = schema.minDistanceToSameTypeStructures * schema.minDistanceToSameTypeStructures;
            for (BlockPos knownPos : knownPositions) {
                if (pos.distanceSq(knownPos) <= minDistSq) {
                    return false;
                }
            }
        }

        // Biome tag check (BiomeDictionary-based)
        try {
            Set<BiomeDictionary.Type> types = BiomeDictionary.getTypes(biome);

            if (schema.isBiomeTagSpawnsBlacklist) {
                for (BiomeDictionary.Type type : types) {
                    if (schema.biomeTagSpawns.contains(type.getName())) {
                        return false;
                    }
                }
                return true;
            } else {
                for (BiomeDictionary.Type type : types) {
                    if (schema.biomeTagSpawns.contains(type.getName())) {
                        return true;
                    }
                }
            }
        } catch (NullPointerException e) {
            // Biome not properly registered
        }

        return false;
    }

    /**
     * Prints a profiling report showing where time is being spent during scans.
     * This helps identify performance bottlenecks.
     */
    private static void printProfilingReport() {
        // Profiling report output removed
    }

    /**
     * Resets profiling counters. Call this before a new scan to get clean measurements.
     */
    public static void resetProfiling() {
        // Profiling reset removed
    }
}
