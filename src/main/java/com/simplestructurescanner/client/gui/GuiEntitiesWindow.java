package com.simplestructurescanner.client.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import com.simplestructurescanner.client.ClientTextResolver;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;


/**
 * Modal popup window that displays structure entities.
 * Shows a scrollable list on the left and entity viewer on the right.
 */
public class GuiEntitiesWindow extends AbstractEntityBrowserWindow<EntityEntry> {

    private final StructureInfo structureInfo;

    private boolean hiddenForNavigation;

    public GuiEntitiesWindow(GuiScreen parent, ResourceLocation structureId, StructureInfo structureInfo) {
        super(parent, structureInfo.getEntities());
        this.structureInfo = structureInfo;
    }

    @Override
    public void show() {
        hiddenForNavigation = false;
        super.show();
    }

    public boolean restoreIfHiddenForNavigation() {
        if (!hiddenForNavigation) return false;

        hiddenForNavigation = false;
        restoreVisibility();

        return true;
    }

    public boolean isHiddenForNavigation() {
        return hiddenForNavigation;
    }

    @Override
    protected String getTitle() {
        return I18n.format("gui.structurescanner.entities.title", ClientTextResolver.resolve(structureInfo.getDisplayName()));
    }

    @Override
    protected String getEmptyMessage() {
        return I18n.format("gui.structurescanner.entities.noEntities");
    }

    @Override
    protected String getFooterText() {
        return I18n.format("gui.structurescanner.entities.count", getEntries().size());
    }

    @Override
    protected ResourceLocation getEntityId(EntityEntry entry) {
        return entry.entityId;
    }

    @Override
    protected String getListEntryName(EntityEntry entry) {
        return formatEntityName(entry.entityId);
    }

    @Override
    protected String getListEntryTrailingText(EntityEntry entry) {
        String trailingText = "";
        if (entry.count > 1) trailingText += " x" + entry.count;
        if (entry.spawner) trailingText += " §7[§aS§7]";

        return trailingText;
    }

    @Override
    protected int drawViewerDetails(EntityEntry entry, FontRenderer font, int textY) {
        if (entry.spawner) {
            font.drawString(I18n.format("gui.structurescanner.entities.spawner"), getViewerX() + 3, textY, 0x55FF55);
            textY += 12;
        }

        return textY + 8;
    }

    @Override
    protected boolean shouldDrawOverlay() {
        return true;
    }

    @Override
    protected boolean useTitleShadow() {
        return true;
    }

    @Override
    protected int getWindowBorderColor() {
        return 0x80303030;
    }

    @Override
    protected int getWindowBackgroundColor() {
        return 0x801A1A1A;
    }
}
