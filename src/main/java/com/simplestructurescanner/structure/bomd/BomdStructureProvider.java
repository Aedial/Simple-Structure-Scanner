package com.simplestructurescanner.structure.bomd;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.AbstractStructureProvider;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.util.PositionHelper;
import com.simplestructurescanner.structure.util.RarityTextHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper.ReflectionException;
import com.simplestructurescanner.structure.util.StructureContentAccumulator;
import com.simplestructurescanner.structure.util.StructurePreviewStitcher;
import com.simplestructurescanner.structure.util.StructureTranslationKeys;


public class BomdStructureProvider extends AbstractStructureProvider {

    private static final String PROVIDER_ID = "dungeon_additions";
    private static final String MOD_NAME = "gui.structurescanner.provider.dungeon_additions";
    private static final String MOD_ID = "da";

    private static final String COMMAND_LOCATE_MOD_CLASS = "com.dungeon_additions.da.util.commands.CommandLocateMod";
    private static final String WORLD_CONFIG_CLASS = "com.dungeon_additions.da.config.WorldConfig";
    private static final String MOD_CONFIG_CLASS = "com.dungeon_additions.da.config.ModConfig";

    private static final int CHUNK_COORDINATE_SHIFT = 4;
    private static final int MIN_SEARCH_RADIUS_CHUNKS = 64;
    private static final int MAX_SEARCH_RADIUS_CHUNKS = 384;
    private static final int PREVIEW_COLUMNS = 4;
    private static final int PREVIEW_SPACING = 2;
    private static final long MAX_SCAN_TIME_MS = 10000L;

    private static final ResourceLocation ENTITY_VOID_BLOSSOM = new ResourceLocation(MOD_ID, "void_blossom");
    private static final ResourceLocation ENTITY_ANCIENT_FALLEN = new ResourceLocation(MOD_ID, "ancient_fallen");
    private static final ResourceLocation ENTITY_ANCIENT_KNIGHT = new ResourceLocation(MOD_ID, "ancient_knight");
    private static final ResourceLocation ENTITY_ANCIENT_KNIGHT_RAPIER = new ResourceLocation(MOD_ID, "ancient_knight_rapier");
    private static final ResourceLocation ENTITY_NIGHT_LICH = new ResourceLocation(MOD_ID, "night_lich");
    private static final ResourceLocation ENTITY_NETHER_ABERRANT = new ResourceLocation(MOD_ID, "nether_aberant");
    private static final ResourceLocation ENTITY_INCENDIUM = new ResourceLocation(MOD_ID, "incendium_spirit");
    private static final ResourceLocation ENTITY_BAREANT = new ResourceLocation(MOD_ID, "bareant");
    private static final ResourceLocation ENTITY_NETHER_PYRE = new ResourceLocation(MOD_ID, "nether_pyre");
    private static final ResourceLocation ENTITY_VOLATILE_ORB = new ResourceLocation(MOD_ID, "volatile_orb");
    private static final ResourceLocation ENTITY_DRAUGR = new ResourceLocation(MOD_ID, "frost_draugr");
    private static final ResourceLocation ENTITY_DRAUGR_RANGER = new ResourceLocation(MOD_ID, "frost_draugr_ranger");
    private static final ResourceLocation ENTITY_ELITE_DRAUGR = new ResourceLocation(MOD_ID, "frost_draugr_elite");
    private static final ResourceLocation ENTITY_WYRK = new ResourceLocation(MOD_ID, "wyrk");
    private static final ResourceLocation ENTITY_GREAT_WYRK = new ResourceLocation(MOD_ID, "great_wyrk");
    private static final ResourceLocation ENTITY_SCUTTER_BEETLE = new ResourceLocation(MOD_ID, "scutter_beetle");
    private static final ResourceLocation ENTITY_AEGYPTIA = new ResourceLocation(MOD_ID, "aegyptia");
    private static final ResourceLocation ENTITY_EVERATOR = new ResourceLocation(MOD_ID, "everator");
    private static final ResourceLocation ENTITY_VOIDIANT = new ResourceLocation(MOD_ID, "voidiant");
    private static final ResourceLocation ENTITY_ENDERPHRITE = new ResourceLocation(MOD_ID, "enderphrite");
    private static final ResourceLocation ENTITY_ENDERPHRITE_GAUNTLET = new ResourceLocation(MOD_ID, "enderphrite_gauntlet");
    private static final ResourceLocation ENTITY_OBSIDILITH = new ResourceLocation(MOD_ID, "obsidilith");
    private static final ResourceLocation ENTITY_VOIDICLYSM = new ResourceLocation(MOD_ID, "voidclysm");
    private static final ResourceLocation ENTITY_REANIMATE = new ResourceLocation(MOD_ID, "reanimate");
    private static final ResourceLocation ENTITY_CURSED_SENTINEL = new ResourceLocation(MOD_ID, "cursed_sentinel");
    private static final ResourceLocation ENTITY_APATHYR = new ResourceLocation(MOD_ID, "apathyr");
    private static final ResourceLocation ENTITY_MYSTERIOUS_TRADER = new ResourceLocation(MOD_ID, "mysterious_trader");
    private static final ResourceLocation ENTITY_TRIDENT_GARGOYLE = new ResourceLocation(MOD_ID, "trident_gargoyle");
    private static final ResourceLocation ENTITY_MAGE_GARGOYLE = new ResourceLocation(MOD_ID, "mage_gargoyle");
    private static final ResourceLocation ENTITY_IMPERIAL_SWORD = new ResourceLocation(MOD_ID, "imperial_sword");
    private static final ResourceLocation ENTITY_IMPERIAL_HALBERD = new ResourceLocation(MOD_ID, "imperial_halberd");
    private static final ResourceLocation ENTITY_HIGH_KING_DRAKE = new ResourceLocation(MOD_ID, "high_king_drake");
    private static final ResourceLocation ENTITY_HIGH_KING = new ResourceLocation(MOD_ID, "high_king");
    private static final ResourceLocation ENTITY_DARK_ORB = new ResourceLocation(MOD_ID, "dark_orb");
    private static final ResourceLocation ENTITY_SKY_TORNADO = new ResourceLocation(MOD_ID, "sky_tornado");

    private static final List<StructureDefinition> STRUCTURES = Arrays.asList(
        new StructureDefinition("blossom_cave", "IsBlossomCaveAtPos",
            "void_blosom_search_distance", 96, "void_blossom_cave_weight", "list_of_dimensions",
            Collections.singletonList("blossom"), Collections.<ResourceLocation>emptyList()),
        new StructureDefinition("rotten_hold", "IsRottenHoldAtPos",
            "rotten_hold_distance", 96, "rot_hold_spacing", "list_of_dimensions_rotten_hold",
            Collections.singletonList("rot_hold"), Collections.singletonList(ENTITY_ANCIENT_FALLEN)),
        new StructureDefinition("night_lich_tower", "IsLichTowerAtPos",
            "lich_search_distance", 96, "lich_tower_spacing", "list_of_dimensions_lich_tower",
            Collections.singletonList("lich_tower"), Collections.singletonList(ENTITY_NIGHT_LICH)),
        new StructureDefinition("burning_flame_arena", "IsBurningFlameArenaAtPos",
            "burning_flame_arena_search_radius", 96, "burning_arena_weight", null,
            Collections.singletonList("nether_arena"), Collections.<ResourceLocation>emptyList(), -1),
        new StructureDefinition("frozen_castle", "IsFrozenCastleAtPos",
            "frozen_castle_search_distance", 96, "frozen_castle_spacing", "list_of_dimensions_frozen_castle",
            Collections.singletonList("frozen_castle"), Collections.singletonList(ENTITY_GREAT_WYRK)),
        new StructureDefinition("high_court_city", "IsHighCityAtPos",
            "high_court_city_search_distance", 96, "high_city_spacing", "list_of_dimensions_high_court_city",
            Arrays.asList("high_city", "aether_high_city"), Arrays.asList(ENTITY_HIGH_KING_DRAKE, ENTITY_HIGH_KING)),
        new StructureDefinition("forgotten_temple", "IsForgottenTempleAtPos",
            "forgotten_temple_distance", 96, "temple_spacing", "list_of_dimensions_forgotten_temple",
            Collections.singletonList("forgotten_temple"), Collections.<ResourceLocation>emptyList()),
        new StructureDefinition("obsidilith_arena", "IsObsidilithArenaAtPos",
            "obsidilith_arena_search_distance", 96, "obsidilith_arena_spacing", "list_of_dimensions_obsidilith_arena",
            Collections.singletonList("obsi_arena"), Arrays.asList(ENTITY_OBSIDILITH, ENTITY_VOIDICLYSM)),
        new StructureDefinition("gaelon_sanctuary", "IsGaelonSanctuaryAtPos",
            "gaelon_sanctuary_search_distance", 96, "gaelon_sanctuary_spacing", "list_of_dimensions_gaelon_sanctuary",
            Collections.singletonList("gaelon_sanctuary"), Collections.<ResourceLocation>emptyList()),
        new StructureDefinition("trader_post", "IsTraderPostAtPos",
            "trader_post_search_distance", 96, "mysterious_trader_post_spacing", "list_of_dimensions_mysterious_trader_post",
            Collections.singletonList("trader"), Collections.<ResourceLocation>emptyList()),
        new StructureDefinition("dark_ruins", "IsDarkRuinsAtPos",
            "dark_ruins_search_distance", 96, "dauntless_spacing", "list_of_dimensions_dauntless",
            Collections.singletonList("dark_ruins"), Collections.<ResourceLocation>emptyList()),
        new StructureDefinition("end_outpost", "IsEndOutpostAtPos",
            null, 90, "end_outposts_spacing", null,
            Collections.singletonList("outpost/end"), Collections.<ResourceLocation>emptyList(), 1)
    );

    private final Map<ResourceLocation, StructureDefinition> definitionsById = new LinkedHashMap<>();
    private final Map<String, Method> predicateMethodCache = new LinkedHashMap<>();

    private Class<?> commandLocateModClass;
    private Class<?> worldConfigClass;
    private Class<?> modConfigClass;
    private boolean coinsSpawnInChests = true;

    public BomdStructureProvider() {
        super(PROVIDER_ID, PROVIDER_ID, MOD_NAME, MOD_ID);
    }

    @Override
    public void postInit() {
        resetStructures();
        definitionsById.clear();
        predicateMethodCache.clear();

        try {
            commandLocateModClass = ReflectionHelper.loadClassRequired(COMMAND_LOCATE_MOD_CLASS);
            worldConfigClass = ReflectionHelper.loadClassRequired(WORLD_CONFIG_CLASS);
            modConfigClass = ReflectionHelper.loadClassRequired(MOD_CONFIG_CLASS);
            coinsSpawnInChests = ReflectionHelper.readStaticBoolean(worldConfigClass, "coins_spawn_in_chests", true);

            for (StructureDefinition structureDefinition : STRUCTURES) registerDefinition(structureDefinition);

            SimpleStructureScanner.LOGGER.info("Loaded {} BOMD structures", structureInfos.size());
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.error("Failed to initialize BOMD structure provider", e);
        }
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        return definitionsById.containsKey(structureId);
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        StructureDefinition definition = definitionsById.get(structureId);
        if (world == null || definition == null) return null;

        return findNearestStable(world, definition, pos, skipCount, locationFilter);
    }

    @Override
    @Nullable
    public List<BlockPos> findAllNearby(World world, ResourceLocation structureId, BlockPos pos, int maxResults) {
        StructureDefinition definition = definitionsById.get(structureId);
        if (world == null || definition == null) return Collections.emptyList();

        ScanOutcome scanOutcome = scanCandidates(world, definition, pos, maxResults);
        PositionHelper.sortByHorizontalDistance(scanOutcome.positions, pos);
        if (scanOutcome.positions.size() <= maxResults) return scanOutcome.positions;

        return new ArrayList<>(scanOutcome.positions.subList(0, maxResults));
    }

    private void registerDefinition(StructureDefinition definition) {
        ResourceLocation structureId = createStructureId(definition.path);
        StructureInfo info = new StructureInfo(
            structureId,
            LocalizedText.translatable(StructureTranslationKeys.structureNameKey(structureId)),
            PROVIDER_ID, 0, 0, 0
        );

        knownStructures.add(structureId);
        structureInfos.put(structureId, info);
        definitionsById.put(structureId, definition);

        Set<DimensionInfo> dimensions = definition.resolveDimensions(worldConfigClass);
        if (dimensions != null && !dimensions.isEmpty()) info.setValidDimensions(dimensions);

        LocalizedText rarity = definition.resolveRarity(worldConfigClass);
        if (rarity != null) info.setRarity(rarity);

        populateStructureContents(definition, info);
    }

    private void populateStructureContents(StructureDefinition definition, StructureInfo info) {
        StructureContentAccumulator contents = new StructureContentAccumulator();
        StructurePreviewStitcher preview = new StructurePreviewStitcher();
        List<String> templatePaths = collectTemplatePaths(definition.templateRoots);

        int previewColumn = 0;
        int previewX = 0;
        int previewZ = 0;
        int rowDepth = 0;

        for (String templatePath : templatePaths) {
            StructureNBTParser.ParsedStructure parsed = StructureNBTParser.parseBundledStructure(
                MOD_ID, templatePath, new BomdTemplateExtension(definition));
            if (parsed == null) continue;

            contents.add(parsed);
            preview.addParsedStructure(parsed, new BlockPos(previewX, 0, previewZ));

            int width = Math.max(parsed.sizeX, 1);
            int depth = Math.max(parsed.sizeZ, 1);
            rowDepth = Math.max(rowDepth, depth);
            previewColumn++;

            if (previewColumn >= PREVIEW_COLUMNS) {
                previewColumn = 0;
                previewX = 0;
                previewZ += rowDepth + PREVIEW_SPACING;
                rowDepth = 0;
            } else {
                previewX += width + PREVIEW_SPACING;
            }
        }

        for (ResourceLocation entityId : definition.manualEntities) {
            contents.addEntity(new EntityEntry(entityId, 1, false));
        }

        contents.applyTo(info);

        List<StructureInfo.StructureLayer> layers = preview.buildLayers();
        if (!layers.isEmpty()) info.setLayers(layers);
    }

    private ScanOutcome scanCandidates(World world, StructureDefinition definition, BlockPos origin, int maxResults) {
        List<BlockPos> positions = new ArrayList<>();
        int playerChunkX = origin.getX() >> CHUNK_COORDINATE_SHIFT;
        int playerChunkZ = origin.getZ() >> CHUNK_COORDINATE_SHIFT;
        int maxRadiusChunks = definition.resolveSearchRadiusChunks(modConfigClass, worldConfigClass);
        long startTime = System.currentTimeMillis();

        for (int radius = 0; radius <= maxRadiusChunks; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    if (System.currentTimeMillis() - startTime > MAX_SCAN_TIME_MS) {
                        return new ScanOutcome(positions, true);
                    }

                    int chunkX = playerChunkX + dx;
                    int chunkZ = playerChunkZ + dz;
                    if (!matchesStructure(definition, world, chunkX, chunkZ)) continue;

                    positions.add(new BlockPos((chunkX << CHUNK_COORDINATE_SHIFT) + 8, 0,
                        (chunkZ << CHUNK_COORDINATE_SHIFT) + 8));
                    if (positions.size() >= maxResults) return new ScanOutcome(positions, false);
                }
            }
        }

        return new ScanOutcome(positions, false);
    }

    private boolean matchesStructure(StructureDefinition definition, World world, int chunkX, int chunkZ) {
        try {
            Method method = predicateMethodCache.get(definition.searchPredicateMethod);
            if (method == null) {
                method = commandLocateModClass.getDeclaredMethod(definition.searchPredicateMethod,
                    World.class, int.class, int.class);
                method.setAccessible(true);
                predicateMethodCache.put(definition.searchPredicateMethod, method);
            }

            Object value = method.invoke(null, world, chunkX, chunkZ);
            return value instanceof Boolean && (Boolean) value;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Failed to evaluate BOMD locate predicate {}", definition.searchPredicateMethod, e);
            return false;
        }
    }

    private List<String> collectTemplatePaths(List<String> templateRoots) {
        File modSource = getRequiredModSource();
        if (modSource == null) return Collections.emptyList();

        Set<String> templatePaths = new LinkedHashSet<>();
        if (modSource.isFile()) {
            collectTemplatePathsFromArchive(modSource, templateRoots, templatePaths);
        } else {
            collectTemplatePathsFromDirectory(modSource, templateRoots, templatePaths);
        }

        List<String> results = new ArrayList<>(templatePaths);
        Collections.sort(results);
        return results;
    }

    @Nullable
    private File getRequiredModSource() {
        ModContainer modContainer = Loader.instance().getIndexedModList().get(MOD_ID);
        return modContainer != null ? modContainer.getSource() : null;
    }

    private void collectTemplatePathsFromArchive(File archiveFile, List<String> templateRoots,
            Set<String> target) {
        String prefixBase = "assets/" + MOD_ID + "/structures/";

        try (ZipFile zipFile = new ZipFile(archiveFile)) {
            zipFile.stream().map(ZipEntry::getName).filter(name -> name.endsWith(".nbt")).forEach(name -> {
                if (!name.startsWith(prefixBase)) return;

                String relativePath = name.substring(prefixBase.length(), name.length() - 4);
                for (String templateRoot : templateRoots) {
                    if (relativePath.equals(templateRoot) || relativePath.startsWith(templateRoot + "/")) {
                        target.add(relativePath);
                        return;
                    }
                }
            });
        } catch (IOException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to scan BOMD asset archive {}", archiveFile, e);
        }
    }

    private void collectTemplatePathsFromDirectory(File modSource, List<String> templateRoots,
            Set<String> target) {
        for (File structuresRoot : findStructureRoots(modSource)) {
            for (String templateRoot : templateRoots) {
                File rootDirectory = new File(structuresRoot, templateRoot);
                if (!rootDirectory.exists()) continue;

                collectTemplateFiles(rootDirectory, structuresRoot, target);
            }
        }
    }

    private List<File> findStructureRoots(File modSource) {
        List<File> roots = new ArrayList<>();
        addRootIfPresent(roots, new File(modSource, "assets/" + MOD_ID + "/structures"));
        addRootIfPresent(roots, new File(modSource, "src/main/resources/assets/" + MOD_ID + "/structures"));
        addRootIfPresent(roots, new File(modSource, "build/resources/main/assets/" + MOD_ID + "/structures"));

        if (!roots.isEmpty()) return roots;

        File discoveredRoot = searchForStructureRoot(modSource, 4);
        if (discoveredRoot != null) roots.add(discoveredRoot);
        return roots;
    }

    private void addRootIfPresent(List<File> roots, File candidate) {
        if (candidate.isDirectory()) roots.add(candidate);
    }

    @Nullable
    private File searchForStructureRoot(File directory, int depthRemaining) {
        if (depthRemaining < 0 || directory == null || !directory.isDirectory()) return null;

        File directCandidate = new File(directory, "assets/" + MOD_ID + "/structures");
        if (directCandidate.isDirectory()) return directCandidate;

        File[] children = directory.listFiles(File::isDirectory);
        if (children == null) return null;

        for (File child : children) {
            File nested = searchForStructureRoot(child, depthRemaining - 1);
            if (nested != null) return nested;
        }

        return null;
    }

    private void collectTemplateFiles(File current, File structuresRoot, Set<String> target) {
        if (current.isFile()) {
            if (!current.getName().endsWith(".nbt")) return;

            String rootPath = structuresRoot.getAbsolutePath();
            String currentPath = current.getAbsolutePath();
            if (!currentPath.startsWith(rootPath)) return;

            String relativePath = currentPath.substring(rootPath.length() + 1)
                .replace(File.separatorChar, '/');
            target.add(relativePath.substring(0, relativePath.length() - 4));
            return;
        }

        File[] children = current.listFiles();
        if (children == null) return;

        for (File child : children) collectTemplateFiles(child, structuresRoot, target);
    }

    /**
     * Returns the loot table ID for a given base name, taking into account whether coins spawn in chests.
     */
    private ResourceLocation lootTableId(String baseName) {
        return new ResourceLocation(MOD_ID, baseName + (coinsSpawnInChests ? "" : "_nc"));
    }

    private final class BomdTemplateExtension implements StructureNBTParser.StructureParseExtension {

        private final StructureDefinition definition;

        private BomdTemplateExtension(StructureDefinition definition) {
            this.definition = definition;
        }

        @Override
        public boolean shouldCountBlock(@Nullable IBlockState state, @Nullable Block block) {
            return block != Blocks.STRUCTURE_BLOCK
                && StructureNBTParser.StructureParseExtension.super.shouldCountBlock(state, block);
        }

        @Override
        public boolean shouldStoreLayerBlock(@Nullable IBlockState state, @Nullable Block block) {
            return block != Blocks.STRUCTURE_BLOCK
                && StructureNBTParser.StructureParseExtension.super.shouldStoreLayerBlock(state, block);
        }

        @Override
        public void handleBlockEntity(StructureNBTParser.ParsedStructureBuilder builder,
                NBTTagCompound blockEntry, @Nullable IBlockState state, @Nullable Block block,
                NBTTagCompound nbtData) {
            if (block != Blocks.STRUCTURE_BLOCK) {
                StructureNBTParser.handleDefaultBlockEntity(builder, state, block, nbtData);
                return;
            }

            String marker = readMarkerName(nbtData);
            if (marker.isEmpty()) return;

            applyMarker(builder, blockEntry, marker);
        }

        // TODO: Should organize it better, like a static mapping of structure + marker start -> entry to add,
        //       instead of a big switch statement
        /**
         * BOMD encodes raid-relevant content in structure-block markers and populates the real
         * spawners, bosses, trader NPCs, and loot tables at generation time. Raw template NBT
         * alone only gives the shell, so the provider has to replay those marker semantics here.
         * We populate the structure info with the same content that would be generated in-world.
         */
        private void applyMarker(StructureNBTParser.ParsedStructureBuilder builder,
                NBTTagCompound blockEntry, String marker) {
            switch (definition.path) {
                case "blossom_cave":
                    if (marker.startsWith("boss")) addEntity(builder, ENTITY_VOID_BLOSSOM);
                    return;

                case "rotten_hold":
                    if (marker.startsWith("set_mob")) {
                        addSpawner(builder, blockEntry, ENTITY_ANCIENT_KNIGHT);
                    } else if (marker.startsWith("mob")) {
                        addSpawner(builder, blockEntry, ENTITY_ANCIENT_KNIGHT, ENTITY_ANCIENT_KNIGHT_RAPIER);
                    } else if (marker.startsWith("key_chest")) {
                        addLoot(builder, new ResourceLocation(MOD_ID, "rot_hold_key"));
                    } else if (marker.startsWith("chest")) {
                        addLoot(builder, lootTableId("rot_hold"));
                    }
                    return;

                case "night_lich_tower":
                    if (marker.startsWith("ice_chest") || marker.startsWith("chest")) {
                        addLoot(builder, new ResourceLocation(MOD_ID, "lich_tower"));
                    }
                    return;

                case "burning_flame_arena":
                    if (marker.startsWith("boss")) {
                        addEntity(builder, ENTITY_NETHER_PYRE);
                    } else if (marker.startsWith("set_mob")) {
                        addSpawner(builder, blockEntry, ENTITY_NETHER_ABERRANT, ENTITY_INCENDIUM);
                    } else if (marker.startsWith("mob")) {
                        addSpawner(builder, blockEntry, ENTITY_NETHER_ABERRANT, ENTITY_INCENDIUM, ENTITY_BAREANT);
                    } else if (marker.startsWith("spirit")) {
                        addSpawner(builder, blockEntry, ENTITY_NETHER_ABERRANT, ENTITY_BAREANT);
                    } else if (marker.startsWith("orb")) {
                        addEntity(builder, ENTITY_VOLATILE_ORB);
                    } else if (marker.startsWith("key_chest")) {
                        addLoot(builder, new ResourceLocation(MOD_ID, "arena_key_chest"));
                    } else if (marker.startsWith("chest")) {
                        addLoot(builder, lootTableId("flame_arena_chests"));
                    }
                    return;

                case "frozen_castle":
                    if (marker.startsWith("big_mob_set") || marker.startsWith("big_mob_double") || marker.startsWith("big_mob")) {
                        addSpawner(builder, blockEntry, ENTITY_ELITE_DRAUGR);
                    } else if (marker.startsWith("mob")) {
                        addSpawner(builder, blockEntry, ENTITY_DRAUGR, ENTITY_DRAUGR_RANGER, ENTITY_WYRK);
                    } else if (marker.startsWith("secret_chest")) {
                        addLoot(builder, lootTableId("frozen_castle_secret"));
                    } else if (marker.startsWith("key_chest")) {
                        addLoot(builder, new ResourceLocation(MOD_ID, "frozen_castle_key"));
                    } else if (marker.startsWith("chest")) {
                        addLoot(builder, lootTableId("frozen_castle"));
                    }
                    return;

                case "high_court_city":
                    if (marker.startsWith("mob")) {
                        addSpawner(builder, blockEntry, ENTITY_TRIDENT_GARGOYLE, ENTITY_MAGE_GARGOYLE);
                    } else if (marker.startsWith("knight_ranged")) {
                        addSpawner(builder, blockEntry, ENTITY_IMPERIAL_SWORD);
                    } else if (marker.startsWith("knight")) {
                        addSpawner(builder, blockEntry, ENTITY_IMPERIAL_HALBERD);
                    } else if (marker.startsWith("tornado")) {
                        addEntity(builder, ENTITY_SKY_TORNADO);
                    } else if (marker.startsWith("key_chest")) {
                        addLoot(builder, new ResourceLocation(MOD_ID, "high_court_city_key"));
                    } else if (marker.startsWith("chest")) {
                        addLoot(builder, lootTableId("high_court_city"));
                    }
                    return;

                case "forgotten_temple":
                    if (marker.startsWith("surface_mob")) {
                        addSpawner(builder, blockEntry, ENTITY_SCUTTER_BEETLE);
                    } else if (marker.startsWith("mob")) {
                        addSpawner(builder, blockEntry, ENTITY_SCUTTER_BEETLE, ENTITY_AEGYPTIA);
                    } else if (marker.startsWith("royal")) {
                        addSpawner(builder, blockEntry, ENTITY_EVERATOR);
                    } else if (marker.startsWith("crypt_chest")) {
                        addLoot(builder, lootTableId("crypt_forgotten_temple"));
                    } else if (marker.startsWith("puzzle_chest")) {
                        addLoot(builder, lootTableId("forgotten_temple_puzzle"));
                    } else if (marker.startsWith("key_chest")) {
                        addLoot(builder, new ResourceLocation(MOD_ID, "forgotten_temple_key"));
                    } else if (marker.startsWith("chest")) {
                        addLoot(builder, lootTableId("forgotten_temple"));
                    }
                    return;

                case "obsidilith_arena":
                    if (marker.startsWith("elite_mob")) {
                        addSpawner(builder, blockEntry, ENTITY_ENDERPHRITE, ENTITY_ENDERPHRITE_GAUNTLET);
                    } else if (marker.startsWith("mob")) {
                        addSpawner(builder, blockEntry, ENTITY_VOIDIANT);
                    } else if (marker.startsWith("key_chest")) {
                        addLoot(builder, new ResourceLocation(MOD_ID, "obsidian_arena_key"));
                    } else if (marker.startsWith("chest")) {
                        addLoot(builder, new ResourceLocation(MOD_ID, "obsidian_arena"));
                    }
                    return;

                case "gaelon_sanctuary":
                    if (marker.startsWith("elite")) {
                        addSpawner(builder, blockEntry, ENTITY_CURSED_SENTINEL);
                    } else if (marker.startsWith("mob")) {
                        addSpawner(builder, blockEntry, ENTITY_REANIMATE);
                    } else if (marker.startsWith("boss")) {
                        addEntity(builder, ENTITY_APATHYR);
                    } else if (marker.startsWith("chest")) {
                        addLoot(builder, lootTableId("gaelon_dungeon"));
                    }
                    return;

                case "trader_post":
                    if (marker.startsWith("trader")) addEntity(builder, ENTITY_MYSTERIOUS_TRADER);
                    return;

                case "dark_ruins":
                    if (marker.startsWith("dauntless")) addEntity(builder, ENTITY_DARK_ORB);
                    return;

                case "end_outpost":
                    if (marker.startsWith("e_elite_mob")) {
                        addSpawner(builder, blockEntry, ENTITY_ENDERPHRITE, ENTITY_ENDERPHRITE_GAUNTLET);
                    } else if (marker.startsWith("e_mob")) {
                        addSpawner(builder, blockEntry, ENTITY_VOIDIANT);
                    } else if (marker.startsWith("e_treasure")) {
                        addLoot(builder, lootTableId("end_outpost_treasure"));
                    } else if (marker.startsWith("e_chest")) {
                        addLoot(builder, new ResourceLocation(MOD_ID, "end_outpost"));
                    }
                    return;

                default:
                    return;
            }
        }

        private void addSpawner(StructureNBTParser.ParsedStructureBuilder builder,
                NBTTagCompound blockEntry, ResourceLocation... entityIds) {
            IBlockState spawnerState = Blocks.MOB_SPAWNER.getDefaultState();
            builder.addBlockCount(
                StructureNBTParser.createDisplayedBlockKey(
                    spawnerState, null, StructureNBTParser.createDisplayStack(spawnerState)
                ),
                spawnerState
            );

            BlockPos markerPos = readMarkerPos(blockEntry);
            builder.setLayerBlock(markerPos.getX(), markerPos.getY(), markerPos.getZ(), spawnerState);

            for (ResourceLocation entityId : entityIds) builder.addEntity(entityId, true);
        }

        private BlockPos readMarkerPos(NBTTagCompound blockEntry) {
            NBTTagList posTag = blockEntry.getTagList("pos", Constants.NBT.TAG_INT);
            return new BlockPos(posTag.getIntAt(0), posTag.getIntAt(1), posTag.getIntAt(2));
        }

        private String readMarkerName(NBTTagCompound nbtData) {
            if (nbtData.hasKey("metadata", Constants.NBT.TAG_STRING)) {
                return nbtData.getString("metadata");
            }

            if (nbtData.hasKey("name", Constants.NBT.TAG_STRING)) {
                return nbtData.getString("name");
            }

            return "";
        }
    }

    private static final class StructureDefinition {
        private final String path;
        private final String searchPredicateMethod;
        @Nullable
        private final String searchDistanceField;
        private final int fallbackSearchRadiusChunks;
        @Nullable
        private final String spacingField;
        @Nullable
        private final String dimensionArrayField;
        private final List<String> templateRoots;
        private final List<ResourceLocation> manualEntities;
        @Nullable
        private final Integer fixedDimension;

        private StructureDefinition(String path, String searchPredicateMethod,
                @Nullable String searchDistanceField, int fallbackSearchRadiusChunks,
                @Nullable String spacingField, @Nullable String dimensionArrayField,
                List<String> templateRoots, List<ResourceLocation> manualEntities) {
            this(path, searchPredicateMethod, searchDistanceField, fallbackSearchRadiusChunks,
                spacingField, dimensionArrayField, templateRoots, manualEntities, null);
        }

        private StructureDefinition(String path, String searchPredicateMethod,
                @Nullable String searchDistanceField, int fallbackSearchRadiusChunks,
                @Nullable String spacingField, @Nullable String dimensionArrayField,
                List<String> templateRoots, List<ResourceLocation> manualEntities,
                @Nullable Integer fixedDimension) {
            this.path = path;
            this.searchPredicateMethod = searchPredicateMethod;
            this.searchDistanceField = searchDistanceField;
            this.fallbackSearchRadiusChunks = fallbackSearchRadiusChunks;
            this.spacingField = spacingField;
            this.dimensionArrayField = dimensionArrayField;
            this.templateRoots = templateRoots;
            this.manualEntities = manualEntities;
            this.fixedDimension = fixedDimension;
        }

        private int resolveSearchRadiusChunks(@Nullable Class<?> modConfigClass, @Nullable Class<?> worldConfigClass) {
            int radius = fallbackSearchRadiusChunks;

            if (modConfigClass != null && searchDistanceField != null) {
                radius = Math.max(radius, ReflectionHelper.readStaticIntField(modConfigClass, searchDistanceField, radius));
            }

            if (worldConfigClass != null && spacingField != null) {
                int spacing = ReflectionHelper.readStaticIntField(worldConfigClass, spacingField, -1);
                if (spacing > 0) radius = Math.max(radius, spacing * 4);
            }

            radius = Math.max(radius, MIN_SEARCH_RADIUS_CHUNKS);
            return Math.min(radius, MAX_SEARCH_RADIUS_CHUNKS);
        }

        @Nullable
        private Set<DimensionInfo> resolveDimensions(@Nullable Class<?> worldConfigClass) {
            Set<DimensionInfo> dimensions = new LinkedHashSet<>();

            if (fixedDimension != null) dimensions.add(new DimensionInfo(fixedDimension));
            if (worldConfigClass != null && dimensionArrayField != null) {
                Object value = ReflectionHelper.getStaticFieldOrNull(worldConfigClass, dimensionArrayField);
                if (value instanceof int[]) {
                    for (int dimensionId : (int[]) value) dimensions.add(new DimensionInfo(dimensionId));
                }
            }

            return dimensions.isEmpty() ? null : dimensions;
        }

        @Nullable
        private LocalizedText resolveRarity(@Nullable Class<?> worldConfigClass) {
            if (worldConfigClass == null || spacingField == null) return null;

            int spacing = ReflectionHelper.readStaticIntField(worldConfigClass, spacingField, -1);
            if (spacing <= 0) return null;

            return RarityTextHelper.oneInChunks((long) spacing * spacing);
        }
    }

    private static final class ScanOutcome {
        private final List<BlockPos> positions;
        private final boolean timedOut;

        private ScanOutcome(List<BlockPos> positions, boolean timedOut) {
            this.positions = positions;
            this.timedOut = timedOut;
        }
    }

    @Nullable
    private StructureLocation findNearestStable(World world, StructureDefinition definition, BlockPos origin,
            int skipCount, @Nullable Predicate<BlockPos> locationFilter) {
        List<BlockPos> validPositions = new ArrayList<>();
        int originChunkX = origin.getX() >> CHUNK_COORDINATE_SHIFT;
        int originChunkZ = origin.getZ() >> CHUNK_COORDINATE_SHIFT;
        int maxRadiusChunks = definition.resolveSearchRadiusChunks(modConfigClass, worldConfigClass);
        long startTime = System.currentTimeMillis();

        for (int radius = 0; radius <= maxRadiusChunks; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    if (System.currentTimeMillis() - startTime > MAX_SCAN_TIME_MS) {
                        return buildNearestLocation(validPositions, origin, skipCount);
                    }

                    int chunkX = originChunkX + dx;
                    int chunkZ = originChunkZ + dz;
                    if (!matchesStructure(definition, world, chunkX, chunkZ)) continue;

                    BlockPos candidate = new BlockPos((chunkX << CHUNK_COORDINATE_SHIFT) + 8, 0,
                        (chunkZ << CHUNK_COORDINATE_SHIFT) + 8);
                    if (locationFilter != null && !locationFilter.test(candidate)) continue;

                    validPositions.add(candidate);
                }
            }

            if (validPositions.size() <= skipCount) continue;

            PositionHelper.sortByHorizontalDistance(validPositions, origin);
            long requiredDistanceSq = PositionHelper.horizontalDistanceSquared(validPositions.get(skipCount), origin);
            long futureMinDistanceSq = minimumFutureDistanceSquared(origin, originChunkX, originChunkZ, radius + 1);

            if (radius >= maxRadiusChunks || requiredDistanceSq <= futureMinDistanceSq) {
                return new StructureLocation(validPositions.get(skipCount), skipCount, validPositions.size(), true);
            }
        }

        return buildNearestLocation(validPositions, origin, skipCount);
    }

    @Nullable
    private StructureLocation buildNearestLocation(List<BlockPos> validPositions, BlockPos origin, int skipCount) {
        if (validPositions.isEmpty()) return null;

        PositionHelper.sortByHorizontalDistance(validPositions, origin);
        if (validPositions.size() <= skipCount) return null;

        return new StructureLocation(validPositions.get(skipCount), skipCount, validPositions.size(), true);
    }

    private long minimumFutureDistanceSquared(BlockPos origin, int originChunkX, int originChunkZ, int nextRadius) {
        if (nextRadius <= 0) return 0L;

        long minDistanceSq = Long.MAX_VALUE;
        for (int dx = -nextRadius; dx <= nextRadius; dx++) {
            for (int dz = -nextRadius; dz <= nextRadius; dz++) {
                if (Math.abs(dx) != nextRadius && Math.abs(dz) != nextRadius) continue;

                BlockPos candidate = new BlockPos(
                    ((originChunkX + dx) << CHUNK_COORDINATE_SHIFT) + 8,
                    0,
                    ((originChunkZ + dz) << CHUNK_COORDINATE_SHIFT) + 8
                );
                long distanceSq = PositionHelper.horizontalDistanceSquared(candidate, origin);
                if (distanceSq < minDistanceSq) minDistanceSq = distanceSq;
            }
        }

        return minDistanceSq;
    }
}