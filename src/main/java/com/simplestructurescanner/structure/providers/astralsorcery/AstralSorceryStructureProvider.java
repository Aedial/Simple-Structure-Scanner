package com.simplestructurescanner.structure.providers.astralsorcery;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.providers.AbstractStructureProvider;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.util.ReflectionHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper.ReflectionException;


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

        registerTemplate("small_shrine", "smallShrine", true);
        registerTemplate("treasure_shrine", "treasureShrine", false);
        registerTemplate("ancient_shrine", "ancientShrine", true);
        registerTemplate("desert_shrine", "desertShrine", true);
    }

    private StructureInfo registerTemplate(String path, String templateField, boolean shrineLoot) {
        return register(path).fromLayersSupplier(ignored -> loadTemplateLayers(path, templateField, shrineLoot));
    }

    private List<StructureInfo.StructureLayer> loadTemplateLayers(String path, String fieldName, boolean shrineLoot) {
        try {
            Class<?> multiblockArraysClass = ReflectionHelper.loadClassRequired(MULTIBLOCK_ARRAYS_CLASS);
            Object template = ReflectionHelper.getStaticField(multiblockArraysClass, fieldName);
            if (template == null) {
                throw new ReflectionException("Astral Sorcery template is not initialized: " + fieldName);
            }

            Object patternValue = ReflectionHelper.invokeRequired(template, "getPattern");
            if (!(patternValue instanceof Map)) {
                throw new ReflectionException("Astral Sorcery template pattern is not a map: " + fieldName);
            }

            Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
            Map<BlockPos, NBTTagCompound> blockEntityDataByPos = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : ((Map<?, ?>) patternValue).entrySet()) {
                if (!(entry.getKey() instanceof BlockPos)) continue;

                Object blockInformation = entry.getValue();
                if (blockInformation == null) continue;

                Object stateValue = ReflectionHelper.getField(blockInformation, blockInformation.getClass(), "state");
                if (!(stateValue instanceof IBlockState)) continue;

                IBlockState state = (IBlockState) stateValue;
                if (StructureNBTParser.isInvisibleBlock(state.getBlock())) continue;

                BlockPos pos = (BlockPos) entry.getKey();
                if (shrineLoot && state.getBlock() == Blocks.CHEST) {
                    blockEntityDataByPos.put(pos, createLootTableData(SHRINE_LOOT_TABLE, "minecraft:chest"));
                }

                blocks.put(pos, state);
            }

            return StructureInfo.createLayers(blocks, blockEntityDataByPos);
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to load Astral Sorcery structure template {}", path, e);
            return Collections.emptyList();
        }
    }

    private static NBTTagCompound createLootTableData(ResourceLocation lootTableId, String tileEntityId) {
        NBTTagCompound blockEntityData = new NBTTagCompound();
        blockEntityData.setString("id", tileEntityId);
        blockEntityData.setString("LootTable", lootTableId.toString());
        return blockEntityData;
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        return null;
    }
}
