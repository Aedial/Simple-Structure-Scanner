package com.simplestructurescanner.client.render;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.BufferUtils;

import javax.vecmath.Vector3f;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Renders a structure preview in a GUI with isometric-style view.
 * Provides time-based auto-rotation similar to entity preview.
 */
@SideOnly(Side.CLIENT)
public class StructurePreviewRenderer {

    private final DummyWorld world;
    private int backgroundColor = 0xFF1A1A1A;

    // Isometric camera settings
    public enum LightingMode {
        /** Light rotates with the structure - same faces always lit (default, works with block rendering) */
        STRUCTURE,
        /**
         * Light is fixed in world space - intended for different faces to be lit as structure rotates.
         * Note: Limited effect with Minecraft block rendering as it uses baked vertex colors
         * without normals. Would require custom rendering with normals for full effect.
         */
        WORLD
    }

    private static final float ISOMETRIC_PITCH = 30f;   // degrees from horizontal
    private static final float ROTATION_SPEED = 20f;    // degrees per second
    private static final float ZOOM_FACTOR = 0.75f;     // higher = smaller structure
    private LightingMode lightingMode = LightingMode.STRUCTURE;

    public StructurePreviewRenderer() {
        this.world = new DummyWorld();
    }

    public DummyWorld getWorld() {
        return world;
    }

    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
    }

    public void setLightingMode(LightingMode mode) {
        this.lightingMode = mode;
    }

    public LightingMode getLightingMode() {
        return lightingMode;
    }

    /**
     * Renders the structure at the given GUI position with automatic rotation.
     */
    public void render(float guiX, float guiY, float guiWidth, float guiHeight) {
        if (world.renderedBlocks.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc);
        float scaleFactor = (float) res.getScaleFactor();

        // Convert GUI to screen coordinates
        int screenX = (int) (guiX * scaleFactor);
        int screenY = mc.displayHeight - (int) ((guiY + guiHeight) * scaleFactor);
        int screenW = (int) (guiWidth * scaleFactor);
        int screenH = (int) (guiHeight * scaleFactor);

        // Calculate time-based rotation
        float rotation = (System.currentTimeMillis() % 36000L) / 1000f * ROTATION_SPEED;

        // Get structure bounds
        Vector3f min = world.getMinPos();
        Vector3f max = world.getMaxPos();
        float sizeX = max.x - min.x + 1;
        float sizeY = max.y - min.y + 1;
        float sizeZ = max.z - min.z + 1;
        float maxDimension = Math.max(Math.max(sizeX, sizeY), sizeZ);

        // === SAVE STATE ===
        // Save viewport
        IntBuffer oldViewport = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, oldViewport);

        // Save projection matrix
        FloatBuffer projMatrix = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projMatrix);

        // Save modelview matrix
        FloatBuffer mvMatrix = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mvMatrix);

        // Save current texture binding
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        // Save blend state
        boolean wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        int oldBlendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
        int oldBlendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);

        // Save other state
        boolean wasDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean wasCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean wasLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean wasAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean wasScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean wasRescale = GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL);

        // Save color
        FloatBuffer colorBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, colorBuf);

        // === RENDER ===
        // Set viewport and scissor
        GlStateManager.viewport(screenX, screenY, screenW, screenH);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(screenX, screenY, screenW, screenH);

        // Clear depth buffer in our region
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);

        // Set projection matrix
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();

        float aspect = guiWidth / guiHeight;
        float orthoSize = maxDimension * ZOOM_FACTOR;
        GL11.glOrtho(-orthoSize * aspect, orthoSize * aspect, -orthoSize, orthoSize, -1000, 1000);

        // Set modelview matrix
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();

        // For WORLD lighting mode, set up light position BEFORE rotation
        // so light stays fixed while structure rotates
        if (lightingMode == LightingMode.WORLD) setupLighting();

        // Apply isometric view transformation
        GlStateManager.rotate(ISOMETRIC_PITCH, 1, 0, 0);
        GlStateManager.rotate(rotation, 0, 1, 0);

        // For STRUCTURE lighting mode, set up light position AFTER rotation
        // so light rotates with the structure (same faces always lit)
        if (lightingMode == LightingMode.STRUCTURE) setupLighting();

        // Center the structure
        float centerX = (min.x + max.x) / 2f + 0.5f;
        float centerY = (min.y + max.y) / 2f + 0.5f;
        float centerZ = (min.z + max.z) / 2f + 0.5f;
        GlStateManager.translate(-centerX, -centerY, -centerZ);

        // Render the blocks
        renderBlocks();

        // === RESTORE STATE ===
        // Disable scissor
        if (!wasScissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Restore viewport
        GlStateManager.viewport(oldViewport.get(0), oldViewport.get(1), oldViewport.get(2), oldViewport.get(3));

        // Restore projection matrix
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GL11.glLoadMatrix(projMatrix);

        // Restore modelview matrix
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GL11.glLoadMatrix(mvMatrix);

        // Restore texture binding
        GlStateManager.bindTexture(oldTexture);
        GL11.glBlendFunc(oldBlendSrc, oldBlendDst);
        if (wasBlend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        if (wasDepth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        if (wasCull) GlStateManager.enableCull(); else GlStateManager.disableCull();
        if (wasLighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
        if (wasAlpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
        if (wasRescale) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
        GlStateManager.color(colorBuf.get(0), colorBuf.get(1), colorBuf.get(2), colorBuf.get(3));
        GlStateManager.depthMask(true);
        GlStateManager.disableColorMaterial();
        GL11.glDisable(GL11.GL_LIGHT0);
        RenderHelper.disableStandardItemLighting();
    }

    /**
     * Sets up OpenGL lighting for the structure preview.
     * Light position is set in current matrix state, so call before or after
     * rotation depending on desired lighting mode.
     */
    private void setupLighting() {
        // Enable lighting
        GlStateManager.enableLighting();
        GL11.glEnable(GL11.GL_LIGHT0);

        // Light from upper-front-right (typical isometric lighting)
        FloatBuffer lightPos = BufferUtils.createFloatBuffer(4);
        lightPos.put(new float[]{0.5f, 1.0f, 0.8f, 0.0f}); // w=0 for directional light
        lightPos.flip();
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, lightPos);

        // Bright diffuse and ambient
        FloatBuffer diffuse = BufferUtils.createFloatBuffer(4);
        diffuse.put(new float[]{0.9f, 0.9f, 0.9f, 1.0f});
        diffuse.flip();
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, diffuse);

        FloatBuffer ambient = BufferUtils.createFloatBuffer(4);
        ambient.put(new float[]{0.4f, 0.4f, 0.4f, 1.0f});
        ambient.flip();
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, ambient);

        // Set material to use vertex colors
        GlStateManager.enableColorMaterial();
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
    }

    /**
     * Renders all blocks in the dummy world.
     */
    private void renderBlocks() {
        Minecraft mc = Minecraft.getMinecraft();

        // Set up render state for blocks
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableRescaleNormal();

        // For WORLD lighting mode, keep GL lighting enabled so light affects blocks
        // For STRUCTURE mode, disable it (blocks use baked vertex colors)
        if (lightingMode == LightingMode.WORLD) {
            // Lighting was already set up before rotation
            GlStateManager.enableLighting();
        } else {
            GlStateManager.disableLighting();
        }

        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);

        mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        BlockRendererDispatcher blockRenderer = mc.getBlockRendererDispatcher();
        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();

        try {
            // Render opaque layers first
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                if (layer == BlockRenderLayer.TRANSLUCENT) continue;

                ForgeHooksClient.setRenderLayer(layer);

                GlStateManager.disableBlend();
                GlStateManager.depthMask(true);

                Tessellator tess = Tessellator.getInstance();
                BufferBuilder buffer = tess.getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

                for (BlockPos pos : world.renderedBlocks) {
                    IBlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();
                    if (block == Blocks.AIR) continue;

                    try {
                        state = state.getActualState(world, pos);
                    } catch (Exception ignored) {
                    }

                    if (block.canRenderInLayer(state, layer)) {
                        blockRenderer.renderBlock(state, pos, world, buffer);
                    }
                }

                tess.draw();
                buffer.setTranslation(0, 0, 0);
            }

            // Render translucent layer last
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.TRANSLUCENT);

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
            );
            GlStateManager.depthMask(false);

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buffer = tess.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

            for (BlockPos pos : world.renderedBlocks) {
                IBlockState state = world.getBlockState(pos);
                Block block = state.getBlock();
                if (block == Blocks.AIR) continue;

                try {
                    state = state.getActualState(world, pos);
                } catch (Exception ignored) {
                }

                if (block.canRenderInLayer(state, BlockRenderLayer.TRANSLUCENT)) {
                    blockRenderer.renderBlock(state, pos, world, buffer);
                }
            }

            tess.draw();
            buffer.setTranslation(0, 0, 0);

        } finally {
            ForgeHooksClient.setRenderLayer(oldLayer);
        }
    }
}
