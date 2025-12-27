package com.simplestructurescanner.client.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.simplestructurescanner.client.ClientSettings;
import com.simplestructurescanner.config.ModConfig;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureProviderRegistry;
import com.simplestructurescanner.searching.StructureSearchManager;


/**
 * Client event handler for rendering structure searching overlays and direction indicators.
 */
public class ClientRenderEvents {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;
        if (!ModConfig.isSearchEnabled()) return;

        // Process any pending search requests
        StructureSearchManager.processPendingSearches(mc.world, mc.player.getPosition());
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!ModConfig.isClientHudEnabled()) return;
        if (!ModConfig.isSearchEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (mc.gameSettings.showDebugInfo) return;

        Set<ResourceLocation> trackedIds = StructureSearchManager.getTrackedIds();
        if (trackedIds.isEmpty()) return;

        BlockPos playerPos = mc.player.getPosition();
        Map<ResourceLocation, StructureLocation> locations = StructureSearchManager.getAllLocations();

        // Build display lines
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        for (ResourceLocation id : trackedIds) {
            if (!ModConfig.isStructureAllowed(id.toString())) continue;

            StructureLocation loc = locations.get(id);

            // Get display name
            String name;
            if (ClientSettings.i18nNames) {
                StructureInfo info = StructureProviderRegistry.getStructureInfo(id);
                name = info != null ? info.getDisplayName() : id.getPath();
            } else {
                name = id.toString();
            }

            if (loc != null) {
                double distance = loc.getDistanceFrom(playerPos);

                // Check local whitelist/blacklist
                if (!ModConfig.isLocallyAllowed(id.toString(), distance)) continue;

                String distanceStr = StructureSearchManager.formatDistance(distance);
                lines.add(name + ": " + distanceStr);
            } else {
                // No location found yet - show "Searching..." status
                lines.add(name + ": " + I18n.format("gui.structurescanner.locate.searching"));
            }
            colors.add(StructureSearchManager.getColor(id));
        }

        if (lines.isEmpty()) return;

        // Get config values
        int paddingExternal = ModConfig.clientHudPaddingExternal;
        int paddingInternal = ModConfig.clientHudPaddingInternal;
        int lineSpacing = ModConfig.clientHudLineSpacing;

        // Calculate dimensions
        int lineHeight = mc.fontRenderer.FONT_HEIGHT;
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, mc.fontRenderer.getStringWidth(line));
        }

        int boxW = maxWidth + paddingInternal * 2 + 8;  // Extra 8 for color indicator
        int boxH = lines.size() * lineHeight + (lines.size() - 1) * lineSpacing + paddingInternal * 2;

        // Position based on HUD position setting
        ScaledResolution res = new ScaledResolution(mc);
        int screenW = res.getScaledWidth();
        int screenH = res.getScaledHeight();

        int boxX, boxY;
        ModConfig.HudPosition hudPos = ModConfig.getClientHudPosition();

        switch (hudPos) {
            case TOP_LEFT:
            default:
                boxX = paddingExternal;
                boxY = paddingExternal;
                break;
            case TOP_CENTER:
                boxX = (screenW - boxW) / 2;
                boxY = paddingExternal;
                break;
            case TOP_RIGHT:
                boxX = screenW - boxW - paddingExternal;
                boxY = paddingExternal;
                break;
            case CENTER_LEFT:
                boxX = paddingExternal;
                boxY = (screenH - boxH) / 2;
                break;
            case CENTER:
                boxX = (screenW - boxW) / 2;
                boxY = (screenH - boxH) / 2;
                break;
            case CENTER_RIGHT:
                boxX = screenW - boxW - paddingExternal;
                boxY = (screenH - boxH) / 2;
                break;
            case BOTTOM_LEFT:
                boxX = paddingExternal;
                boxY = screenH - boxH - paddingExternal;
                break;
            case BOTTOM_CENTER:
                boxX = (screenW - boxW) / 2;
                boxY = screenH - boxH - paddingExternal;
                break;
            case BOTTOM_RIGHT:
                boxX = screenW - boxW - paddingExternal;
                boxY = screenH - boxH - paddingExternal;
                break;
        }

        // Draw box with border
        int bgColor = 0xC0101010;
        int borderColor = 0xFF404040;

        // Border (1px)
        Gui.drawRect(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, borderColor);
        // Background
        Gui.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, bgColor);

        // Draw text with color indicators
        int textX = boxX + paddingInternal;
        int textY = boxY + paddingInternal;

        for (int i = 0; i < lines.size(); i++) {
            // Draw color indicator (small square)
            int color = colors.get(i) | 0xFF000000;
            Gui.drawRect(textX, textY + 1, textX + 4, textY + lineHeight - 1, color);

            // Draw text
            mc.fontRenderer.drawStringWithShadow(lines.get(i), textX + 8, textY, 0xFFFFFF);
            textY += lineHeight + lineSpacing;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!ModConfig.isSearchEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.world == null) return;
        if (mc.gameSettings.showDebugInfo) return;

        Map<ResourceLocation, StructureLocation> locations = StructureSearchManager.getAllLocations();
        if (locations.isEmpty()) return;

        float partialTicks = event.getPartialTicks();

        // Get player's interpolated position
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-playerX, -playerY, -playerZ);

        for (Map.Entry<ResourceLocation, StructureLocation> entry : locations.entrySet()) {
            ResourceLocation id = entry.getKey();
            StructureLocation loc = entry.getValue();

            if (!ModConfig.isStructureAllowed(id.toString())) continue;
            if (loc == null) continue;

            BlockPos structurePos = loc.getPosition();
            double distance = Math.sqrt(player.getDistanceSq(structurePos));

            // Check local whitelist/blacklist
            if (!ModConfig.isLocallyAllowed(id.toString(), distance)) continue;

            int color = StructureSearchManager.getColor(id);
            draw3DTriangle(player, structurePos, color, distance, partialTicks);
        }

        GlStateManager.popMatrix();
    }

    /**
     * Draws a 3D triangle pointing towards the structure with distance text above it.
     */
    private void draw3DTriangle(EntityPlayer player, BlockPos target, int color, double distance, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();

        // Calculate direction from player to target
        double dx = target.getX() + 0.5 - player.posX;
        double dz = target.getZ() + 0.5 - player.posZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Normalize direction
        double dirX = dx / horizontalDist;
        double dirZ = dz / horizontalDist;

        // Position the triangle a fixed distance from the player (5 blocks in front)
        double triangleDistance = Math.min(5.0, horizontalDist - 1);
        if (triangleDistance < 1) triangleDistance = 1;

        double triangleX = player.posX + dirX * triangleDistance;
        double triangleY = player.posY + player.getEyeHeight() - 0.2;
        double triangleZ = player.posZ + dirZ * triangleDistance;

        // Calculate rotation angle
        float angle = (float) Math.toDegrees(Math.atan2(dz, dx));

        // Extract color components
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        // Draw the triangle
        GlStateManager.pushMatrix();
        GlStateManager.translate(triangleX, triangleY, triangleZ);
        GlStateManager.rotate(-angle + 90, 0, 1, 0);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();
        GlStateManager.disableDepth();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // Triangle pointing forward
        float size = 0.3f;
        float alpha = 0.8f;

        buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        // Front face
        buffer.pos(0, size * 0.5, 0).color(r, g, b, alpha).endVertex();
        buffer.pos(-size * 0.5, -size * 0.5, 0).color(r, g, b, alpha).endVertex();
        buffer.pos(size * 0.5, -size * 0.5, 0).color(r, g, b, alpha).endVertex();

        // Point forward
        buffer.pos(0, 0, size).color(r, g, b, alpha).endVertex();
        buffer.pos(-size * 0.5, -size * 0.5, 0).color(r, g, b, alpha).endVertex();
        buffer.pos(0, size * 0.5, 0).color(r, g, b, alpha).endVertex();

        buffer.pos(0, 0, size).color(r, g, b, alpha).endVertex();
        buffer.pos(0, size * 0.5, 0).color(r, g, b, alpha).endVertex();
        buffer.pos(size * 0.5, -size * 0.5, 0).color(r, g, b, alpha).endVertex();

        buffer.pos(0, 0, size).color(r, g, b, alpha).endVertex();
        buffer.pos(size * 0.5, -size * 0.5, 0).color(r, g, b, alpha).endVertex();
        buffer.pos(-size * 0.5, -size * 0.5, 0).color(r, g, b, alpha).endVertex();

        tessellator.draw();

        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();

        // Draw distance text above the triangle
        GlStateManager.rotate(angle - 90, 0, 1, 0);  // Undo rotation for text

        String distanceStr = StructureSearchManager.formatDistance(distance);

        // Face the player
        float playerYaw = player.rotationYaw;
        float playerPitch = player.rotationPitch;

        GlStateManager.rotate(-playerYaw + 180, 0, 1, 0);
        GlStateManager.rotate(playerPitch, 1, 0, 0);
        GlStateManager.scale(-0.025f, -0.025f, 0.025f);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int textWidth = mc.fontRenderer.getStringWidth(distanceStr);
        mc.fontRenderer.drawString(distanceStr, -textWidth / 2, -20, color | 0xFF000000);

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();

        GlStateManager.popMatrix();
    }
}
