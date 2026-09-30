package com.simplestructurescanner.structure.astralsorcery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.AbstractStructureProvider;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.util.ReflectionHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper.ReflectionException;
import com.simplestructurescanner.structure.util.StructureContentAccumulator;


public class AstralSorceryStructureProvider extends AbstractStructureProvider {

    private static final String PROVIDER_ID = "astralsorcery";
    private static final String MOD_NAME = "gui.structurescanner.provider.astralsorcery";
    private static final String MOD_ID = "astralsorcery";
    private static final String MULTIBLOCK_ARRAYS_CLASS =
        "hellfirepvp.astralsorcery.common.lib.MultiBlockArrays";
    private static final ResourceLocation SHRINE_LOOT_TABLE = new ResourceLocation(MOD_ID, "chest_shrine");

    public AstralSorceryStructureProvider() {
        super(PROVIDER_ID, MOD_ID, MOD_NAME, MOD_ID);
    }

    @Override
    public void postInit() {
        resetStructures();

        registerTemplate("small_shrine", "gui.structurescanner.structures.astralsorcery.small_shrine",
            "smallShrine");
        registerTemplate("treasure_shrine", "gui.structurescanner.structures.astralsorcery.treasure_shrine",
            "treasureShrine");
        registerTemplate("ancient_shrine", "gui.structurescanner.structures.astralsorcery.ancient_shrine",
            "ancientShrine");
        registerTemplate("desert_shrine", "gui.structurescanner.structures.astralsorcery.desert_shrine",
            "desertShrine");
    }

    private void registerTemplate(String path, String displayNameKey, String templateField) {
        StructureInfo info = registerStructure(path, displayNameKey, 0, 0, 0);

        try {
            applyTemplate(info, path, templateField);
            if (usesShrineLoot(path)) {
                addLootTables(path, createLootEntry(SHRINE_LOOT_TABLE, "gui.structurescanner.loot.chest"));
            }
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to load Astral Sorcery structure template {}", path, e);
        }
    }

    private void applyTemplate(StructureInfo info, String path, String fieldName) throws ReflectionException {
        Class<?> multiblockArraysClass = ReflectionHelper.loadClassRequired(MULTIBLOCK_ARRAYS_CLASS);
        Object template = ReflectionHelper.getStaticField(multiblockArraysClass, fieldName);
        if (template == null) throw new ReflectionException("Astral Sorcery template is not initialized: " + fieldName);

        Object patternValue = ReflectionHelper.invokeRequired(template, "getPattern");
        if (!(patternValue instanceof Map)) {
            throw new ReflectionException("Astral Sorcery template pattern is not a map: " + fieldName);
        }

        StructureContentAccumulator contents = new StructureContentAccumulator();
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
        Map<BlockPos, NBTTagCompound> blockEntityDataByPos = new LinkedHashMap<>();
        boolean usesShrineLoot = usesShrineLoot(path);
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Map.Entry<?, ?> entry : ((Map<?, ?>) patternValue).entrySet()) {
            if (!(entry.getKey() instanceof BlockPos)) continue;

            Object blockInformation = entry.getValue();
            if (blockInformation == null) continue;

            Object stateValue = ReflectionHelper.getField(blockInformation, blockInformation.getClass(), "state");
            if (!(stateValue instanceof IBlockState)) continue;

            IBlockState state = (IBlockState) stateValue;
            if (StructureNBTParser.isInvisibleBlock(state.getBlock())) continue;

            BlockPos pos = (BlockPos) entry.getKey();
            NBTTagCompound blockEntityData = null;
            if (usesShrineLoot && state.getBlock() == Blocks.CHEST) {
                blockEntityData = createLootTableData(SHRINE_LOOT_TABLE, "minecraft:chest");
                blockEntityDataByPos.put(pos, blockEntityData);
            }

            blocks.put(pos, state);
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());

            if (StructureNBTParser.isFlowingFluid(state, state.getBlock())) continue;

            contents.addBlockCount(
                StructureNBTParser.createDisplayedBlockKey(
                    state,
                    StructureNBTParser.createDisplayFluid(state),
                    StructureNBTParser.createDisplayStack(state, blockEntityData)
                ),
                state,
                blockEntityData
            );
        }

        contents.applyTo(info);
        if (blocks.isEmpty()) return;

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        Map<Integer, StructureInfo.StructureLayer> layers = new TreeMap<>();

        for (Map.Entry<BlockPos, IBlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            StructureInfo.StructureLayer layer = layers.get(pos.getY());
            if (layer == null) {
                layer = new StructureInfo.StructureLayer(pos.getY(), width, depth, minX, minZ);
                layers.put(pos.getY(), layer);
            }

            layer.setBlockState(
                pos.getX() - minX,
                pos.getZ() - minZ,
                entry.getValue(),
                blockEntityDataByPos.get(pos)
            );
        }

        info.setLayers(new ArrayList<>(layers.values()));
    }

    private static boolean usesShrineLoot(String path) {
        return "small_shrine".equals(path) || "ancient_shrine".equals(path) || "desert_shrine".equals(path);
    }

    private static NBTTagCompound createLootTableData(ResourceLocation lootTableId, String tileEntityId) {
        NBTTagCompound blockEntityData = new NBTTagCompound();
        blockEntityData.setString("id", tileEntityId);
        blockEntityData.setString("LootTable", lootTableId.toString());
        return blockEntityData;
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        return false;
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        return null;
    }
}
