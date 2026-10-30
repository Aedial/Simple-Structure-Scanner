package com.simplestructurescanner.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.config.SimpleStructureScannerConfig;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.util.ItemStackKey;


/**
 * Shared client settings and texture checks for filtering aggregated structure block entries.
 */
public final class BlockEntryFilter {
    private static BlockEntryFilter current;

    private final String[] blacklistEntries;
    private final Set<String> blacklistedIds = new HashSet<>();
    private final boolean hideMissingTextures;
    private final IBakedModel missingModel;
    private final TextureAtlasSprite missingSprite;
    private final Map<ItemStackKey, Boolean> missingItemTextures = new HashMap<>();

    private BlockEntryFilter(String[] blacklistEntries, boolean hideMissingTextures,
            IBakedModel missingModel, TextureAtlasSprite missingSprite) {
        this.blacklistEntries = blacklistEntries.clone();
        this.hideMissingTextures = hideMissingTextures;
        this.missingModel = missingModel;
        this.missingSprite = missingSprite;

        for (String entry : blacklistEntries) {
            if (entry != null && !entry.trim().isEmpty()) blacklistedIds.add(entry.trim());
        }
    }

    public static BlockEntryFilter getCurrent() {
        Minecraft mc = Minecraft.getMinecraft();
        String[] blacklistEntries = SimpleStructureScannerConfig.client.blockBlacklist;
        boolean hideMissingTextures = SimpleStructureScannerConfig.client.hideMissingBlockTextures;
        IBakedModel missingModel = mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel();
        TextureAtlasSprite missingSprite = mc.getTextureMapBlocks().getMissingSprite();

        if (current == null || !Arrays.equals(current.blacklistEntries, blacklistEntries)
                || current.hideMissingTextures != hideMissingTextures || current.missingModel != missingModel
                || current.missingSprite != missingSprite) {
            current = new BlockEntryFilter(blacklistEntries, hideMissingTextures, missingModel, missingSprite);
        }

        return current;
    }

    /**
     * Filters aggregated entries on the client thread before display or background indexing.
     */
    public List<BlockEntry> filter(List<BlockEntry> entries) {
        List<BlockEntry> result = new ArrayList<>(entries.size());
        for (BlockEntry entry : entries) {
            if (entry.displayStack == null && entry.displayFluid == null) continue;
            if (entry.blockState != null
                    && isBlacklisted(entry.blockState.getBlock().getRegistryName(), entry.displayStack)) continue;
            if (entry.displayStack != null
                    && isBlacklisted(entry.displayStack.getItem().getRegistryName(), entry.displayStack)) continue;
            if (hideMissingTextures && hasMissingTexture(entry)) continue;

            result.add(entry);
        }

        return result;
    }

    private boolean isBlacklisted(ResourceLocation id, @Nullable ItemStack displayStack) {
        if (id == null) return false;

        String name = id.toString();
        return blacklistedIds.contains(name)
            || displayStack != null && blacklistedIds.contains(name + "@" + displayStack.getMetadata());
    }

    private boolean hasMissingTexture(BlockEntry entry) {
        if (entry.displayStack != null) {
            ItemStackKey key = ItemStackKey.of(entry.displayStack);
            Boolean missing = missingItemTextures.get(key);
            if (missing != null) return missing;

            boolean result = hasMissingItemTexture(entry.displayStack);
            missingItemTextures.put(key, result);
            return result;
        }

        if (entry.displayFluid == null || entry.displayFluid.getFluid() == null) return false;

        ResourceLocation stillTexture = entry.displayFluid.getFluid().getStill();
        if (stillTexture == null) return false;

        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
            .getAtlasSprite(stillTexture.toString());
        return isMissingSprite(sprite);
    }

    private boolean hasMissingItemTexture(ItemStack stack) {
        try {
            IBakedModel model = Minecraft.getMinecraft().getRenderItem().getItemModelWithOverrides(stack, null, null);
            if (model == null || model.isBuiltInRenderer()) return false;
            if (model == missingModel) return true;
            if (hasMissingQuadTexture(model.getQuads(null, null, 0L))) return true;

            for (EnumFacing face : EnumFacing.values()) {
                if (hasMissingQuadTexture(model.getQuads(null, face, 0L))) return true;
            }
        } catch (RuntimeException exception) {
            SimpleStructureScanner.LOGGER.debug("Could not inspect block item textures for {}",
                stack.getItem().getRegistryName(), exception);
        }

        return false;
    }

    private boolean hasMissingQuadTexture(List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            if (isMissingSprite(quad.getSprite())) return true;
        }

        return false;
    }

    private boolean isMissingSprite(TextureAtlasSprite sprite) {
        if (sprite == null) return false;
        if (sprite == missingSprite) return true;

        // Failed texture loads can retain their sprite name and copy the missing sprite's UVs
        return sprite.getMinU() == missingSprite.getMinU() && sprite.getMaxU() == missingSprite.getMaxU()
            && sprite.getMinV() == missingSprite.getMinV() && sprite.getMaxV() == missingSprite.getMaxV();
    }
}
