# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html


## [1.2.0] - 2026-06-15
### Added
- Add Recurrent Complex structure provider, supporting all RC structures with their configured loot tables and entity spawners. The search is not yet implemented, because it is substantially more complex than Pillar's.

### Fixed
- Fix Structure Capture Ruler rounding the player position incorrectly, which could push captured corners onto the positive-adjacent block instead of the actual feet block.
- Fix TESR rendering (chests, beds, etc.) in the structure preview. These blocks should now render properly. The full Global TESR rendering is not implemented, as it is quite heavy, but can be added later if there is demand for it.
- Fix structure NBT previews dropping tile-entity display data, so End Ship dragon heads and mob spawners now render with their correct stored NBT instead of falling back to default skull and pig placeholders.


## [1.1.0] - 2026-05-30
### Added
- Add the Structure Capture Ruler, a tool that allows you to select two corners in-world and save the structure as an NBT file, with options to review and exclude blocks/entities/loot entries before saving. Use it to easily save structure previews for providers (or other mods) without manually entering the full structure description in the provider.
- Populate the structure preview with the captured structure data for all existing providers, allowing accurate preview and content display for these structures.
- Add a standalone `tools/structure_nbt_air.py` helper to strip explicit air blocks from structure NBT files and restore the Structure Capture Ruler's air mask when needed.
- Add missing Hydra Lair and Mausoleum structures to the Ice and Fire provider.

### Fixed
- Fix Pillar structure provider using client code, causing crashes on dedicated servers
- Optimize structure rendering, so large previews stop tanking GUI framerate. Even the largest structures (e.g., Mansion) should now render at a smooth 60+ FPS without getting the CPU hot.
- Fix Blocks window not collapsing different block facings into the same entry, causing an duplicate entry for some blocks with many facings (e.g., stairs, torches, chests, etc.)
- Fix structure loot windows skipping fixed container contents stored directly in structure NBT, so static inventories like the Igloo chest and brewing stand now appear in the loot list.
- Fix loot window drop-rate labels rounding very rare generated items down to 0% instead of showing a small non-zero chance.


## [1.0.1] - 2026-05-20
### Added
- Add proper rarity numbers to Vanilla, Ice and Fire, and AbyssalCraft providers

### Fixed
- Align vanilla structure searches more closely with Minecraft 1.12 for strongholds, fortresses, mineshafts, monuments, mansions, and end cities


## [1.0.0] - 2026-05-19
### Added
- Add Pillar structure provider
- Add metadata-only external structure providers loaded from JSON files in the config directory
- Add per-provider search/visibility blacklist files plus a client command to remove blacklist entries
- Add documented external provider and blacklist examples under `docs/examples/`
- Add a shared structure NBT parser extension API so integrations can reuse most of the NBT parsing logic
- Add dimension defaults to try the shared `gui.structurescanner.dimension.id.<id>` key before falling back to Unknown
- Add scanner list visibility toggles for non-searchable structures and current-dimension-only filtering

### Fixed
- Fix tracked structure searches and cached results to respect the current dimension consistently
- Fix structure and dimension text metadata to resolve client-side instead of during provider registration
- Fix structure block lists considering fluids as air


## [0.4.0] - 2026-01-22
### Added
- Add Ice and Fire structure provider

### Changed
- Tweak the size ratios to better fit different screen resolutions


## [0.3.0] - 2026-01-10
### Added
- Add AbyssalCraft structure provider
- Add Aether structure provider
- Add full documentation for creating structure providers in `docs/STRUCTURE_PROVIDER_GUIDE.md`

### Fixed
- Fix dimension display names in the structure info panel for modded dimensions
- Fix guiding arrow and overlay not taking into account the current dimension (so it would point to structures in other dimensions)
- Fix some entities not showing as spawner entities

### Changed
- Optimize single structure location scanning


## [0.2.4] - 2026-01-05
### Changed
- Tweak the gradient of the guiding arrow


## [0.2.3] - 2026-01-03
### Added
- Add batching support to structure location finding in StructureProvider interface

### Fixed
- Fix Village, Igloo, Witch Hut, Jungle Temple, and Desert Temple structure location finding in VanillaStructureProvider
- Fix safe teleport Y coordinate search to avoid having to go up because you are in the floor
- Fix overflow in sorting structure locations by distance when very far away from origin


## [0.2.2] - 2026-01-02
### Fixed
- Fix the guiding arrow being drawn incorrectly (culling issues)


## [0.2.1] - 2026-01-01
### Fixed
- Fix structure preview interfering with other item-related modals (blocks, loot, entities windows)


## [0.2.0] - 2026-01-01
### Added
- Add Igloo structure retrieval to VanillaStructureProvider (as test case)
- Add structure preview for those providing structure schematic data
- Add structure preview window when clicking on the structure preview area
- Add biome, dimension, rarity information to StructureInfo and display in GUI
- Add y-agnostic arrow mode for structure location
- Add location blacklisting (per structure, per world)
- Add teleport button to a found structure (op only)

### Fixed
- Fix loot table resolution vanilla provider (some loot tables were missing)
- Fix loot tables window not being scrollable with mouse wheel
- Fix structures not being searched on relog
- Fix structures list not being sorted properly
- Fix arrow interpolation, causing jittery movement
- Fix arrow rendering to properly point in 3D space and show the distance label correctly

### Changed
- Change hardcoded block->item mapping to handle GuiBlocksWindow better
- Rehaul the Vanilla provider's structure location finding
- Improve the UX of the structure location cycling in the right panel
- Merge entries with of the same item but different NBT/metadata in the loot tables window


## [0.1.1] - 2025-12-27
### Added
- Keybind handler (`KeybindHandler`) to open the scanner GUI (default: P key)
- Structure provider system for modular structure support:
  - `StructureProvider` interface for implementing structure sources
  - `StructureProviderRegistry` for managing multiple providers
  - `StructureInfo` data class containing block palette, loot tables, and entity entries
  - `StructureLocation` data class for structure coordinates
- Configs to control the HUD and what is allowed in the scanner
- Vanilla structure provider (`VanillaStructureProvider`) supporting:
  - Village, Mineshaft, Stronghold, Desert Temple, Jungle Temple, Witch Hut, Igloo, Ocean Monument, Woodland Mansion, Dungeon, Nether Fortress, End City, End Ship
  - Seed-based structure location finding
- Main scanner GUI (`GuiStructureScanner`) with split-view layout:
  - Left panel: scrollable structure list with search
  - Right panel: structure details (mod origin, description, location)
  - Buttons to open Blocks, Loot, and Entities windows
  - Navigate to nearest structure functionality
- Blocks window (`GuiBlocksWindow`) displaying block palette in a grid layout
- Loot window (`GuiLootWindow`) displaying loot table entries in a grid layout
- Entities window (`GuiEntitiesWindow`) with:
  - Scrollable entity list on the left
  - Interactive entity preview on the right
- Localization support (`en_us.lang`)
