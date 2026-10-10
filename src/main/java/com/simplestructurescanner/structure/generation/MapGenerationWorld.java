package com.simplestructurescanner.structure.generation;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableManager;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.StructureInfo.StructureLayer;
import com.simplestructurescanner.structure.util.StructurePreviewStitcher;


/**
 * In-memory world that overlays generated map changes on a finite layered platform.
 */
public final class MapGenerationWorld extends World {
    private static final LootTableManager MAP_LOOT_TABLE_MANAGER = new MapGenerationLootTableManager();
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int platformY;
    private final int platformMinY;
    private final int platformMaxY;
    private final IBlockState[] platformStates = new IBlockState[256];
    private final Long2ObjectMap<IBlockState> changedStates = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<TileEntity> tileEntities = new Long2ObjectOpenHashMap<>();
    private final List<NBTTagCompound> generatedEntityData = new ArrayList<>();
    private final Set<Block> excludedBlocks;
    private final Set<IBlockState> excludedStates;
    private final boolean hasExclusions;
    private final boolean capturePlatform;
    private final boolean captureLowestNonOpaqueFloor;
    private boolean hasCaptureContent;
    private int minCaptureX = Integer.MAX_VALUE;
    private int minCaptureY = Integer.MAX_VALUE;
    private int minCaptureZ = Integer.MAX_VALUE;
    private int maxCaptureX = Integer.MIN_VALUE;
    private int maxCaptureY = Integer.MIN_VALUE;
    private int maxCaptureZ = Integer.MIN_VALUE;
    @Nullable
    private BlockPos captureOrigin;

    MapGenerationWorld(int sizeX, int sizeZ, int platformY, IBlockState platformMaterial,
            List<MapGenerationBuilder.Layer> aboveLayers, List<MapGenerationBuilder.Layer> belowLayers,
            boolean capturePlatform, boolean captureLowestNonOpaqueFloor,
            Set<Block> excludedBlocks, Set<IBlockState> excludedStates,
            int originX, int originZ, long seed, Biome biome) {

        super(
            new MapGenerationSaveHandler(),
            new WorldInfo(new WorldSettings(seed, GameType.CREATIVE, false, false, WorldType.FLAT),
                "MapGenerationWorld"),
            new MapGenerationWorldProvider(biome),
            new Profiler(),
            false
        );

        minX = originX - sizeX / 2;
        maxX = minX + sizeX - 1;
        minZ = originZ - sizeZ / 2;
        maxZ = minZ + sizeZ - 1;
        this.platformY = platformY;
        this.capturePlatform = capturePlatform;
        this.captureLowestNonOpaqueFloor = captureLowestNonOpaqueFloor;
        platformMinY = platformY - getLayerHeight(belowLayers);
        platformMaxY = platformY + getLayerHeight(aboveLayers);
        this.excludedBlocks = Collections.unmodifiableSet(new HashSet<>(excludedBlocks));
        this.excludedStates = Collections.unmodifiableSet(new HashSet<>(excludedStates));
        hasExclusions = !this.excludedBlocks.isEmpty() || !this.excludedStates.isEmpty();
        fillPlatformStates(platformMaterial, aboveLayers, belowLayers);
        this.lootTable = MAP_LOOT_TABLE_MANAGER;

        this.provider.setDimension(Integer.MAX_VALUE - 666);
        int providerDimension = this.provider.getDimension();
        this.provider.setWorld(this);
        this.provider.setDimension(providerDimension);
        this.chunkProvider = createChunkProvider();
        this.getWorldBorder().setSize(30000000);
    }

    @Override
    protected void initCapabilities() {
        // Map generation only needs the temporary world shell
    }

    @Nonnull
    @Override
    protected IChunkProvider createChunkProvider() {
        return new MapGenerationChunkProvider(this);
    }

    @Override
    protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        return true;
    }

    @Override
    public boolean setBlockState(@Nonnull BlockPos pos, @Nonnull IBlockState newState, int flags) {
        if (!isWithinPlatform(pos) || pos.getY() < 0 || pos.getY() > 255) return false;

        IBlockState oldState = getGeneratedState(pos);
        if (oldState.equals(newState)) return false;

        Block oldBlock = oldState.getBlock();
        Block newBlock = newState.getBlock();
        TileEntity oldTileEntity = tileEntities.get(pos.toLong());

        if (updateDelta(pos, newState)) updateCaptureBounds(pos, newState);
        if (!isRemote && oldBlock != newBlock) oldBlock.breakBlock(this, pos, oldState);
        if (oldTileEntity != null && oldTileEntity.shouldRefresh(this, pos, oldState, newState)) removeTileEntity(pos);
        if (!isRemote && oldBlock != newBlock) newBlock.onBlockAdded(this, pos, newState);

        if (newBlock.hasTileEntity(newState)) {
            TileEntity tileEntity = getTileEntity(pos);
            if (tileEntity != null) tileEntity.updateContainingBlockInfo();
        }

        return true;
    }

    @Nonnull
    @Override
    public IBlockState getBlockState(@Nonnull BlockPos pos) {
        return getGeneratedState(pos);
    }

    @Nullable
    @Override
    public TileEntity getTileEntity(@Nonnull BlockPos pos) {
        IBlockState state = getGeneratedState(pos);
        if (!state.getBlock().hasTileEntity(state)) return null;

        TileEntity tileEntity = tileEntities.get(pos.toLong());
        if (tileEntity != null) return tileEntity;

        TileEntity created = state.getBlock().createTileEntity(this, state);
        if (created == null) return null;

        setTileEntity(pos, created);
        return created;
    }

    @Override
    public void setTileEntity(@Nonnull BlockPos pos, @Nullable TileEntity tileEntity) {
        removeTileEntity(pos);
        if (tileEntity == null) return;

        tileEntity.setWorld(this);
        tileEntity.setPos(pos);
        tileEntities.put(pos.toLong(), tileEntity);
    }

    @Override
    public void removeTileEntity(@Nonnull BlockPos pos) {
        TileEntity tileEntity = tileEntities.remove(pos.toLong());
        if (tileEntity != null) tileEntity.invalidate();
    }

    @Override
    public boolean spawnEntity(@Nullable Entity entity) {
        if (entity == null) return false;

        NBTTagCompound entityData = new NBTTagCompound();
        if (!entity.writeToNBTAtomically(entityData)) return false;

        generatedEntityData.add(entityData);
        return true;
    }

    @Override
    public boolean checkLightFor(@Nonnull EnumSkyBlock lightType, @Nonnull BlockPos pos) {
        return true;
    }

    @Override
    public int getLightFromNeighborsFor(@Nonnull EnumSkyBlock type, @Nonnull BlockPos pos) {
        return 15;
    }

    @Override
    public int getCombinedLight(@Nonnull BlockPos pos, int lightValue) {
        return 15 << 20 | 15 << 4;
    }

    @Override
    public int getStrongPower(@Nonnull BlockPos pos, @Nonnull EnumFacing direction) {
        return 0;
    }

    @Override
    public void markAndNotifyBlock(BlockPos pos, @Nullable Chunk chunk, IBlockState oldState,
            IBlockState newState, int flags) {
        // Generated blocks remain unchanged until the map is captured
    }

    @Nonnull
    @Override
    public WorldType getWorldType() {
        return WorldType.FLAT;
    }

    @Override
    public boolean isSideSolid(@Nonnull BlockPos pos, @Nonnull EnumFacing side, boolean _default) {
        return getBlockState(pos).isSideSolid(this, pos, side);
    }

    @Override
    public boolean isAirBlock(@Nonnull BlockPos pos) {
        return getBlockState(pos).getBlock() == Blocks.AIR;
    }

    @Nonnull
    @Override
    public BlockPos getTopSolidOrLiquidBlock(@Nonnull BlockPos xzPos) {
        int x = xzPos.getX();
        int z = xzPos.getZ();
        for (int y = 255; y >= 0; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            IBlockState state = getBlockState(pos);
            if (state.getMaterial().blocksMovement() && state.getBlock() != Blocks.LEAVES) return pos.up();
        }

        return new BlockPos(x, 0, z);
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public int getPlatformY() {
        return platformY;
    }

    List<NBTTagCompound> getGeneratedEntityData() {
        List<NBTTagCompound> copiedData = new ArrayList<>(generatedEntityData.size());
        for (NBTTagCompound entityData : generatedEntityData) copiedData.add(entityData.copy());

        return copiedData;
    }

    List<StructureLayer> capture() {
        captureOrigin = null;
        CaptureBounds bounds = getCaptureBounds();
        if (bounds == null) return Collections.emptyList();

        captureOrigin = bounds.minPos;
        return buildLayers(bounds);
    }

    @Nullable
    BlockPos getCaptureOrigin() {
        return captureOrigin;
    }

    private int getPlatformTopFilledSegment(int chunkX, int chunkZ) {
        int minChunkX = Math.max(minX, chunkX << 4);
        int maxChunkX = Math.min(maxX, (chunkX << 4) + 15);
        int minChunkZ = Math.max(minZ, chunkZ << 4);
        int maxChunkZ = Math.min(maxZ, (chunkZ << 4) + 15);
        if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) return 0;

        for (int y = platformMaxY; y >= platformMinY; y--) {
            for (int x = minChunkX; x <= maxChunkX; x++) {
                for (int z = minChunkZ; z <= maxChunkZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    IBlockState state = getGeneratedState(pos);
                    if (!state.getBlock().isAir(state, this, pos)) return y & -16;
                }
            }
        }

        return 0;
    }

    private int getHeightValue(int x, int z) {
        for (int y = 255; y >= 0; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            IBlockState state = getGeneratedState(pos);
            if (state.getLightOpacity(this, pos) != 0) return y + 1;
        }

        return 0;
    }

    private IBlockState getGeneratedState(BlockPos pos) {
        if (!isWithinPlatform(pos)) return AIR;

        IBlockState state = changedStates.get(pos.toLong());
        if (state != null) return state;

        return getPlatformState(pos.getY());
    }

    private IBlockState getPlatformState(BlockPos pos) {
        if (!isWithinPlatform(pos)) return AIR;

        return getPlatformState(pos.getY());
    }

    private IBlockState getPlatformState(int y) {
        if (y < 0 || y >= platformStates.length) return AIR;

        IBlockState state = platformStates[y];
        return state != null ? state : AIR;
    }

    private void fillPlatformStates(IBlockState platformMaterial, List<MapGenerationBuilder.Layer> aboveLayers,
            List<MapGenerationBuilder.Layer> belowLayers) {
        platformStates[platformY] = platformMaterial;

        int y = platformY;
        for (MapGenerationBuilder.Layer layer : aboveLayers) {
            for (int height = 0; height < layer.sizeY; height++) platformStates[++y] = layer.material;
        }

        y = platformY;
        for (MapGenerationBuilder.Layer layer : belowLayers) {
            for (int height = 0; height < layer.sizeY; height++) platformStates[--y] = layer.material;
        }
    }

    private static int getLayerHeight(List<MapGenerationBuilder.Layer> layers) {
        int height = 0;
        for (MapGenerationBuilder.Layer layer : layers) height += layer.sizeY;

        return height;
    }

    private boolean isWithinPlatform(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private boolean updateDelta(BlockPos pos, IBlockState state) {
        long key = pos.toLong();
        if (state.equals(getPlatformState(pos))) {
            changedStates.remove(key);
            return false;
        }

        changedStates.put(key, state);
        return true;
    }

    @Nullable
    private CaptureBounds getCaptureBounds() {
        if (!hasCaptureContent) return null;

        return new CaptureBounds(
            new BlockPos(minCaptureX, Math.min(minCaptureY, platformMinY), minCaptureZ),
            new BlockPos(maxCaptureX, Math.max(maxCaptureY, platformMaxY), maxCaptureZ)
        );
    }

    private List<StructureLayer> buildLayers(CaptureBounds bounds) {
        StructurePreviewStitcher preview = new StructurePreviewStitcher();
        LongOpenHashSet occupiedPositions = new LongOpenHashSet(changedStates.size());

        addGeneratedStates(preview, bounds, occupiedPositions);
        if (capturePlatform) addPlatformState(preview, bounds, occupiedPositions);
        if (captureLowestNonOpaqueFloor) addNonOpaqueFloorStates(preview, bounds, occupiedPositions);

        return preview.buildLayers();
    }

    private void addGeneratedStates(StructurePreviewStitcher preview, CaptureBounds bounds,
            LongOpenHashSet occupiedPositions) {
        for (Long2ObjectMap.Entry<IBlockState> entry : changedStates.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            IBlockState state = entry.getValue();
            if (state.getBlock() == Blocks.AIR && !isExcluded(state)) {
                preview.setRecordedAir(
                    unpackBlockX(key) - bounds.minPos.getX(),
                    unpackBlockY(key) - bounds.minPos.getY(),
                    unpackBlockZ(key) - bounds.minPos.getZ()
                );
            } else {
                addPreviewBlock(preview, BlockPos.fromLong(key), state, bounds);
            }
            occupiedPositions.add(key);
        }
    }

    private void addPlatformState(StructurePreviewStitcher preview, CaptureBounds bounds,
            LongOpenHashSet occupiedPositions) {
        IBlockState state = getPlatformState(platformY);
        if (state.getBlock() == Blocks.AIR || isExcluded(state)) return;

        int y = platformY - bounds.minPos.getY();
        int maxX = bounds.maxPos.getX() - bounds.minPos.getX();
        int maxZ = bounds.maxPos.getZ() - bounds.minPos.getZ();
        if (!hasExclusions && !state.getBlock().hasTileEntity(state)) {
            preview.addPlatform(y, 0, 0, maxX, maxZ, state);
            return;
        }

        BlockPos minPos = new BlockPos(bounds.minPos.getX(), platformY, bounds.minPos.getZ());
        BlockPos maxPos = new BlockPos(bounds.maxPos.getX(), platformY, bounds.maxPos.getZ());
        for (BlockPos.MutableBlockPos mutablePos : BlockPos.getAllInBoxMutable(minPos, maxPos)) {
            long key = mutablePos.toLong();
            if (occupiedPositions.contains(key)) continue;

            if (addPreviewBlock(preview, mutablePos, state, bounds)) occupiedPositions.add(key);
        }
    }

    private void addNonOpaqueFloorStates(StructurePreviewStitcher preview, CaptureBounds bounds,
            LongOpenHashSet occupiedPositions) {
        for (Long2ObjectMap.Entry<IBlockState> entry : getNonOpaqueFloorStates().long2ObjectEntrySet()) {
            if (occupiedPositions.contains(entry.getLongKey())) continue;

            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            if (capturePlatform && pos.getY() == platformY) continue;

            if (addPreviewBlock(preview, pos, entry.getValue(), bounds)) {
                occupiedPositions.add(entry.getLongKey());
            }
        }
    }

    private boolean addPreviewBlock(StructurePreviewStitcher preview, BlockPos pos, IBlockState state,
            CaptureBounds bounds) {
        if (state.getBlock() == Blocks.AIR || isExcluded(state)) return false;

        NBTTagCompound tileData = null;
        if (state.getBlock().hasTileEntity(state)) {
            TileEntity tileEntity = getTileEntity(pos);
            if (tileEntity != null) tileData = tileEntity.writeToNBT(new NBTTagCompound());
        }

        preview.setBlock(
            pos.getX() - bounds.minPos.getX(),
            pos.getY() - bounds.minPos.getY(),
            pos.getZ() - bounds.minPos.getZ(),
            state, tileData
        );
        return true;
    }

    private Long2ObjectMap<IBlockState> getNonOpaqueFloorStates() {
        Long2ObjectMap<IBlockState> floorStates = new Long2ObjectOpenHashMap<>();
        for (Long2ObjectMap.Entry<IBlockState> entry : changedStates.long2ObjectEntrySet()) {
            IBlockState state = entry.getValue();
            if (isExcluded(state) || state.isOpaqueCube()) continue;

            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            BlockPos floorPos = pos.down();
            if (floorPos.getY() < 0) continue;

            // Only consider the lowest non-opaque floor block for each column
            long floorKey = floorPos.toLong();
            if (changedStates.containsKey(floorKey)) continue;

            IBlockState floorState = getPlatformState(floorPos);
            if (floorState.getBlock() == Blocks.AIR || isExcluded(floorState)
                    || !floorState.isOpaqueCube()) continue;

            floorStates.put(floorKey, floorState);
        }

        return floorStates;
    }

    private void updateCaptureBounds(BlockPos pos, IBlockState state) {
        if (isExcluded(state)) return;

        hasCaptureContent = true;
        minCaptureX = Math.min(minCaptureX, pos.getX());
        minCaptureY = Math.min(minCaptureY, pos.getY());
        minCaptureZ = Math.min(minCaptureZ, pos.getZ());
        maxCaptureX = Math.max(maxCaptureX, pos.getX());
        maxCaptureY = Math.max(maxCaptureY, pos.getY());
        maxCaptureZ = Math.max(maxCaptureZ, pos.getZ());
    }

    private boolean isExcluded(IBlockState state) {
        if (!hasExclusions) return false;

        return excludedBlocks.contains(state.getBlock()) || excludedStates.contains(state);
    }

    private static int unpackBlockX(long key) {
        return (int) (key >> 38);
    }

    private static int unpackBlockY(long key) {
        return (int) (key << 26 >> 52);
    }

    private static int unpackBlockZ(long key) {
        return (int) (key << 38 >> 38);
    }

    private static final class CaptureBounds {
        private final BlockPos minPos;
        private final BlockPos maxPos;

        private CaptureBounds(BlockPos minPos, BlockPos maxPos) {
            this.minPos = minPos;
            this.maxPos = maxPos;
        }
    }

    /**
     * Loot table duck that prevents actual loot table loading during map generation.
     * We do not resolve actual loot tables during map generation, so there is no need to load their contents.
     * This spares heavy I/O and processing, that are only necessary when actually
     * viewing the structure in-game.
     */
    private static final class MapGenerationLootTableManager extends LootTableManager {

        private MapGenerationLootTableManager() {
            super((File) null);
        }

        @Override
        public void reloadLootTables() {
            // Reload nothing, as loot tables are not used during map generation
        }

        @Nonnull
        @Override
        public LootTable getLootTableFromLocation(ResourceLocation lootTableId) {
            SimpleStructureScanner.LOGGER.warn(
                "Map generation requested loot table {}; generated loot cannot retain its table metadata", lootTableId
            );

            return LootTable.EMPTY_LOOT_TABLE;
        }
    }

    private static final class MapGenerationWorldProvider extends WorldProviderSurface {
        private final BiomeProvider biomeProvider;

        private MapGenerationWorldProvider(Biome biome) {
            biomeProvider = new BiomeProviderSingle(biome);
        }

        @Override
        public BiomeProvider getBiomeProvider() {
            return biomeProvider;
        }
    }

    private static final class MapGenerationChunkProvider implements IChunkProvider {
        private final World world;
        private final Long2ObjectMap<Chunk> loadedChunks = new Long2ObjectOpenHashMap<>();

        private MapGenerationChunkProvider(World world) {
            this.world = world;
        }

        @Nullable
        @Override
        public Chunk getLoadedChunk(int x, int z) {
            return loadedChunks.get(ChunkPos.asLong(x, z));
        }

        @Nonnull
        @Override
        public Chunk provideChunk(int x, int z) {
            long key = ChunkPos.asLong(x, z);
            Chunk chunk = loadedChunks.get(key);
            if (chunk != null) return chunk;

            chunk = new MapGenerationChunk(world, x, z);
            loadedChunks.put(key, chunk);
            return chunk;
        }

        @Override
        public boolean tick() {
            return !loadedChunks.isEmpty();
        }

        @Nonnull
        @Override
        public String makeString() {
            return "MapGenerationChunkProvider";
        }

        @Override
        public boolean isChunkGeneratedAt(int x, int z) {
            return true;
        }
    }

    private static final class MapGenerationChunk extends Chunk {
        private final MapGenerationWorld world;

        private MapGenerationChunk(World world, int x, int z) {
            super(world, x, z);
            this.world = (MapGenerationWorld) world;
        }

        @Nonnull
        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return world.getBlockState(pos);
        }

        @Override
        public int getTopFilledSegment() {
            return Math.max(super.getTopFilledSegment(), world.getPlatformTopFilledSegment(x, z));
        }

        @Override
        public int getHeightValue(int x, int z) {
            return world.getHeightValue((this.x << 4) + x, (this.z << 4) + z);
        }

        @Nullable
        @Override
        public IBlockState setBlockState(BlockPos pos, IBlockState state) {
            IBlockState oldState = world.getBlockState(pos);
            if (!world.setBlockState(pos, state, 2)) return null;

            return oldState;
        }
    }

    private static final class MapGenerationSaveHandler implements ISaveHandler, IPlayerFileData, IChunkLoader {

        @Override
        public WorldInfo loadWorldInfo() {
            return null;
        }

        @Override
        public void checkSessionLock() {
        }

        @Nonnull
        @Override
        public IChunkLoader getChunkLoader(@Nonnull WorldProvider provider) {
            return this;
        }

        @Nonnull
        @Override
        public IPlayerFileData getPlayerNBTManager() {
            return this;
        }

        @Nonnull
        @Override
        public TemplateManager getStructureTemplateManager() {
            return new TemplateManager("", new DataFixer(0));
        }

        @Override
        public void saveWorldInfoWithPlayer(@Nonnull WorldInfo worldInformation, @Nonnull NBTTagCompound tagCompound) {
        }

        @Override
        public void saveWorldInfo(@Nonnull WorldInfo worldInformation) {
        }

        @Override
        public File getWorldDirectory() {
            return null;
        }

        @Override
        public File getMapFileFromName(@Nonnull String mapName) {
            return null;
        }

        @Nullable
        @Override
        public Chunk loadChunk(@Nonnull World world, int x, int z) {
            return null;
        }

        @Override
        public void saveChunk(@Nonnull World world, @Nonnull Chunk chunk) {
        }

        @Override
        public void saveExtraChunkData(@Nonnull World world, @Nonnull Chunk chunk) {
        }

        @Override
        public void chunkTick() {
        }

        @Override
        public void flush() {
        }

        @Override
        public boolean isChunkGeneratedAt(int x, int z) {
            return true;
        }

        @Override
        public void writePlayerData(@Nonnull EntityPlayer player) {
        }

        @Nullable
        @Override
        public NBTTagCompound readPlayerData(@Nonnull EntityPlayer player) {
            return null;
        }

        @Nonnull
        @Override
        public String[] getAvailablePlayerDat() {
            return new String[0];
        }
    }
}
