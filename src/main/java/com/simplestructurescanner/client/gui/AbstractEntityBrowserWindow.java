package com.simplestructurescanner.client.gui;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.config.GuiUtils;

import com.simplestructurescanner.SimpleStructureScanner;


/**
 * Shared entity browser window with a left-side list and a right-side preview pane.
 */
abstract class AbstractEntityBrowserWindow<T> {

    protected static final int HEADER_HEIGHT = 20;
    protected static final int FOOTER_HEIGHT = 16;
    protected static final int LIST_WIDTH = 150;
    protected static final int ENTRY_HEIGHT = 14;

    private static final int ENTRY_BUTTON_HEIGHT = 12;
    private static final int ENTRY_BUTTON_PADDING = 4;

    private static final Map<ResourceLocation, String> ENTITY_NAME_CACHE = new HashMap<>();

    private final GuiScreen parent;
    private final List<T> entries;
    private final Map<ResourceLocation, Entity> entityCache = new HashMap<>();
    private final Set<ResourceLocation> entitiesWithRenderErrors = new HashSet<>();

    private boolean visible;
    private int windowX;
    private int windowY;
    private int windowW;
    private int windowH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int viewerX;
    private int viewerY;
    private int viewerW;
    private int viewerH;
    private int selectedIndex = -1;
    private float scrollOffset;
    private int hoveredListIndex = -1;
    private boolean hoveringFooter;

    protected AbstractEntityBrowserWindow(GuiScreen parent, List<T> entries) {
        this.parent = parent;
        this.entries = entries;
    }

    public void show() {
        visible = true;
        selectedIndex = entries.isEmpty() ? -1 : 0;
        scrollOffset = 0;
        calculateLayout();
        resetHoverState();
    }

    public void hide() {
        visible = false;
        resetHoverState();
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        if (!visible) return false;

        return mouseX >= windowX && mouseX <= windowX + windowW
            && mouseY >= windowY && mouseY <= windowY + windowH;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;

        if (!isMouseOver(mouseX, mouseY)) {
            hide();
            return true;
        }

        int index = getEntryIndexAt(mouseX, mouseY);
        if (index < 0 || index >= entries.size()) return true;

        selectedIndex = index;

        T entry = entries.get(index);
        EntryButtonSpec entryButton = getEntryButton(entry, Minecraft.getMinecraft().fontRenderer);
        if (entryButton != null) {
            int entryY = getEntryY(index);
            int buttonX = getEntryButtonX(entryButton);
            if (isInBounds(mouseX, mouseY, buttonX, entryY + 1, entryButton.width, ENTRY_BUTTON_HEIGHT)) {
                onEntryButtonClicked(entry, index);
                return true;
            }
        }

        onEntryClicked(entry, index, mouseButton);
        return true;
    }

    public boolean handleKey(int keyCode) {
        if (!visible) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            hide();
            return true;
        }

        if (keyCode == Keyboard.KEY_UP && selectedIndex > 0) {
            selectedIndex--;
            ensureVisible(selectedIndex);
            return true;
        }

        if (keyCode == Keyboard.KEY_DOWN && selectedIndex < entries.size() - 1) {
            selectedIndex++;
            ensureVisible(selectedIndex);
            return true;
        }

        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_SPACE)
                && selectedIndex >= 0 && selectedIndex < entries.size()) {
            return handleSelectedEntryAction(entries.get(selectedIndex), selectedIndex);
        }

        return false;
    }

    public boolean handleMouseInput(int mouseX, int mouseY, int wheel) {
        if (!visible || !isMouseOver(mouseX, mouseY)) return false;

        scrollOffset -= wheel * 0.25f;
        float maxScroll = getMaxScroll();
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        return true;
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        if (shouldDrawOverlay()) {
            Gui.drawRect(0, 0, mc.displayWidth, mc.displayHeight, getOverlayColor());
        }

        Gui.drawRect(windowX - 1, windowY - 1, windowX + windowW + 1, windowY + windowH + 1, getWindowBorderColor());
        Gui.drawRect(windowX, windowY, windowX + windowW, windowY + windowH, getWindowBackgroundColor());

        String title = getTitle();
        String elidedTitle = elideText(font, title, windowW - 12);
        if (useTitleShadow()) {
            font.drawStringWithShadow(elidedTitle, windowX + 6, windowY + 6, 0xFFFFFF);
        } else {
            font.drawString(elidedTitle, windowX + 6, windowY + 6, 0xFFFFFF);
        }

        hoveringFooter = false;
        int footerY = windowY + windowH - FOOTER_HEIGHT + 2;
        String footer = getFooterText();
        if (mouseX >= windowX + 6 && mouseX <= windowX + 6 + font.getStringWidth(footer)
                && mouseY >= footerY && mouseY <= footerY + 10) {
            hoveringFooter = true;
        }

        font.drawString(footer, windowX + 6, footerY, hoveringFooter ? 0xFFFFAA : 0xCCCCCC);

        if (entries.isEmpty()) {
            String emptyMessage = getEmptyMessage();
            int textWidth = font.getStringWidth(emptyMessage);
            font.drawString(emptyMessage, windowX + (windowW - textWidth) / 2, listY + 20, 0xFF6666);
            return;
        }

        drawEntityList(mouseX, mouseY);
        drawEntityViewer(partialTicks);
    }

    public void drawTooltips(int mouseX, int mouseY) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen currentScreen = mc.currentScreen != null ? mc.currentScreen : parent;
        int screenWidth = currentScreen != null ? currentScreen.width : mc.displayWidth;
        int screenHeight = currentScreen != null ? currentScreen.height : mc.displayHeight;

        if (hoveredListIndex >= 0 && hoveredListIndex < entries.size()) {
            List<String> tooltipLines = getEntryTooltipLines(entries.get(hoveredListIndex));
            if (!tooltipLines.isEmpty()) {
                GuiUtils.drawHoveringText(tooltipLines, mouseX, mouseY, screenWidth, screenHeight, -1, mc.fontRenderer);
            }

            return;
        }

        if (!hoveringFooter) return;

        List<String> tooltipLines = getFooterTooltipLines();
        if (tooltipLines.isEmpty()) return;

        GuiUtils.drawHoveringText(tooltipLines, mouseX, mouseY, screenWidth, screenHeight, -1, mc.fontRenderer);
    }

    protected final void restoreVisibility() {
        visible = true;
        calculateLayout();
        resetHoverState();
    }

    protected final List<T> getEntries() {
        return entries;
    }

    @Nullable
    protected final T getSelectedEntry() {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) return null;

        return entries.get(selectedIndex);
    }

    protected final String formatEntityName(ResourceLocation entityId) {
        if (entityId == null) return "";

        String cachedName = ENTITY_NAME_CACHE.get(entityId);
        if (cachedName != null) return cachedName;

        Entity entity = getEntityInstance(entityId);
        if (entity != null) {
            String name = entity.getDisplayName().getUnformattedText();
            ENTITY_NAME_CACHE.put(entityId, name);
            return name;
        }

        String[] parts = entityId.toString().split(":", 2);
        String domain = parts.length > 0 ? parts[0] : "minecraft";
        String path = parts.length > 1 ? parts[1] : parts[0];
        String altKey = "entity." + domain + "." + path + ".name";
        if (I18n.hasKey(altKey)) {
            String translatedName = I18n.format(altKey);
            ENTITY_NAME_CACHE.put(entityId, translatedName);
            return translatedName;
        }

        String fallback = entityId.toString();
        ENTITY_NAME_CACHE.put(entityId, fallback);
        return fallback;
    }

    @Nullable
    protected final Entity getEntityInstance(ResourceLocation entityId) {
        if (entityCache.containsKey(entityId)) return entityCache.get(entityId);

        try {
            Entity entity = EntityList.createEntityByIDFromName(entityId, Minecraft.getMinecraft().world);
            entityCache.put(entityId, entity);
            return entity;
        } catch (Exception exception) {
            entityCache.put(entityId, null);
            return null;
        }
    }

    protected final int getViewerX() {
        return viewerX;
    }

    protected final int getViewerY() {
        return viewerY;
    }

    protected final int getViewerW() {
        return viewerW;
    }

    protected final int getViewerH() {
        return viewerH;
    }

    protected final int getSelectedIndex() {
        return selectedIndex;
    }

    protected abstract String getTitle();

    protected abstract String getEmptyMessage();

    protected abstract String getFooterText();

    protected List<String> getFooterTooltipLines() {
        return Collections.emptyList();
    }

    protected abstract ResourceLocation getEntityId(T entry);

    protected abstract String getListEntryName(T entry);

    protected String getListEntryTrailingText(T entry) {
        return "";
    }

    protected int getEntryTextColor(T entry, boolean selected, boolean hovered) {
        if (selected) return 0xFFFFFF;
        if (hovered) return 0xFFFFAA;

        return 0xCCCCCC;
    }

    protected int getEntryMarkerColor(T entry) {
        return -1;
    }

    @Nullable
    protected EntryButtonSpec getEntryButton(T entry, FontRenderer font) {
        return null;
    }

    protected void onEntryButtonClicked(T entry, int index) {
    }

    protected void onEntryClicked(T entry, int index, int mouseButton) {
    }

    protected boolean handleSelectedEntryAction(T entry, int index) {
        return false;
    }

    protected int drawViewerDetails(T entry, FontRenderer font, int textY) {
        return textY + 8;
    }

    protected List<String> getEntryTooltipLines(T entry) {
        return Collections.singletonList(getEntityId(entry).toString());
    }

    protected boolean shouldDrawOverlay() {
        return false;
    }

    protected boolean useTitleShadow() {
        return false;
    }

    protected int getOverlayColor() {
        return 0x80000000;
    }

    protected int getWindowBorderColor() {
        return 0xFF303030;
    }

    protected int getWindowBackgroundColor() {
        return 0xFF1A1A1A;
    }

    private void calculateLayout() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        int screenW = resolution.getScaledWidth();
        int screenH = resolution.getScaledHeight();

        windowW = Math.min(400, screenW - 40);
        windowH = Math.min(300, screenH - 40);
        windowX = (screenW - windowW) / 2;
        windowY = (screenH - windowH) / 2;

        listX = windowX + 6;
        listY = windowY + HEADER_HEIGHT + 5;
        listW = LIST_WIDTH;
        listH = windowH - HEADER_HEIGHT - FOOTER_HEIGHT - 10;

        viewerX = listX + listW + 10;
        viewerY = listY;
        viewerW = windowW - listW - 22;
        viewerH = listH;
    }

    private void resetHoverState() {
        hoveredListIndex = -1;
        hoveringFooter = false;
    }

    private void drawEntityList(int mouseX, int mouseY) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        hoveredListIndex = -1;
        Gui.drawRect(listX, listY, listX + listW, listY + listH, 0x40000000);

        int visibleStart = (int) (scrollOffset / ENTRY_HEIGHT);
        int visibleEnd = Math.min(entries.size(), visibleStart + (listH / ENTRY_HEIGHT) + 2);

        for (int index = visibleStart; index < visibleEnd; index++) {
            int entryY = getEntryY(index);
            if (entryY + ENTRY_HEIGHT < listY || entryY > listY + listH) continue;

            T entry = entries.get(index);
            boolean selected = index == selectedIndex;
            boolean hovered = mouseX >= listX && mouseX <= listX + listW
                && mouseY >= entryY && mouseY <= entryY + ENTRY_HEIGHT;

            if (hovered) hoveredListIndex = index;

            if (selected) {
                Gui.drawRect(listX, entryY, listX + listW, entryY + ENTRY_HEIGHT, 0x60FFFFFF);
            } else if (hovered) {
                Gui.drawRect(listX, entryY, listX + listW, entryY + ENTRY_HEIGHT, 0x30FFFFFF);
            }

            int markerColor = getEntryMarkerColor(entry);
            int textX = listX + 3;
            if (markerColor >= 0) {
                Gui.drawRect(listX, entryY, listX + 2, entryY + ENTRY_HEIGHT, markerColor);
                textX += 3;
            }

            EntryButtonSpec entryButton = getEntryButton(entry, font);
            int buttonWidth = entryButton != null ? entryButton.width + ENTRY_BUTTON_PADDING : 0;
            String trailingText = getListEntryTrailingText(entry);
            int trailingTextWidth = trailingText.isEmpty() ? 0 : font.getStringWidth(trailingText);
            int availableWidth = Math.max(0, listW - (textX - listX) - 3 - buttonWidth - trailingTextWidth);
            String elidedText = elideText(font, getListEntryName(entry), availableWidth);
            int textColor = getEntryTextColor(entry, selected, hovered);

            font.drawString(elidedText, textX, entryY + 3, textColor);
            if (!trailingText.isEmpty()) {
                font.drawString(trailingText, textX + font.getStringWidth(elidedText), entryY + 3, textColor);
            }

            if (entryButton == null) continue;

            int buttonX = getEntryButtonX(entryButton);
            boolean buttonHovered = isInBounds(mouseX, mouseY, buttonX, entryY + 1, entryButton.width, ENTRY_BUTTON_HEIGHT);
            drawEntryButton(font, entryButton, buttonX, entryY + 1, buttonHovered);
        }

        float maxScroll = getMaxScroll();
        if (maxScroll <= 0) return;

        int scrollbarX = listX + listW - 3;
        int scrollbarH = Math.max(20, (int) ((float) listH / (listH + maxScroll) * listH));
        int scrollbarY = listY + (int) ((scrollOffset / maxScroll) * (listH - scrollbarH));
        Gui.drawRect(scrollbarX, listY, scrollbarX + 3, listY + listH, 0x40FFFFFF);
        Gui.drawRect(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarH, 0xA0FFFFFF);
    }

    private void drawEntryButton(FontRenderer font, EntryButtonSpec button, int x, int y, boolean hovered) {
        int backgroundColor = hovered ? button.hoveredBackgroundColor : button.backgroundColor;
        Gui.drawRect(x, y, x + button.width, y + ENTRY_BUTTON_HEIGHT, backgroundColor);
        font.drawString(button.label, x + 4, y + 3, button.textColor);
    }

    private void drawEntityViewer(float partialTicks) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        Gui.drawRect(viewerX, viewerY, viewerX + viewerW, viewerY + viewerH, 0x40000000);

        T entry = getSelectedEntry();
        if (entry == null) return;

        String entityName = formatEntityName(getEntityId(entry));
        int textY = viewerY + 5;

        String nameLabel = I18n.format("gui.structurescanner.entities.entityName", entityName);
        font.drawString(elideText(font, nameLabel, viewerW - 6), viewerX + 3, textY, 0xFFFFFF);
        textY += 12;

        String idLabel = I18n.format("gui.structurescanner.entities.entityId", getEntityId(entry).toString());
        font.drawString(elideText(font, idLabel, viewerW - 6), viewerX + 3, textY, 0xCCCCCC);
        textY += 12;

        int previewTopY = drawViewerDetails(entry, font, textY);
        int previewSize = Math.min(viewerW - 10, viewerY + viewerH - previewTopY - 10);
        if (previewSize <= 30) return;

        int previewX = viewerX + (viewerW - previewSize) / 2;
        int previewY = previewTopY + 5;
        float entityRotation = (Minecraft.getSystemTime() % 10000L) / 10000.0F * 360.0F;
        drawEntityPreview(getEntityId(entry), previewX, previewY, previewSize, entityRotation);
    }

    private void drawEntityPreview(ResourceLocation entityId, int x, int y, int size, float rotation) {
        Entity entity = getEntityInstance(entityId);
        if (entity == null || size <= 0) {
            Gui.drawRect(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF404040);
            Gui.drawRect(x, y, x + size, y + size, 0xFF202020);

            String placeholder = "?";
            int textWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(placeholder);
            Minecraft.getMinecraft().fontRenderer.drawString(placeholder, x + (size - textWidth) / 2, y + (size - 8) / 2, 0x888888);
            return;
        }

        Gui.drawRect(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF404040);
        Gui.drawRect(x, y, x + size, y + size, 0xFF202020);

        preparePreviewEntity(entity);

        float maxDimension = Math.max(1.0F, Math.max(entity.height, entity.width));
        float scale = size / maxDimension / 2.0F;

        int centerX = x + size / 2;
        int centerY = y + size / 2;

        // The parent GUI renders most 2D content with depth disabled.
        // Reset the depth buffer for the preview so entity layers occlude correctly.
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);

        GlStateManager.pushMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 50.0F);
        GlStateManager.scale(-scale, scale, scale);
        GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(135.0F, 0.0F, 1.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(20.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(rotation, 0.0F, 1.0F, 0.0F);

        float verticalOffset = entity.height / 2.0F + (float) entity.getYOffset();
        GlStateManager.translate(0.0F, -verticalOffset, 0.0F);
        Minecraft.getMinecraft().getRenderManager().playerViewY = 180.0F;

        try {
            if (!entitiesWithRenderErrors.contains(entityId)) {
                Minecraft.getMinecraft().getRenderManager().renderEntity(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, false);
            }
        } catch (Throwable throwable) {
            if (!entitiesWithRenderErrors.contains(entityId)) {
                entitiesWithRenderErrors.add(entityId);
                SimpleStructureScanner.LOGGER.warn("Failed to render entity preview for {}: {}", entityId, throwable.getMessage());
            }
        }

        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.popMatrix();
        GlStateManager.disableDepth();
        GlStateManager.disableColorMaterial();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    // Some mod renderers apply world-space visibility or culling checks before drawing.
    // Keep preview entities aligned with the active camera position so those checks do not
    // incorrectly treat off-world preview instances at 0,0,0 as hidden.
    private void preparePreviewEntity(Entity entity) {
        if (entity == null) return;

        Entity cameraEntity = Minecraft.getMinecraft().getRenderViewEntity();
        if (cameraEntity == null) return;

        entity.prevPosX = cameraEntity.prevPosX;
        entity.prevPosY = cameraEntity.prevPosY;
        entity.prevPosZ = cameraEntity.prevPosZ;
        entity.lastTickPosX = cameraEntity.lastTickPosX;
        entity.lastTickPosY = cameraEntity.lastTickPosY;
        entity.lastTickPosZ = cameraEntity.lastTickPosZ;
        entity.setPosition(cameraEntity.posX, cameraEntity.posY, cameraEntity.posZ);
    }

    private float getMaxScroll() {
        return Math.max(0, entries.size() * ENTRY_HEIGHT - listH);
    }

    private void ensureVisible(int index) {
        int itemTop = index * ENTRY_HEIGHT;
        int itemBottom = itemTop + ENTRY_HEIGHT;

        if (itemTop < scrollOffset) {
            scrollOffset = itemTop;
            return;
        }

        if (itemBottom > scrollOffset + listH) scrollOffset = itemBottom - listH;
    }

    private int getEntryIndexAt(int mouseX, int mouseY) {
        if (mouseX < listX || mouseX > listX + listW || mouseY < listY || mouseY > listY + listH) return -1;

        int relativeY = mouseY - listY + (int) scrollOffset;
        return relativeY / ENTRY_HEIGHT;
    }

    private int getEntryY(int index) {
        return listY + index * ENTRY_HEIGHT - (int) scrollOffset;
    }

    private int getEntryButtonX(EntryButtonSpec button) {
        return listX + listW - button.width - 3;
    }

    private boolean isInBounds(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private String elideText(FontRenderer font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";

        String elidedText = font.trimStringToWidth(text, maxWidth);
        if (!elidedText.equals(text)) elidedText += "...";

        return elidedText;
    }

    protected static final class EntryButtonSpec {
        private final String label;
        private final int width;
        private final int backgroundColor;
        private final int hoveredBackgroundColor;
        private final int textColor;

        protected EntryButtonSpec(String label, int width, int backgroundColor, int hoveredBackgroundColor,
                int textColor) {
            this.label = label;
            this.width = width;
            this.backgroundColor = backgroundColor;
            this.hoveredBackgroundColor = hoveredBackgroundColor;
            this.textColor = textColor;
        }
    }
}