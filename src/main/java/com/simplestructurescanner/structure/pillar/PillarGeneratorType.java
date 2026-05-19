package com.simplestructurescanner.structure.pillar;

import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;

/**
 * Proxy for Pillar's GeneratorType enum.
 * <p>
 * This mirrors Pillar's generator-specific position logic and the Random
 * exhaustion needed when a non-target structure advances the chunk quota.
 */
public enum PillarGeneratorType {
    SURFACE(PillarGeneratorType::getSurfacePosition, PillarGeneratorType::consumeNoRandom),
    UNDERGROUND(PillarGeneratorType::getUndergroundPosition,
            (schema, random) -> random.nextInt(getYRange(schema, 0, 60))),
    UNDERWATER(PillarGeneratorType::getUnderwaterPosition, PillarGeneratorType::consumeNoRandom),
    ABOVE_WATER(PillarGeneratorType::getAboveWaterPosition, PillarGeneratorType::consumeNoRandom),
    SKY(PillarGeneratorType::getSkyPosition,
            (schema, random) -> random.nextInt(getYRange(schema, 128, 256))),
    ANYWHERE(PillarGeneratorType::getAnywherePosition,
            (schema, random) -> random.nextInt(getYRange(schema, 1, 256))),
    NONE(PillarGeneratorType::disallow, PillarGeneratorType::consumeNoRandom);

    private final PositionProvider positionProvider;
    private final RandomConsumer randomConsumer;

    PillarGeneratorType(PositionProvider positionProvider, RandomConsumer randomConsumer) {
        this.positionProvider = positionProvider;
        this.randomConsumer = randomConsumer;
    }

    @Nullable
    public BlockPos getGenerationPosition(
            PillarSchemaProxy schema,
            Random random,
            StructureValidationWorld world,
            BlockPos xzPos) {
        return positionProvider.getGenerationPosition(schema, random, world, xzPos);
    }

    public void exhaustRandom(PillarSchemaProxy schema, Random random) {
        randomConsumer.consume(schema, random);
    }

    @Nullable
    private static BlockPos getSurfacePosition(
            PillarSchemaProxy schema,
            Random random,
            StructureValidationWorld world,
            BlockPos xzPos) {
        BlockPos pos = world.getTopSolidOrLiquidBlock(xzPos);
        IBlockState state = world.getBlockState(pos);

        if (pos.getX() == 0 || state.getBlock() instanceof BlockLiquid) return null;
        if (!isInYBounds(schema, pos.getY())) return null;

        return pos;
    }

    @Nullable
    private static BlockPos getUndergroundPosition(
            PillarSchemaProxy schema,
            Random random,
            StructureValidationWorld world,
            BlockPos xzPos) {
        int y = getYFromBounds(schema, random, 0, 60);
        BlockPos pos = new BlockPos(xzPos.getX(), y, xzPos.getZ());

        if (world.canBlockSeeSky(pos)) return null;

        return pos;
    }

    @Nullable
    private static BlockPos getUnderwaterPosition(
            PillarSchemaProxy schema,
            Random random,
            StructureValidationWorld world,
            BlockPos xzPos) {
        BlockPos pos = world.getTopSolidBlock(xzPos);
        IBlockState stateAbove = world.getBlockState(pos.up());

        if (pos.getX() == 0 || !(stateAbove.getBlock() instanceof BlockLiquid)) return null;

        return pos;
    }

    @Nullable
    private static BlockPos getAboveWaterPosition(
            PillarSchemaProxy schema,
            Random random,
            StructureValidationWorld world,
            BlockPos xzPos) {
        BlockPos pos = world.getTopLiquidBlock(xzPos);

        if (pos.getX() == 0 || !isInYBounds(schema, pos.getY())) return null;

        return pos;
    }

    @Nullable
    private static BlockPos getSkyPosition(
            PillarSchemaProxy schema,
            Random random,
            StructureValidationWorld world,
            BlockPos xzPos) {
        int y = getYFromBounds(schema, random, 128, 256);
        BlockPos pos = new BlockPos(xzPos.getX(), y, xzPos.getZ());
        IBlockState state = world.getBlockState(pos);

        if (!world.canBlockSeeSky(pos) || !state.getBlock().isAir(state, world, pos)) return null;

        return pos;
    }

    @Nullable
    private static BlockPos getAnywherePosition(
            PillarSchemaProxy schema,
            Random random,
            StructureValidationWorld world,
            BlockPos xzPos) {
        int y = getYFromBounds(schema, random, 1, 256);

        return new BlockPos(xzPos.getX(), y, xzPos.getZ());
    }

    @Nullable
    private static BlockPos disallow(
            PillarSchemaProxy schema,
            Random random,
            StructureValidationWorld world,
            BlockPos xzPos) {
        return null;
    }

    private static void consumeNoRandom(PillarSchemaProxy schema, Random random) {
    }

    private static boolean isInYBounds(PillarSchemaProxy schema, int y) {
        if (schema.maxY > -1 && y > schema.maxY) return false;

        return schema.minY <= -1 || y >= schema.minY;
    }

    private static int getYFromBounds(PillarSchemaProxy schema, Random rand, int defaultMin, int defaultMax) {
        int maxY = schema.maxY;
        int minY = schema.minY;

        if (maxY < 0) maxY = defaultMax;
        if (minY < 0) minY = defaultMin;

        if (minY > maxY) {
            int temp = maxY;
            maxY = minY;
            minY = temp;
        }

        int diff = maxY - minY;

        return rand.nextInt(diff) + minY;
    }

    private static int getYRange(PillarSchemaProxy schema, int defaultMin, int defaultMax) {
        int maxY = schema.maxY;
        int minY = schema.minY;

        if (maxY < 0) maxY = defaultMax;
        if (minY < 0) minY = defaultMin;

        if (minY > maxY) {
            int temp = maxY;
            maxY = minY;
            minY = temp;
        }

        return Math.max(maxY - minY, 1);
    }

    private static interface PositionProvider {

        @Nullable
        BlockPos getGenerationPosition(
                PillarSchemaProxy schema,
                Random random,
                StructureValidationWorld world,
                BlockPos xzPos);
    }

    private static interface RandomConsumer {

        void consume(PillarSchemaProxy schema, Random random);
    }
}
