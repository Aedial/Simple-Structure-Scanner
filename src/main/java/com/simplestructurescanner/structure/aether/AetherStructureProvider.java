package com.simplestructurescanner.structure.aether;

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
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureProvider;


/**
 * Structure provider for The Aether mod's dungeons.
 * Uses seed-based algorithms to locate Silver and Gold dungeons.
 * Bronze dungeons are not searchable as they depend on terrain.
 */
public class AetherStructureProvider implements StructureProvider {
    private static final String PROVIDER_ID = "aether_legacy";
    private static final String MOD_ID = "aether_legacy";

    private List<ResourceLocation> knownStructures;
    private Map<ResourceLocation, StructureInfo> structureInfos = new HashMap<>();

    // Cache: seed -> list of dungeon positions
    private static final Map<Long, List<BlockPos>> silverDungeonCache = new HashMap<>();
    private static final Map<Long, List<BlockPos>> goldDungeonCache = new HashMap<>();

    // Default config values from AetherConfig (we don't hardcode dependency on config class)
    private static final int SILVER_GRID_SIZE = 6;
    private static final int GOLD_GRID_SIZE = 10;
    private static final int DEFAULT_SILVER_PRIMARY_CHANCE = 110;
    private static final int DEFAULT_SILVER_SECONDARY_CHANCE = 150;
    private static final int DEFAULT_GOLD_PRIMARY_CHANCE = 140;
    private static final int DEFAULT_GOLD_SECONDARY_CHANCE = 170;

    // Aether dimension ID (default is 4, but may be configured)
    private int aetherDimensionId = 4;

    // Dynamically loaded config values
    private int silverPrimaryChance = DEFAULT_SILVER_PRIMARY_CHANCE;
    private int silverSecondaryChance = DEFAULT_SILVER_SECONDARY_CHANCE;
    private int goldPrimaryChance = DEFAULT_GOLD_PRIMARY_CHANCE;
    private int goldSecondaryChance = DEFAULT_GOLD_SECONDARY_CHANCE;

    public AetherStructureProvider() {
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getModName() {
        return I18n.translateToLocal("gui.structurescanner.provider.aether");
    }

    @Override
    public boolean isAvailable() {
        return Loader.isModLoaded(MOD_ID);
    }

    @Override
    public void postInit() {
        knownStructures = new ArrayList<>();

        // Load config values via reflection to avoid hard dependency
        loadAetherConfig();

        // Silver Dungeon (Valkyrie Queen)
        addStructure("silver_dungeon", "gui.structurescanner.structures.aether.silver_dungeon", 81, 40, 31);

        // Gold Dungeon (Sun Spirit)
        addStructure("gold_dungeon", "gui.structurescanner.structures.aether.gold_dungeon", 0, 0, 0);

        // Bronze Dungeon (Slider) - not searchable due to terrain dependency
        addStructure("bronze_dungeon", "gui.structurescanner.structures.aether.bronze_dungeon", 16, 12, 16);

        // Add metadata
        populateStructureMetadata();

        // Add content info (loot, entities)
        populateStructureContents();
    }

    /**
     * Load Aether config values via reflection to avoid compile-time dependency.
     */
    private void loadAetherConfig() {
        try {
            Class<?> configClass = Class.forName("com.gildedgames.the_aether.AetherConfig");

            // Get world_gen instance
            Object worldGen = configClass.getField("world_gen").get(null);
            Class<?> worldGenClass = worldGen.getClass();

            silverPrimaryChance = worldGenClass.getField("silver_dungeon_primary_chance").getInt(worldGen);
            silverSecondaryChance = worldGenClass.getField("silver_dungeon_secondary_chance").getInt(worldGen);
            goldPrimaryChance = worldGenClass.getField("golden_dungeon_primary_chance").getInt(worldGen);
            goldSecondaryChance = worldGenClass.getField("golden_dungeon_secondary_chance").getInt(worldGen);

            // Get dimension config
            Object dimension = configClass.getField("dimension").get(null);
            aetherDimensionId = dimension.getClass().getField("aether_dimension_id").getInt(dimension);

            SimpleStructureScanner.LOGGER.debug("Loaded Aether config: silver={}/{}, gold={}/{}, dim={}",
                silverPrimaryChance, silverSecondaryChance, goldPrimaryChance, goldSecondaryChance, aetherDimensionId);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Could not load Aether config, using defaults: {}", e.getMessage());
        }
    }

    private void addStructure(String path, String displayNameKey, int sizeX, int sizeY, int sizeZ) {
        ResourceLocation id = new ResourceLocation(MOD_ID, path);
        knownStructures.add(id);

        String name = I18n.translateToLocal(displayNameKey);
        StructureInfo info = new StructureInfo(id, name, PROVIDER_ID, sizeX, sizeY, sizeZ);
        structureInfos.put(id, info);
    }

    private void populateStructureMetadata() {
        Set<DimensionInfo> aetherDim = Collections.singleton(new DimensionInfo(aetherDimensionId, "gui.structurescanner.dimension.aether"));

        // Calculate rarity strings based on spawn chance
        // Silver: 6x6 grid, with chance checks
        // Probability = (1/primary + (1 - 1/primary) * 1/secondary) per grid cell
        String silverRarity = calculateRarityString(SILVER_GRID_SIZE, silverPrimaryChance, silverSecondaryChance);
        setMetadata("silver_dungeon", null, aetherDim, silverRarity);

        // Gold: 10x10 grid, with chance checks
        String goldRarity = calculateRarityString(GOLD_GRID_SIZE, goldPrimaryChance, goldSecondaryChance);
        setMetadata("gold_dungeon", null, aetherDim, goldRarity);

        // Bronze: common but not searchable
        setMetadata("bronze_dungeon", null, aetherDim, "gui.structurescanner.rarity.common");
    }

    /**
     * Calculate a human-readable rarity string like "1 in 1000 chunks".
     * The dungeon spawns on a grid (every N chunks aligned), with two-stage random check.
     */
    private String calculateRarityString(int gridSize, int primaryChance, int secondaryChance) {
        // Probability of passing random check:
        // P(spawn) = P(primary=0) + P(primary!=0) * P(secondary=0)
        // P(spawn) = 1/primary + (1 - 1/primary) * 1/secondary
        double pPrimary = 1.0 / primaryChance;
        double pSecondary = 1.0 / secondaryChance;
        double pSpawn = pPrimary + (1 - pPrimary) * pSecondary;

        // Grid cell size (chunks per potential spawn)
        int chunksPerCell = gridSize * gridSize;

        // Average chunks per dungeon
        double chunksPerDungeon = chunksPerCell / pSpawn;

        // Format nicely
        long rounded = Math.round(chunksPerDungeon);
        String rarity = I18n.translateToLocalFormatted("gui.structurescanner.rarity.one_in_chunks", rounded);
    
        return I18n.translateToLocalFormatted("gui.structurescanner.rarity", rarity);
    }

    private void setMetadata(String path, Set<?> biomes, Set<DimensionInfo> dimensions, String rarity) {
        StructureInfo info = structureInfos.get(new ResourceLocation(MOD_ID, path));
        if (info == null) return;

        info.setValidDimensions(dimensions);
        info.setRarity(rarity);
    }

    private void populateStructureContents() {
        populateSilverDungeon();
        populateGoldDungeon();
        populateBronzeDungeon();
    }

    private void populateSilverDungeon() {
        StructureInfo info = structureInfos.get(new ResourceLocation(MOD_ID, "silver_dungeon"));
        if (info == null) return;

        List<LootEntry> loot = new ArrayList<>();
        loot.add(createLootEntry("aether_legacy:chests/silver_dungeon_chest", "gui.structurescanner.loot.chest"));
        loot.add(createLootEntry("aether_legacy:chests/silver_dungeon_reward", "gui.structurescanner.loot.aether.reward"));
        info.setLootTables(loot);

        List<EntityEntry> entities = new ArrayList<>();
        entities.add(new EntityEntry(new ResourceLocation("aether_legacy", "valkyrie_queen"), 1, false));
        entities.add(new EntityEntry(new ResourceLocation("aether_legacy", "valkyrie"), 3, false));
        entities.add(new EntityEntry(new ResourceLocation("aether_legacy", "mimic"), 3, false));
        info.setEntities(entities);
    }

    private void populateGoldDungeon() {
        StructureInfo info = structureInfos.get(new ResourceLocation(MOD_ID, "gold_dungeon"));
        if (info == null) return;

        List<LootEntry> loot = new ArrayList<>();
        loot.add(createLootEntry("aether_legacy:chests/gold_dungeon_reward", "gui.structurescanner.loot.aether.reward"));
        info.setLootTables(loot);

        List<EntityEntry> entities = new ArrayList<>();
        entities.add(new EntityEntry(new ResourceLocation("aether_legacy", "sun_spirit"), 1, false));
        info.setEntities(entities);
    }

    private void populateBronzeDungeon() {
        StructureInfo info = structureInfos.get(new ResourceLocation(MOD_ID, "bronze_dungeon"));
        if (info == null) return;

        List<LootEntry> loot = new ArrayList<>();
        loot.add(createLootEntry("aether_legacy:chests/bronze_dungeon_chest", "gui.structurescanner.loot.chest"));
        loot.add(createLootEntry("aether_legacy:chests/bronze_dungeon_reward", "gui.structurescanner.loot.aether.reward"));
        info.setLootTables(loot);

        List<EntityEntry> entities = new ArrayList<>();
        entities.add(new EntityEntry(new ResourceLocation("aether_legacy", "slider"), 1, false));
        entities.add(new EntityEntry(new ResourceLocation("aether_legacy", "mimic"), 3, false));
        info.setEntities(entities);
    }

    private LootEntry createLootEntry(String lootTable, String displayNameKey) {
        return new LootEntry(new ResourceLocation(lootTable), Collections.emptyList(), I18n.translateToLocal(displayNameKey));
    }

    @Override
    public List<ResourceLocation> getStructureIds() {
        return new ArrayList<>(knownStructures);
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        if (!knownStructures.contains(structureId)) return false;

        // Bronze dungeon can technically be searched, but it's terrain-dependent so very annoying
        return !structureId.getPath().equals("bronze_dungeon");
    }

    @Override
    @Nullable
    public StructureInfo getStructureInfo(ResourceLocation structureId) {
        return structureInfos.get(structureId);
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        if (world == null || !canBeSearched(structureId)) return null;
        if (world.provider.getDimension() != aetherDimensionId) return null;

        Long seed = getWorldSeed(world);
        if (seed == null) {
            SimpleStructureScanner.LOGGER.warn("Could not get world seed for Aether structure search");
            return null;
        }

        String path = structureId.getPath();
        List<BlockPos> candidates = getCachedDungeons(path, pos, seed);
        if (candidates.isEmpty()) return null;

        // Make a copy for sorting
        candidates = new ArrayList<>(candidates);
        sortByDistance(candidates, pos);

        // Apply filter and skip to find the target
        int validIndex = 0;
        int totalValid = 0;
        BlockPos targetPos = null;

        for (BlockPos candidate : candidates) {
            if (locationFilter != null && !locationFilter.test(candidate)) continue;

            if (validIndex == skipCount && targetPos == null) targetPos = candidate;

            validIndex++;
            totalValid++;
        }

        if (targetPos == null) return null;

        // Calculate deterministic Y offset for the structure
        int yOffset = calculateStructureYOffset(path, seed, targetPos.getX() >> 4, targetPos.getZ() >> 4);
        if (yOffset >= 0) {
            targetPos = new BlockPos(targetPos.getX(), yOffset, targetPos.getZ());
            return new StructureLocation(targetPos, skipCount, totalValid, false);
        }

        // Fallback: Y is unknown, mark as y-agnostic
        return new StructureLocation(targetPos, skipCount, totalValid, true);
    }

    @Override
    public List<BlockPos> findAllNearby(World world, ResourceLocation structureId, BlockPos pos, int maxResults) {
        if (world == null || !canBeSearched(structureId)) return Collections.emptyList();
        if (world.provider.getDimension() != aetherDimensionId) return Collections.emptyList();

        Long seed = getWorldSeed(world);
        if (seed == null) return Collections.emptyList();

        String path = structureId.getPath();
        List<BlockPos> results = new ArrayList<>(getCachedDungeons(path, pos, seed));
        sortByDistance(results, pos);
        results = results.subList(0, Math.min(maxResults, results.size()));

        // Add Y coordinates to the results
        List<BlockPos> resultsWithY = new ArrayList<>(results.size());
        for (BlockPos structurePos : results) {
            int chunkX = structurePos.getX() >> 4;
            int chunkZ = structurePos.getZ() >> 4;
            int yOffset = calculateStructureYOffset(path, seed, chunkX, chunkZ);

            if (yOffset >= 0) {
                resultsWithY.add(new BlockPos(structurePos.getX(), yOffset, structurePos.getZ()));
            } else {
                resultsWithY.add(structurePos);
            }
        }

        return resultsWithY;
    }

    private void sortByDistance(List<BlockPos> positions, BlockPos from) {
        final int px = from.getX();
        final int pz = from.getZ();

        positions.sort((a, b) -> {
            long dxA = a.getX() - px;
            long dzA = a.getZ() - pz;
            long dxB = b.getX() - px;
            long dzB = b.getZ() - pz;
            long distA = dxA * dxA + dzA * dzA;
            long distB = dxB * dxB + dzB * dzB;
            return Long.compare(distA, distB);
        });
    }

    private Long getWorldSeed(World world) {
        if (world.getWorldInfo() != null) return world.getWorldInfo().getSeed();
        return null;
    }

    // Structure offsets from setStructureOffset() in each component class
    // Silver: setStructureOffset(31, 24, 30) with bounding box starting at (chunkX<<4)+2
    private static final int SILVER_OFFSET_X = 2 + 31;  // 33
    private static final int SILVER_OFFSET_Y = 24;
    private static final int SILVER_OFFSET_Z = 2 + 30;  // 32
    // Gold: setStructureOffset(60, 0, 60) with bounding box starting at (chunkX<<4)+2
    private static final int GOLD_OFFSET_X = 2 + 60;    // 62
    private static final int GOLD_OFFSET_Y = 0;
    private static final int GOLD_OFFSET_Z = 2 + 60;    // 62

    /**
     * Get cached dungeon positions or calculate and cache them.
     */
    private List<BlockPos> getCachedDungeons(String structureType, BlockPos pos, long seed) {
        switch (structureType) {
            case "silver_dungeon":
                if (!silverDungeonCache.containsKey(seed)) {
                    silverDungeonCache.put(seed, calculateAllDungeons(seed, SILVER_GRID_SIZE,
                        silverPrimaryChance, silverSecondaryChance, SILVER_OFFSET_X, SILVER_OFFSET_Z));
                }
                return silverDungeonCache.get(seed);

            case "gold_dungeon":
                if (!goldDungeonCache.containsKey(seed)) {
                    goldDungeonCache.put(seed, calculateAllDungeons(seed, GOLD_GRID_SIZE,
                        goldPrimaryChance, goldSecondaryChance, GOLD_OFFSET_X, GOLD_OFFSET_Z));
                }
                return goldDungeonCache.get(seed);

            default:
                return Collections.emptyList();
        }
    }

    /**
     * Calculate all dungeon positions for a given seed and cache them.
     * Uses the same algorithm as Aether's MapGenSilverDungeon/MapGenGoldenDungeon.
     *
     * @param offsetX X offset from chunk origin to structure start (bounding box offset + structure offset)
     * @param offsetZ Z offset from chunk origin to structure start (bounding box offset + structure offset)
     */
    private List<BlockPos> calculateAllDungeons(long seed, int gridSize, int primaryChance, int secondaryChance,
            int offsetX, int offsetZ) {
        List<BlockPos> results = new ArrayList<>();

        // Search a large area (200 chunks in each direction from origin)
        int searchRadius = 200;

        for (int chunkX = -searchRadius; chunkX <= searchRadius; chunkX++) {
            for (int chunkZ = -searchRadius; chunkZ <= searchRadius; chunkZ++) {
                if (canSpawnStructureAtCoords(seed, chunkX, chunkZ, gridSize, primaryChance, secondaryChance)) {
                    BlockPos structurePos = new BlockPos((chunkX << 4) + offsetX, 0, (chunkZ << 4) + offsetZ);
                    results.add(structurePos);
                }
            }
        }

        return results;
    }

    /**
     * Replicate the random check from MapGenSilverDungeon/MapGenGoldenDungeon.
     *
     * The Aether's canSpawnStructureAtCoords does:
     * 1. Random check (primary, then secondary if primary fails)
     * 2. Grid alignment check (chunkX % gridSize == 0 && chunkZ % gridSize == 0)
     * Both must pass for the structure to spawn.
     *
     * The seeding formula uses XOR between products:
     *   rand.setSeed(worldSeed);
     *   long i = rand.nextLong();
     *   long j = rand.nextLong();
     *   rand.setSeed((chunkX * i) ^ (chunkZ * j) ^ worldSeed);
     *
     * Additionally, there's 1 random call consumed between setSeed and the spawn
     * check (likely from MapGenStructure internals or during structure lookup).
     */
    private boolean canSpawnStructureAtCoords(long seed, int chunkX, int chunkZ, int gridSize,
            int primaryChance, int secondaryChance) {

        // Seed using XOR formula
        Random rand = new Random(seed);
        long i = rand.nextLong();
        long j = rand.nextLong();
        rand.setSeed(((long) chunkX * i) ^ ((long) chunkZ * j) ^ seed);

        // Skip 1 random call - this happens between setSeed and canSpawnStructureAtCoords
        // in the Aether's MapGenStructure flow. Verified through instrumentation.
        rand.nextInt(1);

        // Replicate RandomTracker.testRandom behavior
        int primary = testRandom(rand, primaryChance, -1);
        boolean randomPassed = (primary == 0);

        if (!randomPassed) {
            int secondary = testRandom(rand, secondaryChance, primary);
            randomPassed = (secondary == 0);
        }

        if (!randomPassed) return false;

        // Grid alignment check (as in Aether's canSpawnStructureAtCoords)
        return chunkX % gridSize == 0 && chunkZ % gridSize == 0;
    }

    /**
     * Replicate RandomTracker.testRandom behavior.
     * NOTE: The Aether's implementation has a bug where the recursive call's
     * return value is ignored, causing it to return -1 when result == lastRand.
     * We must replicate this bug for accuracy.
     */
    private int testRandom(Random random, int bound, int lastRand) {
        int result = random.nextInt(bound);

        if (result != lastRand) return result;

        // Aether's bug: recursive call doesn't return its result
        testRandom(random, bound, result);

        return -1;
    }

    /**
     * Calculate the deterministic Y offset for a structure.
     * Replicates the random sequence from StructureStart.create().
     * @return Y offset, or -1 if cannot be calculated
     */
    private int calculateStructureYOffset(String structureType, long seed, int chunkX, int chunkZ) {
        switch (structureType) {
            case "silver_dungeon":
                return calculateSilverDungeonY(seed, chunkX, chunkZ);
            case "gold_dungeon":
                return calculateGoldDungeonY(seed, chunkX, chunkZ);
            default:
                return -1;
        }
    }

    /**
     * Calculate Silver Dungeon Y offset.
     * Replicates MapGenSilverDungeon.Start.create() random sequence.
     *
     * The bounding box base Y is 80 (set in ComponentSilverDungeon constructor),
     * then offset by random.nextInt(64). The structure offset adds another 24
     * (from setStructureOffset(31, 24, 30)), giving the actual floor position.
     */
    private int calculateSilverDungeonY(long seed, int chunkX, int chunkZ) {
        Random random = new Random();

        // Seed exactly as StructureStart.create() does
        random.setSeed(seed);
        long i = random.nextLong();
        long j = random.nextLong();
        random.setSeed((long) chunkX * i ^ (long) chunkZ * j ^ seed);

        // Skip the 5 random calls before Y offset
        random.nextInt(3);  // firstStaircaseZ
        random.nextInt(3);  // secondStaircaseZ
        random.nextInt(3);  // finalStaircaseZ
        random.nextInt(3);  // xTendency (nextInt(3) - 1)
        random.nextInt(3);  // zTendency (nextInt(3) - 1)

        // Base Y 80 + random offset + structure Y offset (24)
        return 80 + random.nextInt(64) + SILVER_OFFSET_Y;
    }

    /**
     * Calculate Gold Dungeon Y offset.
     * Replicates MapGenGoldenDungeon.Start.create() random sequence.
     *
     * The bounding box base Y is 80 (set in ComponentGoldenDungeon constructor),
     * then offset by random.nextInt(64) in customOffset(). Structure offset Y is 0.
     */
    private int calculateGoldDungeonY(long seed, int chunkX, int chunkZ) {
        Random random = new Random();

        // Seed exactly as StructureStart.create() does
        random.setSeed(seed);
        long i = random.nextLong();
        long j = random.nextLong();
        random.setSeed((long) chunkX * i ^ (long) chunkZ * j ^ seed);

        // dungeonDirection
        random.nextInt(4);

        int stubIslandCount = 8 + random.nextInt(5);

        // For each stub island, 3 float calls
        for (int stub = 0; stub < stubIslandCount; stub++) {
            random.nextFloat();  // f2
            random.nextFloat();  // f3
            random.nextFloat();  // k5 calculation
        }

        // Base Y 80 + random offset + structure Y offset (0)
        return 80 + random.nextInt(64) + GOLD_OFFSET_Y;
    }
}
