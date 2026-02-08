package com.simplestructurescanner.structure.pillar;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.common.base.Optional;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureInfo.StructureLayer;
import com.simplestructurescanner.structure.util.ReflectionHelper;


/**
 * Parses Pillar structure NBT files to extract block, entity, and loot data.
 * <p>
 * Pillar structures are stored as external NBT files in the pillar/structures directory.
 * This parser reads those files and extracts:
 * - Block counts and layer data for visualization
 * - Entities from the NBT
 * - Data blocks that reference loot tables and spawners
 */
public class PillarNBTParser {

    // Pattern to extract loot table from chest data block: "chest [facing] loot_table"
    private static final Pattern CHEST_PATTERN = Pattern.compile("chest\\s+(?:(north|south|east|west)\\s+)?(.+)", Pattern.CASE_INSENSITIVE);

    // Pattern to extract entity from spawner data block: "spawner entity_id"
    private static final Pattern SPAWNER_PATTERN = Pattern.compile("spawner\\s+(.+)", Pattern.CASE_INSENSITIVE);

    // Pattern to extract load_loot_table command: "load_loot_table loot_table"
    private static final Pattern LOAD_LOOT_TABLE_PATTERN = Pattern.compile("load_loot_table\\s+(.+)", Pattern.CASE_INSENSITIVE);

    // Pattern to extract entity from "run summon" or "run /summon" commands: "run [/]summon entity_id [pos] [nbt]"
    private static final Pattern RUN_SUMMON_PATTERN = Pattern.compile("run\\s+/?summon\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    // Matches Pillar function calls like $rand_s(arg1;arg2)$ or $rand_i(1;10)$
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("\\$(\\w+)\\(([^)]*)\\)\\$");

    private static File pillarStructureDir = null;

    /**
     * Result of parsing a Pillar structure NBT file.
     */
    public static class ParsedPillarStructure {
        public final int sizeX, sizeY, sizeZ;
        public final List<BlockEntry> blocks;
        public final List<StructureLayer> layers;
        public final List<EntityEntry> entities;
        public final List<LootEntry> lootTables;

        public ParsedPillarStructure(int sizeX, int sizeY, int sizeZ,
                                      List<BlockEntry> blocks,
                                      List<StructureLayer> layers,
                                      List<EntityEntry> entities,
                                      List<LootEntry> lootTables) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.blocks = blocks;
            this.layers = layers;
            this.entities = entities;
            this.lootTables = lootTables;
        }
    }

    /**
     * Parse a Pillar structure NBT file.
     *
     * @param structureName The structure name (e.g., "dungeon/room1")
     * @return Parsed structure data or null if parsing fails
     */
    @Nullable
    public static ParsedPillarStructure parseStructure(String structureName) {
        File pillarDir = getPillarStructureDir();
        if (pillarDir == null) {
            SimpleStructureScanner.LOGGER.debug("Pillar structure directory not found");
            return null;
        }

        // Convert structure name to file path (structure names use "/" for subdirectories)
        String filePath = structureName + ".nbt";
        File nbtFile = new File(pillarDir, filePath);

        if (!nbtFile.exists()) {
            SimpleStructureScanner.LOGGER.debug("Pillar structure file not found: {}", nbtFile.getAbsolutePath());
            return null;
        }

        try (InputStream stream = new FileInputStream(nbtFile)) {
            NBTTagCompound nbt = CompressedStreamTools.readCompressed(stream);
            return parseNBT(nbt);
        } catch (IOException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to parse Pillar structure {}: {}", structureName, e.getMessage());
            return null;
        }
    }

    /**
     * Get the Pillar structures directory via reflection.
     */
    @Nullable
    private static File getPillarStructureDir() {
        if (pillarStructureDir != null) return pillarStructureDir;

        try {
            Class<?> pillarClass = ReflectionHelper.loadClassRequired("vazkii.pillar.Pillar");
            pillarStructureDir = (File) ReflectionHelper.getStaticField(pillarClass, "structureDir");

            return pillarStructureDir;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug("Failed to get Pillar structure directory", e);
            return null;
        }
    }

    /**
     * Parse structure data from an NBT compound.
     */
    @Nullable
    private static ParsedPillarStructure parseNBT(NBTTagCompound nbt) {
        if (!nbt.hasKey("size") || !nbt.hasKey("palette") || !nbt.hasKey("blocks")) return null;

        // Read size
        NBTTagList sizeTag = nbt.getTagList("size", Constants.NBT.TAG_INT);
        int sizeX = sizeTag.getIntAt(0);
        int sizeY = sizeTag.getIntAt(1);
        int sizeZ = sizeTag.getIntAt(2);

        // Read palette (block state definitions)
        NBTTagList paletteTag = nbt.getTagList("palette", Constants.NBT.TAG_COMPOUND);
        IBlockState[] palette = new IBlockState[paletteTag.tagCount()];

        for (int i = 0; i < paletteTag.tagCount(); i++) {
            NBTTagCompound blockTag = paletteTag.getCompoundTagAt(i);
            palette[i] = parseBlockState(blockTag);
        }

        // Read blocks and count occurrences
        // Key by Block identity (registry name + damageDropped) to merge different orientations
        NBTTagList blocksTag = nbt.getTagList("blocks", Constants.NBT.TAG_COMPOUND);
        Map<Block, Integer> blockCounts = new HashMap<>();
        Map<Block, IBlockState> blockRepresentatives = new HashMap<>();

        // Create layer data structure
        Map<Integer, IBlockState[][]> layerBlocks = new HashMap<>();
        for (int y = 0; y < sizeY; y++) layerBlocks.put(y, new IBlockState[sizeX][sizeZ]);

        // Track data blocks for loot tables and spawners
        Set<ResourceLocation> lootTableIds = new HashSet<>();
        Map<ResourceLocation, Integer> spawnerEntities = new HashMap<>();
        Map<ResourceLocation, Integer> summonEntities = new HashMap<>();

        // Track direct container items (chests with items baked in, not loot tables)
        List<ItemStack> directContainerItems = new ArrayList<>();

        for (int i = 0; i < blocksTag.tagCount(); i++) {
            NBTTagCompound blockEntry = blocksTag.getCompoundTagAt(i);
            int paletteIndex = blockEntry.getInteger("state");

            if (paletteIndex >= 0 && paletteIndex < palette.length) {
                IBlockState state = palette[paletteIndex];
                Block block = state != null ? state.getBlock() : null;

                boolean isInvisible = block == null || block == Blocks.AIR || block == Blocks.STRUCTURE_VOID;
                boolean isStructureBlock = block == Blocks.STRUCTURE_BLOCK;

                // Count visible, non-structural blocks
                if (!isInvisible && !isStructureBlock) {
                    blockCounts.merge(block, 1, Integer::sum);

                    // Keep the first state seen as the representative for display purposes
                    if (!blockRepresentatives.containsKey(block)) blockRepresentatives.put(block, state);
                }

                // Store in layer data (exclude structure blocks — they are metadata carriers, not real blocks)
                NBTTagList posTag = blockEntry.getTagList("pos", Constants.NBT.TAG_INT);
                int x = posTag.getIntAt(0);
                int y = posTag.getIntAt(1);
                int z = posTag.getIntAt(2);

                if (y >= 0 && y < sizeY && x >= 0 && x < sizeX && z >= 0 && z < sizeZ) {
                    if (!isStructureBlock) {
                        layerBlocks.get(y)[x][z] = state;
                    }
                }

                // Check for data blocks (structure blocks in DATA mode carry metadata commands)
                if (blockEntry.hasKey("nbt")) {
                    NBTTagCompound nbtData = blockEntry.getCompoundTag("nbt");

                    if (nbtData.hasKey("metadata")) {
                        String metadata = nbtData.getString("metadata");
                        parseDataBlockMetadata(metadata, lootTableIds, spawnerEntities, summonEntities);
                    }

                    // Detect spawner mobs from tile entity NBT (mob_spawner blocks with SpawnData)
                    if (block == Blocks.MOB_SPAWNER) parseSpawnerTileEntityNBT(nbtData, spawnerEntities);

                    // Detect loot tables from container tile entity NBT (chests, etc. with LootTable tag)
                    if (nbtData.hasKey("LootTable")) {
                        String lootTable = nbtData.getString("LootTable");
                        if (!lootTable.isEmpty()) lootTableIds.add(new ResourceLocation(lootTable));
                    }

                    // Detect direct items in container inventories (chests with baked-in items, not loot tables)
                    // Instantiate the tile entity and let it deserialize its own NBT format
                    extractContainerItems(state, nbtData, directContainerItems);
                }
            }
        }

        // Convert block counts to BlockEntry list
        List<BlockEntry> blocks = new ArrayList<>();
        for (Map.Entry<Block, Integer> entry : blockCounts.entrySet()) {
            Block block = entry.getKey();
            int count = entry.getValue();
            IBlockState representative = blockRepresentatives.get(block);
            ItemStack displayStack = createDisplayStack(representative);
            blocks.add(new BlockEntry(representative, displayStack, count));
        }

        // Sort by count descending
        blocks.sort((a, b) -> Integer.compare(b.count, a.count));

        // Convert layer blocks to StructureLayer list
        List<StructureLayer> layers = new ArrayList<>();
        for (int y = 0; y < sizeY; y++) {
            StructureLayer layer = new StructureLayer(y, sizeX, sizeZ);
            IBlockState[][] yLayer = layerBlocks.get(y);

            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    if (yLayer[x][z] != null) layer.setBlockState(x, z, yLayer[x][z]);
                }
            }

            layers.add(layer);
        }

        // Parse entities from NBT
        List<EntityEntry> entities = new ArrayList<>();
        if (nbt.hasKey("entities")) {
            NBTTagList entitiesTag = nbt.getTagList("entities", Constants.NBT.TAG_COMPOUND);
            Map<String, Integer> entityCounts = new HashMap<>();

            for (int i = 0; i < entitiesTag.tagCount(); i++) {
                NBTTagCompound entityTag = entitiesTag.getCompoundTagAt(i);
                if (entityTag.hasKey("nbt")) {
                    NBTTagCompound entityNbt = entityTag.getCompoundTag("nbt");
                    String entityId = entityNbt.getString("id");
                    if (!entityId.isEmpty()) entityCounts.merge(entityId, 1, Integer::sum);
                }
            }

            for (Map.Entry<String, Integer> entry : entityCounts.entrySet()) {
                entities.add(new EntityEntry(new ResourceLocation(entry.getKey()), entry.getValue(), false));
            }
        }

        for (Map.Entry<ResourceLocation, Integer> entry : summonEntities.entrySet()) {
            entities.add(new EntityEntry(entry.getKey(), entry.getValue(), false));
        }

        // Add spawner entities (marked as spawner=true)
        for (Map.Entry<ResourceLocation, Integer> entry : spawnerEntities.entrySet()) {
            entities.add(new EntityEntry(entry.getKey(), entry.getValue(), true));
        }

        // Convert loot table IDs to LootEntry list
        List<LootEntry> lootTables = new ArrayList<>();
        for (ResourceLocation lootTableId : lootTableIds) {
            // Use empty drops list - actual drops will be resolved by the LootTableResolver
            lootTables.add(new LootEntry(lootTableId, new ArrayList<>(), "gui.structurescanner.loot.chest"));
        }

        // Add direct container items as a special "loot" entry if any were found
        if (!directContainerItems.isEmpty()) {
            // Deduplicate and merge item stacks by item type
            List<ItemStack> mergedItems = mergeItemStacks(directContainerItems);
            lootTables.add(new LootEntry(
                    new ResourceLocation("structurescanner", "direct_items"),
                    mergedItems,
                    "gui.structurescanner.loot.container"));
        }

        return new ParsedPillarStructure(sizeX, sizeY, sizeZ, blocks, layers, entities, lootTables);
    }

    /**
     * Parse data block metadata to extract loot tables and spawner entities.
     * <p>
     * Pillar data block metadata strings can contain function calls like
     * {@code $rand_s(value1;weight1;value2;weight2)$} that randomly select
     * a value at generation time. We extract <em>all</em> possible values
     * from these functions so that every potential loot table or entity is
     * discovered.
     *
     * @param metadata       The data block metadata string
     * @param lootTableIds   Set to add discovered loot table IDs to
     * @param spawnerEntities Map to add spawner entity counts to
     */
    private static void parseDataBlockMetadata(String metadata, Set<ResourceLocation> lootTableIds,
                                               Map<ResourceLocation, Integer> spawnerEntities,
                                               Map<ResourceLocation, Integer> summonEntities) {
        if (metadata == null || metadata.isEmpty()) return;

        // Collect all possible expanded variants of the metadata string.
        // Pillar's $rand_s(a;w1;b;w2)$ picks one at random; we want all of them.
        List<String> variants = expandFunctions(metadata);

        for (String variant : variants) {
            // Strip Pillar comments (/** marks "commented out" via $run_if()$)
            variant = variant.replaceAll("/\\*\\*.*", "").trim();
            if (variant.isEmpty()) continue;

            // Check for chest command
            Matcher chestMatcher = CHEST_PATTERN.matcher(variant);
            if (chestMatcher.find()) {
                String lootTable = chestMatcher.group(2).trim();
                if (!lootTable.isEmpty()) lootTableIds.add(new ResourceLocation(lootTable));

                continue;
            }

            // Check for spawner command
            Matcher spawnerMatcher = SPAWNER_PATTERN.matcher(variant);
            if (spawnerMatcher.find()) {
                String entityId = spawnerMatcher.group(1).trim();
                if (!entityId.isEmpty()) spawnerEntities.merge(new ResourceLocation(entityId), 1, Integer::sum);

                continue;
            }

            // Check for "run summon" or "run /summon" command (used to spawn entities at structure generation)
            Matcher runSummonMatcher = RUN_SUMMON_PATTERN.matcher(variant);
            if (runSummonMatcher.find()) {
                String entityId = runSummonMatcher.group(1).trim();
                if (!entityId.isEmpty()) summonEntities.merge(new ResourceLocation(entityId), 1, Integer::sum);

                continue;
            }

            // Check for load_loot_table command
            Matcher loadLootMatcher = LOAD_LOOT_TABLE_PATTERN.matcher(variant);
            if (loadLootMatcher.find()) {
                String lootTable = loadLootMatcher.group(1).trim();
                if (!lootTable.isEmpty()) lootTableIds.add(new ResourceLocation("pillar", lootTable));
            }
        }
    }

    /**
     * Expand Pillar function calls in a metadata string into all possible
     * concrete variants. Each {@code $rand_s(...)$} call produces one variant
     * per possible string value; {@code $rand_i(...)$} and {@code $run_if(...)$}
     * are replaced with a representative value so the rest of the string can
     * still be parsed.
     * <p>
     * For example, {@code "chest north $rand_s(a;1;b;1)$"} expands to
     * {@code ["chest north a", "chest north b"]}.
     *
     * @return A list of all concrete metadata string variants. If the input
     *         contains no functions, returns a single-element list.
     */
    private static List<String> expandFunctions(String metadata) {
        // Start with the original string as the single seed variant
        List<String> current = new ArrayList<>();
        current.add(metadata);

        // Repeatedly find and expand the first function in each variant
        // until no more functions remain
        boolean changed = true;
        while (changed) {
            changed = false;
            List<String> next = new ArrayList<>();

            for (String variant : current) {
                Matcher m = FUNCTION_PATTERN.matcher(variant);

                if (!m.find()) {
                    next.add(variant);
                    continue;
                }

                changed = true;
                String funcName = m.group(1).toLowerCase();
                String paramsStr = m.group(2);
                String prefix = variant.substring(0, m.start());
                String suffix = variant.substring(m.end());

                // Semicolons separate parameters; backslash-escaped semicolons are literal
                String[] params = paramsStr.split("\\s*(?<!\\\\);\\s*");

                if ("rand_s".equals(funcName)) {
                    // rand_s takes pairs: (value, weight, value, weight, ...)
                    // Extract all even-indexed params (the string values)
                    for (int i = 0; i < params.length; i += 2) next.add(prefix + params[i] + suffix);
                } else if ("rand_i".equals(funcName) && params.length == 2) {
                    // rand_i(min, max) — use the midpoint as a representative value
                    try {
                        int lower = Integer.parseInt(params[0].trim());
                        int upper = Integer.parseInt(params[1].trim());
                        next.add(prefix + ((lower + upper) / 2) + suffix);
                    } catch (NumberFormatException e) {
                        next.add(prefix + "0" + suffix);
                    }
                } else if ("run_if".equals(funcName)) {
                    // run_if(chance) — conditionally inserts "/**" to comment out the rest.
                    // We want to see the content regardless, so replace with empty string.
                    next.add(prefix + suffix);
                } else {
                    // Unknown function — remove it and hope for the best
                    next.add(prefix + suffix);
                }
            }

            current = next;
        }

        return current;
    }

    /**
     * Parse spawner tile entity NBT to extract mob types.
     * Handles both {@code SpawnData} (single entity) and
     * {@code SpawnPotentials} (weighted list of entities).
     */
    private static void parseSpawnerTileEntityNBT(NBTTagCompound nbt, Map<ResourceLocation, Integer> spawnerEntities) {
        Set<String> foundIds = new HashSet<>();

        // SpawnPotentials is a weighted list of possible spawns; preferred over SpawnData
        if (nbt.hasKey("SpawnPotentials", Constants.NBT.TAG_LIST)) {
            NBTTagList potentials = nbt.getTagList("SpawnPotentials", Constants.NBT.TAG_COMPOUND);

            for (int i = 0; i < potentials.tagCount(); i++) {
                NBTTagCompound potential = potentials.getCompoundTagAt(i);

                if (potential.hasKey("Entity", Constants.NBT.TAG_COMPOUND)) {
                    String id = potential.getCompoundTag("Entity").getString("id");
                    if (!id.isEmpty()) foundIds.add(id);
                }
            }
        }

        // Fallback: SpawnData holds the currently selected entity
        if (foundIds.isEmpty() && nbt.hasKey("SpawnData", Constants.NBT.TAG_COMPOUND)) {
            String id = nbt.getCompoundTag("SpawnData").getString("id");
            if (!id.isEmpty()) foundIds.add(id);
        }

        for (String id : foundIds) spawnerEntities.merge(new ResourceLocation(id), 1, Integer::sum);
    }

    /**
     * Extract items from a container by instantiating its tile entity and reading via IInventory/IItemHandler.
     * This allows any mod's container to work regardless of its internal NBT format.
     */
    private static void extractContainerItems(IBlockState state, NBTTagCompound nbtData, List<ItemStack> outItems) {
        Block block = state.getBlock();
        if (block == null || !block.hasTileEntity(state)) return;

        World world = Minecraft.getMinecraft().world;
        TileEntity tileEntity;

        try {
            tileEntity = block.createTileEntity(world, state);
            if (tileEntity == null) return;

            tileEntity.readFromNBT(nbtData);
        } catch (Exception e) {
            // Some tile entities may fail without a proper world context; fall back silently
            return;
        }

        try {
            // Try IItemHandler capability first (Forge's preferred inventory API)
            IItemHandler itemHandler = tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
            if (itemHandler == null) {
                itemHandler = tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            }

            if (itemHandler != null) {
                for (int i = 0; i < itemHandler.getSlots(); i++) {
                    ItemStack stack = itemHandler.getStackInSlot(i);
                    if (!stack.isEmpty()) outItems.add(stack.copy());
                }

                return;
            }
        } catch (Exception e) {
            // Some capabilities may fail without a proper world context; fall back to IInventory
        }

        // Fall back to IInventory (vanilla interface)
        if (tileEntity instanceof IInventory) {
            IInventory inventory = (IInventory) tileEntity;

            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty()) outItems.add(stack.copy());
            }
        }
    }

    /**
     * Merge item stacks by item type, combining counts for identical items.
     * Returns a deduplicated list sorted by total count descending.
     */
    private static List<ItemStack> mergeItemStacks(List<ItemStack> items) {
        // Use a map keyed by item identity (registry name + damage + NBT tag hash)
        Map<String, ItemStack> merged = new HashMap<>();

        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;

            String key = getItemKey(stack);
            ItemStack existing = merged.get(key);

            if (existing != null) {
                existing.grow(stack.getCount());
            } else {
                merged.put(key, stack.copy());
            }
        }

        // Sort by count descending
        List<ItemStack> result = new ArrayList<>(merged.values());
        result.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));

        return result;
    }

    /**
     * Create a unique key for an item stack based on item, damage, and NBT.
     */
    private static String getItemKey(ItemStack stack) {
        StringBuilder key = new StringBuilder();
        key.append(stack.getItem().getRegistryName());
        key.append('@').append(stack.getMetadata());

        if (stack.hasTagCompound()) {
            key.append('#').append(stack.getTagCompound().hashCode());
        }

        return key.toString();
    }

    /**
     * Parse an IBlockState from NBT palette entry.
     */
    private static IBlockState parseBlockState(NBTTagCompound nbt) {
        String blockName = nbt.getString("Name");
        Block block = Block.REGISTRY.getObject(new ResourceLocation(blockName));

        if (block == null || block == Blocks.AIR) return Blocks.AIR.getDefaultState();

        IBlockState state = block.getDefaultState();

        // Parse properties if present
        if (nbt.hasKey("Properties")) {
            NBTTagCompound props = nbt.getCompoundTag("Properties");
            for (String key : props.getKeySet()) {
                String value = props.getString(key);
                state = applyProperty(state, key, value);
            }
        }

        return state;
    }

    /**
     * Apply a block state property from string key/value.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState applyProperty(IBlockState state, String propertyName, String value) {
        for (IProperty property : state.getPropertyKeys()) {
            if (property.getName().equals(propertyName)) {
                Optional<?> parsedValue = property.parseValue(value);
                if (parsedValue.isPresent()) {
                    return state.withProperty(property, (Comparable) parsedValue.get());
                }
            }
        }

        return state;
    }

    private static final Random RANDOM = new Random();

    /**
     * Create a display ItemStack for a block state.
     */
    private static ItemStack createDisplayStack(IBlockState state) {
        Block block = state.getBlock();

        if (block == null || block == Blocks.AIR || block == Blocks.STRUCTURE_VOID) {
            return ItemStack.EMPTY;
        }

        try {
            // Strategy 1: Use Item.getItemFromBlock with damageDropped
            Item blockItem = Item.getItemFromBlock(block);
            if (blockItem != null && blockItem != Items.AIR) {
                int damage = block.damageDropped(state);
                ItemStack stack = new ItemStack(blockItem, 1, damage);
                if (!stack.isEmpty()) return stack;
            }

            // Strategy 2: Use getItemDropped
            Item droppedItem = block.getItemDropped(state, RANDOM, 0);
            if (droppedItem != null && droppedItem != Items.AIR) {
                int damage = block.damageDropped(state);
                ItemStack stack = new ItemStack(droppedItem, 1, damage);
                if (!stack.isEmpty()) return stack;
            }

            // Strategy 3: Fallback to direct ItemStack creation
            int meta = block.getMetaFromState(state);
            ItemStack fallback = new ItemStack(block, 1, meta);
            if (!fallback.isEmpty() && fallback.getItem() != Items.AIR) return fallback;

            // Last resort: try meta 0
            fallback = new ItemStack(block, 1, 0);
            if (!fallback.isEmpty() && fallback.getItem() != Items.AIR) return fallback;

            return ItemStack.EMPTY;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
