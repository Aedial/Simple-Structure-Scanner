package com.simplestructurescanner.structure.external;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.config.ModConfig;
import com.simplestructurescanner.structure.AbstractStructureProvider;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureProvider;
import com.simplestructurescanner.structure.util.StructureTranslationKeys;
import com.simplestructurescanner.structure.util.RarityTextHelper;


/**
 * Loads config-driven external providers from JSON metadata plus optional NBT structure data.
 */
public final class ExternalStructureProviderLoader {

    private static final String DIRECTORY_NAME = "external-providers";
    private static final String DEFAULT_NBT_ROOT = "nbt";

    private ExternalStructureProviderLoader() {
    }

    public static List<StructureProvider> loadProviders() {
        File directory = getDirectory();
        if (directory == null) return Collections.emptyList();
        if (!directory.exists() && !directory.mkdirs()) {
            SimpleStructureScanner.LOGGER.warn("Could not create external provider directory at {}", directory);
            return Collections.emptyList();
        }

        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return Collections.emptyList();

        Arrays.sort(files, Comparator.comparing(File::getName));

        List<StructureProvider> providers = new ArrayList<>();
        for (File file : files) {
            ExternalStructureProvider provider = loadProvider(file);
            if (provider != null) providers.add(provider);
        }

        return providers;
    }

    @Nullable
    private static ExternalStructureProvider loadProvider(File file) {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (!parsed.isJsonObject()) {
                SimpleStructureScanner.LOGGER.warn("External provider file {} must contain a JSON object", file.getName());
                return null;
            }

            JsonObject root = parsed.getAsJsonObject();
            String providerId = readRequiredString(root, "providerId", file);
            String modNameKey = readOptionalString(root, "modNameKey");
            if (modNameKey == null || modNameKey.trim().isEmpty()) modNameKey = StructureTranslationKeys.providerNameKey(providerId);

            List<String> requiredMods = readStringList(root.get("requiredMods"));
            String nbtRoot = readOptionalString(root, "nbtRoot");
            if (nbtRoot == null || nbtRoot.trim().isEmpty()) nbtRoot = DEFAULT_NBT_ROOT;

            JsonArray structuresJson = root.getAsJsonArray("structures");
            if (structuresJson == null || structuresJson.size() == 0) {
                SimpleStructureScanner.LOGGER.warn("External provider file {} does not define any structures", file.getName());
                return null;
            }

            List<ResourceLocation> structureIds = new ArrayList<>();
            Map<ResourceLocation, StructureInfo> structureInfos = new LinkedHashMap<>();

            for (JsonElement structureElement : structuresJson) {
                if (!structureElement.isJsonObject()) {
                    SimpleStructureScanner.LOGGER.warn("Ignoring non-object structure entry in {}", file.getName());
                    continue;
                }

                JsonObject structureObject = structureElement.getAsJsonObject();
                StructureInfo structureInfo = parseStructure(providerId, structureObject, file, nbtRoot);
                if (structureInfo == null) continue;

                structureIds.add(structureInfo.getId());
                structureInfos.put(structureInfo.getId(), structureInfo);
            }

            if (structureInfos.isEmpty()) return null;

            return new ExternalStructureProvider(providerId, modNameKey, requiredMods, structureIds, structureInfos);
        } catch (IOException | IllegalArgumentException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to load external provider file {}", file.getName(), e);
            return null;
        }
    }

    @Nonnull
    private static StructureInfo parseStructure(String providerId, JsonObject structureObject, File file,
            String nbtRoot) {
        String idText = readRequiredString(structureObject, "id", file);
        ResourceLocation id = new ResourceLocation(idText);
        String displayNameKey = readOptionalString(structureObject, "displayNameKey");
        if (displayNameKey == null || displayNameKey.trim().isEmpty()) displayNameKey = StructureTranslationKeys.structureNameKey(id);

        LocalizedText displayName = LocalizedText.translatable(displayNameKey);

        StructureNBTParser.ParsedStructure parsedNbt = parseStructureNbt(structureObject, file, nbtRoot);

        int[] size = readSize(structureObject);
        if ((size[0] <= 0 || size[1] <= 0 || size[2] <= 0) && parsedNbt != null) {
            size = new int[] { parsedNbt.sizeX, parsedNbt.sizeY, parsedNbt.sizeZ };
        }

        StructureInfo info = new StructureInfo(id, displayName, providerId, size[0], size[1], size[2]);

        if (parsedNbt != null) applyParsedNbt(info, parsedNbt);

        Set<Biome> biomes = readBiomes(structureObject.get("biomes"), file);
        if (!biomes.isEmpty()) info.setValidBiomes(biomes);

        Set<DimensionInfo> dimensions = readDimensions(structureObject.get("dimensions"), file);
        if (!dimensions.isEmpty()) info.setValidDimensions(dimensions);

        applyRarity(info, structureObject);

        return info;
    }

    @Nullable
    private static StructureNBTParser.ParsedStructure parseStructureNbt(JsonObject structureObject, File file, String nbtRoot) {
        String nbtPath = readOptionalString(structureObject, "nbtPath");
        if (nbtPath == null || nbtPath.trim().isEmpty()) return null;

        File nbtBaseDirectory = new File(file.getParentFile(), nbtRoot);
        File nbtFile = resolveNbtFile(nbtBaseDirectory, nbtPath);
        if (nbtFile == null) {
            SimpleStructureScanner.LOGGER.warn("Invalid NBT path '{}' in {}", nbtPath, file.getName());
            return null;
        }

        StructureNBTParser.ParsedStructure parsed = StructureNBTParser.parseStructureFile(nbtFile);
        if (parsed == null) {
            SimpleStructureScanner.LOGGER.warn("Failed to parse external structure NBT '{}' in {}", nbtPath, file.getName());
        }

        return parsed;
    }

    private static void applyParsedNbt(StructureInfo info, StructureNBTParser.ParsedStructure parsedNbt) {
        AbstractStructureProvider.apply(info, parsedNbt);
    }

    @Nullable
    private static File resolveNbtFile(File baseDirectory, String nbtPath) {
        String normalizedPath = nbtPath.replace('\\', '/');
        if (normalizedPath.startsWith("/")) normalizedPath = normalizedPath.substring(1);
        if (!normalizedPath.endsWith(".nbt")) normalizedPath = normalizedPath + ".nbt";

        File nbtFile = new File(baseDirectory, normalizedPath);

        try {
            String basePath = baseDirectory.getCanonicalPath();
            String filePath = nbtFile.getCanonicalPath();
            if (!filePath.startsWith(basePath + File.separator) && !filePath.equals(basePath)) return null;

            return nbtFile;
        } catch (IOException e) {
            return null;
        }
    }

    private static void applyRarity(StructureInfo info, JsonObject structureObject) {
        String rarityKey = readOptionalString(structureObject, "rarityKey");
        if (rarityKey != null && !rarityKey.isEmpty()) {
            info.setRarityKey(rarityKey);
            return;
        }

        int rarityChunks = readOptionalInt(structureObject, "rarityChunks", -1);
        if (rarityChunks > 0) {
            info.setRarity(RarityTextHelper.oneInChunks(rarityChunks));
            return;
        }

        String rarityTextKey = readOptionalString(structureObject, "rarityTextKey");
        if (rarityTextKey != null && !rarityTextKey.isEmpty()) {
            info.setRarity(LocalizedText.translatable(rarityTextKey));
        }
    }

    private static Set<Biome> readBiomes(@Nullable JsonElement element, File file) {
        if (element == null || !element.isJsonArray()) return Collections.emptySet();

        Set<Biome> biomes = new LinkedHashSet<>();
        for (JsonElement biomeElement : element.getAsJsonArray()) {
            if (!biomeElement.isJsonPrimitive()) {
                SimpleStructureScanner.LOGGER.warn("Ignoring invalid biome entry in {}", file.getName());
                continue;
            }

            ResourceLocation biomeId = new ResourceLocation(biomeElement.getAsString());
            Biome biome = Biome.REGISTRY.getObject(biomeId);
            if (biome == null) {
                SimpleStructureScanner.LOGGER.warn("Unknown biome {} in {}", biomeId, file.getName());
                continue;
            }

            biomes.add(biome);
        }

        return biomes;
    }

    private static Set<DimensionInfo> readDimensions(@Nullable JsonElement element, File file) {
        if (element == null || !element.isJsonArray()) return Collections.emptySet();

        Set<DimensionInfo> dimensions = new LinkedHashSet<>();
        for (JsonElement dimensionElement : element.getAsJsonArray()) {
            if (dimensionElement.isJsonPrimitive()) {
                dimensions.add(new DimensionInfo(dimensionElement.getAsInt()));
                continue;
            }

            if (!dimensionElement.isJsonObject()) {
                SimpleStructureScanner.LOGGER.warn("Ignoring invalid dimension entry in {}", file.getName());
                continue;
            }

            JsonObject object = dimensionElement.getAsJsonObject();
            int dimensionId = readOptionalInt(object, "id", Integer.MIN_VALUE);
            if (dimensionId == Integer.MIN_VALUE) {
                SimpleStructureScanner.LOGGER.warn("Dimension entry in {} is missing an id", file.getName());
                continue;
            }

            if (object.has("key") || object.has("displayKey")) {
                SimpleStructureScanner.LOGGER.warn(
                    "Ignoring provider-local dimension key override for id {} in {}. Use {} instead.",
                    dimensionId, file.getName(), DimensionInfo.getGeneratedDisplayKey(dimensionId));
            }

            dimensions.add(new DimensionInfo(dimensionId));
        }

        return dimensions;
    }

    private static int[] readSize(JsonObject object) {
        if (object.has("size") && object.get("size").isJsonArray()) {
            JsonArray sizeArray = object.getAsJsonArray("size");
            if (sizeArray.size() == 3) {
                return new int[] {
                    sizeArray.get(0).getAsInt(),
                    sizeArray.get(1).getAsInt(),
                    sizeArray.get(2).getAsInt()
                };
            }
        }

        return new int[] {
            readOptionalInt(object, "sizeX", 0),
            readOptionalInt(object, "sizeY", 0),
            readOptionalInt(object, "sizeZ", 0)
        };
    }

    private static String readRequiredString(JsonObject object, String field, File file) {
        String value = readOptionalString(object, field);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing required field '" + field + "' in " + file.getName());
        }

        return value;
    }

    @Nullable
    private static String readOptionalString(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonPrimitive()) return null;

        return element.getAsString();
    }

    private static int readOptionalInt(JsonObject object, String field, int fallback) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) return fallback;
        if (!element.isJsonPrimitive()) return fallback;

        return element.getAsInt();
    }

    private static List<String> readStringList(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) return Collections.emptyList();

        List<String> values = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonPrimitive()) values.add(child.getAsString());
        }

        return values;
    }

    @Nullable
    private static File getDirectory() {
        File configRoot = ModConfig.getConfigRootDirectory();
        if (configRoot == null) return null;

        return new File(configRoot, DIRECTORY_NAME);
    }
}