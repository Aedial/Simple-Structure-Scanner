package com.simplestructurescanner.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import com.simplestructurescanner.capture.StructureCaptureExclusions;
import com.simplestructurescanner.capture.StructureCaptureSummary.EntityInstance;


/**
 * Aggregated entity exclusion window for the capture preview.
 */
public class GuiCaptureEntitiesWindow extends AbstractEntityBrowserWindow<GuiCaptureEntitiesWindow.EntityGroup> {

    private final StructureCaptureExclusions exclusions;

    public GuiCaptureEntitiesWindow(GuiScreen parent, List<EntityInstance> entities,
            StructureCaptureExclusions exclusions) {
        super(parent, aggregateEntities(entities));
        this.exclusions = exclusions;
    }

    @Override
    protected String getTitle() {
        return I18n.format("gui.structurescanner.capture.entities.title");
    }

    @Override
    protected String getEmptyMessage() {
        return I18n.format("gui.structurescanner.capture.entities.empty");
    }

    @Override
    protected String getFooterText() {
        return I18n.format("gui.structurescanner.entities.count", getTotalEntityCount());
    }

    @Override
    protected List<String> getFooterTooltipLines() {
        return Collections.singletonList(I18n.format("gui.structurescanner.capture.entities.types", getEntries().size()));
    }

    @Override
    protected ResourceLocation getEntityId(EntityGroup entry) {
        return entry.entityId;
    }

    @Override
    protected String getListEntryName(EntityGroup entry) {
        return formatEntityName(entry.entityId);
    }

    @Override
    protected String getListEntryTrailingText(EntityGroup entry) {
        return " x" + entry.count;
    }

    @Override
    protected int getEntryTextColor(EntityGroup entry, boolean selected, boolean hovered) {
        if (selected) return 0xFFFFFF;
        if (hovered) return 0xFFFFAA;
        if (isGroupExcluded(entry)) return 0xFFBBBB;

        return 0xCCCCCC;
    }

    @Override
    protected int getEntryMarkerColor(EntityGroup entry) {
        return isGroupExcluded(entry) ? 0xCCFF6666 : -1;
    }

    @Override
    protected EntryButtonSpec getEntryButton(EntityGroup entry, FontRenderer font) {
        boolean excluded = isGroupExcluded(entry);
        String label = excluded
            ? I18n.format("gui.structurescanner.capture.include")
            : I18n.format("gui.structurescanner.capture.exclude");
        int width = Math.max(52, font.getStringWidth(label) + 8);
        int backgroundColor = excluded ? 0x60338833 : 0x60883333;
        int hoveredBackgroundColor = excluded ? 0x8055AA55 : 0x80AA5555;
        int textColor = excluded ? 0xEEFFEE : 0xFFEEEE;

        return new EntryButtonSpec(label, width, backgroundColor, hoveredBackgroundColor, textColor);
    }

    @Override
    protected void onEntryButtonClicked(EntityGroup entry, int index) {
        setGroupExcluded(entry, !isGroupExcluded(entry));
    }

    @Override
    protected boolean handleSelectedEntryAction(EntityGroup entry, int index) {
        setGroupExcluded(entry, !isGroupExcluded(entry));
        return true;
    }

    @Override
    protected int drawViewerDetails(EntityGroup entry, FontRenderer font, int textY) {
        boolean excluded = isGroupExcluded(entry);

        font.drawString(I18n.format("gui.structurescanner.capture.entities.count", entry.count), getViewerX() + 3, textY, 0xCCCCCC);
        textY += 12;
        font.drawString(
            excluded ? I18n.format("gui.structurescanner.capture.excludedTooltip") : I18n.format("gui.structurescanner.capture.entities.included"),
            getViewerX() + 3,
            textY,
            excluded ? 0xFFAAAA : 0xAAFFAA
        );

        return textY + 8;
    }

    @Override
    protected List<String> getEntryTooltipLines(EntityGroup entry) {
        List<String> tooltipLines = new ArrayList<>();
        tooltipLines.add(entry.entityId.toString());
        tooltipLines.add(I18n.format("gui.structurescanner.capture.entities.count", entry.count));
        if (isGroupExcluded(entry)) tooltipLines.add(I18n.format("gui.structurescanner.capture.excludedTooltip"));

        return tooltipLines;
    }

    private boolean isGroupExcluded(EntityGroup group) {
        return exclusions.isEntityExcluded(group.entityId);
    }

    private void setGroupExcluded(EntityGroup group, boolean excluded) {
        exclusions.setEntityExcluded(group.entityId, excluded);
    }

    private int getTotalEntityCount() {
        int total = 0;
        for (EntityGroup group : getEntries()) total += group.count;

        return total;
    }

    private static List<EntityGroup> aggregateEntities(List<EntityInstance> entityInstances) {
        LinkedHashMap<ResourceLocation, EntityGroup> groupedEntities = new LinkedHashMap<>();
        for (EntityInstance entity : entityInstances) {
            ResourceLocation entityId = entity.getEntityId();
            EntityGroup group = groupedEntities.computeIfAbsent(entityId, k -> new EntityGroup(entityId));
            group.add(entity);
        }

        List<EntityGroup> aggregatedEntities = new ArrayList<>(groupedEntities.values());
        aggregatedEntities.sort((first, second) -> {
            int countCompare = Integer.compare(second.count, first.count);
            if (countCompare != 0)
                return countCompare;

            return first.entityId.toString().compareTo(second.entityId.toString());
        });

        return aggregatedEntities;
    }

    static public class EntityGroup {
        private final ResourceLocation entityId;
        private int count;

        private EntityGroup(ResourceLocation entityId) {
            this.entityId = entityId;
        }

        private void add(EntityInstance entity) {
            count++;
        }
    }
}