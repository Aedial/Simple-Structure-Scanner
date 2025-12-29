# Simple Structure Scanner

A Minecraft 1.12.2 mod to help you look into and find specific structures. 

## Features
- GUI accessible via keybinding (default P).
- Filterable scrolling list of structures on the left; right panel shows details.
- Live search on the main screen for the nearest selected structure. Double-click a structure in the list to toggle search.

- Preview of structure schematic when available. Click on the preview area to open a larger preview window.

- Support for custom loot tables integrated with JEI: U/Left-click for uses, R/Right-click for recipes.

- Configs:
  - enableSearch: Globally disable search.
  - whitelist/blacklist: Configure which structures are allowed/disallowed for searching. Whitelist takes priority over blacklist. Partial matches are supported (e.g., `village` matches all structures with "village" in their ID or `minecraft` matches all structures from the Minecraft namespace). To avoid matching too broadly, keep the `:` separator for namespace matching (e.g., `pillar:`). Add `;radius` to make the entry local, which stops being enforced if the player is within a certain radius of the structure (e.g., `minecraft:;100` only blocks vanilla structures until within 100 blocks).
  - showBlocks: Show blocks that are part of the structure in the details panel.
  - showEntities: Show entities that are part of the structure in the details panel.
  - showLootTables: Show loot tables that are part of the structure in the details panel.

- List of supported structures:
  - Vanilla Minecraft structures.
  - Custom structures from the Pillar mod.

## FAQ
### Do I need to install this on a server?
If you wish the query structure locations, you will need to put the mod on the server as well. However, if you only want to view structure information, you can use it client-side, as long as the mod providing said structures is also installed client-side.

### How is the structures list filtered?
The filter box matches both localized and unlocalized structure names. This means you can type the mod name, the name in your selected language, or the default English name.

### The structure wasn't there.
Due to how complex the process is, structures from a mod may prevent or overlap with other structures from another mod. In this case, you should try searching for another structure of the same type.

## Building
Run:
```
./gradlew -q build
```
Resulting jar will be under `build/libs/`.

## License
This project is licensed under the MIT License - see the LICENSE file for details.
