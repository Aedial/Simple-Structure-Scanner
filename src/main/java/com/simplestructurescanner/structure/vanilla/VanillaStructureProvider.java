package com.simplestructurescanner.structure.vanilla;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.ChunkGeneratorEnd;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.BiomeManager;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.AbstractStructureProvider;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.TerrainHeightCalculator;
import com.simplestructurescanner.structure.pillar.ValidationContextManager;
import com.simplestructurescanner.structure.util.PositionHelper;
import com.simplestructurescanner.structure.util.RarityTextHelper;
import com.simplestructurescanner.structure.util.SeedHelper;


/**
 * Structure provider for vanilla Minecraft structures.
 * Uses seed-based algorithms to locate structures.
 */
public class VanillaStructureProvider extends AbstractStructureProvider {
    private static final String PROVIDER_ID = "minecraft";
    private static final String MOD_NAME = "gui.structurescanner.provider.minecraft";
    private static final double MINESHAFT_CHUNKS = 250.0D;
    private static final double STRONGHOLD_OUTER_RING_RADIUS_CHUNKS = 1472.0D;
    private static final int STRONGHOLD_COUNT = 128;
    private static final double END_SHIP_END_CITY_PROBABILITY = 0.57122D;
    private static final List<Biome> MONUMENT_WATER_BIOMES = Arrays.asList(
        Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.RIVER, Biomes.FROZEN_OCEAN, Biomes.FROZEN_RIVER
    );
    private static final List<Biome> MONUMENT_SPAWN_BIOMES = Collections.singletonList(Biomes.DEEP_OCEAN);
    private static final List<Biome> MANSION_BIOMES = Arrays.asList(
        Biomes.ROOFED_FOREST, Biomes.MUTATED_ROOFED_FOREST
    );

    // Cache: generation world -> (structureType -> list of positions)
    // Using the resolved generation world keeps dimensions isolated and avoids client/server seed mismatches.
    private static final Map<World, Map<String, List<BlockPos>>> positionCache = new WeakHashMap<>();
    private static final int MAX_CACHED_POSITIONS = 200;

    public VanillaStructureProvider() {
        super(PROVIDER_ID, "minecraft", MOD_NAME);
    }

    @Override
    public void postInit() {
        resetStructures();

        // Overworld
        registerStructure("village", "gui.structurescanner.structures.minecraft.village", 0, 0, 0);
        registerStructure("mineshaft", "gui.structurescanner.structures.minecraft.mineshaft", 0, 0, 0);
        registerStructure("stronghold", "gui.structurescanner.structures.minecraft.stronghold", 0, 0, 0);
        registerStructure("desert_temple", "gui.structurescanner.structures.minecraft.desert_temple", 21, 21, 21);
        registerStructure("jungle_temple", "gui.structurescanner.structures.minecraft.jungle_temple", 12, 14, 15);
        registerStructure("witch_hut", "gui.structurescanner.structures.minecraft.witch_hut", 7, 5, 9);
        registerStructure("igloo", "gui.structurescanner.structures.minecraft.igloo", 7, 5, 8);
        registerStructure("monument", "gui.structurescanner.structures.minecraft.ocean_monument", 58, 23, 58);
        registerStructure("mansion", "gui.structurescanner.structures.minecraft.woodland_mansion", 0, 0, 0);
        registerStructure("dungeon", "gui.structurescanner.structures.minecraft.dungeon", 9, 7, 9);
        // Nether
        registerStructure("fortress", "gui.structurescanner.structures.minecraft.nether_fortress", 0, 0, 0);

        // End
        registerStructure("endcity", "gui.structurescanner.structures.minecraft.end_city", 0, 0, 0);
        registerStructure("end_ship", "gui.structurescanner.structures.minecraft.end_ship", 0, 0, 0);

        // Add blocks and loot tables to structures
        populateStructureContents();

        // Add biome, dimension, and rarity data
        populateStructureMetadata();
    }

    /**
     * Populates biome, dimension, and rarity info for vanilla structures.
     */
    private void populateStructureMetadata() {
        // Dimension sets using DimensionInfo
        Set<DimensionInfo> overworld = Collections.singleton(DimensionInfo.OVERWORLD);
        Set<DimensionInfo> nether = Collections.singleton(DimensionInfo.NETHER);
        Set<DimensionInfo> end = Collections.singleton(DimensionInfo.END);

        // Village - Plains, Desert, Savanna, Taiga
        setMetadata("village", biomes(Biomes.PLAINS, Biomes.DESERT, Biomes.SAVANNA, Biomes.TAIGA), overworld,
            RarityTextHelper.oneInChunks(1024));

        // Mineshaft - any biome underground
        setMetadata("mineshaft", null, overworld, RarityTextHelper.oneInChunks(MINESHAFT_CHUNKS));

        // Stronghold - fixed ring placement with 128 total structures
        setMetadata("stronghold", null, overworld,
            RarityTextHelper.oneInChunks(
                RarityTextHelper.averageChunksForFixedCountInRadius(STRONGHOLD_COUNT, STRONGHOLD_OUTER_RING_RADIUS_CHUNKS)
            ));

        // Desert Temple - Desert, Desert Hills
        setMetadata("desert_temple", biomes(Biomes.DESERT, Biomes.DESERT_HILLS), overworld,
            RarityTextHelper.oneInChunks(1024));

        // Jungle Temple - Jungle, Jungle Hills
        setMetadata("jungle_temple", biomes(Biomes.JUNGLE, Biomes.JUNGLE_HILLS), overworld,
            RarityTextHelper.oneInChunks(1024));

        // Witch Hut - Swamp
        setMetadata("witch_hut", biomes(Biomes.SWAMPLAND), overworld, RarityTextHelper.oneInChunks(1024));

        // Igloo - Snowy biomes
        setMetadata("igloo", biomes(Biomes.ICE_PLAINS, Biomes.COLD_TAIGA), overworld,
            RarityTextHelper.oneInChunks(1024));

        // Ocean Monument - Deep Ocean
        setMetadata("monument", biomes(Biomes.DEEP_OCEAN), overworld, RarityTextHelper.oneInChunks(1024));

        // Woodland Mansion - Roofed Forest
        setMetadata("mansion", biomes(Biomes.ROOFED_FOREST, Biomes.MUTATED_ROOFED_FOREST), overworld,
            RarityTextHelper.oneInChunks(6400));

        // Dungeon - any biome underground
        setMetadata("dungeon", null, overworld, "gui.structurescanner.rarity.common");

        // Nether Fortress
        setMetadata("fortress", null, nether, RarityTextHelper.oneInChunks(768));

        // End City & End Ship
        setMetadata("endcity", null, end, RarityTextHelper.oneInChunks(400));
        setMetadata("end_ship", null, end,
            RarityTextHelper.oneInChunks(400.0D / END_SHIP_END_CITY_PROBABILITY));
    }

    private Set<Biome> biomes(Biome... biomes) {
        return Stream.of(biomes).collect(Collectors.toSet());
    }

    /**
     * Populates blocks and loot tables for vanilla structures. Uses NBT parsing when possible.
     */
    private void populateStructureContents() {
        // Give config overrides precedence, then fall back to bundled scanner snapshots.
        applyStructureContentsFromNbt("village");
        applyStructureContentsFromNbt("mineshaft");
        applyStructureContentsFromNbt("stronghold");
        applyStructureContentsFromNbt("desert_temple");
        applyStructureContentsFromNbt("jungle_temple");
        applyStructureContentsFromNbt("witch_hut");
        applyStructureContentsFromNbt("igloo");
        applyStructureContentsFromNbt("monument");
        applyStructureContentsFromNbt("mansion");
        applyStructureContentsFromNbt("dungeon");
        applyStructureContentsFromNbt("fortress");
        applyStructureContentsFromNbt("endcity");
        applyStructureContentsFromNbt("end_ship");

        // Fill in remaining data (loot tables, entities) and fallback for structures without NBT data
        populateStronghold();
        populateMineshaft();
        populateNetherFortress();
    }

    // Procedural structures use hardcoded estimates since they're generated algorithmically

    private void populateStronghold() {
        setBlocksIfMissing("stronghold", filterNulls(
            createBlockEntry(Blocks.STONEBRICK, 0, 3000),
            createBlockEntry(Blocks.STONEBRICK, 1, 500),  // Mossy
            createBlockEntry(Blocks.STONEBRICK, 2, 500),  // Cracked
            createBlockEntry(Blocks.STONE_BRICK_STAIRS, 0, 200),
            createBlockEntry(Blocks.IRON_BARS, 0, 100),
            createBlockEntry(Blocks.IRON_DOOR, 0, 10),
            createBlockEntry(Blocks.BOOKSHELF, 0, 100),
            createBlockEntry(Blocks.END_PORTAL_FRAME, 0, 12),
            createBlockEntry(Blocks.MOB_SPAWNER, 0, 1)
        ));
        setLootTablesIfMissing("stronghold",
            createLootEntry("minecraft:chests/stronghold_corridor", "gui.structurescanner.loot.chest"),
            createLootEntry("minecraft:chests/stronghold_crossing", "gui.structurescanner.loot.chest"),
            createLootEntry("minecraft:chests/stronghold_library", "gui.structurescanner.loot.chest"));
        setEntitiesIfMissing("stronghold", createEntityEntry("minecraft:silverfish", 1, true));
    }

    private void populateMineshaft() {
        setBlocksIfMissing("mineshaft", filterNulls(
            createBlockEntry(Blocks.PLANKS, 0, 500),
            createBlockEntry(Blocks.OAK_FENCE, 0, 200),
            createBlockEntry(Blocks.RAIL, 0, 300),
            createBlockEntry(Blocks.TORCH, 0, 100),
            createBlockEntry(Blocks.WEB, 0, 50),
            createBlockEntry(Blocks.MOB_SPAWNER, 0, 1)
        ));
        setLootTablesIfMissing("mineshaft",
            createLootEntry("minecraft:chests/abandoned_mineshaft", "gui.structurescanner.loot.minecart_chest"));
        setEntitiesIfMissing("mineshaft", createEntityEntry("minecraft:cave_spider", 1, true));
    }

    private void populateNetherFortress() {
        addEntities("fortress", createEntityEntry("minecraft:wither_skeleton", 3));
    }

    private BlockEntry createBlockEntry(Block block, int meta, int count) {
        IBlockState state = block.getStateFromMeta(meta);

        return StructureNBTParser.createBlockEntry(state, count);
    }

    @SafeVarargs
    private final <T> List<T> filterNulls(T... elements) {
        return Stream.of(elements).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        return knownStructures.contains(structureId) && !structureId.getPath().equals("dungeon");
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        if (world == null || !canBeSearched(structureId)) return null;

        World generationWorld = ValidationContextManager.getGenerationWorld(world);
        String path = structureId.getPath();
        Long seed = SeedHelper.getWorldSeed(generationWorld);

        if (seed == null) {
            SimpleStructureScanner.LOGGER.warn("Could not get world seed for structure search");
            return null;
        }

        // Get cached or generate positions
        List<BlockPos> candidates = getCachedPositions(generationWorld, path, pos, seed);
        if (candidates.isEmpty()) return null;

        // Sort by distance (Y-agnostic - only use X and Z)
        PositionHelper.sortByHorizontalDistance(candidates, pos);

        PositionHelper.FilteredPositionResult selection = PositionHelper.selectFilteredPosition(candidates, skipCount, locationFilter);
        if (selection == null) return null;

        BlockPos targetPos = selection.getPosition();

        // Calculate terrain height for surface structures with Y=0
        if (targetPos.getY() == 0 && isSurfaceStructure(path)) {
            TerrainHeightCalculator heightCalc = new TerrainHeightCalculator(seed, generationWorld.getBiomeProvider());
            int terrainY = heightCalc.getTerrainHeight(targetPos.getX(), targetPos.getZ());
            targetPos = new BlockPos(targetPos.getX(), terrainY, targetPos.getZ());
        }

        boolean yAgnostic = targetPos.getY() == 0;

        return new StructureLocation(targetPos, skipCount, selection.getTotalMatches(), yAgnostic);
    }

    /**
     * Get cached positions or generate and cache them.
     */
    private List<BlockPos> getCachedPositions(World world, String structureType, BlockPos searchPos, long seed) {
        Map<String, List<BlockPos>> worldCache = positionCache.computeIfAbsent(world, ignored -> new HashMap<>());

        if (!worldCache.containsKey(structureType)) {
            List<BlockPos> positions = findStructuresByType(world, structureType, searchPos, seed, MAX_CACHED_POSITIONS);
            worldCache.put(structureType, positions);
        }

        return new ArrayList<>(worldCache.get(structureType));
    }

    @Override
    public List<BlockPos> findAllNearby(World world, ResourceLocation structureId, BlockPos pos, int maxResults) {
        if (world == null) return Collections.emptyList();
        if (!canBeSearched(structureId)) return Collections.emptyList();

        World generationWorld = ValidationContextManager.getGenerationWorld(world);
        String path = structureId.getPath();
        Long seed = SeedHelper.getWorldSeed(generationWorld);

        if (seed == null) {
            SimpleStructureScanner.LOGGER.warn("Could not get world seed for structure search");
            return Collections.emptyList();
        }

        List<BlockPos> candidates = findStructuresByType(generationWorld, path, pos, seed, maxResults);

        // Calculate terrain heights for surface structures
        if (isSurfaceStructure(path) && !candidates.isEmpty()) {
            TerrainHeightCalculator heightCalc = new TerrainHeightCalculator(seed, generationWorld.getBiomeProvider());
            List<BlockPos> withHeights = new ArrayList<>(candidates.size());

            for (BlockPos candidate : candidates) {
                if (candidate.getY() == 0) {
                    int terrainY = heightCalc.getTerrainHeight(candidate.getX(), candidate.getZ());
                    withHeights.add(new BlockPos(candidate.getX(), terrainY, candidate.getZ()));
                } else {
                    withHeights.add(candidate);
                }
            }

            return withHeights;
        }

        return candidates;
    }

    /**
     * Check if a structure is a surface structure (vs underground/underwater).
     */
    private boolean isSurfaceStructure(String structureType) {
        switch (structureType) {
            case "village":
            case "desert_temple":
            case "jungle_temple":
            case "witch_hut":
            case "igloo":
            case "mansion":
            case "endcity":
            case "end_ship":
                return true;
            default:
                return false;
        }
    }

    /**
     * Find structures of a given type using seed-based algorithms.
     */
    private List<BlockPos> findStructuresByType(World world, String structureType, BlockPos pos, long seed, int maxResults) {
        switch (structureType) {
            case "village":
                // Village uses spacing=32, separation=8, salt=10387312
                return findVillages(world, pos, seed, maxResults);

            case "desert_temple":
            case "jungle_temple":
            case "witch_hut":
            case "igloo":
                // All temples share spacing=32, separation=8, salt=14357617
                return findTemples(world, pos, seed, structureType, maxResults);

            case "monument":
                return findOceanMonuments(world, pos, seed, maxResults);

            case "mansion":
                return findWoodlandMansions(world, pos, seed, maxResults);

            case "stronghold":
                return findStrongholds(world, pos, seed, maxResults);

            case "fortress":
                return findNetherFortresses(world, pos, seed, maxResults);

            case "endcity":
            case "end_ship":
                return findEndCities(world, pos, seed, maxResults);

            case "mineshaft":
                return findMineshafts(world, pos, seed, maxResults);

            default:
                return Collections.emptyList();
        }
    }

    @Nullable
    private static ChunkGeneratorOverworld getOverworldChunkGenerator(World world) {
        IChunkGenerator chunkGenerator = ValidationContextManager.getGenerationChunkGenerator(world);
        if (chunkGenerator instanceof ChunkGeneratorOverworld) return (ChunkGeneratorOverworld) chunkGenerator;

        return null;
    }

    @Nullable
    private static ChunkGeneratorEnd getEndChunkGenerator(World world) {
        IChunkGenerator chunkGenerator = ValidationContextManager.getGenerationChunkGenerator(world);
        if (chunkGenerator instanceof ChunkGeneratorEnd) return (ChunkGeneratorEnd) chunkGenerator;

        return null;
    }

    private List<Biome> getStrongholdAllowedBiomes() {
        List<Biome> allowedBiomes = new ArrayList<>();

        for (Biome biome : Biome.REGISTRY) {
            if (biome != null && biome.getBaseHeight() > 0.0F
                    && !BiomeManager.strongHoldBiomesBlackList.contains(biome)) {
                allowedBiomes.add(biome);
            }
        }

        for (Biome biome : BiomeManager.strongHoldBiomes) {
            if (!allowedBiomes.contains(biome)) allowedBiomes.add(biome);
        }

        return allowedBiomes;
    }

    // ========== Village Algorithm ==========

    /**
     * Find villages using MC 1.12 algorithm.
     * Villages have their own salt (10387312) separate from temples.
     */
    private List<BlockPos> findVillages(World world, BlockPos pos, long seed, int maxResults) {
        Set<Biome> validBiomes = new HashSet<>();
        validBiomes.add(Biomes.PLAINS);
        validBiomes.add(Biomes.DESERT);
        validBiomes.add(Biomes.SAVANNA);
        validBiomes.add(Biomes.TAIGA);

        return findScatteredFeature(world, pos, seed, 32, 8, 10387312, maxResults, validBiomes);
    }

    /**
     * MC 1.12 scattered feature algorithm (used by village, temple types).
     * This matches MapGenScatteredFeature.getStructurePosition exactly.
     *
     * @param validBiomes If null, skips biome checking (finds all grid positions)
     */
    private List<BlockPos> findScatteredFeature(World world, BlockPos pos, long seed,
            int maxDist, int minDist, int salt, int maxResults, @Nullable Set<Biome> validBiomes) {

        List<BlockPos> results = new ArrayList<>();
        Set<Long> checkedRegions = new HashSet<>();
        BiomeProvider biomeProvider = world.getBiomeProvider();

        // Player's region coordinates
        int playerRegionX = Math.floorDiv(pos.getX() >> 4, maxDist);
        int playerRegionZ = Math.floorDiv(pos.getZ() >> 4, maxDist);

        // Search outward in regions (not chunks) - much more efficient
        int searchRadiusRegions = 20;

        for (int dist = 0; dist <= searchRadiusRegions && results.size() < maxResults; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dz = -dist; dz <= dist; dz++) {
                    // Only check the perimeter at this distance (expanding square)
                    if (dist > 0 && Math.abs(dx) != dist && Math.abs(dz) != dist) continue;

                    int regionX = playerRegionX + dx;
                    int regionZ = playerRegionZ + dz;

                    // Avoid duplicate region checks
                    long regionKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
                    if (checkedRegions.contains(regionKey)) continue;
                    checkedRegions.add(regionKey);

                    // Get the structure position for this region directly
                    BlockPos structurePos = getScatteredFeaturePosForRegion(seed, maxDist, minDist, salt, regionX, regionZ);

                    // Check biome using BiomeProvider (fast, doesn't load chunks)
                    if (validBiomes != null) {
                        Biome biome = biomeProvider.getBiome(structurePos);
                        if (!validBiomes.contains(biome)) continue;
                    }

                    results.add(structurePos);
                }
            }
        }

        return results;
    }

    /**
     * Get the structure position for a specific region.
     */
    private BlockPos getScatteredFeaturePosForRegion(long seed, int maxDist, int minDist, int salt, int regionX, int regionZ) {
        Random random = SeedHelper.seedRegionRandom(seed, regionX, regionZ, salt);

        int offsetX = random.nextInt(maxDist - minDist);
        int offsetZ = random.nextInt(maxDist - minDist);

        int structChunkX = regionX * maxDist + offsetX;
        int structChunkZ = regionZ * maxDist + offsetZ;

        return new BlockPos(structChunkX * 16 + 8, 0, structChunkZ * 16 + 8);
    }

    // ========== Temple Algorithm (Desert Temple, Jungle Temple, Witch Hut, Igloo) ==========

    /**
     * Find temples using MC 1.12 algorithm.
     * All temple types share the same grid (salt=14357617) but filter by biome.
     */
    private List<BlockPos> findTemples(World world, BlockPos pos, long seed, String templeType, int maxResults) {
        Set<Biome> validBiomes = new HashSet<>();

        switch (templeType) {
            case "desert_temple":
                validBiomes.add(Biomes.DESERT);
                validBiomes.add(Biomes.DESERT_HILLS);
                break;
            case "jungle_temple":
                validBiomes.add(Biomes.JUNGLE);
                validBiomes.add(Biomes.JUNGLE_HILLS);
                break;
            case "witch_hut":
                validBiomes.add(Biomes.SWAMPLAND);
                break;
            case "igloo":
                validBiomes.add(Biomes.ICE_PLAINS);
                validBiomes.add(Biomes.COLD_TAIGA);
                break;
        }

        return findScatteredFeature(world, pos, seed, 32, 8, 14357617, maxResults, validBiomes);
    }

    // ========== Ocean Monument Algorithm ==========

    /**
     * Find ocean monuments using MC 1.12 algorithm.
     * Monuments use spacing=32, separation=5, salt=10387313.
     * Iterates over regions for efficiency.
     */
    private List<BlockPos> findOceanMonuments(World world, BlockPos pos, long seed, int maxResults) {
        List<BlockPos> results = new ArrayList<>();
        Set<Long> checkedRegions = new HashSet<>();
        BiomeProvider biomeProvider = world.getBiomeProvider();

        int maxDist = 32;
        int minDist = 5;
        int salt = 10387313;

        int playerRegionX = Math.floorDiv(pos.getX() >> 4, maxDist);
        int playerRegionZ = Math.floorDiv(pos.getZ() >> 4, maxDist);

        int searchRadiusRegions = 20;

        for (int dist = 0; dist <= searchRadiusRegions && results.size() < maxResults; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dz = -dist; dz <= dist; dz++) {
                    if (dist > 0 && Math.abs(dx) != dist && Math.abs(dz) != dist) continue;

                    int regionX = playerRegionX + dx;
                    int regionZ = playerRegionZ + dz;

                    long regionKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
                    if (checkedRegions.contains(regionKey)) continue;
                    checkedRegions.add(regionKey);

                    // Vanilla picks one candidate chunk per 32x32 region, then validates the
                    // center biome and the surrounding 29-block radius entirely through the biome provider.
                    BlockPos structurePos = getMonumentPosForRegion(seed, maxDist, minDist, salt, regionX, regionZ);

                    if (!biomeProvider.areBiomesViable(
                            structurePos.getX(), structurePos.getZ(), 16, MONUMENT_SPAWN_BIOMES)) continue;
                    if (!biomeProvider.areBiomesViable(
                            structurePos.getX(), structurePos.getZ(), 29, MONUMENT_WATER_BIOMES)) continue;

                    results.add(structurePos);
                }
            }
        }

        return results;
    }

    private BlockPos getMonumentPosForRegion(long seed, int maxDist, int minDist, int salt, int regionX, int regionZ) {
        Random random = SeedHelper.seedRegionRandom(seed, regionX, regionZ, salt);

        // Monument placement uses the averaged offset formula from vanilla.
        // The sum of two bounded random values creates the triangular distribution toward the region center.
        int range = maxDist - minDist;
        int offsetX = (random.nextInt(range) + random.nextInt(range)) / 2;
        int offsetZ = (random.nextInt(range) + random.nextInt(range)) / 2;

        int structChunkX = regionX * maxDist + offsetX;
        int structChunkZ = regionZ * maxDist + offsetZ;

        // Ocean monuments sit on the sea floor, typically Y=39 (center), but surface is ~Y=63
        return new BlockPos(structChunkX * 16 + 8, 63, structChunkZ * 16 + 8);
    }

    // ========== Woodland Mansion Algorithm ==========

    /**
     * Find woodland mansions using MC 1.12 algorithm.
     * Mansions use spacing=80, separation=20, salt=10387319.
     * Mansions are VERY rare - roofed forest biomes are uncommon.
     */
    private List<BlockPos> findWoodlandMansions(World world, BlockPos pos, long seed, int maxResults) {
        List<BlockPos> results = new ArrayList<>();
        Set<Long> checkedRegions = new HashSet<>();
        BiomeProvider biomeProvider = world.getBiomeProvider();
        ChunkGeneratorOverworld chunkGenerator = getOverworldChunkGenerator(world);

        int maxDist = 80;
        int minDist = 20;
        int salt = 10387319;

        int playerRegionX = Math.floorDiv(pos.getX() >> 4, maxDist);
        int playerRegionZ = Math.floorDiv(pos.getZ() >> 4, maxDist);

        // Mansions are very rare, search further
        int searchRadiusRegions = 30;

        for (int dist = 0; dist <= searchRadiusRegions && results.size() < maxResults; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dz = -dist; dz <= dist; dz++) {
                    if (dist > 0 && Math.abs(dx) != dist && Math.abs(dz) != dist) continue;

                    int regionX = playerRegionX + dx;
                    int regionZ = playerRegionZ + dz;

                    long regionKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
                    if (checkedRegions.contains(regionKey)) continue;
                    checkedRegions.add(regionKey);

                    // Mansions use the same averaged-offset region math as monuments, but the
                    // candidate must also survive vanilla's biome and terrain validation.
                    BlockPos structurePos = getMansionPosForRegion(
                        seed, maxDist, minDist, salt, regionX, regionZ, chunkGenerator
                    );
                    if (structurePos == null) continue;
                    if (!biomeProvider.areBiomesViable(
                            structurePos.getX(), structurePos.getZ(), 32, MANSION_BIOMES)) continue;

                    results.add(structurePos);
                }
            }
        }

        return results;
    }

    @Nullable
    private BlockPos getMansionPosForRegion(long seed, int maxDist, int minDist, int salt, int regionX, int regionZ,
            @Nullable ChunkGeneratorOverworld chunkGenerator) {
        Random random = SeedHelper.seedRegionRandom(seed, regionX, regionZ, salt);

        // Vanilla uses the same triangular region offset here, but on an 80-chunk grid.
        int range = maxDist - minDist;
        int chunkX = regionX * maxDist + (random.nextInt(range) + random.nextInt(range)) / 2;
        int chunkZ = regionZ * maxDist + (random.nextInt(range) + random.nextInt(range)) / 2;

        if (chunkGenerator == null) return new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8);

        int structureY = getMansionStructureY(seed, chunkGenerator, chunkX, chunkZ);
        if (structureY < 0) return null;

        return new BlockPos((chunkX << 4) + 8, structureY, (chunkZ << 4) + 8);
    }

    private int getMansionStructureY(long seed, ChunkGeneratorOverworld chunkGenerator, int chunkX, int chunkZ) {
        // The footprint check uses the structure-start RNG, not the region RNG above.
        Random random = SeedHelper.seedStructureStartRandom(seed, chunkX, chunkZ);
        Rotation rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
        ChunkPrimer chunkPrimer = new ChunkPrimer();
        chunkGenerator.setBlocksInChunk(chunkX, chunkZ, chunkPrimer);

        int offsetX = 5;
        int offsetZ = 5;

        if (rotation == Rotation.CLOCKWISE_90) {
            offsetX = -5;
        } else if (rotation == Rotation.CLOCKWISE_180) {
            offsetX = -5;
            offsetZ = -5;
        } else if (rotation == Rotation.COUNTERCLOCKWISE_90) {
            offsetZ = -5;
        }

        // Vanilla samples the four rotated corners of the start room footprint and rejects
        // the mansion if the lowest sampled ground height is below Y=60.
        int baseY = Math.min(
            Math.min(chunkPrimer.findGroundBlockIdx(7, 7), chunkPrimer.findGroundBlockIdx(7, 7 + offsetZ)),
            Math.min(
                chunkPrimer.findGroundBlockIdx(7 + offsetX, 7),
                chunkPrimer.findGroundBlockIdx(7 + offsetX, 7 + offsetZ)
            )
        );

        if (baseY < 60) return -1;

        return baseY + 1;
    }

    // ========== Stronghold Algorithm ==========

    /**
     * Find strongholds using Minecraft 1.12's ring-based algorithm.
     * In 1.12, there are 128 strongholds total, placed in concentric rings.
     * Ring 1: 3 strongholds at distance 1408-2688 blocks
     * Ring 2: 6 strongholds at distance 4480-5760 blocks
     * etc.
     */
    private List<BlockPos> findStrongholds(World world, BlockPos pos, long seed, int maxResults) {
        List<BlockPos> strongholds = calculateStrongholds(world, seed);

        // Sort by distance from player
        strongholds.sort(Comparator.comparingDouble(a -> a.distanceSq(pos)));

        return strongholds.subList(0, Math.min(maxResults, strongholds.size()));
    }

    /**
     * Calculate stronghold positions using Minecraft 1.12's algorithm.
     * MC 1.12 places 128 strongholds in 8 concentric rings.
     * <p>
     * From MapGenStronghold source:
     * distance = (4 * 32 + ringNumber * 32 * 6) + (random - 0.5) * 32 * 2.5
     * This is in CHUNKS, so:
     * - Ring 0: 128 chunks ± 40 chunks = 88-168 chunks = 1408-2688 blocks
     * - Ring 1: 320 chunks ± 40 chunks = 280-360 chunks = 4480-5760 blocks
     */
    private List<BlockPos> calculateStrongholds(World world, long seed) {
        List<BlockPos> strongholds = new ArrayList<>(STRONGHOLD_COUNT);
        List<Biome> allowedBiomes = getStrongholdAllowedBiomes();
        Random random = new Random();
        random.setSeed(seed);

        // Starting angle (random)
        double angle = random.nextDouble() * Math.PI * 2.0;

        int ringNumber = 0;
        int placedInRing = 0;
        int spread = 3;
        BiomeProvider biomeProvider = world.getBiomeProvider();

        for (int i = 0; i < STRONGHOLD_COUNT; i++) {
            // MapGenStronghold computes a polar position in chunk space, with each successive ring
            // 192 chunks farther out on average and a +/- 40 chunk random spread.
            double distanceInChunks = 4.0D * 32.0D + 32.0D * ringNumber * 6.0D
                + (random.nextDouble() - 0.5D) * 32.0D * 2.5D;

            int chunkX = (int) Math.round(Math.cos(angle) * distanceInChunks);
            int chunkZ = (int) Math.round(Math.sin(angle) * distanceInChunks);

            // After picking the ring position, vanilla nudges the stronghold to the nearest
            // allowed biome within the same 112-block search radius used by MapGenStronghold.
            BlockPos biomePos = biomeProvider.findBiomePosition(
                (chunkX << 4) + 8, (chunkZ << 4) + 8, 112, allowedBiomes, random
            );
            if (biomePos != null) {
                chunkX = biomePos.getX() >> 4;
                chunkZ = biomePos.getZ() >> 4;
            }

            // The ring algorithm only determines chunk X/Z. The actual staircase height depends
            // on structure piece generation, so Y cannot be determined here.
            strongholds.add(new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8));

            // Strongholds are evenly spaced around the current ring, then the phase is randomized
            // again when vanilla advances to the next ring.
            angle += (Math.PI * 2D) / (double) spread;

            placedInRing++;
            if (placedInRing == spread) {
                ringNumber++;
                placedInRing = 0;
                spread += 2 * spread / (ringNumber + 1);
                spread = Math.min(spread, STRONGHOLD_COUNT - i);
                angle += random.nextDouble() * Math.PI * 2.0;
            }
        }

        return strongholds;
    }

    // ========== Nether Fortress Algorithm ==========

    /**
     * Find nether fortresses using the fortress-specific algorithm.
     * Fortresses generate one per 16x16 chunk region.
     */
    private List<BlockPos> findNetherFortresses(World world, BlockPos pos, long seed, int maxResults) {
        Set<Long> checkedRegions = new HashSet<>();
        List<BlockPos> results = new ArrayList<>();

        int regionSize = 16;
        int playerRegionX = Math.floorDiv(pos.getX() >> 4, regionSize);
        int playerRegionZ = Math.floorDiv(pos.getZ() >> 4, regionSize);

        // Number of regions to search
        int searchRadius = Math.max(10, (int) Math.ceil(Math.sqrt(maxResults * 3.0D)));

        for (int radius = 0; radius <= searchRadius && results.size() < maxResults; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    int regionX = playerRegionX + dx;
                    int regionZ = playerRegionZ + dz;

                    long regionKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
                    if (checkedRegions.contains(regionKey)) continue;
                    checkedRegions.add(regionKey);

                    BlockPos fortressPos = getFortressPos(seed, regionX, regionZ);
                    if (fortressPos != null) results.add(fortressPos);
                }
            }
        }

        return results;
    }

    /**
     * Get the fortress position in a region.
     */
    @Nullable
    private BlockPos getFortressPos(long seed, int regionX, int regionZ) {
        // MapGenNetherBridge seeds each 16x16-chunk region directly from the region coordinates,
        // burns one value, then gives the region a 1-in-3 chance to host a fortress.
        Random random = new Random((long) (regionX ^ regionZ << 4) ^ seed);
        random.nextInt();
        if (random.nextInt(3) != 0) return null;

        // When the region is selected, vanilla places the fortress in the inner 8x8 window,
        // offset four chunks away from the region edge on both axes.
        int chunkX = (regionX << 4) + 4 + random.nextInt(8);
        int chunkZ = (regionZ << 4) + 4 + random.nextInt(8);

        // Nether fortresses typically generate around Y=64 (middle of nether)
        return new BlockPos(chunkX * 16 + 8, 64, chunkZ * 16 + 8);
    }

    // ========== End City Algorithm ==========

    /**
     * Find end cities using the end city algorithm.
     * End cities generate on the outer End islands (beyond 1000 blocks from origin).
     */
    private List<BlockPos> findEndCities(World world, BlockPos pos, long seed, int maxResults) {
        Set<Long> checkedRegions = new HashSet<>();
        List<BlockPos> results = new ArrayList<>();
        ChunkGeneratorEnd chunkGenerator = getEndChunkGenerator(world);

        int spacing = 20;
        int separation = 11;
        int salt = 10387313;

        int playerRegionX = Math.floorDiv(pos.getX() >> 4, spacing);
        int playerRegionZ = Math.floorDiv(pos.getZ() >> 4, spacing);

        int searchRadius = 15;

        for (int dist = 0; dist <= searchRadius && results.size() < maxResults; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dz = -dist; dz <= dist; dz++) {
                    if (dist > 0 && Math.abs(dx) != dist && Math.abs(dz) != dist) continue;

                    int regionX = playerRegionX + dx;
                    int regionZ = playerRegionZ + dz;

                    long regionKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
                    if (checkedRegions.contains(regionKey)) continue;
                    checkedRegions.add(regionKey);

                    BlockPos structurePos = getEndCityPosForRegion(
                        seed, spacing, separation, salt, regionX, regionZ, chunkGenerator
                    );
                    if (structurePos == null) continue;

                    results.add(structurePos);
                }
            }
        }

        return results;
    }

    @Nullable
    private BlockPos getEndCityPosForRegion(long seed, int spacing, int separation, int salt, int regionX, int regionZ,
            @Nullable ChunkGeneratorEnd chunkGenerator) {
        Random random = SeedHelper.seedRegionRandom(seed, regionX, regionZ, salt);

        // End cities use the same averaged-offset region formula as monuments and mansions,
        // but vanilla also requires the candidate to be outside the main island ring (1000 radius).
        int chunkX = regionX * spacing + (random.nextInt(spacing - separation) + random.nextInt(spacing - separation)) / 2;
        int chunkZ = regionZ * spacing + (random.nextInt(spacing - separation) + random.nextInt(spacing - separation)) / 2;
        long blockX = chunkX * 16L + 8L;
        long blockZ = chunkZ * 16L + 8L;

        if (blockX * blockX + blockZ * blockZ < 1000L * 1000L) return null;
        if (chunkGenerator == null) return new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8);
        if (!chunkGenerator.isIslandChunk(chunkX, chunkZ)) return null;

        // Vanilla rejects the city if the rotated start footprint cannot find terrain at Y>=60.
        int structureY = getEndCityStructureY(chunkGenerator, chunkX, chunkZ);
        if (structureY < 60) return null;

        return new BlockPos((chunkX << 4) + 8, structureY, (chunkZ << 4) + 8);
    }

    private int getEndCityStructureY(ChunkGeneratorEnd chunkGenerator, int chunkX, int chunkZ) {
        // End cities rotate the same 5-block footprint offsets as mansions and sample the
        // lowest of the four corners to decide whether the structure can start here.
        Random random = new Random(chunkX + chunkZ * 10387313L);
        Rotation rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
        ChunkPrimer chunkPrimer = new ChunkPrimer();
        chunkGenerator.setBlocksInChunk(chunkX, chunkZ, chunkPrimer);

        int offsetX = 5;
        int offsetZ = 5;

        if (rotation == Rotation.CLOCKWISE_90) {
            offsetX = -5;
        } else if (rotation == Rotation.CLOCKWISE_180) {
            offsetX = -5;
            offsetZ = -5;
        } else if (rotation == Rotation.COUNTERCLOCKWISE_90) {
            offsetZ = -5;
        }

        return Math.min(
            Math.min(chunkPrimer.findGroundBlockIdx(7, 7), chunkPrimer.findGroundBlockIdx(7, 7 + offsetZ)),
            Math.min(
                chunkPrimer.findGroundBlockIdx(7 + offsetX, 7),
                chunkPrimer.findGroundBlockIdx(7 + offsetX, 7 + offsetZ)
            )
        );
    }

    // ========== Mineshaft Algorithm ==========

    /**
     * Find mineshafts using the mineshaft generation algorithm. Mineshafts are determined per chunk based
     * on the same MapGenBase chunk seeding path vanilla uses when the world actually generates them.
     */
    private List<BlockPos> findMineshafts(World world, BlockPos pos, long seed, int maxResults) {
        List<BlockPos> results = new ArrayList<>();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        // Follow vanilla's outward ring scan. The 1000-chunk cap matches MapGenMineshaft#getNearestStructurePos,
        // but this batch search stops as soon as it has collected enough matches.
        int maxSearchRadius = 1000;

        for (int radius = 0; radius <= maxSearchRadius && results.size() < maxResults; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Only check the perimeter at this distance
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    int cx = chunkX + dx;
                    int cz = chunkZ + dz;

                    if (isMineshaftChunk(seed, cx, cz)) {
                        // Mineshafts generate between Y=10 and Y=60, typically starting around Y=35
                        // There is no deterministic way to get exact Y without loading chunks, so use Y=0
                        results.add(new BlockPos(cx * 16 + 8, 0, cz * 16 + 8));
                    }
                }
            }
        }

        return results;
    }

    /**
     * Check if a chunk contains a mineshaft based on seed.
     * Uses Minecraft 1.12's world-generation algorithm:
     * 1. MapGenBase.setupChunkSeed(seed, rand, chunkX, chunkZ)
     * 2. MapGenStructure.recursiveGenerate() burns one random value
     * 3. random.nextDouble() < 0.004
     * 4. random.nextInt(80) < max(abs(chunkX), abs(chunkZ))
     */
    private boolean isMineshaftChunk(long seed, int chunkX, int chunkZ) {
        // Use the same chunk seed path as MapGenBase.generate() before
        // MapGenStructure.recursiveGenerate() calls canSpawnStructureAtCoords().
        Random random = SeedHelper.seedChunkRandom(seed, chunkX, chunkZ);
        random.nextInt();

        // Both conditions must be met
        if (random.nextDouble() >= 0.004) return false;

        return random.nextInt(80) < Math.max(Math.abs(chunkX), Math.abs(chunkZ));
    }

}
