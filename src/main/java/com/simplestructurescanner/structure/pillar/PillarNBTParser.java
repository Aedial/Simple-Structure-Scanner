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


/**
 * Parses Pillar structure NBT files by extending the shared structure NBT parser.
 * <p>
 * Pillar structures are stored as external NBT files in the pillar/structures directory.
 * The shared parser handles the base structure walk, while this class adds:
 * - Pillar data block metadata expansion
 * - Summoned entities declared by metadata commands
 * - Direct container item extraction
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
        public Object getBlockCountKey(@Nullable IBlockState state, @Nullable Block block) {
            return block;
        }

        @Override
        public boolean shouldStoreLayerBlock(@Nullable IBlockState state, @Nullable Block block) {
            // Keep Pillar's flowing fluid states in the preview while still hiding structure markers.
            return !StructureNBTParser.isInvisibleBlock(block) && block != Blocks.STRUCTURE_BLOCK;
        }

        @Override
        public void handleBlockEntity(StructureNBTParser.ParsedStructureBuilder builder, NBTTagCompound blockEntry,
                @Nullable IBlockState state, @Nullable Block block, NBTTagCompound nbtData) {
            StructureNBTParser.handleDefaultBlockEntity(builder, state, block, nbtData);

            if (nbtData.hasKey("metadata")) {
                parseDataBlockMetadata(nbtData.getString("metadata"), builder);
            }

            extractContainerItems(state, nbtData, directContainerItems);
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
     * Parse a Pillar structure NBT file.
     *
     * @param structureName The structure name (e.g., "dungeon/room1")
     * @return Parsed structure data or null if parsing fails
     */
    @Nullable
    public static StructureNBTParser.ParsedStructure parseStructure(String structureName) {
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

        return StructureNBTParser.parseStructureFile(nbtFile, new PillarParseExtension());
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
     * Parse data block metadata to extract loot tables and spawner entities.
     * <p>
     * Pillar data block metadata strings can contain function calls like
     * {@code $rand_s(value1;weight1;value2;weight2)$} that randomly select
     * a value at generation time. We extract <em>all</em> possible values
     * from these functions so that every potential loot table or entity is
     * discovered.
     *
     * @param metadata       The data block metadata string
     */
    private static void parseDataBlockMetadata(String metadata, StructureNBTParser.ParsedStructureBuilder builder) {
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
                if (!lootTable.isEmpty()) builder.addLootTable(new ResourceLocation(lootTable));

                continue;
            }

            // Check for spawner command
            Matcher spawnerMatcher = SPAWNER_PATTERN.matcher(variant);
            if (spawnerMatcher.find()) {
                String entityId = spawnerMatcher.group(1).trim();
                if (!entityId.isEmpty()) builder.addEntity(new ResourceLocation(entityId), true);

                continue;
            }

            // Check for "run summon" or "run /summon" command (used to spawn entities at structure generation)
            Matcher runSummonMatcher = RUN_SUMMON_PATTERN.matcher(variant);
            if (runSummonMatcher.find()) {
                String entityId = runSummonMatcher.group(1).trim();
                if (!entityId.isEmpty()) builder.addEntity(new ResourceLocation(entityId), false);

                continue;
            }

            // Check for load_loot_table command
            Matcher loadLootMatcher = LOAD_LOOT_TABLE_PATTERN.matcher(variant);
            if (loadLootMatcher.find()) {
                String lootTable = loadLootMatcher.group(1).trim();
                if (!lootTable.isEmpty()) builder.addLootTable(new ResourceLocation("pillar", lootTable));
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
     * Extract items from a container by instantiating its tile entity and reading via IInventory/IItemHandler.
     * This allows any mod's container to work regardless of its internal NBT format.
     */
    private static void extractContainerItems(@Nullable IBlockState state, NBTTagCompound nbtData, List<ItemStack> outItems) {
        if (state == null) return;

        Block block = state.getBlock();
        if (!block.hasTileEntity(state)) return;

        TileEntity tileEntity = createContainerTileEntity(state, nbtData);
        if (tileEntity == null) return;

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

    @Nullable
    private static TileEntity createContainerTileEntity(IBlockState state, NBTTagCompound nbtData) {
        try {
            // Prefer the registry-backed factory so dedicated servers never need a client world.
            if (nbtData.hasKey("id", Constants.NBT.TAG_STRING)) {
                TileEntity tileEntity = TileEntity.create(null, nbtData);
                if (tileEntity != null) return tileEntity;
            }
        } catch (Exception e) {
            // Some tile entities may fail to load directly from NBT; fall back to the block factory.
        }

        try {
            TileEntity tileEntity = state.getBlock().createTileEntity(null, state);
            if (tileEntity == null) return null;

            tileEntity.readFromNBT(nbtData);
            return tileEntity;
        } catch (Exception e) {
            // Some tile entities still require a real world; skip direct item extraction in that case.
            return null;
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
}
