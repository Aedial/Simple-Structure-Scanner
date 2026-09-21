package com.simplestructurescanner.structure.pillar;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.util.ReflectionHelper;
import com.simplestructurescanner.util.ItemStackKey;


/**
 * Reads Pillar structure NBT files through the shared structure NBT parser.
 * <p>
 * These files are located in {@code pillar/structures}. This extension :
 * - Expands data-block metadata
 * - Reads entities named by metadata commands
 * - Extracts fixed container contents
 */
public class PillarNBTParser {

    // Matches chest metadata as "chest [<facing>] <loot table>"
    private static final Pattern CHEST_PATTERN = Pattern.compile("chest\\s+(?:(north|south|east|west)\\s+)?(.+)", Pattern.CASE_INSENSITIVE);

    // Matches spawner metadata as "spawner <entityID>"
    private static final Pattern SPAWNER_PATTERN = Pattern.compile("spawner\\s+(.+)", Pattern.CASE_INSENSITIVE);

    // Matches load_loot_table metadata as "load_loot_table <loot table>"
    private static final Pattern LOAD_LOOT_TABLE_PATTERN = Pattern.compile("load_loot_table\\s+(.+)", Pattern.CASE_INSENSITIVE);

    // Matches run summon metadata as "run [/]summon <entityID> [pos] [nbt]"
    private static final Pattern RUN_SUMMON_PATTERN = Pattern.compile("run\\s+/?summon\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    // Matches Pillar functions such as $rand_s(value;weight)$ and $rand_i(1;10)$
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("\\$(\\w+)\\(([^)]*)\\)\\$");

    private static File pillarStructureDir = null;

    private static final class PillarParseExtension implements StructureNBTParser.StructureParseExtension {
        private final List<ItemStack> directContainerItems = new ArrayList<>();

        @Override
        public boolean shouldCountBlock(@Nullable IBlockState state, @Nullable Block block) {
            return !StructureNBTParser.isInvisibleBlock(block)
                && !StructureNBTParser.isFlowingFluid(state, block)
                && block != Blocks.STRUCTURE_BLOCK;
        }

        @Nullable
        @Override
        public Object getBlockCountKey(@Nullable IBlockState state, @Nullable Block block,
            @Nullable NBTTagCompound blockEntityData) {
            return block;
        }

        @Override
        public boolean shouldStoreLayerBlock(@Nullable IBlockState state, @Nullable Block block) {
            // Keep flowing fluids in the preview and hide structure blocks
            return !StructureNBTParser.isInvisibleBlock(block) && block != Blocks.STRUCTURE_BLOCK;
        }

        @Override
        public void handleBlockEntity(StructureNBTParser.ParsedStructureBuilder builder, NBTTagCompound blockEntry,
                @Nullable IBlockState state, @Nullable Block block, NBTTagCompound nbtData) {
            StructureNBTParser.handleDefaultBlockEntity(builder, state, block, nbtData);

            if (nbtData.hasKey("metadata")) {
                parseDataBlockMetadata(nbtData.getString("metadata"), builder);
            }

            // Read fixed inventories separately so their contents are counted once
            if (!hasSerializedInventoryItems(nbtData)) extractContainerItems(state, nbtData, directContainerItems);
        }

        @Override
        public void finish(StructureNBTParser.ParsedStructureBuilder builder, NBTTagCompound structureNbt) {
            if (directContainerItems.isEmpty()) return;

            List<ItemStack> mergedItems = mergeItemStacks(directContainerItems);
            builder.addLootEntry(new LootEntry(
                null,
                mergedItems,
                LocalizedText.translatable("gui.structurescanner.loot.container")
            ));
        }
    }

    private PillarNBTParser() {
    }

    /**
     * Parses a Pillar structure NBT file.
     *
     * @param structureName The structure name (e.g., "dungeon/room1")
     * @return Parsed structure data, or null when the file is unavailable or invalid
     */
    @Nullable
    public static StructureNBTParser.ParsedStructure parseStructure(String structureName) {
        File pillarDir = getPillarStructureDir();
        if (pillarDir == null) {
            SimpleStructureScanner.LOGGER.debug("Could not locate the Pillar structure directory");
            return null;
        }

        // Structure names retain subdirectories below pillar/structures
        String filePath = structureName + ".nbt";
        File nbtFile = new File(pillarDir, filePath);

        if (!nbtFile.exists()) {
            SimpleStructureScanner.LOGGER.debug("Could not find Pillar structure file {}", nbtFile.getAbsolutePath());
            return null;
        }

        return StructureNBTParser.parseStructureFile(nbtFile, new PillarParseExtension());
    }

    /**
     * Reads Pillar's structure directory through reflection.
     */
    @Nullable
    private static File getPillarStructureDir() {
        if (pillarStructureDir != null) return pillarStructureDir;

        try {
            Class<?> pillarClass = ReflectionHelper.loadClassRequired("vazkii.pillar.Pillar");
            pillarStructureDir = (File) ReflectionHelper.getStaticField(pillarClass, "structureDir");

            return pillarStructureDir;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug("Could not read the Pillar structure directory", e);
            return null;
        }
    }


    /**
     * Parses data-block metadata for loot tables and entities.
     * <p>
     * Each {@code $rand_s(value;weight;...)$} branch is expanded before parsing
     * so every possible loot table and entity is included.
     *
     * @param metadata The data block metadata string
     */
    private static void parseDataBlockMetadata(String metadata, StructureNBTParser.ParsedStructureBuilder builder) {
        if (metadata == null || metadata.isEmpty()) return;

        // Expand every random string branch before matching the command
        List<String> variants = expandFunctions(metadata);

        for (String variant : variants) {
            // $run_if()$ can disable the remaining command with /**
            variant = variant.replaceAll("/\\*\\*.*", "").trim();
            if (variant.isEmpty()) continue;

            // Read chest metadata
            Matcher chestMatcher = CHEST_PATTERN.matcher(variant);
            if (chestMatcher.find()) {
                String lootTable = chestMatcher.group(2).trim();
                if (!lootTable.isEmpty()) builder.addLootTable(new ResourceLocation(lootTable));

                continue;
            }

            // Read spawner metadata
            Matcher spawnerMatcher = SPAWNER_PATTERN.matcher(variant);
            if (spawnerMatcher.find()) {
                String entityId = spawnerMatcher.group(1).trim();
                if (!entityId.isEmpty()) builder.addEntity(new ResourceLocation(entityId), true);

                continue;
            }

            // Read run summon metadata
            Matcher runSummonMatcher = RUN_SUMMON_PATTERN.matcher(variant);
            if (runSummonMatcher.find()) {
                String entityId = runSummonMatcher.group(1).trim();
                if (!entityId.isEmpty()) builder.addEntity(new ResourceLocation(entityId), false);

                continue;
            }

            // Read load_loot_table metadata
            Matcher loadLootMatcher = LOAD_LOOT_TABLE_PATTERN.matcher(variant);
            if (loadLootMatcher.find()) {
                String lootTable = loadLootMatcher.group(1).trim();
                if (!lootTable.isEmpty()) builder.addLootTable(new ResourceLocation("pillar", lootTable));
            }
        }
    }

    /**
     * Expands {@code $rand_s(...)$} calls into metadata variants.
     * {@code $rand_i(...)} and {@code $run_if(...)} are replaced with values
     * that leave the command parseable.
     * <p>
     * For example, {@code "chest north $rand_s(a;1;b;1)$"} expands to
     * {@code ["chest north a", "chest north b"]}.
     *
     * @return All expanded variants, or a list containing the input when it has no functions
     */
    private static List<String> expandFunctions(String metadata) {
        // Start with the metadata as the initial variant to be expanded
        List<String> current = new ArrayList<>();
        current.add(metadata);

        // Expand the functions in each variant until none remain
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

                // A backslash escapes semicolons inside a parameter
                String[] params = paramsStr.split("\\s*(?<!\\\\);\\s*");

                if ("rand_s".equals(funcName)) {
                    // rand_s arguments alternate between strings and weights
                    for (int i = 0; i < params.length; i += 2) next.add(prefix + params[i] + suffix);
                } else if ("rand_i".equals(funcName) && params.length == 2) {
                    // Use the midpoint as a the average value of the range
                    try {
                        int lower = Integer.parseInt(params[0].trim());
                        int upper = Integer.parseInt(params[1].trim());
                        next.add(prefix + ((lower + upper) / 2) + suffix);
                    } catch (NumberFormatException e) {
                        next.add(prefix + "0" + suffix);
                    }
                } else if ("run_if".equals(funcName)) {
                    // Drop run_if so the command remains visible to the parser
                    next.add(prefix + suffix);
                } else {
                    // Drop functions that cannot be expanded
                    next.add(prefix + suffix);
                }
            }

            current = next;
        }

        return current;
    }

    /**
     * Reads fixed items by loading the container tile entity from NBT,
     * so we can use the generic item extraction logic.
     * Supports {@link IItemHandler} and {@link IInventory}.
     */
    private static void extractContainerItems(@Nullable IBlockState state, NBTTagCompound nbtData, List<ItemStack> outItems) {
        if (state == null) return;

        Block block = state.getBlock();
        if (!block.hasTileEntity(state)) return;

        TileEntity tileEntity = createContainerTileEntity(state, nbtData);
        if (tileEntity == null) return;

        try {
            // Read Forge item-handler slots when the tile entity exposes them
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
            // Use the vanilla inventory when the capability needs a world instance
        }

        // Read vanilla inventory slots (IInventory)
        if (tileEntity instanceof IInventory) {
            IInventory inventory = (IInventory) tileEntity;

            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty()) outItems.add(stack.copy());
            }
        }
    }

    private static boolean hasSerializedInventoryItems(NBTTagCompound nbtData) {
        if (!nbtData.hasKey("Items", Constants.NBT.TAG_LIST)) return false;

        return nbtData.getTagList("Items", Constants.NBT.TAG_COMPOUND).tagCount() > 0;
    }

    @Nullable
    private static TileEntity createContainerTileEntity(IBlockState state, NBTTagCompound nbtData) {
        try {
            // Prefer a saved tile entity (NBT) before asking the block for an instance
            if (nbtData.hasKey("id", Constants.NBT.TAG_STRING)) {
                TileEntity tileEntity = TileEntity.create(null, nbtData);
                if (tileEntity != null) return tileEntity;
            }
        } catch (Exception e) {
            // Fall back to the block factory when the saved tile entity cannot load
        }

        try {
            TileEntity tileEntity = state.getBlock().createTileEntity(null, state);
            if (tileEntity == null) return null;

            tileEntity.readFromNBT(nbtData);
            return tileEntity;
        } catch (Exception e) {
            // Skip inventories that require a world while reading NBT
            return null;
        }
    }

    /**
     * Combines equivalent item stacks and sorts them by total count.
     */
    private static List<ItemStack> mergeItemStacks(List<ItemStack> items) {
        Map<ItemStackKey, ItemStack> merged = new HashMap<>();

        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;

            ItemStackKey key = ItemStackKey.of(stack);
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
}
