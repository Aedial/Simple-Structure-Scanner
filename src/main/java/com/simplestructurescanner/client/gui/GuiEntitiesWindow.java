package com.simplestructurescanner.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

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

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;


/**
 * Modal popup window that displays structure entities.
 * Shows a scrollable list on the left and entity viewer on the right.
 */
public class GuiEntitiesWindow {
    private static final int HEADER_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 16;
    private static final int LIST_WIDTH = 150;
    private static final int ENTRY_HEIGHT = 14;

    private final GuiScreen parent;
    private final ResourceLocation structureId;
    private final StructureInfo structureInfo;

    private boolean visible = false;
    private boolean hiddenForNavigation = false;
    private int windowX, windowY, windowW, windowH;

    // Entity list
    private List<EntityEntry> entities;
    private int selectedIndex = -1;
    private float scrollOffset = 0;
    private int listX, listY, listW, listH;

    // Entity viewer
    private int viewerX, viewerY, viewerW, viewerH;

    // Hover state
    private int hoveredListIndex = -1;
    private boolean hoveringTotal = false;

    // Entity caching and error searching
    private Map<ResourceLocation, Entity> entityCache = new HashMap<>();
    private Set<ResourceLocation> entitiesWithRenderErrors = new HashSet<>();
    private static Map<ResourceLocation, String> entityNameCache = new HashMap<>();

    public GuiEntitiesWindow(GuiScreen parent, ResourceLocation structureId, StructureInfo structureInfo) {
        this.parent = parent;
        this.structureId = structureId;
        this.structureInfo = structureInfo;
        this.entities = structureInfo.getEntities();
    }

    public void show() {
        visible = true;
        hiddenForNavigation = false;
        selectedIndex = entities.isEmpty() ? -1 : 0;
        calculateLayout();
    }

    public void hide() {
        visible = false;
        hiddenForNavigation = false;
        hoveredListIndex = -1;
        hoveringTotal = false;
    }

    private void hideForNavigation() {
        visible = false;
        hiddenForNavigation = true;
        hoveredListIndex = -1;
        hoveringTotal = false;
    }

    public boolean restoreIfHiddenForNavigation() {
        if (hiddenForNavigation) {
            visible = true;
            hiddenForNavigation = false;
            calculateLayout();

            return true;
        }

        return false;
    }

    public boolean isHiddenForNavigation() {
        return hiddenForNavigation;
    }

    public boolean isVisible() {
        return visible;
    }

    private void calculateLayout() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        // Calculate window size (wider to accommodate split view)
        windowW = Math.min(400, screenW - 40);
        windowH = Math.min(300, screenH - 40);

        windowX = (screenW - windowW) / 2;
        windowY = (screenH - windowH) / 2;

        // List area (left side)
        listX = windowX + 6;
        listY = windowY + HEADER_HEIGHT + 5;
        listW = LIST_WIDTH;
        listH = windowH - HEADER_HEIGHT - FOOTER_HEIGHT - 10;

        // Viewer area (right side)
        viewerX = listX + listW + 10;
        viewerY = listY;
        viewerW = windowW - listW - 22;
        viewerH = listH;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;

        if (mouseX < windowX || mouseX > windowX + windowW ||
            mouseY < windowY || mouseY > windowY + windowH) {
            hide();

            return true;
        }

        // Check list clicks
        if (mouseX >= listX && mouseX <= listX + listW &&
            mouseY >= listY && mouseY <= listY + listH) {
            int relativeY = mouseY - listY + (int) scrollOffset;
            int index = relativeY / ENTRY_HEIGHT;

            if (index >= 0 && index < entities.size()) selectedIndex = index;
        }

        return true;
    }

    public boolean handleKey(int keyCode) {
        if (!visible) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            hide();

            return true;
        }

        // Arrow keys for list navigation
        if (keyCode == Keyboard.KEY_UP && selectedIndex > 0) {
            selectedIndex--;
            ensureVisible(selectedIndex);

            return true;
        }

        if (keyCode == Keyboard.KEY_DOWN && selectedIndex < entities.size() - 1) {
            selectedIndex++;
            ensureVisible(selectedIndex);

            return true;
        }

        return false;
    }

    public boolean handleMouseInput(int mouseX, int mouseY) {
        if (!visible) return false;
        if (!isMouseOver(mouseX, mouseY)) return false;

        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            scrollOffset -= wheel * 0.25f;
            float maxScroll = getMaxScroll();
            if (scrollOffset < 0) scrollOffset = 0;
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;

            return true;
        }

        return false;
    }

    private float getMaxScroll() {
        int contentHeight = entities.size() * ENTRY_HEIGHT;

        return Math.max(0, contentHeight - listH);
    }

    private void ensureVisible(int index) {
        int itemTop = index * ENTRY_HEIGHT;
        int itemBottom = itemTop + ENTRY_HEIGHT;

        if (itemTop < scrollOffset) {
            scrollOffset = itemTop;
        } else if (itemBottom > scrollOffset + listH) {
            scrollOffset = itemBottom - listH;
        }
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
        String title = I18n.format("gui.structurescanner.entities.title", structureInfo.getDisplayName());
        String elidedTitle = font.trimStringToWidth(title, windowW - 16);
        if (!elidedTitle.equals(title)) elidedTitle += "…";
        font.drawString(elidedTitle, windowX + 6, windowY + 6, 0xFFFFFF);

        // Draw footer
        hoveringTotal = false;
        int footerY = windowY + windowH - FOOTER_HEIGHT + 2;
        String footer = I18n.format("gui.structurescanner.entities.count", entities.size());

        if (mouseX >= windowX + 6 && mouseX <= windowX + 6 + font.getStringWidth(footer) &&
            mouseY >= footerY && mouseY <= footerY + 10) {
            hoveringTotal = true;
        }

        font.drawString(footer, windowX + 6, footerY, hoveringTotal ? 0xFFFFAA : 0xCCCCCC);

        // Draw content
        if (entities.isEmpty()) {
            String msg = I18n.format("gui.structurescanner.entities.noEntities");
            int textW = font.getStringWidth(msg);
            font.drawString(msg, windowX + (windowW - textW) / 2, listY + 20, 0xFF6666);
        } else {
            drawEntityList(mouseX, mouseY);
            drawEntityViewer(mouseX, mouseY, partialTicks);
        }
    }

    private Entity getEntityInstance(ResourceLocation entityId) {
        if (entityCache.containsKey(entityId)) return entityCache.get(entityId);

        try {
            Entity entity = EntityList.createEntityByIDFromName(entityId, Minecraft.getMinecraft().world);
            entityCache.put(entityId, entity);

            return entity;
        } catch (Exception e) {
            entityCache.put(entityId, null);

            return null;
        }
    }

    private String formatEntityName(ResourceLocation id) {
        if (id == null) return "";
        if (entityNameCache.containsKey(id)) return entityNameCache.get(id);

        Entity entity = getEntityInstance(id);
        if (entity != null) {
            String name = entity.getDisplayName().getUnformattedText();
            entityNameCache.put(id, name);
            return name;
        }

        // Fallback for modded entities missing translation mapping
        String[] parts = id.toString().split(":" , 2);
        String domain = parts.length > 0 ? parts[0] : "minecraft";
        String path = parts.length > 1 ? parts[1] : parts[0];
        String altKey = "entity." + domain + "." + path + ".name";
        if (I18n.hasKey(altKey)) {
            String name = I18n.format(altKey);
            entityNameCache.put(id, name);
            return name;
        }

        entityNameCache.put(id, id.toString());
        return id.toString();
    }

    private void drawEntityList(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        hoveredListIndex = -1;

        // Draw list background
        Gui.drawRect(listX, listY, listX + listW, listY + listH, 0x40000000);

        int visibleStart = (int) (scrollOffset / ENTRY_HEIGHT);
        int visibleEnd = Math.min(entities.size(), visibleStart + (listH / ENTRY_HEIGHT) + 2);

        for (int i = visibleStart; i < visibleEnd; i++) {
            int entryY = listY + (i * ENTRY_HEIGHT) - (int) scrollOffset;

            if (entryY + ENTRY_HEIGHT < listY || entryY > listY + listH) continue;

            EntityEntry entry = entities.get(i);
            boolean isSelected = i == selectedIndex;
            boolean isHovered = mouseX >= listX && mouseX <= listX + listW &&
                               mouseY >= entryY && mouseY <= entryY + ENTRY_HEIGHT;

            if (isHovered) hoveredListIndex = i;

            // Draw background
            if (isSelected) {
                Gui.drawRect(listX, entryY, listX + listW, entryY + ENTRY_HEIGHT, 0x60FFFFFF);
            } else if (isHovered) {
                Gui.drawRect(listX, entryY, listX + listW, entryY + ENTRY_HEIGHT, 0x30FFFFFF);
            }

            // Draw entity name and count
            String displayText = formatEntityName(entry.entityId);
            if (entry.count > 1) {
                displayText += " x" + entry.count;
            }
            if (entry.spawner) {
                displayText += " \u00A77[\u00A7aS\u00A77]";  // [S] for spawner
            }

            String elidedText = font.trimStringToWidth(displayText, listW - 6);
            if (!elidedText.equals(displayText)) elidedText += "…";

            int textColor = isSelected ? 0xFFFFFF : (isHovered ? 0xFFFFAA : 0xCCCCCC);
            font.drawString(elidedText, listX + 3, entryY + 3, textColor);
        }

        // Draw scrollbar if needed
        float maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarX = listX + listW - 3;
            int scrollbarH = Math.max(20, (int) ((float) listH / (listH + maxScroll) * listH));
            int scrollbarY = listY + (int) ((scrollOffset / maxScroll) * (listH - scrollbarH));

            Gui.drawRect(scrollbarX, listY, scrollbarX + 3, listY + listH, 0x40FFFFFF);
            Gui.drawRect(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarH, 0xA0FFFFFF);
        }
    }

    private void drawEntityViewer(int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        // Draw viewer background
        Gui.drawRect(viewerX, viewerY, viewerX + viewerW, viewerY + viewerH, 0x40000000);

        if (selectedIndex < 0 || selectedIndex >= entities.size()) return;

        EntityEntry entry = entities.get(selectedIndex);
        String entityName = formatEntityName(entry.entityId);

        // Draw entity name and ID
        int textY = viewerY + 5;

        String nameLabel = I18n.format("gui.structurescanner.entities.entityName", entityName);
        String elidedName = font.trimStringToWidth(nameLabel, viewerW - 6);
        if (!elidedName.equals(nameLabel)) elidedName += "…";
        font.drawString(elidedName, viewerX + 3, textY, 0xFFFFFF);
        textY += 12;

        String idLabel = I18n.format("gui.structurescanner.entities.entityId", entry.entityId.toString());
        String elidedId = font.trimStringToWidth(idLabel, viewerW - 6);
        if (!elidedId.equals(idLabel)) elidedId += "…";
        font.drawString(elidedId, viewerX + 3, textY, 0xCCCCCC);
        textY += 12;

        if (entry.spawner) {
            String spawnerLabel = I18n.format("gui.structurescanner.entities.spawner");
            font.drawString(spawnerLabel, viewerX + 3, textY, 0x55FF55);
            textY += 12;
        }
        textY += 8;

        // Draw entity preview
        int previewSize = Math.min(viewerW - 10, viewerH - textY + viewerY - 10);
        if (previewSize > 30) {
            int previewX = viewerX + (viewerW - previewSize) / 2;
            int previewY = textY + 5;

            float entityRotation = (mc.getSystemTime() % 10000L) / 10000.0f * 360.0f;
            drawEntityPreview(entry.entityId, previewX, previewY, previewSize, entityRotation);
        }
    }

    private void drawEntityPreview(ResourceLocation entityId, int x, int y, int size, float rotation) {
        Entity entity = getEntityInstance(entityId);
        if (entity == null || size <= 0) {
            // Draw background with placeholder
            Gui.drawRect(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF404040);
            Gui.drawRect(x, y, x + size, y + size, 0xFF202020);
            String msg = "?";
            int textW = Minecraft.getMinecraft().fontRenderer.getStringWidth(msg);
            Minecraft.getMinecraft().fontRenderer.drawString(msg, x + (size - textW) / 2, y + (size - 8) / 2, 0x888888);

            return;
        }

        // Draw background with border
        Gui.drawRect(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF404040);
        Gui.drawRect(x, y, x + size, y + size, 0xFF202020);

        // Calculate scale based on entity's visual model size
        float maxDimension = Math.max(1.0f, Math.max(entity.height, entity.width));
        float scale = size / maxDimension / 2f;

        // Center position of the preview box
        int centerX = x + size / 2;
        int centerY = y + size / 2;

        GlStateManager.pushMatrix();
        GlStateManager.color(1f, 1f, 1f);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 50F);
        GlStateManager.scale(-scale, scale, scale);
        GlStateManager.rotate(180F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(135F, 0.0F, 1.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.rotate(-135F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(20F, 1.0F, 0.0F, 0.0F); // isometric tilt
        GlStateManager.rotate(rotation, 0.0F, 1.0F, 0.0F);

        // Translate entity so its bounding box center aligns with the preview center
        // Entity origin (0,0,0) is at their feet, so we shift up by half their height
        float verticalOffset = entity.height / 2.0f;
        // Also apply entity's intrinsic Y offset (e.g., for hanging entities)
        verticalOffset += (float) entity.getYOffset();
        GlStateManager.translate(0.0F, -verticalOffset, 0.0F);
        Minecraft.getMinecraft().getRenderManager().playerViewY = 180F;

        try {
            if (!entitiesWithRenderErrors.contains(entityId)) {
                Minecraft.getMinecraft().getRenderManager().renderEntity(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, false);
            }
        } catch (Throwable t) {
            if (!entitiesWithRenderErrors.contains(entityId)) {
                entitiesWithRenderErrors.add(entityId);
                SimpleStructureScanner.LOGGER.warn("Failed to render entity preview for " + entityId + ": " + t.getMessage());
            }
        }

        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();

        GlStateManager.disableRescaleNormal();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
        GlStateManager.disableColorMaterial();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    public void drawTooltips(int mouseX, int mouseY) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();

        // List entry tooltip - show full entity ID
        if (hoveredListIndex >= 0 && hoveredListIndex < entities.size()) {
            EntityEntry entry = entities.get(hoveredListIndex);
            String tooltip = entry.entityId.toString();

            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 500);
            drawHoveringText(Collections.singletonList(tooltip), mouseX, mouseY, mc.fontRenderer);
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
