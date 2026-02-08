package com.simplestructurescanner.structure.pillar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;


/**
 * Predicts Pillar structure locations by replicating Pillar's generation algorithm.
 * <p>
 * This class simulates Pillar's WorldGenerator without requiring actual chunk
 * generation. It uses Forge's chunk random seeding formula to predict where
 * structures will spawn, and a validation world for terrain checks.
 */
public class PillarStructurePredictor {

    /**
     * Recreates the exact Random that Forge provides to IWorldGenerators.
     * This formula is from GameRegistry.generateWorld() in Forge 1.12.2.
     *
     * @param worldSeed The world's seed (world.getSeed())
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @return A Random in the exact state Forge would provide
     */
    public static Random getChunkRandom(long worldSeed, int chunkX, int chunkZ) {
        Random random = new Random(worldSeed);
        long xSeed = random.nextLong() >> 2 + 1L;
        long zSeed = random.nextLong() >> 2 + 1L;
        long chunkSeed = (xSeed * chunkX + zSeed * chunkZ) ^ worldSeed;
        random.setSeed(chunkSeed);

        return random;
    }

    /**
     * Predict whether a specific structure would spawn in the given chunk.
     * <p>
     * Replicates Pillar's {@code WorldGenerator.generate()} loop: shuffle schemas,
     * iterate, and call generateStructure for each. Only performs expensive terrain
     * verification for the target structure.
     *
     * @param world The world (used for seed and dimension info, never modified)
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param targetStructureName The structure name to look for
     * @param knownPositions Previously found positions of this structure type
     * @param schemasInOrder Schemas in Pillar's captured iteration order
     * @param rarityMultiplier Pillar's global rarity multiplier config
     * @param maxStructures Pillar's maxStructuresInOneChunk config
     * @return Predicted BlockPos, or null if the target would not spawn here
     */
    @Nullable
    public static BlockPos predictStructureInChunk(
            World world,
            int chunkX,
            int chunkZ,
            String targetStructureName,
            Collection<BlockPos> knownPositions,
            List<PillarSchemaProxy> schemasInOrder,
            float rarityMultiplier,
            int maxStructures) {

        if (schemasInOrder == null || schemasInOrder.isEmpty()) return null;

        Random random = getChunkRandom(world.getSeed(), chunkX, chunkZ);

        List<PillarSchemaProxy> shuffledSchemas = new ArrayList<>(schemasInOrder);
        Collections.shuffle(shuffledSchemas, random);

        int structuresGenerated = 0;

        for (PillarSchemaProxy schema : shuffledSchemas) {
            boolean isTarget = schema.structureName.equals(targetStructureName);

            BlockPos result;
            if (isTarget) {
                result = predictSingleStructure(
                        schema, random, world, chunkX, chunkZ, knownPositions, rarityMultiplier);
            } else {
                result = predictNonTargetStructure(schema, random, rarityMultiplier);
            }

            if (result == null) continue;

            if (isTarget) return result;

            structuresGenerated++;
            if (structuresGenerated >= maxStructures) return null;
        }

        return null;
    }

    /**
     * Full prediction for the target structure, including terrain verification.
     * Replicates Pillar's {@code WorldGenerator.generateStructure()}.
     *
     * @param schema The structure schema
     * @param random The Forge-seeded Random (will be advanced)
     * @param world The world
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param knownPositions Previously found positions (for minDistance check)
     * @param rarityMultiplier Pillar's global rarity multiplier
     * @return Predicted BlockPos with Y from terrain, or null
     */
    @Nullable
    private static BlockPos predictSingleStructure(
            PillarSchemaProxy schema,
            Random random,
            World world,
            int chunkX,
            int chunkZ,
            Collection<BlockPos> knownPositions,
            float rarityMultiplier) {

        if (schema.generatorType == PillarGeneratorType.NONE) return null;

        // Rarity check — matches Pillar's:
        //   int rarity = (int) (schema.rarity * Pillar.rarityMultiplier);
        //   if (rarity > 0 && random.nextInt(rarity) == 0) { ... }
        int rarity = (int) (schema.rarity * rarityMultiplier);
        if (rarity <= 0) return null;
        if (random.nextInt(rarity) != 0) return null;

        // X/Z within chunk
        int x = chunkX * 16 + random.nextInt(16);
        int z = chunkZ * 16 + random.nextInt(16);

        // Y-coordinate via validation world (never touches the real world)
        BlockPos xzPos = new BlockPos(x, 0, z);
        StructureValidationWorld validationWorld = ValidationContextManager.getValidationWorld(world);
        BlockPos pos = getYCoordinateForGeneratorType(schema, random, validationWorld, xzPos);

        if (pos == null) return null;

        if (!canSpawnInPosition(schema, world, pos, knownPositions)) return null;

        return pos;
    }

    /**
     * Lightweight prediction for non-target structures.
     * Only checks rarity and consumes Random calls to keep the state synchronized
     * with Pillar's actual generation. Skips expensive terrain and spawn checks.
     *
     * @param schema The structure schema
     * @param random The Forge-seeded Random (will be advanced)
     * @param rarityMultiplier Pillar's global rarity multiplier
     * @return Placeholder BlockPos if structure would pass rarity, or null
     */
    @Nullable
    private static BlockPos predictNonTargetStructure(
            PillarSchemaProxy schema,
            Random random,
            float rarityMultiplier) {

        if (schema.generatorType == PillarGeneratorType.NONE) return null;

        int rarity = (int) (schema.rarity * rarityMultiplier);
        if (rarity <= 0) return null;
        if (random.nextInt(rarity) != 0) return null;

        // Consume X/Z random calls
        random.nextInt(16);
        random.nextInt(16);

        // Consume Y random calls
        consumeGeneratorTypeRandom(schema, random);

        // This structure would spawn (return placeholder - Y=0 since we didn't verify)
        // We return a non-null result to indicate it counts toward the quota
        return new BlockPos(0, 0, 0);
    }

    // ========== GeneratorType Y-coordinate logic ==========

    /**
     * Determines the Y-coordinate using Pillar's GeneratorType logic.
     * Uses the validation world for terrain queries (never the real world).
     */
    @Nullable
    private static BlockPos getYCoordinateForGeneratorType(
            PillarSchemaProxy schema,
            Random random,
            StructureValidationWorld validationWorld,
            BlockPos xzPos) {

        switch (schema.generatorType) {
            case SURFACE:
                return getSurfacePosition(schema, validationWorld, xzPos);
            case UNDERGROUND:
                // UNDERGROUND: Random Y between 0-60, must not see sky
                return getUndergroundPosition(schema, random, validationWorld, xzPos);

            case UNDERWATER:
                // UNDERWATER: Uses getTopSolidBlock(), must have liquid above
                return getUnderwaterPosition(schema, validationWorld, xzPos);

            case ABOVE_WATER:
                // ABOVE_WATER: Uses getTopLiquidBlock(), Y in bounds
                return getAboveWaterPosition(schema, validationWorld, xzPos);

            case SKY:
                // SKY: Random Y between 128-256, must see sky and be air
                return getSkyPosition(schema, random, validationWorld, xzPos);

            case ANYWHERE:
                // ANYWHERE: Random Y between 1-256, no terrain checks
                return getAnywherePosition(schema, random, xzPos);

            case NONE:
            default:
                return null;
        }
    }

    @Nullable
    private static BlockPos getSurfacePosition(
            PillarSchemaProxy schema, StructureValidationWorld world, BlockPos xzPos) {

        BlockPos pos = world.getTopSolidOrLiquidBlock(xzPos);
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);

        if (pos.getX() == 0 || state.getBlock() instanceof net.minecraft.block.BlockLiquid) return null;
        if (!isInYBounds(schema, pos.getY())) return null;

        return pos;
    }

    @Nullable
    private static BlockPos getUndergroundPosition(
            PillarSchemaProxy schema, Random random, StructureValidationWorld world, BlockPos xzPos) {

        int y = getYFromBounds(schema, random, 0, 60);
        BlockPos pos = new BlockPos(xzPos.getX(), y, xzPos.getZ());

        if (world.canBlockSeeSky(pos)) return null;

        return pos;
    }

    @Nullable
    private static BlockPos getUnderwaterPosition(
            PillarSchemaProxy schema, StructureValidationWorld world, BlockPos xzPos) {

        BlockPos pos = world.getTopSolidBlock(xzPos);
        net.minecraft.block.state.IBlockState stateAbove = world.getBlockState(pos.up());

        if (pos.getX() == 0 || !(stateAbove.getBlock() instanceof net.minecraft.block.BlockLiquid)) return null;

        return pos;
    }

    @Nullable
    private static BlockPos getAboveWaterPosition(
            PillarSchemaProxy schema, StructureValidationWorld world, BlockPos xzPos) {

        BlockPos pos = world.getTopLiquidBlock(xzPos);

        if (pos.getX() == 0 || !isInYBounds(schema, pos.getY())) return null;

        return pos;
    }

    @Nullable
    private static BlockPos getSkyPosition(
            PillarSchemaProxy schema, Random random, StructureValidationWorld world, BlockPos xzPos) {

        int y = getYFromBounds(schema, random, 128, 256);
        BlockPos pos = new BlockPos(xzPos.getX(), y, xzPos.getZ());
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);

        if (!world.canBlockSeeSky(pos) || !state.getBlock().isAir(state, world, pos)) return null;

        return pos;
    }

    @Nullable
    private static BlockPos getAnywherePosition(
            PillarSchemaProxy schema, Random random, BlockPos xzPos) {

        int y = getYFromBounds(schema, random, 1, 256);

        return new BlockPos(xzPos.getX(), y, xzPos.getZ());
    }

    // ========== Y-coordinate bounds helpers ==========

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

    /**
     * Consume the Random calls that a GeneratorType's getGenerationPosition()
     * would make, without performing terrain checks. Keeps Random state in sync
     * for non-target structures.
     */
    private static void consumeGeneratorTypeRandom(PillarSchemaProxy schema, Random random) {
        switch (schema.generatorType) {
            case SURFACE:
            case UNDERWATER:
            case ABOVE_WATER:
                // These types use world scanning, no random consumed
                break;
            case UNDERGROUND:
                random.nextInt(getYRange(schema, 0, 60));
                break;
            case SKY:
                random.nextInt(getYRange(schema, 128, 256));
                break;
            case ANYWHERE:
                random.nextInt(getYRange(schema, 1, 256));
                break;
            case NONE:
                break;
        }
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

    // ========== Spawn condition checks ==========

    /**
     * Check if a structure can spawn at the given position.
     * <p>
     * Replicates Pillar's {@code WorldGenerator.canSpawnInPosition()} exactly:
     * generateEverywhere → minDistance → dimension → biomeName → biomeTag.
     *
     * @param schema The structure schema
     * @param world The world (for dimension and biome info)
     * @param pos The position to check
     * @param knownPositions Previously found positions of this structure type
     * @return true if the structure could spawn here
     */
    private static boolean canSpawnInPosition(
            PillarSchemaProxy schema,
            World world,
            BlockPos pos,
            Collection<BlockPos> knownPositions) {

        if (schema.generateEverywhere) return true;

        // Minimum distance to same-type structures (checked before dimension/biome)
        if (schema.minDistanceToSameTypeStructures > 0) {
            int minDistSq = schema.minDistanceToSameTypeStructures * schema.minDistanceToSameTypeStructures;

            for (BlockPos knownPos : knownPositions) {
                if (pos.distanceSq(knownPos) <= minDistSq) return false;
            }
        }

        // Dimension whitelist/blacklist
        if (!schema.dimensionSpawns.isEmpty()) {
            int dim = world.provider.getDimension();

            if (schema.isDimensionSpawnsBlacklist && schema.dimensionSpawns.contains(dim)) return false;
            if (!schema.isDimensionSpawnsBlacklist && !schema.dimensionSpawns.contains(dim)) return false;
        }

        // Biome name check — Pillar's exact logic:
        //   if blacklist && name NOT in list → return true (allowed, skip tags)
        //   if name IS in list → return !blacklist (blacklist=denied, whitelist=allowed)
        //   otherwise (whitelist && name NOT in list) → fall through to tags
        Biome biome = world.getBiome(pos);
        String name = biome.getRegistryName().toString();

        if (schema.isBiomeNameSpawnsBlacklist && !schema.biomeNameSpawns.contains(name)) return true;
        if (schema.biomeNameSpawns.contains(name)) return !schema.isBiomeNameSpawnsBlacklist;

        // Biome tag check (BiomeDictionary-based)
        try {
            Set<BiomeDictionary.Type> types = BiomeDictionary.getTypes(biome);

            if (schema.isBiomeTagSpawnsBlacklist) {
                for (BiomeDictionary.Type type : types) {
                    if (schema.biomeTagSpawns.contains(type.getName())) return false;
                }

                return true;
            } else {
                for (BiomeDictionary.Type type : types) {
                    if (schema.biomeTagSpawns.contains(type.getName())) return true;
                }
            }
        } catch (NullPointerException e) {
            // Biome not properly registered in BiomeDictionary
        }

        return false;
    }
}
