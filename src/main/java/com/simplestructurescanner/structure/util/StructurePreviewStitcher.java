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

    private final Map<Integer, PreviewLayerAccumulator> layersByY = new TreeMap<>();

    public void setBlock(BlockPos pos, @Nullable IBlockState state) {
        setBlock(pos, state, null);
    }

    public void setBlock(BlockPos pos, @Nullable IBlockState state, @Nullable NBTTagCompound blockEntityData) {
        if (pos == null) return;

        setBlock(pos.getX(), pos.getY(), pos.getZ(), state, blockEntityData);
    }

    public void setBlock(int x, int y, int z, @Nullable IBlockState state) {
        setBlock(x, y, z, state, null);
    }

    public void setBlock(int x, int y, int z, @Nullable IBlockState state,
            @Nullable NBTTagCompound blockEntityData) {
        if (state == null) return;

        PreviewLayerAccumulator layer = layersByY.computeIfAbsent(y, PreviewLayerAccumulator::new);
        layer.add(x, z, new PreviewBlock(state, blockEntityData));
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
        if (layersByY.isEmpty()) return Collections.emptyList();

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
            this.blockEntityData = blockEntityData != null && !blockEntityData.isEmpty() ? blockEntityData : null;
        }
    }

    private static final class PreviewLayerAccumulator {
        private final int y;
        private int minX = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxZ = Integer.MIN_VALUE;
        private final Map<Long, PreviewBlock> blocks = new LinkedHashMap<>();

        private PreviewLayerAccumulator(int y) {
            this.y = y;
        }

        private void add(int x, int z, PreviewBlock block) {
            blocks.put(createColumnKey(x, z), block);

            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        private StructureInfo.StructureLayer build() {
            int width = maxX - minX + 1;
            int depth = maxZ - minZ + 1;
            StructureInfo.StructureLayer layer = new StructureInfo.StructureLayer(y, width, depth, minX, minZ);

            for (Map.Entry<Long, PreviewBlock> entry : blocks.entrySet()) {
                int x = unpackX(entry.getKey());
                int z = unpackZ(entry.getKey());
                PreviewBlock block = entry.getValue();
                layer.setBlockState(x - minX, z - minZ, block.state, block.blockEntityData);
            }

            return layer;
        }
    }

    private static long createColumnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }
}