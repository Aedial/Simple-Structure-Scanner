# Structure Provider Implementation Guide

This guide covers everything needed to implement a `StructureProvider` for integrating mod structures with Simple Structure Scanner.

> **Maintainer Note:** If you modify `StructureProvider.java`, `StructureInfo.java`, `StructureLocation.java`, or `DimensionInfo.java`, update this documentation accordingly.

## Table of Contents

1. [Overview](#overview)
2. [Basic Implementation](#basic-implementation)
3. [Provider Registration](#provider-registration)
4. [Structure Information](#structure-information)
   - [Dimensions](#dimensions)
   - [Biomes](#biomes)
   - [Rarity](#rarity)
   - [Blocks and Layers](#blocks-and-layers)
   - [Loot Tables](#loot-tables)
   - [Entities](#entities)
5. [Search Implementation](#search-implementation)
   - [Individual Search](#individual-search)
   - [Batch Search](#batch-search)
   - [Searchability (Deterministic vs Non-Deterministic)](#searchability-deterministic-vs-non-deterministic)
   - [Y-Agnostic Locations](#y-agnostic-locations)
6. [Mod Presence Check](#mod-presence-check)
7. [Complete Example](#complete-example)

---

## Overview

A `StructureProvider` is responsible for:
- Reporting which structures a mod provides
- Providing metadata about structures (dimensions, biomes, rarity, blocks, entities, loot)
- Locating structures in the world via search algorithms

Each provider is registered with the `StructureProviderRegistry` and is queried when players search for structures.

---

## Basic Implementation

Create a class that implements `StructureProvider`:

```java
package com.yourmod.structure;

import java.util.function.Predicate;

import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.structure.providers.AbstractStructureProvider;


public class YourModStructureProvider extends AbstractStructureProvider {

    private static final String PROVIDER_ID = "yourmod";
    private static final String MOD_ID = "yourmod";

    public YourModStructureProvider() {
        // Constructor logic here (avoid heavy initialization)
    }

    @Override
    public void postInit() {
        // Initialize structure data here (NOT in constructor)
        // This runs after the provider has been registered and the mod is confirmed to be loaded, but before structure data is queried
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        // Return true if this structure can be searched for
    }

    @Override
    public StructureLocation findNearest(World world, ResourceLocation structureId, 
            BlockPos pos, int skipCount, Predicate<BlockPos> locationFilter) {
        // Implement search logic (this **should not** load chunks or perform worldgen, as these operations are expensive and can cause unintended side effects)
    }
}
```

---

## Provider Registration

Add your provider class to `StructureProviderRegistry`:

```java
// In StructureProviderRegistry.java
private static List<Class<? extends StructureProvider>> providerClasses = Arrays.asList(
    VanillaStructureProvider.class,
    ...,
    YourModStructureProvider.class  // Add your provider here
);
```

The registry will:
1. Instantiate your provider
2. Call `isAvailable()` to check if the mod is loaded
3. Call `postInit()` to allow structure setup
4. Index all structure IDs returned by `getStructureIds()`

The registry also loads config-driven external providers from `config/simplestructurescanner/external-providers/`.
These providers split data in two parts:
- JSON metadata (translation keys, dimensions, biomes, rarity, mod requirements)
- NBT structure data (`nbtPath`) for blocks, layers, entities, and loot tables

This NBT parsing path uses the shared `StructureNBTParser`.

Per-provider hidden blacklist files live in `config/simplestructurescanner/hidden-blacklists/`.
Per-provider search blacklist files live in `config/simplestructurescanner/search-blacklists/`.
Each file is named after the provider ID, for example `minecraft.txt` or `pillar.txt`.
Supported entries are identical in both directories:

```text
# Hide a structure everywhere for that provider
structure minecraft:village

# Hide all structures from that provider in one dimension
dimension -1

# Hide one structure only in one dimension
dimension minecraft:end_city 1
```

Entries in `hidden-blacklists` remove matching structures from the list.
Entries in `search-blacklists` keep matching structures visible but make them non-searchable.
Of course, both can be used together to achieve all three states: hidden, visible but non-searchable, and fully searchable.

These blacklist files are consumed client-side. The `sssblacklist <hidden|search> remove` client command removes existing entries without requiring manual file edits.

---

## Structure Information

### Creating StructureInfo Objects

Do note that any `from*` method is superseded by overrides in config/simplestructurescanner/structures/<provider>/<structure>.nbt, as they are a means for users to customize the structure data without modifying the code directly.
Only **ONE** `from*` method should be used per structure registration. Any subsequent calls will make the whole provider registration invalid.

The order is as follows: config override -> [Map gen if provided] -> [bundled NBT file if requested].

```java
import net.minecraft.world.biome.Biome;

import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.generation.MapGenerationBuilder;

private void addStructure(Set<Biome> biomes, Set<DimensionInfo> dimensions, LocalizedText rarityKey) {
    // tries to load the structure from the bundled NBT file (will still work if the file is missing)
    register(<structureName>, true)  // true indicates that the structure is searchable
        .fromBundled()
        .withMetadata(biomes, dimensions, rarityKey);

    // if you have a long rarity key, you can use withRarity separately
    register(<structureName2>)
        .fromBundled()
        .withBlocks(...)  // adds the specific blocks for this structure if not overridden
        .withMetadata(biomes, dimensions)
        .withRarity(rarityKey);

    // if you have a structure that needs to be generated programmatically rather than loaded from a bundled NBT file
    // the bundled NBT file is used as a fallback if available and the map building crashes
    MapGenerationBuilder map = new MapGenerationBuilder(<sizeX>, <sizeZ>, <yLevel>, <groundBlock>)
            .withName(<structureName3>)
            .build(<generateFunction>, <biome>);
    register(<structureName3>)
        .fromMapWithBundledFallback(map)  // or .fromMap(map)
        .withEntitiesForMap(entity1, entity2)  // only adds if the map doesn't crash
        .withMetadata(biomes, dimensions, rarityKey);

    // if you have a structure that only exists in code
    // the supplier function should provide the structure layers's list
    register(<structureName4>)
        .fromLayersSupplier(layersSupplierFunction)
        .withMetadata(biomes, dimensions, rarityKey);
}
```

The dimensions/blocks/loot tables/entities are extracted from the bundled NBT file automatically, the translation key is derived from name, and additional data (blocks, loot tables, entities) can be added with the corresponding `with*` methods.
`biomes`, `dimensions`, and `rarityKey` can be set to `null` if you want to leave them unspecified (no restrictions provided).

You will need to provide the translation key for localization purposes :
- `gui.structurescanner.provider.<providerId>`
- `gui.structurescanner.structures.<providerId>.<structureName>`

---

#### Map-Generated Structures

When a structure is generated programmatically rather than loaded from a bundled NBT file, you use the MapGenerationBuilder to define its layers. This usually goes like that :
```java
import com.simplestructurescanner.structure.generation.MapGenerationBuilder;

// create a 128x128 platform at y-level 63 with GRASS as the ground block
MapGenerationBuilder map = new MapGenerationBuilder(128, 128, 63, GRASS)
    // name for logging purposes, only used for the Map itself
    .withName("mymod:example_map")
    // origin point for the structure (optional): re-center the map if it starts off-center
    .withOrigin(64, 64)
    // add 20 layers of water above the platform
    .withAboveLayers(20, WATER)
    // add 10 layers of stone above the water
    .withAboveLayers(10, STONE)
    // add 5 layers of stone below the platform
    .withBelowLayers(5, STONE)
    // exclude any lava the generation might place
    .withoutBlocks(LAVA)
    // you can also use .withoutBlockStates(...) to exclude specific block-state variants
    // build the structure with the specified generation function and biome
    .build(generateStructureFunction, Biomes.PLAINS);
```

**NOTE:** You do not need to remove the blocks from the aboveLayers or belowLayers manually; the builder handles them for you. `withoutBlocks`/`withoutBlockStates` is only necessary for blocks placed by the generation function that you want to clear. By default, the platform is shown in the captured structure, use `withoutPlatform()` to omit it.

If you have corridors and tunnels that are carved into the terrain without any floor being generated, `withLowestNonOpaqueFloor()` ensures that a floor composed of the provided layer blocks (`withBelowLayers(...)`) is placed below each vertical run of air or non-opaque generated blocks. This toggle can be pretty performance-intensive, so use it judiciously.

To help with underground structures, you are offered `MapGenerationBuilder.ofBuried()` and `MapGenerationBuilder.ofBuriedWithFloor()`. They are shorthand constructors for a 64-layers tall underground map, with platform disabled. Floating structures can be handled similarly using `MapGenerationBuilder.ofFloatingIsland()`, which is the same concept but without any layers or platform.

The `build()` method finalizes the map generation process. No further modifications to the map should be made after calling this method, and any attempts to do so will explicitly error.

If generateStructureFunction crashes, the structure generation will fail gracefully (with error log), and the capture will be discarded. The `fromMapWithBundledFallback` method can be used to provide a fallback to a bundled NBT structure if it happens.

#### Generation function (`generateStructureFunction`)

The `generateStructureFunction` is the function responsible for generating the structure in the world. It is passed to the `build()` method of the `MapGenerationBuilder` and should handle the placement of blocks according to the structure's design. This usually involves reflectively initializing the structure's generator class and invoking its generation methods at the appropriate positions.

To help with this, you are provided with several helper methods and classes that facilitate reflective access and handling :
```java
MapGenerationBuilder map = new MapGenerationBuilder(128, 128, 63, GRASS)
    .withName("mymod:example_map")
    .withWorldGenerator("com.mymod.structure.ExampleStructureGenerator")
    // from this point onward, you can only use withPreGeneration(), withPostGeneration(), and build()
    .withPreGeneration((generator, world, random) -> {
        // Custom pre-generation logic here
    })
    .withPostGeneration((generator, world, random) -> {
        // Custom post-generation logic here
    })
    .build(Biomes.PLAINS);
    // As usual, only capture() is allowed after build()
```

There are 3 types of structure generators:
1. **StructureStart**: A structure with a starting point and multiple components that make up the entire structure. It is typically used for complex structures that are composed of several interconnected pieces. Most common in Mineshaft or Stronghold-like structures. The generator entry point is `.withStructureStart()`. A validator can be passed, that can accept or reject starting points before generation. Starting points will be created repeatedly until a valid one is found.
2. **WorldGenerator called once**: A structure that is generated by invoking a `WorldGenerator` a single time. This is typically used for monolithic structures that do not fit neatly into interconnected components. The generator entry point is `.withWorldGenerator()`.
3. **WorldGenerator called multiple times**: A structure that is generated by invoking a `WorldGenerator` multiple times, with varying positions. This is typically used for smaller structures that are spread over a large area, to avoid heavy cascading worldgen. The generator entry point is `.withChunkPopulation()`. A position provider should be supplied to determine the positions for each generation attempt, as `chunk pos -> block pos on the platform`. If the structure cannot be placed at the given chunk, the provider must return `null`.

The withPreGeneration() and withPostGeneration() methods allow you to define custom logic that should be executed before and after the main structure generation, respectively. These can be used for tasks such as preparing the terrain, adding additional features, or performing cleanup operations. They can be called multiple times and will be executed in the order they were added.

#### Biomes

Specify which biomes a structure can generate in:

```java
import net.minecraft.world.biome.Biome;
import net.minecraft.init.Biomes;
import net.minecraftforge.common.BiomeDictionary;

// Single biome
Set<Biome> desertOnly = biomes(Biomes.DESERT);

// Multiple biomes
Set<Biome> plainsLike = biomes(Biomes.PLAINS, Biomes.SAVANNA, Biomes.MUTATED_PLAINS);

// Using biome types: cold AND snowy
Set<Biome> iceBiomes = hasAllBiomes(BiomeDictionary.Type.COLD, BiomeDictionary.Type.SNOWY);

// Exclude certain biome types from the selection
Set<Biome> iceBiomesWithoutBeach = hasBiomesBut(iceBiomes, BiomeDictionary.Type.BEACH);

// Any of the specified biome types
Set<Biome> coldOrMountainBiomes = hasAnyBiomes(BiomeDictionary.Type.COLD, BiomeDictionary.Type.MOUNTAIN);
```

For modded biomes, fetch them at runtime in `postInit()`:

```java
try {
    Class<?> modBiomesClass = Class.forName("com.somemod.init.ModBiomes");
    Biome customBiome = (Biome) modBiomesClass.getField("CUSTOM_BIOME").get(null);
} catch (Exception e) {
    // Handle gracefully
}
```

If no biomes are set (`null`), the structure has no biome restrictions.

#### Dimensions

Specify which dimensions a structure can generate in:

```java
import com.simplestructurescanner.structure.DimensionInfo;

// Single dimension
Set<DimensionInfo> customDimensions = Collections.singleton(new DimensionInfo(42));

// Multiple dimensions
Set<DimensionInfo> commonDimensions = Stream.of(
    DimensionInfo.OVERWORLD,
    DimensionInfo.NETHER,
    DimensionInfo.END
).collect(Collectors.toSet());
```

For modded dimensions, fetch them at runtime in `postInit()`:

```java
try {
    Class<?> modDimensionsClass = Class.forName("com.somemod.init.ModDimensions");
    Integer customDimensionId = (Integer) modDimensionsClass.getField("CUSTOM_DIMENSION").get(null);
    DimensionInfo customDimension = new DimensionInfo(customDimensionId);
} catch (Exception e) {
    // Handle gracefully
}
```

If no dimensions are set (`null`), the structure has no dimension restrictions.

#### Rarity

For rarity, you either use a translation key directly (e.g., LocalizedText.translatable("gui.structurescanner.rarity.common")) or use RarityTextHelper methods to generate the appropriate translation keys.

### External Providers (JSON Metadata + NBT Data)

External provider JSON files define lightweight metadata and reference NBT files for structure contents.
All user-visible text in this format should be translation keys.
That requirement is especially important for user-made structures: they must provide user-authored translation keys.
Generated default keys should come from the shared `StructureTranslationKeys` helpers so every provider follows the same naming scheme.
Each JSON file defines one provider object with a `providerId`, a `modNameKey`, and a `structures` array.

```json
{
    "providerId": "examplepack",
    "modNameKey": "gui.structurescanner.provider.examplepack",
    "requiredMods": ["minecraft"],  // Gates provider loading on mod presence
    "nbtRoot": "nbt",               // Optional root directory for NBT files, defaults to "nbt"
    "structures": [
        {
            "id": "examplepack:ruined_tower",
            // You will need to provide the localization for this key
            "displayNameKey": "gui.structurescanner.structures.examplepack.ruined_tower",
            "nbtPath": "examplepack/ruined_tower",  // Relative to nbtRoot, without .nbt extension
            "dimensions": [0, 7],                   // Valid dimensions
            "biomes": ["minecraft:plains", "minecraft:forest"], // Valid biomes (optional)
            "rarityKey": "gui.structurescanner.rarity.uncommon"
            // May also use a number (per-chunk rarity), using the "rarityChunks" field instead of "rarityKey"
        }
    ]
}
```

`nbtPath` is relative to `nbtRoot` (default `nbt`) under `config/simplestructurescanner/external-providers/`.

Provider and structure name examples live together in `docs/examples/en_us.lang`.

For the example above, the NBT file path is:
- `config/simplestructurescanner/external-providers/nbt/examplepack/ruined_tower.nbt`

If `nbtPath` is present, structure block/layer/entity/loot information is loaded from NBT.
If `nbtPath` is missing, only the JSON metadata is available.

If `modNameKey` or `displayNameKey` are omitted, the loader falls back to generated keys:
- `gui.structurescanner.provider.<providerId>`
- `gui.structurescanner.structures.<namespace>.<path>`

These fallbacks are still translation keys, not human-readable titles.
If a user-made provider relies on them, the author is expected to add matching localization entries.

External providers remain non-searchable unless a Java provider supplies custom location logic.

## Search Implementation

### Individual Search

The primary search method finds the nearest structure, optionally skipping some results:

```java
@Override
public StructureLocation findNearest(World world, ResourceLocation structureId, 
        BlockPos pos, int skipCount, Predicate<BlockPos> locationFilter) {
    
    List<BlockPos> candidates = findCandidatePositions(world, structureId, pos);
    
    // Sort by distance
    candidates.sort(Comparator.comparingDouble(p -> p.distanceSq(pos)));
    
    // Apply filter and skip
    int skipped = 0;
    for (int i = 0; i < candidates.size(); i++) {
        BlockPos candidate = candidates.get(i);
        
        // Skip filtered positions
        if (locationFilter != null && !locationFilter.test(candidate)) continue;
        
        if (skipped < skipCount) {
            skipped++;
            continue;
        }
        
        // Found the result
        return new StructureLocation(candidate, i, candidates.size());
    }
    
    return null;  // Not found
}
```

**Parameters:**
- `world`: The world to search in
- `structureId`: Which structure to find
- `pos`: Search origin (usually player position)
- `skipCount`: Number of valid results to skip (for "next" functionality)
- `locationFilter`: Optional predicate to exclude positions (e.g., blacklisted locations)

**Return Value:**
- `StructureLocation` containing position, index, and total found
- `null` if no structure was found

**Note:** As search should be determinstic, it is advised to cache results if possible to improve performance on repeated calls (like skipping). Providing batch search support is a good alternative (see next section).

---

### Batch Search

For providers that can efficiently return multiple positions at once, implement `findAllNearby`:

```java
@Override
public List<BlockPos> findAllNearby(World world, ResourceLocation structureId, 
        BlockPos pos, int maxResults) {
    
    List<BlockPos> results = new ArrayList<>();
    
    // Your batch search algorithm here
    // ...
    
    return results;  // Return unsorted; caller will sort by distance
}
```

**Return Values:**
- `List<BlockPos>` - List of found positions (unsorted)
- Empty list - No structures found
- `null` - Batch search not supported (default), use `findNearest` instead

The caller will sort results by distance, so you don't need to sort them.

---

### Searchability (Deterministic vs Non-Deterministic)

Some structures can be located deterministically (calculated from world seed), while others cannot:

```java
@Override
public boolean canBeSearched(ResourceLocation structureId) {
    String path = structureId.getResourcePath();
    
    // Deterministic structures - position can be calculated from seed
    if (path.equals("village") || path.equals("monument")) return true;
    
    // Non-deterministic structures - require world scanning
    // *In most cases, it would not be feasible to implement scanning logic*
    // Searching should never load chunks or perform worldgen, to avoid performance issues
    if (path.equals("random_structure")) return false;
    
    return true;
}
```

**Guidelines:**
- Return `true` if the structure position can be reliably calculated
- Return `false` if the structure cannot be located (random generation, no pattern)
- Structures with `canBeSearched() = false` won't be selectable for tracking

---

### Y-Agnostic Locations

Some structures have unknown or irrelevant Y coordinates (e.g., structures that generate underground):

```java
// For structures where Y is unknown
BlockPos pos = new BlockPos(chunkX * 16, 0, chunkZ * 16);  // Y = 0 as placeholder
return new StructureLocation(pos, index, total, true);  // yAgnostic = true
```

When `yAgnostic = true`:
- Distance calculations use horizontal distance only
- The scanner will not attempt to find the actual Y at that location

If your structure has a known Y coordinate, the provider itself should determine it during search and return the actual Y value, without setting `yAgnostic`.

---

## Mod Presence Check

The presence check is usually done automatically by AbstractStructureProvider, using the provided `MOD_ID`, but you might need a custom implementation in `isAvailable()` for more complex conditions.

```java
@Override
public boolean isAvailable() {
    // multi-mod check
    return super.isAvailable() && Loader.isModLoaded(ANOTHER_MOD_ID);
}
```

**Important:** Never reference mod classes directly in your provider class (fields, imports with direct usage). Use reflection in `postInit()`:

```java
@Override
public void postInit() {
    // Safe to access mod classes via reflection here
    try {
        Class<?> modClass = Class.forName("com.othermod.SomeClass");
        Object value = modClass.getField("SOME_FIELD").get(null);
    } catch (Exception e) {
        SimpleStructureScanner.LOGGER.warn("Failed to access mod data", e);
    }
}
```

This prevents `ClassNotFoundException` when the mod is not installed, or when it is updated and the internal structure of its classes has changed. `postInit()` is only called if `isAvailable()` returns `true`, which means it is safe to access mod classes there.

---

## Complete Example

Here's a complete minimal implementation:

```java
package com.example.structure;

import java.util.*;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import net.minecraft.init.Biomes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.structure.*;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.providers.AbstractStructureProvider;


public class ExampleStructureProvider extends AbstractStructureProvider {

    private static final String PROVIDER_ID = "examplemod";
    private static final String MOD_NAME = "gui.structurescanner.provider.examplemod";
    private static final String MOD_ID = "xmplmod";
    
    public ExampleStructureProvider() {
        super(PROVIDER_ID, PROVIDER_ID, MOD_NAME, MOD_ID);
    }

    @Override
    public void postInit() {  // only called if isAvailable() returns true
        // Register structures
        register("tower")
            .fromBundled()
            .withEntities(
                new EntityEntry(MOD_ID + ":tower_guard", 2),        // static spawn
                new EntityEntry("minecraft:skeleton", 1, true))     // spawner
            .withMetadata(biomes(Biomes.PLAINS, Biomes.FOREST), Collections.singleton(DimensionInfo.OVERWORLD))
            .withRarity(RarityTextHelper.oneInChunks(200));
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        // This structure uses deterministic generation
        // You do not need to check mod from ResourceLocation, the caller ensures this (considering you're not referencing multiple mods, which is discouraged)
        return structureId.getResourcePath().equals("tower");
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, 
            BlockPos pos, int skipCount, @Nullable Predicate<BlockPos> locationFilter) {
        
        // Your search algorithm here
        // This example uses a simple spiral search pattern
        
        List<BlockPos> found = new ArrayList<>();
        
        // ... search logic ...
        
        // Sort by distance
        found.sort(Comparator.comparingDouble(p -> p.distanceSq(pos)));
        
        // Apply filter and skip count
        int validIndex = 0;
        for (BlockPos candidate : found) {
            if (locationFilter != null && !locationFilter.test(candidate)) continue;
            
            if (validIndex >= skipCount) {
                return new StructureLocation(candidate, validIndex, found.size());
            }
            validIndex++;
        }
        
        return null;
    }
    
    @Override
    @Nullable
    public List<BlockPos> findAllNearby(World world, ResourceLocation structureId, 
            BlockPos pos, int maxResults) {
        // Optional: implement batch search for better performance
        return null;  // null = use findNearest instead
    }
}
```

---

## Summary Checklist

- [ ] Implement `StructureProvider` interface
- [ ] Initialize structures in `postInit()` (not constructor), using the register() chain
- [ ] Implement `canBeSearched()` based on structure generation type
- [ ] Implement `findNearest()` with filter and skip support
- [ ] Optionally implement `findAllNearby()` for batch search
- [ ] Register provider in `StructureProviderRegistry.providerClasses`
- [ ] Add localization strings for everything user-facing, if not already present
