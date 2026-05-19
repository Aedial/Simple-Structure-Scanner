# External Provider Example

This folder contains a minimal example for config-driven external providers.

## Where to place these files

- Place the JSON provider file under:
  - `config/simplestructurescanner/external-providers/`
- Place the referenced NBT files under:
  - `config/simplestructurescanner/external-providers/nbt/`

With the included sample, this means:

- `config/simplestructurescanner/external-providers/example_provider.json`
- `config/simplestructurescanner/external-providers/nbt/examplepack/ruined_tower.nbt`
- `config/simplestructurescanner/external-providers/nbt/examplepack/ocean/sunken_library.nbt`

## Notes

- `nbtPath` is relative to `nbtRoot` (defaults to `nbt` if omitted).
- If `nbtPath` is present, block/layer/entity/loot details are parsed from the NBT file.
- JSON still controls metadata such as dimensions, biomes, and rarity.
- User-facing text should use translation keys, not inline English strings.
- Custom dimensions should use the shared key format `gui.structurescanner.dimension.id.<dimensionId>`.
- `Unknown` is only used if that shared dimension key is missing.
- External providers are metadata-only and are not searchable unless you add custom Java search logic.

See the sample lang snippet in `en_us.lang` for the provider, structure, and shared dimension keys referenced by the example JSON.
