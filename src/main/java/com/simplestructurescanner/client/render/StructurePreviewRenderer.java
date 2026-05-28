package com.simplestructurescanner.client.render;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

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
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.simplestructurescanner.structure.StructureInfo.StructureLayer;

/**
 * Renders a structure preview in a GUI with isometric-style view.
 * Provides time-based auto-rotation similar to entity preview.
 */
@SideOnly(Side.CLIENT)
public class StructurePreviewRenderer {

    private static final float ISOMETRIC_PITCH = 30f;
    private static final float ROTATION_SPEED = 20f;
    private static final float ZOOM_FACTOR = 0.75f;
    private static final int CACHE_BUFFER_SIZE = 131072;
    private static final BlockRenderLayer[] OPAQUE_LAYERS = new BlockRenderLayer[] {
        BlockRenderLayer.SOLID,
        BlockRenderLayer.CUTOUT_MIPPED,
        BlockRenderLayer.CUTOUT
    };
    private static final FloatBuffer LIGHT_POSITION = makeLightBuffer(0.5f, 1.0f, 0.8f, 0.0f);
    private static final FloatBuffer LIGHT_DIFFUSE = makeLightBuffer(0.9f, 0.9f, 0.9f, 1.0f);
    private static final FloatBuffer LIGHT_AMBIENT = makeLightBuffer(0.4f, 0.4f, 0.4f, 1.0f);

    private final DummyWorld world;
    private final EnumMap<BlockRenderLayer, List<RenderBlockEntry>> layerEntries = new EnumMap<>(BlockRenderLayer.class);
    private final EnumMap<BlockRenderLayer, LayerBufferCache> layerBuffers = new EnumMap<>(BlockRenderLayer.class);

    private LightingMode lightingMode = LightingMode.STRUCTURE;
    private float centerX = 0.5f;
    private float centerY = 0.5f;
    private float centerZ = 0.5f;
    private float maxDimension = 1.0f;
    private boolean buffersUploaded;

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

    public StructurePreviewRenderer() {
        this.world = new DummyWorld();

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            layerEntries.put(layer, new ArrayList<RenderBlockEntry>());
            layerBuffers.put(layer, new LayerBufferCache());
        }
    }

    public static StructurePreviewRenderer createFromLayers(List<StructureLayer> layers) {
        StructurePreviewRenderer renderer = new StructurePreviewRenderer();
        if (layers == null || layers.isEmpty()) return renderer;

        int minY = Integer.MAX_VALUE;
        for (StructureLayer layer : layers) {
            if (layer.y < minY) minY = layer.y;
        }

        // Shift the preview upward when a structure uses negative layer coordinates.
        int yOffset = minY < 0 ? -minY : 0;
        for (StructureLayer layer : layers) {
            int y = layer.y + yOffset;

            for (int x = 0; x < layer.width; x++) {
                for (int z = 0; z < layer.depth; z++) {
                    IBlockState state = layer.getBlockState(x, z);
                    if (state == null || state.getBlock() == Blocks.AIR || state.getBlock() == Blocks.STRUCTURE_VOID) continue;

                    renderer.getWorld().addBlock(new BlockPos(x + layer.xOffset, y, z + layer.zOffset), state);
                }
            }
        }

        renderer.rebuildRenderCache();
        return renderer;
    }

    public DummyWorld getWorld() {
        return world;
    }

    public void setBackgroundColor(int color) {
    }

    public void setLightingMode(LightingMode mode) {
        this.lightingMode = mode;
    }

    public LightingMode getLightingMode() {
        return lightingMode;
    }

    public void release() {
        deleteLayerBuffers();
    }

    /**
     * Renders the structure at the given GUI position with automatic rotation.
     */
    public void render(float guiX, float guiY, float guiWidth, float guiHeight) {
        if (world.renderedBlocks.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        float scaleFactor = (float) resolution.getScaleFactor();

        int screenX = (int) (guiX * scaleFactor);
        int screenY = mc.displayHeight - (int) ((guiY + guiHeight) * scaleFactor);
        int screenW = Math.max(1, (int) (guiWidth * scaleFactor));
        int screenH = Math.max(1, (int) (guiHeight * scaleFactor));
        float rotation = (System.currentTimeMillis() % 36000L) / 1000f * ROTATION_SPEED;
        float aspect = guiHeight <= 0.0f ? 1.0f : guiWidth / guiHeight;
        float orthoSize = maxDimension * ZOOM_FACTOR;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();

        try {
            GlStateManager.viewport(screenX, screenY, screenW, screenH);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(screenX, screenY, screenW, screenH);
            GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            GL11.glOrtho(-orthoSize * aspect, orthoSize * aspect, -orthoSize, orthoSize, -1000, 1000);

            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();

            if (lightingMode == LightingMode.WORLD) setupLighting();

            // Apply isometric view transformations after setting up projection and lighting
            GlStateManager.rotate(ISOMETRIC_PITCH, 1, 0, 0);
            GlStateManager.rotate(rotation, 0, 1, 0);

            if (lightingMode == LightingMode.STRUCTURE) setupLighting();

            GlStateManager.translate(-centerX, -centerY, -centerZ);
            renderBlocks();
        } finally {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            restoreGuiState();
        }
    }

    private void restoreGuiState() {
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.disableRescaleNormal();
        GlStateManager.depthMask(true);
        GlStateManager.color(1f, 1f, 1f, 1f);
        RenderHelper.disableStandardItemLighting();
    }

    private void rebuildRenderCache() {
        deleteLayerBuffers();

        for (List<RenderBlockEntry> entries : layerEntries.values()) {
            entries.clear();
        }

        if (world.renderedBlocks.isEmpty()) {
            centerX = 0.5f;
            centerY = 0.5f;
            centerZ = 0.5f;
            maxDimension = 1.0f;
            return;
        }

        float minX = world.getMinPos().x;
        float minY = world.getMinPos().y;
        float minZ = world.getMinPos().z;
        float maxX = world.getMaxPos().x;
        float maxY = world.getMaxPos().y;
        float maxZ = world.getMaxPos().z;

        centerX = (minX + maxX) / 2f + 0.5f;
        centerY = (minY + maxY) / 2f + 0.5f;
        centerZ = (minZ + maxZ) / 2f + 0.5f;
        maxDimension = Math.max(Math.max(maxX - minX + 1.0f, maxY - minY + 1.0f), maxZ - minZ + 1.0f);

        // Resolve actual states once so large previews do not repeat the same block work every frame.
        for (BlockPos pos : world.renderedBlocks) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() == Blocks.AIR) continue;

            try {
                state = state.getActualState(world, pos);
            } catch (Exception ignored) {
            }

            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                if (state.getBlock().canRenderInLayer(state, layer)) {
                    layerEntries.get(layer).add(new RenderBlockEntry(pos, state));
                }
            }
        }
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
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, LIGHT_POSITION.duplicate());
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, LIGHT_DIFFUSE.duplicate());
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_AMBIENT, LIGHT_AMBIENT.duplicate());
        GlStateManager.enableColorMaterial();
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
    }

    /**
     * Renders all blocks in the dummy world.
     */
    private void renderBlocks() {
        Minecraft mc = Minecraft.getMinecraft();
        BlockRendererDispatcher blockRenderer = mc.getBlockRendererDispatcher();
        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();

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

        try {
            if (OpenGlHelper.useVbo()) {
                ensureLayerBuffersUploaded(blockRenderer);
                renderLayerBuffers();
                return;
            }

            renderImmediateLayers(blockRenderer);
        } finally {
            ForgeHooksClient.setRenderLayer(oldLayer);
        }
    }

    private void ensureLayerBuffersUploaded(BlockRendererDispatcher blockRenderer) {
        if (buffersUploaded) return;

        deleteLayerBuffers();

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            List<RenderBlockEntry> entries = layerEntries.get(layer);
            if (entries == null || entries.isEmpty()) continue;

            ForgeHooksClient.setRenderLayer(layer);

            BufferBuilder buffer = new BufferBuilder(CACHE_BUFFER_SIZE);
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

            for (RenderBlockEntry entry : entries) {
                blockRenderer.renderBlock(entry.state, entry.pos, world, buffer);
            }

            if (buffer.getVertexCount() <= 0) {
                buffer.reset();
                continue;
            }

            buffer.finishDrawing();

            LayerBufferCache layerBuffer = layerBuffers.get(layer);
            layerBuffer.vertexBuffer = new VertexBuffer(DefaultVertexFormats.BLOCK);
            layerBuffer.drawMode = buffer.getDrawMode();
            layerBuffer.vertexBuffer.bufferData(buffer.getByteBuffer());
        }

        buffersUploaded = true;
    }

    private void renderImmediateLayers(BlockRendererDispatcher blockRenderer) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        for (BlockRenderLayer layer : OPAQUE_LAYERS) {
            renderImmediateLayer(blockRenderer, tessellator, buffer, layer, false);
        }

        renderImmediateLayer(blockRenderer, tessellator, buffer, BlockRenderLayer.TRANSLUCENT, true);
    }

    private void renderImmediateLayer(BlockRendererDispatcher blockRenderer, Tessellator tessellator, BufferBuilder buffer,
            BlockRenderLayer layer, boolean translucent) {
        List<RenderBlockEntry> entries = layerEntries.get(layer);
        if (entries == null || entries.isEmpty()) return;

        ForgeHooksClient.setRenderLayer(layer);

        if (translucent) {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
            );
            GlStateManager.depthMask(false);
        } else {
            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
        }

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

        for (RenderBlockEntry entry : entries) {
            blockRenderer.renderBlock(entry.state, entry.pos, world, buffer);
        }

        if (buffer.getVertexCount() > 0) {
            tessellator.draw();
            buffer.setTranslation(0, 0, 0);
            return;
        }

        buffer.reset();
        buffer.setTranslation(0, 0, 0);
    }

    private void renderLayerBuffers() {
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);

        try {
            for (BlockRenderLayer layer : OPAQUE_LAYERS) {
                GlStateManager.disableBlend();
                GlStateManager.depthMask(true);
                renderLayerBuffer(layer);
            }

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
            );
            GlStateManager.depthMask(false);
            renderLayerBuffer(BlockRenderLayer.TRANSLUCENT);
        } finally {
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
            GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GlStateManager.resetColor();
        }
    }

    private void renderLayerBuffer(BlockRenderLayer layer) {
        LayerBufferCache layerBuffer = layerBuffers.get(layer);
        if (layerBuffer == null || layerBuffer.vertexBuffer == null) return;

        ForgeHooksClient.setRenderLayer(layer);
        layerBuffer.vertexBuffer.bindBuffer();
        setupBlockArrayPointers();
        layerBuffer.vertexBuffer.drawArrays(layerBuffer.drawMode);
    }

    private void setupBlockArrayPointers() {
        GlStateManager.glVertexPointer(3, 5126, 28, 0);
        GlStateManager.glColorPointer(4, 5121, 28, 12);
        GlStateManager.glTexCoordPointer(2, 5126, 28, 16);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glTexCoordPointer(2, 5122, 28, 24);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private void deleteLayerBuffers() {
        for (LayerBufferCache layerBuffer : layerBuffers.values()) {
            if (layerBuffer.vertexBuffer == null) continue;

            layerBuffer.vertexBuffer.deleteGlBuffers();
            layerBuffer.vertexBuffer = null;
            layerBuffer.drawMode = GL11.GL_QUADS;
        }

        buffersUploaded = false;
    }

    private static FloatBuffer makeLightBuffer(float x, float y, float z, float w) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(4);
        buffer.put(new float[] {x, y, z, w});
        buffer.flip();
        return buffer.asReadOnlyBuffer();
    }

    private static class RenderBlockEntry {
        private final BlockPos pos;
        private final IBlockState state;

        private RenderBlockEntry(BlockPos pos, IBlockState state) {
            this.pos = pos;
            this.state = state;
        }
    }

    private static class LayerBufferCache {
        private VertexBuffer vertexBuffer;
        private int drawMode = GL11.GL_QUADS;
    }
}
