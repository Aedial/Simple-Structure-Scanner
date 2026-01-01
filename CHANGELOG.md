# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html


## [0.2.1] - 2026-01-
### Fixed
- Fix Vanilla structure provider (location finding and missing structures)

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
