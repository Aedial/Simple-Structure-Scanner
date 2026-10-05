package com.simplestructurescanner.structure.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
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

    private final int sizeX;
    private final int sizeZ;
    private final int y;
    private final IBlockState material;
    private final List<Layer> aboveLayers = new ArrayList<>();
    private final List<Layer> belowLayers = new ArrayList<>();
    private final Set<Block> removedBlocks = new HashSet<>();

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
     * Adds a material band directly above the previous upper band.
     */
    public MapGenerationBuilder withAboveLayers(int sizeY, IBlockState layerMaterial) {
        if (built) throw new IllegalStateException("Cannot add layers after the map has been built");

        addLayer(aboveLayers, sizeY, layerMaterial);
        return this;
    }

    /**
     * Adds a material band directly below the previous lower band.
     */
    public MapGenerationBuilder withBelowLayers(int sizeY, IBlockState layerMaterial) {
        if (built) throw new IllegalStateException("Cannot add layers after the map has been built");

        addLayer(belowLayers, sizeY, layerMaterial);
        return this;
    }

    /**
     * Moves the platform center from the default world origin.
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

    public MapGenerationBuilder withName(String value) {
        if (built) throw new IllegalStateException("Cannot set the map name after the map has been built");
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("Map name is required");

        mapName = value;
        return this;
    }

    /**
     * Runs the supplied map before server worlds exist.
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

        SparkWrapper spark = null;
        if (MAP_PROFILE) spark = SparkWrapper.start(mapName + "-build", 1);

        // We isolate individual map generation failures, so the rest of the provider can go through
        try {
            world = new MapGenerationWorld(
                sizeX, sizeZ, y,
                material, aboveLayers, belowLayers,
                originX, originZ, seed, biome
            );

            map.accept(world, new Random(seed));
        } catch (Exception e) {
            generationFailed = true;
            SimpleStructureScanner.LOGGER.error("Map generation failed for {}: {}",
                mapName, e.getMessage(), e);
        } finally {
            if (spark != null) spark.stop();
        }

        built = true;

        return this;
    }

    /**
     * Removes matching block states from the captured layers.
     */
    public MapGenerationBuilder removeBlocks(Block... blocks) {
        if (captured) throw new IllegalStateException("Map layers are already captured");
        if (blocks == null) return this;

        for (Block block : blocks) {
            if (block != null) removedBlocks.add(block);
        }

        return this;
    }

    /**
     * Captures the generated structure(s) with the platform restored inside structure bounds.
     */
    public List<StructureLayer> capture() {
        if (!built) throw new IllegalStateException("Build the map before capturing its layers");
        if (captured) return capturedLayers;

        captured = true;
        if (generationFailed) return capturedLayers;

        SparkWrapper spark = null;
        if (MAP_PROFILE) spark = SparkWrapper.start(mapName + "-capture", 1);

        try {
            capturedLayers = world.capture(removedBlocks);
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
}
