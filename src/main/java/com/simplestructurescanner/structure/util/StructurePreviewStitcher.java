package com.simplestructurescanner.structure.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;

import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureNBTParser;


/**
 * Shared stitched-preview builder for procedural multi-piece structures.
 * Other procedural or multi-piece providers can reuse the same layer assembly logic
 * instead of each carrying a private copy of the same accumulator classes.
 */
public class StructurePreviewStitcher {

    private final Map<BlockPos, PreviewBlock> blocks = new LinkedHashMap<>();

    public void setBlock(BlockPos pos, @Nullable IBlockState state) {
        setBlock(pos, state, null);
    }

    public void setBlock(BlockPos pos, @Nullable IBlockState state, @Nullable NBTTagCompound blockEntityData) {
        if (state == null) return;

        blocks.put(pos, new PreviewBlock(state, blockEntityData));
    }

    public void addParsedStructure(StructureNBTParser.ParsedStructure parsed, BlockPos origin) {
        addParsedStructure(parsed, origin, Mirror.NONE, Rotation.NONE);
    }

    public void addParsedStructure(StructureNBTParser.ParsedStructure parsed, BlockPos origin,
            Mirror mirror, Rotation rotation) {
        if (parsed == null || parsed.layers == null) return;

        for (StructureInfo.StructureLayer layer : parsed.layers) {
            if (layer == null) continue;

            for (int x = 0; x < layer.width; x++) {
                for (int z = 0; z < layer.depth; z++) {
                    IBlockState state = layer.getBlockState(x, z);
                    if (state == null) continue;

                    BlockPos pos = transformParsedPosition(parsed,
                        new BlockPos(layer.xOffset + x, layer.y, layer.zOffset + z), origin, mirror, rotation);
                    setBlock(pos, state, layer.getBlockEntityData(x, z));
                }
            }
        }
    }

    private BlockPos transformParsedPosition(StructureNBTParser.ParsedStructure parsed, BlockPos localPos,
            BlockPos origin, Mirror mirror, Rotation rotation) {
        int x = localPos.getX();
        int y = localPos.getY();
        int z = localPos.getZ();

        switch (mirror) {
            case LEFT_RIGHT:
                z = parsed.sizeZ - 1 - z;
                break;

            case FRONT_BACK:
                x = parsed.sizeX - 1 - x;
                break;

            default:
                break;
        }

        switch (rotation) {
            case COUNTERCLOCKWISE_90:
                return origin.add(z, y, parsed.sizeX - 1 - x);

            case CLOCKWISE_90:
                return origin.add(parsed.sizeZ - 1 - z, y, x);

            case CLOCKWISE_180:
                return origin.add(parsed.sizeX - 1 - x, y, parsed.sizeZ - 1 - z);

            default:
                return origin.add(x, y, z);
        }
    }

    public List<StructureInfo.StructureLayer> buildLayers() {
        if (blocks.isEmpty()) return Collections.emptyList();

        Map<Integer, PreviewLayerAccumulator> layersByY = new TreeMap<>();
        for (Map.Entry<BlockPos, PreviewBlock> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            PreviewLayerAccumulator layer = layersByY.computeIfAbsent(pos.getY(), PreviewLayerAccumulator::new);
            layer.add(pos, entry.getValue());
        }

        List<StructureInfo.StructureLayer> layers = new ArrayList<>();
        for (PreviewLayerAccumulator accumulator : layersByY.values()) {
            layers.add(accumulator.build());
        }

        return layers;
    }

    private static final class PreviewBlock {
        private final IBlockState state;
        @Nullable
        private final NBTTagCompound blockEntityData;

        private PreviewBlock(IBlockState state, @Nullable NBTTagCompound blockEntityData) {
            this.state = state;
            this.blockEntityData = blockEntityData != null && !blockEntityData.isEmpty() ? blockEntityData.copy() : null;
        }
    }

    private static final class PreviewLayerAccumulator {
        private final int y;
        private int minX = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxZ = Integer.MIN_VALUE;
        private final Map<BlockPos, PreviewBlock> blocks = new LinkedHashMap<>();

        private PreviewLayerAccumulator(int y) {
            this.y = y;
        }

        private void add(BlockPos pos, PreviewBlock block) {
            blocks.put(pos, block);

            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }

        private StructureInfo.StructureLayer build() {
            int width = maxX - minX + 1;
            int depth = maxZ - minZ + 1;
            StructureInfo.StructureLayer layer = new StructureInfo.StructureLayer(y, width, depth, minX, minZ);

            for (Map.Entry<BlockPos, PreviewBlock> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                PreviewBlock block = entry.getValue();
                layer.setBlockState(pos.getX() - minX, pos.getZ() - minZ, block.state, block.blockEntityData);
            }

            return layer;
        }
    }
}