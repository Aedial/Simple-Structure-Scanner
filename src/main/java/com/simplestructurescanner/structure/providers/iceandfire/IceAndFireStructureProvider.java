package com.simplestructurescanner.structure.providers.iceandfire;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.WorldGenerator;
import net.minecraftforge.common.BiomeDictionary;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.providers.AbstractStructureProvider;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.generation.MapGenerationBuilder;
import com.simplestructurescanner.structure.generation.MapGenerationWorld;
import com.simplestructurescanner.structure.util.RarityTextHelper;


/**
 * Structure provider for Ice and Fire mod.
 * Supports previews and metadata for Ice and Fire's major structures.
 *
 * <p>Note: Ice and Fire structures use non-deterministic generation based on
 * terrain checks, biome conditions, random chance, and instance-based distance tracking.
 * As such, none of these structures can be reliably searched for.</p>
 */
public class IceAndFireStructureProvider extends AbstractStructureProvider {

    private static final String PROVIDER_ID = "iceandfire";
    private static final String MOD_ID = "iceandfire";
    private static final String MOD_NAME = "gui.structurescanner.provider.iceandfire";

    private static final int MYRMEX_MIN_DISTANCE_BLOCKS = 500;
    private static final String WORLD_GENERATOR_PACKAGE = "com.github.alexthe666.iceandfire.world.gen.";

    private int worldGenDistance = 150;
    private int dragonDenChance = 180;
    private int dragonRoostChance = 360;
    private int gorgonChance = 75;
    private int cyclopsCaveChance = 170;
    private int mausoleumChance = 1800;
    private int hydraChance = 200;
    private int myrmexChance = 150;

    public IceAndFireStructureProvider() {
        super(PROVIDER_ID, MOD_ID, MOD_NAME, MOD_ID);
    }

    @Override
    public void postInit() {
        resetStructures();
        loadConfig();
        registerStructures();
    }

    private void loadConfig() {
        try {
            Class<?> iceAndFireClass = Class.forName("com.github.alexthe666.iceandfire.IceAndFire");
            Object config = iceAndFireClass.getField("CONFIG").get(null);
            Class<?> configClass = config.getClass();

            worldGenDistance = configClass.getField("worldGenDistance").getInt(config);
            dragonDenChance = configClass.getField("generateDragonDenChance").getInt(config);
            dragonRoostChance = configClass.getField("generateDragonRoostChance").getInt(config);
            gorgonChance = configClass.getField("spawnGorgonsChance").getInt(config);
            cyclopsCaveChance = configClass.getField("spawnCyclopsCaveChance").getInt(config);
            mausoleumChance = configClass.getField("generateMausoleumChance").getInt(config);
            hydraChance = configClass.getField("generateHydraChance").getInt(config);
            myrmexChance = configClass.getField("myrmexColonyGenChance").getInt(config);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Could not load Ice and Fire config, using defaults: {}", e.getMessage());
        }
    }

    private void registerStructures() {
        Set<DimensionInfo> overworld = Collections.singleton(DimensionInfo.OVERWORLD);

        // Fire Dragon Roost - warm, non-snowy land biomes
        Set<Biome> fireDragonRoostBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (!biome.getEnableSnow()
                    && biome.getDefaultTemperature() > 0.0F
                    && biome != Biomes.ICE_PLAINS
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.COLD)
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.SNOWY)
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.WET)
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN)
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER)) {
                fireDragonRoostBiomes.add(biome);
            }
        }
        register("fire_dragon_roost")
            .fromMapWithBundledFallback(createSurfaceMap("fire_dragon_roost", Biomes.PLAINS,
                createMapGenerator("WorldGenFireDragonRoosts"), GRASS))
            .withMetadata(fireDragonRoostBiomes, overworld)
            .withRarity(calculateDragonRarity(fireDragonRoostBiomes, true, dragonRoostChance));

        Set<Biome> fireDragonCaveBiomes = hasBiomesBut(fireDragonRoostBiomes, BiomeDictionary.Type.BEACH);
        register("fire_dragon_cave")
            .fromMapWithBundledFallback(createUndergroundMap("fire_dragon_cave", Biomes.PLAINS,
                createMapGenerator("WorldGenFireDragonCave", -32)))
            .withMetadata(fireDragonCaveBiomes, overworld)
            .withRarity(calculateDragonRarity(fireDragonCaveBiomes, false, dragonDenChance));

        // Ice Dragon Roost - cold & snowy biomes
        Set<Biome> iceDragonRoostBiomes = hasAllBiomes(BiomeDictionary.Type.COLD, BiomeDictionary.Type.SNOWY);
        register("ice_dragon_roost")
            .fromMapWithBundledFallback(createSurfaceMap("ice_dragon_roost", Biomes.ICE_PLAINS,
                createMapGenerator("WorldGenIceDragonRoosts"), SNOW))
            .withMetadata(iceDragonRoostBiomes, overworld)
            .withRarity(calculateDragonRarity(iceDragonRoostBiomes, true, dragonRoostChance));

        Set<Biome> iceDragonCaveBiomes = hasBiomesBut(iceDragonRoostBiomes, BiomeDictionary.Type.BEACH);
        register("ice_dragon_cave")
            .fromMapWithBundledFallback(createUndergroundMap("ice_dragon_cave", Biomes.ICE_PLAINS,
                createMapGenerator("WorldGenIceDragonCave", -32)))
            .withMetadata(iceDragonCaveBiomes, overworld)
            .withRarity(calculateDragonRarity(iceDragonCaveBiomes, false, dragonDenChance));

        // Lightning Dragon Roost/Cave - jungle, mesa, savanna biomes
        Set<Biome> lightningDragonBiomes = hasAnyBiomes(BiomeDictionary.Type.JUNGLE,
            BiomeDictionary.Type.MESA, BiomeDictionary.Type.SAVANNA);

        register("lightning_dragon_roost")
            .fromMapWithBundledFallback(createSurfaceMap("lightning_dragon_roost", Biomes.SAVANNA,
                createMapGenerator("WorldGenLightningDragonRoosts"), GRASS))
            .withMetadata(lightningDragonBiomes, overworld)
            .withRarity(calculateDragonRarity(lightningDragonBiomes, true, dragonRoostChance));

        register("lightning_dragon_cave")
            .fromMapWithBundledFallback(createUndergroundMap("lightning_dragon_cave", Biomes.SAVANNA,
                createMapGenerator("WorldGenLightningDragonCave", -32)))
            .withMetadata(lightningDragonBiomes, overworld)
            .withRarity(calculateDragonRarity(lightningDragonBiomes, false, dragonDenChance));

        // Cyclops Cave - beach biomes
        Set<Biome> beachBiomes = hasAnyBiomes(BiomeDictionary.Type.BEACH);

        register("cyclops_cave")
            .fromMapWithBundledFallback(createSurfaceMap("cyclops_cave", Biomes.BEACH,
                createMapGenerator("WorldGenCyclopsCave", 3), STONE))
            .withMetadata(beachBiomes, overworld)
            .withRarity(calculateApproximateRarity(cyclopsCaveChance + 1.0D, worldGenDistance));

        // Gorgon Temple - beach biomes
        register("gorgon_temple")
            .fromMapWithBundledFallback(createSurfaceMap("gorgon_temple", Biomes.BEACH,
                createFacingMapGenerator("WorldGenGorgonTemple"), SAND))
            .withMetadata(beachBiomes, overworld)
            .withRarity(calculateApproximateRarity(gorgonChance + 1.0D, worldGenDistance));

        // Mausoleum - cold, snowy biomes
        register("mausoleum")
            .fromMapWithBundledFallback(createSurfaceMap("mausoleum", Biomes.ICE_PLAINS,
                createFacingMapGenerator("WorldGenMausoleum"), SNOW))
            .withMetadata(iceDragonRoostBiomes, overworld)
            .withRarity(calculateApproximateRarity(mausoleumChance + 1.0D, worldGenDistance));

        // Hydra Lair - swamp biomes
        Set<Biome> swampBiomes = hasAnyBiomes(BiomeDictionary.Type.SWAMP);

        register("hydra_lair")
            .fromMapWithBundledFallback(createSurfaceMap("hydra_lair", Biomes.SWAMPLAND,
                createMapGenerator("WorldGenHydraCave", 3), GRASS))
            .withMetadata(swampBiomes, overworld)
            .withRarity(calculateApproximateRarity(hydraChance + 1.0D, worldGenDistance));

        // Myrmex Hive Desert - hot & dry & sandy biomes
        Set<Biome> desertBiomes = hasAllBiomes(BiomeDictionary.Type.HOT,
            BiomeDictionary.Type.DRY, BiomeDictionary.Type.SANDY);

        register("myrmex_hive_desert")
            .fromMapWithBundledFallback(createHiveMap("myrmex_hive_desert", Biomes.DESERT, false))
            .withMetadata(desertBiomes, overworld)
            .withRarity(calculateApproximateRarity(myrmexChance, MYRMEX_MIN_DISTANCE_BLOCKS));

        // Myrmex Hive Jungle - jungle biomes
        Set<Biome> jungleBiomes = hasAnyBiomes(BiomeDictionary.Type.JUNGLE);
        register("myrmex_hive_jungle")
            .fromMapWithBundledFallback(createHiveMap("myrmex_hive_jungle", Biomes.JUNGLE, true))
            .withMetadata(jungleBiomes, overworld)
            .withRarity(calculateApproximateRarity(myrmexChance, MYRMEX_MIN_DISTANCE_BLOCKS));
    }

    private static MapGenerationBuilder createSurfaceMap(String name, Biome biome,
            BiConsumer<MapGenerationWorld, Random> generator, IBlockState platformBlock) {
        return new MapGenerationBuilder(128, 128, 64, platformBlock)
            .withName(MOD_ID + ":" + name)
            .build(generator, biome);
    }

    private static MapGenerationBuilder createUndergroundMap(String name, Biome biome,
            BiConsumer<MapGenerationWorld, Random> generator) {
        return new MapGenerationBuilder(128, 128, 64, GRASS)
            .withName(MOD_ID + ":" + name)
            .withBelowLayers(63, STONE)
            .withoutPlatform()
            .build(generator, biome);
    }

    private static MapGenerationBuilder createHiveMap(String name, Biome biome, boolean jungle) {
        return new MapGenerationBuilder(256, 256, 64, GRASS)
            .withName(MOD_ID + ":" + name)
            .withoutPlatform()
            .build(createMapGenerator("WorldGenMyrmexHive", -32,
                new Class<?>[] { boolean.class, boolean.class }, false, jungle), biome);
    }

    private static BiConsumer<MapGenerationWorld, Random> createMapGenerator(String generatorClassName) {
        return createMapGenerator(generatorClassName, 0);
    }

    private static BiConsumer<MapGenerationWorld, Random> createMapGenerator(String generatorClassName,
            int offset) {
        return (world, random) -> generateWorldGenerator(world, random, generatorClassName,
            new Class<?>[0], offset);
    }

    private static BiConsumer<MapGenerationWorld, Random> createMapGenerator(
            String generatorClassName, int offset, Class<?>[] parameterTypes, Object... parameters) {
        return (world, random) -> generateWorldGenerator(world, random, generatorClassName,
            parameterTypes, offset, parameters);
    }

    private static BiConsumer<MapGenerationWorld, Random> createFacingMapGenerator(String generatorClassName) {
        return createMapGenerator(generatorClassName, 0, new Class<?>[] { EnumFacing.class }, EnumFacing.NORTH);
    }

    private static void generateWorldGenerator(MapGenerationWorld world, Random random,
            String generatorClassName, Class<?>[] parameterTypes, int offset, Object... parameters) {
        try {
            Object generator = Class.forName(WORLD_GENERATOR_PACKAGE + generatorClassName)
                .getConstructor(parameterTypes).newInstance(parameters);
            if (!(generator instanceof WorldGenerator)) {
                throw new IllegalStateException("Ice and Fire generator is not a WorldGenerator: "
                    + generatorClassName);
            }

            BlockPos pos = new BlockPos(0, world.getPlatformY() + offset, 0);
            ((WorldGenerator) generator).generate(world, random, pos);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not create Ice and Fire generator: "
                + generatorClassName, e);
        }
    }

    private LocalizedText calculateDragonRarity(Set<Biome> biomes, boolean roost, int baseChance) {
        if (biomes.isEmpty()) return calculateApproximateRarity(baseChance + 1.0D, worldGenDistance);

        int hillBiomes = 0;

        for (Biome biome : biomes) {
            if (isDragonHillBiome(biome, roost)) hillBiomes++;
        }

        int flatBiomes = biomes.size() - hillBiomes;
        double hillProbability = 1.0D / (baseChance + 1.0D);
        double flatProbability = 1.0D / (baseChance * 2.0D + 1.0D);
        double averageProbability = (hillBiomes * hillProbability + flatBiomes * flatProbability) / biomes.size();

        return calculateApproximateRarity(RarityTextHelper.chunksFromProbability(averageProbability), worldGenDistance);
    }

    private boolean isDragonHillBiome(Biome biome, boolean roost) {
        if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.HILLS)) return true;
        if (!BiomeDictionary.hasType(biome, BiomeDictionary.Type.MOUNTAIN)) return false;
        if (!roost) return true;

        return !BiomeDictionary.hasType(biome, BiomeDictionary.Type.SNOWY);
    }

    private LocalizedText calculateApproximateRarity(double rawChunks, double minDistanceBlocks) {
        return RarityTextHelper.withMinimumSpacing(rawChunks, minDistanceBlocks);
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        // Ice and Fire structures use non-deterministic generation
        // based on terrain checks, random chance, and instance-based distance tracking.
        // None can be reliably searched for.
        return false;
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId,
            BlockPos pos, int skipCount, @Nullable Predicate<BlockPos> locationFilter) {
        // Not searchable
        return null;
    }

    @Override
    @Nullable
    public List<BlockPos> findAllNearby(World world, ResourceLocation structureId,
            BlockPos pos, int maxResults) {
        // Not searchable
        return null;
    }
}
