package com.simplestructurescanner.structure.generation;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

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

    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int platformY;
    private final int platformMinY;
    private final int platformMaxY;
    private final IBlockState platformMaterial;
    private final List<MapGenerationBuilder.Layer> aboveLayers;
    private final List<MapGenerationBuilder.Layer> belowLayers;
    private final Long2ObjectMap<IBlockState> changedStates = new Long2ObjectOpenHashMap<>();
    private final Map<Long, TileEntity> tileEntities = new HashMap<>();
    private final List<NBTTagCompound> generatedEntityData = new ArrayList<>();
    private final Set<Block> captureRemovedBlocks = new HashSet<>();

    MapGenerationWorld(int sizeX, int sizeZ, int platformY, IBlockState platformMaterial,
            List<MapGenerationBuilder.Layer> aboveLayers, List<MapGenerationBuilder.Layer> belowLayers,
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
        this.platformMaterial = platformMaterial;
        this.aboveLayers = Collections.unmodifiableList(new ArrayList<>(aboveLayers));
        this.belowLayers = Collections.unmodifiableList(new ArrayList<>(belowLayers));
        platformMinY = platformY - getLayerHeight(this.belowLayers);
        platformMaxY = platformY + getLayerHeight(this.aboveLayers);
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
        if (!isRemote) oldBlock.breakBlock(this, pos, oldState);
        updateDelta(pos, newState);
        if (oldBlock.hasTileEntity(oldState)) removeTileEntity(pos);
        if (!isRemote) newBlock.onBlockAdded(this, pos, newState);
        if (newBlock.hasTileEntity(newState)) {
            TileEntity tileEntity = getTileEntity(pos);
            if (tileEntity != null) tileEntity.updateContainingBlockInfo();
        }

        return true;
    }

    @Nonnull
    @Override
    public IBlockState getBlockState(@Nonnull BlockPos pos) {
        IBlockState state = getGeneratedState(pos);
        if (captureRemovedBlocks.contains(state.getBlock())) return Blocks.AIR.getDefaultState();

        return state;
    }

    @Nullable
    @Override
    public TileEntity getTileEntity(@Nonnull BlockPos pos) {
        IBlockState state = getGeneratedState(pos);
        if (captureRemovedBlocks.contains(state.getBlock()) || !state.getBlock().hasTileEntity(state)) return null;

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

    List<StructureLayer> capture(Set<Block> removedBlocks) {
        captureRemovedBlocks.clear();
        captureRemovedBlocks.addAll(removedBlocks);

        try {
            CaptureBounds bounds = findCaptureBounds();
            if (bounds == null) return Collections.emptyList();

            return buildLayers(bounds);
        } finally {
            captureRemovedBlocks.clear();
        }
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
        if (!isWithinPlatform(pos)) return Blocks.AIR.getDefaultState();

        IBlockState state = changedStates.get(pos.toLong());
        if (state != null) return state;

        return getPlatformState(pos);
    }

    private IBlockState getPlatformState(BlockPos pos) {
        if (!isWithinPlatform(pos)) return Blocks.AIR.getDefaultState();

        int relativeY = pos.getY() - platformY;
        if (relativeY == 0) return platformMaterial;

        List<MapGenerationBuilder.Layer> layers = relativeY > 0 ? aboveLayers : belowLayers;
        return getLayerState(layers, Math.abs(relativeY));
    }

    private static IBlockState getLayerState(List<MapGenerationBuilder.Layer> layers, int distance) {
        int currentDistance = 0;
        for (MapGenerationBuilder.Layer layer : layers) {
            currentDistance += layer.sizeY;
            if (distance <= currentDistance) return layer.material;
        }

        return Blocks.AIR.getDefaultState();
    }

    private static int getLayerHeight(List<MapGenerationBuilder.Layer> layers) {
        int height = 0;
        for (MapGenerationBuilder.Layer layer : layers) height += layer.sizeY;

        return height;
    }

    private boolean isWithinPlatform(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private void updateDelta(BlockPos pos, IBlockState state) {
        long key = pos.toLong();
        if (state.equals(getPlatformState(pos))) {
            changedStates.remove(key);
            return;
        }

        changedStates.put(key, state);
    }

    @Nullable
    private CaptureBounds findCaptureBounds() {
        boolean hasContent = false;
        int minCaptureX = Integer.MAX_VALUE;
        int minCaptureY = Integer.MAX_VALUE;
        int minCaptureZ = Integer.MAX_VALUE;
        int maxCaptureX = Integer.MIN_VALUE;
        int maxCaptureY = Integer.MIN_VALUE;
        int maxCaptureZ = Integer.MIN_VALUE;

        for (long key : changedStates.keySet()) {
            BlockPos pos = BlockPos.fromLong(key);
            IBlockState state = getBlockState(pos);
            if (state.getBlock() == Blocks.AIR) continue;

            hasContent = true;
            minCaptureX = Math.min(minCaptureX, pos.getX());
            minCaptureY = Math.min(minCaptureY, pos.getY());
            minCaptureZ = Math.min(minCaptureZ, pos.getZ());
            maxCaptureX = Math.max(maxCaptureX, pos.getX());
            maxCaptureY = Math.max(maxCaptureY, pos.getY());
            maxCaptureZ = Math.max(maxCaptureZ, pos.getZ());
        }

        if (!hasContent) return null;

        minCaptureY = Math.min(minCaptureY, platformMinY);
        maxCaptureY = Math.max(maxCaptureY, platformMaxY);
        return new CaptureBounds(
            new BlockPos(minCaptureX, minCaptureY, minCaptureZ),
            new BlockPos(maxCaptureX, maxCaptureY, maxCaptureZ)
        );
    }

    private List<StructureLayer> buildLayers(CaptureBounds bounds) {
        StructurePreviewStitcher preview = new StructurePreviewStitcher();

        for (BlockPos.MutableBlockPos mutablePos : BlockPos.getAllInBoxMutable(bounds.minPos, bounds.maxPos)) {
            BlockPos worldPos = new BlockPos(mutablePos.getX(), mutablePos.getY(), mutablePos.getZ());
            IBlockState state = getBlockState(worldPos);
            if (state.getBlock() == Blocks.AIR) continue;

            TileEntity tileEntity = getTileEntity(worldPos);
            NBTTagCompound tileData = tileEntity != null ? tileEntity.writeToNBT(new NBTTagCompound()) : null;
            preview.setBlock(worldPos.subtract(bounds.minPos), state, tileData);
        }

        return preview.buildLayers();
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
