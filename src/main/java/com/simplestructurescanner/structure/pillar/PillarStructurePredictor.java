package com.simplestructurescanner.structure.pillar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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

import com.simplestructurescanner.SimpleStructureScanner;
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

    // MASTER DEBUG SWITCH - Set to true to enable all debug logging, false to disable
    // When false, significantly reduces log spam and improves performance
    private static final boolean DEBUG_ENABLED = true;  // ENABLED FOR AR TESTING

    // TERRAIN VALIDATION DEBUG - Set to true to debug terrain/water issues
    private static final boolean DEBUG_TERRAIN_VALIDATION = false;

    // PERFORMANCE PROFILING - Set to true to enable timing measurements
    private static final boolean PROFILE_PERFORMANCE = false;

    // TEMPORARY: Disable verification phase to test prediction-only performance
    // Set to true to skip fake world terrain generation (like old branch)
    private static final boolean DISABLE_VERIFICATION_PHASE = false;  // RE-ENABLED: Verification now works

    // Track if we've logged warning messages to avoid spam
    private static boolean loggedSeedMessage = false;
    private static boolean loggedOrderMismatch = false;

    // Performance tracking
    private static long totalPredictTime = 0;
    private static long totalGetValidationWorldTime = 0;
    private static long totalPredictSingleTime = 0;
    private static long totalGetYCoordinateTime = 0;
    private static long totalCanSpawnTime = 0;
    private static int predictCallCount = 0;
    private static int getValidationWorldCallCount = 0;
    private static int predictSingleCallCount = 0;
    private static int getYCoordinateCallCount = 0;
    private static int canSpawnCallCount = 0;
    private static boolean profileLogged = false;

    // Detailed tracking - where do schemas fail?
    private static int totalSchemaChecks = 0;
    private static int failedNoneType = 0;
    private static int failedRarity = 0;
    private static int failedYCoordinate = 0;
    private static int failedSpawnConditions = 0;
    private static int passedAllChecks = 0;

    // Optimization tracking - target vs non-target
    private static int nonTargetStructureChecks = 0;
    private static int targetStructureChecks = 0;

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
                            // Only log once to avoid spam
                            if (!loggedSeedMessage) {
                                SimpleStructureScanner.LOGGER.info("Using server world seed: {} for dimension {} (client world had seed {})",
                                    seed, dimension, world.getSeed());
                                loggedSeedMessage = true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.warn("Failed to get server world seed: {}", e.getMessage());
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

    // Debug: enable logging for specific structures
    private static final boolean DEBUG_ALL_PREDICTIONS = DEBUG_ENABLED;  // Controlled by master switch
    private static final Set<String> debugStructures = new HashSet<>();

    // Filter debug logging to only show for test structures (to reduce log spam in AR dimensions)
    private static final Set<String> MOON_STRUCTURES = new HashSet<>(java.util.Arrays.asList(
        "book",
        "cobble",
        "lamp",
        "pyr",
        "terra",
        "test1"
    ));

    // Filter debug logging to only show in specific dimension ID (set to -1 to allow all dimensions)
    private static final int DEBUG_DIMENSION_ID = 2;  // Set to 2 for testing AR dimension

    /**
     * Check if debug logging should be enabled for a specific structure.
     * Returns true only if the structure is in the debug list AND (optionally) in the correct dimension.
     */
    private static boolean shouldDebugStructure(String structureName, String targetStructureName) {
        if (!DEBUG_ALL_PREDICTIONS && !debugStructures.contains(targetStructureName)) {
            return false;
        }
        // Only log if this specific structure is in our test structure list
        return MOON_STRUCTURES.contains(structureName);
    }

    /**
     * Check if debug logging should be enabled for a specific dimension.
     * Returns true if DEBUG_DIMENSION_ID is -1 (allow all) or matches the provided dimension ID.
     */
    private static boolean shouldDebugDimension(World world) {
        if (DEBUG_DIMENSION_ID == -1) {
            return true;  // Allow all dimensions
        }
        return world.provider.getDimension() == DEBUG_DIMENSION_ID;
    }

    public static void enableDebugFor(String structureName) {
        debugStructures.add(structureName);
    }

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

        return predictStructureInChunk(world, chunkX, chunkZ, targetStructureName, knownPositions, false);
    }

    @Nullable
    private static BlockPos predictStructureInChunk(
            World world,
            int chunkX,
            int chunkZ,
            String targetStructureName,
            Collection<BlockPos> knownPositions,
            boolean isDebugRun) {

        long predictStart = PROFILE_PERFORMANCE ? System.nanoTime() : 0;

        // OPTION 10: Get schemas in CAPTURED iteration order
        // This is the key to matching Pillar without modifying Pillar.
        List<PillarSchemaProxy> schemaList = PillarIntegration.getSchemasInOrder();

        if (schemaList == null || schemaList.isEmpty()) {
            SimpleStructureScanner.LOGGER.debug("No Pillar schemas available for prediction");
            return null;
        }

        // Create the seeded Random for this chunk
        long actualSeed = getActualWorldSeed(world);

        // Detect if this is an Advanced Rocketry dimension
        boolean isARDimension = isAdvancedRocketryDimension(world);

        // DEBUG: Log seed info if this is our target AND target is in test structure list AND in debug dimension
        boolean shouldDebug = DEBUG_ALL_PREDICTIONS || debugStructures.contains(targetStructureName);
        boolean shouldDebugTarget = shouldDebugStructure(targetStructureName, targetStructureName) && shouldDebugDimension(world);

        if (shouldDebugTarget) {
            // Log the captured schema order (OPTION 10) - limited to first 10 to reduce log spam
            StringBuilder schemaOrder = new StringBuilder("SSS Schema order - CAPTURED (first " + Math.min(10, schemaList.size()) + " of " + schemaList.size() + "): ");
            for (int i = 0; i < Math.min(10, schemaList.size()); i++) {
                if (i > 0) schemaOrder.append(", ");
                schemaOrder.append(schemaList.get(i).structureName);
            }
            if (schemaList.size() > 10) {
                schemaOrder.append(" ... (+").append(schemaList.size() - 10).append(" more)");
            }
            SimpleStructureScanner.LOGGER.info("{}", schemaOrder);

            // Log dimension type detection
            SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] Dimension detection: isAR={}, providerClass={}",
                isARDimension, world.provider.getClass().getSimpleName());
        }

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
            if (shouldDebugTarget) {
                SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] Using Forge IWorldGenerator formula for AR dimension chunk [{}, {}]", chunkX, chunkZ);

                // Forge formula details (GameRegistry.generateWorld uses this)
                Random forgeTestRandom = new Random(actualSeed);
                long xSeed = forgeTestRandom.nextLong() >> 2 + 1L;
                long zSeed = forgeTestRandom.nextLong() >> 2 + 1L;
                long forgeChunkSeed = (xSeed * chunkX + zSeed * chunkZ) ^ actualSeed;
                SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] Forge IWorldGenerator Formula: xSeed={}, zSeed={}, chunkSeed={}",
                    xSeed, zSeed, forgeChunkSeed);
            }
        } else {
            // Use standard Forge seeding formula
            random = getChunkRandom(actualSeed, chunkX, chunkZ);
            if (shouldDebugTarget) {
                SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] Using standard Forge seeding formula for chunk [{}, {}]", chunkX, chunkZ);

                // Forge formula details
                Random forgeTestRandom = new Random(actualSeed);
                long xSeed = forgeTestRandom.nextLong() >> 2 + 1L;
                long zSeed = forgeTestRandom.nextLong() >> 2 + 1L;
                long forgeChunkSeed = (xSeed * chunkX + zSeed * chunkZ) ^ actualSeed;
                SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] Forge Formula: xSeed={}, zSeed={}, chunkSeed={}",
                    xSeed, zSeed, forgeChunkSeed);
            }
        }
        // ========================================================================
        // END ADVANCED ROCKETRY COMPATIBILITY
        // ========================================================================

        if (shouldDebugTarget) {
            // Comprehensive debug logging: Compare both formulas
            SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] ===== SEEDING COMPARISON =====");
            SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] worldSeed={}, chunk=[{}, {}]", actualSeed, chunkX, chunkZ);

            // Test Forge formula
            Random forgeRandom = new Random(actualSeed);
            long forgeXSeed = forgeRandom.nextLong() >> 2 + 1L;
            long forgeZSeed = forgeRandom.nextLong() >> 2 + 1L;
            long forgeChunkSeed = (forgeXSeed * chunkX + forgeZSeed * chunkZ) ^ actualSeed;
            forgeRandom.setSeed(forgeChunkSeed);
            int forgeFirst = forgeRandom.nextInt(100);
            int forgeSecond = forgeRandom.nextInt(100);

            // Test AR formula
            Random arRandom = new Random(actualSeed);
            long arK = arRandom.nextLong() / 2L * 2L + 1L;
            long arL = arRandom.nextLong() / 2L * 2L + 1L;
            long arChunkSeed = (long) chunkX * arK + (long) chunkZ * arL ^ actualSeed;
            arRandom.setSeed(arChunkSeed);
            int arFirst = arRandom.nextInt(100);
            int arSecond = arRandom.nextInt(100);

            // Log comparison
            SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] FORGE: xSeed={}, zSeed={}, chunkSeed={}, nextInt(100)=[{}, {}]",
                forgeXSeed, forgeZSeed, forgeChunkSeed, forgeFirst, forgeSecond);
            SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] AR:    k={}, l={}, chunkSeed={}, nextInt(100)=[{}, {}]",
                arK, arL, arChunkSeed, arFirst, arSecond);
            // Note: Both AR and standard dimensions use Forge's formula for IWorldGenerators
            SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] Using: FORGE formula (isAR={})",
                isARDimension);
            // DEBUG: Commented out to avoid consuming Random call before shuffle
            // This was shifting SSS's Random state by 1 compared to Pillar
            // SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] Active Random first nextInt(100)={}", random.nextInt(100));
            SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] ===== END SEEDING COMPARISON =====");
        }

        // OPTION 10: Use captured order directly for shuffling
        // This matches Pillar's natural behavior without sorting
        List<PillarSchemaProxy> shuffledSchemas = new ArrayList<>(schemaList);
        Collections.shuffle(shuffledSchemas, random);

        // ========================================================================
        // DEBUG: Log Random state to match Pillar's output (for comparison)
        // ========================================================================
        // This mirrors Pillar's [PILLAR RANDOM STATE] logging for direct comparison
        // Both should show the same nextInt(100) values if Random states are synchronized
        if (isARDimension) {
            int firstNextInt = random.nextInt(100);
            int secondNextInt = random.nextInt(100);
            int thirdNextInt = random.nextInt(100);

            SimpleStructureScanner.LOGGER.info("[SSS RANDOM STATE] chunk=[{}, {}], worldSeed={}, nextInt(100)=[{}, {}, {}], totalSchemas={}",
                chunkX, chunkZ, actualSeed, firstNextInt, secondNextInt, thirdNextInt, shuffledSchemas.size());
        }
        // ========================================================================

        if (shouldDebugTarget) {
            // Log FULL schema order after shuffle
            StringBuilder shuffleOrder = new StringBuilder("SSS After shuffle (first " + Math.min(10, shuffledSchemas.size()) + "): ");
            for (int i = 0; i < Math.min(10, shuffledSchemas.size()); i++) {
                if (i > 0) shuffleOrder.append(", ");
                shuffleOrder.append(shuffledSchemas.get(i).structureName);
            }
            if (shuffledSchemas.size() > 10) {
                shuffleOrder.append(" ... (+").append(shuffledSchemas.size() - 10).append(" more)");
            }
            SimpleStructureScanner.LOGGER.info("{}", shuffleOrder);
            // NOTE: Removed extra random.nextInt(100) call here - it was consuming a random value
            // that Pillar doesn't consume, causing rarity roll mismatches
        }

        int structuresGenerated = 0;
        int maxStructures = PillarIntegration.getMaxStructuresInOneChunk();

        // OPTIMIZATION: Only perform expensive verification for target structure
        // For non-target structures, just check rarity and consume Random calls
        // This assumes we don't care about inter-structure interactions
        boolean isTargetStructure = false;

        // Try to generate each structure (up to maxStructuresInOneChunk)
        for (int i = 0; i < shuffledSchemas.size(); i++) {
            if (PROFILE_PERFORMANCE) {
                totalSchemaChecks++;
            }
            PillarSchemaProxy schema = shuffledSchemas.get(i);
            isTargetStructure = schema.structureName.equals(targetStructureName);

            if (shouldDebugStructure(schema.structureName, targetStructureName)) {
                SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] Processing #{}/{}: '{}' (target: {})",
                    i + 1, shuffledSchemas.size(), schema.structureName, isTargetStructure);
            }

            BlockPos result;
            if (isTargetStructure) {
                // TARGET: Full verification including Y-coordinate and spawn conditions
                if (PROFILE_PERFORMANCE) {
                    targetStructureChecks++;
                }
                boolean shouldDebugThis = shouldDebugStructure(schema.structureName, targetStructureName);
                result = predictSingleStructure(
                        schema, random, world, chunkX, chunkZ, knownPositions, shouldDebugThis);
            } else {
                // NON-TARGET: Just check rarity and consume Random calls (no expensive operations)
                if (PROFILE_PERFORMANCE) {
                    nonTargetStructureChecks++;
                }
                boolean shouldDebugThis = shouldDebugStructure(schema.structureName, targetStructureName);
                result = predictNonTargetStructure(schema, random, shouldDebugThis);
            }

            if (result != null) {
                // This structure would spawn here
                if (isTargetStructure) {
                    // This is our target structure!
                    if (shouldDebugStructure(targetStructureName, targetStructureName)) {
                        SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] FOUND TARGET '{}' at chunk [{}, {}] after processing {}/{} structures",
                            targetStructureName, chunkX, chunkZ, i + 1, shuffledSchemas.size());
                    }
                    return result;
                }

                // Not our target, but it counts toward the quota
                structuresGenerated++;
                if (shouldDebugStructure(schema.structureName, targetStructureName)) {
                    SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] Structure '{}' spawned here (not target), structuresGenerated={}/{}",
                        schema.structureName, structuresGenerated, maxStructures);
                }
                if (structuresGenerated >= maxStructures) {
                    // Log if target is a moon structure or if any moon structure triggered the limit
                    if (shouldDebugStructure(targetStructureName, targetStructureName)) {
                        SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] Reached max structures limit ({}), stopping", maxStructures);
                    }
                    return null;
                }
            }
        }

        // Target structure not selected for this chunk
        if (shouldDebugStructure(targetStructureName, targetStructureName)) {
            SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] Target '{}' NOT found in chunk [{}, {}] after checking all {} structures",
                targetStructureName, chunkX, chunkZ, shuffledSchemas.size());
        }

        if (PROFILE_PERFORMANCE) {
            long predictEnd = System.nanoTime();
            long predictTime = predictEnd - predictStart;
            totalPredictTime += predictTime;
            predictCallCount++;

            // Print profiling report every 10,000 chunks
            if (predictCallCount % 10000 == 0 && !profileLogged) {
                printProfilingReport();
                profileLogged = true;
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
            Collection<BlockPos> knownPositions,
            boolean debug) {

        long predictSingleStart = PROFILE_PERFORMANCE ? System.nanoTime() : 0;

        // Step 1: Skip if generator type is NONE
        if (schema.generatorType == PillarGeneratorType.NONE) {
            if (PROFILE_PERFORMANCE) {
                failedNoneType++;
            }
            return null;
        }

        // Step 2: Rarity check
        int rarity = (int) (schema.rarity * PillarIntegration.getRarityMultiplier());
        int rarityRoll = random.nextInt(rarity);

        if (rarity > 0 && rarityRoll != 0) {
            if (debug) {
                SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] FAILED rarity for '{}': rarity={}, roll={}",
                    schema.structureName, rarity, rarityRoll);
            }
            if (PROFILE_PERFORMANCE) {
                failedRarity++;
            }
            return null;
        }

        if (debug) {
            SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] RARITY PASSED for '{}': rarity={}, roll={}, generatorType={}",
                schema.structureName, rarity, rarityRoll, schema.generatorType);
        }

        // Step 3: Determine X/Z position within chunk
        int x = chunkX * 16 + random.nextInt(16);
        int z = chunkZ * 16 + random.nextInt(16);

        BlockPos pos;

        long getValidationWorldStart = 0;
        long getValidationWorldTime = 0;

        if (DISABLE_VERIFICATION_PHASE) {
            // PREDICTION ONLY (like old branch): Skip terrain generation, use Y=0 placeholder
            // Consume random calls that GeneratorType.getGenerationPosition() would make
            consumeGeneratorTypeRandom(schema, random);
            // Create position with Y=0 (we can't predict Y without terrain data)
            pos = new BlockPos(x, 0, z);
        } else {
            // VERIFICATION ENABLED: Get Y-coordinate using fake world with terrain generation
            BlockPos xzPos = new BlockPos(x, 0, z);

            if (PROFILE_PERFORMANCE) {
                getValidationWorldStart = System.nanoTime();
            }
            StructureValidationWorld validationWorld = ValidationContextManager.getValidationWorld(world);
            if (PROFILE_PERFORMANCE) {
                getValidationWorldTime = System.nanoTime() - getValidationWorldStart;
                totalGetValidationWorldTime += getValidationWorldTime;
                getValidationWorldCallCount++;
            }

            long getYStart = PROFILE_PERFORMANCE ? System.nanoTime() : 0;
            pos = getYCoordinateForGeneratorType(schema, random, validationWorld, xzPos, world);
            if (PROFILE_PERFORMANCE) {
                long getYTime = System.nanoTime() - getYStart;
                totalGetYCoordinateTime += getYTime;
                getYCoordinateCallCount++;
            }

            if (pos == null) {
                // Terrain validation failed
                if (debug) {
                    SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] FAILED terrain validation for '{}' at [{}, {}]",
                        schema.structureName, x, z);
                }
                if (PROFILE_PERFORMANCE) {
                    failedYCoordinate++;
                }
                return null;
            }
        }

        // Step 5: Check spawn conditions (biome, dimension, distance)
        long canSpawnStart = PROFILE_PERFORMANCE ? System.nanoTime() : 0;
        boolean canSpawn = canSpawnInPosition(schema, world, pos, knownPositions);
        if (PROFILE_PERFORMANCE) {
            long canSpawnTime = System.nanoTime() - canSpawnStart;
            totalCanSpawnTime += canSpawnTime;
            canSpawnCallCount++;
        }

        if (!canSpawn) {
            if (debug) {
                SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] FAILED spawn check for '{}' at {}",
                    schema.structureName, pos);
            }
            if (PROFILE_PERFORMANCE) {
                failedSpawnConditions++;
            }
            return null;
        }

        if (debug) {
            SimpleStructureScanner.LOGGER.info("[SSS PREDICTION] SPAWN PASSED for '{}' at {}",
                schema.structureName, pos);
        }

        if (PROFILE_PERFORMANCE) {
            long predictSingleEnd = System.nanoTime();
            long predictSingleTime = predictSingleEnd - predictSingleStart;
            totalPredictSingleTime += predictSingleTime;
            predictSingleCallCount++;
            passedAllChecks++;
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
     * @param debug Whether to log debug output
     * @return A placeholder BlockPos if structure would spawn, or null otherwise
     */
    @Nullable
    private static BlockPos predictNonTargetStructure(
            PillarSchemaProxy schema,
            Random random,
            boolean debug) {

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

        if (DEBUG_TERRAIN_VALIDATION) {
            net.minecraft.block.Block block = state.getBlock();
            boolean isLiquid = block instanceof net.minecraft.block.BlockLiquid;

            // Log validation world terrain
            SimpleStructureScanner.LOGGER.info("================================================================================");
            SimpleStructureScanner.LOGGER.info("[SSS TERRAIN] SURFACE position check at xzPos={}", xzPos);
            SimpleStructureScanner.LOGGER.info("[SSS TERRAIN] Validation World:");
            SimpleStructureScanner.LOGGER.info("[SSS TERRAIN]   -> Predicted pos={}, Y={}", pos, pos.getY());
            SimpleStructureScanner.LOGGER.info("[SSS TERRAIN]   -> Block={}, isLiquid={}, Xis0={}",
                block.getRegistryName(), isLiquid, pos.getX() == 0);
            SimpleStructureScanner.LOGGER.info("[SSS TERRAIN]   -> Biome: {}", validationWorld.getBiome(pos).getRegistryName());

            // Compare with real world (only if chunk is generated)
            SimpleStructureScanner.LOGGER.info("[SSS TERRAIN] Real World Comparison:");
            int chunkX = xzPos.getX() >> 4;
            int chunkZ = xzPos.getZ() >> 4;
            boolean isChunkGenerated = realWorld.getChunkProvider().isChunkGeneratedAt(chunkX, chunkZ);

            if (!isChunkGenerated) {
                SimpleStructureScanner.LOGGER.info("[SSS TERRAIN]   -> Chunk [{}, {}] NOT YET GENERATED in real world", chunkX, chunkZ);
                SimpleStructureScanner.LOGGER.info("[SSS TERRAIN]   -> Cannot compare - validation world is predicting terrain for ungenerated chunk");
            } else {
                BlockPos realPos = realWorld.getTopSolidOrLiquidBlock(xzPos);
                net.minecraft.block.state.IBlockState realState = realWorld.getBlockState(realPos);
                net.minecraft.block.Block realBlock = realState.getBlock();
                boolean realIsLiquid = realBlock instanceof net.minecraft.block.BlockLiquid;

                SimpleStructureScanner.LOGGER.info("[SSS TERRAIN]   -> Real pos={}, Y={}", realPos, realPos.getY());
                SimpleStructureScanner.LOGGER.info("[SSS TERRAIN]   -> Real Block={}, isLiquid={}",
                    realBlock.getRegistryName(), realIsLiquid);
                SimpleStructureScanner.LOGGER.info("[SSS TERRAIN]   -> Real Biome: {}", realWorld.getBiome(realPos).getRegistryName());

                // Show differences
                SimpleStructureScanner.LOGGER.info("[SSS TERRAIN] TERRAIN MISMATCH DETECTED!");
                SimpleStructureScanner.LOGGER.info("[SSS TERRAIN]   -> Validation Y={}, Real Y={}", pos.getY(), realPos.getY());
                SimpleStructureScanner.LOGGER.info("[SSS TERRAIN]   -> Validation Block={}, Real Block={}",
                    block.getRegistryName(), realBlock.getRegistryName());
                SimpleStructureScanner.LOGGER.info("[SSS TERRAIN]   -> Same Position: {}", pos.equals(realPos));
            }
            SimpleStructureScanner.LOGGER.info("================================================================================");
        }

        // Validate: X != 0, not liquid, Y in bounds (matches Pillar's validation)
        if (pos.getX() == 0 || state.getBlock() instanceof net.minecraft.block.BlockLiquid) {
            if (DEBUG_TERRAIN_VALIDATION) {
                SimpleStructureScanner.LOGGER.info("[SSS TERRAIN] REJECTED: X=0 or is liquid");
                SimpleStructureScanner.LOGGER.info("================================================================================");
            }
            return null;
        }

        if (!isInYBounds(schema, pos.getY())) {
            if (DEBUG_TERRAIN_VALIDATION) {
                SimpleStructureScanner.LOGGER.info("[SSS TERRAIN] REJECTED: Y out of bounds (Y={}, minY={}, maxY={})",
                    pos.getY(), schema.minY, schema.maxY);
                SimpleStructureScanner.LOGGER.info("================================================================================");
            }
            return null;
        }

        if (DEBUG_TERRAIN_VALIDATION) {
            SimpleStructureScanner.LOGGER.info("[SSS TERRAIN] ACCEPTED at pos={}", pos);
            SimpleStructureScanner.LOGGER.info("================================================================================");
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
        SimpleStructureScanner.LOGGER.info("================================================================================");
        SimpleStructureScanner.LOGGER.info("SSS PERFORMANCE PROFILING REPORT");
        SimpleStructureScanner.LOGGER.info("================================================================================");

        // Convert nanoseconds to milliseconds
        double totalPredictMs = totalPredictTime / 1_000_000.0;
        double totalGetValidationWorldMs = totalGetValidationWorldTime / 1_000_000.0;
        double totalGetYCoordinateMs = totalGetYCoordinateTime / 1_000_000.0;
        double totalPredictSingleMs = totalPredictSingleTime / 1_000_000.0;
        double totalCanSpawnMs = totalCanSpawnTime / 1_000_000.0;

        // Calculate averages
        double avgPredictMs = predictCallCount > 0 ? totalPredictMs / predictCallCount : 0;
        double avgGetValidationWorldMs = getValidationWorldCallCount > 0 ? totalGetValidationWorldMs / getValidationWorldCallCount : 0;
        double avgGetYCoordinateMs = getYCoordinateCallCount > 0 ? totalGetYCoordinateMs / getYCoordinateCallCount : 0;
        double avgPredictSingleMs = predictSingleCallCount > 0 ? totalPredictSingleMs / predictSingleCallCount : 0;
        double avgCanSpawnMs = canSpawnCallCount > 0 ? totalCanSpawnMs / canSpawnCallCount : 0;

        // Calculate percentages
        double predictPercent = totalPredictMs > 0 ? 100.0 : 0;
        double getValidationWorldPercent = totalPredictMs > 0 ? (totalGetValidationWorldMs / totalPredictMs * 100) : 0;
        double getYCoordinatePercent = totalPredictMs > 0 ? (totalGetYCoordinateMs / totalPredictMs * 100) : 0;
        double canSpawnPercent = totalPredictMs > 0 ? (totalCanSpawnMs / totalPredictMs * 100) : 0;

        SimpleStructureScanner.LOGGER.info("Call Counts:");
        SimpleStructureScanner.LOGGER.info("  predictStructureInChunk() calls:      " + String.format("%,d", predictCallCount));
        SimpleStructureScanner.LOGGER.info("  Total schema checks:                 " + String.format("%,d", totalSchemaChecks));
        SimpleStructureScanner.LOGGER.info("    Target structure checks:            " + String.format("%,d", targetStructureChecks) + " (with full verification)");
        SimpleStructureScanner.LOGGER.info("    Non-target structure checks:        " + String.format("%,d", nonTargetStructureChecks) + " (rarity + Random only)");
        SimpleStructureScanner.LOGGER.info("  getValidationWorld() calls:           " + String.format("%,d", getValidationWorldCallCount));
        SimpleStructureScanner.LOGGER.info("  getYCoordinateForGeneratorType():    " + String.format("%,d", getYCoordinateCallCount));
        SimpleStructureScanner.LOGGER.info("  canSpawnInPosition() calls:          " + String.format("%,d", canSpawnCallCount));

        SimpleStructureScanner.LOGGER.info("");
        SimpleStructureScanner.LOGGER.info("Where Schemas Fail:");
        SimpleStructureScanner.LOGGER.info("  Failed (NONE generator type):        " + String.format("%,d", failedNoneType) + " (" + String.format("%.1f", totalSchemaChecks > 0 ? (failedNoneType * 100.0 / totalSchemaChecks) : 0) + "%)");
        SimpleStructureScanner.LOGGER.info("  Failed (rarity check):                " + String.format("%,d", failedRarity) + " (" + String.format("%.1f", totalSchemaChecks > 0 ? (failedRarity * 100.0 / totalSchemaChecks) : 0) + "%)");
        SimpleStructureScanner.LOGGER.info("  Failed (Y-coordinate determination):  " + String.format("%,d", failedYCoordinate) + " (" + String.format("%.1f", totalSchemaChecks > 0 ? (failedYCoordinate * 100.0 / totalSchemaChecks) : 0) + "%)");
        SimpleStructureScanner.LOGGER.info("  Failed (spawn conditions):            " + String.format("%,d", failedSpawnConditions) + " (" + String.format("%.1f", totalSchemaChecks > 0 ? (failedSpawnConditions * 100.0 / totalSchemaChecks) : 0) + "%)");
        SimpleStructureScanner.LOGGER.info("  Passed ALL checks:                    " + String.format("%,d", passedAllChecks) + " (" + String.format("%.1f", totalSchemaChecks > 0 ? (passedAllChecks * 100.0 / totalSchemaChecks) : 0) + "%)");

        SimpleStructureScanner.LOGGER.info("");
        SimpleStructureScanner.LOGGER.info("Total Time:");
        SimpleStructureScanner.LOGGER.info("  predictStructureInChunk():            " + String.format("%.2f", totalPredictMs) + " ms (" + String.format("%.1f", predictPercent) + "%)");
        SimpleStructureScanner.LOGGER.info("    getValidationWorld():               " + String.format("%.2f", totalGetValidationWorldMs) + " ms (" + String.format("%.1f", getValidationWorldPercent) + "%)");
        SimpleStructureScanner.LOGGER.info("    getYCoordinateForGeneratorType():  " + String.format("%.2f", totalGetYCoordinateMs) + " ms (" + String.format("%.1f", getYCoordinatePercent) + "%)");
        SimpleStructureScanner.LOGGER.info("    canSpawnInPosition():               " + String.format("%.2f", totalCanSpawnMs) + " ms (" + String.format("%.1f", canSpawnPercent) + "%)");

        SimpleStructureScanner.LOGGER.info("");
        SimpleStructureScanner.LOGGER.info("Average Time Per Call:");
        SimpleStructureScanner.LOGGER.info("  predictStructureInChunk():            " + String.format("%.6f", avgPredictMs) + " ms");
        SimpleStructureScanner.LOGGER.info("  getValidationWorld():                 " + String.format("%.6f", avgGetValidationWorldMs) + " ms");
        SimpleStructureScanner.LOGGER.info("  getYCoordinateForGeneratorType():     " + String.format("%.6f", avgGetYCoordinateMs) + " ms");
        SimpleStructureScanner.LOGGER.info("  canSpawnInPosition():                 " + String.format("%.6f", avgCanSpawnMs) + " ms");

        SimpleStructureScanner.LOGGER.info("");
        SimpleStructureScanner.LOGGER.info("Analysis:");
        if (getYCoordinatePercent > 50) {
            SimpleStructureScanner.LOGGER.warn("  ? Y-COORDINATE DETERMINATION IS THE BOTTLENECK (" + String.format("%.1f", getYCoordinatePercent) + "% of time)");
            SimpleStructureScanner.LOGGER.warn("    This includes terrain scanning for SURFACE/UNDERGROUND/etc.");
            SimpleStructureScanner.LOGGER.warn("    Each call scans from Y=256 down to find the appropriate position");
        } else if (getValidationWorldPercent > 50) {
            SimpleStructureScanner.LOGGER.warn("  ? GETVALIDATIONWORLD IS THE BOTTLENECK (" + String.format("%.1f", getValidationWorldPercent) + "% of time)");
            SimpleStructureScanner.LOGGER.warn("    The caching fix didn't help - need deeper investigation");
        } else if (canSpawnPercent > 50) {
            SimpleStructureScanner.LOGGER.warn("  ? CANSPAWNINPOSITION IS THE BOTTLENECK (" + String.format("%.1f", canSpawnPercent) + "% of time)");
            SimpleStructureScanner.LOGGER.warn("    Biome lookups or distance calculations are slow");
        } else if (totalPredictSingleMs > 0 && (totalPredictSingleMs / totalPredictMs) > 0.5) {
            double predictSinglePercent = totalPredictSingleMs / totalPredictMs * 100;
            SimpleStructureScanner.LOGGER.warn("  ? PREDICTSINGLESTRUCTURE OVERHEAD IS THE BOTTLENECK (" + String.format("%.1f", predictSinglePercent) + "% of time)");
            SimpleStructureScanner.LOGGER.warn("    This includes rarity checks, Y-coordinate determination, and spawn condition checks");
        } else {
            SimpleStructureScanner.LOGGER.info("  ? No single bottleneck dominates - time is spread across operations");
        }

        // Optimization benefit analysis
        if (nonTargetStructureChecks > 0 && targetStructureChecks > 0) {
            double optimizationRatio = (double) nonTargetStructureChecks / totalSchemaChecks * 100;
            SimpleStructureScanner.LOGGER.info("");
            SimpleStructureScanner.LOGGER.info("Optimization Analysis:");
            SimpleStructureScanner.LOGGER.info("  ? Skipping verification for " + String.format("%.1f", optimizationRatio) + "% of structures (non-target)");
            SimpleStructureScanner.LOGGER.info("    Only " + String.format("%,d", targetStructureChecks) + " target structures received full verification");
            SimpleStructureScanner.LOGGER.info("    " + String.format("%,d", nonTargetStructureChecks) + " non-target structures only checked rarity + Random consumption");
            SimpleStructureScanner.LOGGER.info("    NOTE: Inter-structure interactions are not modeled (assumes they don't affect target)");
        }

        SimpleStructureScanner.LOGGER.info("================================================================================");
    }

    /**
     * Resets profiling counters. Call this before a new scan to get clean measurements.
     */
    public static void resetProfiling() {
        totalPredictTime = 0;
        totalGetValidationWorldTime = 0;
        totalPredictSingleTime = 0;
        totalGetYCoordinateTime = 0;
        totalCanSpawnTime = 0;
        predictCallCount = 0;
        getValidationWorldCallCount = 0;
        predictSingleCallCount = 0;
        getYCoordinateCallCount = 0;
        canSpawnCallCount = 0;
        profileLogged = false;

        // Reset detailed tracking
        totalSchemaChecks = 0;
        failedNoneType = 0;
        failedRarity = 0;
        failedYCoordinate = 0;
        failedSpawnConditions = 0;
        passedAllChecks = 0;

        // Reset optimization tracking
        nonTargetStructureChecks = 0;
        targetStructureChecks = 0;
    }
}
