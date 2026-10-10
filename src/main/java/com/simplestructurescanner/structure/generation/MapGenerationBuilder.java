package com.simplestructurescanner.structure.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.WorldGenerator;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.fml.common.FMLCommonHandler;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.capture.StructureCaptureService;
import com.simplestructurescanner.structure.StructureInfo.StructureLayer;
import com.simplestructurescanner.util.SparkWrapper;


/**
 * Configures a finite terrain, generates a map, and captures its raw block layers.
 */
public final class MapGenerationBuilder {
    private static final boolean MAP_PROFILE = Boolean.getBoolean("simplestructurescanner.mapProfile");
    private static final boolean MAP_EXPORT = Boolean.getBoolean("simplestructurescanner.mapExport");
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    private final int sizeX;
    private final int sizeZ;
    private final int y;
    private final IBlockState material;
    private final List<Layer> aboveLayers = new ArrayList<>();
    private final List<Layer> belowLayers = new ArrayList<>();
    private final Set<Block> excludedBlocks = new HashSet<>();
    private final Set<IBlockState> excludedStates = new HashSet<>();

    private boolean capturePlatform = true;
    private boolean captureLowestNonOpaqueFloor;
    private int originX;
    private int originZ;
    private long seed = 69L;

    private MapGenerationWorld world;
    private String mapName;
    private long startNanos;
    private boolean built;
    private boolean captured;
    private boolean generationFailed;
    private List<StructureLayer> capturedLayers = Collections.emptyList();

    /**
     * Creates a new map generation builder with the specified platform size, height, and material.
     * The platform will be sizeX x 1 x sizeZ, of the specified material and at the specified Y level.
     * The parameters should be chosen to match the MapGen implementation requirements.
     */
    public MapGenerationBuilder(int sizeX, int sizeZ, int y, IBlockState material) {
        if (sizeX <= 0 || sizeZ <= 0) throw new IllegalArgumentException("Platform sizes must be positive");
        if (y < 0 || y > 255) throw new IllegalArgumentException("Platform Y must be between 0 and 255");
        if (material == null) throw new IllegalArgumentException("Platform material is required");

        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.y = y;
        this.material = material;
    }

    /**
     * Creates a new map generation builder for an underground structure (buried beneath the surface).
     * The terrain will be a {@code sizeX} x {@code y} x {@code sizeZ} rectangular cuboid
     * of the specified material, meaning it extends down to the bottom of the world.
     * The alternative, {@link #ofBuried(int, int, IBlockState)}, is usually enough for most cases.
     */
    public static MapGenerationBuilder ofBuried(int sizeX, int sizeZ, int y, IBlockState material) {
        return new MapGenerationBuilder(sizeX, sizeZ, y, material)
            .withBelowLayers(y, material)
            .withoutPlatform();
    }

    /**
     * Creates a new map generation builder for an underground structure with a default Y level of 64.
     * The terrain will be a {@code sizeX} x {@code 64} x {@code sizeZ} rectangular cuboid
     * of the specified material, meaning it extends down to the bottom of the world.
     */
    public static MapGenerationBuilder ofBuried(int sizeX, int sizeZ, IBlockState material) {
        return ofBuried(sizeX, sizeZ, 64, material);
    }

    /**
     * Creates a new map generation builder for an underground structure (buried beneath the surface)
     * with a floor layer underneath the carved-out air making the corridors and rooms.
     * Use it when the structure does not naturally generate its own floor layer.
     * The terrain will be a {@code sizeX} x {@code y} x {@code sizeZ} rectangular cuboid
     * of the specified material, meaning it extends down to the bottom of the world.
     */
    public static MapGenerationBuilder ofBuriedWithFloor(int sizeX, int sizeZ, int y, IBlockState material) {
        return new MapGenerationBuilder(sizeX, sizeZ, y, material)
            .withBelowLayers(y, material)
            .withLowestNonOpaqueFloor()
            .withoutPlatform();
    }

    /**
     * Creates a new map generation builder for an underground structure with a default Y level of 64
     * and a floor layer underneath the carved-out air making the corridors and rooms.
     * Use it when the structure does not naturally generate its own floor layer.
     * The terrain will be a {@code sizeX} x {@code 64} x {@code sizeZ} rectangular cuboid
     * of the specified material, meaning it extends down to the bottom of the world.
     */
    public static MapGenerationBuilder ofBuriedWithFloor(int sizeX, int sizeZ, IBlockState material) {
        return ofBuriedWithFloor(sizeX, sizeZ, 64, material);
    }

    /**
     * Creates a new map generation builder for a floating island structure.
     * A floating island is a structure that does not need any ground support,
     * instead floating in the air.
     * It has no platform or layers beneath it.
     */
    public static MapGenerationBuilder ofFloatingIsland(int sizeX, int sizeZ) {
        return new MapGenerationBuilder(sizeX, sizeZ, 64, AIR)
            .withoutPlatform();
    }

    /**
     * Adds a material band directly above the previous upper band.
     * These layers exist purely to satify the generator's requirements
     * and should be chosen considering the MapGen implementation.
     */
    public MapGenerationBuilder withAboveLayers(int sizeY, IBlockState layerMaterial) {
        if (built) throw new IllegalStateException("Cannot add layers after the map has been built");

        addLayer(aboveLayers, sizeY, layerMaterial);
        return this;
    }

    /**
     * Adds a material band directly below the previous lower band.
     * These layers exist purely to satify the generator's requirements.
     * and should be chosen considering the MapGen implementation.
     */
    public MapGenerationBuilder withBelowLayers(int sizeY, IBlockState layerMaterial) {
        if (built) throw new IllegalStateException("Cannot add layers after the map has been built");

        addLayer(belowLayers, sizeY, layerMaterial);
        return this;
    }

    /**
     * Omits the (1-block-thick) platform from the captured layers.
     * Should be used if the structure expands under the platform.
     */
    public MapGenerationBuilder withoutPlatform() {
        if (built) throw new IllegalStateException("Cannot change platform capture after the map has been built");

        capturePlatform = false;
        return this;
    }

    /**
     * Captures the configured terrain block below each vertical run of non-opaque generated blocks.
     * This creates a floor for structures that rely on terrain for support, only carving air.
     */
    public MapGenerationBuilder withLowestNonOpaqueFloor() {
        if (built) throw new IllegalStateException("Cannot add a generated floor after the map has been built");

        captureLowestNonOpaqueFloor = true;
        return this;
    }

    /**
     * Moves the platform center from the default world origin.
     * Should be used if the MapGen starts generating structures away from the default world origin.
     * A misaligned structure may try to expand beyond the platform area, resulting in cut-off.
     * A bigger platform area *may* be an alternative solution, but is more of a workaround.
     */
    public MapGenerationBuilder withOrigin(int x, int z) {
        if (built) throw new IllegalStateException("Cannot set the origin after the map has been built");

        originX = x;
        originZ = z;
        return this;
    }

    /**
     * Sets the deterministic random seed supplied to the map callback.
     */
    public MapGenerationBuilder withSeed(long value) {
        if (built) throw new IllegalStateException("Cannot set the seed after the map has been built");

        seed = value;
        return this;
    }

    /**
     * Configures a reflected structure start for this map.
     * Use it if the original structure generator is a StructureStart subclass.
     */
    public StructureStartGeneration withStructureStart(String className) {
        return withStructureStart(className, null);
    }

    /**
     * Configures a reflected structure start for this map and validates each created start.
     * Structure starts will be created until the validator accepts one.
     * Use it if the original structure generator is a StructureStart subclass.
     */
    public StructureStartGeneration withStructureStart(String className, StructureStartValidator validator) {
        if (built) throw new IllegalStateException("Cannot set the map generator after the map has been built");
        if (className == null || className.isEmpty()) throw new IllegalArgumentException("Map generator class is required");

        return new StructureStartGeneration(this, className, validator);
    }

    /**
     * Configures a reflected world generator at the platform origin.
     * Use it if the original world generator is a WorldGenerator subclass.
     */
    public WorldGeneratorGeneration withWorldGenerator(String className) {
        return withWorldGenerator(className, BlockPos.ORIGIN);
    }

    /**
     * Configures a reflected world generator at a vertical offset from the platform.
     * Use it if the original world generator is a WorldGenerator subclass.
     */
    public WorldGeneratorGeneration withWorldGenerator(String className, int yOffset) {
        return withWorldGenerator(className, new BlockPos(0, yOffset, 0));
    }

    /**
     * Configures a reflected world generator at an offset from the platform origin.
     * Use it if the original world generator is a WorldGenerator subclass.
     */
    public WorldGeneratorGeneration withWorldGenerator(String className, BlockPos relativePos) {
        return withWorldGenerator(className, relativePos, new Class<?>[0]);
    }

    /**
     * Configures a reflected world generator with constructor parameters at a vertical offset.
     * Use it if the original world generator is a WorldGenerator subclass.
     */
    public WorldGeneratorGeneration withWorldGenerator(String className, int yOffset,
            Class<?>[] parameterTypes, Object... parameters) {
        return withWorldGenerator(className, new BlockPos(0, yOffset, 0), parameterTypes, parameters);
    }

    /**
     * Configures a reflected world generator with constructor parameters at an offset from the platform origin.
     * Use it if the original world generator is a WorldGenerator subclass.
     */
    public WorldGeneratorGeneration withWorldGenerator(String className, BlockPos relativePos,
            Class<?>[] parameterTypes, Object... parameters) {
        if (built) throw new IllegalStateException("Cannot set the map generator after the map has been built");
        if (className == null || className.isEmpty()) throw new IllegalArgumentException("Map generator class is required");
        if (relativePos == null) throw new IllegalArgumentException("Map generator position is required");
        if (parameterTypes == null) throw new IllegalArgumentException("Map generator parameter types are required");
        if (parameters == null) throw new IllegalArgumentException("Map generator parameters are required");

        return new WorldGeneratorGeneration(this, className, relativePos, parameterTypes, parameters);
    }

    /**
     * Configures a reflected world generator to populate the platform's chunks.
     * Use it when the original world generator needs to be called multiple times,
     * instead of a single call during the initial map generation.
     * If the generator only needs to be called once, consider using {@link #withWorldGenerator}.
     *
     * @param padding The padding around the platform where chunks should not be populated.
     *                Structures within this padding area would run the risk of being cut-off.
     * @param positionProvider The provider that determines the position of the structure within
     *                         the given platform. Returns null if the chunk is not suitable.
     */
    public ChunkPopulationGeneration withChunkPopulation(String className, int padding,
            ChunkPositionProvider positionProvider) {
        if (built) throw new IllegalStateException("Cannot set the map generator after the map has been built");
        if (className == null || className.isEmpty()) throw new IllegalArgumentException("Map generator class is required");
        if (padding < 0) throw new IllegalArgumentException("Population padding cannot be negative");
        if (positionProvider == null) throw new IllegalArgumentException("Chunk position provider is required");

        return new ChunkPopulationGeneration(this, className, padding, positionProvider);
    }

    /**
     * Sets the debugging name of the generated map. Used for logging, profiling, and exporting purposes.
     */
    public MapGenerationBuilder withName(String value) {
        if (built) throw new IllegalStateException("Cannot set the map name after the map has been built");
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("Map name is required");

        mapName = value;
        return this;
    }

    /**
     * Runs the supplied map for capture purposes. Note that this occurs in a fake World
     * (not WorldServer), meaning any MapGen that relies on server-side world features
     * may not function as expected. Some may crash (resulting in no captured data),
     * while others may be missing features (partial capture).
     */
    public MapGenerationBuilder build(BiConsumer<MapGenerationWorld, Random> map, Biome biome) {
        if (built) throw new IllegalStateException("The map has already been built");

        if (map == null) throw new IllegalArgumentException("Map callback is required");
        if (biome == null) throw new IllegalArgumentException("Map biome is required");
        if (FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
            throw new IllegalStateException("Map generation must run before a server world loads");
        }

        validateHeight();

        startNanos = System.nanoTime();

        if (mapName == null) mapName = map.getClass().getName();

        built = true;

        SparkWrapper spark = null;
        if (MAP_PROFILE) spark = SparkWrapper.start(mapName + "-build", 1);

        // We isolate individual map generation failures, so the rest of the provider can go through
        try {
            world = new MapGenerationWorld(
                sizeX, sizeZ, y,
                material, aboveLayers, belowLayers, capturePlatform, captureLowestNonOpaqueFloor,
                excludedBlocks, excludedStates, originX, originZ, seed, biome
            );

            Random random = new Random(seed);
            map.accept(world, random);
        } catch (Exception e) {
            generationFailed = true;
            SimpleStructureScanner.LOGGER.error("Map generation failed for {}: {}",
                mapName, e.getMessage(), e);
        } finally {
            if (spark != null) spark.stop();
        }

        return this;
    }

    /**
     * Excludes matching blocks from the captured layers.
     * This excludes EVERY variant of the specified blocks.
     */
    public MapGenerationBuilder withoutBlocks(Block... blocks) {
        if (built) throw new IllegalStateException("Cannot exclude blocks after the map has been built");
        if (blocks == null) return this;

        for (Block block : blocks) {
            if (block != null) excludedBlocks.add(block);
        }

        return this;
    }

    /**
     * Excludes matching block states from the captured layers.
     */
    public MapGenerationBuilder withoutBlockStates(IBlockState... states) {
        if (built) throw new IllegalStateException("Cannot exclude block states after the map has been built");
        if (states == null) return this;

        for (IBlockState state : states) {
            if (state != null) excludedStates.add(state);
        }

        return this;
    }

    /**
     * Captures the generated structure(s) after the map has been built.
     */
    public List<StructureLayer> capture() {
        if (!built) throw new IllegalStateException("Build the map before capturing its layers");
        if (captured) return capturedLayers;

        captured = true;
        if (generationFailed) return capturedLayers;

        SparkWrapper spark = null;
        if (MAP_PROFILE) spark = SparkWrapper.start(mapName + "-capture", 1);

        try {
            capturedLayers = world.capture();
            if (MAP_EXPORT) exportMap(world, capturedLayers);
            return capturedLayers;
        } catch (Exception e) {
            generationFailed = true;
            SimpleStructureScanner.LOGGER.error("Map capture failed for {}: {}",
                mapName, e.getMessage(), e);

            return capturedLayers;
        } finally {
            if (spark != null) spark.stop();

            long elapsedMillis = (System.nanoTime() - startNanos) / 1000000L;
            SimpleStructureScanner.LOGGER.debug("Generated map {} in {} ms", mapName, elapsedMillis);
        }
    }

    private void exportMap(MapGenerationWorld world, List<StructureLayer> capturedLayers) {
        BlockPos captureOrigin = world.getCaptureOrigin();
        if (captureOrigin == null) return;

        try {
            StructureCaptureService.SaveResult result = StructureCaptureService.saveMapCapture(
                mapName, captureOrigin, capturedLayers, world.getGeneratedEntityData());
            if (result != null) {
                SimpleStructureScanner.LOGGER.info(
                    "Exported generated map {} to {}", mapName, result.getFile());
            }
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.error("Could not export generated map {}: {}", mapName,
                e.getMessage(), e);
        }
    }

    /**
     * Returns entity data produced by the generated map.
     */
    public List<NBTTagCompound> getGeneratedEntityData() {
        if (!built) throw new IllegalStateException("Build the map before reading generated entities");
        if (generationFailed) return Collections.emptyList();

        return world.getGeneratedEntityData();
    }

    /**
     * Reports whether building or capturing this map failed.
     */
    public boolean hasGenerationFailed() {
        return generationFailed;
    }

    private static void addLayer(List<Layer> layers, int sizeY, IBlockState material) {
        if (sizeY <= 0) throw new IllegalArgumentException("Layer height must be positive");
        if (material == null) throw new IllegalArgumentException("Layer material is required");

        layers.add(new Layer(sizeY, material));
    }

    private void validateHeight() {
        int upperHeight = getLayerHeight(aboveLayers);
        int lowerHeight = getLayerHeight(belowLayers);
        if (y + upperHeight > 255 || y - lowerHeight < 0) {
            throw new IllegalStateException("Platform layers extend outside the build height");
        }
    }

    private static int getLayerHeight(List<Layer> layers) {
        int height = 0;
        for (Layer layer : layers) height += layer.sizeY;

        return height;
    }

    static final class Layer {
        final int sizeY;
        final IBlockState material;

        private Layer(int sizeY, IBlockState material) {
            this.sizeY = sizeY;
            this.material = material;
        }
    }

    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T first, U second, V third) throws ReflectiveOperationException;
    }

    @FunctionalInterface
    public interface ChunkPositionProvider {
        @Nullable
        BlockPos getGenerationPosition(WorldGenerator generator, MapGenerationWorld world, Random random,
                int chunkX, int chunkZ) throws ReflectiveOperationException;
    }

    @FunctionalInterface
    public interface StructureStartValidator {
        boolean isComplete(StructureStart structureStart) throws ReflectiveOperationException;
    }

    /**
     * Base class for generator-based map generation.
     */
    public abstract static class GeneratorGeneration<G> {
        private final MapGenerationBuilder builder;
        private final List<TriConsumer<G, MapGenerationWorld, Random>> preGenerationSteps = new ArrayList<>();
        private final List<TriConsumer<G, MapGenerationWorld, Random>> postGenerationSteps = new ArrayList<>();

        private GeneratorGeneration(MapGenerationBuilder builder) {
            this.builder = builder;
        }

        /**
         * Adds a step that runs after creating the generator, but before running the main generation logic.
         */
        public GeneratorGeneration<G> withPreGeneration(TriConsumer<G, MapGenerationWorld, Random> step) {
            if (builder.built) throw new IllegalStateException("Cannot add generation steps after the map has been built");
            if (step == null) throw new IllegalArgumentException("Generation step is required");

            preGenerationSteps.add(step);
            return this;
        }

        /**
         * Adds a step that runs after the main generation logic, but before the capture.
         */
        public GeneratorGeneration<G> withPostGeneration(TriConsumer<G, MapGenerationWorld, Random> step) {
            if (builder.built) throw new IllegalStateException("Cannot add generation steps after the map has been built");
            if (step == null) throw new IllegalArgumentException("Generation step is required");

            postGenerationSteps.add(step);
            return this;
        }

        /**
         * Generates the configured map for capture purposes.
         */
        public final MapGenerationBuilder build(Biome biome) {
            return builder.build(this::generateMap, biome);
        }

        private void generateMap(MapGenerationWorld world, Random random) {
            try {
                G generator = createGenerator(world, random);
                runGenerationSteps(preGenerationSteps, generator, world, random);
                generate(generator, world, random);
                runGenerationSteps(postGenerationSteps, generator, world, random);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(getFailureMessage(), e);
            }
        }

        private static <G> void runGenerationSteps(List<TriConsumer<G, MapGenerationWorld, Random>> steps,
                G generator, MapGenerationWorld world, Random random) throws ReflectiveOperationException {
            for (TriConsumer<G, MapGenerationWorld, Random> step : steps) step.accept(generator, world, random);
        }

        protected abstract G createGenerator(MapGenerationWorld world, Random random)
                throws ReflectiveOperationException;

        protected abstract void generate(G generator, MapGenerationWorld world, Random random)
                throws ReflectiveOperationException;

        protected abstract String getFailureMessage();
    }

    /**
     * Map generation wrapper for StructureStart-based generators.
     * Use it if the original world generator is a StructureStart subclass.
     */
    public static final class StructureStartGeneration extends GeneratorGeneration<StructureStart> {
        private final String className;
        private final StructureStartValidator validator;

        private StructureStartGeneration(MapGenerationBuilder builder, String className,
                StructureStartValidator validator) {
            super(builder);
            this.className = className;
            this.validator = validator;
        }

        @Override
        protected StructureStart createGenerator(MapGenerationWorld world, Random random)
                throws ReflectiveOperationException {
            StructureStart structureStart;
            do {
                Object createdStart = Class.forName(className)
                    .getConstructor(World.class, Random.class, int.class, int.class)
                    .newInstance(world, random, 0, 0);
                if (!(createdStart instanceof StructureStart)) {
                    throw new IllegalStateException("Map structure start has an invalid type: " + className);
                }

                structureStart = (StructureStart) createdStart;
            } while (validator != null && !validator.isComplete(structureStart));

            return structureStart;
        }

        @Override
        protected void generate(StructureStart structureStart, MapGenerationWorld world, Random random) {
            StructureBoundingBox bounds = new StructureBoundingBox(world.getMinX(), 0, world.getMinZ(),
                world.getMaxX(), 255, world.getMaxZ());
            structureStart.generateStructure(world, random, bounds);
        }

        @Override
        protected String getFailureMessage() {
            return "Could not create map structure start: " + className;
        }
    }

    /**
     * Base class for reflected WorldGenerator-based map generation wrappers.
     */
    private abstract static class ReflectedWorldGeneratorGeneration extends GeneratorGeneration<WorldGenerator> {
        private final String className;
        private final Class<?>[] parameterTypes;
        private final Object[] parameters;

        private ReflectedWorldGeneratorGeneration(MapGenerationBuilder builder, String className,
                Class<?>[] parameterTypes, Object[] parameters) {
            super(builder);
            this.className = className;
            this.parameterTypes = parameterTypes;
            this.parameters = parameters;
        }

        @Override
        protected WorldGenerator createGenerator(MapGenerationWorld world, Random random)
                throws ReflectiveOperationException {
            Object generator = Class.forName(className).getConstructor(parameterTypes).newInstance(parameters);
            if (!(generator instanceof WorldGenerator)) {
                throw new IllegalStateException("Map generator has an invalid type: " + className);
            }

            return (WorldGenerator) generator;
        }

        @Override
        protected String getFailureMessage() {
            return "Could not create map generator: " + className;
        }
    }

    /**
     * Map generation wrapper for WorldGenerator-based generators.
     * Use it if the original world generator is a WorldGenerator subclass
     * that builds the whole structure in one generate call.
     */
    public static final class WorldGeneratorGeneration extends ReflectedWorldGeneratorGeneration {
        private final BlockPos relativePos;

        private WorldGeneratorGeneration(MapGenerationBuilder builder, String className, BlockPos relativePos,
                Class<?>[] parameterTypes, Object[] parameters) {
            super(builder, className, parameterTypes, parameters);
            this.relativePos = relativePos;
        }

        @Override
        protected void generate(WorldGenerator generator, MapGenerationWorld world, Random random) {
            BlockPos pos = new BlockPos(relativePos.getX(), world.getPlatformY() + relativePos.getY(),
                relativePos.getZ());
            generator.generate(world, random, pos);
        }
    }

    /**
     * Map generation wrapper that runs a WorldGenerator across the platform's chunks.
     * Use it if the original world generator is a WorldGenerator subclass
     * that needs to be run across multiple chunks rather than just once,
     * in a pseudo-populate way.
     */
    public static final class ChunkPopulationGeneration extends ReflectedWorldGeneratorGeneration {
        private final int padding;
        private final ChunkPositionProvider positionProvider;

        private ChunkPopulationGeneration(MapGenerationBuilder builder, String className, int padding,
                ChunkPositionProvider positionProvider) {
            super(builder, className, new Class<?>[0], new Object[0]);
            this.padding = padding;
            this.positionProvider = positionProvider;
        }

        @Override
        protected void generate(WorldGenerator generator, MapGenerationWorld world, Random random)
                throws ReflectiveOperationException {
            int minChunkX = Math.floorDiv(world.getMinX(), 16);
            int maxChunkX = Math.floorDiv(world.getMaxX(), 16);
            int minChunkZ = Math.floorDiv(world.getMinZ(), 16);
            int maxChunkZ = Math.floorDiv(world.getMaxZ(), 16);

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    BlockPos pos = positionProvider.getGenerationPosition(generator, world, random, chunkX, chunkZ);
                    if (pos == null || !isWithinPopulationBounds(pos, world)) continue;

                    generator.generate(world, random, pos);
                }
            }
        }

        private boolean isWithinPopulationBounds(BlockPos pos, MapGenerationWorld world) {
            return pos.getX() >= world.getMinX() + padding && pos.getX() <= world.getMaxX() - padding
                && pos.getZ() >= world.getMinZ() + padding && pos.getZ() <= world.getMaxZ() - padding;
        }
    }
}
