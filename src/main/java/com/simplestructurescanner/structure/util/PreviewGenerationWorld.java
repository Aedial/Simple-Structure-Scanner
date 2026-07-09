package com.simplestructurescanner.structure.util;

import java.io.File;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;


/**
 * Lightweight flat world used to let external structure generators compute preview layouts
 * without touching the real world or requiring terrain generation.
 */
public class PreviewGenerationWorld extends World {

    private static final WorldSettings PREVIEW_WORLD_SETTINGS = new WorldSettings(
        1L, GameType.CREATIVE, false, false, WorldType.FLAT
    );

    private final int groundY;

    public PreviewGenerationWorld(long seed, int groundY) {
        super(
            new PreviewSaveHandler(),
            new WorldInfo(new WorldSettings(seed, GameType.CREATIVE, false, false, WorldType.FLAT),
                "StructurePreviewGenerationWorld"),
            new WorldProviderSurface(),
            new Profiler(),
            true
        );

        this.groundY = groundY;
        this.provider.setDimension(Integer.MAX_VALUE - 4096);
        int providerDimension = this.provider.getDimension();
        this.provider.setWorld(this);
        this.provider.setDimension(providerDimension);
        this.chunkProvider = createChunkProvider();
        this.getWorldBorder().setSize(30000000);
    }

    @Override
    protected void initCapabilities() {
        // Preview generation only needs a lightweight world shell.
    }

    @Override
    protected IChunkProvider createChunkProvider() {
        return new PreviewChunkProvider(this, groundY);
    }

    @Override
    protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return 15 << 20 | 15 << 4;
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return 0;
    }

    @Override
    public WorldType getWorldType() {
        return WorldType.FLAT;
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        return getBlockState(pos).isSideSolid(this, pos, side);
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return getBlockState(pos).getBlock() == Blocks.AIR;
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return getPreviewState(pos.getY(), groundY);
    }

    private static IBlockState getPreviewState(int y, int groundY) {
        if (y < 0) return Blocks.BEDROCK.getDefaultState();
        if (y > groundY) return Blocks.AIR.getDefaultState();
        if (y == groundY) return Blocks.GRASS.getDefaultState();
        if (y >= groundY - 3) return Blocks.DIRT.getDefaultState();

        return Blocks.STONE.getDefaultState();
    }

    private static final class PreviewChunkProvider implements IChunkProvider {
        private final World world;
        private final int groundY;

        private PreviewChunkProvider(World world, int groundY) {
            this.world = world;
            this.groundY = groundY;
        }

        @Nullable
        @Override
        public Chunk getLoadedChunk(int x, int z) {
            return provideChunk(x, z);
        }

        @Override
        public Chunk provideChunk(int x, int z) {
            return new PreviewChunk(world, x, z, groundY);
        }

        @Override
        public boolean tick() {
            return false;
        }

        @Override
        public String makeString() {
            return "PreviewChunkProvider";
        }

        @Override
        public boolean isChunkGeneratedAt(int x, int z) {
            return true;
        }
    }

    private static final class PreviewChunk extends Chunk {
        private final int groundY;

        private PreviewChunk(World worldIn, int x, int z, int groundY) {
            super(worldIn, x, z);
            this.groundY = groundY;
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return getPreviewState(pos.getY(), groundY);
        }

        @Override
        public int getTopFilledSegment() {
            return groundY >> 4;
        }
    }

    private static final class PreviewSaveHandler implements ISaveHandler, IPlayerFileData, IChunkLoader {

        @Override
        public WorldInfo loadWorldInfo() {
            return new WorldInfo(PREVIEW_WORLD_SETTINGS, "StructurePreviewGenerationWorld");
        }

        @Override
        public void checkSessionLock() {
        }

        @Override
        public IChunkLoader getChunkLoader(WorldProvider provider) {
            return this;
        }

        @Override
        public IPlayerFileData getPlayerNBTManager() {
            return this;
        }

        @Override
        public TemplateManager getStructureTemplateManager() {
            return new TemplateManager("", new DataFixer(0));
        }

        @Override
        public void saveWorldInfoWithPlayer(WorldInfo worldInformation, NBTTagCompound tagCompound) {
        }

        @Override
        public void saveWorldInfo(WorldInfo worldInformation) {
        }

        @Override
        public File getWorldDirectory() {
            return null;
        }

        @Override
        public File getMapFileFromName(String mapName) {
            return null;
        }

        @Override
        public Chunk loadChunk(World worldIn, int x, int z) {
            return null;
        }

        @Override
        public void saveChunk(World worldIn, Chunk chunkIn) {
        }

        @Override
        public void saveExtraChunkData(World worldIn, Chunk chunkIn) {
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
        public void writePlayerData(EntityPlayer player) {
        }

        @Override
        public NBTTagCompound readPlayerData(EntityPlayer player) {
            return null;
        }

        @Override
        public String[] getAvailablePlayerDat() {
            return new String[0];
        }
    }
}