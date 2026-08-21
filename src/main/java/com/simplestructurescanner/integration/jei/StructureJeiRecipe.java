package com.simplestructurescanner.integration.jei;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidStack;

import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeWrapper;

import com.simplestructurescanner.client.ClientTextResolver;
import com.simplestructurescanner.client.gui.SmallVanillaButton;
import com.simplestructurescanner.client.render.StructurePreviewRenderer;
import com.simplestructurescanner.integration.JEIHelper;
import com.simplestructurescanner.item.ModItems;
import com.simplestructurescanner.structure.LootTableResolver;
import com.simplestructurescanner.structure.LootTableResolver.LootItem;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureProviderRegistry;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntryKind;


/**
 * JEI wrapper for one structure in one logical tab.
 */
public class StructureJeiRecipe implements IRecipeWrapper {
    /** NBT tag that binds the synthetic JEI anchor item back to one structure id. */
    private static final String ANCHOR_TAG = "SSSJeiStructure";
    /** Optional NBT tag that narrows the synthetic anchor item to one tab. */
    private static final String ANCHOR_VIEW_TAG = "SSSJeiView";

    private static final int PANEL_PADDING = 7;
    private static final int BUTTON_SIZE = 12;
    private static final int BUTTON_GAP = 1;
    private static final int CONTENT_X = PANEL_PADDING;
    private static final int CONTENT_Y = PANEL_PADDING;
    private static final int CONTENT_W = StructureJeiCategory.WIDTH - PANEL_PADDING * 2;
    private static final int CONTENT_H = StructureJeiCategory.HEIGHT - PANEL_PADDING * 2;
    private static final int STRUCTURE_TITLE_Y = CONTENT_Y + 4;
    private static final int INNER_TAB_Y = CONTENT_Y + 16;
    private static final int GRID_COLS = 7;
    private static final int GRID_ROWS = 7;
    private static final int CELL_W = 18;
    private static final int CELL_H = 18;
    private static final int SLOT_SIZE = 16;
    private static final float LABEL_SCALE = 0.5f;
    private static final int PAGE_SIZE = GRID_COLS * GRID_ROWS;
    private static final int INNER_FRAME_W = GRID_COLS * CELL_W + 4;
    private static final int INNER_FRAME_H = GRID_ROWS * CELL_H + 4;
    private static final int INNER_FRAME_X = CONTENT_X + (CONTENT_W - INNER_FRAME_W) / 2;
    private static final int INNER_FRAME_Y = CONTENT_Y + 32;
    private static final int GRID_START_X = INNER_FRAME_X + 2;
    private static final int GRID_START_Y = INNER_FRAME_Y + 2;
    private static final int PAGE_BUTTON_Y = INNER_FRAME_Y + INNER_FRAME_H + 2;
    private static final int NEXT_PAGE_BUTTON_X = INNER_FRAME_X + INNER_FRAME_W - BUTTON_SIZE;
    private static final int PREV_PAGE_BUTTON_X = NEXT_PAGE_BUTTON_X - BUTTON_SIZE - 2;
    private static final int SCANNER_BUTTON_X = INNER_FRAME_X + INNER_FRAME_W - BUTTON_SIZE;
    private static final int CENTER_Y = INNER_FRAME_Y + INNER_FRAME_H / 2 - 4;
    private static final int PANEL_BACKGROUND_COLOR = 0x80F0F0F0;
    private static final int PANEL_LIGHT_BORDER_COLOR = 0xFFF5F5F5;
    private static final int PANEL_DARK_BORDER_COLOR = 0xFF8A8A8A;
    private static final int CONTENT_BACKGROUND_COLOR = 0xFFC8C8C8;
    private static final int PREVIEW_BACKGROUND_COLOR = 0xFF1A1A1A;
    private static final int PRIMARY_TEXT_COLOR = 0xFF000000;
    private static final int SECONDARY_TEXT_COLOR = 0xFF505050;
    private static final int EMPTY_TEXT_COLOR = 0xFF707070;

    /** JEI translates recipe drawing into local coordinates, but the preview renderer needs screen-space bounds. */
    private static final FloatBuffer MODEL_VIEW_MATRIX = BufferUtils.createFloatBuffer(16);

    private final ResourceLocation structureId;
    private final StructureJeiView view;

    @Nullable
    private IGuiItemStackGroup jeiItemStacks;
    @Nullable
    private IGuiFluidStackGroup jeiFluidStacks;
    @Nullable
    private IDrawable jeiSlotDrawable;
    @Nullable
    private StructureInfo jeiLayoutInfo;
    private int jeiLayoutPage = -1;

    /** Current page for the grid-based blocks and loot tabs. */
    private int page = 0;

    private SmallVanillaButton prevPageButton = new SmallVanillaButton(
        0, PREV_PAGE_BUTTON_X, PAGE_BUTTON_Y, BUTTON_SIZE, "<");
    private SmallVanillaButton nextPageButton = new SmallVanillaButton(
        1, NEXT_PAGE_BUTTON_X, PAGE_BUTTON_Y, BUTTON_SIZE, ">");
    private SmallVanillaButton scannerButton = new SmallVanillaButton(
        2, SCANNER_BUTTON_X, INNER_TAB_Y, BUTTON_SIZE, "S");
    private SmallVanillaButton previewButton = new SmallVanillaButton(
        3, getTabX(StructureJeiView.PREVIEW), INNER_TAB_Y, BUTTON_SIZE, StructureJeiView.PREVIEW.getButtonLabel());
    private SmallVanillaButton blocksButton = new SmallVanillaButton(
        4, getTabX(StructureJeiView.BLOCKS), INNER_TAB_Y, BUTTON_SIZE, StructureJeiView.BLOCKS.getButtonLabel());
    private SmallVanillaButton lootButton = new SmallVanillaButton(
        5, getTabX(StructureJeiView.LOOT), INNER_TAB_Y, BUTTON_SIZE, StructureJeiView.LOOT.getButtonLabel());

    private SmallVanillaButton[] BUTTONS = new SmallVanillaButton[] {
        prevPageButton, nextPageButton, scannerButton, previewButton, blocksButton, lootButton
    };

    @Nullable
    private StructureInfo cachedPreviewInfo;
    /**
     * Cached preview renderer for the current structure info instance.
     * This avoid rebuilding the preview world every time JEI redraws the panel,
     * which would bring us to 5 fps on big structures (like Mansion).
     */
    @Nullable
    private StructurePreviewRenderer previewRenderer;

    @Nullable
    private StructureInfo cachedBlockInfo;
    /** Cached block-tab data derived from the current structure info instance. */
    private List<BlockEntry> cachedSortedBlockEntries = Collections.emptyList();
    private List<ItemStack> cachedBlockItemOutputs = Collections.emptyList();
    private List<FluidStack> cachedBlockFluidOutputs = Collections.emptyList();

    @Nullable
    private StructureInfo cachedLootInfo;
    /** Cached loot-tab data derived from the current structure info instance. */
    private List<LootDisplayEntry> cachedLootEntries = Collections.emptyList();
    private List<ItemStack> cachedLootOutputs = Collections.emptyList();

    /**
     * Creates the wrapper for one structure and one tab.
     */
    public StructureJeiRecipe(ResourceLocation structureId, StructureJeiView view) {
        this.structureId = structureId;
        this.view = view;

        prevPageButton.setStaticTooltip(I18n.format("jei.structurescanner.button.prevPage"));
        nextPageButton.setStaticTooltip(I18n.format("jei.structurescanner.button.nextPage"));
        scannerButton.setStaticTooltip(I18n.format("jei.structurescanner.button.scanner"));
        previewButton.setStaticTooltip(I18n.format("jei.structurescanner.button.preview"));
        blocksButton.setStaticTooltip(I18n.format("jei.structurescanner.button.blocks"));
        lootButton.setStaticTooltip(I18n.format("jei.structurescanner.button.loot"));

        // Update the tooltips and visibility of all tab buttons, based on the current config and the current tab
        for (StructureJeiView tabView : StructureJeiView.values()) {
            SmallVanillaButton tabButton = getTabButton(tabView);
            if (tabButton != null) {
                tabButton.setStaticTooltip(I18n.format(getTabTooltipKey(tabView)));

                if (!StructureJeiVisibility.isCategoryEnabled(tabView)) {
                    tabButton.enabled = false;
                    tabButton.setStaticTooltip(I18n.format("jei.structurescanner.button.disabled"));
                }
            }
        }

        // Disable the button for the current tab, so it doesn't look like a clickable button
        SmallVanillaButton currentButton = getTabButton(view);
        if (currentButton != null) currentButton.enabled = false;
    }

    private SmallVanillaButton getTabButton(StructureJeiView tabView) {
        switch (tabView) {
            case PREVIEW: return previewButton;
            case BLOCKS: return blocksButton;
            case LOOT: return lootButton;
            default: return null;
        }
    }

    public ResourceLocation getStructureId() {
        return structureId;
    }

    void bindJeiLayout(IGuiItemStackGroup itemStacks, @Nullable IGuiFluidStackGroup fluidStacks, IDrawable slotDrawable) {
        if (jeiItemStacks != itemStacks) {
            itemStacks.addTooltipCallback((slotIndex, input, ingredient, tooltip) -> {
                if (view == StructureJeiView.BLOCKS) appendBlockItemTooltip(slotIndex, tooltip);
                if (view == StructureJeiView.LOOT) appendLootTooltip(slotIndex, tooltip);
            });
        }

        if (fluidStacks != null && jeiFluidStacks != fluidStacks) {
            fluidStacks.addTooltipCallback((slotIndex, input, ingredient, tooltip) -> appendBlockFluidTooltip(slotIndex, tooltip));
        }

        this.jeiItemStacks = itemStacks;
        this.jeiFluidStacks = fluidStacks;
        this.jeiSlotDrawable = slotDrawable;
        invalidateJeiLayout();
    }

    void syncJeiIngredientLayout() {
        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) return;
        syncJeiIngredientLayout(structureInfo);
    }

    public String getSortKey() {
        return getDisplayName();
    }

    /**
     * Exposes the outputs surfaced by the current tab.
     */
    @Override
    public void getIngredients(@Nonnull IIngredients ingredients) {
        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) return;

        if (view == StructureJeiView.BLOCKS) {
            List<ItemStack> itemOutputs = getBlockItemOutputs(structureInfo);
            if (!itemOutputs.isEmpty()) ingredients.setOutputs(VanillaTypes.ITEM, itemOutputs);

            List<FluidStack> fluidOutputs = getBlockFluidOutputs(structureInfo);
            if (!fluidOutputs.isEmpty()) ingredients.setOutputs(VanillaTypes.FLUID, fluidOutputs);

            return;
        }

        if (view == StructureJeiView.LOOT) {
            List<ItemStack> lootOutputs = getLootOutputStacks(structureInfo);
            if (!lootOutputs.isEmpty()) ingredients.setOutputs(VanillaTypes.ITEM, lootOutputs);
        }
    }

    /**
     * Draws the shared panel and the current tab body.
     */
    @Override
    public void drawInfo(@Nonnull Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        resetGuiOverlayState(minecraft);
        drawBackgroundLayer(minecraft, mouseX, mouseY);

        if (!StructureJeiVisibility.isCategoryEnabled(view)) {
            String disabledText = I18n.format("jei.structurescanner.disabledByConfig");
            drawCenteredFrameText(minecraft, disabledText, CENTER_Y, 0xFF8888);
            return;
        }

        if (!StructureJeiVisibility.isStructureVisible(structureId)) {
            String hiddenText = I18n.format("jei.structurescanner.hidden");
            drawCenteredFrameText(minecraft, hiddenText, CENTER_Y, 0xFF8888);
            return;
        }

        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) {
            String hiddenText = I18n.format("jei.structurescanner.hidden");
            drawCenteredFrameText(minecraft, hiddenText, CENTER_Y, 0xFF8888);
            return;
        }

        if (view == StructureJeiView.PREVIEW) {
            drawPreview(minecraft, structureInfo);
            return;
        }

        if (view == StructureJeiView.BLOCKS) {
            drawBlocks(minecraft, structureInfo, mouseX, mouseY);
            return;
        }

        drawLoot(minecraft, structureInfo, mouseX, mouseY);
    }

    /**
     * Supplies tab, paging, and hovered-entry tooltips for the panel.
     */
    @Nonnull
    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        if (!StructureJeiVisibility.isCategoryEnabled(view)) return Collections.emptyList();
        if (!StructureJeiVisibility.isStructureVisible(structureId)) return Collections.emptyList();

        for (SmallVanillaButton button : BUTTONS) {
            List<String> tooltip = button.getTooltipHovered();
            if (!tooltip.isEmpty()) return tooltip;
        }

        return Collections.emptyList();
    }

    /**
     * Handles tab switching, scanner jumps, paging, and recipe/use clicks inside the panel.
     */
    @Override
    public boolean handleClick(@Nonnull Minecraft minecraft, int mouseX, int mouseY, int mouseButton) {
        if (!StructureJeiVisibility.isCategoryEnabled(view)) return false;
        if (!StructureJeiVisibility.isStructureVisible(structureId)) return false;

        for (StructureJeiView tabView : StructureJeiView.values()) {
            SmallVanillaButton tabButton = getTabButton(tabView);
            if (tabButton.mousePressed(minecraft, mouseX, mouseY)) {
                return JEIHelper.showStructureCategory(structureId, tabView);
            }
        }

        if (scannerButton.mousePressed(minecraft, mouseX, mouseY)) {
            return JEIHelper.openStructureScanner(structureId);
        }

        if (view == StructureJeiView.BLOCKS || view == StructureJeiView.LOOT) {
            if (hasMultiplePages() && prevPageButton.mousePressed(minecraft, mouseX, mouseY)) {
                if (page > 0) {
                    page--;
                    invalidateJeiLayout();
                }

                return true;
            }

            if (hasMultiplePages() && nextPageButton.mousePressed(minecraft, mouseX, mouseY)) {
                if (page < getPageCount() - 1) {
                    page++;
                    invalidateJeiLayout();
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Checks whether this wrapper should match the provided JEI focus.
     */
    public boolean matchesFocus(IFocus<?> focus) {
        if (focus == null) return false;

        Object focusValue = focus.getValue();
        if (focusValue instanceof ItemStack) return matchesItemFocus(castFocus(focus));
        if (focusValue instanceof FluidStack) return matchesFluidFocus(castFocus(focus));

        return false;
    }

    /**
     * Builds the synthetic item JEI uses to jump directly to one structure entry.
     */
    public static ItemStack createAnchorStack(ResourceLocation structureId, @Nullable StructureJeiView view) {
        ItemStack anchorStack = new ItemStack(ModItems.JEI_ANCHOR);
        NBTTagCompound tagCompound = new NBTTagCompound();
        tagCompound.setString(ANCHOR_TAG, structureId.toString());
        if (view != null) tagCompound.setString(ANCHOR_VIEW_TAG, view.name());
        anchorStack.setTagCompound(tagCompound);

        return anchorStack;
    }

    /**
     * Identifies the synthetic item used for JEI structure navigation.
     */
    public static boolean isAnchorStack(ItemStack stack) {
        return !stack.isEmpty()
            && stack.getItem() == ModItems.JEI_ANCHOR
            && stack.hasTagCompound()
            && stack.getTagCompound().hasKey(ANCHOR_TAG);
    }

    public static ResourceLocation getAnchorStructureId(ItemStack stack) {
        assert stack.getTagCompound() != null;
        return new ResourceLocation(stack.getTagCompound().getString(ANCHOR_TAG));
    }

    /**
     * Reads the optional target tab from a synthetic anchor item.
     */
    @Nullable
    public static StructureJeiView getAnchorView(ItemStack stack) {
        if (!isAnchorStack(stack)) return null;
        if (!stack.getTagCompound().hasKey(ANCHOR_VIEW_TAG)) return null;

        try {
            return StructureJeiView.valueOf(stack.getTagCompound().getString(ANCHOR_VIEW_TAG));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> IFocus<V> castFocus(IFocus<?> focus) {
        return (IFocus<V>) focus;
    }

    @Nullable
    private StructureInfo getStructureInfo() {
        return StructureProviderRegistry.getStructureInfo(structureId);
    }

    private String getDisplayName() {
        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) return structureId.toString();

        return ClientTextResolver.resolve(structureInfo.getDisplayName());
    }

    /**
     * Draws all the static background elements of the JEI panel.
     */
    private void drawBackgroundLayer(Minecraft minecraft, int mouseX, int mouseY) {
        FontRenderer fontRenderer = minecraft.fontRenderer;

        drawFramedPanel(CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H, PANEL_BACKGROUND_COLOR);

        String displayName = fontRenderer.trimStringToWidth(getDisplayName(), INNER_FRAME_W - 4);
        fontRenderer.drawString(displayName, INNER_FRAME_X, STRUCTURE_TITLE_Y, PRIMARY_TEXT_COLOR);

        drawFramedPanel(INNER_FRAME_X, INNER_FRAME_Y, INNER_FRAME_W, INNER_FRAME_H, CONTENT_BACKGROUND_COLOR);

        int totalPages = getPageCount();
        prevPageButton.visible = totalPages > 1;
        prevPageButton.enabled = page > 0;

        if (totalPages > 1) {
            String pageText = I18n.format("jei.structurescanner.page", page + 1, totalPages);
            int pageTextWidth = fontRenderer.getStringWidth(pageText);
            int pageTextX = PREV_PAGE_BUTTON_X - pageTextWidth - 4;
            fontRenderer.drawString(pageText, pageTextX, PAGE_BUTTON_Y + 2, SECONDARY_TEXT_COLOR);
        }

        nextPageButton.visible = totalPages > 1;
        nextPageButton.enabled = page < totalPages - 1;

        for (SmallVanillaButton button : BUTTONS) button.drawButton(minecraft, mouseX, mouseY, 0.0F);
    }

    private void drawPreview(Minecraft minecraft, StructureInfo structureInfo) {
        StructurePreviewRenderer renderer = getPreviewRenderer(structureInfo);
        if (renderer == null || renderer.getWorld().renderedBlocks.isEmpty()) {
            drawCenteredFrameText(minecraft, I18n.format("gui.structurescanner.preview.unavailable"), INNER_FRAME_Y + INNER_FRAME_H / 2 - 4, EMPTY_TEXT_COLOR);
            return;
        }

        int previewSize = Math.max(1, Math.min(INNER_FRAME_W, INNER_FRAME_H) - 4);
        int previewX = INNER_FRAME_X + (INNER_FRAME_W - previewSize) / 2;
        int previewY = INNER_FRAME_Y + (INNER_FRAME_H - previewSize) / 2;
        int previewScreenX = previewX + getGuiTranslation(12);
        int previewScreenY = previewY + getGuiTranslation(13);

        renderer.setBackgroundColor(PREVIEW_BACKGROUND_COLOR);
        renderer.render(previewScreenX, previewScreenY, previewSize, previewSize);
        resetGuiOverlayState(minecraft);
    }

    private void drawBlocks(Minecraft minecraft, StructureInfo structureInfo, int mouseX, int mouseY) {
        List<BlockEntry> blockEntries = getSortedBlockEntries(structureInfo);
        int totalPages = Math.max(1, (blockEntries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        clampPage(totalPages);
        syncJeiIngredientLayout(structureInfo);

        if (!blockEntries.isEmpty()) drawBlockSlotBackgrounds(minecraft, structureInfo);

        String footerText = I18n.format("gui.structurescanner.blocks.count", blockEntries.size());
        drawFooter(minecraft, footerText, totalPages, mouseX, mouseY);

        if (blockEntries.isEmpty()) {
            String emptyText = I18n.format("gui.structurescanner.blocks.empty");
            drawCenteredFrameText(minecraft, emptyText, CENTER_Y, EMPTY_TEXT_COLOR);
        }
    }

    // TODO: Loot can take a second to load for ~10 pages, which is probably building the full loot table beforehand
    //       Can it be optimized? Not that big of a deal, but still slightly annoying.
    private void drawLoot(Minecraft minecraft, StructureInfo structureInfo, int mouseX, int mouseY) {
        List<LootDisplayEntry> lootEntries = getLootDisplayEntries(structureInfo);
        int totalPages = Math.max(1, (lootEntries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        clampPage(totalPages);
        syncJeiIngredientLayout(structureInfo);

        if (!lootEntries.isEmpty()) drawLootSlotBackgrounds(minecraft, structureInfo);

        String footerText = I18n.format("jei.structurescanner.loot.uniqueCount", lootEntries.size());
        drawFooter(minecraft, footerText, totalPages, mouseX, mouseY);

        if (lootEntries.isEmpty()) {
            String emptyText = I18n.format("jei.structurescanner.loot.empty");
            drawCenteredFrameText(minecraft, emptyText, CENTER_Y, EMPTY_TEXT_COLOR);
        }
    }

    /**
     * Draws the shared footer and page controls for multi-page tabs.
     */
    private void drawFooter(Minecraft minecraft, String footerText, int totalPages, int mouseX, int mouseY) {
        FontRenderer fontRenderer = minecraft.fontRenderer;
        int footerY = PAGE_BUTTON_Y + (totalPages <= 1 ? 4 : 2);
        fontRenderer.drawString(footerText, INNER_FRAME_X, footerY, SECONDARY_TEXT_COLOR);
    }

    private void syncJeiIngredientLayout(StructureInfo structureInfo) {
        if (jeiItemStacks == null) return;
        if (jeiLayoutInfo == structureInfo && jeiLayoutPage == page) return;

        if (view == StructureJeiView.BLOCKS) syncBlockJeiSlots(structureInfo);
        if (view == StructureJeiView.LOOT) syncLootJeiSlots(structureInfo);

        jeiLayoutInfo = structureInfo;
        jeiLayoutPage = page;
    }

    private void invalidateJeiLayout() {
        jeiLayoutInfo = null;
        jeiLayoutPage = -1;
    }

    private void syncBlockJeiSlots(StructureInfo structureInfo) {
        if (jeiItemStacks == null || jeiFluidStacks == null) return;

        for (int slotIndex = 0; slotIndex < PAGE_SIZE; slotIndex++) {
            jeiItemStacks.init(slotIndex, false, -10000, -10000);
            jeiFluidStacks.init(slotIndex, false, -10000, -10000, 16, 16, 1000, false, null);

            BlockEntry entry = getDisplayedBlockEntry(slotIndex, structureInfo);
            if (entry == null) continue;

            int slotX = getSlotX(slotIndex);
            int slotY = getSlotY(slotIndex);

            if (entry.displayStack != null && !entry.displayStack.isEmpty()) {
                ItemStack displayStack = entry.displayStack.copy();
                displayStack.setCount(1);
                jeiItemStacks.init(slotIndex, false, slotX, slotY);
                jeiItemStacks.set(slotIndex, displayStack);
                continue;
            }

            if (entry.displayFluid == null || entry.displayFluid.getFluid() == null || entry.displayFluid.amount <= 0) continue;

            // Where the hell does the -1px offset come from
            FluidStack displayFluid = entry.displayFluid.copy();
            displayFluid.amount = 1000;
            jeiFluidStacks.init(slotIndex, false, slotX + 1, slotY + 1, 16, 16, 1000, false, null);
            jeiFluidStacks.set(slotIndex, displayFluid);
        }
    }

    private void syncLootJeiSlots(StructureInfo structureInfo) {
        if (jeiItemStacks == null) return;

        for (int slotIndex = 0; slotIndex < PAGE_SIZE; slotIndex++) {
            jeiItemStacks.init(slotIndex, false, -10000, -10000);

            LootDisplayEntry entry = getDisplayedLootEntry(slotIndex, structureInfo);
            if (entry == null) continue;

            ItemStack displayStack = entry.stack.copy();
            displayStack.setCount(1);
            jeiItemStacks.init(slotIndex, false, getSlotX(slotIndex), getSlotY(slotIndex));
            jeiItemStacks.set(slotIndex, displayStack);
        }
    }

    private void appendBlockItemTooltip(int slotIndex, List<String> tooltip) {
        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) return;

        BlockEntry entry = getDisplayedBlockEntry(slotIndex, structureInfo);
        if (entry == null) return;

        tooltip.add(I18n.format("gui.structurescanner.blocks.count", entry.count));
    }

    private void appendBlockFluidTooltip(int slotIndex, List<String> tooltip) {
        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) return;

        BlockEntry entry = getDisplayedBlockEntry(slotIndex, structureInfo);
        if (entry == null || entry.displayFluid == null) return;

        tooltip.add(I18n.format("gui.structurescanner.blocks.count", entry.count));
        tooltip.add(I18n.format("gui.structurescanner.blocks.fluidAmount", (long) entry.displayFluid.amount * entry.count));
    }

    private void appendLootTooltip(int slotIndex, List<String> tooltip) {
        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) return;

        LootDisplayEntry entry = getDisplayedLootEntry(slotIndex, structureInfo);
        if (entry == null) return;

        tooltip.add(I18n.format("jei.structurescanner.loot.sources", entry.sourceCount));

        if (entry.isFixedOnly()) {
            tooltip.add(I18n.format("gui.structurescanner.loot.fixedCountTooltip", entry.getFixedCount()));
            return;
        }

        tooltip.add(I18n.format("gui.structurescanner.loot.rateTooltip", entry.formatMetric()));
        if (!entry.hasFixedContribution()) {
            tooltip.add(I18n.format("gui.structurescanner.loot.rateTooltipSimulated",
                entry.totalDropCount, LootTableResolver.getSimulationCount()));
        }

        // TODO: maybe add the sources as - <loot table name>\n- <container name>\n- ...
    }

    /**
     * Checks whether the current tab matches an item focus.
     */
    private boolean matchesItemFocus(IFocus<ItemStack> focus) {
        ItemStack stack = focus.getValue();
        if (stack.isEmpty()) return false;

        if (focus.getMode() == IFocus.Mode.INPUT && isAnchorStack(stack)) {
            if (!structureId.equals(getAnchorStructureId(stack))) return false;

            StructureJeiView anchorView = getAnchorView(stack);
            return anchorView == null || anchorView == view;
        }

        if (focus.getMode() != IFocus.Mode.OUTPUT) return false;

        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) return false;

        if (view == StructureJeiView.BLOCKS) {
            for (ItemStack output : getBlockItemOutputs(structureInfo)) {
                if (matchesItemStack(output, stack)) return true;
            }

            return false;
        }

        if (view == StructureJeiView.LOOT) {
            for (ItemStack output : getLootOutputStacks(structureInfo)) {
                if (matchesItemStack(output, stack)) return true;
            }
        }

        return false;
    }

    /**
     * Checks whether the blocks tab matches a fluid focus.
     */
    private boolean matchesFluidFocus(IFocus<FluidStack> focus) {
        if (view != StructureJeiView.BLOCKS || focus.getMode() != IFocus.Mode.OUTPUT) return false;

        FluidStack stack = focus.getValue();
        if (stack.amount <= 0 || stack.getFluid() == null) return false;

        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) return false;

        for (FluidStack output : getBlockFluidOutputs(structureInfo)) {
            if (output.isFluidEqual(stack)) return true;
        }

        return false;
    }

    /**
     * Exposes the block item outputs used by the lookup indexes.
     */
    List<ItemStack> getBlockItemOutputs() {
        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) return Collections.emptyList();

        return getBlockItemOutputs(structureInfo);
    }

    /**
     * Exposes the block fluid outputs used by the lookup indexes.
     */
    List<FluidStack> getBlockFluidOutputs() {
        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) return Collections.emptyList();

        return getBlockFluidOutputs(structureInfo);
    }

    /**
     * Exposes the loot item outputs used by the lookup indexes.
     */
    List<ItemStack> getLootOutputStacks() {
        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) return Collections.emptyList();

        return getLootOutputStacks(structureInfo);
    }

    private List<ItemStack> getBlockItemOutputs(StructureInfo structureInfo) {
        refreshBlockCache(structureInfo);

        return cachedBlockItemOutputs;
    }

    private List<FluidStack> getBlockFluidOutputs(StructureInfo structureInfo) {
        refreshBlockCache(structureInfo);

        return cachedBlockFluidOutputs;
    }

    private List<ItemStack> getLootOutputStacks(StructureInfo structureInfo) {
        refreshLootCache(structureInfo);

        return cachedLootOutputs;
    }

    private List<BlockEntry> getSortedBlockEntries(StructureInfo structureInfo) {
        refreshBlockCache(structureInfo);

        return cachedSortedBlockEntries;
    }

    /**
     * Refreshes the cached block-tab payload when the structure info instance changes.
     */
    private void refreshBlockCache(StructureInfo structureInfo) {
        if (cachedBlockInfo == structureInfo) return;

        List<BlockEntry> sortedEntries = new ArrayList<>(structureInfo.getBlocks());
        sortedEntries.sort((first, second) -> Integer.compare(second.count, first.count));

        List<ItemStack> itemOutputs = new ArrayList<>();
        List<FluidStack> fluidOutputs = new ArrayList<>();

        for (BlockEntry entry : sortedEntries) {
            if (entry.displayStack == null || entry.displayStack.isEmpty()) continue;

            ItemStack outputStack = entry.displayStack.copy();
            outputStack.setCount(1);
            if (containsMatchingItemStack(itemOutputs, outputStack)) continue;

            itemOutputs.add(outputStack);
        }

        for (BlockEntry entry : sortedEntries) {
            if (entry.displayFluid == null || entry.displayFluid.amount <= 0 || entry.displayFluid.getFluid() == null) continue;
            if (containsMatchingFluidStack(fluidOutputs, entry.displayFluid)) continue;

            fluidOutputs.add(entry.displayFluid.copy());
        }

        cachedBlockInfo = structureInfo;
        cachedSortedBlockEntries = Collections.unmodifiableList(sortedEntries);
        cachedBlockItemOutputs = Collections.unmodifiableList(itemOutputs);
        cachedBlockFluidOutputs = Collections.unmodifiableList(fluidOutputs);
    }

    private List<LootDisplayEntry> getLootDisplayEntries(StructureInfo structureInfo) {
        refreshLootCache(structureInfo);

        return cachedLootEntries;
    }

    void drawOverlay(Minecraft minecraft, int offsetX, int offsetY, int mouseX, int mouseY) {
        if (!StructureJeiVisibility.isCategoryEnabled(view)) return;
        if (!StructureJeiVisibility.isStructureVisible(structureId)) return;

        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) return;

        if (view == StructureJeiView.BLOCKS) drawBlockCountOverlays(minecraft, structureInfo, offsetX, offsetY);
        else if (view == StructureJeiView.LOOT) drawLootCountOverlays(minecraft, structureInfo, offsetX, offsetY);
    }

    /**
     * Refreshes the cached loot-tab payload when the structure info instance changes.
     */
    private void refreshLootCache(StructureInfo structureInfo) {
        if (cachedLootInfo == structureInfo) return;

        Map<String, LootDisplayEntry> lootByKey = new LinkedHashMap<>();
        World lootResolutionWorld = getLootResolutionWorld();
        int simulationCount = LootTableResolver.getSimulationCount();

        for (LootEntry lootEntry : structureInfo.getLootTables()) {
            List<LootItem> resolvedDrops = resolveLootDisplayItems(lootEntry, lootResolutionWorld, simulationCount);
            if (resolvedDrops.isEmpty()) continue;

            Set<String> seenInEntry = new LinkedHashSet<>();

            for (LootItem possibleDrop : resolvedDrops) {
                if (possibleDrop == null || possibleDrop.stack.isEmpty() || possibleDrop.dropCount <= 0) continue;

                ItemStack displayStack = LootTableResolver.normalizeForDisplay(possibleDrop.stack);
                if (displayStack.isEmpty()) continue;

                displayStack.setCount(1);

                String aggregationKey = LootTableResolver.createAggregationKey(displayStack);
                LootDisplayEntry displayEntry = lootByKey.computeIfAbsent(
                        aggregationKey,
                        k -> new LootDisplayEntry(displayStack));

                displayEntry.addContribution(
                    possibleDrop.dropCount,
                    seenInEntry.add(aggregationKey),
                    lootEntry.kind == LootEntryKind.FIXED_ITEMS
                );
            }
        }

        List<LootDisplayEntry> lootEntries = new ArrayList<>(lootByKey.values());
        lootEntries.sort((first, second) -> {
            int dropOrder = Integer.compare(second.totalDropCount, first.totalDropCount);
            if (dropOrder != 0) return dropOrder;

            int sourceOrder = Integer.compare(second.sourceCount, first.sourceCount);
            if (sourceOrder != 0) return sourceOrder;

            return first.stack.getDisplayName().compareToIgnoreCase(second.stack.getDisplayName());
        });

        List<ItemStack> lootOutputs = new ArrayList<>(lootEntries.size());
        for (LootDisplayEntry entry : lootEntries) lootOutputs.add(entry.stack.copy());

        cachedLootInfo = structureInfo;
        cachedLootEntries = Collections.unmodifiableList(lootEntries);
        cachedLootOutputs = Collections.unmodifiableList(lootOutputs);
    }

    private List<LootItem> resolveLootDisplayItems(LootEntry lootEntry, @Nullable World world,
            int simulationCount) {
        List<LootItem> resolvedLoot = StructureJeiRecipes.getResolvedLootItems(lootEntry, world);
        if (!resolvedLoot.isEmpty()) return resolvedLoot;

        if (lootEntry.possibleDrops == null || lootEntry.possibleDrops.isEmpty()) return Collections.emptyList();

        List<LootItem> fallbackStacks = new ArrayList<>(lootEntry.possibleDrops.size());
        for (ItemStack possibleDrop : lootEntry.possibleDrops) {
            if (possibleDrop == null || possibleDrop.isEmpty()) continue;

            int dropCount = possibleDrop.getCount();
            ItemStack fallbackStack = possibleDrop.copy();
            fallbackStack.setCount(1);

            if (lootEntry.kind == LootEntryKind.FIXED_ITEMS) dropCount *= simulationCount;

            fallbackStacks.add(new LootItem(fallbackStack, dropCount));
        }

        return fallbackStacks;
    }

    @Nullable
    private static World getLootResolutionWorld() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) return null;
        if (minecraft.getIntegratedServer() == null) return minecraft.world;

        WorldServer serverWorld = minecraft.getIntegratedServer().getWorld(minecraft.world.provider.getDimension());
        return serverWorld != null ? serverWorld : minecraft.world;
    }

    /**
     * Lazily creates and reuses the preview renderer for the current structure info instance.
     */
    @Nullable
    private StructurePreviewRenderer getPreviewRenderer(StructureInfo structureInfo) {
        if (structureInfo.getPreviewSnapshot().isEmpty()) {
            clearPreviewRenderer();
            return null;
        }

        if (previewRenderer != null && cachedPreviewInfo == structureInfo) return previewRenderer;

        clearPreviewRenderer();

        previewRenderer = StructurePreviewRenderer.createFromStructureInfo(structureInfo);
        cachedPreviewInfo = structureInfo;

        return previewRenderer;
    }

    /**
     * Releases the cached preview renderer when the preview payload changes or disappears.
     */
    private void clearPreviewRenderer() {
        if (previewRenderer == null) return;

        previewRenderer.release();
        previewRenderer = null;
        cachedPreviewInfo = null;
    }

    @Nullable
    private BlockEntry getHoveredBlockEntry(int mouseX, int mouseY, StructureInfo structureInfo) {
        List<BlockEntry> blockEntries = getSortedBlockEntries(structureInfo);
        int startIndex = page * PAGE_SIZE;

        for (int index = 0; index < PAGE_SIZE && startIndex + index < blockEntries.size(); index++) {
            int itemX = GRID_START_X + (index % GRID_COLS) * CELL_W;
            int itemY = GRID_START_Y + (index / GRID_COLS) * CELL_H;

            if (!isInRect(mouseX, mouseY, itemX, itemY, SLOT_SIZE, SLOT_SIZE)) continue;

            return blockEntries.get(startIndex + index);
        }

        return null;
    }

    @Nullable
    private LootDisplayEntry getHoveredLootEntry(int mouseX, int mouseY, StructureInfo structureInfo) {
        List<LootDisplayEntry> lootEntries = getLootDisplayEntries(structureInfo);
        int startIndex = page * PAGE_SIZE;

        for (int index = 0; index < PAGE_SIZE && startIndex + index < lootEntries.size(); index++) {
            int itemX = GRID_START_X + (index % GRID_COLS) * CELL_W;
            int itemY = GRID_START_Y + (index / GRID_COLS) * CELL_H;

            if (!isInRect(mouseX, mouseY, itemX, itemY, SLOT_SIZE, SLOT_SIZE)) continue;

            return lootEntries.get(startIndex + index);
        }

        return null;
    }

    @Nullable
    private BlockEntry getDisplayedBlockEntry(int slotIndex, StructureInfo structureInfo) {
        List<BlockEntry> blockEntries = getSortedBlockEntries(structureInfo);
        int entryIndex = page * PAGE_SIZE + slotIndex;
        if (entryIndex < 0 || entryIndex >= blockEntries.size()) return null;

        return blockEntries.get(entryIndex);
    }

    @Nullable
    private LootDisplayEntry getDisplayedLootEntry(int slotIndex, StructureInfo structureInfo) {
        List<LootDisplayEntry> lootEntries = getLootDisplayEntries(structureInfo);
        int entryIndex = page * PAGE_SIZE + slotIndex;
        if (entryIndex < 0 || entryIndex >= lootEntries.size()) return null;

        return lootEntries.get(entryIndex);
    }

    private int getSlotX(int slotIndex) {
        return GRID_START_X + (slotIndex % GRID_COLS) * CELL_W;
    }

    private int getSlotY(int slotIndex) {
        return GRID_START_Y + (slotIndex / GRID_COLS) * CELL_H;
    }

    private void drawBlockSlotBackgrounds(Minecraft minecraft, StructureInfo structureInfo) {
        if (jeiSlotDrawable == null) return;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        for (int slotIndex = 0; slotIndex < PAGE_SIZE; slotIndex++) {
            jeiSlotDrawable.draw(minecraft, getSlotX(slotIndex), getSlotY(slotIndex));
        }
    }

    private void drawLootSlotBackgrounds(Minecraft minecraft, StructureInfo structureInfo) {
        if (jeiSlotDrawable == null) return;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        for (int slotIndex = 0; slotIndex < PAGE_SIZE; slotIndex++) {
            jeiSlotDrawable.draw(minecraft, getSlotX(slotIndex), getSlotY(slotIndex));
        }
    }

    private void drawBlockCountOverlays(Minecraft minecraft, StructureInfo structureInfo, int offsetX, int offsetY) {
        for (int slotIndex = 0; slotIndex < PAGE_SIZE; slotIndex++) {
            BlockEntry entry = getDisplayedBlockEntry(slotIndex, structureInfo);
            if (entry == null || entry.count <= 1) continue;

            drawSlotCount(minecraft.fontRenderer, entry.formatCount(), offsetX + getSlotX(slotIndex), offsetY + getSlotY(slotIndex));
        }
    }

    private void drawLootCountOverlays(Minecraft minecraft, StructureInfo structureInfo, int offsetX, int offsetY) {
        for (int slotIndex = 0; slotIndex < PAGE_SIZE; slotIndex++) {
            LootDisplayEntry entry = getDisplayedLootEntry(slotIndex, structureInfo);
            if (entry == null) continue;

            drawSlotCount(minecraft.fontRenderer, entry.formatMetric(), offsetX + getSlotX(slotIndex), offsetY + getSlotY(slotIndex));
        }
    }

    private boolean hasMultiplePages() {
        return getPageCount() > 1;
    }

    private int getPageCount() {
        StructureInfo structureInfo = getStructureInfo();
        if (structureInfo == null) return 1;

        if (view == StructureJeiView.BLOCKS) return Math.max(1, (getSortedBlockEntries(structureInfo).size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (view == StructureJeiView.LOOT) return Math.max(1, (getLootDisplayEntries(structureInfo).size() + PAGE_SIZE - 1) / PAGE_SIZE);

        return 1;
    }

    private void clampPage(int totalPages) {
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
    }

    private static boolean containsMatchingItemStack(List<ItemStack> stacks, ItemStack target) {
        for (ItemStack stack : stacks) {
            if (matchesItemStack(stack, target)) return true;
        }

        return false;
    }

    private static boolean containsMatchingFluidStack(List<FluidStack> stacks, FluidStack target) {
        for (FluidStack stack : stacks) {
            if (stack.isFluidEqual(target)) return true;
        }

        return false;
    }

    private static boolean matchesItemStack(ItemStack first, ItemStack second) {
        return ItemStack.areItemsEqual(first, second) && ItemStack.areItemStackTagsEqual(first, second);
    }

    private static boolean isInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void drawSlotCount(FontRenderer fontRenderer, String countText, int slotX, int slotY) {
        if (countText == null || countText.isEmpty()) return;

        float inverseScale = 1.0F / LABEL_SCALE;
        boolean unicodeFlag = fontRenderer.getUnicodeFlag();
        fontRenderer.setUnicodeFlag(false);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.scale(LABEL_SCALE, LABEL_SCALE, 1.0F);

        int textX = (int) ((slotX + 16.0F - fontRenderer.getStringWidth(countText) * LABEL_SCALE) * inverseScale);
        int textY = (int) ((slotY + 16.0F - 7.0F * LABEL_SCALE) * inverseScale);
        fontRenderer.drawStringWithShadow(countText, textX, textY, 0xFFFFFF);

        GlStateManager.popMatrix();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        fontRenderer.setUnicodeFlag(unicodeFlag);
    }

    /**
     * Rebuilds a clean JEI 2D GUI baseline while preserving the recipe area's translated origin.
     * The preview renderer uses raw GL push/pop calls, which can desynchronize GlStateManager's cache.
     */
    private static void resetGuiOverlayState(Minecraft minecraft) {
        float translateX = getGuiTranslationFloat(12);
        float translateY = getGuiTranslationFloat(13);
        float translateZ = getGuiTranslationFloat(14);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GlStateManager.viewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);
        minecraft.entityRenderer.setupOverlayRendering();
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(false);

        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GlStateManager.disableTexture2D();
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GlStateManager.enableTexture2D();
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.translate(translateX, translateY, translateZ);
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.disableStandardItemLighting();
    }

    private static float getGuiTranslationFloat(int matrixIndex) {
        MODEL_VIEW_MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW_MATRIX);
        return MODEL_VIEW_MATRIX.get(matrixIndex);
    }

    private static int getGuiTranslation(int matrixIndex) {
        return Math.round(getGuiTranslationFloat(matrixIndex));
    }

    private static int getTabX(StructureJeiView tabView) {
        return INNER_FRAME_X + tabView.ordinal() * (BUTTON_SIZE + BUTTON_GAP);
    }

    private static String getTabTooltipKey(StructureJeiView tabView) {
        if (tabView == StructureJeiView.PREVIEW) return "jei.structurescanner.button.preview";
        if (tabView == StructureJeiView.BLOCKS) return "jei.structurescanner.button.blocks";

        return "jei.structurescanner.button.loot";
    }

    private static void drawFramedPanel(int x, int y, int width, int height, int fillColor) {
        Gui.drawRect(x, y, x + width, y + height, fillColor);
        Gui.drawRect(x, y, x + width - 1, y + 1, PANEL_LIGHT_BORDER_COLOR);
        Gui.drawRect(x, y, x + 1, y + height - 1, PANEL_LIGHT_BORDER_COLOR);
        Gui.drawRect(x + 1, y + height - 1, x + width, y + height, PANEL_DARK_BORDER_COLOR);
        Gui.drawRect(x + width - 1, y + 1, x + width, y + height, PANEL_DARK_BORDER_COLOR);
    }

    private void drawCenteredFrameText(Minecraft minecraft, String text, int y, int color) {
        FontRenderer fontRenderer = minecraft.fontRenderer;
        int textWidth = fontRenderer.getStringWidth(text);
        int x = INNER_FRAME_X + (INNER_FRAME_W - textWidth) / 2;
        fontRenderer.drawString(text, x, y, color);
    }

    /**
     * Aggregated loot row used by the loot tab after equivalent drops are merged.
     */
    private static class LootDisplayEntry {
        private final ItemStack stack;
        private int sourceCount;
        private int totalDropCount;
        private boolean fixedContribution;
        private boolean dynamicContribution;

        private LootDisplayEntry(ItemStack stack) {
            this.stack = stack;
            this.sourceCount = 0;
            this.totalDropCount = 0;
        }

        private void addContribution(int dropCount, boolean newSource, boolean fixed) {
            if (dropCount <= 0) return;

            totalDropCount += dropCount;
            if (newSource) sourceCount++;

            if (fixed) fixedContribution = true;
            else dynamicContribution = true;
        }

        private String formatMetric() {
            if (totalDropCount <= 0) return "";

            if (isFixedOnly()) return String.valueOf(getFixedCount());

            return LootTableResolver.formatDropRate(totalDropCount, LootTableResolver.getSimulationCount());
        }

        private int getFixedCount() {
            int simulationCount = LootTableResolver.getSimulationCount();
            if (simulationCount <= 0) return totalDropCount;

            return totalDropCount / simulationCount;
        }

        private boolean isFixedOnly() {
            return fixedContribution && !dynamicContribution;
        }

        private boolean hasFixedContribution() {
            return fixedContribution;
        }
    }
}