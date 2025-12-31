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
        for (String line : lines) maxWidth = Math.max(maxWidth, mc.fontRenderer.getStringWidth(line));

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
            drawDirectionArrow(player, structurePos, color, distance, partialTicks);
        }
    }

    // ========== Arrow Rendering Constants ==========
    private static final float ARROW_BASE_DISTANCE = 0.6f;   // Base distance in front of camera
    private static final float ARROW_SPREAD_RADIUS = 0.1f;   // How far arrows spread from center based on target dir
    private static final float ARROW_LENGTH = 0.05f;         // Length of the arrow
    private static final float ARROW_WIDTH = 0.02f;          // Width of arrow base
    private static final float ARROW_THICKNESS = 0.01f;      // Thickness between top/bottom triangles
    private static final float MIN_PITCH_ANGLE = 10.0f;      // Minimum angle from horizontal (degrees)
    private static final float TEXT_SCALE = 0.0012f;         // Scale for distance text

    /**
     * Draws a 3D directional arrow pointing towards the target structure.
     * Arrow is positioned in front of the player, offset towards target direction.
     */
    private void drawDirectionArrow(EntityPlayer player, BlockPos target, int color, double distance, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();

        // Use interpolated player position for smooth rendering
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        double eyeY = playerY + player.getEyeHeight();

        // Interpolated camera rotation
        float cameraYaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
        float cameraPitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;

        // Direction to target (from player's eye)
        double dx = target.getX() + 0.5 - playerX;
        double dy = target.getY() + 0.5 - eyeY;
        double dz = target.getZ() + 0.5 - playerZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (totalDist < 1) return;

        // Calculate yaw and pitch to target
        float targetYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float targetPitch = (float) Math.toDegrees(Math.atan2(dy, horizontalDist));

        // Clamp pitch so arrow doesn't go fully horizontal
        if (Math.abs(targetPitch) < MIN_PITCH_ANGLE) targetPitch = targetPitch >= 0 ? MIN_PITCH_ANGLE : -MIN_PITCH_ANGLE;

        // Camera forward direction
        double camYawRad = Math.toRadians(cameraYaw);
        double camPitchRad = Math.toRadians(cameraPitch);
        double camForwardX = -Math.sin(camYawRad) * Math.cos(camPitchRad);
        double camForwardY = -Math.sin(camPitchRad);
        double camForwardZ = Math.cos(camYawRad) * Math.cos(camPitchRad);

        // Camera right direction (for horizontal offset)
        double camRightX = Math.cos(camYawRad);
        double camRightZ = Math.sin(camYawRad);

        // Base position: in front of camera
        double baseX = playerX + camForwardX * ARROW_BASE_DISTANCE;
        double baseY = eyeY + camForwardY * ARROW_BASE_DISTANCE;
        double baseZ = playerZ + camForwardZ * ARROW_BASE_DISTANCE;

        // Calculate relative yaw: difference between target direction and camera direction
        double relativeYaw = Math.toRadians(targetYaw - cameraYaw);
        double targetPitchRad = Math.toRadians(targetPitch);

        // Project onto camera's local axes using relative angle
        double rightOffset = Math.sin(relativeYaw) * ARROW_SPREAD_RADIUS;
        double forwardOffset = Math.cos(relativeYaw);

        // Offset the arrow position in camera space, then convert to world
        double offsetX = camRightX * rightOffset;
        double offsetZ = camRightZ * rightOffset;

        // Vertical: based on target pitch
        double offsetY = Math.sin(targetPitchRad) * ARROW_SPREAD_RADIUS;

        // If target is behind camera, push arrow further out to sides
        if (forwardOffset < 0) {
            double behindFactor = 1.0 + Math.abs(forwardOffset) * 0.5;
            offsetX *= behindFactor;
            offsetZ *= behindFactor;
        }

        double arrowX = baseX + offsetX;
        double arrowY = baseY + offsetY;
        double arrowZ = baseZ + offsetZ;

        // Extract color components
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float alpha = 0.95f;

        // Darker shade for sides
        float rd = r * 0.6f;
        float gd = g * 0.6f;
        float bd = b * 0.6f;

        // Translate to render coordinates (relative to player position)
        double renderX = arrowX - playerX;
        double renderY = arrowY - playerY;
        double renderZ = arrowZ - playerZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(renderX, renderY, renderZ);

        // Rotate arrow to point towards target
        GlStateManager.rotate(180 + targetYaw, 0, 1, 0);
        GlStateManager.rotate(targetPitch, 1, 0, 0);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();
        GlStateManager.disableDepth();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        // Arrow points towards -Z (after rotation points to target)
        float halfThick = ARROW_THICKNESS / 2;
        float len = ARROW_LENGTH;
        float w = ARROW_WIDTH;

        // TOP triangular face
        buffer.pos(0, halfThick, -len).color(r, g, b, alpha).endVertex();
        buffer.pos(w, halfThick, 0).color(r, g, b, alpha).endVertex();
        buffer.pos(-w, halfThick, 0).color(r, g, b, alpha).endVertex();

        // BOTTOM triangular face
        buffer.pos(0, -halfThick, -len).color(r, g, b, alpha).endVertex();
        buffer.pos(-w, -halfThick, 0).color(r, g, b, alpha).endVertex();
        buffer.pos(w, -halfThick, 0).color(r, g, b, alpha).endVertex();

        // LEFT side
        buffer.pos(0, halfThick, -len).color(rd, gd, bd, alpha).endVertex();
        buffer.pos(-w, halfThick, 0).color(rd, gd, bd, alpha).endVertex();
        buffer.pos(-w, -halfThick, 0).color(rd, gd, bd, alpha).endVertex();

        buffer.pos(0, halfThick, -len).color(rd, gd, bd, alpha).endVertex();
        buffer.pos(-w, -halfThick, 0).color(rd, gd, bd, alpha).endVertex();
        buffer.pos(0, -halfThick, -len).color(rd, gd, bd, alpha).endVertex();

        // RIGHT side
        buffer.pos(0, halfThick, -len).color(rd, gd, bd, alpha).endVertex();
        buffer.pos(w, -halfThick, 0).color(rd, gd, bd, alpha).endVertex();
        buffer.pos(w, halfThick, 0).color(rd, gd, bd, alpha).endVertex();

        buffer.pos(0, halfThick, -len).color(rd, gd, bd, alpha).endVertex();
        buffer.pos(0, -halfThick, -len).color(rd, gd, bd, alpha).endVertex();
        buffer.pos(w, -halfThick, 0).color(rd, gd, bd, alpha).endVertex();

        // BACK side
        buffer.pos(-w, halfThick, 0).color(rd, gd, bd, alpha).endVertex();
        buffer.pos(w, halfThick, 0).color(rd, gd, bd, alpha).endVertex();
        buffer.pos(w, -halfThick, 0).color(rd, gd, bd, alpha).endVertex();

        buffer.pos(-w, halfThick, 0).color(rd, gd, bd, alpha).endVertex();
        buffer.pos(w, -halfThick, 0).color(rd, gd, bd, alpha).endVertex();
        buffer.pos(-w, -halfThick, 0).color(rd, gd, bd, alpha).endVertex();

        tessellator.draw();

        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();

        GlStateManager.popMatrix();

        // Draw distance text above arrow
        String distanceStr = StructureSearchManager.formatDistance(distance);

        GlStateManager.pushMatrix();
        GlStateManager.translate(renderX, renderY + 0.04, renderZ);

        // Billboard: face camera (both yaw and pitch for proper facing)
        GlStateManager.rotate(-cameraYaw, 0, 1, 0);
        GlStateManager.rotate(cameraPitch, 1, 0, 0);

        GlStateManager.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();

        int textWidth = mc.fontRenderer.getStringWidth(distanceStr);
        mc.fontRenderer.drawStringWithShadow(distanceStr, -textWidth / 2.0f, 0, color | 0xFF000000);

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();

        GlStateManager.popMatrix();
    }
}
