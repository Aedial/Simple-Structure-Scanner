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

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraftforge.common.BiomeDictionary;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.providers.AbstractStructureProvider;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureLocation;
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

    private static final String CRATE_KEY = "gui.structurescanner.loot.abyssalcraft.crate";

    // Cache: seed -> list of AbyStronghold positions
    private static final Map<Long, List<BlockPos>> abyStrongholdCache = new HashMap<>();

    // AbyssalCraft dimension IDs (fetched at runtime)
    private int abyssalWastelandId = -1;
    private int dreadlandsId = -1;
    private int omotholId = -1;

    // AbyssalCraft biomes (fetched at runtime)
    private Biome wastelandsBiome;
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
            omotholBiome = (Biome) ReflectionHelper.getStaticField(acBiomesClass, "omothol");
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.error("Failed to get AbyssalCraft biomes", e);
        }

        loadConfig();
        registerStructures();
    }

    private void loadConfig() {
        try {
            Class<?> acConfigClass = ReflectionHelper.loadClassRequired("com.shinoow.abyssalcraft.lib.ACConfig");
            shoggothLairSpawnRate = ReflectionHelper.getStaticIntField(acConfigClass, "shoggothLairSpawnRate");
            shoggothLairSpawnRateRivers = ReflectionHelper.getStaticIntField(acConfigClass, "shoggothLairSpawnRateRivers");
            shoggothLairGenerationDistance = ReflectionHelper.getStaticIntField(acConfigClass, "shoggothLairGenerationDistance");
            graveyardGenerationChance = ReflectionHelper.getStaticIntField(acConfigClass, "graveyardGenerationChance");
            graveyardGenerationDistance = ReflectionHelper.getStaticIntField(acConfigClass, "graveyardGenerationDistance");
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Could not load AbyssalCraft config, using defaults: {}", e.getMessage());
        }
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

        // AbyStronghold - Abyssal Wasteland only
        register("aby_stronghold")
            .fromBundled()
            .withFallbackLootTables(
                new LootEntry("abyssalcraft:chests/stronghold_corridor", CHEST_KEY),
                new LootEntry("abyssalcraft:chests/stronghold_crossing", CHEST_KEY))
            .withFallbackEntities(new EntityEntry("abyssalcraft:abyssalzombie", 1, true))
            .withMetadata(wastelandsBiomes, abyssalWasteland)
            .oneInChunks(RarityTextHelper.averageChunksForFixedCountInRadius(128, 1472.0D));

        // Dreadlands Mineshaft
        register("dreadlands_mineshaft")
            .fromBundled()
            .withFallbackLootTables(
                new LootEntry("abyssalcraft:chests/mineshaft", MINECART_CHEST_KEY))
            .withMetadata(null, dreadlands, RarityTextHelper.oneInChunks(250.0D));

        // J'zahar Temple - fixed position at origin
        register("jzahar_temple")
            .fromBundled()
            .withFallbackEntities(
                new EntityEntry("abyssalcraft:jzahar", 1),
                new EntityEntry("abyssalcraft:jzaharminion", 3))
            .withMetadata(omotholBiomes, omothol, Rarity.FIXED_POSITION);

        // Omothol City - randomly generated buildings with various loot
        register("omothol_city")
            .fromBundled()
            .withFallbackLootTables(
                new LootEntry("abyssalcraft:chests/omothol/blacksmith", CHEST_KEY),
                new LootEntry("abyssalcraft:chests/omothol/house", CHEST_KEY),
                new LootEntry("abyssalcraft:chests/omothol/library", CHEST_KEY),
                new LootEntry("abyssalcraft:chests/omothol/farmhouse", CHEST_KEY))
            .withFallbackEntities(new EntityEntry("abyssalcraft:remnant", 10))
            .withMetadata(omotholBiomes, omothol, calculateApproximateRarity(1.0D, 18.0D));

        // Omothol Storage - storage buildings with crates
        register("omothol_storage")
            .fromBundled()
            .withFallbackLootTables(
                new LootEntry("abyssalcraft:chests/omothol/storage_junk", CRATE_KEY),
                new LootEntry("abyssalcraft:chests/omothol/storage_treasure", CRATE_KEY))
            .withFallbackEntities(new EntityEntry("abyssalcraft:shoggoth", 1))
            .withMetadata(omotholBiomes, omothol, calculateApproximateRarity(2.0D, 300.0D));

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

        register("shoggoth_lair")
            .fromBundled()
            .withFallbackEntities(new EntityEntry("abyssalcraft:shoggoth", 1))
            .withMetadata(shoggothBiomes, overworldAndOmothol)
            .withRarity(calculateShoggothRarity(swampBiomeCount, riverBiomeCount));

        // Graveyards spawn in the Overworld and Omothol.
        register("graveyard")
            .fromBundled()
            .withMetadata(null, overworldAndOmothol, calculateGraveyardRarity());
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
    public boolean canBeSearched(ResourceLocation structureId) {
        String path = structureId.getPath();

        // Only deterministic structures can be searched
        return path.equals("aby_stronghold") || path.equals("jzahar_temple");
    }
    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        if (world == null || !canBeSearched(structureId)) return null;

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
        if (world == null || !canBeSearched(structureId)) return Collections.emptyList();

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
