# Simple Structure Scanner

A Minecraft 1.12.2 mod to help you look into and find specific structures. 

## Features
- GUI accessible via keybinding (default P).
- Filterable scrolling list of structures on the left; right panel shows details.
- Live search on the main screen for the nearest selected structure. Double-click a structure in the list to toggle search.

- Support for custom loot tables integrated with JEI.
  - Custom loot tables viewer cannot be used without server-side installation (if you play singleplayer, you can ignore this).
  - The custom viewer can use mouse or keys to open JEI: U/Left-click for uses, R/Right-click for recipes.

- Configs:
  - enableSearch: Globally disable search.
  - whitelist/blacklist: Configure which structures are allowed/disallowed for searching. Whitelist takes priority over blacklist. Partial matches are supported (e.g., `village` matches all structures with "village" in their ID or `minecraft` matches all structures from the Minecraft namespace). To avoid matching too broadly, keep the `:` separator for namespace matching (e.g., `pillar:`). As it is a purely client-side mod, there is no way to enforce server-side structure restrictions.
  - localWhitelist/localBlacklist: Same as above, but it stops being disallowed if the player is within a certain radius of the structure. The radius is per whitelist/blacklist entry, configured as `filter;radius` (e.g., `minecraft:;100`).

- List of supported structures:
  - Vanilla Minecraft structures.
  - Custom structures from the Pillar mod.

## FAQ
### Do I need to install this on a server?
You do not need to, unless you want to use the custom loot tables viewer. The mod will still work fine without server-side installation.

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
