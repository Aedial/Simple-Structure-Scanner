package com.simplestructurescanner.integration.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidStack;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.config.ModConfig;
import com.simplestructurescanner.integration.JEIHelper;
import com.simplestructurescanner.structure.LootTableResolver;
import com.simplestructurescanner.structure.LootTableResolver.LootItem;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureProviderRegistry;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntryKind;
import com.simplestructurescanner.structure.recurrentcomplex.RecurrentComplexLootResolver;


/**
 * Lazy structure recipe cache for JEI lookups.
 * <p>
 * Warm-up runs whenever JEI sees no cached visibility snapshot or the current visibility signature changes
 * (provider revision, captured GameStages snapshot, or client blacklist fingerprint).
 * <p>
 * Phase 1 runs on the client thread: {@code createBuildRequest()} filters visible structures, sorts the
 * per-tab wrappers, and publishes a temporary visible-only state. Direct category opens through
 * {@link #getAllVisible(StructureJeiView)} are exact as soon as this swap happens, but reverse match lookups
 * through {@link #getMatchingBlockItems(ItemStack)}, {@link #getMatchingBlockFluids(FluidStack)}, and
 * {@link #getMatchingLootItems(ItemStack)} still return empty lists because no lookup buckets have been attached yet.
 * <p>
 * Phase 2 runs as one background burst on the {@code SSS-JeiWarmup} thread. CPU time there is spent walking stable
 * {@link StructureInfo} snapshots, accumulating block and fluid output keys, collecting static loot keys, and
 * deduplicating dynamic loot sources (loot tables and Recurrent Complex generator stacks) into a pending queue.
 * When that thread swaps its result into {@code visibleState}, block and fluid matching become exact immediately,
 * static loot matches become available, and only unresolved dynamic loot sources can still miss.
 * <p>
 * Phase 3 is throttled on the client tick thread. {@link #processLootWarmup()} resolves pending dynamic loot sources
 * for roughly {@value #LOOT_WARMUP_BUDGET_NS} ns per tick, which is currently 2 ms/tick. CPU time in that phase is
 * spent simulating loot tables or Recurrent Complex generator items against the current world, caching each unique
 * resolved source in {@code RESOLVED_LOOT_SOURCE_CACHE}, and merging the resulting item keys into the visible loot
 * index. During this phase, loot reverse matching can temporarily miss structures whose dynamic source has not been
 * simulated yet.
 * <p>
 * Once the pending queue is empty, direct category opens and all reverse match lookups are exact for the current
 * visibility signature until another warm-up is scheduled.
 */
final class StructureJeiRecipes {
    private static final long LOOT_WARMUP_BUDGET_NS = 2_000_000L;

    /** Stable wrappers for every known structure and logical tab. */
    private static final Map<StructureJeiView, Map<ResourceLocation, StructureJeiRecipe>> RECIPES =
        new EnumMap<>(StructureJeiView.class);

    /** Last observed StructureInfo reference for each structure id. */
    private static final Map<ResourceLocation, StructureInfo> INFO_REFERENCES = new LinkedHashMap<>();

    /** Per-session cache of resolved dynamic loot outputs, deduplicated by logical loot source. */
    private static final Map<LootSourceKey, List<ItemStack>> RESOLVED_LOOT_SOURCE_CACHE = new ConcurrentHashMap<>();

    @Nullable
    private static volatile VisibleState visibleState = null;

    @Nullable
    private static volatile VisibilitySignature requestedSignature = null;

    @Nullable
    private static volatile Thread buildThread = null;

    @Nullable
    private static volatile WarmupProgress activeWarmup = null;

    private static volatile boolean worldSessionActive = false;

    private static volatile long requestedBuildId = 0L;

    static {
        for (StructureJeiView view : StructureJeiView.values()) {
            RECIPES.put(view, new LinkedHashMap<>());
        }
    }

    private StructureJeiRecipes() {
    }

    /**
     * Returns the stable wrapper for one structure and tab.
     */
    public static StructureJeiRecipe get(ResourceLocation structureId, StructureJeiView view) {
        syncBaseRecipes();
        return RECIPES.get(view).get(structureId);
    }

    /**
     * Returns the current visible recipes for one category and schedules a refresh when the snapshot is stale.
     */
    public static List<StructureJeiRecipe> getAllVisible(StructureJeiView view) {
        ensureVisibleStateScheduled();

        VisibleState state = visibleState;
        if (state == null) return Collections.emptyList();

        return state.getSortedRecipes(view);
    }

    public static List<StructureJeiRecipe> getMatchingBlockItems(ItemStack stack) {
        ensureVisibleStateScheduled();

        VisibleState state = visibleState;
        if (state == null) return Collections.emptyList();

        return state.getBlockItemMatches(StructureJeiIngredientKeys.itemLookupKey(stack));
    }

    public static List<StructureJeiRecipe> getMatchingBlockFluids(FluidStack stack) {
        ensureVisibleStateScheduled();

        VisibleState state = visibleState;
        if (state == null) return Collections.emptyList();

        return state.getBlockFluidMatches(StructureJeiIngredientKeys.fluidLookupKey(stack));
    }

    public static List<StructureJeiRecipe> getMatchingLootItems(ItemStack stack) {
        ensureVisibleStateScheduled();

        VisibleState state = visibleState;
        if (state == null) return Collections.emptyList();

        return state.getLootItemMatches(StructureJeiIngredientKeys.itemLookupKey(stack));
    }

    public static void onWorldLoad() {
        // Client world loads also fire during dimension travel. Keep the current JEI snapshot alive
        // until the connection actually ends so portal hops do not restart the full warm-up.
        if (worldSessionActive) return;

        reset();
        worldSessionActive = true;

        if (!JEIHelper.isJEIAvailable()) return;
        ensureVisibleStateScheduled();
    }

    public static void onClientTick() {
        if (!JEIHelper.isJEIAvailable()) return;

        ensureVisibleStateScheduled();
        processLootWarmup();
    }

    public static WarmupProgress getActiveWarmup() {
        return activeWarmup;
    }

    public static void reset() {
        Thread thread = buildThread;
        buildThread = null;
        activeWarmup = null;
        requestedSignature = null;
        visibleState = null;
        worldSessionActive = false;
        requestedBuildId++;
        RESOLVED_LOOT_SOURCE_CACHE.clear();

        if (thread != null) thread.interrupt();
    }

    /**
     * Synchronizes the stable wrapper layer against the provider registry.
     */
    private static void syncBaseRecipes() {
        Set<ResourceLocation> liveStructureIds = new LinkedHashSet<>(StructureProviderRegistry.getAllStructureIds());

        for (ResourceLocation knownId : new ArrayList<>(INFO_REFERENCES.keySet())) {
            if (liveStructureIds.contains(knownId)) continue;

            INFO_REFERENCES.remove(knownId);
            for (StructureJeiView view : StructureJeiView.values()) {
                RECIPES.get(view).remove(knownId);
            }
        }

        for (ResourceLocation structureId : liveStructureIds) {
            StructureInfo structureInfo = StructureProviderRegistry.getStructureInfo(structureId);
            if (structureInfo == null) {
                INFO_REFERENCES.remove(structureId);
                for (StructureJeiView view : StructureJeiView.values()) {
                    RECIPES.get(view).remove(structureId);
                }
                continue;
            }

            INFO_REFERENCES.put(structureId, structureInfo);

            for (StructureJeiView view : StructureJeiView.values()) {
                if (RECIPES.get(view).containsKey(structureId)) continue;

                RECIPES.get(view).put(structureId, new StructureJeiRecipe(structureId, view));
            }
        }
    }

    private static void ensureVisibleStateScheduled() {
        if (!StructureJeiVisibility.isAnyCategoryEnabled()) return;
        if (!worldSessionActive) return;

        syncBaseRecipes();
        if (INFO_REFERENCES.isEmpty()) return;
        if (!isWarmupContextReady()) return;

        VisibilitySignature signature = captureVisibilitySignature();
        VisibleState state = visibleState;
        if (state != null && state.matches(signature)) return;

        VisibilitySignature pendingSignature = requestedSignature;
        if (pendingSignature != null && pendingSignature.equals(signature)) return;

        scheduleVisibleStateRefresh(signature, describeWarmupReason(signature, state));
    }

    private static boolean isWarmupContextReady() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) return false;

        return StructureJeiVisibility.isStageSnapshotReady();
    }

    /**
     * Capture the current visibility inputs on the main thread before the background warmup starts,
     * before starting a new thread to build the visible indexes.
     */
    private static void scheduleVisibleStateRefresh(VisibilitySignature signature, String warmupReason) {
        BuildRequest request = createBuildRequest(signature);
        long buildId = ++requestedBuildId;
        requestedSignature = signature;
        activeWarmup = new WarmupProgress(buildId, warmupReason, request.seeds.size(), System.nanoTime());
        visibleState = VisibleState.visibleOnly(buildId, signature, request.sortedRecipes);

        SimpleStructureScanner.LOGGER.info(
            "Starting JEI structure warmup: {} ({} visible structures, {} ms/tick main-thread loot budget)",
            warmupReason,
            Integer.valueOf(request.seeds.size()),
            Double.valueOf(roundMillis(LOOT_WARMUP_BUDGET_NS))
        );

        Thread previousThread = buildThread;
        if (previousThread != null) previousThread.interrupt();

        Thread thread = new Thread(() -> buildVisibleIndexes(buildId, request), "SSS-JeiWarmup");
        thread.setDaemon(true);
        buildThread = thread;
        thread.start();
    }

    private static VisibilitySignature captureVisibilitySignature() {
        return new VisibilitySignature(
            StructureProviderRegistry.getRevision(),
            StructureJeiVisibility.captureStageSnapshot(),
            captureBlacklistFingerprint()
        );
    }

    private static String captureBlacklistFingerprint() {
        List<String> blacklistEntries = ModConfig.clientStructureBlacklist;
        if (blacklistEntries == null || blacklistEntries.isEmpty()) return "";

        StringBuilder fingerprint = new StringBuilder();
        for (String entry : blacklistEntries) {
            if (entry == null) continue;

            fingerprint.append(entry.trim()).append('\n');
        }

        return fingerprint.toString();
    }

    private static BuildRequest createBuildRequest(VisibilitySignature signature) {
        List<BuildSeed> seeds = new ArrayList<>();

        for (ResourceLocation structureId : new ArrayList<>(INFO_REFERENCES.keySet())) {
            StructureInfo structureInfo = INFO_REFERENCES.get(structureId);
            if (structureInfo == null) continue;
            if (!StructureJeiVisibility.isStructureVisible(structureId, signature.stageSnapshot)) continue;

            StructureJeiRecipe previewRecipe = RECIPES.get(StructureJeiView.PREVIEW).get(structureId);
            StructureJeiRecipe blockRecipe = RECIPES.get(StructureJeiView.BLOCKS).get(structureId);
            StructureJeiRecipe lootRecipe = RECIPES.get(StructureJeiView.LOOT).get(structureId);
            if (previewRecipe == null || blockRecipe == null || lootRecipe == null) continue;

            seeds.add(new BuildSeed(structureInfo, previewRecipe.getSortKey(), previewRecipe, blockRecipe,
                lootRecipe));
        }

        seeds.sort((first, second) -> first.sortKey.compareToIgnoreCase(second.sortKey));

        Map<StructureJeiView, List<StructureJeiRecipe>> sortedRecipes = new EnumMap<>(StructureJeiView.class);
        List<StructureJeiRecipe> previewRecipes = new ArrayList<>(seeds.size());
        List<StructureJeiRecipe> blockRecipes = new ArrayList<>(seeds.size());
        List<StructureJeiRecipe> lootRecipes = new ArrayList<>(seeds.size());

        for (BuildSeed seed : seeds) {
            previewRecipes.add(seed.previewRecipe);
            blockRecipes.add(seed.blockRecipe);
            lootRecipes.add(seed.lootRecipe);
        }

        sortedRecipes.put(StructureJeiView.PREVIEW, Collections.unmodifiableList(previewRecipes));
        sortedRecipes.put(StructureJeiView.BLOCKS, Collections.unmodifiableList(blockRecipes));
        sortedRecipes.put(StructureJeiView.LOOT, Collections.unmodifiableList(lootRecipes));

        return new BuildRequest(signature, seeds, Collections.unmodifiableMap(sortedRecipes));
    }

    private static void buildVisibleIndexes(long buildId, BuildRequest request) {
        long backgroundStartTime = System.nanoTime();

        try {
            PreparedVisibleState preparedState = prepareVisibleState(buildId, request);
            if (preparedState == null || shouldAbortBuild(buildId, request.signature)) return;

            recordBackgroundPhase(preparedState, System.nanoTime() - backgroundStartTime);
            visibleState = preparedState.toVisibleState();
            if (!preparedState.hasPendingLootTasks()) finishWarmup(visibleState);
        } catch (Throwable throwable) {
            SimpleStructureScanner.LOGGER.error("Failed to warm JEI structure recipe indexes", throwable);
        } finally {
            if (buildThread == Thread.currentThread()) buildThread = null;
        }
    }

    @Nullable
    private static PreparedVisibleState prepareVisibleState(long buildId, BuildRequest request) {
        Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> blockItemOutputs = new HashMap<>();
        Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> blockFluidOutputs = new HashMap<>();
        Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> staticLootOutputs = new HashMap<>();
        Map<LootSourceKey, List<StructureJeiRecipe>> pendingLootSources = new LinkedHashMap<>();

        for (BuildSeed seed : request.seeds) {
            if (shouldAbortBuild(buildId, request.signature)) return null;

            collectBlockOutputs(blockItemOutputs, blockFluidOutputs, seed);
            collectLootOutputs(staticLootOutputs, pendingLootSources, seed);
        }

        List<LootWarmupTask> pendingLootTasks = new ArrayList<>(pendingLootSources.size());
        for (Map.Entry<LootSourceKey, List<StructureJeiRecipe>> entry : pendingLootSources.entrySet()) {
            pendingLootTasks.add(new LootWarmupTask(entry.getKey(), entry.getValue()));
        }

        return new PreparedVisibleState(
            buildId,
            request.signature,
            request.sortedRecipes,
            freezeIndex(blockItemOutputs),
            freezeIndex(blockFluidOutputs),
            freezeIndex(staticLootOutputs),
            pendingLootTasks
        );
    }

    private static boolean shouldAbortBuild(long buildId, VisibilitySignature signature) {
        if (Thread.currentThread().isInterrupted()) return true;
        if (buildId != requestedBuildId) return true;

        VisibilitySignature pendingSignature = requestedSignature;
        return pendingSignature == null || !pendingSignature.equals(signature);
    }

    private static void collectBlockOutputs(
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> blockItemOutputs,
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> blockFluidOutputs,
            BuildSeed seed) {
        Set<StructureJeiIngredientKeys.IngredientKey> seenItemKeys = new HashSet<>();
        Set<StructureJeiIngredientKeys.IngredientKey> seenFluidKeys = new HashSet<>();

        for (BlockEntry blockEntry : seed.structureInfo.getBlocks()) {
            if (blockEntry.displayStack != null && !blockEntry.displayStack.isEmpty()) {
                StructureJeiIngredientKeys.IngredientKey itemKey =
                    StructureJeiIngredientKeys.itemLookupKey(blockEntry.displayStack);
                if (seenItemKeys.add(itemKey)) {
                    addIndexedRecipe(blockItemOutputs, itemKey.copyForStorage(), seed.blockRecipe);
                }
            }

            if (blockEntry.displayFluid == null || blockEntry.displayFluid.getFluid() == null
                    || blockEntry.displayFluid.amount <= 0) {
                continue;
            }

            StructureJeiIngredientKeys.IngredientKey fluidKey =
                StructureJeiIngredientKeys.fluidLookupKey(blockEntry.displayFluid);
            if (seenFluidKeys.add(fluidKey)) {
                addIndexedRecipe(blockFluidOutputs, fluidKey.copyForStorage(), seed.blockRecipe);
            }
        }
    }

    private static void collectLootOutputs(
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> staticLootOutputs,
            Map<LootSourceKey, List<StructureJeiRecipe>> pendingLootSources, BuildSeed seed) {
        Set<StructureJeiIngredientKeys.IngredientKey> seenStaticKeys = new HashSet<>();
        Set<LootSourceKey> seenDynamicSources = new LinkedHashSet<>();

        for (LootEntry lootEntry : seed.structureInfo.getLootTables()) {
            LootSourceKey dynamicSource = createDynamicLootSource(lootEntry);
            if (dynamicSource != null) {
                if (!seenDynamicSources.add(dynamicSource)) continue;

                List<ItemStack> cachedOutputs = RESOLVED_LOOT_SOURCE_CACHE.get(dynamicSource);
                if (cachedOutputs != null) {
                    addResolvedLootOutputs(staticLootOutputs, cachedOutputs, seed.lootRecipe);
                    continue;
                }

                addIndexedRecipe(pendingLootSources, dynamicSource, seed.lootRecipe);
                continue;
            }

            addStaticLootOutputs(staticLootOutputs, seenStaticKeys, lootEntry, seed.lootRecipe);
        }
    }

    @Nullable
    private static LootSourceKey createDynamicLootSource(LootEntry lootEntry) {
        if (lootEntry.kind == LootEntryKind.LOOT_TABLE && lootEntry.lootTableId != null) {
            return LootSourceKey.forLootTable(lootEntry.lootTableId);
        }

        if (lootEntry.kind == LootEntryKind.GENERATED_ITEMS && lootEntry.sourceStack != null
                && !lootEntry.sourceStack.isEmpty()) {
            return LootSourceKey.forGeneratedItems(lootEntry.sourceStack);
        }

        return null;
    }

    private static void addStaticLootOutputs(
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> staticLootOutputs,
            Set<StructureJeiIngredientKeys.IngredientKey> seenStaticKeys, LootEntry lootEntry,
            StructureJeiRecipe lootRecipe) {
        if (lootEntry.possibleDrops == null || lootEntry.possibleDrops.isEmpty()) return;

        for (ItemStack possibleDrop : lootEntry.possibleDrops) {
            if (possibleDrop == null || possibleDrop.isEmpty()) continue;

            ItemStack displayStack = LootTableResolver.normalizeForDisplay(possibleDrop);
            if (displayStack.isEmpty()) continue;

            displayStack.setCount(1);
            StructureJeiIngredientKeys.IngredientKey itemKey = StructureJeiIngredientKeys.itemLookupKey(displayStack);
            if (!seenStaticKeys.add(itemKey)) continue;

            addIndexedRecipe(staticLootOutputs, itemKey.copyForStorage(), lootRecipe);
        }
    }

    private static void processLootWarmup() {
        VisibleState state = visibleState;
        if (state == null || !state.hasPendingLootTasks()) return;

        World lootResolutionWorld = getLootResolutionWorld();
        if (lootResolutionWorld == null) return;

        long startTime = System.nanoTime();
        int processedSources = 0;

        while (state == visibleState) {
            LootWarmupTask task = state.pollPendingLootTask();
            if (task == null) return;

            long sourceStartTime = System.nanoTime();

            List<ItemStack> resolvedOutputs = RESOLVED_LOOT_SOURCE_CACHE.get(task.sourceKey);
            if (resolvedOutputs == null) {
                resolvedOutputs = Collections.unmodifiableList(resolveLootOutputs(task.sourceKey, lootResolutionWorld));
                RESOLVED_LOOT_SOURCE_CACHE.putIfAbsent(task.sourceKey, resolvedOutputs);
                resolvedOutputs = RESOLVED_LOOT_SOURCE_CACHE.get(task.sourceKey);
            }

            addResolvedLootOutputs(state.lootItemOutputs, resolvedOutputs, task.recipes);
            recordMainThreadLootPhase(state.buildId, System.nanoTime() - sourceStartTime);
            processedSources++;

            if (!state.hasPendingLootTasks()) {
                finishWarmup(state);
                return;
            }

            if (processedSources > 0 && System.nanoTime() - startTime >= LOOT_WARMUP_BUDGET_NS) return;
        }
    }

    private static String describeWarmupReason(VisibilitySignature nextSignature, @Nullable VisibleState currentState) {
        if (currentState == null) return "no cached visibility snapshot";

        VisibilitySignature currentSignature = currentState.signature;
        List<String> reasons = new ArrayList<>(3);
        if (currentSignature.providerRevision != nextSignature.providerRevision) {
            reasons.add("provider registry changed");
        }
        if (!Objects.equals(currentSignature.stageSnapshot, nextSignature.stageSnapshot)) {
            reasons.add("client stage snapshot changed");
        }
        if (!currentSignature.blacklistFingerprint.equals(nextSignature.blacklistFingerprint)) {
            reasons.add("client blacklist changed");
        }

        if (reasons.isEmpty()) return "cached snapshot was replaced";

        return String.join(", ", reasons);
    }

    private static void recordBackgroundPhase(PreparedVisibleState preparedState, long backgroundNanos) {
        WarmupProgress warmup = activeWarmup;
        if (warmup == null || warmup.buildId != preparedState.buildId) return;

        warmup.backgroundNanos = backgroundNanos;
        warmup.blockItemKeyCount = preparedState.blockItemOutputs.size();
        warmup.blockFluidKeyCount = preparedState.blockFluidOutputs.size();
        warmup.staticLootKeyCount = preparedState.staticLootOutputs.size();
        warmup.dynamicLootSourceCount = preparedState.pendingLootTasks.size();
    }

    private static void recordMainThreadLootPhase(long buildId, long elapsedNanos) {
        WarmupProgress warmup = activeWarmup;
        if (warmup == null || warmup.buildId != buildId) return;

        warmup.mainThreadLootNanos += elapsedNanos;
    }

    private static void finishWarmup(VisibleState state) {
        WarmupProgress warmup = activeWarmup;
        if (warmup == null || warmup.buildId != state.buildId) return;

        SimpleStructureScanner.LOGGER.info(
            "Finished JEI structure warmup: {} visible structures, {} block item keys, {} block fluid keys, {} static loot keys, {} dynamic loot sources, {} ms off-thread indexing, {} ms main-thread loot, {} ms total",
            Integer.valueOf(warmup.visibleStructureCount),
            Integer.valueOf(warmup.blockItemKeyCount),
            Integer.valueOf(warmup.blockFluidKeyCount),
            Integer.valueOf(warmup.staticLootKeyCount),
            Integer.valueOf(warmup.dynamicLootSourceCount),
            Double.valueOf(roundMillis(warmup.backgroundNanos)),
            Double.valueOf(roundMillis(warmup.mainThreadLootNanos)),
            Double.valueOf(roundMillis(System.nanoTime() - warmup.startNanos))
        );

        activeWarmup = null;
    }

    private static double roundMillis(long nanos) {
        return Math.round(nanos / 100_000.0D) / 10.0D;
    }

    private static List<ItemStack> resolveLootOutputs(LootSourceKey sourceKey, World lootResolutionWorld) {
        Map<StructureJeiIngredientKeys.IngredientKey, ItemStack> outputsByKey = new HashMap<>();

        if (sourceKey.kind == LootSourceKind.LOOT_TABLE && sourceKey.lootTableId != null) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            for (LootItem lootItem : LootTableResolver.resolveLootTableWithSimulation(
                    lootResolutionWorld, sourceKey.lootTableId, player)) {
                addResolvedLootOutput(outputsByKey, lootItem.stack);
            }

            return new ArrayList<>(outputsByKey.values());
        }

        if (sourceKey.kind == LootSourceKind.GENERATED_ITEMS && sourceKey.sourceStack != null) {
            for (LootItem lootItem : RecurrentComplexLootResolver.resolveGeneratedLootWithSimulation(
                    lootResolutionWorld, sourceKey.sourceStack.copy())) {
                addResolvedLootOutput(outputsByKey, lootItem.stack);
            }
        }

        return new ArrayList<>(outputsByKey.values());
    }

    private static void addResolvedLootOutput(Map<StructureJeiIngredientKeys.IngredientKey, ItemStack> outputsByKey,
            ItemStack stack) {
        if (stack.isEmpty()) return;

        ItemStack displayStack = LootTableResolver.normalizeForDisplay(stack);
        if (displayStack.isEmpty()) return;

        displayStack.setCount(1);
        outputsByKey.putIfAbsent(StructureJeiIngredientKeys.itemStorageKey(displayStack), displayStack);
    }

    @Nullable
    private static World getLootResolutionWorld() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) return null;
        if (minecraft.getIntegratedServer() == null) return minecraft.world;

        WorldServer serverWorld = minecraft.getIntegratedServer().getWorld(minecraft.world.provider.getDimension());
        return serverWorld != null ? serverWorld : minecraft.world;
    }

    private static Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> freezeIndex(
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> sourceIndex) {
        if (sourceIndex.isEmpty()) return Collections.emptyMap();

        Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> frozenIndex = new HashMap<>();
        for (Map.Entry<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> entry : sourceIndex.entrySet()) {
            frozenIndex.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }

        return Collections.unmodifiableMap(frozenIndex);
    }

    private static Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> copyMutableIndex(
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> sourceIndex) {
        if (sourceIndex.isEmpty()) return new HashMap<>();

        Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> copy = new HashMap<>();
        for (Map.Entry<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> entry : sourceIndex.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        return copy;
    }

    private static void addResolvedLootOutputs(
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> lootOutputs,
            List<ItemStack> resolvedOutputs, StructureJeiRecipe recipe) {
        if (resolvedOutputs.isEmpty()) return;

        for (ItemStack output : resolvedOutputs) {
            addIndexedRecipe(lootOutputs, StructureJeiIngredientKeys.itemStorageKey(output), recipe);
        }
    }

    private static void addResolvedLootOutputs(
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> lootOutputs,
            List<ItemStack> resolvedOutputs, List<StructureJeiRecipe> recipes) {
        if (resolvedOutputs.isEmpty() || recipes.isEmpty()) return;

        for (ItemStack output : resolvedOutputs) {
            StructureJeiIngredientKeys.IngredientKey key = StructureJeiIngredientKeys.itemStorageKey(output);

            for (StructureJeiRecipe recipe : recipes) {
                addIndexedRecipe(lootOutputs, key, recipe);
            }
        }
    }

    private static <K> void addIndexedRecipe(Map<K, List<StructureJeiRecipe>> index, K key,
            StructureJeiRecipe recipe) {
        List<StructureJeiRecipe> bucket = index.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (!bucket.contains(recipe)) bucket.add(recipe);
    }

    private static final class BuildRequest {
        private final VisibilitySignature signature;
        private final List<BuildSeed> seeds;
        private final Map<StructureJeiView, List<StructureJeiRecipe>> sortedRecipes;

        private BuildRequest(VisibilitySignature signature, List<BuildSeed> seeds,
                Map<StructureJeiView, List<StructureJeiRecipe>> sortedRecipes) {
            this.signature = signature;
            this.seeds = seeds;
            this.sortedRecipes = sortedRecipes;
        }
    }

    private static final class BuildSeed {
        private final StructureInfo structureInfo;
        private final String sortKey;
        private final StructureJeiRecipe previewRecipe;
        private final StructureJeiRecipe blockRecipe;
        private final StructureJeiRecipe lootRecipe;

        private BuildSeed(StructureInfo structureInfo, String sortKey, StructureJeiRecipe previewRecipe,
            StructureJeiRecipe blockRecipe, StructureJeiRecipe lootRecipe) {
            this.structureInfo = structureInfo;
            this.sortKey = sortKey;
            this.previewRecipe = previewRecipe;
            this.blockRecipe = blockRecipe;
            this.lootRecipe = lootRecipe;
        }
    }

    private static final class PreparedVisibleState {
        private final long buildId;
        private final VisibilitySignature signature;
        private final Map<StructureJeiView, List<StructureJeiRecipe>> sortedRecipes;
        private final Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> blockItemOutputs;
        private final Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> blockFluidOutputs;
        private final Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> staticLootOutputs;
        private final List<LootWarmupTask> pendingLootTasks;

        private PreparedVisibleState(long buildId, VisibilitySignature signature,
                Map<StructureJeiView, List<StructureJeiRecipe>> sortedRecipes,
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> blockItemOutputs,
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> blockFluidOutputs,
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> staticLootOutputs,
                List<LootWarmupTask> pendingLootTasks) {
            this.buildId = buildId;
            this.signature = signature;
            this.sortedRecipes = sortedRecipes;
            this.blockItemOutputs = blockItemOutputs;
            this.blockFluidOutputs = blockFluidOutputs;
            this.staticLootOutputs = staticLootOutputs;
            this.pendingLootTasks = pendingLootTasks;
        }

        private VisibleState toVisibleState() {
            return new VisibleState(buildId, signature, sortedRecipes, blockItemOutputs, blockFluidOutputs,
                copyMutableIndex(staticLootOutputs), new ArrayList<>(pendingLootTasks));
        }

        private boolean hasPendingLootTasks() {
            return !pendingLootTasks.isEmpty();
        }
    }

    private static final class VisibleState {
        private final long buildId;
        private final VisibilitySignature signature;
        private final Map<StructureJeiView, List<StructureJeiRecipe>> sortedRecipes;
        private final Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> blockItemOutputs;
        private final Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> blockFluidOutputs;
        private final Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> lootItemOutputs;
        private final List<LootWarmupTask> pendingLootTasks;
        private int nextPendingLootTaskIndex = 0;

        private VisibleState(long buildId, VisibilitySignature signature,
                Map<StructureJeiView, List<StructureJeiRecipe>> sortedRecipes,
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> blockItemOutputs,
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> blockFluidOutputs,
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> lootItemOutputs,
                List<LootWarmupTask> pendingLootTasks) {
            this.buildId = buildId;
            this.signature = signature;
            this.sortedRecipes = sortedRecipes;
            this.blockItemOutputs = blockItemOutputs;
            this.blockFluidOutputs = blockFluidOutputs;
            this.lootItemOutputs = lootItemOutputs;
            this.pendingLootTasks = pendingLootTasks;
        }

        private static VisibleState visibleOnly(long buildId, VisibilitySignature signature,
                Map<StructureJeiView, List<StructureJeiRecipe>> sortedRecipes) {
            return new VisibleState(buildId, signature, sortedRecipes, Collections.emptyMap(), Collections.emptyMap(),
                new LinkedHashMap<>(), Collections.emptyList());
        }

        private boolean matches(VisibilitySignature other) {
            return signature.equals(other);
        }

        private List<StructureJeiRecipe> getSortedRecipes(StructureJeiView view) {
            return sortedRecipes.getOrDefault(view, Collections.emptyList());
        }

        private List<StructureJeiRecipe> getBlockItemMatches(StructureJeiIngredientKeys.IngredientKey key) {
            return blockItemOutputs.getOrDefault(key, Collections.emptyList());
        }

        private List<StructureJeiRecipe> getBlockFluidMatches(StructureJeiIngredientKeys.IngredientKey key) {
            return blockFluidOutputs.getOrDefault(key, Collections.emptyList());
        }

        private List<StructureJeiRecipe> getLootItemMatches(StructureJeiIngredientKeys.IngredientKey key) {
            return lootItemOutputs.getOrDefault(key, Collections.emptyList());
        }

        private boolean hasPendingLootTasks() {
            return nextPendingLootTaskIndex < pendingLootTasks.size();
        }

        @Nullable
        private LootWarmupTask pollPendingLootTask() {
            if (!hasPendingLootTasks()) return null;

            return pendingLootTasks.get(nextPendingLootTaskIndex++);
        }
    }

    private static final class LootWarmupTask {
        private final LootSourceKey sourceKey;
        private final List<StructureJeiRecipe> recipes;

        private LootWarmupTask(LootSourceKey sourceKey, List<StructureJeiRecipe> recipes) {
            this.sourceKey = sourceKey;
            this.recipes = recipes;
        }
    }

    private enum LootSourceKind {
        LOOT_TABLE,
        GENERATED_ITEMS
    }

    private static final class LootSourceKey {
        private final LootSourceKind kind;
        @Nullable
        private final ResourceLocation lootTableId;
        @Nullable
        private final ItemStack sourceStack;
        private final String token;

        private LootSourceKey(LootSourceKind kind, @Nullable ResourceLocation lootTableId,
                @Nullable ItemStack sourceStack, String token) {
            this.kind = kind;
            this.lootTableId = lootTableId;
            this.sourceStack = sourceStack;
            this.token = token;
        }

        private static LootSourceKey forLootTable(ResourceLocation lootTableId) {
            return new LootSourceKey(LootSourceKind.LOOT_TABLE, lootTableId, null, lootTableId.toString());
        }

        private static LootSourceKey forGeneratedItems(ItemStack sourceStack) {
            ItemStack normalizedStack = sourceStack.copy();
            normalizedStack.setCount(1);

            NBTTagCompound serializedStack = normalizedStack.writeToNBT(new NBTTagCompound());
            serializedStack.removeTag("Count");

            return new LootSourceKey(LootSourceKind.GENERATED_ITEMS, null, normalizedStack,
                serializedStack.toString());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof LootSourceKey)) return false;

            LootSourceKey that = (LootSourceKey) other;
            return kind == that.kind && token.equals(that.token);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, token);
        }
    }

    private static final class VisibilitySignature {
        private final long providerRevision;
        @Nullable
        private final Set<String> stageSnapshot;
        private final String blacklistFingerprint;

        private VisibilitySignature(long providerRevision, @Nullable Set<String> stageSnapshot,
                String blacklistFingerprint) {
            this.providerRevision = providerRevision;
            this.stageSnapshot = stageSnapshot;
            this.blacklistFingerprint = blacklistFingerprint;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof VisibilitySignature)) return false;

            VisibilitySignature that = (VisibilitySignature) other;
            return providerRevision == that.providerRevision
                && Objects.equals(stageSnapshot, that.stageSnapshot)
                && blacklistFingerprint.equals(that.blacklistFingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(providerRevision, stageSnapshot, blacklistFingerprint);
        }
    }

    public static final class WarmupProgress {
        private final long buildId;
        private final String reason;
        private final int visibleStructureCount;
        private final long startNanos;
        private volatile long backgroundNanos;
        private volatile long mainThreadLootNanos;
        private volatile int blockItemKeyCount;
        private volatile int blockFluidKeyCount;
        private volatile int staticLootKeyCount;
        private volatile int dynamicLootSourceCount;

        private WarmupProgress(long buildId, String reason, int visibleStructureCount, long startNanos) {
            this.buildId = buildId;
            this.reason = reason;
            this.visibleStructureCount = visibleStructureCount;
            this.startNanos = startNanos;
        }

        public String getReason() {
            return reason;
        }
    }
}