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

import com.simplestructurescanner.structure.StructureProvider;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.util.List;
import java.util.function.Predicate;

public class YourModStructureProvider implements StructureProvider {

    private static final String PROVIDER_ID = "yourmod";
    private static final String MOD_ID = "yourmod";

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getModName() {
        // Return a localization key or direct string
        return "gui.structurescanner.provider.yourmod";
    }

    @Override
    public boolean isAvailable() {
        return Loader.isModLoaded(MOD_ID);
    }

    @Override
    public void postInit() {
        // Initialize structure data here (NOT in constructor)
        // This runs after provider registration when the mod is confirmed loaded
    }

    @Override
    public List<ResourceLocation> getStructureIds() {
        // Return list of all structure IDs this provider handles
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        // Return true if this structure can be searched for
    }

    @Override
    public StructureInfo getStructureInfo(ResourceLocation structureId) {
        // Return structure metadata or null if not found
    }

    @Override
    public StructureLocation findNearest(World world, ResourceLocation structureId, 
            BlockPos pos, int skipCount, Predicate<BlockPos> locationFilter) {
        // Implement search logic (this **should not** load chunks or perform worldgen)
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

---

## Structure Information

### Creating StructureInfo Objects

```java
private void addStructure(String path, String displayNameKey, int sizeX, int sizeY, int sizeZ) {
    ResourceLocation id = new ResourceLocation(MOD_ID, path);
    knownStructures.add(id);
    
    String name = I18n.translateToLocal(displayNameKey);
    StructureInfo info = new StructureInfo(id, name, PROVIDER_ID, sizeX, sizeY, sizeZ);
    structureInfos.put(id, info);
}
```

**Parameters:**
- `id`: Unique identifier (e.g., `yourmod:tower`)
- `displayName`: Localized display name for the GUI
- `modId`: Provider ID for grouping in the UI
- `sizeX/Y/Z`: Structure dimensions (use 0 if unknown/variable)

---

### Dimensions

Use `DimensionInfo` to specify which dimensions a structure can generate in:

```java
import com.simplestructurescanner.structure.DimensionInfo;

// Using built-in vanilla dimensions
Set<DimensionInfo> overworld = Collections.singleton(DimensionInfo.OVERWORLD);
Set<DimensionInfo> nether = Collections.singleton(DimensionInfo.NETHER);
Set<DimensionInfo> end = Collections.singleton(DimensionInfo.END);

// Custom dimensions with localization
int customDimId = 42; // Get from your mod's config/API
DimensionInfo customDim = new DimensionInfo(customDimId, "gui.structurescanner.dimension.yourdimensionid");

// Multiple dimensions
Set<DimensionInfo> multipleDims = new HashSet<>();
multipleDims.add(DimensionInfo.OVERWORLD);
multipleDims.add(customDim);

// Apply to structure info
info.setValidDimensions(multipleDims);
```

**DimensionInfo Constructors:**
- `DimensionInfo(int dimensionId)` - Uses ID as display name  - **not recommended**, will show as "Unknown (dimensionId)"
- `DimensionInfo(int dimensionId, String displayKey)` - Uses localization key

If no dimensions are set (`null` or empty), the structure is assumed valid in all dimensions.

---

### Biomes

Specify which biomes a structure can generate in:

```java
import net.minecraft.world.biome.Biome;
import net.minecraft.init.Biomes;

// Single biome
Set<Biome> desertOnly = Collections.singleton(Biomes.DESERT);

// Multiple biomes
Set<Biome> plainsLike = Stream.of(
    Biomes.PLAINS,
    Biomes.SAVANNA,
    Biomes.MUTATED_PLAINS
).collect(Collectors.toSet());

info.setValidBiomes(plainsLike);
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

---

### Rarity

Rarity can be specified as either a localization key or a literal string:

```java
// Using localization key
info.setRarity("gui.structurescanner.rarity.common");
info.setRarity("gui.structurescanner.rarity.uncommon");
info.setRarity("gui.structurescanner.rarity.rare");
info.setRarity("gui.structurescanner.rarity.unique");

// Using literal "1 in X" format
String rarityString = I18n.translateToLocalFormatted("gui.structurescanner.rarity.one_in_chunks", chunksPerOccurrence);
info.setRarity(I18n.translateToLocalFormatted("gui.structurescanner.rarity", rarityString));

// Custom localized rarity (you must add the lang string yourself)
info.setRarity("gui.yourmod.rarity.legendary");
```

Standard rarity keys provided by the mod:
- `gui.structurescanner.rarity.common`
- `gui.structurescanner.rarity.uncommon`
- `gui.structurescanner.rarity.rare`
- `gui.structurescanner.rarity.unique`

---

### Blocks and Layers

Blocks are listed for the structure viewer. Layers provide a visual 2D slice representation. Blocks can be extracted directly from the layer data, which is preferred.

#### Block Entries

```java
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;

List<BlockEntry> blocks = new ArrayList<>();

// Simple block entry
IBlockState stoneState = Blocks.STONE.getDefaultState();
ItemStack stoneStack = new ItemStack(Blocks.STONE);
blocks.add(new BlockEntry(stoneState, stoneStack, 150));  // 150 stone blocks

// Block with no item representation
blocks.add(new BlockEntry(stoneState, null, 50));

info.setBlocks(blocks);
```

**BlockEntry Parameters:**
- `blockState`: The `IBlockState` of the block
- `displayStack`: `ItemStack` for GUI display (can be null)
- `count`: Number of this block in the structure

#### Structure Layers

Layers enable the structure viewer to show a complete visualization:

```java
import com.simplestructurescanner.structure.StructureInfo.StructureLayer;

List<StructureLayer> layers = new ArrayList<>();

for (int y = 0; y < structureHeight; y++) {
    StructureLayer layer = new StructureLayer(y, sizeX, sizeZ);
    // Or with offsets: new StructureLayer(y, sizeX, sizeZ, xOffset, zOffset);
    
    for (int x = 0; x < sizeX; x++) {
        for (int z = 0; z < sizeZ; z++) {
            IBlockState state = getBlockAt(x, y, z);
            layer.setBlockState(x, z, state);
        }
    }
    
    layers.add(layer);
}

info.setLayers(layers);
```

**Tip:** Use `StructureNBTParser` to automatically extract blocks and layers from NBT structure files:

```java
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.StructureNBTParser.ParsedStructure;

ParsedStructure parsed = StructureNBTParser.parseStructure("igloo/igloo_top");
if (parsed != null) {
    info.setBlocks(parsed.blocks);
    info.setLayers(parsed.layers);
}
```

---

### Loot Tables

Document which loot tables can be found in a structure:

```java
import com.simplestructurescanner.structure.StructureInfo.LootEntry;

List<LootEntry> lootTables = new ArrayList<>();

// Create a loot entry with possible drops
ResourceLocation lootTableId = new ResourceLocation("minecraft", "chests/desert_pyramid");
List<ItemStack> possibleDrops = Arrays.asList(
    new ItemStack(Items.DIAMOND),
    new ItemStack(Items.GOLD_INGOT),
    new ItemStack(Items.EMERALD)
);
String containerType = "gui.structurescanner.loot.chest";

lootTables.add(new LootEntry(lootTableId, possibleDrops, containerType));

info.setLootTables(lootTables);
```

`possibleDrops` exists for things that exist outside of loot tables (e.g., structures without actual loot tables). This should usually be mutually exclusive with actual loot tables, so provide either loot tables or possible drops, unless you are sure it needs special handling.

**LootEntry Parameters:**
- `lootTableId`: The `ResourceLocation` of the loot table
- `possibleDrops`: Representative items that can drop (for display)
- `containerType`: Type of container (e.g., "Chest", "Barrel", "Dispenser")

---

### Entities

Document entities that spawn with or in the structure:

```java
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;

List<EntityEntry> entities = new ArrayList<>();

// 1 entity
entities.add(new EntityEntry(new ResourceLocation("minecraft", "witch"), 1));

// Multiple entities
entities.add(new EntityEntry(new ResourceLocation("minecraft", "zombie"), 3));

// Entity from a spawner (set spawner = true)
entities.add(new EntityEntry(new ResourceLocation("minecraft", "skeleton"), 1, true));

info.setEntities(entities);
```

**EntityEntry Constructors:**
- `EntityEntry(ResourceLocation entityId, int count)` - Regular entity
- `EntityEntry(ResourceLocation entityId, int count, boolean spawner)` - Specify if from spawner

The `spawner` flag indicates the entity comes from a mob spawner block rather than spawning directly with the structure (or naturally from the structure logic).

---

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

Always check if the target mod is loaded before accessing its classes:

```java
@Override
public boolean isAvailable() {
    return Loader.isModLoaded("targetmodid");
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
        SimpleStructureScanner.LOGGER.error("Failed to access mod data", e);
    }
}
```

This prevents `ClassNotFoundException` when the mod is not installed. `postInit()` is only called if `isAvailable()` returns `true`, which means it is safe to access mod classes there.

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

public class ExampleStructureProvider implements StructureProvider {

    private static final String PROVIDER_ID = "examplemod";
    private static final String MOD_ID = "examplemod";
    
    private List<ResourceLocation> knownStructures = new ArrayList<>();
    private Map<ResourceLocation, StructureInfo> structureInfos = new HashMap<>();

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getModName() {
        return I18n.translateToLocal("gui.structurescanner.provider.examplemod");
    }

    @Override
    public boolean isAvailable() {
        return Loader.isModLoaded(MOD_ID);
    }

    @Override
    public void postInit() {
        if (!isAvailable()) return;
        
        // Register structures
        ResourceLocation towerId = new ResourceLocation(MOD_ID, "tower");
        knownStructures.add(towerId);
        
        StructureInfo towerInfo = new StructureInfo(
            towerId, 
            I18n.translateToLocal("structure.examplemod.tower"), 
            PROVIDER_ID, 
            15, 30, 15  // Size
        );
        
        // Set dimensions (Overworld only)
        towerInfo.setValidDimensions(Collections.singleton(DimensionInfo.OVERWORLD));
        
        // Set biomes
        Set<Biome> biomes = new HashSet<>();
        biomes.add(Biomes.PLAINS);
        biomes.add(Biomes.FOREST);
        towerInfo.setValidBiomes(biomes);
        
        // Set rarity
        String rarityInfo = I18n.translateToLocalFormatted("gui.structurescanner.rarity.one_in_chunks", 100);
        towerInfo.setRarity(I18n.translateToLocalFormatted("gui.structurescanner.rarity", rarityInfo));
        
        // Set entities (2 guards + 1 spawner)
        List<EntityEntry> entities = new ArrayList<>();
        entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "tower_guard"), 2));
        entities.add(new EntityEntry(new ResourceLocation("minecraft", "skeleton"), 1, true));
        towerInfo.setEntities(entities);
        
        structureInfos.put(towerId, towerInfo);
    }

    @Override
    public List<ResourceLocation> getStructureIds() {
        return new ArrayList<>(knownStructures);
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        // This structure uses deterministic generation
        // You do not need to check mod from ResourceLocation, the caller ensures this (considering you're not referencing multiple mods, which is discouraged)
        return structureId.getResourcePath().equals("tower");
    }

    @Override
    @Nullable
    public StructureInfo getStructureInfo(ResourceLocation structureId) {
        return structureInfos.get(structureId);
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
- [ ] Return unique `getProviderId()`
- [ ] Return localized `getModName()`
- [ ] Check mod availability in `isAvailable()` using `Loader.isModLoaded()`
- [ ] Initialize structures in `postInit()` (not constructor)
- [ ] Create `StructureInfo` for each structure with:
  - [ ] Valid dimensions (`setValidDimensions`)
  - [ ] Valid biomes (`setValidBiomes`) if applicable
  - [ ] Rarity string or localization key (`setRarity`)
  - [ ] Block entries (`setBlocks`) for block summary
  - [ ] Layer data (`setLayers`) for visual representation
  - [ ] Loot table entries (`setLootTables`)
  - [ ] Entity entries (`setEntities`) with spawner flag where applicable
- [ ] Implement `canBeSearched()` based on structure generation type
- [ ] Implement `findNearest()` with filter and skip support
- [ ] Optionally implement `findAllNearby()` for batch search
- [ ] Register provider in `StructureProviderRegistry.providerClasses`
- [ ] Add localization strings for everything user-facing, if not already present
