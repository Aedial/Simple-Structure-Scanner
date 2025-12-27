package com.simplestructurescanner.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import com.simplestructurescanner.client.ClientSettings;
import com.simplestructurescanner.config.ModConfig;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureProviderRegistry;
import com.simplestructurescanner.searching.StructureSearchManager;


/**
 * Main GUI for the Structure Scanner.
 * Split design with structure list on the left and details on the right.
 */
public class GuiStructureScanner extends GuiScreen {
    private GuiTextField filterField;
    private StructureListWidget listWidget;
    private ResourceLocation selected;
    private StructureInfo selectedInfo;

    // Double-click detection
    private long lastClickTime = 0L;
    private ResourceLocation lastClickId = null;
    private static final long DOUBLE_CLICK_TIME = 500L;

    // Button bounds
    private int blocksButtonX, blocksButtonY, blocksButtonW, blocksButtonH;
    private boolean blocksButtonVisible = false;
    private int lootButtonX, lootButtonY, lootButtonW, lootButtonH;
    private boolean lootButtonVisible = false;
    private int entitiesButtonX, entitiesButtonY, entitiesButtonW, entitiesButtonH;
    private boolean entitiesButtonVisible = false;
    private int refreshButtonX, refreshButtonY, refreshButtonW, refreshButtonH;
    private boolean refreshButtonVisible = false;
    private int skipButtonX, skipButtonY, skipButtonW, skipButtonH;
    private boolean skipButtonVisible = false;

    // Modal windows
    private GuiBlocksWindow blocksWindow = null;
    private GuiLootWindow lootWindow = null;
    private GuiEntitiesWindow entitiesWindow = null;

    // Panel bounds
    private int panelMaxY = Integer.MAX_VALUE;

    private String getI18nButtonString() {
        return ClientSettings.i18nNames ? I18n.format("gui.structurescanner.i18nIDs.on") : I18n.format("gui.structurescanner.i18nIDs.off");
    }

    @Override
    public void initGui() {
        int leftWidth = Math.min(width / 2, 250);
        filterField = new GuiTextField(0, fontRenderer, 10, 10, leftWidth - 20, 14);
        filterField.setText(ModConfig.getClientFilterText());
        listWidget = new StructureListWidget(10, 30, leftWidth - 20, height - 70, fontRenderer, this);
        this.buttonList.clear();

        this.buttonList.add(new GuiButton(1, 10, height - 30, leftWidth - 20, 20, getI18nButtonString()));

        // Restore last selected structure
        String lastStructure = ModConfig.getClientLastSelectedStructure();
        if (lastStructure != null && !lastStructure.isEmpty()) {
            ResourceLocation id = new ResourceLocation(lastStructure);
            if (StructureProviderRegistry.getStructureInfo(id) != null) {
                selectStructure(id);
            }
        }

        // Restore modal windows if they were hidden for navigation
        if (blocksWindow != null) blocksWindow.restoreIfHiddenForNavigation();
        if (lootWindow != null) lootWindow.restoreIfHiddenForNavigation();
        if (entitiesWindow != null) entitiesWindow.restoreIfHiddenForNavigation();
    }

    public void selectStructure(ResourceLocation id) {
        this.selected = id;
        this.selectedInfo = id != null ? StructureProviderRegistry.getStructureInfo(id) : null;
        ModConfig.setClientLastSelectedStructure(id != null ? id.toString() : "");
    }

    public ResourceLocation getSelectedStructure() {
        return selected;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) {
            ClientSettings.setI18nNames(!ClientSettings.i18nNames);
            button.displayString = getI18nButtonString();
        }
    }

    @Override
    public void updateScreen() {
        filterField.updateCursorCounter();
        String newFilter = filterField.getText();
        listWidget.setFilter(newFilter);
        ModConfig.setClientFilterText(newFilter);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Handle modal windows first
        if (blocksWindow != null && blocksWindow.isVisible() && blocksWindow.handleKey(keyCode)) return;
        if (lootWindow != null && lootWindow.isVisible() && lootWindow.handleKey(keyCode)) return;
        if (entitiesWindow != null && entitiesWindow.isVisible() && entitiesWindow.handleKey(keyCode)) return;

        if (filterField.textboxKeyTyped(typedChar, keyCode)) return;
        if (listWidget.handleKey(keyCode)) return;

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Handle modal windows first
        if (blocksWindow != null && blocksWindow.isVisible() && blocksWindow.handleClick(mouseX, mouseY, mouseButton)) return;
        if (lootWindow != null && lootWindow.isVisible() && lootWindow.handleClick(mouseX, mouseY, mouseButton)) return;
        if (entitiesWindow != null && entitiesWindow.isVisible() && entitiesWindow.handleClick(mouseX, mouseY, mouseButton)) return;

        // Right-click on filter field clears it
        if (mouseButton == 1 &&
            mouseX >= filterField.x && mouseX <= filterField.x + filterField.width &&
            mouseY >= filterField.y && mouseY <= filterField.y + filterField.height) {
            filterField.setText("");
        }

        filterField.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0) {
            // Check button clicks
            if (blocksButtonVisible && isInBounds(mouseX, mouseY, blocksButtonX, blocksButtonY, blocksButtonW, blocksButtonH)) {
                openBlocksWindow();

                return;
            }
            if (lootButtonVisible && isInBounds(mouseX, mouseY, lootButtonX, lootButtonY, lootButtonW, lootButtonH)) {
                openLootWindow();

                return;
            }
            if (entitiesButtonVisible && isInBounds(mouseX, mouseY, entitiesButtonX, entitiesButtonY, entitiesButtonW, entitiesButtonH)) {
                openEntitiesWindow();

                return;
            }
            if (refreshButtonVisible && isInBounds(mouseX, mouseY, refreshButtonX, refreshButtonY, refreshButtonW, refreshButtonH)) {
                StructureSearchManager.refreshSearch(selected);

                return;
            }
            if (skipButtonVisible && isInBounds(mouseX, mouseY, skipButtonX, skipButtonY, skipButtonW, skipButtonH)) {
                StructureSearchManager.skipCurrent(selected);

                return;
            }

            // Check list widget
            listWidget.handleClick(mouseX, mouseY);
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean isInBounds(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void openBlocksWindow() {
        if (selected == null || selectedInfo == null) return;

        blocksWindow = new GuiBlocksWindow(this, selected, selectedInfo);
        blocksWindow.show();
    }

    private void openLootWindow() {
        if (selected == null || selectedInfo == null) return;

        lootWindow = new GuiLootWindow(this, selected, selectedInfo);
        lootWindow.show();
    }

    private void openEntitiesWindow() {
        if (selected == null || selectedInfo == null) return;

        entitiesWindow = new GuiEntitiesWindow(this, selected, selectedInfo);
        entitiesWindow.show();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (listWidget != null) listWidget.handleDrag(mouseX, mouseY);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (listWidget != null) listWidget.handleRelease();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        // Check if modal is blocking scroll
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

        if (blocksWindow != null && blocksWindow.isVisible() && blocksWindow.isMouseOver(mouseX, mouseY)) return;
        if (lootWindow != null && lootWindow.isVisible() && lootWindow.isMouseOver(mouseX, mouseY)) return;
        if (entitiesWindow != null && entitiesWindow.isVisible() && entitiesWindow.isMouseOver(mouseX, mouseY) && entitiesWindow.handleMouseInput(mouseX, mouseY)) return;

        int wheel = Mouse.getDWheel();
        if (wheel != 0) listWidget.handleScroll(wheel);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        // Check if any modal is blocking
        boolean modalBlocking = (blocksWindow != null && blocksWindow.isVisible() && blocksWindow.isMouseOver(mouseX, mouseY)) ||
                                (lootWindow != null && lootWindow.isVisible() && lootWindow.isMouseOver(mouseX, mouseY)) ||
                                (entitiesWindow != null && entitiesWindow.isVisible() && entitiesWindow.isMouseOver(mouseX, mouseY));

        int effectiveMouseX = modalBlocking ? -1 : mouseX;
        int effectiveMouseY = modalBlocking ? -1 : mouseY;

        filterField.drawTextBox();
        listWidget.draw(effectiveMouseX, effectiveMouseY);
        drawRightPanel(effectiveMouseX, effectiveMouseY, partialTicks);

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw modal windows on top
        if (blocksWindow != null && blocksWindow.isVisible()) {
            blocksWindow.draw(mouseX, mouseY, partialTicks);
            blocksWindow.drawTooltips(mouseX, mouseY);
        }
        if (lootWindow != null && lootWindow.isVisible()) {
            lootWindow.draw(mouseX, mouseY, partialTicks);
            lootWindow.drawTooltips(mouseX, mouseY);
        }
        if (entitiesWindow != null && entitiesWindow.isVisible()) {
            entitiesWindow.draw(mouseX, mouseY, partialTicks);
            entitiesWindow.drawTooltips(mouseX, mouseY);
        }
    }

    private void drawRightPanel(int mouseX, int mouseY, float partialTicks) {
        int leftWidth = Math.min(width / 2, 250);
        int panelX = leftWidth + 10;
        int panelY = 10;
        int panelW = width - panelX - 20;
        int panelH = height - 40;
        panelMaxY = panelY + panelH - 6;

        // Reset button visibility
        blocksButtonVisible = false;
        lootButtonVisible = false;
        entitiesButtonVisible = false;
        refreshButtonVisible = false;
        skipButtonVisible = false;

        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + panelH, 0x80000000);

        if (selected == null) {
            String msg = I18n.format("gui.structurescanner.noSelection");
            fontRenderer.drawString(msg, panelX + 10, panelY + 10, 0xAAAAAA);

            return;
        }

        int textX = panelX + 6;
        int textY = panelY + 6;
        int textW = panelW - 12;
        int color = 0xFFFFFF;

        // TODO: Structure viewer (will likely require significant work to render properly)

        // Structure name (localized)
        String displayName = selectedInfo != null ? selectedInfo.getDisplayName() : selected.getPath();
        String nameStr = I18n.format("gui.structurescanner.structureName", displayName);
        textY = drawElidedString(fontRenderer, nameStr, textX, textY, 14, textW, color);

        // Structure ID
        String idStr = I18n.format("gui.structurescanner.structureId", selected.toString());
        textY = drawElidedString(fontRenderer, idStr, textX, textY, 14, textW, 0xCCCCCC);

        // Mod origin (localized)
        String modNameKey = StructureProviderRegistry.getModNameForStructure(selected);
        String modName = I18n.format(modNameKey);
        String modStr = I18n.format("gui.structurescanner.modOrigin", modName);
        textY = drawElidedString(fontRenderer, modStr, textX, textY, 14, textW, 0xAADDFF);

        // Draw buttons
        int buttonY = textY;
        int buttonSpacing = 4;

        // Blocks button
        String blocksText = I18n.format("gui.structurescanner.blocksButton");
        blocksButtonW = fontRenderer.getStringWidth(blocksText) + 8;
        blocksButtonH = 12;
        blocksButtonX = textX;
        blocksButtonY = buttonY;
        blocksButtonVisible = true;

        boolean blocksHovered = isInBounds(mouseX, mouseY, blocksButtonX, blocksButtonY, blocksButtonW, blocksButtonH);
        drawButton(blocksButtonX, blocksButtonY, blocksButtonW, blocksButtonH, blocksText, blocksHovered);

        int nextButtonX = blocksButtonX + blocksButtonW + buttonSpacing;

        // Loot button
        String lootText = I18n.format("gui.structurescanner.lootButton");
        lootButtonW = fontRenderer.getStringWidth(lootText) + 8;
        lootButtonH = 12;
        lootButtonX = nextButtonX;
        lootButtonY = buttonY;
        lootButtonVisible = true;

        boolean lootHovered = isInBounds(mouseX, mouseY, lootButtonX, lootButtonY, lootButtonW, lootButtonH);
        drawButton(lootButtonX, lootButtonY, lootButtonW, lootButtonH, lootText, lootHovered);

        nextButtonX = lootButtonX + lootButtonW + buttonSpacing;

        // Entities button
        String entitiesText = I18n.format("gui.structurescanner.entitiesButton");
        entitiesButtonW = fontRenderer.getStringWidth(entitiesText) + 8;
        entitiesButtonH = 12;
        entitiesButtonX = nextButtonX;
        entitiesButtonY = buttonY;
        entitiesButtonVisible = true;

        boolean entitiesHovered = isInBounds(mouseX, mouseY, entitiesButtonX, entitiesButtonY, entitiesButtonW, entitiesButtonH);
        drawButton(entitiesButtonX, entitiesButtonY, entitiesButtonW, entitiesButtonH, entitiesText, entitiesHovered);

        textY += 14 + 12 + 4;

        // Structure size
        if (selectedInfo != null) {
            String sizeStr;
            if (selectedInfo.getSizeX() == 0 && selectedInfo.getSizeY() == 0 && selectedInfo.getSizeZ() == 0) {
                sizeStr = I18n.format("gui.structurescanner.structureSizeUnknown");
            } else {
                sizeStr = I18n.format("gui.structurescanner.structureSize",
                    selectedInfo.getSizeX(), selectedInfo.getSizeY(), selectedInfo.getSizeZ());
            }
            textY = drawElidedString(fontRenderer, sizeStr, textX, textY, 14, textW, 0xAAFFAA);
        }

        // Searchable status
        boolean canSearch = StructureProviderRegistry.canBeSearched(selected);
        String searchableKey = canSearch ? "gui.structurescanner.searchableYes" : "gui.structurescanner.searchableNo";
        String searchableStr = I18n.format(searchableKey);
        int searchableColor = canSearch ? 0x55FF55 : 0xFF5555;
        textY = drawElidedString(fontRenderer, searchableStr, textX, textY, 14, textW, searchableColor);

        // Draw refresh and skip buttons if structure is being tracked
        if (StructureSearchManager.isTracked(selected)) {
            textY += 6;

            String refreshText = I18n.format("gui.structurescanner.refreshButton");
            refreshButtonW = fontRenderer.getStringWidth(refreshText) + 8;
            refreshButtonH = 12;
            refreshButtonX = textX;
            refreshButtonY = textY;
            refreshButtonVisible = true;

            boolean refreshHovered = isInBounds(mouseX, mouseY, refreshButtonX, refreshButtonY, refreshButtonW, refreshButtonH);
            drawButton(refreshButtonX, refreshButtonY, refreshButtonW, refreshButtonH, refreshText, refreshHovered);

            String skipText = I18n.format("gui.structurescanner.skipButton");
            skipButtonW = fontRenderer.getStringWidth(skipText) + 8;
            skipButtonH = 12;
            skipButtonX = refreshButtonX + refreshButtonW + buttonSpacing;
            skipButtonY = textY;
            skipButtonVisible = true;

            boolean skipHovered = isInBounds(mouseX, mouseY, skipButtonX, skipButtonY, skipButtonW, skipButtonH);
            drawButton(skipButtonX, skipButtonY, skipButtonW, skipButtonH, skipText, skipHovered);

            textY += 14;

            // Show current skip offset
            int skipOffset = StructureSearchManager.getSkipOffset(selected);
            if (skipOffset > 0) {
                String resultStr = I18n.format("gui.structurescanner.locate.result", skipOffset + 1, "?");
                textY = drawElidedString(fontRenderer, resultStr, textX, textY, 14, textW, 0xAAAAFF);
            }
        }

        // TODO: Biomes list, dimension, rarity
    }

    private void drawButton(int x, int y, int w, int h, String text, boolean hovered) {
        int bgColor = hovered ? 0x60FFFFFF : 0x40FFFFFF;
        int textColor = hovered ? 0xFFFFAA : 0xCCCCCC;

        Gui.drawRect(x, y, x + w, y + h, bgColor);
        fontRenderer.drawString(text, x + 4, y + 2, textColor);
    }

    private int drawElidedString(FontRenderer renderer, String text, int x, int y, int lineHeight, int maxWidth, int color) {
        if (y + lineHeight > panelMaxY) return y;

        String elided = renderer.trimStringToWidth(text, maxWidth - renderer.getStringWidth("…"));
        if (!elided.equals(text)) elided += "…";

        renderer.drawString(elided, x, y, color);

        return y + lineHeight;
    }

    /**
     * Structure list widget with filtering and scrolling.
     */
    class StructureListWidget {
        private final int x, y, width, height;
        private final FontRenderer font;
        private final GuiStructureScanner parent;
        private final int entryHeight = 14;

        private List<ResourceLocation> allStructures;
        private List<ResourceLocation> filteredStructures;
        private String filter = "";
        private float scrollOffset = 0;
        private boolean isDragging = false;
        private int dragStartY;
        private float dragStartScroll;

        public StructureListWidget(int x, int y, int width, int height, FontRenderer font, GuiStructureScanner parent) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.font = font;
            this.parent = parent;
            refreshStructures();
        }

        private void refreshStructures() {
            allStructures = StructureProviderRegistry.getAllStructureIds();
            applyFilter();
        }

        public void setFilter(String filter) {
            if (!this.filter.equals(filter)) {
                this.filter = filter.toLowerCase();
                applyFilter();
            }
        }

        private void applyFilter() {
            filteredStructures = new ArrayList<>();

            for (ResourceLocation id : allStructures) {
                // Match against ID
                if (filter.isEmpty() ||
                    id.toString().toLowerCase().contains(filter) ||
                    id.getPath().toLowerCase().contains(filter)) {
                    filteredStructures.add(id);

                    continue;
                }

                // Also match against localized name
                StructureInfo info = StructureProviderRegistry.getStructureInfo(id);
                if (info != null) {
                    String localizedName = I18n.format(info.getDisplayName()).toLowerCase();
                    if (localizedName.contains(filter)) filteredStructures.add(id);
                }
            }

            // Clamp scroll
            float maxScroll = getMaxScroll();
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            if (scrollOffset < 0) scrollOffset = 0;
        }

        private float getMaxScroll() {
            int contentHeight = filteredStructures.size() * entryHeight;

            return Math.max(0, contentHeight - height);
        }

        public void handleScroll(int wheel) {
            scrollOffset -= wheel * 0.25f;
            float maxScroll = getMaxScroll();
            if (scrollOffset < 0) scrollOffset = 0;
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        }

        public void handleClick(int mouseX, int mouseY) {
            if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return;

            int relativeY = mouseY - y + (int) scrollOffset;
            int index = relativeY / entryHeight;

            if (index >= 0 && index < filteredStructures.size()) {
                ResourceLocation clickedId = filteredStructures.get(index);
                long currentTime = System.currentTimeMillis();

                // Check for double-click
                if (clickedId.equals(parent.lastClickId) &&
                    (currentTime - parent.lastClickTime) < DOUBLE_CLICK_TIME) {
                    // Double-click: toggle searching
                    if (StructureProviderRegistry.canBeSearched(clickedId) && ModConfig.isStructureAllowed(clickedId.toString())) {
                        StructureSearchManager.toggleTracking(clickedId);
                    }
                    parent.lastClickTime = 0L;
                    parent.lastClickId = null;
                } else {
                    // Single click: select
                    parent.selectStructure(clickedId);
                    parent.lastClickTime = currentTime;
                    parent.lastClickId = clickedId;
                }
            }

            isDragging = true;
            dragStartY = mouseY;
            dragStartScroll = scrollOffset;
        }

        public void handleDrag(int mouseX, int mouseY) {
            if (!isDragging) return;

            int deltaY = dragStartY - mouseY;
            scrollOffset = dragStartScroll + deltaY;

            float maxScroll = getMaxScroll();
            if (scrollOffset < 0) scrollOffset = 0;
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        }

        public void handleRelease() {
            isDragging = false;
        }

        public boolean handleKey(int keyCode) {
            if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
                int currentIndex = parent.selected != null ? filteredStructures.indexOf(parent.selected) : -1;

                if (keyCode == Keyboard.KEY_UP && currentIndex > 0) {
                    parent.selectStructure(filteredStructures.get(currentIndex - 1));
                    ensureVisible(currentIndex - 1);

                    return true;
                } else if (keyCode == Keyboard.KEY_DOWN && currentIndex < filteredStructures.size() - 1) {
                    parent.selectStructure(filteredStructures.get(currentIndex + 1));
                    ensureVisible(currentIndex + 1);

                    return true;
                }
            }

            return false;
        }

        private void ensureVisible(int index) {
            int itemTop = index * entryHeight;
            int itemBottom = itemTop + entryHeight;

            if (itemTop < scrollOffset) {
                scrollOffset = itemTop;
            } else if (itemBottom > scrollOffset + height) {
                scrollOffset = itemBottom - height;
            }
        }

        public void draw(int mouseX, int mouseY) {
            // FIXME: the list is not sorted
            Gui.drawRect(x, y, x + width, y + height, 0x80000000);

            int visibleStart = (int) (scrollOffset / entryHeight);
            int visibleEnd = Math.min(filteredStructures.size(), visibleStart + (height / entryHeight) + 2);

            for (int i = visibleStart; i < visibleEnd; i++) {
                int entryY = y + (i * entryHeight) - (int) scrollOffset;

                if (entryY + entryHeight < y || entryY > y + height) continue;

                ResourceLocation id = filteredStructures.get(i);
                boolean isSelected = id.equals(parent.selected);
                boolean isTracked = StructureSearchManager.isTracked(id);
                boolean isHovered = mouseX >= x && mouseX <= x + width &&
                                   mouseY >= entryY && mouseY <= entryY + entryHeight;

                // Draw background
                if (isSelected) {
                    Gui.drawRect(x, entryY, x + width, entryY + entryHeight, 0x60FFFFFF);
                } else if (isHovered) {
                    Gui.drawRect(x, entryY, x + width, entryY + entryHeight, 0x30FFFFFF);
                }

                // Draw searching star indicator
                int textStartX = x + 3;
                if (isTracked) {
                    int color = StructureSearchManager.getColor(id);
                    drawStar(x + 6, entryY + entryHeight / 2, 4, color | 0xFF000000);
                    textStartX = x + 14;
                }

                // Draw text - use localized name if i18n is enabled
                String displayName;
                if (ClientSettings.i18nNames) {
                    StructureInfo info = StructureProviderRegistry.getStructureInfo(id);
                    displayName = info != null ? I18n.format(info.getDisplayName()) : id.getPath();
                } else {
                    displayName = id.toString();
                }

                int availableWidth = width - (textStartX - x) - 3;
                String elidedName = font.trimStringToWidth(displayName, availableWidth);
                if (!elidedName.equals(displayName)) elidedName += "…";

                boolean canSearch = StructureProviderRegistry.canBeSearched(id);
                int textColor = isSelected ? 0xFFFFFF : (isHovered ? 0xFFFFAA : 0xCCCCCC);
                if (!canSearch) {
                    // Dim the color by reducing brightness
                    int r = (int)(((textColor >> 16) & 0xFF) / 1.5);
                    int g = (int)(((textColor >> 8) & 0xFF) / 1.5);
                    int b = (int)((textColor & 0xFF) / 1.5);
                    textColor = (r << 16) | (g << 8) | b;
                }

                font.drawString(elidedName, textStartX, entryY + 3, textColor);
            }

            // Draw scrollbar if needed
            float maxScroll = getMaxScroll();
            if (maxScroll > 0) {
                int scrollbarX = x + width - 3;
                int scrollbarH = Math.max(20, (int) ((float) height / (height + maxScroll) * height));
                int scrollbarY = y + (int) ((scrollOffset / maxScroll) * (height - scrollbarH));

                Gui.drawRect(scrollbarX, y, scrollbarX + 3, y + height, 0x40FFFFFF);
                Gui.drawRect(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarH, 0xA0FFFFFF);
            }
        }

        /**
         * Draws a 5-pointed star for searching indicator.
         */
        private void drawStar(float centerX, float centerY, float radius, int color) {
            float a = ((color >> 24) & 0xFF) / 255.0f;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            float innerRadius = radius * 0.4f;
            float[] outerAngles = new float[5];
            float[] innerAngles = new float[5];

            for (int i = 0; i < 5; i++) {
                outerAngles[i] = (float) (Math.PI / 2 + i * 2 * Math.PI / 5);
                innerAngles[i] = (float) (Math.PI / 2 + Math.PI / 5 + i * 2 * Math.PI / 5);
            }

            GL11.glPushMatrix();
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            buffer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(centerX, centerY, 0).color(r, g, b, a).endVertex();

            for (int i = 0; i < 5; i++) {
                float ox = centerX + (float) Math.cos(outerAngles[i]) * radius;
                float oy = centerY - (float) Math.sin(outerAngles[i]) * radius;
                float ix = centerX + (float) Math.cos(innerAngles[i]) * innerRadius;
                float iy = centerY - (float) Math.sin(innerAngles[i]) * innerRadius;

                buffer.pos(ox, oy, 0).color(r, g, b, a).endVertex();
                buffer.pos(ix, iy, 0).color(r, g, b, a).endVertex();
            }

            // Close the star
            float ox = centerX + (float) Math.cos(outerAngles[0]) * radius;
            float oy = centerY - (float) Math.sin(outerAngles[0]) * radius;
            buffer.pos(ox, oy, 0).color(r, g, b, a).endVertex();

            tessellator.draw();

            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GL11.glPopMatrix();
        }
    }
}
