package com.simplestructurescanner.structure.wizardry;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.simplestructurescanner.structure.AbstractStructureProvider;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureNBTParser;


public class WizardryStructureProvider extends AbstractStructureProvider {

    private static final String PROVIDER_ID = "wizardry";
    private static final String MOD_NAME = "gui.structurescanner.provider.wizardry";
    private static final String MOD_ID = "ebwizardry";
    private static final ResourceLocation REMNANT_ID = new ResourceLocation(MOD_ID, "remnant");

    public WizardryStructureProvider() {
        super(PROVIDER_ID, MOD_ID, MOD_NAME, MOD_ID);
    }

    @Override
    public void postInit() {
        resetStructures();

        registerTemplate("wizard_tower");
        registerTemplate("shrine");
        registerTemplate("obelisk");
        registerTemplate("library_ruins");
        registerTemplate("underground_library_ruins");
    }

    private void registerTemplate(String path) {
        StructureInfo info = registerStructure(
            path,
            "gui.structurescanner.structures.wizardry." + path,
            0,
            0,
            0
        );
        StructureNBTParser.ParsedStructure parsed = StructureNBTParser.parseInstalledModStructure(
            MOD_ID,
            path + "_0",
            new WizardryTemplateExtension(path)
        );
        if (parsed != null) AbstractStructureProvider.apply(info, parsed);

        if ("wizard_tower".equals(path)) {
            addEntities(path, createEntityEntry("ebwizardry:wizard", 1));
        } else if ("shrine".equals(path)) {
            addLootTables(path,
                createLootEntry("ebwizardry:chests/shrine", "gui.structurescanner.loot.chest"));
        }
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

    private static final class WizardryTemplateExtension implements StructureNBTParser.StructureParseExtension {
        private final String structurePath;

        private WizardryTemplateExtension(String structurePath) {
            this.structurePath = structurePath;
        }

        @Override
        public boolean shouldCountBlock(@Nullable IBlockState state, @Nullable Block block) {
            return block != Blocks.STRUCTURE_BLOCK
                && StructureNBTParser.StructureParseExtension.super.shouldCountBlock(state, block);
        }

        @Override
        public boolean shouldStoreLayerBlock(@Nullable IBlockState state, @Nullable Block block) {
            return block != Blocks.STRUCTURE_BLOCK
                && StructureNBTParser.StructureParseExtension.super.shouldStoreLayerBlock(state, block);
        }

        @Override
        public void handleBlockEntity(StructureNBTParser.ParsedStructureBuilder builder,
                NBTTagCompound blockEntry, @Nullable IBlockState state, @Nullable Block block,
                NBTTagCompound nbtData) {
            if (block != Blocks.STRUCTURE_BLOCK) {
                StructureNBTParser.handleDefaultBlockEntity(builder, state, block, nbtData);
                return;
            }

            String marker = nbtData.getString("metadata");
            if ("obelisk".equals(structurePath) && "spawner".equals(marker)) {
                IBlockState spawnerState = Blocks.MOB_SPAWNER.getDefaultState();
                builder.addBlockCount(
                    StructureNBTParser.createDisplayedBlockKey(
                        spawnerState, null, StructureNBTParser.createDisplayStack(spawnerState)
                    ),
                    spawnerState
                );

                NBTTagList position = blockEntry.getTagList("pos", Constants.NBT.TAG_INT);
                builder.setLayerBlock(position.getIntAt(0), position.getIntAt(1), position.getIntAt(2), spawnerState);
                builder.addEntity(REMNANT_ID, true);
            }
        }
    }
}
