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

    private PillarStructurePredictor() {
    }

    /**
     * Recreates the exact Random that Forge provides to IWorldGenerators.
     * This formula is from GameRegistry.generateWorld() in Forge 1.12.2.
     * Do not simplify the bit-shift expression here. Pillar inherits Forge's
     * precedence exactly, and changing it would change every predicted chunk.
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
            if (schema.structureName.equals(targetStructureName)) {
                BlockPos predictedPos = predictTargetStructure(
                        schema, random, world, chunkX, chunkZ, knownPositions, rarityMultiplier);

                if (predictedPos != null) return predictedPos;

                continue;
            }

            if (!countsTowardChunkQuota(schema, random, rarityMultiplier)) continue;

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
    private static BlockPos predictTargetStructure(
            PillarSchemaProxy schema,
            Random random,
            World world,
            int chunkX,
            int chunkZ,
            Collection<BlockPos> knownPositions,
            float rarityMultiplier) {

        if (!passesRarityCheck(schema, random, rarityMultiplier)) return null;

        BlockPos xzPos = pickChunkPosition(random, chunkX, chunkZ);
        StructureValidationWorld validationWorld = ValidationContextManager.getValidationWorld(world);
        BlockPos pos = schema.generatorType.getGenerationPosition(schema, random, validationWorld, xzPos);

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
     * @return whether the structure would count toward Pillar's per-chunk quota
     */
    private static boolean countsTowardChunkQuota(
            PillarSchemaProxy schema, Random random, float rarityMultiplier) {

        if (!passesRarityCheck(schema, random, rarityMultiplier)) return false;

        consumeChunkPositionRandom(random);
        schema.generatorType.exhaustRandom(schema, random);

        return true;
    }

    private static boolean passesRarityCheck(PillarSchemaProxy schema, Random random, float rarityMultiplier) {
        if (schema.generatorType == PillarGeneratorType.NONE) return false;

        int rarity = getRarity(schema, rarityMultiplier);
        if (rarity <= 0) return false;

        return random.nextInt(rarity) == 0;
    }

    private static int getRarity(PillarSchemaProxy schema, float rarityMultiplier) {
        return (int) (schema.rarity * rarityMultiplier);
    }

    private static BlockPos pickChunkPosition(Random random, int chunkX, int chunkZ) {
        int x = chunkX * 16 + random.nextInt(16);
        int z = chunkZ * 16 + random.nextInt(16);

        return new BlockPos(x, 0, z);
    }

    private static void consumeChunkPositionRandom(Random random) {
        random.nextInt(16);
        random.nextInt(16);
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
