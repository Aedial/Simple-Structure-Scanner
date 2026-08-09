package com.simplestructurescanner.structure.recurrentcomplex;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.IChunkGenerator;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.rcv.RCVRandomCache;
import com.simplestructurescanner.rcv.RCVPredictionContext;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.pillar.ValidationContextManager;
import com.simplestructurescanner.structure.util.PositionHelper;


/**
 * Handles predictive structure search for Recurrent Complex (RCV) structures.
 * <p>
 * ARCHITECTURE — Prediction-based search:
 * <p>
 * The search predicts which structures will be selected in each chunk by
 * replicating RCV's candidate-selection pipeline using the cached event
 * Random (captured by {@link com.simplestructurescanner.mixin.rcv.MixinRCForgeEventHandler}
 * and replayed by {@link com.simplestructurescanner.mixin.rcv.MixinStructureLocator}).
 * <p>
 * Unlike {@code StructureLocator.locateInChunk()}, which calls {@code test()}
 * (and fails on already-generated chunks due to the structure overlap check
 * and terrain validation), the prediction pipeline skips {@code test()}
 * entirely. It only checks whether the target structure is among the
 * candidates selected for this chunk, then computes the position from the
 * deterministic seed.
 * <p>
 * This approach works for both generated and ungenerated chunks (Phase A and
 * Phase B), with the only requirement being a cached Random entry in
 * {@link RCVRandomCache}.
 */
public class RecurrentComplexStructureSearcher {

    // ========== Configuration ==========

    private static final int SEARCH_RADIUS_CHUNKS = 64;
    private static final long MAX_SCAN_TIME_MS = 10000;

    // ========== Reflection: class names ==========

    private static final String STRUCTURE_LOCATOR_CLASS = "ivorius.reccomplex.world.gen.feature.StructureLocator";
    private static final String STRUCTURE_REGISTRY_CLASS = "ivorius.reccomplex.world.gen.feature.structure.StructureRegistry";
    private static final String RC_STRUCTURE_CLASS = "ivorius.reccomplex.world.gen.feature.structure.Structure";

    // ========== Reflection: cached objects (initialized lazily) ==========

    @Nullable
    private static Class<?> locatorClass;
    @Nullable
    private static Method chunksByDistanceMethod;
    @Nullable
    private static Object structureRegistryInstance;
    @Nullable
    private static Method registryGetMethod;
    @Nullable
    private static Method registryActiveIDsMethod;

    // Prediction pipeline methods
    @Nullable
    private static Method diagPopulationRandomMethod;
    @Nullable
    private static Method diagStaticCandidatesMethod;
    @Nullable
    private static Method diagSeedCandidatesMethod;
    @Nullable
    private static Method diagNaturalCandidatesMethod;
    @Nullable
    private static Method diagMayGenerateMethod;
    @Nullable
    private static Method pairGetLeftMethod;
    @Nullable
    private static Method pairGetRightMethod;

    // Placer check reflection
    @Nullable
    private static Class<?> structureGeneratorClass;
    @Nullable
    private static java.lang.reflect.Constructor<?> sgConstructor;
    @Nullable
    private static Method sgGenerationInfoMethod;
    @Nullable
    private static Method sgSeedMethod;
    @Nullable
    private static Method sgStructureIDMethod;
    @Nullable
    private static Method sgMaturityMethod;
    @Nullable
    private static Method sgRandomPositionMethod;
    @Nullable
    private static Method sgFromCenterMethod;
    @Nullable
    private static Method sgPartiallyMethod;
    @Nullable
    private static Method sgBoundingBoxMethod;
    @Nullable
    private static Field sgWorldField;
    private static long sgWorldFieldOffset;
    private static sun.misc.Unsafe unsafeInstance;
    @Nullable
    private static Method placerMethod;
    @Nullable
    private static Method blockSurfacePosFromMethod;
    @Nullable
    private static Object generateMaturitySuggest;
    @Nullable
    private static Field sbbMinXField, sbbMinYField, sbbMinZField;
    @Nullable
    private static Field sbbMaxXField, sbbMaxYField, sbbMaxZField;

    // Direct RC handler invocation (bypasses Forge event bus)
    @Nullable
    private static Object rcHandlerInstance;
    @Nullable
    private static Method rcHandlerMethod;

    // Village search reflection
    @Nullable
    private static Class<?> vanillaGenerationClass;
    @Nullable
    private static Class<?> genericVillagePieceClass;
    @Nullable
    private static java.lang.reflect.Constructor<?> villageStartConstructor;
    @Nullable
    private static Field startComponentsField;
    @Nullable
    private static Field componentBoundingBoxField;
    @Nullable
    private static Field gvpStructureIDField;
    @Nullable
    private static Method structureGenerationTypesMethod;
    @Nullable
    private static java.util.List<Biome> villageSpawnBiomes;

    private static boolean reflectionInitialized = false;

    // ========== Public API ==========

    @Nullable
    public static StructureLocation findNearest(World world, ResourceLocation structureId,
            BlockPos pos, int skipCount, @Nullable Predicate<BlockPos> locationFilter) {

        if (!ensureReflectionInitialized()) return null;

        WorldServer worldServer = resolveWorldServer(world);
        if (worldServer == null) return null;

        String rawId = structureId.getPath();
        Object structure = getStructureFromRegistry(rawId);
        if (structure == null) {
            SimpleStructureScanner.LOGGER.warn("Recurrent Complex structure not found in registry: {}", rawId);
            return null;
        }

        List<BlockPos> results = searchChunks(worldServer, rawId, structure, pos, Integer.MAX_VALUE);
        if (results.isEmpty()) return null;

        PositionHelper.sortByHorizontalDistance(results, pos);
        PositionHelper.FilteredPositionResult selection =
                PositionHelper.selectFilteredPosition(results, skipCount, locationFilter);
        if (selection == null) return null;

        BlockPos targetPos = selection.getPosition();
        boolean yAgnostic = targetPos.getY() == 0;

        return new StructureLocation(targetPos, skipCount, selection.getTotalMatches(), yAgnostic);
    }

    @Nullable
    public static List<BlockPos> findAllNearby(World world, ResourceLocation structureId,
            BlockPos pos, int maxResults) {

        if (!ensureReflectionInitialized()) return null;

        WorldServer worldServer = resolveWorldServer(world);
        if (worldServer == null) return null;

        String rawId = structureId.getPath();
        Object structure = getStructureFromRegistry(rawId);
        if (structure == null) {
            SimpleStructureScanner.LOGGER.warn("Recurrent Complex structure not found in registry: {}", rawId);
            return null;
        }

        List<BlockPos> results = searchChunks(worldServer, rawId, structure, pos, maxResults);

        PositionHelper.sortByHorizontalDistance(results, pos);

        return results;
    }

    // ========== Shared search loop (identical for Phase A and Phase B) ==========

    private static List<BlockPos> searchChunks(WorldServer worldServer, String structureId,
            Object structure, BlockPos origin, int maxResults) {

        if (hasVanillaGeneration(structure)) {
            return searchVillages(worldServer, structureId, structure, origin, maxResults);
        }

        List<ChunkPos> chunks = chunksByDistance(origin, SEARCH_RADIUS_CHUNKS);
        if (chunks == null) return new ArrayList<>();

        long worldSeed = worldServer.getSeed();

        SimpleStructureScanner.LOGGER.info(
                "Starting Recurrent Complex search for '{}': origin={}, radius={} chunks, maxResults={}",
                structureId, origin, SEARCH_RADIUS_CHUNKS, maxResults);

        List<BlockPos> results = new ArrayList<>();
        Set<BlockPos> foundPositions = new HashSet<>();
        long startTime = System.currentTimeMillis();
        int chunksSearched = 0;
        int cacheHits = 0;
        int eventsFired = 0;
        int dimensionId = worldServer.provider.getDimension();

        try {
        for (ChunkPos chunkPos : chunks) {
            if (results.size() >= maxResults) break;
            if (System.currentTimeMillis() - startTime > MAX_SCAN_TIME_MS) {
                SimpleStructureScanner.LOGGER.warn(
                        "Search for '{}' timed out after {}ms (searched {} chunks), found {} results",
                        structureId, MAX_SCAN_TIME_MS, chunksSearched, results.size());
                break;
            }

            chunksSearched++;

            if (RCVRandomCache.has(worldSeed, chunkPos.x, chunkPos.z)) {
                cacheHits++;
            } else {
                eventsFired++;
            }

            BlockPos found = searchInChunk(worldServer, structureId, structure, origin, chunkPos);
            if (found != null && foundPositions.add(found)) {
                results.add(found);
            }
        }
        } finally {
            ValidationContextManager.clearDimensionCache(dimensionId);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        SimpleStructureScanner.LOGGER.info(
                "Search for '{}' complete: searched {} chunks ({} cached, {} events), found {} results, took {}ms",
                structureId, chunksSearched, cacheHits, eventsFired, results.size(), elapsed);

        return results;
    }

    // ========== Village search (VanillaGeneration structures) ==========

    private static final int VILLAGE_DISTANCE = 32;
    private static final int VILLAGE_GRID_SEED = 10387312;

    /**
     * Checks if the target structure has a VanillaGeneration type (village piece).
     */
    private static boolean hasVanillaGeneration(Object structure) {
        if (vanillaGenerationClass == null || structureGenerationTypesMethod == null) return false;
        try {
            @SuppressWarnings("unchecked")
            List<?> types = (List<?>) structureGenerationTypesMethod.invoke(structure, vanillaGenerationClass);
            return types != null && !types.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Searches for the target structure in villages within the search radius.
     * <p>
     * Village positions are determined by a deterministic grid formula:
     * the world is divided into 32×32-chunk cells, each containing exactly
     * one candidate village position. For each viable village, we create a
     * {@code MapGenVillage.Start} with the deterministic Random state and
     * inspect its components for the target structure.
     * <p>
     * The Random state is deterministic because {@code MapGenBase.generate()}
     * re-seeds its RNG per chunk from the world seed (line 30). Village piece
     * selection is therefore fully predictable.
     */
    private static List<BlockPos> searchVillages(WorldServer worldServer, String structureId,
            Object structure, BlockPos origin, int maxResults) {

        List<BlockPos> results = new ArrayList<>();
        if (villageStartConstructor == null || genericVillagePieceClass == null) {
            SimpleStructureScanner.LOGGER.warn("Village search not initialized — cannot search for Recurrent Complex structure '{}'", structureId);
            return results;
        }

        long worldSeed = worldServer.getSeed();
        long startTime = System.currentTimeMillis();

        SimpleStructureScanner.LOGGER.info(
                "Starting village search for Recurrent Complex structure '{}': origin={}, maxResults={}",
                structureId, origin, maxResults);

        // Compute deterministic per-world constants used by MapGenBase.generate()
        Random seedRand = new Random(worldSeed);
        long j = seedRand.nextLong();
        long k = seedRand.nextLong();

        int centerChunkX = origin.getX() >> 4;
        int centerChunkZ = origin.getZ() >> 4;

        // Iterate grid cells overlapping the search radius
        int minCellX = Math.floorDiv(centerChunkX - SEARCH_RADIUS_CHUNKS, VILLAGE_DISTANCE);
        int maxCellX = Math.floorDiv(centerChunkX + SEARCH_RADIUS_CHUNKS, VILLAGE_DISTANCE);
        int minCellZ = Math.floorDiv(centerChunkZ - SEARCH_RADIUS_CHUNKS, VILLAGE_DISTANCE);
        int maxCellZ = Math.floorDiv(centerChunkZ + SEARCH_RADIUS_CHUNKS, VILLAGE_DISTANCE);

        int villagesChecked = 0;
        int villagesWithTarget = 0;

        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                // Grid formula: compute candidate village chunk for this cell
                long gridSeed = (long) cellX * 341873128712L + (long) cellZ * 132897987541L
                        + worldSeed + VILLAGE_GRID_SEED;
                Random gridRand = new Random(gridSeed);
                int villageChunkX = cellX * VILLAGE_DISTANCE + gridRand.nextInt(VILLAGE_DISTANCE - 8);
                int villageChunkZ = cellZ * VILLAGE_DISTANCE + gridRand.nextInt(VILLAGE_DISTANCE - 8);

                // Check biome viability (read-only, no side effects)
                if (villageSpawnBiomes != null) {
                    boolean viable = worldServer.getBiomeProvider().areBiomesViable(
                            villageChunkX * 16 + 8, villageChunkZ * 16 + 8, 0, villageSpawnBiomes);
                    if (!viable) continue;
                }

                villagesChecked++;

                BlockPos piecePos = inspectVillageForStructure(worldServer, structureId,
                        villageChunkX, villageChunkZ, worldSeed, j, k);
                if (piecePos != null) {
                    villagesWithTarget++;
                    results.add(piecePos);
                    SimpleStructureScanner.LOGGER.info(
                            "Found '{}' in village at chunk({},{}), piece position={}",
                            structureId, villageChunkX, villageChunkZ, piecePos);
                    if (results.size() >= maxResults) break;
                }
            }
            if (results.size() >= maxResults) break;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        SimpleStructureScanner.LOGGER.info(
                "Village search for '{}' complete: checked {} villages, found {} results, took {}ms",
                structureId, villagesChecked, results.size(), elapsed);

        return results;
    }

    /**
     * Creates a MapGenVillage.Start for the given village chunk and inspects
     * its components for the target structure.
     * <p>
     * The Random state is computed deterministically from the world seed and
     * chunk coordinates, matching MapGenBase.generate() line 30 (per-chunk
     * re-seeding) followed by MapGenStructure.recursiveGenerate() line 37
     * (one nextInt() advance).
     *
     * @return the center of the target piece's bounding box, or null if not found
     */
    @Nullable
    private static BlockPos inspectVillageForStructure(WorldServer worldServer, String structureId,
            int villageChunkX, int villageChunkZ, long worldSeed, long j, long k) {
        try {
            // Compute the village Random state
            long chunkSeed = ((long) villageChunkX * j) ^ ((long) villageChunkZ * k) ^ worldSeed;
            Random villageRand = new Random(chunkSeed);
            villageRand.nextInt(); // advance once (matching recursiveGenerate line 37)

            // Create the village Start — this runs the full village layout algorithm
            Object villageStart = villageStartConstructor.newInstance(
                    worldServer, villageRand, villageChunkX, villageChunkZ, 0);

            // Get components list (field access — method is SRG-mapped at runtime)
            @SuppressWarnings("unchecked")
            List<Object> components = (List<Object>) startComponentsField.get(villageStart);
            if (components == null) return null;

            // Search for target structure among village pieces
            for (Object component : components) {
                if (genericVillagePieceClass.isInstance(component)) {
                    String pieceStructureId = (String) gvpStructureIDField.get(component);
                    if (structureId.equalsIgnoreCase(pieceStructureId)) {
                        Object bb = componentBoundingBoxField.get(component);
                        if (bb != null) {
                            return boundingBoxCenter(bb);
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn(
                    "Failed to inspect village at chunk({},{})",
                    villageChunkX, villageChunkZ, e);
            return null;
        }
    }

    /**
     * Computes the center of a StructureBoundingBox.
     */
    private static BlockPos boundingBoxCenter(Object bb) {
        try {
            int minX = sbbMinXField.getInt(bb);
            int minY = sbbMinYField.getInt(bb);
            int minZ = sbbMinZField.getInt(bb);
            int maxX = sbbMaxXField.getInt(bb);
            int maxY = sbbMaxYField.getInt(bb);
            int maxZ = sbbMaxZField.getInt(bb);
            return new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        } catch (Exception e) {
            return BlockPos.ORIGIN;
        }
    }

    // ========== Per-chunk search (prediction-based, works for both Phase A and Phase B) ==========

    /**
     * Predicts whether the target structure would be selected in this chunk,
     * then validates the placement using the placer check on a validation world.
     * Returns the validated center position (including real Y) if accepted,
     * or null if the target is not among this chunk's candidates or the placer
     * rejects the terrain.
     *
     * @param worldServer  The real server world
     * @param structureId  The structure ID string (e.g., "wizard_home_overworld")
     * @param structure    The Structure object from the registry
     * @param origin       The search origin position
     * @param chunkPos     The chunk to search
     * @return Validated center position with real Y, or null if not found / rejected
     */
    @Nullable
    private static BlockPos searchInChunk(WorldServer worldServer, String structureId,
            Object structure, BlockPos origin, ChunkPos chunkPos) {

        long worldSeed = worldServer.getSeed();

        try {
            boolean mayGen = diagMayGenerateMethod != null &&
                    (boolean) diagMayGenerateMethod.invoke(null, worldServer, chunkPos);
            if (!mayGen) return null;

            boolean cached = RCVRandomCache.has(worldSeed, chunkPos.x, chunkPos.z);

            if (!cached) {
                captureRandomViaEvent(worldServer, chunkPos, worldSeed);
                if (!RCVRandomCache.has(worldSeed, chunkPos.x, chunkPos.z)) return null;
            }

            Random random = (Random) diagPopulationRandomMethod.invoke(null, worldSeed, chunkPos);

            List<?> statics = (List<?>) diagStaticCandidatesMethod.invoke(null, worldServer, chunkPos);
            diagSeedCandidatesMethod.invoke(null, statics, random);

            List<?> candidates = (List<?>) diagNaturalCandidatesMethod.invoke(null, worldServer, chunkPos, random);

            for (Object candidate : candidates) {
                long seed = random.nextLong();
                Object structObj = pairGetLeftMethod.invoke(candidate);
                if (structObj == structure) {
                    Object generation = pairGetRightMethod.invoke(candidate);
                    World validationWorld = ValidationContextManager.getValidationWorld(worldServer);
                    try {
                        BlockPos validated = validateWithPlacer(
                                validationWorld, structure, generation, structureId, seed, chunkPos);
                        if (validated == null) {
                            SimpleStructureScanner.LOGGER.debug(
                                    "Placer rejected '{}' at chunk ({},{}) — false positive filtered",
                                    structureId, chunkPos.x, chunkPos.z);
                            continue;
                        }
                        SimpleStructureScanner.LOGGER.info(
                                "Predicted structure '{}' at chunk({},{}), position={}",
                                structureId, chunkPos.x, chunkPos.z, validated);
                        return validated;
                    } catch (Exception e) {
                        SimpleStructureScanner.LOGGER.debug(
                                "Placer check failed for '{}' at chunk ({},{}) — using unvalidated position",
                                structureId, chunkPos.x, chunkPos.z, e);
                        BlockPos pos = computeSurfacePos(chunkPos, seed);
                        return pos;
                    }
                }
            }

            return null;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn(
                    "Prediction failed for Recurrent Complex structure '{}' at chunk ({},{})",
                    structureId, chunkPos.x, chunkPos.z, e);
            return null;
        }
    }

    // ========== Utility methods ==========

    /**
     * Simulates RC's PopulateChunkEvent.Pre handler to capture the event Random
     * state for chunks without a cache entry.
     * <p>
     * Creates the decoration Random using the exact vanilla formula from
     * ChunkGeneratorOverworld.populate(), then invokes RC's handler directly
     * (bypassing the Forge event bus). The Mixin on RC's handler captures the
     * Random state at HEAD and cancels the method body (via
     * RCVPredictionContext flag), preventing RC's decorate() from executing.
     */
    private static void captureRandomViaEvent(WorldServer worldServer, ChunkPos chunkPos, long worldSeed) {
        try {
            Random rand = new Random(worldSeed);
            long k = rand.nextLong() / 2L * 2L + 1L;
            long l = rand.nextLong() / 2L * 2L + 1L;
            rand.setSeed((long) chunkPos.x * k + (long) chunkPos.z * l ^ worldSeed);

            IChunkGenerator gen = ValidationContextManager.getGenerationChunkGenerator(worldServer);
            if (gen == null) return;

            World validationWorld = ValidationContextManager.getValidationWorld(worldServer);
            PopulateChunkEvent.Pre event = new PopulateChunkEvent.Pre(
                    gen, validationWorld, rand, chunkPos.x, chunkPos.z, false);

            RCVPredictionContext.setPredicting(true);
            try {
                if (rcHandlerInstance != null && rcHandlerMethod != null) {
                    rcHandlerMethod.invoke(rcHandlerInstance, event);
                } else {
                    MinecraftForge.EVENT_BUS.post(event);
                }
            } finally {
                RCVPredictionContext.setPredicting(false);
            }
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug("Event simulation failed for chunk ({},{})",
                    chunkPos.x, chunkPos.z, e);
        }
    }

    /**
     * Replicates {@code WorldGenStructures.randomSurfacePos(chunkPos, seed)}.
     * Returns the X/Z center of the structure within the chunk (Y=0, y-agnostic).
     * <p>
     * The position is deterministic from the seed: the structure's center
     * is at {@code chunkX*16 + nextInt(16)+8, chunkZ*16 + nextInt(16)+8}.
     */
    private static BlockPos computeSurfacePos(ChunkPos chunkPos, long seed) {
        Random posRandom = new Random(seed ^ 0x12048F0015F8B476L);
        int x = chunkPos.x * 16 + posRandom.nextInt(16) + 8;
        int z = chunkPos.z * 16 + posRandom.nextInt(16) + 8;
        return new BlockPos(x, 0, z);
    }

    /**
     * Validates a predicted candidate by running the placer check on validation world terrain.
     * Returns null if the placer rejects the terrain (false positive), or a BlockPos with
     * the real center coordinates (including Y) if accepted.
     *
     * @param validationWorld The validation world (StructureValidationWorld)
     * @param structure       The Structure object from the registry
     * @param generation      The NaturalGeneration from the candidate pair
     * @param structureId     The structure ID string
     * @param seed            The candidate seed
     * @param chunkPos        The chunk position
     * @return Validated center position with real Y, or null if placer rejected
     * @throws Exception if the placer check itself fails (caller should fall back to unvalidated)
     */
    @Nullable
    private static BlockPos validateWithPlacer(Object validationWorld, Object structure,
            Object generation, String structureId, long seed, ChunkPos chunkPos) throws Exception {

        Object generator = sgConstructor.newInstance(structure);

        sgGenerationInfoMethod.invoke(generator, generation);
        sgSeedMethod.invoke(generator, (Long) seed);
        sgStructureIDMethod.invoke(generator, structureId);
        sgMaturityMethod.invoke(generator, generateMaturitySuggest);

        BlockPos surfaceBlockPos = computeSurfacePos(chunkPos, seed);
        Object blockSurfacePos = blockSurfacePosFromMethod.invoke(null, surfaceBlockPos);
        Object placer = placerMethod.invoke(generation);
        sgRandomPositionMethod.invoke(generator, blockSurfacePos, placer);
        sgFromCenterMethod.invoke(generator, true);
        sgPartiallyMethod.invoke(generator, true, chunkPos);

        unsafeInstance.putObject(generator, sgWorldFieldOffset, validationWorld);

        Object resultObj = sgBoundingBoxMethod.invoke(generator);
        if (resultObj == null) return null;

        java.util.Optional<?> opt = (java.util.Optional<?>) resultObj;
        if (!opt.isPresent()) return null;

        Object bb = opt.get();
        int minX = sbbMinXField.getInt(bb);
        int minY = sbbMinYField.getInt(bb);
        int minZ = sbbMinZField.getInt(bb);
        int maxX = sbbMaxXField.getInt(bb);
        int maxY = sbbMaxYField.getInt(bb);
        int maxZ = sbbMaxZField.getInt(bb);

        return new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    @Nullable
    private static WorldServer resolveWorldServer(World world) {
        World generationWorld = ValidationContextManager.getGenerationWorld(world);
        if (generationWorld instanceof WorldServer) return (WorldServer) generationWorld;

        SimpleStructureScanner.LOGGER.warn("Could not resolve WorldServer for Recurrent Complex search (got {})",
                generationWorld == null ? "null" : generationWorld.getClass().getName());
        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static List<ChunkPos> chunksByDistance(BlockPos origin, int radius) {
        if (chunksByDistanceMethod == null) return null;
        try {
            return (List<ChunkPos>) chunksByDistanceMethod.invoke(null, origin, radius);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug("Failed to compute chunks by distance", e);
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Object getStructureFromRegistry(String structureId) {
        if (structureRegistryInstance == null || registryGetMethod == null) return null;
        try {
            Object result = registryGetMethod.invoke(structureRegistryInstance, structureId);
            if (result != null) return result;

            if (registryActiveIDsMethod != null) {
                Set<String> activeIds = (Set<String>) registryActiveIDsMethod.invoke(structureRegistryInstance);
                for (String id : activeIds) {
                    if (id.equalsIgnoreCase(structureId)) {
                        return registryGetMethod.invoke(structureRegistryInstance, id);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    // ========== Reflection initialization ==========

    private static boolean ensureReflectionInitialized() {
        if (reflectionInitialized) return locatorClass != null;
        reflectionInitialized = true;

        try {
            locatorClass = Class.forName(STRUCTURE_LOCATOR_CLASS);
            Class<?> registryClass = Class.forName(STRUCTURE_REGISTRY_CLASS);
            Class<?> structureClass = Class.forName(RC_STRUCTURE_CLASS);

            // chunksByDistance(BlockPos, int) — public static
            chunksByDistanceMethod = locatorClass.getMethod("chunksByDistance", BlockPos.class, int.class);

            // StructureRegistry.INSTANCE — public static field
            Field instanceField = registryClass.getField("INSTANCE");
            structureRegistryInstance = instanceField.get(null);

            // StructureRegistry.get(String) — public method
            registryGetMethod = registryClass.getMethod("get", String.class);

            // StructureRegistry.activeIDs() — for case-insensitive ID resolution
            registryActiveIDsMethod = registryClass.getMethod("activeIDs");

            // Prediction pipeline methods
            diagPopulationRandomMethod = locatorClass.getMethod("populationRandom", long.class, ChunkPos.class);
            diagStaticCandidatesMethod = locatorClass.getDeclaredMethod("staticCandidatesInChunk", WorldServer.class, ChunkPos.class);
            diagStaticCandidatesMethod.setAccessible(true);
            diagSeedCandidatesMethod = locatorClass.getMethod("seedCandidates", Collection.class, Random.class);
            diagNaturalCandidatesMethod = locatorClass.getDeclaredMethod("naturalCandidatesInChunk", WorldServer.class, ChunkPos.class, Random.class);
            diagNaturalCandidatesMethod.setAccessible(true);
            diagMayGenerateMethod = locatorClass.getDeclaredMethod("mayGenerateNaturally", WorldServer.class, ChunkPos.class);
            diagMayGenerateMethod.setAccessible(true);

            // Pair.getLeft() / getRight() — extract Structure / NaturalGeneration from candidate pairs
            pairGetLeftMethod = Class.forName("org.apache.commons.lang3.tuple.Pair").getMethod("getLeft");
            pairGetRightMethod = Class.forName("org.apache.commons.lang3.tuple.Pair").getMethod("getRight");

            // ========== Placer check reflection ==========
            String sgClassName = "ivorius.reccomplex.world.gen.feature.StructureGenerator";
            structureGeneratorClass = Class.forName(sgClassName);
            sgConstructor = structureGeneratorClass.getConstructor(structureClass);

            Class<?> generationTypeClass = Class.forName(
                "ivorius.reccomplex.world.gen.feature.structure.generic.generation.GenerationType");
            Class<?> placerClass2 = Class.forName("ivorius.reccomplex.world.gen.feature.structure.Placer");
            Class<?> blockSurfacePosClass2 = Class.forName("ivorius.ivtoolkit.blocks.BlockSurfacePos");
            Class<?> generateMaturityClass2 = Class.forName(
                "ivorius.reccomplex.world.gen.feature.structure.context.StructureSpawnContext$GenerateMaturity");

            sgGenerationInfoMethod = structureGeneratorClass.getMethod("generationInfo", generationTypeClass);
            sgSeedMethod = structureGeneratorClass.getMethod("seed", Long.class);
            sgStructureIDMethod = structureGeneratorClass.getMethod("structureID", String.class);
            sgMaturityMethod = structureGeneratorClass.getMethod("maturity", generateMaturityClass2);
            sgRandomPositionMethod = structureGeneratorClass.getMethod("randomPosition", blockSurfacePosClass2, placerClass2);
            sgFromCenterMethod = structureGeneratorClass.getMethod("fromCenter", boolean.class);
            sgPartiallyMethod = structureGeneratorClass.getMethod("partially", boolean.class, ChunkPos.class);
            sgBoundingBoxMethod = structureGeneratorClass.getMethod("boundingBox");
            sgWorldField = structureGeneratorClass.getDeclaredField("world");
            sgWorldField.setAccessible(true);
            // Use Unsafe to bypass Field.set()'s type check (WorldServer vs World).
            // Unsafe.putObject writes directly to memory without checking instance-of.
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            unsafeInstance = (sun.misc.Unsafe) unsafeField.get(null);
            sgWorldFieldOffset = unsafeInstance.objectFieldOffset(sgWorldField);

            placerMethod = generationTypeClass.getMethod("placer");
            blockSurfacePosFromMethod = blockSurfacePosClass2.getMethod("from", BlockPos.class);
            generateMaturitySuggest = Enum.valueOf((Class<Enum>) generateMaturityClass2, "SUGGEST");

            // StructureBoundingBox SRG field names
            Class<?> sbbClass = Class.forName("net.minecraft.world.gen.structure.StructureBoundingBox");
            sbbMinXField = sbbClass.getDeclaredField("field_78897_a");
            sbbMinYField = sbbClass.getDeclaredField("field_78895_b");
            sbbMinZField = sbbClass.getDeclaredField("field_78896_c");
            sbbMaxXField = sbbClass.getDeclaredField("field_78893_d");
            sbbMaxYField = sbbClass.getDeclaredField("field_78894_e");
            sbbMaxZField = sbbClass.getDeclaredField("field_78892_f");
            for (Field f : new Field[]{sbbMinXField, sbbMinYField, sbbMinZField, sbbMaxXField, sbbMaxYField, sbbMaxZField}) {
                f.setAccessible(true);
            }

            SimpleStructureScanner.LOGGER.info("Recurrent Complex search initialized");

            // Cache RC's Forge event handler instance + method for direct invocation.
            // This bypasses MinecraftForge.EVENT_BUS.post(), which would iterate ALL
            // registered handlers for PopulateChunkEvent.Pre across 423 mods.
            try {
                Class<?> handlerClass = Class.forName("ivorius.reccomplex.events.handlers.RCForgeEventHandler");
                rcHandlerInstance = handlerClass.newInstance();
                rcHandlerMethod = handlerClass.getMethod("onPreChunkDecoration", PopulateChunkEvent.Pre.class);
                SimpleStructureScanner.LOGGER.info("Cached Recurrent Complex event handler for direct invocation");
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.warn("Failed to cache Recurrent Complex event handler — falling back to event bus", e);
            }

            // ========== Village search reflection ==========
            try {
                vanillaGenerationClass = Class.forName(
                    "ivorius.reccomplex.world.gen.feature.structure.generic.generation.VanillaGeneration");
                genericVillagePieceClass = Class.forName(
                    "ivorius.reccomplex.world.gen.feature.villages.GenericVillagePiece");

                Class<?> mapGenVillageStartClass = Class.forName(
                    "net.minecraft.world.gen.structure.MapGenVillage$Start");
                villageStartConstructor = mapGenVillageStartClass.getConstructor(
                    World.class, Random.class, int.class, int.class, int.class);

                // Access StructureStart.components by type (method name is SRG-mapped at runtime)
                Class<?> structureStartClass = Class.forName("net.minecraft.world.gen.structure.StructureStart");
                for (Field f : structureStartClass.getDeclaredFields()) {
                    if (java.util.List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        startComponentsField = f;
                        break;
                    }
                }
                if (startComponentsField == null) throw new NoSuchFieldException("components field not found in StructureStart");

                // Access StructureComponent.boundingBox via known SRG name field_74887_e
                Class<?> structureComponentClass = Class.forName("net.minecraft.world.gen.structure.StructureComponent");
                componentBoundingBoxField = structureComponentClass.getDeclaredField("field_74887_e");
                componentBoundingBoxField.setAccessible(true);

                gvpStructureIDField = genericVillagePieceClass.getField("structureID");

                structureGenerationTypesMethod = structureClass.getMethod("generationTypes", Class.class);

                // Read MapGenVillage.VILLAGE_SPAWN_BIOMES (SRG name field_75055_e at runtime)
                @SuppressWarnings("unchecked")
                java.util.List<Biome> biomes = (java.util.List<Biome>) Class.forName(
                    "net.minecraft.world.gen.structure.MapGenVillage")
                    .getDeclaredField("field_75055_e").get(null);
                villageSpawnBiomes = biomes;

                SimpleStructureScanner.LOGGER.info("Village search initialized ({} spawn biomes)", biomes.size());
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.warn("Village search initialization failed", e);
            }

            // Check for VanillaDecorationGeneration entries that could replace villages
            try {
                Class<?> vanillaDecoClass = Class.forName(
                    "ivorius.reccomplex.world.gen.feature.structure.generic.generation.VanillaDecorationGeneration");
                Method getGenTypesMethod = registryClass.getMethod("getGenerationTypes", Class.class);
                @SuppressWarnings("unchecked")
                Collection<?> decoEntries = (Collection<?>) getGenTypesMethod.invoke(structureRegistryInstance, vanillaDecoClass);
                if (decoEntries != null && !decoEntries.isEmpty()) {
                    SimpleStructureScanner.LOGGER.warn(
                        "{} VanillaDecorationGeneration entries found — these can replace village contents during population, causing false positives in village predictions.",
                        decoEntries.size());
                }
            } catch (Exception ignored) {
            }

            return true;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Failed to initialize Recurrent Complex reflection (mod not loaded?)", e);
            return false;
        }
    }
}
