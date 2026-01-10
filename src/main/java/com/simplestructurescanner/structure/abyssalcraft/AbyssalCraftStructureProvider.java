package com.simplestructurescanner.structure.abyssalcraft;

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
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureProvider;


/**
 * Structure provider for AbyssalCraft mod.
 * Provides location data for AC's major structures.
 */
public class AbyssalCraftStructureProvider implements StructureProvider {

    private static final String PROVIDER_ID = "abyssalcraft";
    private static final String MOD_ID = "abyssalcraft";
    private static final String MOD_NAME = I18n.translateToLocal("gui.structurescanner.provider.abyssalcraft");

    private List<ResourceLocation> knownStructures;
    private Map<ResourceLocation, StructureInfo> structureInfos = new HashMap<>();

    // Cache: seed -> list of AbyStronghold positions
    private static final Map<Long, List<BlockPos>> abyStrongholdCache = new HashMap<>();

    // AbyssalCraft dimension IDs (fetched at runtime)
    private int abyssalWastelandId = -1;
    private int dreadlandsId = -1;
    private int omotholId = -1;
    private int darkRealmId = -1;

    // AbyssalCraft biomes (fetched at runtime)
    private Biome abyssalWastelandsBiome;
    private Biome dreadlandsBiome;
    private Biome omotholBiome;

    public AbyssalCraftStructureProvider() {
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getModName() {
        return MOD_NAME;
    }

    @Override
    public boolean isAvailable() {
        return Loader.isModLoaded(MOD_ID);
    }

    @Override
    public void postInit() {
        // Get dimension IDs from ACLib
        try {
            Class<?> acLibClass = Class.forName("com.shinoow.abyssalcraft.lib.ACLib");
            abyssalWastelandId = acLibClass.getField("abyssal_wasteland_id").getInt(null);
            dreadlandsId = acLibClass.getField("dreadlands_id").getInt(null);
            omotholId = acLibClass.getField("omothol_id").getInt(null);
            darkRealmId = acLibClass.getField("dark_realm_id").getInt(null);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.error("Failed to get AbyssalCraft dimension IDs", e);
        }

        // Get biomes from ACBiomes
        try {
            Class<?> acBiomesClass = Class.forName("com.shinoow.abyssalcraft.api.biome.ACBiomes");
            abyssalWastelandsBiome = (Biome) acBiomesClass.getField("abyssal_wastelands").get(null);
            dreadlandsBiome = (Biome) acBiomesClass.getField("dreadlands").get(null);
            omotholBiome = (Biome) acBiomesClass.getField("omothol").get(null);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.error("Failed to get AbyssalCraft biomes", e);
        }

        knownStructures = new ArrayList<>();

        // Abyssal Wasteland structures
        addStructure("aby_stronghold", "gui.structurescanner.structures.abyssalcraft.aby_stronghold", 0, 0, 0);

        // Dreadlands structures
        addStructure("dreadlands_mineshaft", "gui.structurescanner.structures.abyssalcraft.dreadlands_mineshaft", 0, 0, 0);

        // TODO: Add Chagaroth's Lair?

        // Omothol structures
        addStructure("jzahar_temple", "gui.structurescanner.structures.abyssalcraft.jzahar_temple", 64, 30, 96);
        addStructure("omothol_city", "gui.structurescanner.structures.abyssalcraft.omothol_city", 0, 0, 0);
        addStructure("omothol_storage", "gui.structurescanner.structures.abyssalcraft.omothol_storage", 17, 10, 18);

        // Cross-dimension structures
        addStructure("shoggoth_lair", "gui.structurescanner.structures.abyssalcraft.shoggoth_lair", 28, 15, 28);
        addStructure("graveyard", "gui.structurescanner.structures.abyssalcraft.graveyard", 16, 8, 16);

        populateStructureMetadata();
        populateStructureContents();
    }

    private void addStructure(String path, String displayName, int sizeX, int sizeY, int sizeZ) {
        ResourceLocation id = new ResourceLocation(MOD_ID, path);
        knownStructures.add(id);

        String name = I18n.translateToLocal(displayName);
        StructureInfo info = new StructureInfo(id, name, PROVIDER_ID, sizeX, sizeY, sizeZ);
        structureInfos.put(id, info);
    }

    private void populateStructureMetadata() {
        // Create DimensionInfo with localization keys for AC dimensions
        DimensionInfo abyssalWastelandDim = new DimensionInfo(abyssalWastelandId, "gui.structurescanner.dimension.abyssal_wasteland");
        DimensionInfo dreadlandsDim = new DimensionInfo(dreadlandsId, "gui.structurescanner.dimension.dreadlands");
        DimensionInfo omotholDim = new DimensionInfo(omotholId, "gui.structurescanner.dimension.omothol");
        DimensionInfo darkRealmDim = new DimensionInfo(darkRealmId, "gui.structurescanner.dimension.dark_realm");

        Set<DimensionInfo> abyssalWasteland = Collections.singleton(abyssalWastelandDim);
        Set<DimensionInfo> dreadlands = Collections.singleton(dreadlandsDim);
        Set<DimensionInfo> omothol = Collections.singleton(omotholDim);
        Set<DimensionInfo> allDims = new HashSet<>();
        allDims.add(DimensionInfo.OVERWORLD);
        allDims.add(abyssalWastelandDim);
        allDims.add(dreadlandsDim);
        allDims.add(omotholDim);
        allDims.add(darkRealmDim);

        // AbyStronghold - Abyssal Wasteland only
        setMetadata("aby_stronghold",
            abyssalWastelandsBiome != null ? Collections.singleton(abyssalWastelandsBiome) : null,
            abyssalWasteland,
            "gui.structurescanner.rarity.rare");

        // Dreadlands Mineshaft
        setMetadata("dreadlands_mineshaft", null, dreadlands, "gui.structurescanner.rarity.uncommon");

        // J'zahar Temple - fixed position at origin
        setMetadata("jzahar_temple",
            omotholBiome != null ? Collections.singleton(omotholBiome) : null,
            omothol,
            "gui.structurescanner.rarity.unique");

        // Omothol City - randomly generated buildings with various loot
        setMetadata("omothol_city",
            omotholBiome != null ? Collections.singleton(omotholBiome) : null,
            omothol,
            "gui.structurescanner.rarity.common");

        // Omothol Storage - storage buildings with crates
        setMetadata("omothol_storage",
            omotholBiome != null ? Collections.singleton(omotholBiome) : null,
            omothol,
            "gui.structurescanner.rarity.uncommon");

        // Shoggoth Lairs spawn in SWAMP and RIVER biomes in Overworld, all biomes in AC dimensions
        Set<Biome> shoggothBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.SWAMP)
                    || (BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER)
                        && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN))) {
                shoggothBiomes.add(biome);
            }
        }

        setMetadata("shoggoth_lair", shoggothBiomes, allDims, "gui.structurescanner.rarity.uncommon");

        // Graveyards spawn in multiple dimensions
        setMetadata("graveyard", null, allDims, "gui.structurescanner.rarity.uncommon");
    }

    private void setMetadata(String path, Set<Biome> biomes, Set<DimensionInfo> dimensions, String rarity) {
        StructureInfo info = structureInfos.get(new ResourceLocation(MOD_ID, path));
        if (info == null) return;

        info.setValidBiomes(biomes);
        info.setValidDimensions(dimensions);
        info.setRarity(rarity);
    }

    private void populateStructureContents() {
        // AbyStronghold - contains portal room like vanilla stronghold
        StructureInfo abyStronghold = structureInfos.get(new ResourceLocation(MOD_ID, "aby_stronghold"));
        if (abyStronghold != null) {
            List<LootEntry> loot = new ArrayList<>();
            loot.add(new LootEntry(new ResourceLocation("abyssalcraft", "chests/stronghold_corridor"),
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
            loot.add(new LootEntry(new ResourceLocation("abyssalcraft", "chests/stronghold_crossing"),
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
            abyStronghold.setLootTables(loot);

            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation("abyssalcraft", "abyssalzombie"), 1, true));
            abyStronghold.setEntities(entities);
        }

        // Dreadlands Mineshaft
        StructureInfo dreadMine = structureInfos.get(new ResourceLocation(MOD_ID, "dreadlands_mineshaft"));
        if (dreadMine != null) {
            List<LootEntry> loot = Collections.singletonList(
                new LootEntry(new ResourceLocation("abyssalcraft", "chests/mineshaft"),
                    Collections.emptyList(), "gui.structurescanner.loot.minecart_chest")
            );
            dreadMine.setLootTables(loot);
        }

        // J'zahar Temple - boss arena
        StructureInfo jzaharTemple = structureInfos.get(new ResourceLocation(MOD_ID, "jzahar_temple"));
        if (jzaharTemple != null) {
            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation("abyssalcraft", "jzahar"), 1, false));
            entities.add(new EntityEntry(new ResourceLocation("abyssalcraft", "jzaharminion"), 3, false));
            jzaharTemple.setEntities(entities);
        }

        // Omothol City - various building types with different loot
        StructureInfo omotholCity = structureInfos.get(new ResourceLocation(MOD_ID, "omothol_city"));
        if (omotholCity != null) {
            List<LootEntry> loot = new ArrayList<>();
            loot.add(new LootEntry(new ResourceLocation("abyssalcraft", "chests/omothol/blacksmith"),
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
            loot.add(new LootEntry(new ResourceLocation("abyssalcraft", "chests/omothol/house"),
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
            loot.add(new LootEntry(new ResourceLocation("abyssalcraft", "chests/omothol/library"),
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
            loot.add(new LootEntry(new ResourceLocation("abyssalcraft", "chests/omothol/farmhouse"),
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
            omotholCity.setLootTables(loot);

            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation("abyssalcraft", "remnant"), 10, false));
            omotholCity.setEntities(entities);
        }

        // Omothol Storage - storage building with crates
        StructureInfo omotholStorage = structureInfos.get(new ResourceLocation(MOD_ID, "omothol_storage"));
        if (omotholStorage != null) {
            List<LootEntry> loot = new ArrayList<>();
            loot.add(new LootEntry(new ResourceLocation("abyssalcraft", "chests/omothol/storage_junk"),
                Collections.emptyList(), "gui.structurescanner.loot.crate"));
            loot.add(new LootEntry(new ResourceLocation("abyssalcraft", "chests/omothol/storage_treasure"),
                Collections.emptyList(), "gui.structurescanner.loot.crate"));
            omotholStorage.setLootTables(loot);

            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation("abyssalcraft", "shoggoth"), 1, false));
            omotholStorage.setEntities(entities);
        }

        // Shoggoth Lair
        StructureInfo shoggothLair = structureInfos.get(new ResourceLocation(MOD_ID, "shoggoth_lair"));
        if (shoggothLair != null) {
            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation("abyssalcraft", "shoggoth"), 1, false));
            shoggothLair.setEntities(entities);
        }
    }

    @Override
    public List<ResourceLocation> getStructureIds() {
        return new ArrayList<>(knownStructures);
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        String path = structureId.getPath();

        // Only deterministic structures can be searched
        return path.equals("aby_stronghold") || path.equals("jzahar_temple");
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

        String path = structureId.getPath();
        Long seed = getWorldSeed(world);
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

        boolean yAgnostic = targetPos.getY() == 0;

        return new StructureLocation(targetPos, skipCount, totalValid, yAgnostic);
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
        Long seed = getWorldSeed(world);
        if (seed == null) return Collections.emptyList();

        List<BlockPos> results;

        switch (path) {
            case "aby_stronghold":
                if (world.provider.getDimension() != abyssalWastelandId) return Collections.emptyList();
                results = new ArrayList<>(getCachedAbyStrongholds(world, seed));
                sortByDistance(results, pos);
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
     *
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
                Collections.singletonList(abyssalWastelandsBiome), random);

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

    // ========== Utility Methods ==========

    private Long getWorldSeed(World world) {
        if (world.getWorldInfo() != null) return world.getWorldInfo().getSeed();

        return null;
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
}
