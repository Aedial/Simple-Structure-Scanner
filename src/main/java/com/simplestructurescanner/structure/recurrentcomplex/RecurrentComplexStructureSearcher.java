package com.simplestructurescanner.structure.recurrentcomplex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

import net.minecraftforge.event.terraingen.PopulateChunkEvent;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.rcv.RCVRandomCache;
import com.simplestructurescanner.rcv.RCVPredictionContext;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.util.PositionHelper;
import com.simplestructurescanner.structure.validation.ValidationContextManager;


/**
 * Finds Recurrent Complex structures by reproducing its generation checks.
 * <p>
 * For ungenerated chunks, the search filters the target's biome weight and
 * posts {@code PopulateChunkEvent.Pre} until the Recurrent Complex mixin
 * captures the random state. It then selects candidates and checks placement
 * in a validation world.
 * <p>
 * Already generated chunks use their saved {@code WorldStructureGenerationData} result
 * instead of predicting their positions.
 */
public class RecurrentComplexStructureSearcher {

    // ========== Configuration ==========

    private static final int SEARCH_RADIUS_CHUNKS = 64;
    private static final long MAX_SCAN_TIME_MS = 10000;

    // Stops later event dispatch after a listener fails in the validation world
    private static volatile boolean busPostBroken = false;

    // ========== Village search configuration ==========

    private static final int VILLAGE_SEARCH_RADIUS_CHUNKS = 128;
    private static final int VILLAGE_DISTANCE = 32;
    private static final int VILLAGE_GRID_SEED = 10387312;

    // ========== Public API ==========

    @Nullable
    public static StructureLocation findNearest(World world, ResourceLocation structureId,
            BlockPos pos, int skipCount, @Nullable Predicate<BlockPos> locationFilter) {

        if (!RecurrentComplexAccessors.isAvailable()) return null;

        WorldServer worldServer = resolveWorldServer(world);
        if (worldServer == null) return null;

        String rawId = structureId.getPath();
        RecurrentComplexAccessors.RcStructure structure = RecurrentComplexAccessors.findStructure(rawId);
        if (structure == null) {
            SimpleStructureScanner.LOGGER.warn("Recurrent Complex does not contain structure '{}'", rawId);
            return null;
        }

        List<BlockPos> results = searchChunks(worldServer, rawId, structure, pos, Integer.MAX_VALUE);
        if (results.isEmpty()) return null;

        PositionHelper.sortByHorizontalDistance(results, pos);
        PositionHelper.FilteredPositionResult selection = PositionHelper.selectFilteredPosition(results,
            skipCount, locationFilter);
        if (selection == null) return null;

        BlockPos targetPos = selection.getPosition();
        boolean yAgnostic = targetPos.getY() == 0;

        return new StructureLocation(targetPos, skipCount, selection.getTotalMatches(), yAgnostic);
    }

    @Nullable
    public static List<BlockPos> findAllNearby(World world, ResourceLocation structureId,
            BlockPos pos, int maxResults) {

        if (!RecurrentComplexAccessors.isAvailable()) return null;

        WorldServer worldServer = resolveWorldServer(world);
        if (worldServer == null) return null;

        String rawId = structureId.getPath();
        RecurrentComplexAccessors.RcStructure structure = RecurrentComplexAccessors.findStructure(rawId);
        if (structure == null) {
            SimpleStructureScanner.LOGGER.warn("Recurrent Complex does not contain structure '{}'", rawId);
            return null;
        }

        List<BlockPos> results = searchChunks(worldServer, rawId, structure, pos, maxResults);
        PositionHelper.sortByHorizontalDistance(results, pos);

        return results;
    }

    // ========== World resolution ==========

    @Nullable
    private static WorldServer resolveWorldServer(World world) {
        World generationWorld = ValidationContextManager.getGenerationWorld(world);
        if (generationWorld instanceof WorldServer) return (WorldServer) generationWorld;

        SimpleStructureScanner.LOGGER.warn("Could not get a WorldServer for Recurrent Complex search; got {}",
            generationWorld == null ? "null" : generationWorld.getClass().getName());

        return null;
    }

    // ========== Chunk search ==========

    private static List<BlockPos> searchChunks(WorldServer worldServer, String structureId,
            RecurrentComplexAccessors.RcStructure structure, BlockPos origin, int maxResults) {

        if (hasVanillaGeneration(structure)) {
            return searchVillages(worldServer, structureId, origin, maxResults);
        }

        List<RecurrentComplexAccessors.RcGeneration> naturalTypes = RecurrentComplexAccessors.naturalGenerationTypes(structure);
        boolean hasNaturalGeneration = naturalTypes == null || !naturalTypes.isEmpty();
        boolean hasStaticGeneration = RecurrentComplexAccessors.hasStaticGeneration(structure);
        if (!hasNaturalGeneration && !hasStaticGeneration) {
            SimpleStructureScanner.LOGGER.info("Skipping '{}': it has no supported generation entries",
                structureId);
            return new ArrayList<>();
        }

        // RCConfig.tweakedSpawnRate scales each natural-generation weight
        double spawnRateTweak = hasNaturalGeneration && naturalTypes != null
            ? RecurrentComplexAccessors.tweakedSpawnRate(structureId) : 1.0;

        // Cache target weights and Recurrent Complex settings for this scan
        // The search dimension stays fixed, so each biome needs only a single target weight
        Map<Biome, Double> weightByBiome = new HashMap<>();
        // isGenerationEnabled has a single result for each biome during a scan
        Map<Biome, Boolean> rcBiomeEnabled = new HashMap<>();

        List<ChunkPos> chunks = RecurrentComplexAccessors.chunksByDistance(origin, SEARCH_RADIUS_CHUNKS);
        if (chunks == null) return new ArrayList<>();

        long worldSeed = worldServer.getSeed();
        int dimensionId = worldServer.provider.getDimension();
        boolean memoizedGenerationFilter = RecurrentComplexAccessors.supportsMemoizedGenerationFilter();
        boolean rcProviderEnabled = true;
        double minDistSq = -1;
        int spawnX = 0;
        int spawnZ = 0;

        if (memoizedGenerationFilter) {
            try {
                rcProviderEnabled = RecurrentComplexAccessors.isGenerationEnabled(worldServer.provider);
                if (rcProviderEnabled && dimensionId == 0) {
                    Float minDist = RecurrentComplexAccessors.minDistanceToSpawn();
                    if (minDist != null) {
                        BlockPos spawn = worldServer.getSpawnPoint();
                        spawnX = spawn.getX();
                        spawnZ = spawn.getZ();
                        minDistSq = (double) minDist * minDist;
                    }
                }
            } catch (Exception e) {
                memoizedGenerationFilter = false;
            }
        }

        SimpleStructureScanner.LOGGER.info(
            "Searching for Recurrent Complex structure '{}' from {} within {} chunks; limit {}",
            structureId, origin, SEARCH_RADIUS_CHUNKS, maxResults);

        List<BlockPos> results = new ArrayList<>();
        Set<BlockPos> foundPositions = new HashSet<>();
        long startTime = System.currentTimeMillis();
        int chunksSearched = 0;
        int cacheHits = 0;
        int eventsFired = 0;

        try {
            for (ChunkPos chunkPos : chunks) {
                if (results.size() >= maxResults) break;
                if (System.currentTimeMillis() - startTime > MAX_SCAN_TIME_MS) {
                    SimpleStructureScanner.LOGGER.warn(
                        "Stopped Recurrent Complex search for '{}' after {} ms: scanned {} chunks and found {} results",
                        structureId, MAX_SCAN_TIME_MS, chunksSearched, results.size());
                    break;
                }

                chunksSearched++;

                boolean mayGenerateNaturally = hasNaturalGeneration
                    && mayGenerateNaturallyCached(worldServer, chunkPos, rcBiomeEnabled,
                        memoizedGenerationFilter, rcProviderEnabled, minDistSq, spawnX, spawnZ);

                if (mayGenerateNaturally) {
                    if (RCVRandomCache.has(worldSeed, chunkPos.x, chunkPos.z)) {
                        cacheHits++;
                    } else {
                        eventsFired++;
                    }
                }

                if (!hasStaticGeneration && !mayGenerateNaturally) continue;

                BlockPos found = searchInChunk(worldServer, structureId, structure, chunkPos,
                    hasStaticGeneration, mayGenerateNaturally, naturalTypes, spawnRateTweak, weightByBiome);
                if (found != null && foundPositions.add(found)) results.add(found);
            }
        } finally {
            ValidationContextManager.clearDimensionCache(dimensionId);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        SimpleStructureScanner.LOGGER.info(
            "Recurrent Complex search for '{}' completed in {} ms: scanned {} chunks, used {} cached random states, " +
                "posted {} events, and found {} results",
            structureId, elapsed, chunksSearched, cacheHits, eventsFired, results.size());

        return results;
    }

    // ========== Biome weight pre-filter ==========
    private static final int BIOME_MEMO_MAX = 100_000;

    /**
     * Caches each chunk-center biome by world seed, dimension, and chunk position.
     * Recurrent Complex selects from the biome at {@code (8, 0, 8)} within the
     * chunk. Entries use FIFO eviction beyond {@link #BIOME_MEMO_MAX}; the map
     * is synchronized because searches can run on either client or server threads.
     */
    private static final Long2ObjectLinkedOpenHashMap<Biome> BIOME_MEMO = new Long2ObjectLinkedOpenHashMap<>();

    private static long getKey(long worldSeed, int dim, ChunkPos chunkPos) {
        return worldSeed * 0x9E3779B97F4A7C15L ^ ((long) dim * 0xC2B2AE3D27D4EB4FL)
                ^ ChunkPos.asLong(chunkPos.x, chunkPos.z);
    }

    /**
     * Returns the cached chunk-center biome, reading it from the world when absent.
     */
    private static Biome biomeAt(WorldServer worldServer, ChunkPos chunkPos) {
        long worldSeed = worldServer.getSeed();
        int dim = worldServer.provider.getDimension();
        long key = getKey(worldSeed, dim, chunkPos);

        synchronized (BIOME_MEMO) {
            Biome cached = BIOME_MEMO.get(key);
            if (cached != null) return cached;

            Biome biome = worldServer.getBiome(chunkPos.getBlock(8, 0, 8));
            if (BIOME_MEMO.size() >= BIOME_MEMO_MAX) BIOME_MEMO.remove(BIOME_MEMO.firstLongKey());
            BIOME_MEMO.put(key, biome);

            return biome;
        }
    }

    /**
     * Returns a cached biome for a chunk-center position, or null for any other position.
     */
    @Nullable
    public static Biome getCachedChunkCenterBiome(World world, BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        if ((x & 15) != 8 || (z & 15) != 8) return null;

        long key = getKey(world.getSeed(), world.provider.getDimension(), new ChunkPos(x >> 4, z >> 4));
        synchronized (BIOME_MEMO) {
            return BIOME_MEMO.get(key);
        }
    }

    /**
     * Returns the highest target NaturalGeneration weight in a biome after
     * {@code tweakedSpawnRate} is applied. A non-positive weight excludes the
     * structure from selection. Reflection failure returns infinity so the
     * search keeps the chunk.
     */
    private static double maxNaturalWeight(
            List<RecurrentComplexAccessors.RcGeneration> naturalTypes, double spawnRateTweak,
            WorldProvider provider, Biome biome) {
        if (naturalTypes == null) return Double.POSITIVE_INFINITY;

        double max = 0;
        try {
            for (RecurrentComplexAccessors.RcGeneration type : naturalTypes) {
                double weight = RecurrentComplexAccessors.generationWeight(type, provider, biome);
                if (weight > max) max = weight;
            }
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }

        return max * spawnRateTweak;
    }

    // ========== Cached mayGenerateNaturally checks ==========

    /**
     * Runs {@code StructureLocator.mayGenerateNaturally} with Recurrent Complex
     * settings cached for the scan. Uses the reflected method when settings are
     * unavailable.
     */
    private static boolean mayGenerateNaturallyCached(WorldServer worldServer, ChunkPos chunkPos,
            Map<Biome, Boolean> rcBiomeEnabled, boolean memoizedGenerationFilter,
            boolean rcProviderEnabled, double minDistSq, int spawnX, int spawnZ) {
        if (!memoizedGenerationFilter) {
            return RecurrentComplexAccessors.mayGenerateNaturally(worldServer, chunkPos);
        }

        try {
            Biome biome = biomeAt(worldServer, chunkPos);
            Boolean biomeEnabled = rcBiomeEnabled.get(biome);
            if (biomeEnabled == null) {
                biomeEnabled = RecurrentComplexAccessors.isGenerationEnabled(biome);
                rcBiomeEnabled.put(biome, biomeEnabled);
            }

            if (!biomeEnabled || !rcProviderEnabled) return false;

            // Dimension 0 requires the chunk center to be outside the spawn radius
            if (minDistSq < 0) return true;

            double dx = chunkPos.x * 16 + 8 - spawnX;
            double dz = chunkPos.z * 16 + 8 - spawnZ;

            return dx * dx + dz * dz >= minDistSq;
        } catch (Exception e) {
            return true;
        }
    }

    // ========== Village search for VanillaGeneration structures ==========

    /**
     * Checks whether the structure can occur as a Recurrent Complex village piece.
     */
    private static boolean hasVanillaGeneration(RecurrentComplexAccessors.RcStructure structure) {
        return RecurrentComplexAccessors.hasVanillaGeneration(structure);
    }

    /**
     * Searches candidate villages for a matching Recurrent Complex piece.
     * <p>
     * Each 32-by-32 chunk cell supplies a village candidate. Viable candidates
     * build a {@link MapGenVillage.Start} from the same chunk random state used
     * during generation, then inspect its components.
     */
    private static List<BlockPos> searchVillages(WorldServer worldServer, String structureId,
            BlockPos origin, int maxResults) {

        List<BlockPos> results = new ArrayList<>();
        if (!RecurrentComplexAccessors.isVillageSearchAvailable()) {
            SimpleStructureScanner.LOGGER.warn(
                "Cannot search villages for '{}': Recurrent Complex village access is unavailable", structureId);
            return results;
        }

        long worldSeed = worldServer.getSeed();
        long startTime = System.currentTimeMillis();

        SimpleStructureScanner.LOGGER.info(
                "Searching villages for Recurrent Complex structure '{}' from {}; limit {}",
                structureId, origin, maxResults);

        // Read the MapGenBase seed multipliers for this world
        Random seedRand = new Random(worldSeed);
        long j = seedRand.nextLong();
        long k = seedRand.nextLong();

        int centerChunkX = origin.getX() >> 4;
        int centerChunkZ = origin.getZ() >> 4;

        // Visit village cells that overlap the search radius
        int minCellX = Math.floorDiv(centerChunkX - VILLAGE_SEARCH_RADIUS_CHUNKS, VILLAGE_DISTANCE);
        int maxCellX = Math.floorDiv(centerChunkX + VILLAGE_SEARCH_RADIUS_CHUNKS, VILLAGE_DISTANCE);
        int minCellZ = Math.floorDiv(centerChunkZ - VILLAGE_SEARCH_RADIUS_CHUNKS, VILLAGE_DISTANCE);
        int maxCellZ = Math.floorDiv(centerChunkZ + VILLAGE_SEARCH_RADIUS_CHUNKS, VILLAGE_DISTANCE);

        int villagesChecked = 0;
        int villagesWithTarget = 0;

        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                // Select this cell's candidate village chunk
                long gridSeed = (long) cellX * 341873128712L + (long) cellZ * 132897987541L
                        + worldSeed + VILLAGE_GRID_SEED;
                Random gridRand = new Random(gridSeed);
                int villageChunkX = cellX * VILLAGE_DISTANCE + gridRand.nextInt(VILLAGE_DISTANCE - 8);
                int villageChunkZ = cellZ * VILLAGE_DISTANCE + gridRand.nextInt(VILLAGE_DISTANCE - 8);

                // Check the biome required for village generation
                boolean viable = worldServer.getBiomeProvider().areBiomesViable(
                    villageChunkX * 16 + 8, villageChunkZ * 16 + 8, 0,
                    MapGenVillage.VILLAGE_SPAWN_BIOMES);
                if (!viable) continue;

                villagesChecked++;

                BlockPos piecePos = inspectVillageForStructure(worldServer, structureId,
                        villageChunkX, villageChunkZ, worldSeed, j, k);
                if (piecePos != null) {
                    villagesWithTarget++;
                    results.add(piecePos);
                    SimpleStructureScanner.LOGGER.info(
                        "Found '{}' in village chunk ({},{}), structure center {}",
                        structureId, villageChunkX, villageChunkZ, piecePos);
                    if (results.size() >= maxResults) break;
                }
            }

            if (results.size() >= maxResults) break;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        SimpleStructureScanner.LOGGER.info(
                "Village search for '{}' completed in {} ms: checked {} villages and found {} results",
                structureId, elapsed, villagesChecked, results.size());

        return results;
    }

    /**
     * Builds a village start for a chunk and inspects its Recurrent Complex pieces.
     * <p>
     * The random state uses the same world-seed and chunk-coordinate calculation
     * as {@code MapGenBase.generate()}, including the advance before the start.
     *
     * @return the center of the target piece's bounding box, or null if not found
     */
    @Nullable
    private static BlockPos inspectVillageForStructure(WorldServer worldServer, String structureId,
            int villageChunkX, int villageChunkZ, long worldSeed, long j, long k) {
        try {
            // Match the per-chunk random state used to create the village
            long chunkSeed = ((long) villageChunkX * j) ^ ((long) villageChunkZ * k) ^ worldSeed;
            Random villageRand = new Random(chunkSeed);
            villageRand.nextInt(); // MapGenStructure.recursiveGenerate consumes this value first

            // Build the village components with MapGenVillage's layout code
            StructureStart village = new MapGenVillage.Start(worldServer,
                villageRand, villageChunkX, villageChunkZ, 0);
            List<StructureComponent> components = village.getComponents();
            if (components == null) return null;

            // Find the requested Recurrent Complex piece
            for (StructureComponent component : components) {
                if (RecurrentComplexAccessors.isVillagePiece(component)) {
                    String pieceStructureId = RecurrentComplexAccessors.villagePieceStructureId(component);
                    if (structureId.equalsIgnoreCase(pieceStructureId)) {
                        StructureBoundingBox bb = component.getBoundingBox();
                        if (bb != null) return boundingBoxCenter(bb);
                    }
                }
            }

            return null;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Could not inspect village in chunk ({},{})",
                villageChunkX, villageChunkZ, e);
            return null;
        }
    }

    /**
     * Returns the center of a structure bounding box.
     */
    private static BlockPos boundingBoxCenter(StructureBoundingBox boundingBox) {
        return new BlockPos(
            (boundingBox.minX + boundingBox.maxX) / 2,
            (boundingBox.minY + boundingBox.maxY) / 2,
            (boundingBox.minZ + boundingBox.maxZ) / 2);
    }

    // ========== Per-chunk prediction ==========

    /**
     * Predicts target selection in a chunk and validates its placement in a
     * validation world.
     *
     * @param worldServer The source server world
     * @param structureId The Recurrent Complex structure ID
     * @param structure The reflected Recurrent Complex structure
     * @param chunkPos The chunk to search
     * @param searchStatic Whether the target has StaticGeneration entries
     * @param mayGenerateNaturally Whether Recurrent Complex permits natural generation in this chunk
     * @param naturalTypes The target NaturalGeneration types, or null when unavailable
     * @param spawnRateTweak RCConfig.tweakedSpawnRate for the target structure
     * @param weightByBiome Per-search natural generation weights
     * @return Validated center position with Y, or null when selection or placement fails
     */
    @Nullable
    private static BlockPos searchInChunk(WorldServer worldServer, String structureId,
            RecurrentComplexAccessors.RcStructure structure, ChunkPos chunkPos,
            boolean searchStatic, boolean mayGenerateNaturally,
            @Nullable List<RecurrentComplexAccessors.RcGeneration> naturalTypes,
            double spawnRateTweak, Map<Biome, Double> weightByBiome) {

        long worldSeed = worldServer.getSeed();

        try {
            // Use saved Recurrent Complex results when the chunk is loaded or generated on disk

            Chunk loadedChunk = worldServer.getChunkProvider().getLoadedChunk(chunkPos.x, chunkPos.z);
            boolean realChunkLoaded = loadedChunk != null;

            boolean generatedOnDisk = false;
            if (!realChunkLoaded && worldServer.getChunkProvider() instanceof ChunkProviderServer) {
                generatedOnDisk = ((ChunkProviderServer) worldServer.getChunkProvider())
                        .isChunkGeneratedAt(chunkPos.x, chunkPos.z);
            }

            if ((realChunkLoaded || generatedOnDisk) && RecurrentComplexAccessors.hasLedgerAccess()) {
                RecurrentComplexAccessors.LedgerChunk ledger =
                        RecurrentComplexAccessors.ledgerChunk(worldServer, chunkPos, structureId);
                if (RecurrentComplexAccessors.isChecked(ledger)) {
                    BlockPos center = RecurrentComplexAccessors.structurePosition(ledger);
                    if (center != null) {
                        SimpleStructureScanner.LOGGER.debug(
                            "Saved Recurrent Complex data confirms '{}' in chunk ({},{}), center {}",
                            structureId, chunkPos.x, chunkPos.z, center);
                        return center;
                    }

                    SimpleStructureScanner.LOGGER.debug(
                            "Skipping '{}' in {} chunk ({},{}): Recurrent Complex has no saved structure entry",
                            structureId, realChunkLoaded ? "loaded" : "generated", chunkPos.x, chunkPos.z);
                    return null;
                }

                // Skip populated or unloaded generated chunks Recurrent Complex did not process
                if (realChunkLoaded) {
                    if (loadedChunk.isTerrainPopulated()) {
                        SimpleStructureScanner.LOGGER.debug(
                                "Skipping '{}' in populated chunk ({},{}): Recurrent Complex did not process it",
                            structureId, chunkPos.x, chunkPos.z);
                        return null;
                    }
                } else {
                    SimpleStructureScanner.LOGGER.debug(
                            "Skipping '{}' in generated chunk ({},{}): Recurrent Complex did not process it",
                        structureId, chunkPos.x, chunkPos.z);
                    return null;
                }
            }

            Random random = RecurrentComplexAccessors.populationRandom(worldSeed, chunkPos);
            List<RecurrentComplexAccessors.RcStaticCandidate> staticCandidates =
                RecurrentComplexAccessors.staticCandidates(worldServer, chunkPos, random);

            if (searchStatic) {
                for (RecurrentComplexAccessors.RcStaticCandidate candidate : staticCandidates) {
                    if (candidate.structureValue() != structure.value()) continue;

                    World validationWorld = ValidationContextManager.getValidationWorld(worldServer);
                    try {
                        BlockPos validated = RecurrentComplexAccessors.validateStaticPlacement(
                            validationWorld, structure, candidate, structureId, chunkPos);
                        if (validated == null) {
                            SimpleStructureScanner.LOGGER.debug(
                                "Static placement rejected '{}' in chunk ({},{})",
                                structureId, chunkPos.x, chunkPos.z);
                            continue;
                        }

                        SimpleStructureScanner.LOGGER.info(
                            "Predicted static '{}' in chunk ({},{}), center {}",
                            structureId, chunkPos.x, chunkPos.z, validated);
                        return validated;
                    } catch (Exception e) {
                        SimpleStructureScanner.LOGGER.warn(
                            "Could not validate static '{}' in chunk ({},{}): {}: {}",
                            structureId, chunkPos.x, chunkPos.z,
                            e.getClass().getSimpleName(), e.getMessage());
                    }
                }
            }

            if (!mayGenerateNaturally) return null;

            // Skip chunks where every target NaturalGeneration entry has non-positive weight
            if (naturalTypes != null) {
                Biome chunkBiome = biomeAt(worldServer, chunkPos);
                Double weight = weightByBiome.get(chunkBiome);
                if (weight == null) {
                    weight = maxNaturalWeight(naturalTypes, spawnRateTweak,
                            worldServer.provider, chunkBiome);
                    weightByBiome.put(chunkBiome, weight);
                }

                if (weight <= 0) return null;
            }

            if (!RCVRandomCache.has(worldSeed, chunkPos.x, chunkPos.z)) {
                captureRandomViaEvent(worldServer, chunkPos, worldSeed);
                if (!RCVRandomCache.has(worldSeed, chunkPos.x, chunkPos.z)) return null;
            }

            List<RecurrentComplexAccessors.RcCandidate> candidates =
                    RecurrentComplexAccessors.naturalCandidates(worldServer, chunkPos, random);

            if (candidates.isEmpty()) return null;

            for (RecurrentComplexAccessors.RcCandidate candidate : candidates) {
                long seed = random.nextLong();
                if (candidate.structureValue() == structure.value()) {
                    RecurrentComplexAccessors.RcGeneration generation = candidate.generation();
                    World validationWorld = ValidationContextManager.getValidationWorld(worldServer);
                    try {
                        BlockPos validated = RecurrentComplexAccessors.validatePlacement(
                            validationWorld, structure, generation, structureId, seed, chunkPos);
                        if (validated == null) {
                            SimpleStructureScanner.LOGGER.debug(
                                    "Placement rejected '{}' in chunk ({},{})",
                                    structureId, chunkPos.x, chunkPos.z);
                            continue;
                        }

                        SimpleStructureScanner.LOGGER.info(
                            "Predicted '{}' in chunk ({},{}), center {}",
                            structureId, chunkPos.x, chunkPos.z, validated);
                        return validated;
                    } catch (Exception e) {
                        SimpleStructureScanner.LOGGER.warn(
                            "Could not validate '{}' in chunk ({},{}); using its seeded surface position: {}: {}",
                            structureId, chunkPos.x, chunkPos.z, e.getClass().getSimpleName(), e.getMessage());
                        BlockPos pos = RecurrentComplexAccessors.computeSurfacePos(chunkPos, seed);
                        return pos;
                    }
                }
            }

            return null;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn(
                "Could not predict Recurrent Complex structure '{}' in chunk ({},{})",
                structureId, chunkPos.x, chunkPos.z, e);
            return null;
        }
    }

    // ========== Population-event random capture ==========

    /**
     * Posts {@code PopulateChunkEvent.Pre} to capture the random state Recurrent
     * Complex receives for a chunk without a cached state.
     * <p>
     * Forge passes the same random instance through listeners in registration
     * order. The mixin records that state when Recurrent Complex begins and
     * cancels its generation code.
     * <p>
     * A listener failure disables later event dispatch. The search does not call
     * Recurrent Complex directly because earlier listeners may change the random
     * state. This direct loop still invokes Recurrent Complex after cancellation.
     */
    private static void captureRandomViaEvent(WorldServer worldServer, ChunkPos chunkPos,
            long worldSeed) {
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
                if (busPostBroken) return;

                try {
                    RecurrentComplexAccessors.dispatchUntilRcCaptured(event);
                } catch (Throwable t) {
                    busPostBroken = true;

                    StackTraceElement[] st = t.getStackTrace();
                    SimpleStructureScanner.LOGGER.warn(
                        "Could not capture the population random in chunk ({},{}): {} at {}; " +
                        "disabling later captures",
                        chunkPos.x, chunkPos.z, t.getClass().getSimpleName(), st.length > 0 ? st[0] : "?");
                }
            } finally {
                RCVPredictionContext.setPredicting(false);
            }
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug("Could not prepare the population event for chunk ({},{})",
                chunkPos.x, chunkPos.z, e);
        }
    }

}
