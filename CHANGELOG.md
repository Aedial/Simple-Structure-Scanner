# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html


## [0.2.0] - 2025-12-28
### Added
- Add Igloo structure retrieval to VanillaStructureProvider (as test case)
- Add structure preview for those providing structure schematic data
- Add biome, dimension, rarity information to StructureInfo and display in GUI

### Fixed
- Fix loot table resolution vanilla provider (some loot tables were missing)

### Changed
- Change hardcoded block->item mapping to handle GuiBlocksWindow better


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
