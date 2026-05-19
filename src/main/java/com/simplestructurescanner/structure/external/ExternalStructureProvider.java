package com.simplestructurescanner.structure.external;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureProvider;


/**
 * Metadata-only structure provider loaded from config JSON.
 */
public class ExternalStructureProvider implements StructureProvider {

    private final String providerId;
    private final String modName;
    private final List<String> requiredMods;
    private final List<ResourceLocation> structureIds;
    private final Map<ResourceLocation, StructureInfo> structureInfos;

    public ExternalStructureProvider(String providerId, String modName, List<String> requiredMods,
            List<ResourceLocation> structureIds, Map<ResourceLocation, StructureInfo> structureInfos) {
        this.providerId = providerId;
        this.modName = modName;
        this.requiredMods = new ArrayList<>(requiredMods);
        this.structureIds = new ArrayList<>(structureIds);
        this.structureInfos = new LinkedHashMap<>(structureInfos);
    }

    @Override
    public String getProviderId() {
        return providerId;
    }

    @Override
    public String getModName() {
        return modName;
    }

    @Override
    public boolean isAvailable() {
        for (String modId : requiredMods) {
            if (!Loader.isModLoaded(modId)) return false;
        }

        return true;
    }

    @Override
    public void postInit() {
    }

    @Override
    public List<ResourceLocation> getStructureIds() {
        return new ArrayList<>(structureIds);
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        return false;
    }

    @Override
    @Nullable
    public StructureInfo getStructureInfo(ResourceLocation structureId) {
        return structureInfos.get(structureId);
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        return null;
    }
}