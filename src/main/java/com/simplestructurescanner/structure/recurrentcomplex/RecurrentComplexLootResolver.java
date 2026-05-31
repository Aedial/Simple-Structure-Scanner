package com.simplestructurescanner.structure.recurrentcomplex;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.LootTableResolver;
import com.simplestructurescanner.structure.LootTableResolver.LootItem;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntryKind;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.util.ReflectionHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper.ReflectionException;


/**
 * Resolves Recurrent Complex generator items embedded in inventories.
 * <p>
 * RC stores placeholder items such as inventory generation tags, artifact tags,
 * and book tags directly in container contents. At generation time those items are
 * recursively replaced with real loot, so the scanner has to emulate that logic
 * instead of listing the placeholder item itself.
 */
public final class RecurrentComplexLootResolver {

    private static final String WEIGHTED_ITEM_COLLECTION_REGISTRY_CLASS = "ivorius.reccomplex.world.storage.loot.WeightedItemCollectionRegistry";
    private static final String GENERIC_ITEM_COLLECTION_REGISTRY_CLASS = "ivorius.reccomplex.world.storage.loot.GenericItemCollectionRegistry";
    private static final String VANILLA_LOOT_TABLE_CLASS = "ivorius.reccomplex.world.storage.loot.VanillaLootTable";
    private static final String RC_CONFIG_CLASS = "ivorius.reccomplex.RCConfig";
    private static final String ARTIFACT_ITEM_CLASS = "ivorius.reccomplex.random.item.ArtifactItem";
    private static final String BOOK_ITEM_CLASS = "ivorius.reccomplex.random.item.Book";

    private static final ResourceLocation MULTI_TAG_ITEM_ID = new ResourceLocation("reccomplex", "inventory_generation_tag");
    private static final ResourceLocation SINGLE_TAG_ITEM_ID = new ResourceLocation("reccomplex", "inventory_generation_single_tag");
    private static final ResourceLocation COMPONENT_TAG_ITEM_ID = new ResourceLocation("reccomplex", "inventory_generation_component_tag");
    private static final ResourceLocation ARTIFACT_TAG_ITEM_ID = new ResourceLocation("reccomplex", "artifact_generation_tag");
    private static final ResourceLocation BOOK_TAG_ITEM_ID = new ResourceLocation("reccomplex", "book_generation_tag");

    private static final ResourceLocation LEGACY_MULTI_TAG_ITEM_ID = new ResourceLocation("reccomplex", "inventoryGenerationTag");
    private static final ResourceLocation LEGACY_SINGLE_TAG_ITEM_ID = new ResourceLocation("reccomplex", "inventoryGenerationSingleTag");
    private static final ResourceLocation LEGACY_COMPONENT_TAG_ITEM_ID = new ResourceLocation("reccomplex", "inventoryGenerationComponentTag");
    private static final ResourceLocation LEGACY_ARTIFACT_TAG_ITEM_ID = new ResourceLocation("reccomplex", "artifactGenerationTag");
    private static final ResourceLocation LEGACY_BOOK_TAG_ITEM_ID = new ResourceLocation("reccomplex", "bookGenerationTag");

    private static final Map<String, String> LEGACY_VANILLA_GENERATOR_KEYS = createLegacyVanillaGeneratorKeys();

    private static final String ITEM_COLLECTION_KEY_TAG = "itemCollectionKey";
    private static final String HIDDEN_ITEM_TAG = "RC_HIDDEN_ITEM";
    private static final String DISPLAY_TAG = "display";
    private static final String DISPLAY_NAME_TAG = "Name";
    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("(?<=[A-Za-z])(?=[A-Z][a-z])|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Za-z])(?=[0-9])");
    private static final int MAX_RECURSION_DEPTH = 16;

    private RecurrentComplexLootResolver() {
    }

    public static boolean isGeneratingItem(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        ResourceLocation itemId = getContainedItemId(stack);
        if (itemId == null) return false;

        return MULTI_TAG_ITEM_ID.equals(itemId)
            || SINGLE_TAG_ITEM_ID.equals(itemId)
            || COMPONENT_TAG_ITEM_ID.equals(itemId)
            || ARTIFACT_TAG_ITEM_ID.equals(itemId)
            || BOOK_TAG_ITEM_ID.equals(itemId);
    }

    @Nullable
    public static LootEntry createLootEntry(@Nullable IBlockState state, ItemStack generatorStack) {
        if (!isGeneratingItem(generatorStack)) return null;
        if (!hasResolvableSource(generatorStack)) return null;

        return new LootEntry(
            null,
            Collections.emptyList(),
            createContainerType(state),
            LootEntryKind.GENERATED_ITEMS,
            createSourceName(generatorStack),
            generatorStack
        );
    }

    public static List<LootItem> resolveGeneratedLootWithSimulation(@Nullable World world, ItemStack generatorStack) {
        if (generatorStack.isEmpty()) return Collections.emptyList();

        Map<String, LootItem> itemMap = new LinkedHashMap<>();
        long seed = 0x5EEDC0DEL ^ generatorStack.copy().writeToNBT(new NBTTagCompound()).toString().hashCode();
        Random random = new Random(seed);

        for (int i = 0; i < LootTableResolver.getSimulationCount(); i++) {
            simulateGenerator(world, generatorStack, random, itemMap, 0);
        }

        List<LootItem> resolvedLoot = new ArrayList<>(itemMap.values());
        resolvedLoot.sort((first, second) -> Integer.compare(second.dropCount, first.dropCount));
        return resolvedLoot;
    }

    private static void simulateGenerator(@Nullable World world, ItemStack stack, Random random,
            Map<String, LootItem> itemMap, int depth) {
        if (stack.isEmpty() || depth > MAX_RECURSION_DEPTH) return;

        ResourceLocation itemId = getContainedItemId(stack);
        if (MULTI_TAG_ITEM_ID.equals(itemId)) {
            simulateMultiTag(world, stack, random, itemMap, depth + 1);
            return;
        }

        if (SINGLE_TAG_ITEM_ID.equals(itemId)) {
            simulateSingleTag(world, stack, random, itemMap, depth + 1);
            return;
        }

        if (COMPONENT_TAG_ITEM_ID.equals(itemId)) {
            simulateComponentTag(stack, random, itemMap, depth + 1);
            return;
        }

        if (ARTIFACT_TAG_ITEM_ID.equals(itemId)) {
            simulateGeneratedSpecialItem(world, random, itemMap, depth + 1, true);
            return;
        }

        if (BOOK_TAG_ITEM_ID.equals(itemId)) {
            simulateGeneratedSpecialItem(world, random, itemMap, depth + 1, false);
            return;
        }

        addResolvedItem(itemMap, stack);
    }

    private static void simulateMultiTag(@Nullable World world, ItemStack stack, Random random,
            Map<String, LootItem> itemMap, int depth) {
        Object lootTable = getWeightedLootTable(getGeneratorKey(stack));
        if (lootTable == null) return;

        int itemCountMin = getIntegerTag(stack, "itemCountMin", 4);
        int itemCountMax = getIntegerTag(stack, "itemCountMax", 8);
        int amount = itemCountMin < itemCountMax
            ? random.nextInt(itemCountMax - itemCountMin + 1) + itemCountMin
            : 0;

        for (int i = 0; i < amount; i++) {
            simulateLootTableResult(world, lootTable, random, itemMap, depth);
        }
    }

    private static void simulateSingleTag(@Nullable World world, ItemStack stack, Random random,
            Map<String, LootItem> itemMap, int depth) {
        Object lootTable = getWeightedLootTable(getGeneratorKey(stack));
        if (lootTable == null) return;
        if (random.nextFloat() >= getFloatTag(stack, "itemChance", 1.0f)) return;

        simulateLootTableResult(world, lootTable, random, itemMap, depth);
    }

    private static void simulateComponentTag(ItemStack stack, Random random,
            Map<String, LootItem> itemMap, int depth) {
        Object component = getGenericComponent(getGeneratorKey(stack));
        if (component == null) return;

        try {
            ItemStack generated = (ItemStack) invokeRequired(component, "getRandomItemStack",
                new Class<?>[]{Random.class}, random);
            if (generated != null) simulateGenerator(null, generated, random, itemMap, depth);
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.debug("Failed to resolve Recurrent Complex loot component", e);
        }
    }

    private static void simulateGeneratedSpecialItem(@Nullable World world, Random random,
            Map<String, LootItem> itemMap, int depth, boolean artifact) {
        CustomOverride customOverride = getCustomOverride(artifact ? "customArtifactTag" : "customBookTag");
        if (customOverride.chance > 0.0f && random.nextFloat() < customOverride.chance) {
            Object overrideLootTable = getWeightedLootTable(customOverride.key);
            if (overrideLootTable != null) simulateLootTableResult(world, overrideLootTable, random, itemMap, depth);

            return;
        }

        try {
            Class<?> sourceClass = ReflectionHelper.loadClassRequired(artifact ? ARTIFACT_ITEM_CLASS : BOOK_ITEM_CLASS);
            ItemStack generated = (ItemStack) invokeStaticRequired(sourceClass, "any", new Class<?>[]{Random.class}, random);
            if (generated != null) simulateGenerator(world, generated, random, itemMap, depth);
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.debug("Failed to resolve Recurrent Complex {} generator",
                artifact ? "artifact" : "book", e);
        }
    }

    private static void simulateLootTableResult(@Nullable World world, Object lootTable, Random random,
            Map<String, LootItem> itemMap, int depth) {
        ItemStack generated = generateLootTableItem(world, lootTable, random);
        if (generated == null || generated.isEmpty()) return;

        simulateGenerator(world, generated, random, itemMap, depth);
    }

    @Nullable
    private static ItemStack generateLootTableItem(@Nullable World world, Object lootTable, Random random) {
        try {
            if (isVanillaLootTable(lootTable) && !(world instanceof WorldServer)) {
                return pickApproximateVanillaLoot(world, lootTable, random);
            }

            return (ItemStack) invokeRequired(lootTable, "getRandomItemStack",
                new Class<?>[]{WorldServer.class, Random.class}, world instanceof WorldServer ? (WorldServer) world : null, random);
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.debug("Failed to generate Recurrent Complex loot result", e);
            return ItemStack.EMPTY;
        }
    }

    private static void addResolvedItem(Map<String, LootItem> itemMap, ItemStack stack) {
        if (stack.isEmpty()) return;

        String itemKey = LootTableResolver.createAggregationKey(stack);
        LootItem existingItem = itemMap.get(itemKey);
        if (existingItem != null) {
            existingItem.dropCount += stack.getCount();
            return;
        }

        itemMap.put(itemKey, new LootItem(stack.copy(), stack.getCount()));
    }

    @Nullable
    private static ItemStack pickApproximateVanillaLoot(@Nullable World world, Object lootTable, Random random) {
        if (world == null) return ItemStack.EMPTY;

        try {
            ResourceLocation vanillaKey = (ResourceLocation) ReflectionHelper.getField(lootTable, lootTable.getClass(), "vanillaKey");
            if (vanillaKey == null) return ItemStack.EMPTY;

            List<LootItem> approximatedLoot = LootTableResolver.resolveLootTableWithSimulation(world, vanillaKey, null);
            if (approximatedLoot.isEmpty()) return ItemStack.EMPTY;

            int totalWeight = 0;
            for (LootItem item : approximatedLoot) {
                totalWeight += Math.max(1, item.dropCount);
            }

            int selectedWeight = random.nextInt(Math.max(1, totalWeight));
            int runningWeight = 0;
            for (LootItem item : approximatedLoot) {
                runningWeight += Math.max(1, item.dropCount);
                if (selectedWeight >= runningWeight) continue;

                return item.stack.copy();
            }
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.debug("Failed to approximate Recurrent Complex vanilla loot", e);
        }

        return ItemStack.EMPTY;
    }

    private static boolean isVanillaLootTable(Object lootTable) {
        return lootTable != null && VANILLA_LOOT_TABLE_CLASS.equals(lootTable.getClass().getName());
    }

    @Nullable
    private static Object getWeightedLootTable(@Nullable String key) {
        key = normalizeGeneratorKey(key);
        if (key == null || key.isEmpty()) return null;

        try {
            Class<?> registryClass = ReflectionHelper.loadClassRequired(WEIGHTED_ITEM_COLLECTION_REGISTRY_CLASS);
            Object registry = ReflectionHelper.getStaticField(registryClass, "INSTANCE");
            return invokeRequired(registry, "get", new Class<?>[]{String.class}, key);
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.debug("Failed to access Recurrent Complex weighted loot registry", e);
            return null;
        }
    }

    @Nullable
    private static Object getGenericComponent(@Nullable String key) {
        key = normalizeGeneratorKey(key);
        if (key == null || key.isEmpty()) return null;

        try {
            Class<?> registryClass = ReflectionHelper.loadClassRequired(GENERIC_ITEM_COLLECTION_REGISTRY_CLASS);
            Object registry = ReflectionHelper.getStaticField(registryClass, "INSTANCE");
            return invokeRequired(registry, "get", new Class<?>[]{String.class}, key);
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.debug("Failed to access Recurrent Complex generic loot registry", e);
            return null;
        }
    }

    private static LocalizedText createContainerType(@Nullable IBlockState state) {
        ItemStack displayStack = state != null ? StructureNBTParser.createDisplayStack(state) : ItemStack.EMPTY;
        if (!displayStack.isEmpty()) {
            String translationKey = displayStack.getTranslationKey() + ".name";
            return LocalizedText.translatableWithFallback(translationKey,
                LocalizedText.literal(displayStack.getDisplayName()));
        }

        Block block = state != null ? state.getBlock() : null;
        if (block != null) {
            String translationKey = block.getTranslationKey() + ".name";
            return LocalizedText.translatableWithFallback(translationKey,
                LocalizedText.literal(block.getLocalizedName()));
        }

        return LocalizedText.translatable("gui.structurescanner.loot.container");
    }

    private static LocalizedText createSourceName(ItemStack generatorStack) {
        ResourceLocation itemId = getContainedItemId(generatorStack);
        String generatorKey = getGeneratorKey(generatorStack);

        if (MULTI_TAG_ITEM_ID.equals(itemId) || SINGLE_TAG_ITEM_ID.equals(itemId) || COMPONENT_TAG_ITEM_ID.equals(itemId)) {
            if (generatorKey == null || generatorKey.isEmpty()) {
                return LocalizedText.translatable("gui.structurescanner.structures.reccomplex.generator");
            }

            return LocalizedText.literal(formatGeneratorKey(generatorKey));
        }

        if (ARTIFACT_TAG_ITEM_ID.equals(itemId)) {
            return LocalizedText.translatable("gui.structurescanner.structures.reccomplex.artifact");
        }

        if (BOOK_TAG_ITEM_ID.equals(itemId)) {
            return LocalizedText.translatable("gui.structurescanner.structures.reccomplex.book");
        }

        return LocalizedText.literal(generatorStack.getDisplayName());
    }

    @Nullable
    private static String getGeneratorKey(ItemStack generatorStack) {
        NBTTagCompound tagCompound = generatorStack.getTagCompound();
        if (tagCompound == null) return null;

        if (tagCompound.hasKey(ITEM_COLLECTION_KEY_TAG, Constants.NBT.TAG_STRING)) {
            String key = tagCompound.getString(ITEM_COLLECTION_KEY_TAG);
            return key.isEmpty() ? null : key;
        }

        if (tagCompound.hasKey(DISPLAY_TAG, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound displayTag = tagCompound.getCompoundTag(DISPLAY_TAG);
            if (displayTag.hasKey(DISPLAY_NAME_TAG, Constants.NBT.TAG_STRING)) {
                String key = displayTag.getString(DISPLAY_NAME_TAG);
                return key.isEmpty() ? null : key;
            }
        }

        return null;
    }

    private static boolean hasResolvableSource(ItemStack generatorStack) {
        ResourceLocation itemId = getContainedItemId(generatorStack);
        if (itemId == null) return false;
        if (ARTIFACT_TAG_ITEM_ID.equals(itemId) || BOOK_TAG_ITEM_ID.equals(itemId)) return true;
        if (Arrays.asList(MULTI_TAG_ITEM_ID, SINGLE_TAG_ITEM_ID, COMPONENT_TAG_ITEM_ID).contains(itemId)) {
            return getGeneratorKey(generatorStack) != null;
        }

        return true;
    }

    @Nullable
    private static String normalizeGeneratorKey(@Nullable String key) {
        if (key == null || key.isEmpty()) return null;

        String normalizedKey = LEGACY_VANILLA_GENERATOR_KEYS.get(key);
        return normalizedKey != null ? normalizedKey : key;
    }

    @Nullable
    private static ResourceLocation getContainedItemId(ItemStack stack) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound != null && tagCompound.hasKey(HIDDEN_ITEM_TAG, Constants.NBT.TAG_STRING)) {
            try {
                return normalizeContainedItemId(new ResourceLocation(tagCompound.getString(HIDDEN_ITEM_TAG)));
            } catch (IllegalArgumentException e) {
                SimpleStructureScanner.LOGGER.debug("Invalid hidden Recurrent Complex item id", e);
            }
        }

        return normalizeContainedItemId(stack.getItem().getRegistryName());
    }

    @Nullable
    private static ResourceLocation normalizeContainedItemId(@Nullable ResourceLocation itemId) {
        if (itemId == null) return null;
        if (!"reccomplex".equals(itemId.getNamespace())) return itemId;
        if (LEGACY_MULTI_TAG_ITEM_ID.equals(itemId)) return MULTI_TAG_ITEM_ID;
        if (LEGACY_SINGLE_TAG_ITEM_ID.equals(itemId)) return SINGLE_TAG_ITEM_ID;
        if (LEGACY_COMPONENT_TAG_ITEM_ID.equals(itemId)) return COMPONENT_TAG_ITEM_ID;
        if (LEGACY_ARTIFACT_TAG_ITEM_ID.equals(itemId)) return ARTIFACT_TAG_ITEM_ID;
        if (LEGACY_BOOK_TAG_ITEM_ID.equals(itemId)) return BOOK_TAG_ITEM_ID;

        return itemId;
    }

    private static int getIntegerTag(ItemStack stack, String key, int defaultValue) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null || !tagCompound.hasKey(key, Constants.NBT.TAG_INT)) return defaultValue;

        return tagCompound.getInteger(key);
    }

    private static float getFloatTag(ItemStack stack, String key, float defaultValue) {
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null || !tagCompound.hasKey(key, Constants.NBT.TAG_FLOAT)) return defaultValue;

        return tagCompound.getFloat(key);
    }

    private static String formatGeneratorKey(String rawKey) {
        String normalized = rawKey.replace(':', ' ').replace('/', ' ').replace('_', ' ').replace('-', ' ');
        normalized = CAMEL_CASE_BOUNDARY.matcher(normalized).replaceAll(" ");
        normalized = normalized.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) return rawKey;

        return normalized;
    }

    private static Map<String, String> createLegacyVanillaGeneratorKeys() {
        Map<String, String> keys = new LinkedHashMap<>();

        // Old RC structures can still store pre-loot-table ChestGenHooks aliases in display.Name.
        keys.put("bonusChest", "minecraft:chests/spawn_bonus_chest");
        keys.put("dungeonChest", "minecraft:chests/simple_dungeon");
        keys.put("mineshaftCorridor", "minecraft:chests/abandoned_mineshaft");
        keys.put("pyramidDesertyChest", "minecraft:chests/desert_pyramid");
        keys.put("pyramidJungleChest", "minecraft:chests/jungle_temple");
        keys.put("pyramidJungleDispenser", "minecraft:chests/jungle_temple_dispenser");
        keys.put("strongholdCorridor", "minecraft:chests/stronghold_corridor");
        keys.put("strongholdCrossing", "minecraft:chests/stronghold_crossing");
        keys.put("strongholdLibrary", "minecraft:chests/stronghold_library");
        keys.put("villageBlacksmith", "minecraft:chests/village_blacksmith");

        return keys;
    }

    private static CustomOverride getCustomOverride(String fieldName) {
        try {
            Class<?> configClass = ReflectionHelper.loadClassRequired(RC_CONFIG_CLASS);
            Object value = ReflectionHelper.getStaticField(configClass, fieldName);
            if (!(value instanceof Pair)) return CustomOverride.EMPTY;

            Pair<?, ?> pair = (Pair<?, ?>) value;
            Object key = pair.getLeft();
            Object chance = pair.getRight();
            if (!(chance instanceof Number)) return CustomOverride.EMPTY;

            return new CustomOverride(key instanceof String ? (String) key : "", ((Number) chance).floatValue());
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.debug("Failed to read Recurrent Complex custom loot override {}", fieldName, e);
            return CustomOverride.EMPTY;
        }
    }

    private static Object invokeRequired(Object target, String methodName, Class<?>[] parameterTypes,
            Object... args) throws ReflectionException {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Exception e) {
            throw new ReflectionException("Failed to invoke method '" + methodName + "' on " + target.getClass().getName(), e);
        }
    }

    private static Object invokeStaticRequired(Class<?> ownerClass, String methodName, Class<?>[] parameterTypes,
            Object... args) throws ReflectionException {
        try {
            Method method = ownerClass.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Exception e) {
            throw new ReflectionException("Failed to invoke static method '" + methodName + "' on " + ownerClass.getName(), e);
        }
    }

    private static final class CustomOverride {
        private static final CustomOverride EMPTY = new CustomOverride("", 0.0f);

        private final String key;
        private final float chance;

        private CustomOverride(String key, float chance) {
            this.key = key;
            this.chance = chance;
        }
    }
}