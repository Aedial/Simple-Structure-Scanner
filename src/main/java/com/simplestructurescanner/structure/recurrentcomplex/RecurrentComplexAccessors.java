package com.simplestructurescanner.structure.recurrentcomplex;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;

import sun.misc.Unsafe;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.IEventListener;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.rcv.RCVPredictionContext;
import com.simplestructurescanner.structure.util.ReflectionHelper;
import com.simplestructurescanner.structure.validation.StructureValidationWorld;


/**
 * Reflectively accesses the Recurrent Complex members used by the searcher.
 * The mod is not used directly to not fail directly if the internal API changes.
 * This allows some flexibility in handling changes.
 */
@SuppressWarnings("unchecked")
final class RecurrentComplexAccessors {

    // ========== Recurrent Complex class names ==========

    private static final String STRUCTURE_LOCATOR_CLASS =
            "ivorius.reccomplex.world.gen.feature.StructureLocator";
    private static final String STRUCTURE_REGISTRY_CLASS =
            "ivorius.reccomplex.world.gen.feature.structure.StructureRegistry";
    private static final String RC_STRUCTURE_CLASS =
            "ivorius.reccomplex.world.gen.feature.structure.Structure";
    private static final String GENERIC_VILLAGE_PIECE_CLASS =
            "ivorius.reccomplex.world.gen.feature.villages.GenericVillagePiece";
    private static final String STRUCTURE_GENERATOR_CLASS =
            "ivorius.reccomplex.world.gen.feature.StructureGenerator";
    private static final String GENERATION_TYPE_CLASS =
            "ivorius.reccomplex.world.gen.feature.structure.generic.generation.GenerationType";
    private static final String PLACER_CLASS =
            "ivorius.reccomplex.world.gen.feature.structure.Placer";
    private static final String BLOCK_SURFACE_POS_CLASS =
            "ivorius.ivtoolkit.blocks.BlockSurfacePos";
    private static final String GENERATE_MATURITY_CLASS =
            "ivorius.reccomplex.world.gen.feature.structure.context.StructureSpawnContext$GenerateMaturity";
    private static final String GENERATION_RESULT_CLASS =
            "ivorius.reccomplex.world.gen.feature.StructureGenerator$GenerationResult";
    private static final String GENERATION_FAILURE_CLASS =
            "ivorius.reccomplex.world.gen.feature.StructureGenerator$GenerationResult$Failure";
    private static final String ENVIRONMENT_CLASS =
            "ivorius.reccomplex.world.gen.feature.structure.Environment";
    private static final String NATURAL_GENERATION_CLASS =
            "ivorius.reccomplex.world.gen.feature.structure.generic.generation.NaturalGeneration";
    private static final String VANILLA_GENERATION_CLASS =
            "ivorius.reccomplex.world.gen.feature.structure.generic.generation.VanillaGeneration";
    private static final String VANILLA_DECORATION_GENERATION_CLASS =
            "ivorius.reccomplex.world.gen.feature.structure.generic.generation.VanillaDecorationGeneration";
    private static final String RC_CONFIG_CLASS = "ivorius.reccomplex.RCConfig";
    private static final String WORLD_STRUCTURE_GENERATION_DATA_CLASS =
            "ivorius.reccomplex.world.gen.feature.WorldStructureGenerationData";
    private static final String STRUCTURE_ENTRY_CLASS = WORLD_STRUCTURE_GENERATION_DATA_CLASS + "$StructureEntry";
    private static final String ENTRY_CLASS = WORLD_STRUCTURE_GENERATION_DATA_CLASS + "$Entry";

    // ========== Search reflection access ==========

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
    @Nullable
    private static Method structureGenerationTypesMethod;

    // ========== Candidate-selection reflection access ==========

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

    // Method handles for per-chunk reflection calls
    @Nullable
    private static MethodHandle mhPopulationRandom;
    @Nullable
    private static MethodHandle mhStaticCandidates;
    @Nullable
    private static MethodHandle mhSeedCandidates;
    @Nullable
    private static MethodHandle mhNaturalCandidates;

    // ========== Placement-validation reflection access ==========

    @Nullable
    private static Constructor<?> sgConstructor;
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
    @Nullable
    private static Unsafe unsafeInstance;
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
    private static Object generateMaturitySuggest;

    // ========== Natural-generation reflection access ==========

    // Natural-generation weight access
    @Nullable
    private static Class<?> naturalGenerationClass;
    @Nullable
    private static Class<?> vanillaGenerationClass;
    @Nullable
    private static Method ngGetGenerationWeightMethod;
    @Nullable
    private static Method rcTweakedSpawnRateMethod;

    // Cached Recurrent Complex generation settings
    @Nullable
    private static Method rcGenEnabledBiomeMethod;
    @Nullable
    private static Method rcGenEnabledProviderMethod;
    @Nullable
    private static Field rcMinDistToSpawnField;

    // ========== Persisted-generation reflection access ==========

    // WorldStructureGenerationData records processed chunks and generated structures
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

    // ========== Event-dispatch reflection access ==========

    @Nullable
    private static Field eventBusBusIdField;
    private static int forgeBusId = -1;

    // ========== Village-search reflection access ==========

    @Nullable
    private static Class<?> genericVillagePieceClass;
    @Nullable
    private static Field gvpStructureIDField;

    // ========== Initialization state ==========

    private static boolean initializationAttempted = false;
    private static boolean initialized = false;
    private static boolean villageInitializationAttempted = false;

    // ========== Event-listener exclusions ==========

    /**
     * Listener prefixes skipped while posting {@code PopulateChunkEvent.Pre} for prediction.
     * A listed listener must neither consume the event {@link Random} nor change
     * terrain in a scanned chunk. Each prefix is tied to an exact mod version:
     * <ul>
     *   <li>{@code AbyssalCraftEventHooks} (AbyssalCraft 1.12.2-1.11.3):
     *       {@code populateChunk} does not read the event Random and only changes
     *       the Darklands Mountains biome, which the scanned dimensions cannot use.</li>
     * </ul>
     * FIXME: This shit is not proper coding. Need at least to be configurable and
     *        maybe a versions -> prefixes mapping
     */
    private static final String[] RAND_INDEPENDENT_LISTENER_PREFIXES = {
        "ASM: com.shinoow.abyssalcraft.common.handlers.AbyssalCraftEventHooks",
    };

    private RecurrentComplexAccessors() {
    }

    // ========== Typed reflection values ==========

    // Abstractions for typed access to Recurrent Complex structures and generations
    static final class RcStructure {

        private final Object value;

        private RcStructure(Object value) {
            this.value = value;
        }

        Object value() {
            return value;
        }
    }

    static final class RcGeneration {

        private final Object value;

        private RcGeneration(Object value) {
            this.value = value;
        }

        Object value() {
            return value;
        }
    }

    static final class RcCandidate {

        private final RcStructure structure;
        private final RcGeneration generation;

        private RcCandidate(RcStructure structure, RcGeneration generation) {
            this.structure = structure;
            this.generation = generation;
        }

        RcGeneration generation() {
            return generation;
        }

        Object structureValue() {
            return structure.value();
        }

        Object generationValue() {
            return generation.value();
        }
    }

    static final class LedgerChunk {

        private final boolean checked;
        @Nullable
        private final BlockPos structurePosition;

        private LedgerChunk(boolean checked, @Nullable BlockPos structurePosition) {
            this.checked = checked;
            this.structurePosition = structurePosition;
        }
    }

    // ========== Reflection initialization ==========

    static synchronized boolean isAvailable() {
        if (initialized) return true;
        if (initializationAttempted) return false;

        initializationAttempted = true;
        try {
            locatorClass = Class.forName(STRUCTURE_LOCATOR_CLASS);
            Class<?> registryClass = Class.forName(STRUCTURE_REGISTRY_CLASS);
            Class<?> structureClass = Class.forName(RC_STRUCTURE_CLASS);

            chunksByDistanceMethod = locatorClass.getMethod("chunksByDistance", BlockPos.class, int.class);

            structureRegistryInstance = registryClass.getField("INSTANCE").get(null);
            registryGetMethod = registryClass.getMethod("get", String.class);
            registryActiveIDsMethod = registryClass.getMethod("activeIDs");
            structureGenerationTypesMethod = structureClass.getMethod("generationTypes", Class.class);

            diagPopulationRandomMethod = locatorClass.getMethod("populationRandom", long.class, ChunkPos.class);
            diagStaticCandidatesMethod = ReflectionHelper.getAccessibleDeclaredMethod(
                locatorClass, "staticCandidatesInChunk", WorldServer.class, ChunkPos.class);
            diagSeedCandidatesMethod = locatorClass.getMethod("seedCandidates", Collection.class, Random.class);
            diagNaturalCandidatesMethod = ReflectionHelper.getAccessibleDeclaredMethod(
                locatorClass, "naturalCandidatesInChunk", WorldServer.class, ChunkPos.class, Random.class);
            diagMayGenerateMethod = ReflectionHelper.getAccessibleDeclaredMethod(
                locatorClass, "mayGenerateNaturally", WorldServer.class, ChunkPos.class);

            mhPopulationRandom = ReflectionHelper.unreflectOrNull(diagPopulationRandomMethod);
            mhStaticCandidates = ReflectionHelper.unreflectOrNull(diagStaticCandidatesMethod);
            mhSeedCandidates = ReflectionHelper.unreflectOrNull(diagSeedCandidatesMethod);
            mhNaturalCandidates = ReflectionHelper.unreflectOrNull(diagNaturalCandidatesMethod);

            initializeGeneratorAccess(structureClass);
            initializeNaturalGenerationAccess();
            initializeEventBusAccess();
            initializeLedgerAccess();
            warnVanillaDecorationGeneration(registryClass);

            initialized = true;
            SimpleStructureScanner.LOGGER.info("Initialized Recurrent Complex search");
            return true;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Could not initialize Recurrent Complex search", e);
            return false;
        }
    }

    // ========== Structure lookup ==========

    @Nullable
    static RcStructure findStructure(String structureId) {
        if (structureRegistryInstance == null || registryGetMethod == null) return null;

        try {
            Object result = registryGetMethod.invoke(structureRegistryInstance, structureId);
            if (result != null) return new RcStructure(result);

            if (registryActiveIDsMethod != null) {
                Set<String> activeIds = (Set<String>) registryActiveIDsMethod.invoke(structureRegistryInstance);
                for (String id : activeIds) {
                    if (id.equalsIgnoreCase(structureId)) {
                        return new RcStructure(registryGetMethod.invoke(structureRegistryInstance, id));
                    }
                }
            }
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug("Could not resolve Recurrent Complex structure '{}'", structureId, e);
        }

        return null;
    }

    // ========== Search planning ==========

    @Nullable
    static List<ChunkPos> chunksByDistance(BlockPos origin, int radius) {
        if (chunksByDistanceMethod == null) return null;

        try {
            List<ChunkPos> chunks = (List<ChunkPos>) chunksByDistanceMethod.invoke(null, origin, radius);
            return chunks;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug("Could not list chunks by distance", e);
            return null;
        }
    }

    // ========== Natural-generation lookup and filtering ==========

    @Nullable
    static List<RcGeneration> naturalGenerationTypes(RcStructure structure) {
        if (naturalGenerationClass == null || structureGenerationTypesMethod == null) return null;

        try {
            List<?> types = (List<?>) structureGenerationTypesMethod.invoke(structure.value, naturalGenerationClass);
            List<RcGeneration> generations = new ArrayList<>();
            if (types != null) {
                for (Object type : types) generations.add(new RcGeneration(type));
            }

            return generations;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug(
                "Could not read NaturalGeneration entries; skipping the biome-weight filter", e);
            return null;
        }
    }

    static double tweakedSpawnRate(String structureId) {
        if (rcTweakedSpawnRateMethod == null) return 1.0;

        try {
            return ((Number) rcTweakedSpawnRateMethod.invoke(null, structureId)).doubleValue();
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug("Could not read the Recurrent Complex spawn-rate setting", e);
            return 1.0;
        }
    }

    static double generationWeight(RcGeneration generation, WorldProvider provider, Biome biome) {
        if (ngGetGenerationWeightMethod == null) return Double.POSITIVE_INFINITY;

        try {
            return ((Number) ngGetGenerationWeightMethod.invoke(generation.value, provider, biome)).doubleValue();
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }
    }

    static boolean mayGenerateNaturally(WorldServer worldServer, ChunkPos chunkPos) {
        if (rcGenEnabledBiomeMethod == null || rcGenEnabledProviderMethod == null) {
            try {
                return diagMayGenerateMethod != null &&
                    (boolean) diagMayGenerateMethod.invoke(null, worldServer, chunkPos);
            } catch (Exception e) {
                return true;
            }
        }

        try {
            Biome biome = worldServer.getBiome(chunkPos.getBlock(8, 0, 8));
            if (!(boolean) rcGenEnabledBiomeMethod.invoke(null, biome)) return false;
            if (!(boolean) rcGenEnabledProviderMethod.invoke(null, worldServer.provider)) return false;

            if (worldServer.provider.getDimension() != 0 || rcMinDistToSpawnField == null) return true;

            float minDist = rcMinDistToSpawnField.getFloat(null);
            BlockPos spawn = worldServer.getSpawnPoint();
            double dx = chunkPos.x * 16 + 8 - spawn.getX();
            double dz = chunkPos.z * 16 + 8 - spawn.getZ();

            return dx * dx + dz * dz >= (double) minDist * minDist;
        } catch (Exception e) {
            return true;
        }
    }

    static boolean supportsMemoizedGenerationFilter() {
        return rcGenEnabledBiomeMethod != null && rcGenEnabledProviderMethod != null;
    }

    static boolean isGenerationEnabled(Biome biome) throws Exception {
        return (boolean) rcGenEnabledBiomeMethod.invoke(null, biome);
    }

    static boolean isGenerationEnabled(WorldProvider provider) throws Exception {
        return (boolean) rcGenEnabledProviderMethod.invoke(null, provider);
    }

    @Nullable
    static Float minDistanceToSpawn() throws Exception {
        return rcMinDistToSpawnField != null ? rcMinDistToSpawnField.getFloat(null) : null;
    }

    // ========== Candidate selection ==========

    static Random populationRandom(long worldSeed, ChunkPos chunkPos) throws Exception {
        return (Random) ReflectionHelper.invoke(mhPopulationRandom, diagPopulationRandomMethod,
            null, worldSeed, chunkPos);
    }

    static List<RcCandidate> naturalCandidates(WorldServer worldServer, ChunkPos chunkPos, Random random)
            throws Exception {

        List<?> statics = (List<?>) ReflectionHelper.invoke(mhStaticCandidates,
            diagStaticCandidatesMethod, null, worldServer, chunkPos);
        ReflectionHelper.invoke(mhSeedCandidates, diagSeedCandidatesMethod, null, statics, random);

        List<?> rawCandidates = (List<?>) ReflectionHelper.invoke(mhNaturalCandidates,
            diagNaturalCandidatesMethod, null, worldServer, chunkPos, random);

        List<RcCandidate> candidates = new ArrayList<>(rawCandidates.size());
        for (Object rawCandidate : rawCandidates) {
            Pair<?, ?> candidate = (Pair<?, ?>) rawCandidate;
            candidates.add(new RcCandidate(new RcStructure(candidate.getLeft()),
                                           new RcGeneration(candidate.getRight())));
        }

        return candidates;
    }

    // ========== Persisted generation results ==========

    static boolean hasLedgerAccess() {
        return wsgdGetMethod != null && wsgdIsChunkCheckedMethod != null &&
            wsgdStructureEntriesInMethod != null && wsgdGetStructureIDMethod != null &&
            entryGetBoundingBoxMethod != null;
    }

    /**
     * Reads Recurrent Complex's saved result for a processed chunk.
     * If the chunk has been processed and contains the specified structure, returns its saved bounding box center.
     */
    static LedgerChunk ledgerChunk(WorldServer worldServer, ChunkPos chunkPos, String structureId)
            throws Exception {

        Object ledger = wsgdGetMethod.invoke(null, worldServer);
        boolean checked = (boolean) wsgdIsChunkCheckedMethod.invoke(ledger, chunkPos);
        if (!checked) return new LedgerChunk(false, null);

        for (Object entry : entriesIn(ledger, chunkPos)) {
            String id = (String) wsgdGetStructureIDMethod.invoke(entry);
            if (id.equalsIgnoreCase(structureId)) {
                StructureBoundingBox boundingBox = (StructureBoundingBox) entryGetBoundingBoxMethod.invoke(entry);
                return new LedgerChunk(true, boundingBoxCenter(boundingBox));
            }
        }

        return new LedgerChunk(true, null);
    }

    static boolean isChecked(LedgerChunk ledgerChunk) {
        return ledgerChunk.checked;
    }

    @Nullable
    static BlockPos structurePosition(LedgerChunk ledgerChunk) {
        return ledgerChunk.structurePosition;
    }

    /**
     * Copies the saved structure entries for a chunk from Recurrent Complex's stream.
     */
    private static List<Object> entriesIn(Object data, ChunkPos chunkPos) throws Exception {
        List<Object> entries = new ArrayList<>();
        Object result = wsgdStructureEntriesInMethod.invoke(data, chunkPos);
        if (result == null) return entries;

        Iterator<?> iterator = ((Stream<?>) result).iterator();
        while (iterator.hasNext()) entries.add(iterator.next());

        return entries;
    }

    // ========== Village-piece lookup ==========

    static boolean hasVanillaGeneration(RcStructure structure) {
        if (vanillaGenerationClass == null || structureGenerationTypesMethod == null) return false;

        try {
            List<?> types = (List<?>) structureGenerationTypesMethod.invoke(structure.value, vanillaGenerationClass);
            return types != null && !types.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isVillageSearchAvailable() {
        if (!isAvailable()) return false;
        if (genericVillagePieceClass != null && gvpStructureIDField != null) return true;
        if (villageInitializationAttempted) return false;

        villageInitializationAttempted = true;
        try {
            genericVillagePieceClass = Class.forName(GENERIC_VILLAGE_PIECE_CLASS);
            gvpStructureIDField = genericVillagePieceClass.getField("structureID");
            return true;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Could not initialize Recurrent Complex village search", e);
            return false;
        }
    }

    static boolean isVillagePiece(StructureComponent component) {
        return genericVillagePieceClass != null && genericVillagePieceClass.isInstance(component);
    }

    @Nullable
    static String villagePieceStructureId(StructureComponent component) {
        if (gvpStructureIDField == null) return null;

        try {
            return (String) gvpStructureIDField.get(component);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug("Could not read the Recurrent Complex village piece structure ID", e);
            return null;
        }
    }

    // ========== Population-event dispatch ==========

    /**
     * Invokes listeners until the Recurrent Complex mixin captures the event random.
     * Falls back to a normal event post when EventBus internals are unavailable.
     */
    static void dispatchUntilRcCaptured(PopulateChunkEvent.Pre event) {
        if (forgeBusId < 0) {
            MinecraftForge.EVENT_BUS.post(event);
            return;
        }

        IEventListener[] listeners = event.getListenerList().getListeners(forgeBusId);
        RCVPredictionContext.resetCaptureSignal();
        for (IEventListener listener : listeners) {
            if (isRandIndependentSkippable(listener.toString())) continue;

            listener.invoke(event);
            if (RCVPredictionContext.wasCapturedThisPost()) return;
        }
    }

    private static boolean isRandIndependentSkippable(String listenerName) {
        for (String prefix : RAND_INDEPENDENT_LISTENER_PREFIXES) {
            if (listenerName.startsWith(prefix)) return true;
        }

        return false;
    }

    // ========== Placement validation ==========

    /**
     * Tests a candidate on generated terrain, then on decorated terrain.
     * <p>
     * The generated-terrain test avoids decoration work for placements the
     * Recurrent Complex placer rejects, to avoid unnecessary decoration work.
     */
    @Nullable
    static BlockPos validatePlacement(World validationWorld, RcStructure structure,
            RcGeneration generation, String structureId, long seed, ChunkPos chunkPos) throws Exception {

        if (sgConstructor == null || unsafeInstance == null) {
            throw new IllegalStateException(
                "Cannot validate Recurrent Complex placement: generator access is unavailable");
        }

        BlockPos surfaceBlockPos = computeSurfacePos(chunkPos, seed);
        Object generator = sgConstructor.newInstance(structure.value);
        setupGenerator(generator, generation, structureId, seed, surfaceBlockPos);

        // Generate the rotated structure footprint before the placer reads terrain
        // test() caches the placed bounding box, so the decorated pass needs a second generator
        if (sgStructureSizeMethod != null && validationWorld instanceof StructureValidationWorld) {
            try {
                int[] size = (int[]) sgStructureSizeMethod.invoke(generator);
                int bbMinX = surfaceBlockPos.getX() - size[0] / 2;
                int bbMinZ = surfaceBlockPos.getZ() - size[2] / 2;
                int bbMaxX = bbMinX + size[0];
                int bbMaxZ = bbMinZ + size[2];

                StructureValidationWorld svw = (StructureValidationWorld) validationWorld;
                svw.provideChunkRange(bbMinX, bbMinZ, bbMaxX, bbMaxZ);

                Object rawGenerator = sgConstructor.newInstance(structure.value);
                setupGenerator(rawGenerator, generation, structureId, seed, surfaceBlockPos);
                setValidationWorld(rawGenerator, validationWorld);
                sgAllowOverlapsMethod.invoke(rawGenerator, true);

                Object rawResult = sgTestMethod.invoke(rawGenerator);
                if (rawResult == null || !(boolean) grSucceededMethod.invoke(rawResult)) {
                    SimpleStructureScanner.LOGGER.debug(
                        "Generated-terrain placement check rejected '{}' in chunk ({},{}): {}",
                        structureId, chunkPos.x, chunkPos.z,
                        rawResult != null ? extractFailureDescription(rawResult) : "null-result");
                    return null;
                }
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.debug(
                    "Could not check '{}' on generated terrain in chunk ({},{}): {}: {}",
                    structureId, chunkPos.x, chunkPos.z, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        // Decorate the same footprint before the final test so placers read populated terrain
        if (sgStructureSizeMethod != null && validationWorld instanceof StructureValidationWorld) {
            try {
                int[] size = (int[]) sgStructureSizeMethod.invoke(generator);
                int bbMinX = surfaceBlockPos.getX() - size[0] / 2;
                int bbMinZ = surfaceBlockPos.getZ() - size[2] / 2;
                int bbMaxX = bbMinX + size[0];
                int bbMaxZ = bbMinZ + size[2];

                StructureValidationWorld svw = (StructureValidationWorld) validationWorld;
                svw.populateChunkRange(bbMinX, bbMinZ, bbMaxX, bbMaxZ);
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.debug(
                    "Could not decorate terrain for '{}' in chunk ({},{}): {}: {}",
                    structureId, chunkPos.x, chunkPos.z, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        setValidationWorld(generator, validationWorld);
        sgAllowOverlapsMethod.invoke(generator, true);

        Object testResult = sgTestMethod.invoke(generator);
        if (testResult == null || !(boolean) grSucceededMethod.invoke(testResult)) {
            if (testResult != null) {
                SimpleStructureScanner.LOGGER.debug(
                    "Placement check rejected '{}' in chunk ({},{}): {}",
                    structureId, chunkPos.x, chunkPos.z, extractFailureDescription(testResult));
            }
            return null;
        }

        Optional<?> boundingBoxResult = (Optional<?>) sgBoundingBoxMethod.invoke(generator);
        if (!boundingBoxResult.isPresent()) return null;

        StructureBoundingBox boundingBox = (StructureBoundingBox) boundingBoxResult.get();
        Object environment = sgEnvironmentMethod.invoke(generator);
        Biome biome = (Biome) envBiomeField.get(environment);
        if (biome != null) {
            double weight = generationWeight(generation, ((World) validationWorld).provider, biome);
            if (weight <= 0) {
                SimpleStructureScanner.LOGGER.debug(
                    "Rejected '{}' in chunk ({},{}): biome {} has invalid generation weight ({})",
                    structureId, chunkPos.x, chunkPos.z, biome.getRegistryName(), weight);
                return null;
            }
        }

        return boundingBoxCenter(boundingBox);
    }

    /**
     * Calculates Recurrent Complex's seeded X/Z position inside a candidate chunk.
     */
    static BlockPos computeSurfacePos(ChunkPos chunkPos, long seed) {
        Random posRandom = new Random(seed ^ 0x12048F0015F8B476L);
        int x = chunkPos.x * 16 + posRandom.nextInt(16) + 8;
        int z = chunkPos.z * 16 + posRandom.nextInt(16) + 8;

        return new BlockPos(x, 0, z);
    }

    private static void setupGenerator(Object generator, RcGeneration generation,
            String structureId, long seed, BlockPos surfaceBlockPos) throws Exception {
        sgGenerationInfoMethod.invoke(generator, generation.value);
        sgSeedMethod.invoke(generator, seed);
        sgStructureIDMethod.invoke(generator, structureId);
        sgMaturityMethod.invoke(generator, generateMaturitySuggest);

        sgRandomPositionMethod.invoke(generator,
            blockSurfacePosFromMethod.invoke(null, surfaceBlockPos),
            placerMethod.invoke(generation.value));


        sgFromCenterMethod.invoke(generator, true);
    }

    private static void setValidationWorld(Object generator, World validationWorld) {
        // FIXME: May be removed in future java version, erf...
        unsafeInstance.putObject(generator, sgWorldFieldOffset, validationWorld);
    }

    private static String extractFailureDescription(Object testResult) {
        if (failureDescriptionField != null) {
            try {
                Object description = failureDescriptionField.get(testResult);
                return description != null ? description.toString() : "null";
            } catch (Exception ignored) {
            }
        }

        return testResult.toString();
    }

    private static BlockPos boundingBoxCenter(StructureBoundingBox boundingBox) {
        return new BlockPos(
            (boundingBox.minX + boundingBox.maxX) / 2,
            (boundingBox.minY + boundingBox.maxY) / 2,
            (boundingBox.minZ + boundingBox.maxZ) / 2);
    }

    // ========== Reflection initialization helpers ==========

    private static void initializeGeneratorAccess(Class<?> structureClass) throws Exception {
        Class<?> structureGeneratorClass = Class.forName(STRUCTURE_GENERATOR_CLASS);
        Class<?> generationTypeClass = Class.forName(GENERATION_TYPE_CLASS);
        Class<?> placerClass = Class.forName(PLACER_CLASS);
        Class<?> blockSurfacePosClass = Class.forName(BLOCK_SURFACE_POS_CLASS);
        Class<?> generateMaturityClass = Class.forName(GENERATE_MATURITY_CLASS);

        sgConstructor = structureGeneratorClass.getConstructor(structureClass);
        sgGenerationInfoMethod = structureGeneratorClass.getMethod("generationInfo", generationTypeClass);
        sgSeedMethod = structureGeneratorClass.getMethod("seed", Long.class);
        sgStructureIDMethod = structureGeneratorClass.getMethod("structureID", String.class);
        sgMaturityMethod = structureGeneratorClass.getMethod("maturity", generateMaturityClass);
        sgRandomPositionMethod = structureGeneratorClass.getMethod("randomPosition", blockSurfacePosClass, placerClass);
        sgFromCenterMethod = structureGeneratorClass.getMethod("fromCenter", boolean.class);
        sgBoundingBoxMethod = structureGeneratorClass.getMethod("boundingBox");
        sgStructureSizeMethod = structureGeneratorClass.getMethod("structureSize");
        sgWorldField = ReflectionHelper.getAccessibleDeclaredField(structureGeneratorClass, "world");

        // Unsafe assigns the validation world without StructureGenerator's WorldServer type check
        unsafeInstance = (Unsafe) ReflectionHelper.getAccessibleDeclaredField(Unsafe.class, "theUnsafe")
                                                  .get(null);
        // FIXME: Same issue as above...
        sgWorldFieldOffset = unsafeInstance.objectFieldOffset(sgWorldField);

        placerMethod = generationTypeClass.getMethod("placer");
        blockSurfacePosFromMethod = blockSurfacePosClass.getMethod("from", BlockPos.class);
        generateMaturitySuggest = Enum.valueOf((Class<Enum>) generateMaturityClass, "SUGGEST");

        sgTestMethod = structureGeneratorClass.getDeclaredMethod("test");
        sgAllowOverlapsMethod = structureGeneratorClass.getDeclaredMethod("allowOverlaps", boolean.class);
        sgEnvironmentMethod = structureGeneratorClass.getDeclaredMethod("environment");

        grSucceededMethod = Class.forName(GENERATION_RESULT_CLASS).getDeclaredMethod("succeeded");

        try {
            failureDescriptionField = Class.forName(GENERATION_FAILURE_CLASS).getField("description");
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Could not access the Recurrent Complex failure description", e);
        }

        envBiomeField = Class.forName(ENVIRONMENT_CLASS).getField("biome");
    }

    private static void initializeNaturalGenerationAccess() throws Exception {
        naturalGenerationClass = Class.forName(NATURAL_GENERATION_CLASS);
        vanillaGenerationClass = Class.forName(VANILLA_GENERATION_CLASS);
        ngGetGenerationWeightMethod = naturalGenerationClass.getDeclaredMethod("getGenerationWeight",
            WorldProvider.class, Biome.class);

        try {
            Class<?> rcConfigClass = Class.forName(RC_CONFIG_CLASS);
            rcTweakedSpawnRateMethod = rcConfigClass.getMethod("tweakedSpawnRate", String.class);
            rcGenEnabledBiomeMethod = rcConfigClass.getMethod("isGenerationEnabled", Biome.class);
            rcGenEnabledProviderMethod = rcConfigClass.getMethod("isGenerationEnabled", WorldProvider.class);
            rcMinDistToSpawnField = rcConfigClass.getField("minDistToSpawnForGeneration");
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug(
                "Could not access all Recurrent Complex generation settings; cached checks are unavailable", e);
        }
    }

    private static void initializeEventBusAccess() {
        try {
            eventBusBusIdField = ReflectionHelper.getAccessibleDeclaredField(EventBus.class, "busID");
            forgeBusId = eventBusBusIdField.getInt(MinecraftForge.EVENT_BUS);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug(
                "Could not access EventBus.busID; posting complete population events", e);
        }
    }

    private static void initializeLedgerAccess() {
        try {
            Class<?> dataClass = Class.forName(WORLD_STRUCTURE_GENERATION_DATA_CLASS);
            wsgdGetMethod = dataClass.getMethod("get", World.class);
            wsgdIsChunkCheckedMethod = dataClass.getMethod("isChunkChecked", ChunkPos.class);
            wsgdStructureEntriesInMethod = dataClass.getMethod("structureEntriesIn", ChunkPos.class);

            wsgdGetStructureIDMethod = Class.forName(STRUCTURE_ENTRY_CLASS).getMethod("getStructureID");
            entryGetBoundingBoxMethod = Class.forName(ENTRY_CLASS).getMethod("getBoundingBox");

            SimpleStructureScanner.LOGGER.info("Initialized Recurrent Complex saved-generation access");
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn(
                "Could not initialize Recurrent Complex saved-generation access", e);
        }
    }

    private static void warnVanillaDecorationGeneration(Class<?> registryClass) {
        try {
            Class<?> vanillaDecorationClass = Class.forName(VANILLA_DECORATION_GENERATION_CLASS);
            Method getGenerationTypesMethod = registryClass.getMethod("getGenerationTypes", Class.class);
            Collection<?> entries = (Collection<?>) getGenerationTypesMethod.invoke(
                    structureRegistryInstance, vanillaDecorationClass);

            if (entries != null && !entries.isEmpty()) {
                SimpleStructureScanner.LOGGER.warn("Recurrent Complex has {} VanillaDecorationGeneration entries. " +
                    "They can replace village pieces during population and invalidate village predictions.",
                    entries.size());
            }
        } catch (Exception ignored) {
        }
    }

}
