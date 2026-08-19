package com.simplestructurescanner.structure.recurrentcomplex;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
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

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.IEventListener;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.rcv.RCVRandomCache;
import com.simplestructurescanner.rcv.RCVPredictionContext;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.pillar.StructureValidationWorld;
import com.simplestructurescanner.structure.pillar.ValidationContextManager;
import com.simplestructurescanner.structure.util.PositionHelper;


/**
 * Handles predictive structure search for Recurrent Complex (RCV) structures.
 * <p>
 * ARCHITECTURE — Prediction-based search:
 * <p>
 * The search predicts which structures will be selected in each chunk by
 * replicating RCV's candidate-selection pipeline. The population Random is
 * captured by dispatching {@code PopulateChunkEvent.Pre} to the real Forge
 * event bus's registered listeners in order (so handlers registered before RC
 * consume the Random in true registration order), stopping as soon as RC's
 * handler is reached — {@link com.simplestructurescanner.mixin.rcv.MixinRCForgeEventHandler}
 * captures the post-consumption state RC actually receives and cancels RC's
 * body. Handlers registered after RC are skipped: they cannot influence the
 * Random state RC receives, so this is behavior-identical while avoiding
 * their cost.
 * <p>
 * Before any event dispatch, a per-biome weight filter rejects chunks whose
 * biome gives the target structure a generation weight of zero — RC's
 * StructureSelector never admits such entries into its selection, so the
 * chunk cannot contain the target.
 * <p>
 * Matching candidates are validated by running RC's {@code test()} on a
 * simulated validation world (populated terrain, overlap check disabled via
 * {@code allowOverlaps(true)}), yielding the structure's real center position.
 * <p>
 * For chunks that are already generated and recorded in RC's persisted
 * generation ledger ({@code WorldStructureGenerationData}), the ledger's ground
 * truth is used directly instead of simulation: predictions come from the real
 * recorded bounding box, and chunks where RC decided not to generate are skipped.
 */
public class RecurrentComplexStructureSearcher {

    // ========== Configuration ==========

    private static final int SEARCH_RADIUS_CHUNKS = 64;
    private static final int VILLAGE_SEARCH_RADIUS_CHUNKS = 128;
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

    // MethodHandle fast paths for the per-chunk hot reflection call sites.
    // Built via Method.unreflect() from the resolved Methods; null = that call
    // site keeps using Method.invoke (fail-open, logged once at debug).
    @Nullable
    private static MethodHandle mhPopulationRandom;
    @Nullable
    private static MethodHandle mhStaticCandidates;
    @Nullable
    private static MethodHandle mhSeedCandidates;
    @Nullable
    private static MethodHandle mhNaturalCandidates;
    @Nullable
    private static MethodHandle mhPairGetLeft;
    @Nullable
    private static MethodHandle mhPairGetRight;

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
    private static Method sgBoundingBoxMethod;
    @Nullable
    private static Method sgStructureSizeMethod;
    @Nullable
    private static Field sgWorldField;
    private static long sgWorldFieldOffset;
    private static sun.misc.Unsafe unsafeInstance;
    @Nullable
    private static Method placerMethod;
    @Nullable
    private static Method blockSurfacePosFromMethod;
    @Nullable
    private static Method sgTestMethod;
    @Nullable
    private static Method sgAllowOverlapsMethod;
    @Nullable
    private static Method sgEnvironmentMethod;
    @Nullable
    private static Method grSucceededMethod;
    @Nullable
    private static Field failureDescriptionField;
    @Nullable
    private static Field envBiomeField;
    @Nullable
    private static Method ngGetGenerationWeightMethod;
    @Nullable
    private static Object generateMaturitySuggest;
    @Nullable
    private static Field sbbMinXField, sbbMinYField, sbbMinZField;
    @Nullable
    private static Field sbbMaxXField, sbbMaxYField, sbbMaxZField;

    // Partial event dispatch (stop at RC's handler instead of running the whole bus)
    @Nullable
    private static Field eventBusBusIdField;
    private static int forgeBusId = -1;

    // Per-biome weight filter
    @Nullable
    private static Class<?> naturalGenerationClass;
    @Nullable
    private static Method rcTweakedSpawnRateMethod;

    // Memoized mayGenerateNaturally (RCConfig checks are per-biome constants)
    @Nullable
    private static Method rcGenEnabledBiomeMethod;
    @Nullable
    private static Method rcGenEnabledProviderMethod;
    @Nullable
    private static java.lang.reflect.Field rcMinDistToSpawnField;

    // Ground-truth ledger reflection (WorldStructureGenerationData — RC's persisted
    // record of which chunks it processed and what it generated there)
    @Nullable
    private static Method wsgdGetMethod;
    @Nullable
    private static Method wsgdIsChunkCheckedMethod;
    @Nullable
    private static Method wsgdStructureEntriesInMethod;
    @Nullable
    private static Method wsgdGetStructureIDMethod;
    @Nullable
    private static Method entryGetBoundingBoxMethod;
    @Nullable
    private static Method streamIteratorMethod;

    // Set after a real event-bus post for capture fails (handler crashed on the
    // validation world); all subsequent captures use direct invocation fallback.
    private static volatile boolean busPostBroken = false;

    /**
     * Listener (wrapper toString() prefix) allow-list for capture-time skipping.
     * A listener may be listed here ONLY if bytecode analysis proves both:
     * <ul>
     *   <li>it never consumes the event's Random (no rand reads anywhere in the
     *       handler — skipping cannot change the seed RC receives), and</li>
     *   <li>its world effects cannot reach the chunks being scanned (so the
     *       validation terrain stays identical to what real generation produces
     *       for those chunks).</li>
     * </ul>
     * Pinned to this modpack's mod versions; re-verify if a listed mod updates.
     * <ul>
     *   <li>{@code AbyssalCraftEventHooks} (AbyssalCraft 1.12.2-1.11.3):
     *       {@code populateChunk} performs zero Random calls — it scans the
     *       chunk's block storages (y>=60) and replaces stone with abyssal
     *       stone only in the darklands_mountains biome (an Abyssal Craft
     *       dimension biome, unreachable in overworld scans). Its ~2ms/chunk
     *       biome lookups otherwise dominate the entire capture phase.</li>
     * </ul>
     */
    private static final String[] RAND_INDEPENDENT_LISTENER_PREFIXES = {
            "ASM: com.shinoow.abyssalcraft.common.handlers.AbyssalCraftEventHooks",
    };

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

        // Structures without NaturalGeneration types can never be selected by
        // the natural candidate pipeline — no chunk can contain them.
        ScanContext ctx = new ScanContext();
        if (!prepareNaturalWeightFilter(ctx, structure, structureId)) {
            SimpleStructureScanner.LOGGER.info(
                    "Skipping search for '{}': structure has no NaturalGeneration types — cannot generate naturally",
                    structureId);
            return new ArrayList<>();
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

            BlockPos found = searchInChunk(worldServer, structureId, structure, origin, chunkPos, ctx);
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

    // ========== MethodHandle fast paths for hot reflection ==========
    //
    // Method.invoke pays per-call access checks; MethodHandle skips them. These
    // helpers prefer the handle and fall back to the Method when the handle
    // could not be built. Throwable from handle invocation is wrapped so call
    // sites keep the same 'throws Exception' shape as Method.invoke.

    @Nullable
    private static MethodHandle unreflectOrNull(@Nullable Method method) {
        if (method == null) return null;
        try {
            return MethodHandles.publicLookup().unreflect(method);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug(
                    "MethodHandle unavailable for {} — using Method.invoke",
                    method.getName(), e);
            return null;
        }
    }

    /** Invokes a 1-arg method via handle (preferred) or reflective fallback. */
    private static Object invoke1(@Nullable MethodHandle mh, Method m, @Nullable Object target, Object a) throws Exception {
        if (mh != null) {
            try {
                return mh.invoke(a);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        return m.invoke(target, a);
    }

    /** Invokes a 2-arg method via handle (preferred) or reflective fallback. */
    private static Object invoke2(@Nullable MethodHandle mh, Method m, @Nullable Object target, Object a, Object b) throws Exception {
        if (mh != null) {
            try {
                return mh.invoke(a, b);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        return m.invoke(target, a, b);
    }

    /** Invokes a 3-arg method via handle (preferred) or reflective fallback. */
    private static Object invoke3(@Nullable MethodHandle mh, Method m, @Nullable Object target, Object a, Object b, Object c) throws Exception {
        if (mh != null) {
            try {
                return mh.invoke(a, b, c);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        return m.invoke(target, a, b, c);
    }

    /** Per-search state: memoized filters shared across the chunks of one search. */
    private static final class ScanContext {
        /** Maximum natural generation weight of the target per biome (dimension fixed per search). */
        final Map<Biome, Double> weightByBiome = new HashMap<>();
        /** The target's NaturalGeneration entries; null = weight filter unavailable (fail open). */
        @Nullable
        List<?> naturalTypes;
        /** RCConfig.tweakedSpawnRate for the target structure ID (RC multiplies it into weights). */
        double spawnRateTweak = 1.0;
        /** Memoized RCConfig.isGenerationEnabled per biome (config is constant within a search). */
        final Map<Biome, Boolean> rcBiomeEnabled = new HashMap<>();
        /** Memoized RCConfig.isGenerationEnabled(provider) — constant within a search. */
        boolean rcProviderEnabledComputed = false;
        boolean rcProviderEnabled = false;
        /** Hoisted spawn-distance inputs (dimension 0 only; lazy on first use). */
        boolean spawnInfoComputed = false;
        int spawnX;
        int spawnZ;
        double minDistSq;
    }

    // ========== Biome weight pre-filter ==========

    /**
     * Global per-chunk biome memo. Biome values are deterministic per world
     * (the biome layer never changes), so each chunk's center biome is fetched
     * from {@code WorldServer.getBiome} — the exact path RC's selection code
     * uses — once, then reused by every subsequent search. Keyed by world
     * seed, dimension, and chunk position; FIFO eviction beyond
     * {@link #BIOME_MEMO_MAX}. Synchronized like {@link RCVRandomCache}
     * (client-thread scans, server-thread safety margin).
     */
    private static final int BIOME_MEMO_MAX = 100_000;
    private static final Long2ObjectLinkedOpenHashMap<Biome> BIOME_MEMO = new Long2ObjectLinkedOpenHashMap<>();

    /** Reusable chunk-center position for biome lookups (scan-thread only). */
    private static final BlockPos.MutableBlockPos BIOME_LOOKUP_POS = new BlockPos.MutableBlockPos();

    /** Returns the chunk-center biome via the global memo (single fetch per chunk ever). */
    private static Biome biomeAt(WorldServer worldServer, ChunkPos chunkPos) {
        long worldSeed = worldServer.getSeed();
        int dim = worldServer.provider.getDimension();
        long key = worldSeed * 0x9E3779B97F4A7C15L ^ ((long) dim * 0xC2B2AE3D27D4EB4FL)
                ^ ChunkPos.asLong(chunkPos.x, chunkPos.z);
        synchronized (BIOME_MEMO) {
            Biome cached = BIOME_MEMO.get(key);
            if (cached != null) return cached;
            BIOME_LOOKUP_POS.setPos(chunkPos.x * 16 + 8, 0, chunkPos.z * 16 + 8);
            Biome biome = worldServer.getBiome(BIOME_LOOKUP_POS);
            if (BIOME_MEMO.size() >= BIOME_MEMO_MAX) {
                BIOME_MEMO.remove(BIOME_MEMO.firstLongKey());
            }
            BIOME_MEMO.put(key, biome);
            return biome;
        }
    }

    /**
     * Prepares the per-search biome weight filter. Returns false if the target
     * has no NaturalGeneration types at all — the search can be skipped
     * entirely because RC's natural candidate selection can never pick it.
     * <p>
     * On reflection failure the filter is disabled (fail open) and the search
     * proceeds unfiltered, exactly as before this filter existed.
     */
    private static boolean prepareNaturalWeightFilter(ScanContext ctx, Object structure, String structureId) {
        if (naturalGenerationClass == null || structureGenerationTypesMethod == null) return true;

        try {
            List<?> types = (List<?>) structureGenerationTypesMethod.invoke(structure, naturalGenerationClass);
            ctx.naturalTypes = types != null ? types : new ArrayList<>();
            if (ctx.naturalTypes.isEmpty()) return false;

            if (rcTweakedSpawnRateMethod != null) {
                ctx.spawnRateTweak = (float) rcTweakedSpawnRateMethod.invoke(null, structureId);
            }
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug(
                    "NaturalGeneration weight filter unavailable — searching unfiltered", e);
            ctx.naturalTypes = null;
        }
        return true;
    }

    /**
     * Returns the maximum weight any of the target's NaturalGeneration entries
     * has in the given biome, multiplied by the structure's spawn-rate tweak —
     * the exact quantity RC's StructureSelector requires to be positive before
     * admitting an entry into its selection multimap. Fails open (positive
     * infinity) on reflection errors so the filter can never cause a miss.
     */
    private static double maxNaturalWeight(ScanContext ctx, WorldProvider provider, Biome biome) {
        if (ctx.naturalTypes == null || ngGetGenerationWeightMethod == null) {
            return Double.POSITIVE_INFINITY;
        }
        double max = 0;
        try {
            for (Object type : ctx.naturalTypes) {
                double weight = (double) ngGetGenerationWeightMethod.invoke(type, provider, biome);
                if (weight > max) max = weight;
            }
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }
        return max * ctx.spawnRateTweak;
    }

    // ========== Memoized mayGenerateNaturally ==========

    /**
     * Replicates RC's {@code StructureLocator.mayGenerateNaturally} (bytecode-verified)
     * with its per-biome RCConfig checks memoized: the biome is fetched for the
     * chunk center, {@code RCConfig.isGenerationEnabled(biome/provider)} results
     * are cached per search, and the spawn-distance test is plain arithmetic.
     * Fails open (true) when the RCConfig reflection is unavailable, falling back
     * to the direct method invocation.
     */
    private static boolean mayGenerateNaturallyCached(WorldServer worldServer, ChunkPos chunkPos, ScanContext ctx) {
        if (rcGenEnabledBiomeMethod == null || rcGenEnabledProviderMethod == null) {
            try {
                return diagMayGenerateMethod != null &&
                        (boolean) diagMayGenerateMethod.invoke(null, worldServer, chunkPos);
            } catch (Exception e) {
                return true;
            }
        }

        try {
            Biome biome = biomeAt(worldServer, chunkPos);
            Boolean biomeEnabled = ctx.rcBiomeEnabled.get(biome);
            if (biomeEnabled == null) {
                biomeEnabled = (boolean) rcGenEnabledBiomeMethod.invoke(null, biome);
                ctx.rcBiomeEnabled.put(biome, biomeEnabled);
            }
            if (!biomeEnabled) return false;

            if (!ctx.rcProviderEnabledComputed) {
                ctx.rcProviderEnabled = (boolean) rcGenEnabledProviderMethod.invoke(null, worldServer.provider);
                ctx.rcProviderEnabledComputed = true;
            }
            if (!ctx.rcProviderEnabled) return false;

            // Dim 0 only: require chunk center outside the min spawn distance
            // (spawn point + minDist^2 hoisted into ctx on first use)
            if (worldServer.provider.getDimension() == 0 && rcMinDistToSpawnField != null) {
                if (!ctx.spawnInfoComputed) {
                    float minDist = rcMinDistToSpawnField.getFloat(null);
                    BlockPos spawn = worldServer.getSpawnPoint();
                    ctx.spawnX = spawn.getX();
                    ctx.spawnZ = spawn.getZ();
                    ctx.minDistSq = (double) minDist * (double) minDist;
                    ctx.spawnInfoComputed = true;
                }
                double dx = chunkPos.x * 16 + 8 - ctx.spawnX;
                double dz = chunkPos.z * 16 + 8 - ctx.spawnZ;
                return dx * dx + dz * dz >= ctx.minDistSq;
            }
            return true;
        } catch (Exception e) {
            return true;
        }
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
        int minCellX = Math.floorDiv(centerChunkX - VILLAGE_SEARCH_RADIUS_CHUNKS, VILLAGE_DISTANCE);
        int maxCellX = Math.floorDiv(centerChunkX + VILLAGE_SEARCH_RADIUS_CHUNKS, VILLAGE_DISTANCE);
        int minCellZ = Math.floorDiv(centerChunkZ - VILLAGE_SEARCH_RADIUS_CHUNKS, VILLAGE_DISTANCE);
        int maxCellZ = Math.floorDiv(centerChunkZ + VILLAGE_SEARCH_RADIUS_CHUNKS, VILLAGE_DISTANCE);

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
     * @param ctx          Per-search context (weight/memo caches)
     * @return Validated center position with real Y, or null if not found / rejected
     */
    @Nullable
    private static BlockPos searchInChunk(WorldServer worldServer, String structureId,
            Object structure, BlockPos origin, ChunkPos chunkPos, ScanContext ctx) {

        long worldSeed = worldServer.getSeed();

        try {
            if (!mayGenerateNaturallyCached(worldServer, chunkPos, ctx)) return null;

            // --- Ground-truth filter for already-generated chunks ---
            // If RC's persisted ledger marks a GENERATED chunk as processed, RC has
            // already made its final decision — simulation is pointless (and wrong:
            // the real decision depended on cross-chunk state we cannot reproduce).
            // Entry present → predict from the REAL bounding box; absent → skip.
            // <p>
            // Applies to loaded chunks AND to on-disk-but-unloaded chunks (probed
            // via ChunkProviderServer.isChunkGeneratedAt — a cached region-file
            // header check, no chunk loading). Simulation only ever runs on chunks
            // that have never been generated.
            Chunk loadedChunk = worldServer.getChunkProvider()
                    .getLoadedChunk(chunkPos.x, chunkPos.z);
            boolean realChunkLoaded = loadedChunk != null;
            boolean generatedOnDisk = false;
            if (!realChunkLoaded && worldServer.getChunkProvider() instanceof ChunkProviderServer) {
                generatedOnDisk = ((ChunkProviderServer) worldServer.getChunkProvider())
                        .isChunkGeneratedAt(chunkPos.x, chunkPos.z);
            }
            if ((realChunkLoaded || generatedOnDisk) && wsgdGetMethod != null) {
                Object ledger = wsgdGetMethod.invoke(null, worldServer);
                boolean chunkChecked = (boolean) wsgdIsChunkCheckedMethod.invoke(ledger, chunkPos);
                if (chunkChecked) {
                    for (Object entry : entriesIn(ledger, chunkPos)) {
                        String id = (String) wsgdGetStructureIDMethod.invoke(entry);
                        if (id.equalsIgnoreCase(structureId)) {
                            Object bb = entryGetBoundingBoxMethod.invoke(entry);
                            BlockPos center = new BlockPos(
                                    (sbbMinXField.getInt(bb) + sbbMaxXField.getInt(bb)) / 2,
                                    (sbbMinYField.getInt(bb) + sbbMaxYField.getInt(bb)) / 2,
                                    (sbbMinZField.getInt(bb) + sbbMaxZField.getInt(bb)) / 2);
                            SimpleStructureScanner.LOGGER.debug(
                                    "LEDGER_CONFIRMED '{}' at chunk({},{}) — real BB center={} (ground truth, no simulation)",
                                    structureId, chunkPos.x, chunkPos.z, center);
                            return center;
                        }
                    }
                    SimpleStructureScanner.LOGGER.debug(
                            "SKIP_{} '{}' at chunk({},{}) — chunk generated + RC processed, no ledger entry (RC decided not to generate)",
                            realChunkLoaded ? "LOADED_CHUNK" : "DISK_CHUNK",
                            structureId, chunkPos.x, chunkPos.z);
                    return null;
                }
                // Generated but not checked by RC:
                if (realChunkLoaded) {
                    // Loaded: if fully populated, RC's decorate can never run there
                    // (population fires exactly once; re-populate only re-runs
                    // generateStructures). Simulating would predict structures that
                    // can never appear — guaranteed false positives. Unpopulated +
                    // unchecked (complementation pending) falls through: population
                    // is yet to happen.
                    if (loadedChunk.isTerrainPopulated()) {
                        SimpleStructureScanner.LOGGER.debug(
                                "SKIP_POPULATED_UNCHECKED '{}' at chunk({},{}) — chunk populated but RC never processed it; RC cannot generate there",
                                structureId, chunkPos.x, chunkPos.z);
                        return null;
                    }
                    // Loaded, unchecked, unpopulated — fall through to simulation.
                } else {
                    // On disk but unchecked: population already happened (or will
                    // complete on load) without RC recording a check — either way RC's
                    // decorate will never (re)run in a way simulation could predict
                    // better than the ledger. Conservative skip trades a rare false
                    // negative for eliminating guaranteed false positives.
                    SimpleStructureScanner.LOGGER.debug(
                            "SKIP_DISK_UNCHECKED '{}' at chunk({},{}) — chunk generated on disk, RC unchecked; skipping conservatively",
                            structureId, chunkPos.x, chunkPos.z);
                    return null;
                }
            }

            // --- Biome weight pre-filter ---
            // RC's StructureSelector admits a NaturalGeneration entry into its
            // selection multimap only when getGenerationWeight(provider, biome)
            // * tweakedSpawnRate(id) > 0 (bytecode-verified). Weight depends only
            // on (dimension, biome), so it is memoized per biome per search.
            // This is the same chunk-center (8,0,8) biome lookup RC uses.
            if (ctx.naturalTypes != null) {
                Biome chunkBiome = biomeAt(worldServer, chunkPos);
                Double weight = ctx.weightByBiome.get(chunkBiome);
                if (weight == null) {
                    weight = maxNaturalWeight(ctx, worldServer.provider, chunkBiome);
                    ctx.weightByBiome.put(chunkBiome, weight);
                }
                if (weight <= 0) return null;
            }

            if (!RCVRandomCache.has(worldSeed, chunkPos.x, chunkPos.z)) {
                captureRandomViaEvent(worldServer, chunkPos, worldSeed);
                if (!RCVRandomCache.has(worldSeed, chunkPos.x, chunkPos.z)) return null;
            }

            Random random = (Random) invoke2(mhPopulationRandom, diagPopulationRandomMethod, null, worldSeed, chunkPos);

            List<?> statics = (List<?>) invoke2(mhStaticCandidates, diagStaticCandidatesMethod, null, worldServer, chunkPos);
            invoke2(mhSeedCandidates, diagSeedCandidatesMethod, null, statics, random);

            List<?> candidates = (List<?>) invoke3(mhNaturalCandidates, diagNaturalCandidatesMethod, null, worldServer, chunkPos, random);

            if (candidates.isEmpty()) return null;

            for (Object candidate : candidates) {
                long seed = random.nextLong();
                Object structObj = invoke1(mhPairGetLeft, pairGetLeftMethod, candidate, candidate);
                if (structObj == structure) {
                    Object generation = invoke1(mhPairGetRight, pairGetRightMethod, candidate, candidate);
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
                        SimpleStructureScanner.LOGGER.warn(
                                "Validation threw for '{}' at chunk ({},{}): {}: {}",
                                structureId, chunkPos.x, chunkPos.z, e.getClass().getSimpleName(), e.getMessage());
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
     * Bytecode analysis proved RC's handler passes the EVENT's Random directly to
     * WorldGenStructures.decorate — and that Random is shared across all Pre
     * handlers in registration order. Mods registered before RC (CoFHWorld,
     * NuclearCraft, ...) consume rand calls, so RC receives it in an ADVANCED
     * state. Invoking RC's handler directly with a pristine formula rand caused
     * seed divergence and false positives (8/8 failed verifications had
     * seedMatch=FALSE).
     * <p>
     * Therefore: dispatch the event to the REAL Forge event bus listeners in
     * registration order (validation world + formula rand, predicting=true), but
     * only up to and including RC's handler — see
     * {@link #dispatchUntilRcCaptured(PopulateChunkEvent.Pre)}.
     * Handlers registered before RC consume the validation rand in true
     * registration order, exactly as in real generation. When the dispatch
     * reaches RC's handler, MixinRCForgeEventHandler captures the
     * post-consumption seed (what RC actually receives) into RCVRandomCache and
     * cancels RC's body.
     * <p>
     * If a listener throws on the validation world, event dispatch is disabled
     * for the rest of the search session (logged once) and affected chunks
     * yield no prediction. There is deliberately NO fallback to direct RC
     * handler invocation: a direct invocation with the pristine formula rand
     * produces wrong seeds (see above), so skipping is strictly safer.
     * <p>
     * Note: if a pre-RC handler CANCELS the event, RC's mixin never fires and no
     * seed is captured — the chunk yields no prediction. This is correct: in real
     * generation, a cancelling handler also prevents RC from generating there.
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
                if (!busPostBroken) {
                    try {
                        dispatchUntilRcCaptured(event);
                    } catch (Throwable t) {
                        busPostBroken = true;
                        StackTraceElement[] st = t.getStackTrace();
                        SimpleStructureScanner.LOGGER.warn(
                                "Event-bus capture failed for chunk ({},{}): {} at {} — "
                                        + "disabling capture for all future chunks (affected chunks yield no prediction)",
                                chunkPos.x, chunkPos.z, t.getClass().getSimpleName(),
                                st.length > 0 ? st[0] : "?");
                    }
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
     * Dispatches a PopulateChunkEvent.Pre to the Forge event bus's registered
     * listeners manually, replicating {@code EventBus.post()} exactly (same
     * priority-ordered listener array, same per-listener invocation, exceptions
     * propagate) — but STOPS as soon as RC's handler has been reached and its
     * seed captured, signalled via
     * {@link RCVPredictionContext#wasCapturedThisPost()}.
     * <p>
     * Handlers registered after RC never run. They cannot influence the Random
     * state RC receives (RC's mixin cancels its body immediately after capture),
     * so skipping them is behavior-identical for prediction while avoiding
     * their cost — some, like Battle Towers, generate whole structures into
     * the validation world.
     * <p>
     * Additionally, pre-RC listeners proven rand-independent and
     * scan-unreachable (see {@link #RAND_INDEPENDENT_LISTENER_PREFIXES}) are
     * skipped outright — their invocation is pure cost with no effect on the
     * captured seed or the validation terrain.
     * <p>
     * If RC's handler is not registered on the bus, the full array is walked,
     * matching {@code EventBus.post()} behavior.
     */
    private static void dispatchUntilRcCaptured(PopulateChunkEvent.Pre event) {
        if (forgeBusId < 0) {
            MinecraftForge.EVENT_BUS.post(event);
            return;
        }

        IEventListener[] listeners = event.getListenerList().getListeners(forgeBusId);
        RCVPredictionContext.resetCaptureSignal();
        for (IEventListener listener : listeners) {
            if (isRandIndependentSkippable(listener.toString())) continue;
            listener.invoke(event);
            if (RCVPredictionContext.wasCapturedThisPost()) break;
        }
    }

    /** True if the listener is on the proven-rand-independent skip list. */
    private static boolean isRandIndependentSkippable(String listenerName) {
        for (String prefix : RAND_INDEPENDENT_LISTENER_PREFIXES) {
            if (listenerName.startsWith(prefix)) return true;
        }
        return false;
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
     * Validates a predicted candidate by running RC's test() on validation world terrain.
     * Returns null if test() fails (placement, outOfBounds) or the biome-edge weight is 0.
     * Overlap check is disabled via allowOverlaps(true) since the validation world
     * has no structure data.
     * <p>
     * Two-stage check for speed: first {@code test()} runs against RAW generated
     * terrain (no decoration) — a separate generator instance so no state leaks
     * into the populated check. Only candidates that pass raw placement pay for
     * the full decoration pass, after which {@code test()} runs again exactly as
     * before. For surface-placed structures this is monotone (decoration adds
     * foliage/liquids that the placer rays either exclude or that cannot rescue
     * a failed conformity check), so raw-fail implies populated-fail; exotic
     * custom placers could theoretically violate this, hence the populated
     * re-check still runs on every raw pass.
     *
     * @param validationWorld The validation world (StructureValidationWorld)
     * @param structure       The Structure object from the registry
     * @param generation      The NaturalGeneration from the candidate pair
     * @param structureId     The structure ID string
     * @param seed            The candidate seed
     * @param chunkPos        The chunk position
     * @return Validated center position with real Y, or null if rejected
     * @throws Exception if the validation itself fails (caller should fall back to unvalidated)
     */
    @Nullable
    private static BlockPos validateWithPlacer(Object validationWorld, Object structure,
            Object generation, String structureId, long seed, ChunkPos chunkPos) throws Exception {

        BlockPos surfaceBlockPos = computeSurfacePos(chunkPos, seed);

        Object generator = sgConstructor.newInstance(structure);
        setupGenerator(generator, generation, structureId, seed, surfaceBlockPos);

        // --- Raw-terrain pre-screen ---
        // Generate (undecorated) the BB chunks and run test() against raw terrain.
        // Fail-open: any exception here proceeds to the full populated validation.
        if (sgStructureSizeMethod != null && validationWorld instanceof StructureValidationWorld) {
            try {
                int[] size = (int[]) sgStructureSizeMethod.invoke(generator);
                int bbMinX = surfaceBlockPos.getX() - size[0] / 2;
                int bbMinZ = surfaceBlockPos.getZ() - size[2] / 2;
                int bbMaxX = bbMinX + size[0];
                int bbMaxZ = bbMinZ + size[2];

                StructureValidationWorld svw = (StructureValidationWorld) validationWorld;
                svw.provideChunkRange(bbMinX, bbMinZ, bbMaxX, bbMaxZ);

                Object rawGenerator = sgConstructor.newInstance(structure);
                setupGenerator(rawGenerator, generation, structureId, seed, surfaceBlockPos);
                unsafeInstance.putObject(rawGenerator, sgWorldFieldOffset, validationWorld);
                sgAllowOverlapsMethod.invoke(rawGenerator, true);

                Object rawResult = sgTestMethod.invoke(rawGenerator);
                boolean rawOk = rawResult != null && (boolean) grSucceededMethod.invoke(rawResult);
                if (!rawOk) {
                    SimpleStructureScanner.LOGGER.debug(
                            "Raw pre-screen rejected '{}' at chunk ({},{}) — failure: [{}]",
                            structureId, chunkPos.x, chunkPos.z,
                            rawResult != null ? extractFailureDescription(rawResult) : "null-result");
                    return null;
                }
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.debug(
                        "Raw pre-screen skipped for '{}' at chunk({},{}) — {}: {}",
                        structureId, chunkPos.x, chunkPos.z, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        // --- Populate chunks overlapping the structure BB on the validation world ---
        // Compute preliminary BB to determine which chunks need population.
        // structureSize() doesn't need the world (only seed+transform).
        if (sgStructureSizeMethod != null && validationWorld instanceof StructureValidationWorld) {
            try {
                int[] size = (int[]) sgStructureSizeMethod.invoke(generator);
                int centerX = surfaceBlockPos.getX();
                int centerZ = surfaceBlockPos.getZ();
                // fromCenter=true: BB starts at center - size/2
                int bbMinX = centerX - size[0] / 2;
                int bbMinZ = centerZ - size[2] / 2;
                int bbMaxX = bbMinX + size[0];
                int bbMaxZ = bbMinZ + size[2];

                StructureValidationWorld svw = (StructureValidationWorld) validationWorld;
                svw.populateChunkRange(bbMinX, bbMinZ, bbMaxX, bbMaxZ);
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.debug(
                        "Populate skipped for '{}' at chunk({},{}) — {}: {}",
                        structureId, chunkPos.x, chunkPos.z, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        unsafeInstance.putObject(generator, sgWorldFieldOffset, validationWorld);
        sgAllowOverlapsMethod.invoke(generator, true);

        // Run RC's full test() validation (placement + spawn + outOfBounds), overlap disabled
        Object testResult = sgTestMethod.invoke(generator);
        if (testResult == null) return null;
        boolean succeeded = (boolean) grSucceededMethod.invoke(testResult);
        if (!succeeded) {
            SimpleStructureScanner.LOGGER.debug(
                    "test() rejected structure '{}' at chunk ({},{}) — failure: [{}]",
                    structureId, chunkPos.x, chunkPos.z, extractFailureDescription(testResult));
            return null;
        }

        // test() succeeded — boundingBox() is cached and guaranteed non-empty
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

        // Biome-edge re-check: reject if the biome at the BB center has generation weight 0
        Object env = sgEnvironmentMethod.invoke(generator);
        Biome bbBiome = (Biome) envBiomeField.get(env);
        if (bbBiome != null) {
            WorldProvider provider = ((World) validationWorld).provider;
            double weight = (double) ngGetGenerationWeightMethod.invoke(generation, provider, bbBiome);
            if (weight <= 0) {
                SimpleStructureScanner.LOGGER.debug(
                        "Biome-edge rejected '{}' at chunk({},{}) — biome={}, weight={}",
                        structureId, chunkPos.x, chunkPos.z, bbBiome.getRegistryName(), weight);
                return null;
            }
        }

        return new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    /**
     * Configures a StructureGenerator with the scan-time parameters: generation
     * info, seed, structure ID, maturity, random surface position (from the
     * structure's placer), and fromCenter=true.
     *
     * @param surfaceBlockPos The precomputed surface position (computeSurfacePos(chunkPos, seed))
     */
    private static void setupGenerator(Object generator, Object generation,
            String structureId, long seed, BlockPos surfaceBlockPos) throws Exception {
        sgGenerationInfoMethod.invoke(generator, generation);
        sgSeedMethod.invoke(generator, (Long) seed);
        sgStructureIDMethod.invoke(generator, structureId);
        sgMaturityMethod.invoke(generator, generateMaturitySuggest);

        Object blockSurfacePos = blockSurfacePosFromMethod.invoke(null, surfaceBlockPos);
        Object placer = placerMethod.invoke(generation);
        sgRandomPositionMethod.invoke(generator, blockSurfacePos, placer);
        sgFromCenterMethod.invoke(generator, true);
    }

    @Nullable
    private static String extractFailureDescription(Object testResult) {
        if (failureDescriptionField != null) {
            try {
                Object desc = failureDescriptionField.get(testResult);
                return desc != null ? desc.toString() : "null";
            } catch (Exception ignored) {
            }
        }
        return testResult.toString();
    }

    // ========== RC generation ledger access ==========
    //
    // WorldStructureGenerationData is RC's persisted record of which chunks its
    // pipeline processed and which structures it actually generated in them.
    // The ground-truth filter in searchInChunk() consults it to skip simulation
    // for chunks whose outcome RC has already decided.

    /** Lists RC ledger entry objects for the given chunk. */
    private static List<Object> entriesIn(Object data, ChunkPos chunkPos) throws Exception {
        List<Object> out = new ArrayList<>();
        Object stream = wsgdStructureEntriesInMethod.invoke(data, chunkPos);
        if (stream == null) return out;
        java.util.Iterator<?> it = (java.util.Iterator<?>) streamIteratorMethod.invoke(stream);
        while (it.hasNext()) {
            out.add(it.next());
        }
        return out;
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

            // MethodHandle fast paths for the per-chunk hot call sites
            mhPopulationRandom = unreflectOrNull(diagPopulationRandomMethod);
            mhStaticCandidates = unreflectOrNull(diagStaticCandidatesMethod);
            mhSeedCandidates = unreflectOrNull(diagSeedCandidatesMethod);
            mhNaturalCandidates = unreflectOrNull(diagNaturalCandidatesMethod);
            mhPairGetLeft = unreflectOrNull(pairGetLeftMethod);
            mhPairGetRight = unreflectOrNull(pairGetRightMethod);

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
            sgBoundingBoxMethod = structureGeneratorClass.getMethod("boundingBox");
            sgStructureSizeMethod = structureGeneratorClass.getMethod("structureSize");
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

            // test() + allowOverlaps + environment on StructureGenerator
            sgTestMethod = structureGeneratorClass.getDeclaredMethod("test");
            sgAllowOverlapsMethod = structureGeneratorClass.getDeclaredMethod("allowOverlaps", boolean.class);
            sgEnvironmentMethod = structureGeneratorClass.getDeclaredMethod("environment");

            // GenerationResult.succeeded()
            Class<?> generationResultClass = Class.forName(
                "ivorius.reccomplex.world.gen.feature.StructureGenerator$GenerationResult");
            grSucceededMethod = generationResultClass.getDeclaredMethod("succeeded");

            // Failure.description (public field on the Failure subclass)
            try {
                Class<?> failureClass = Class.forName(
                    "ivorius.reccomplex.world.gen.feature.StructureGenerator$GenerationResult$Failure");
                failureDescriptionField = failureClass.getField("description");
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.warn("Could not access Failure.description field", e);
            }

            // Environment.biome (public field)
            Class<?> environmentClass = Class.forName(
                "ivorius.reccomplex.world.gen.feature.structure.Environment");
            envBiomeField = environmentClass.getField("biome");

            // NaturalGeneration.getGenerationWeight(WorldProvider, Biome) → double
            naturalGenerationClass = Class.forName(
                "ivorius.reccomplex.world.gen.feature.structure.generic.generation.NaturalGeneration");
            ngGetGenerationWeightMethod = naturalGenerationClass.getDeclaredMethod(
                "getGenerationWeight", WorldProvider.class, Biome.class);

            SimpleStructureScanner.LOGGER.info("Recurrent Complex search initialized");

            // Cache RC's Forge event handler instance + method for direct invocation.
            // This bypasses MinecraftForge.EVENT_BUS.post(), which would iterate ALL
            // registered handlers for PopulateChunkEvent.Pre across 423 mods.
            // RCConfig.tweakedSpawnRate(String) — per-structure multiplier RC applies
            // to natural generation weights. Missing method = filter ignores tweaks.
            try {
                Class<?> rcConfigClass = Class.forName("ivorius.reccomplex.RCConfig");
                rcTweakedSpawnRateMethod = rcConfigClass.getMethod("tweakedSpawnRate", String.class);
                // Memoized mayGenerateNaturally support
                rcGenEnabledBiomeMethod = rcConfigClass.getMethod("isGenerationEnabled", Biome.class);
                rcGenEnabledProviderMethod = rcConfigClass.getMethod("isGenerationEnabled", WorldProvider.class);
                rcMinDistToSpawnField = rcConfigClass.getField("minDistToSpawnForGeneration");
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.debug("RCConfig reflection incomplete — weight tweaks / memoized mayGen unavailable", e);
            }

            // EventBus.busID — needed to fetch the Forge bus's listener array for
            // partial dispatch. Missing = fall back to full event posts.
            try {
                eventBusBusIdField = EventBus.class.getDeclaredField("busID");
                eventBusBusIdField.setAccessible(true);
                forgeBusId = eventBusBusIdField.getInt(MinecraftForge.EVENT_BUS);
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.debug("EventBus.busID unavailable — using full event posts", e);
            }

            // ========== Generation ledger reflection (ground-truth filter) ==========
            try {
                Class<?> wsgdClass = Class.forName("ivorius.reccomplex.world.gen.feature.WorldStructureGenerationData");
                wsgdGetMethod = wsgdClass.getMethod("get", World.class);
                wsgdIsChunkCheckedMethod = wsgdClass.getMethod("isChunkChecked", ChunkPos.class);
                wsgdStructureEntriesInMethod = wsgdClass.getMethod("structureEntriesIn", ChunkPos.class);
                Class<?> structureEntryClass = Class.forName(
                        "ivorius.reccomplex.world.gen.feature.WorldStructureGenerationData$StructureEntry");
                wsgdGetStructureIDMethod = structureEntryClass.getMethod("getStructureID");
                Class<?> entryClass = Class.forName(
                        "ivorius.reccomplex.world.gen.feature.WorldStructureGenerationData$Entry");
                entryGetBoundingBoxMethod = entryClass.getMethod("getBoundingBox");
                streamIteratorMethod = Class.forName("java.util.stream.BaseStream").getMethod("iterator");
                SimpleStructureScanner.LOGGER.info("Recurrent Complex generation ledger reflection initialized");
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.warn("Generation ledger reflection init failed — ground-truth filter disabled", e);
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
