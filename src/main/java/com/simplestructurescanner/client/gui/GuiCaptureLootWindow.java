package com.simplestructurescanner.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.simplestructurescanner.capture.StructureCaptureExclusions;
import com.simplestructurescanner.capture.StructureCaptureSummary.ContainerSummary;
import com.simplestructurescanner.structure.StructureNBTParser;


/**
 * Row-based container exclusion list for the capture preview.
 */
public class GuiCaptureLootWindow extends GuiCaptureToggleListWindow<ContainerSummary> {

    private final List<ContainerSummary> containers;
    private final StructureCaptureExclusions exclusions;

    public GuiCaptureLootWindow(GuiScreen parent, List<ContainerSummary> containers,
            StructureCaptureExclusions exclusions) {
        super(parent);
        this.containers = containers;
        this.exclusions = exclusions;
    }

    @Override
    protected List<ContainerSummary> getEntries() {
        return containers;
    }

    @Override
    protected String getTitle() {
        return I18n.format("gui.structurescanner.capture.loot.title");
    }

    @Override
    protected String getEmptyMessage() {
        return I18n.format("gui.structurescanner.capture.loot.empty");
    }

    @Override
    protected String getRowLabel(ContainerSummary entry) {
        return I18n.format(
            "gui.structurescanner.capture.loot.row",
            getContainerName(entry),
            entry.getContainerCount(),
            getContainerDetails(entry)
        );
    }

    @Override
    protected List<String> getTooltipLines(ContainerSummary entry) {
        List<String> lines = new ArrayList<>();
        lines.add(getContainerName(entry));
        lines.add(getContainerDetails(entry));
        lines.add(I18n.format("gui.structurescanner.capture.loot.containerCount", entry.getContainerCount()));
        if (isEntryExcluded(entry)) lines.add(I18n.format("gui.structurescanner.capture.excludedTooltip"));

        return lines;
    }

    @Override
    protected boolean isEntryExcluded(ContainerSummary entry) {
        return exclusions.isContainerExcluded(entry.getKey());
    }

    @Override
    protected void setEntryExcluded(ContainerSummary entry, boolean excluded) {
        exclusions.setContainerExcluded(entry.getKey(), excluded);
    }

    private String getContainerName(ContainerSummary entry) {
        ItemStack displayStack = StructureNBTParser.createDisplayStack(entry.getBlockState());
        if (!displayStack.isEmpty()) return displayStack.getDisplayName();

        ResourceLocation blockId = entry.getBlockState().getBlock().getRegistryName();
        if (blockId != null) return blockId.toString();

        return I18n.format("gui.structurescanner.unknown");
    }

    private String getContainerDetails(ContainerSummary entry) {
        if (entry.getLootTableId() != null) return entry.getLootTableId().toString();
        if (entry.getTotalItemCount() > 0) {
            return I18n.format("gui.structurescanner.capture.loot.fixedItems", entry.getTotalItemCount());
        }

        return I18n.format("gui.structurescanner.capture.loot.emptyContainer");
    }
}