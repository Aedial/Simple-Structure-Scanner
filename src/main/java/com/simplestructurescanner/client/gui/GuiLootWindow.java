package com.simplestructurescanner.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.simplestructurescanner.integration.JEIHelper;
import com.simplestructurescanner.structure.LootTableResolver;
import com.simplestructurescanner.structure.LootTableResolver.LootItem;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;


/**
 * Modal popup window that displays structure loot tables.
 * Shows aggregated loot items with drop rates in a grid.
 */
public class GuiLootWindow {
    private static final int ITEM_SIZE = 18;
    private static final int ITEM_PADDING = 4;
    private static final int ITEM_RATE_HEIGHT = 8;
    private static final int HEADER_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 16;
    private static final int ENTRY_HEADER_HEIGHT = 14;
    private static final int ENTRY_PADDING = 6;

    private final GuiScreen parent;
    private final ResourceLocation structureId;
    private final StructureInfo structureInfo;

    private boolean visible = false;
    private boolean hiddenForJEI = false;
    private int windowX, windowY, windowW, windowH;

    // Scroll state
    private float scrollOffset = 0;
    private int contentHeight = 0;

    // Hover state
    private int hoveredEntryIndex = -1;
    private int hoveredItemIndex = -1;
    private boolean hoveringTotal = false;
    private boolean hoveringDropRate = false;
    private int dropRateHoverIndex = -1;

    // Loot data - resolved and aggregated
    private List<LootEntry> lootEntries;
    private List<List<LootItem>> resolvedLoot = new ArrayList<>();
    private boolean lootResolved = false;
    private int simulationCount = 0;

    public GuiLootWindow(GuiScreen parent, ResourceLocation structureId, StructureInfo structureInfo) {
        this.parent = parent;
        this.structureId = structureId;
        this.structureInfo = structureInfo;
        this.lootEntries = structureInfo.getLootTables();
    }

    public void show() {
        visible = true;
        hiddenForJEI = false;
        scrollOffset = 0;
        resolveLootTables();
        calculateLayout();
    }

    /**
     * Resolve loot table items with proper simulation.
     * Uses server world for proper event firing if available.
     */
    private void resolveLootTables() {
        if (lootResolved) return;
        lootResolved = true;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return;

        // Get server world if available (for proper loot simulation)
        World world = mc.world;
        if (mc.getIntegratedServer() != null) {
            WorldServer serverWorld = mc.getIntegratedServer().getWorld(mc.world.provider.getDimension());
            if (serverWorld != null) world = serverWorld;
        }

        simulationCount = LootTableResolver.getSimulationCount();

        for (LootEntry entry : lootEntries) {
            List<LootItem> entryLoot = new ArrayList<>();

            // Try to resolve from loot table ID first
            if (entry.lootTableId != null) {
                entryLoot = LootTableResolver.resolveLootTableWithSimulation(
                    world, entry.lootTableId, mc.player);
            }

            // If loot table resolution failed or wasn't available, use possibleDrops as fallback
            if (entryLoot.isEmpty() && entry.possibleDrops != null && !entry.possibleDrops.isEmpty()) {
                for (ItemStack stack : entry.possibleDrops) {
                    int count = stack.getCount();
                    stack = stack.copy();
                    stack.setCount(1);
                    entryLoot.add(new LootItem(stack, count * simulationCount));
                }
            }

            resolvedLoot.add(entryLoot);
        }
    }

    public void hide() {
        visible = false;
        hiddenForJEI = false;
        hoveredEntryIndex = -1;
        hoveredItemIndex = -1;
        hoveringTotal = false;
        hoveringDropRate = false;
        dropRateHoverIndex = -1;
    }

    private void hideForJEI() {
        visible = false;
        hiddenForJEI = true;
        hoveredEntryIndex = -1;
        hoveredItemIndex = -1;
        hoveringTotal = false;
        hoveringDropRate = false;
        dropRateHoverIndex = -1;
    }

    public boolean restoreIfHiddenForNavigation() {
        if (!hiddenForJEI) return false;

        visible = true;
        hiddenForJEI = false;
        calculateLayout();

        return true;
    }

    public boolean isHiddenForNavigation() {
        return hiddenForJEI;
    }

    public boolean isVisible() {
        return visible;
    }

    private void calculateLayout() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        windowW = Math.min(350, screenW - 40);
        windowH = Math.min(300, screenH - 40);

        windowX = (screenW - windowW) / 2;
        windowY = (screenH - windowH) / 2;

        // Calculate total content height
        int itemsPerRow = (windowW - 20) / (ITEM_SIZE + ITEM_PADDING);
        if (itemsPerRow < 1) itemsPerRow = 1;

        contentHeight = 0;
        for (int i = 0; i < resolvedLoot.size(); i++) {
            List<LootItem> items = resolvedLoot.get(i);
            int itemRows = (items.size() + itemsPerRow - 1) / itemsPerRow;
            if (itemRows == 0) itemRows = 1;
            contentHeight += ENTRY_HEADER_HEIGHT + itemRows * (ITEM_SIZE + ITEM_RATE_HEIGHT + ITEM_PADDING) + ENTRY_PADDING;
        }

        // If no resolved loot yet, estimate from loot entries
        if (resolvedLoot.isEmpty()) {
            contentHeight = lootEntries.size() * (ENTRY_HEADER_HEIGHT + ITEM_SIZE + ITEM_PADDING + ENTRY_PADDING);
        }
    }

    public void handleScroll(int amount) {
        if (!visible) return;

        scrollOffset -= amount * 10;
        clampScroll();
    }

    private void clampScroll() {
        int viewHeight = windowH - HEADER_HEIGHT - FOOTER_HEIGHT;
        float maxScroll = Math.max(0, contentHeight - viewHeight);
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;

        // Click outside closes
        if (mouseX < windowX || mouseX > windowX + windowW ||
            mouseY < windowY || mouseY > windowY + windowH) {
            hide();

            return true;
        }

        // Handle JEI integration on item click
        if (hoveredEntryIndex >= 0 && hoveredEntryIndex < resolvedLoot.size() &&
            hoveredItemIndex >= 0 && hoveredItemIndex < resolvedLoot.get(hoveredEntryIndex).size()) {

            LootItem item = resolvedLoot.get(hoveredEntryIndex).get(hoveredItemIndex);

            if (mouseButton == 0) {
                // Left click - show uses
                if (JEIHelper.showItemUses(item.stack)) {
                    hideForJEI();

                    return true;
                }
            } else if (mouseButton == 1) {
                // Right click - show recipes
                if (JEIHelper.showItemRecipes(item.stack)) {
                    hideForJEI();

                    return true;
                }
            }
        }

        return true;
    }

    public boolean handleKey(int keyCode) {
        if (!visible) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            hide();

            return true;
        }

        // JEI keybinds
        if (hoveredEntryIndex >= 0 && hoveredEntryIndex < resolvedLoot.size() &&
            hoveredItemIndex >= 0 && hoveredItemIndex < resolvedLoot.get(hoveredEntryIndex).size()) {

            LootItem item = resolvedLoot.get(hoveredEntryIndex).get(hoveredItemIndex);

            if (keyCode == Keyboard.KEY_U) {
                if (JEIHelper.showItemUses(item.stack)) {
                    hideForJEI();

                    return true;
                }
            } else if (keyCode == Keyboard.KEY_R) {
                if (JEIHelper.showItemRecipes(item.stack)) {
                    hideForJEI();

                    return true;
                }
            }
        }

        return false;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        if (!visible) return false;

        return mouseX >= windowX && mouseX <= windowX + windowW &&
               mouseY >= windowY && mouseY <= windowY + windowH;
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        // Draw semi-transparent overlay
        Gui.drawRect(0, 0, mc.displayWidth, mc.displayHeight, 0x80000000);

        // Draw window background
        Gui.drawRect(windowX - 1, windowY - 1, windowX + windowW + 1, windowY + windowH + 1, 0x80303030);
        Gui.drawRect(windowX, windowY, windowX + windowW, windowY + windowH, 0x801A1A1A);

        // Draw header
        String title = I18n.format("gui.structurescanner.loot.title", structureInfo.getDisplayName());
        String elidedTitle = font.trimStringToWidth(title, windowW - 16);
        if (!elidedTitle.equals(title)) elidedTitle += "…";
        font.drawString(elidedTitle, windowX + 6, windowY + 6, 0xFFFFFF);

        // Draw footer
        hoveringTotal = false;
        int footerY = windowY + windowH - FOOTER_HEIGHT + 2;
        String footer = I18n.format("gui.structurescanner.loot.count", lootEntries.size());

        if (mouseX >= windowX + 6 && mouseX <= windowX + 6 + font.getStringWidth(footer) &&
            mouseY >= footerY && mouseY <= footerY + 10) {
            hoveringTotal = true;
        }

        font.drawString(footer, windowX + 6, footerY, hoveringTotal ? 0xFFFFAA : 0xCCCCCC);

        // Draw content
        if (lootEntries.isEmpty()) {
            String msg = I18n.format("gui.structurescanner.loot.noLoot");
            int textW = font.getStringWidth(msg);
            font.drawString(msg, windowX + (windowW - textW) / 2, windowY + HEADER_HEIGHT + 20, 0xFF6666);
        } else {
            drawLootEntries(mouseX, mouseY);
        }

        // Draw scrollbar if needed
        int viewHeight = windowH - HEADER_HEIGHT - FOOTER_HEIGHT;
        if (contentHeight > viewHeight) {
            int scrollbarX = windowX + windowW - 6;
            int scrollbarH = Math.max(20, (int) ((float) viewHeight / contentHeight * viewHeight));
            float maxScroll = contentHeight - viewHeight;
            int scrollbarY = windowY + HEADER_HEIGHT + (int) ((scrollOffset / maxScroll) * (viewHeight - scrollbarH));

            Gui.drawRect(scrollbarX, windowY + HEADER_HEIGHT, scrollbarX + 4, windowY + windowH - FOOTER_HEIGHT, 0x40FFFFFF);
            Gui.drawRect(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarH, 0xA0FFFFFF);
        }
    }

    private void drawLootEntries(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        float textScale = 0.5f;

        hoveredEntryIndex = -1;
        hoveredItemIndex = -1;
        hoveringDropRate = false;
        dropRateHoverIndex = -1;

        int contentX = windowX + 6;
        int contentW = windowW - 16;
        int contentTop = windowY + HEADER_HEIGHT;
        int contentBottom = windowY + windowH - FOOTER_HEIGHT;
        int viewHeight = contentBottom - contentTop;

        int itemsPerRow = contentW / (ITEM_SIZE + ITEM_PADDING);
        if (itemsPerRow < 1) itemsPerRow = 1;

        // Enable scissor
        ScaledResolution sr = new ScaledResolution(mc);
        int scaleFactor = sr.getScaleFactor();
        int scissorX = windowX * scaleFactor;
        int scissorY = (sr.getScaledHeight() - contentBottom) * scaleFactor;
        int scissorW = windowW * scaleFactor;
        int scissorH = viewHeight * scaleFactor;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

        int currentY = contentTop - (int) scrollOffset;

        for (int entryIdx = 0; entryIdx < lootEntries.size(); entryIdx++) {
            LootEntry entry = lootEntries.get(entryIdx);
            List<LootItem> items = entryIdx < resolvedLoot.size() ? resolvedLoot.get(entryIdx) : new ArrayList<>();

            // Calculate entry height
            int itemRows = (items.size() + itemsPerRow - 1) / itemsPerRow;
            if (itemRows == 0) itemRows = 1;
            int entryHeight = ENTRY_HEADER_HEIGHT + itemRows * (ITEM_SIZE + ITEM_RATE_HEIGHT + ITEM_PADDING) + ENTRY_PADDING;

            // Skip if out of view
            if (currentY + entryHeight < contentTop || currentY > contentBottom) {
                currentY += entryHeight;
                continue;
            }

            // Draw entry header
            String tableName = entry.lootTableId.getPath();
            String containerKey = "gui.structurescanner.loot." + entry.containerType.toLowerCase();
            String containerName = I18n.format(containerKey);
            if (!containerName.equals(containerKey)) {
                tableName += " (" + containerName + ")";
            }
            String elidedName = font.trimStringToWidth(tableName, contentW - 6);
            if (!elidedName.equals(tableName)) elidedName += "…";

            if (currentY >= contentTop - 10) {
                font.drawString(elidedName, contentX + 2, currentY + 2, 0xFFFFAA);
            }

            int itemY = currentY + ENTRY_HEADER_HEIGHT;

            // Draw items
            GlStateManager.pushMatrix();
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableDepth();

            for (int i = 0; i < items.size(); i++) {
                int col = i % itemsPerRow;
                int row = i / itemsPerRow;
                int itemX = contentX + col * (ITEM_SIZE + ITEM_PADDING);
                int itemPosY = itemY + row * (ITEM_SIZE + ITEM_RATE_HEIGHT + ITEM_PADDING);

                if (itemPosY + ITEM_SIZE < contentTop || itemPosY > contentBottom) continue;

                LootItem item = items.get(i);

                // Draw slot background
                Gui.drawRect(itemX - 1, itemPosY - 1, itemX + 17, itemPosY + 17, 0xFF373737);

                // Check hover
                boolean hovered = mouseX >= itemX && mouseX < itemX + 16 &&
                                  mouseY >= itemPosY && mouseY < itemPosY + 16 &&
                                  mouseY >= contentTop && mouseY <= contentBottom;
                if (hovered) {
                    hoveredEntryIndex = entryIdx;
                    hoveredItemIndex = i;
                    Gui.drawRect(itemX - 1, itemPosY - 1, itemX + 17, itemPosY + 17, 0xFF555555);
                }

                // Render item
                mc.getRenderItem().renderItemIntoGUI(item.stack, itemX, itemPosY);

                // Draw drop rate below item
                String rate = item.formatDropRate(simulationCount);
                int rateW = (int) (font.getStringWidth(rate) * textScale);
                int rateX = itemX + (16 - rateW) / 2;
                int rateY = itemPosY + 18;

                // Check rate hover
                boolean rateHovered = mouseX >= rateX - 1 && mouseX <= rateX + rateW + 1 &&
                                      mouseY >= rateY && mouseY <= rateY + 8;
                if (rateHovered) {
                    hoveringDropRate = true;
                    dropRateHoverIndex = i;
                }

                GlStateManager.pushMatrix();
                GlStateManager.scale(textScale, textScale, 1.0f);
                int rateColor = rateHovered ? 0xFFFFAA : 0xCCCCCC;
                font.drawString(rate, (int) (rateX / textScale), (int) (rateY / textScale), rateColor);
                GlStateManager.popMatrix();
            }

            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            GlStateManager.popMatrix();

            // Draw "no drops" message if empty
            if (items.isEmpty() && itemY >= contentTop && itemY <= contentBottom) {
                String noDrops = I18n.format("gui.structurescanner.loot.unknown");
                font.drawString(noDrops, contentX + 4, itemY + 4, 0x888888);
            }

            currentY += entryHeight;
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public void drawTooltips(int mouseX, int mouseY) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();

        // Item tooltip
        if (hoveredEntryIndex >= 0 && hoveredEntryIndex < resolvedLoot.size() &&
            hoveredItemIndex >= 0 && hoveredItemIndex < resolvedLoot.get(hoveredEntryIndex).size()) {

            LootItem item = resolvedLoot.get(hoveredEntryIndex).get(hoveredItemIndex);
            ITooltipFlag.TooltipFlags tooltipFlag = mc.gameSettings.advancedItemTooltips ?
                ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
            List<String> tooltip = item.stack.getTooltip(mc.player, tooltipFlag);

            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 500);
            drawHoveringText(tooltip, mouseX, mouseY, mc.fontRenderer);
            GlStateManager.popMatrix();
        }

        // Drop rate tooltip
        if (hoveringDropRate && hoveredEntryIndex >= 0 && hoveredEntryIndex < resolvedLoot.size() &&
            dropRateHoverIndex >= 0 && dropRateHoverIndex < resolvedLoot.get(hoveredEntryIndex).size()) {

            LootItem item = resolvedLoot.get(hoveredEntryIndex).get(dropRateHoverIndex);
            String tooltip = I18n.format("gui.structurescanner.loot.rateTooltipSimulated",
                item.dropCount, simulationCount);

            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 500);
            List<String> lines = new ArrayList<>();
            lines.add(tooltip);
            drawHoveringText(lines, mouseX, mouseY, mc.fontRenderer);
            GlStateManager.popMatrix();
        }
    }

    private void drawHoveringText(List<String> textLines, int x, int y, FontRenderer font) {
        if (textLines.isEmpty()) return;

        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        int maxAllowedWidth = screenW - x - 8;
        List<String> wrappedLines = new ArrayList<>();

        for (String line : textLines) {
            int lineWidth = font.getStringWidth(line);
            if (lineWidth > maxAllowedWidth) {
                wrappedLines.addAll(font.listFormattedStringToWidth(line, maxAllowedWidth));
            } else {
                wrappedLines.add(line);
            }
        }

        int maxWidth = 0;
        for (String s : wrappedLines) {
            int w = font.getStringWidth(s);
            if (w > maxWidth) maxWidth = w;
        }

        int posX = x + 12;
        int posY = y - 12;
        int height = 8;
        if (wrappedLines.size() > 1) height += 2 + (wrappedLines.size() - 1) * 10;

        if (posX + maxWidth > screenW) posX -= 28 + maxWidth;
        if (posY + height + 6 > screenH) posY = screenH - height - 6;

        int bgColor = 0xF0100010;
        int borderColorStart = 0x505000FF;
        int borderColorEnd = 0x5028007F;

        Gui.drawRect(posX - 3, posY - 4, posX + maxWidth + 3, posY - 3, bgColor);
        Gui.drawRect(posX - 3, posY + height + 3, posX + maxWidth + 3, posY + height + 4, bgColor);
        Gui.drawRect(posX - 3, posY - 3, posX + maxWidth + 3, posY + height + 3, bgColor);
        Gui.drawRect(posX - 4, posY - 3, posX - 3, posY + height + 3, bgColor);
        Gui.drawRect(posX + maxWidth + 3, posY - 3, posX + maxWidth + 4, posY + height + 3, bgColor);

        Gui.drawRect(posX - 3, posY - 3 + 1, posX - 3 + 1, posY + height + 3 - 1, borderColorStart);
        Gui.drawRect(posX + maxWidth + 2, posY - 3 + 1, posX + maxWidth + 3, posY + height + 3 - 1, borderColorStart);
        Gui.drawRect(posX - 3, posY - 3, posX + maxWidth + 3, posY - 3 + 1, borderColorStart);
        Gui.drawRect(posX - 3, posY + height + 2, posX + maxWidth + 3, posY + height + 3, borderColorEnd);

        for (int i = 0; i < wrappedLines.size(); i++) {
            String line = wrappedLines.get(i);
            font.drawStringWithShadow(line, posX, posY, -1);
            posY += (i == 0) ? 12 : 10;
        }

        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableRescaleNormal();
    }
}
