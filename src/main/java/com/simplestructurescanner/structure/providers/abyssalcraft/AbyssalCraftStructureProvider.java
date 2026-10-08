package com.simplestructurescanner.structure.providers.abyssalcraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.common.BiomeDictionary;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.providers.AbstractStructureProvider;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.generation.MapGenerationBuilder;
import com.simplestructurescanner.structure.util.PositionHelper;
import com.simplestructurescanner.structure.util.RarityTextHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper;
import com.simplestructurescanner.structure.util.SeedHelper;


/**
 * Structure provider for AbyssalCraft mod.
 * Provides location data for AC's major structures.
 */
public class AbyssalCraftStructureProvider extends AbstractStructureProvider {

    private static final String PROVIDER_ID = "abyssalcraft";
    private static final String MOD_ID = "abyssalcraft";
    private static final String MOD_NAME = "gui.structurescanner.provider.abyssalcraft";

    private static final String AC_BLOCKS_CLASS = "com.shinoow.abyssalcraft.api.block.ACBlocks";
    private static final String AC_CONFIG_CLASS = "com.shinoow.abyssalcraft.lib.ACConfig";
    private static final String STRUCTURE_PACKAGE = "com.shinoow.abyssalcraft.common.structures.";
    private static final String GRAVEYARD_CLASS = STRUCTURE_PACKAGE + "StructureGraveyard";
    private static final int LEGACY_OMOTHOL_STONE_META = 6;

    // Cache: seed -> list of AbyStronghold positions
    private static final Map<Long, List<BlockPos>> abyStrongholdCache = new HashMap<>();

    // AbyssalCraft dimension IDs (fetched at runtime)
    private int abyssalWastelandId = -1;
    private int dreadlandsId = -1;
    private int omotholId = -1;

    // AbyssalCraft biomes (fetched at runtime)
    private Biome wastelandsBiome;
    private Biome dreadlandsBiome;
    private Biome omotholBiome;
    private int shoggothLairSpawnRate = 35;
    private int shoggothLairSpawnRateRivers = 30;
    private int shoggothLairGenerationDistance = 100;
    private int graveyardGenerationChance = 50;
    private int graveyardGenerationDistance = 150;

    public AbyssalCraftStructureProvider() {
        super(PROVIDER_ID, MOD_ID, MOD_NAME, MOD_ID);
    }

    @Override
    public void postInit() {
        resetStructures();

        // Get dimension IDs from ACLib
        try {
            Class<?> acLibClass = ReflectionHelper.loadClassRequired("com.shinoow.abyssalcraft.lib.ACLib");
            abyssalWastelandId = ReflectionHelper.getStaticIntField(acLibClass, "abyssal_wasteland_id");
            dreadlandsId = ReflectionHelper.getStaticIntField(acLibClass, "dreadlands_id");
            omotholId = ReflectionHelper.getStaticIntField(acLibClass, "omothol_id");
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.error("Failed to get AbyssalCraft dimension IDs", e);
        }

        // Get biomes from ACBiomes
        try {
            Class<?> acBiomesClass = ReflectionHelper.loadClassRequired("com.shinoow.abyssalcraft.api.biome.ACBiomes");
            wastelandsBiome = (Biome) ReflectionHelper.getStaticField(acBiomesClass, "abyssal_wastelands");
            dreadlandsBiome = (Biome) ReflectionHelper.getStaticField(acBiomesClass, "dreadlands");
            omotholBiome = (Biome) ReflectionHelper.getStaticField(acBiomesClass, "omothol");
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.error("Failed to get AbyssalCraft biomes", e);
        }

        loadConfig();
        registerStructures();
    }

    private void loadConfig() {
        Class<?> acConfigClass = ReflectionHelper.loadClass(AC_CONFIG_CLASS);
        if (acConfigClass == null) {
            SimpleStructureScanner.LOGGER.warn("Could not load AbyssalCraft config, using defaults");
            return;
        }

        shoggothLairSpawnRate = ReflectionHelper.readStaticIntField(acConfigClass,
            "shoggothLairSpawnRate", shoggothLairSpawnRate);
        shoggothLairSpawnRateRivers = ReflectionHelper.readStaticIntField(acConfigClass,
            "shoggothLairSpawnRateRivers", shoggothLairSpawnRateRivers);
        shoggothLairGenerationDistance = ReflectionHelper.readStaticIntField(acConfigClass,
            "shoggothLairGenerationDistance", shoggothLairGenerationDistance);
        graveyardGenerationChance = ReflectionHelper.readStaticIntField(acConfigClass,
            "graveyardGenerationChance", graveyardGenerationChance);
        graveyardGenerationDistance = ReflectionHelper.readStaticIntField(acConfigClass,
            "graveyardGenerationDistance", graveyardGenerationDistance);
    }

    private void registerStructures() {
        DimensionInfo abyssalWastelandDim = new DimensionInfo(abyssalWastelandId);
        DimensionInfo dreadlandsDim = new DimensionInfo(dreadlandsId);
        DimensionInfo omotholDim = new DimensionInfo(omotholId);

        Set<DimensionInfo> abyssalWasteland = Collections.singleton(abyssalWastelandDim);
        Set<DimensionInfo> dreadlands = Collections.singleton(dreadlandsDim);
        Set<DimensionInfo> omothol = Collections.singleton(omotholDim);
        Set<DimensionInfo> overworldAndOmothol = new HashSet<>();
        overworldAndOmothol.add(DimensionInfo.OVERWORLD);
        overworldAndOmothol.add(omotholDim);

        Set<Biome> wastelandsBiomes = wastelandsBiome == null ? null : Collections.singleton(wastelandsBiome);
        Set<Biome> omotholBiomes = omotholBiome == null ? null : Collections.singleton(omotholBiome);
        IBlockState omotholStone = getOmotholStone();

        // AbyStronghold - Abyssal Wasteland only (same generation rules as vanilla strongholds)
        MapGenerationBuilder abyssalStrongholdMap = null;
        if (wastelandsBiome != null) {
            abyssalStrongholdMap = MapGenerationBuilder.ofBuried(256, 256, STONE)
                .withName(MOD_ID + ":aby_stronghold")
                .build(createMapStructureStart(STRUCTURE_PACKAGE
                    + "abyss.stronghold.MapGenAbyStronghold$Start",
                    AbyssalCraftStructureProvider::hasStrongholdPortal), wastelandsBiome);
        }
        register("aby_stronghold", true)
            .fromMap(abyssalStrongholdMap)
            .withMetadata(wastelandsBiomes, abyssalWasteland)
            .oneInChunks(RarityTextHelper.averageChunksForFixedCountInRadius(128, 1472.0D));

        // Dreadlands Mineshaft
        MapGenerationBuilder dreadlandsMineshaftMap = null;
        if (dreadlandsBiome != null) {
            dreadlandsMineshaftMap = MapGenerationBuilder.ofBuriedWithFloor(256, 256, STONE)
                .withName(MOD_ID + ":dreadlands_mineshaft")
                .build(createMapStructureStart(STRUCTURE_PACKAGE
                    + "dreadlands.mineshaft.StructureDreadlandsMineStart"), dreadlandsBiome);
        }
        register("dreadlands_mineshaft")
            .fromMap(dreadlandsMineshaftMap)
            .withMetadata(null, dreadlands, RarityTextHelper.oneInChunks(250.0D));

        // J'zahar Temple - fixed position at origin (so, technically searchable lol)
        MapGenerationBuilder jzaharTempleMap = null;
        if (omotholStone != null && omotholBiome != null) {
            jzaharTempleMap = new MapGenerationBuilder(128, 128, 64, omotholStone)
                .withName(MOD_ID + ":jzahar_temple")
                .withOrigin(0, 52)
                .build(createMapGenerator(STRUCTURE_PACKAGE + "omothol.StructureJzaharTemple",
                    new BlockPos(4, 1, 7)), omotholBiome);
        }
        register("jzahar_temple", true)
            .fromMap(jzaharTempleMap)
            .withMetadata(omotholBiomes, omothol, Rarity.FIXED_POSITION);

        // Omothol City - randomly generated buildings with various loot
        MapGenerationBuilder omotholCityMap = null;
        if (omotholStone != null && omotholBiome != null) {
            omotholCityMap = new MapGenerationBuilder(128, 128, 64, omotholStone)
                .withName(MOD_ID + ":omothol_city")
                .build(createMapGenerator(STRUCTURE_PACKAGE + "omothol.StructureCity", 1), omotholBiome);
        }
        register("omothol_city")
            .fromMap(omotholCityMap)
            .withMetadata(omotholBiomes, omothol, calculateApproximateRarity(1.0D, 18.0D));

        // Omothol Storage - storage buildings with crates, generated separately from city buildings
        MapGenerationBuilder omotholStorageMap = null;
        if (omotholStone != null && omotholBiome != null) {
            omotholStorageMap = new MapGenerationBuilder(128, 128, 64, omotholStone)
                .withName(MOD_ID + ":omothol_storage")
                .build(createMapGenerator(STRUCTURE_PACKAGE + "omothol.StructureStorage", 1), omotholBiome);
        }
        register("omothol_storage")
            .fromMap(omotholStorageMap)
            .withMetadata(omotholBiomes, omothol, calculateApproximateRarity(2.0D, 300.0D));

        // Chagaroth Lair - constructed after the Dreadlands sealing lock is opened
        // The lair spans Z=-101 through Z=2 relative to the sealing lock
        MapGenerationBuilder chagarothLairMap = null;
        if (dreadlandsBiome != null) {
            chagarothLairMap = new MapGenerationBuilder(128, 128, 128, AIR)
                .withName(MOD_ID + ":chagaroth_lair")
                .withoutPlatform()
                .withOrigin(0, -60)
                .build(createMapGenerator(STRUCTURE_PACKAGE + "dreadlands.chagarothlair"), dreadlandsBiome);
        }
        register("chagaroth_lair")
            .fromMap(chagarothLairMap)
            .withEntities(new EntityEntry(MOD_ID + ":chagaroth", 1))
            .withMetadata(null, dreadlands);

        // Shoggoth Lairs spawn in SWAMP and RIVER biomes in the Overworld.
        // We do not calculate the Omothol rarity, because people usually need to find the first one in the Overworld
        Set<Biome> swampBiomes = hasAnyBiomes(BiomeDictionary.Type.SWAMP);
        Set<Biome> riverBiomes = hasBiomesBut(hasAnyBiomes(BiomeDictionary.Type.RIVER), BiomeDictionary.Type.OCEAN);
        riverBiomes.removeAll(swampBiomes);

        Set<Biome> shoggothBiomes = new HashSet<>(swampBiomes);
        shoggothBiomes.addAll(riverBiomes);
        if (omotholBiome != null) shoggothBiomes.add(omotholBiome);

        int swampBiomeCount = swampBiomes.size();
        int riverBiomeCount = riverBiomes.size();

        // older versions have no graveyard structure
        boolean hasGraveyardStructure = ReflectionHelper.loadClass(GRAVEYARD_CLASS) != null;

        MapGenerationBuilder shoggothLairMap = new MapGenerationBuilder(128, 128, 64, GRASS)
            .withName(MOD_ID + ":shoggoth_lair")
            .build(createMapGenerator(STRUCTURE_PACKAGE + "StructureShoggothPit", 1), Biomes.SWAMPLAND);
        register("shoggoth_lair")
            .fromMapWithBundledFallback(shoggothLairMap)
            .withMetadata(shoggothBiomes, overworldAndOmothol)
            .withRarity(calculateShoggothRarity(swampBiomeCount, riverBiomeCount));

        // Graveyards spawn in the Overworld and Omothol. Size 2 = big
        if (hasGraveyardStructure) {
            MapGenerationBuilder graveyardMap = new MapGenerationBuilder(128, 128, 64, GRASS)
                .withName(MOD_ID + ":graveyard")
                .build(createMapGenerator(GRAVEYARD_CLASS, new BlockPos(0, 1, 0),
                    (generator, random) -> generator.getClass().getMethod("setSize", int.class)
                        .invoke(generator, 2)), Biomes.PLAINS);
            register("graveyard")
                .fromMapWithBundledFallback(graveyardMap)
                .withMetadata(null, overworldAndOmothol, calculateGraveyardRarity());
        }
    }

    @Nullable
    private IBlockState getOmotholStone() {
        Block omotholStone = Block.getBlockFromName(MOD_ID + ":omotholstone");
        if (omotholStone != null) return omotholStone.getDefaultState();

        Class<?> acBlocksClass = ReflectionHelper.loadClass(AC_BLOCKS_CLASS);
        Object omotholStoneField = ReflectionHelper.getStaticFieldOrNull(acBlocksClass, "omothol_stone");
        if (omotholStoneField instanceof Block) return ((Block) omotholStoneField).getDefaultState();

        Object legacyStoneField = ReflectionHelper.getStaticFieldOrNull(acBlocksClass, "stone");
        if (legacyStoneField instanceof Block) return ((Block) legacyStoneField).getStateFromMeta(LEGACY_OMOTHOL_STONE_META);

        SimpleStructureScanner.LOGGER.error("Could not find AbyssalCraft Omothol stone");
        return null;
    }

    private static boolean hasStrongholdPortal(StructureStart structureStart)
            throws ReflectiveOperationException {
        if (structureStart.getComponents().isEmpty()) return false;

        Object stairs = structureStart.getComponents().get(0);
        return stairs.getClass().getField("strongholdPortalRoom").get(stairs) != null;
    }

    private LocalizedText calculateShoggothRarity(int swampBiomeCount, int riverBiomeCount) {
        double weightedProbability = 0.0D;
        int totalWeight = 0;

        if (swampBiomeCount > 0 && shoggothLairSpawnRate > 0) {
            weightedProbability += swampBiomeCount / calculateApproximateChunks(shoggothLairSpawnRate, shoggothLairGenerationDistance);
            totalWeight += swampBiomeCount;
        }

        if (riverBiomeCount > 0 && shoggothLairSpawnRateRivers > 0) {
            weightedProbability += riverBiomeCount / calculateApproximateChunks(shoggothLairSpawnRateRivers, shoggothLairGenerationDistance);
            totalWeight += riverBiomeCount;
        }

        if (totalWeight <= 0) return RarityTextHelper.oneInChunks(200.0D);

        return RarityTextHelper.oneInChunks(RarityTextHelper.chunksFromProbability(weightedProbability / totalWeight));
    }

    private LocalizedText calculateGraveyardRarity() {
        double weightedProbability = 0.0D;
        int totalWeight = 0;

        if (graveyardGenerationChance > 0) {
            weightedProbability += 1.0D / calculateApproximateChunks(graveyardGenerationChance, graveyardGenerationDistance);
            totalWeight++;
        }

        weightedProbability += 1.0D / calculateApproximateChunks(50.0D, graveyardGenerationDistance);
        totalWeight++;

        return RarityTextHelper.oneInChunks(RarityTextHelper.chunksFromProbability(weightedProbability / totalWeight));
    }

    private LocalizedText calculateApproximateRarity(double rawChunks, double minDistanceBlocks) {
        return RarityTextHelper.withMinimumSpacing(rawChunks, minDistanceBlocks);
    }

    private double calculateApproximateChunks(double rawChunks, double minDistanceBlocks) {
        return Math.max(rawChunks, RarityTextHelper.minimumSpacingChunks(minDistanceBlocks));
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        String path = structureId.getPath();
        Long seed = SeedHelper.getWorldSeed(world);
        if (seed == null) return null;

        List<BlockPos> candidates;

        switch (path) {
            case "aby_stronghold":
                if (world.provider.getDimension() != abyssalWastelandId) return null;
                candidates = getCachedAbyStrongholds(world, seed);
                break;

            case "jzahar_temple":
                if (world.provider.getDimension() != omotholId) return null;
                candidates = Collections.singletonList(getJzaharTemplePosition());
                break;

            default:
                return null;
        }

        if (candidates.isEmpty()) return null;

        // Make a copy for sorting
        candidates = new ArrayList<>(candidates);
        PositionHelper.sortByHorizontalDistance(candidates, pos);

        PositionHelper.FilteredPositionResult selection = PositionHelper.selectFilteredPosition(candidates, skipCount, locationFilter);
        if (selection == null) return null;

        BlockPos targetPos = selection.getPosition();
        boolean yAgnostic = targetPos.getY() == 0;

        return new StructureLocation(targetPos, skipCount, selection.getTotalMatches(), yAgnostic);
    }

    /**
     * Get cached AbyStronghold positions or calculate and cache them.
     */
    private List<BlockPos> getCachedAbyStrongholds(World world, long seed) {
        if (!abyStrongholdCache.containsKey(seed)) {
            abyStrongholdCache.put(seed, calculateAbyStrongholdPositions(world, seed));
        }

        return abyStrongholdCache.get(seed);
    }

    @Override
    @Nullable
    public List<BlockPos> findAllNearby(World world, ResourceLocation structureId, BlockPos pos, int maxResults) {
        String path = structureId.getPath();
        Long seed = SeedHelper.getWorldSeed(world);
        if (seed == null) return Collections.emptyList();

        List<BlockPos> results;

        switch (path) {
            case "aby_stronghold":
                if (world.provider.getDimension() != abyssalWastelandId) return Collections.emptyList();
                results = new ArrayList<>(getCachedAbyStrongholds(world, seed));
                PositionHelper.sortByHorizontalDistance(results, pos);
                return results.subList(0, Math.min(maxResults, results.size()));

            case "jzahar_temple":
                if (world.provider.getDimension() != omotholId) return Collections.emptyList();
                return Collections.singletonList(getJzaharTemplePosition());

            default:
                return Collections.emptyList();
        }
    }

    // ========== AbyStronghold Algorithm ==========
    // Based on MapGenAbyStronghold.checkBiomes()

    /**
     * Calculate AbyStronghold positions using MapGenAbyStronghold's algorithm.
     * <p>
     * From checkBiomes():
     * - 128 strongholds total
     * - Uses spiral pattern with increasing distance
     * - field_82671_h = 32.0 (base distance multiplier)
     * - field_82672_i = 3 (initial spread, increases with rings)
     */
    private List<BlockPos> calculateAbyStrongholdPositions(World world, long seed) {
        List<BlockPos> positions = new ArrayList<>();
        BiomeProvider biomeProvider = world.getBiomeProvider();

        Random random = new Random();
        random.setSeed(seed);

        double angle = random.nextDouble() * Math.PI * 2.0;
        int ringIndex = 0;
        int posInRing = 0;
        int spread = 3;  // field_82672_i initial value
        double distanceMultiplier = 32.0;  // field_82671_h

        for (int i = 0; i < 128; i++) {
            // Distance formula from MapGenAbyStronghold:
            // d0 = 4.0 * distMult + distMult * ringIndex * 6.0 + (random - 0.5) * distMult * 2.5
            double d0 = 4.0 * distanceMultiplier
                + distanceMultiplier * ringIndex * 6.0
                + (random.nextDouble() - 0.5) * distanceMultiplier * 2.5;

            int chunkX = (int) Math.round(Math.cos(angle) * d0);
            int chunkZ = (int) Math.round(Math.sin(angle) * d0);

            // Try to find valid biome position (within 112 blocks)
            BlockPos biomePos = biomeProvider.findBiomePosition(
                (chunkX << 4) + 8, (chunkZ << 4) + 8, 112,
                Collections.singletonList(wastelandsBiome), random);

            if (biomePos != null) {
                chunkX = biomePos.getX() >> 4;
                chunkZ = biomePos.getZ() >> 4;
            }

            // Convert to block coordinates
            int blockX = (chunkX << 4) + 8;
            int blockZ = (chunkZ << 4) + 8;

            positions.add(new BlockPos(blockX, 0, blockZ));

            // Advance angle
            angle += Math.PI * 2.0 / spread;
            posInRing++;

            if (posInRing >= spread) {
                ringIndex++;
                posInRing = 0;
                spread += 2 * spread / (ringIndex + 1);
                spread = Math.min(spread, 128 - i);
                angle += random.nextDouble() * Math.PI * 2.0;
            }
        }

        return positions;
    }

    // ========== J'zahar Temple ==========

    /**
     * Get the J'zahar Temple position.
     * From ChunkGeneratorOmothol.populate(): spawns at chunk (0,0) at coordinates (4, height, 7).
     * Returns Y=0 to avoid loading chunks; the location will be marked as y-agnostic.
     */
    private BlockPos getJzaharTemplePosition() {
        // Fixed position at world origin in Omothol
        return new BlockPos(4, 0, 7);
    }

}
