package com.simplestructurescanner.client.render;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import javax.annotation.Nullable;

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
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureInfo.PreviewBlockEntry;
import com.simplestructurescanner.structure.StructureInfo.PreviewSnapshot;
import com.simplestructurescanner.structure.StructureInfo.StructureLayer;


/**
 * Renders a structure preview in a GUI with isometric-style view.
 * Provides time-based auto-rotation similar to entity preview.
 */
@SideOnly(Side.CLIENT)
public class StructurePreviewRenderer {

    private static final float ISOMETRIC_PITCH = 30f;
    private static final float ROTATION_SPEED = 20f;
    private static final float ZOOM_IN_FACTOR = 1.1f;
    private static final int CACHE_BUFFER_SIZE = 131072;
    private static final BlockRenderLayer[] OPAQUE_LAYERS = new BlockRenderLayer[] {
        BlockRenderLayer.SOLID,
        BlockRenderLayer.CUTOUT_MIPPED,
        BlockRenderLayer.CUTOUT
    };
    private static final FloatBuffer LIGHT_POSITION = makeLightBuffer(0.5f, 1.0f, 0.8f, 0.0f);
    private static final FloatBuffer LIGHT_DIFFUSE = makeLightBuffer(0.9f, 0.9f, 0.9f, 1.0f);
    private static final FloatBuffer LIGHT_AMBIENT = makeLightBuffer(0.4f, 0.4f, 0.4f, 1.0f);
    private static final boolean PROFILE_PREPARE_PREVIEW = Boolean.getBoolean("simplestructurescanner.profile.preparePreview");

    private final Object buildLock = new Object();
    private final EnumMap<BlockRenderLayer, List<RenderBlockEntry>> layerEntries = new EnumMap<>(BlockRenderLayer.class);
    private final EnumMap<BlockRenderLayer, LayerBufferCache> layerBuffers = new EnumMap<>(BlockRenderLayer.class);
    private final List<RenderTileEntityEntry> tileEntityEntries = new ArrayList<>();

    private DummyWorld world;
    @Nullable
    private Thread buildThread;
    private LightingMode lightingMode = LightingMode.STRUCTURE;
    private float zoom_factor = 0.75f;
    private float centerX = 0.5f;
    private float centerY = 0.5f;
    private float centerZ = 0.5f;
    private float maxDimension = 1.0f;
    private boolean buffersUploaded;
    private volatile boolean buildReady = true;
    private volatile boolean released;

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

    // TODO: Get the full Global TESR rendering from Machinery Assembler, if needed.
    //       It is quite heavy, so will only be done if there is demand for it.
    // TODO: We do not create a "real" world with all the block states and tile entities,
    //       so some TESRs may not render properly. This should not be necessary with
    //       most structures (do you put machines in your structures?), and will only be
    //       implemented if there is demand for it. See Machinery Assembler for example.
    //       The most common case would probably be something like Botania's Mana Pylons.
    public StructurePreviewRenderer() {
        this.world = new DummyWorld();

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            layerEntries.put(layer, new ArrayList<>());
            layerBuffers.put(layer, new LayerBufferCache());
        }
    }

    public static StructurePreviewRenderer createFromLayers(List<StructureLayer> layers) {
        StructurePreviewRenderer renderer = new StructurePreviewRenderer();
        renderer.startBuild(layers);
        return renderer;
    }

    public static StructurePreviewRenderer createFromStructureInfo(@Nullable StructureInfo structureInfo) {
        StructurePreviewRenderer renderer = new StructurePreviewRenderer();
        if (structureInfo == null || structureInfo.getPreviewSnapshot().isEmpty()) return renderer;

        renderer.startBuild(structureInfo.getPreviewSnapshot());
        return renderer;
    }

    public DummyWorld getWorld() {
        return world;
    }

    public boolean isBuildReady() {
        return buildReady;
    }

    public boolean hasRenderableBlocks() {
        return !world.renderedBlocks.isEmpty();
    }

    public void setBackgroundColor(int color) {
    }

    public void setZoomFactor(float zoomFactor) {
        this.zoom_factor = zoomFactor;
    }

    public float getZoomFactor() {
        return zoom_factor;
    }

    /**
     * Zooms in the structure preview by the default zoom factor.
     * The default zoom factor is 1.1, which means the structure will appear 10% larger.
     */
    public void zoomIn() {
        zoomIn(ZOOM_IN_FACTOR);
    }

    /**
     * Zooms in the structure preview by the given factor.
     * @param factor The zoom factor to apply (e.g., 1.1 for a structure 10% larger)
     */
    public void zoomIn(float factor) {
        if (factor <= 0.0f) return;

        zoom_factor = zoom_factor / factor;
    }

    /**
     * Zooms out the structure preview by the default zoom factor.
     * The default zoom factor is 1.1, which means the structure will appear 10% smaller.
     */
    public void zoomOut() {
        zoomOut(ZOOM_IN_FACTOR);
    }

    /**
     * Zooms out the structure preview by the given factor.
     * @param factor The zoom factor to apply (e.g., 1.1 for a structure 10% smaller)
     */
    public void zoomOut(float factor) {
        if (factor <= 0.0f) return;

        zoom_factor = zoom_factor * factor;
    }

    public void setLightingMode(LightingMode mode) {
        this.lightingMode = mode;
    }

    public LightingMode getLightingMode() {
        return lightingMode;
    }

    public void release() {
        Thread threadToStop;

        synchronized (buildLock) {
            released = true;
            buildReady = false;
            threadToStop = buildThread;
            buildThread = null;
        }

        if (threadToStop != null) threadToStop.interrupt();

        deleteLayerBuffers();
        clearPendingLayerUploadData();
        for (List<RenderBlockEntry> entries : layerEntries.values()) entries.clear();

        tileEntityEntries.clear();
        world.clear();
        centerX = 0.5f;
        centerY = 0.5f;
        centerZ = 0.5f;
        maxDimension = 1.0f;
    }

    /**
     * Renders the structure at the given GUI position with automatic rotation.
     */
    public void render(float guiX, float guiY, float guiWidth, float guiHeight) {
        if (!buildReady) return;
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
        float orthoSize = maxDimension * zoom_factor;

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();

        try {
            prepareLightmap(mc);
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
            renderTileEntities(mc.getRenderPartialTicks());
        } finally {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            restoreGuiState();
        }
    }

    private void prepareLightmap(Minecraft minecraft) {
        // Some GUI paths disable the lightmap unit after 2D drawing.
        // Use the vanilla setup so the preview samples Minecraft's actual lightmap texture.
        minecraft.entityRenderer.enableLightmap();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
    }

    private void restoreGuiState() {
        Minecraft mc = Minecraft.getMinecraft();

        GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Some tile entity renderers leave texture transforms or the lightmap unit active.
        // Reset both texture units before returning to 2D GUI drawing.
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);

        mc.entityRenderer.disableLightmap();

        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
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

    private void startBuild(@Nullable List<StructureLayer> layers) {
        if (layers == null || layers.isEmpty()) return;

        startBuildTask(() -> preparePreview(StructureInfo.createPreviewSnapshot(layers)));
    }

    private void startBuild(PreviewSnapshot previewSnapshot) {
        if (previewSnapshot.isEmpty()) return;

        startBuildTask(() -> preparePreview(previewSnapshot));
    }

    private void startBuildTask(PreviewBuildTask buildTask) {
        released = false;
        buildReady = false;

        Thread thread = new Thread(() -> buildPreview(buildTask), "Structure Preview Builder");
        thread.setDaemon(true);

        synchronized (buildLock) {
            buildThread = thread;
        }

        thread.start();
    }

    private void buildPreview(PreviewBuildTask buildTask) {
        PreparedPreview preparedPreview = null;
        boolean installed = false;

        try {
            preparedPreview = buildTask.prepare();
            if (preparedPreview == null) return;

            synchronized (buildLock) {
                if (released || buildThread != Thread.currentThread()) return;

                installPreparedPreview(preparedPreview);
                buildReady = true;
                installed = true;
            }
        } finally {
            synchronized (buildLock) {
                if (buildThread == Thread.currentThread()) {
                    buildThread = null;

                    if (!released) buildReady = true;
                }
            }

            if (!installed && preparedPreview != null) preparedPreview.world.clear();
        }
    }

    @Nullable
    private PreparedPreview preparePreview(PreviewSnapshot previewSnapshot) {
        final long startNano = PROFILE_PREPARE_PREVIEW ? System.nanoTime() : 0L;

        long t = 0L;
        long tAddBlocks = 0L;
        long tLoopTotal = 0L;
        long tTileEntity = 0L;
        long tActualState = 0L;
        long tLayerDispatch = 0L;
        long tLayerBufferBuild = 0L;
        int loopCount = previewSnapshot.getBlocks().size();
        int tileEntityCount = 0;

        if (previewSnapshot.isEmpty()) return new PreparedPreview(
                new DummyWorld(),
                new EnumMap<>(BlockRenderLayer.class),
                new ArrayList<>(),
                new EnumMap<>(BlockRenderLayer.class),
                0.5f, 0.5f, 0.5f, 1.0f);

        DummyWorld buildWorld = new DummyWorld();
        EnumMap<BlockRenderLayer, List<RenderBlockEntry>> buildLayerEntries = new EnumMap<>(BlockRenderLayer.class);
        List<RenderTileEntityEntry> buildTileEntityEntries = new ArrayList<>();

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            buildLayerEntries.put(layer, new ArrayList<>());
        }

        if (PROFILE_PREPARE_PREVIEW) t = System.nanoTime();
        // Populate the full dummy world first so neighbor-dependent actual states resolve
        // against the same complete snapshot in the second pass.
        buildWorld.addBlocks(previewSnapshot.getBlocks(),
            previewSnapshot.getMinX(), previewSnapshot.getMinY(), previewSnapshot.getMinZ(),
            previewSnapshot.getMaxX(), previewSnapshot.getMaxY(), previewSnapshot.getMaxZ());
        if (PROFILE_PREPARE_PREVIEW) tAddBlocks = System.nanoTime() - t;

        if (shouldCancelBuild()) return null;

        for (PreviewBlockEntry entry : previewSnapshot.getBlocks()) {
            if (shouldCancelBuild()) return null;

            long loopStart = PROFILE_PREPARE_PREVIEW ? System.nanoTime() : 0L;

            IBlockState state = entry.state;

            // Initialize tile entities if the block has one. They are expected to be far and few
            if (PROFILE_PREPARE_PREVIEW) t = System.nanoTime();
            TileEntity tileEntity = createRenderTileEntity(buildWorld, entry.pos, state, entry.blockEntityData);
            if (PROFILE_PREPARE_PREVIEW) tTileEntity += System.nanoTime() - t;

            if (tileEntity != null) {
                buildWorld.setTileEntity(entry.pos, tileEntity);
                buildTileEntityEntries.add(new RenderTileEntityEntry(entry.pos, tileEntity));
                tileEntityCount++;
            }

            // Retrieve correct state if it has world-dependent properties
            if (PROFILE_PREPARE_PREVIEW) t = System.nanoTime();
            state = state.getActualState(buildWorld, entry.pos);
            if (PROFILE_PREPARE_PREVIEW) tActualState += System.nanoTime() - t;

            // Separate blocks into their render layers for efficient render dispatch
            // TODO: This step is the most expensive part of the loop and could easily be cached into bitmasks.
            //       But it is fairly minor, as the whole build *before VBO upload* is still < 1s for 100k blocks.
            if (PROFILE_PREPARE_PREVIEW) t = System.nanoTime();
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                if (!state.getBlock().canRenderInLayer(state, layer)) continue;

                buildLayerEntries.get(layer).add(new RenderBlockEntry(entry.pos, state));
            }
            if (PROFILE_PREPARE_PREVIEW) tLayerDispatch += System.nanoTime() - t;

            if (PROFILE_PREPARE_PREVIEW) tLoopTotal += System.nanoTime() - loopStart;
        }

        // TODO: This part will need to be optimized, as it's extremely expensive for large structures.
        //       Profiling shows a good 80% of the time is spent on VBO, which can be a few seconds for 100k blocks.
        //       But honestly, I don't really know *how* to optimize that, beside throwing more power into the pot...
        //       I *guess* it's not that big of a deal to wait a few seconds for a large structure to load.
        //       It's not like the GUI freezes anymore, so it's just a mild annoyance.
        EnumMap<BlockRenderLayer, PreparedLayerBufferData> buildLayerBufferData = new EnumMap<>(BlockRenderLayer.class);
        if (OpenGlHelper.useVbo()) {
            if (PROFILE_PREPARE_PREVIEW) t = System.nanoTime();

            try {
                EnumMap<BlockRenderLayer, PreparedLayerBufferData> preparedLayerBufferData =
                    prepareLayerUploadData(buildWorld, buildLayerEntries);
                if (preparedLayerBufferData == null) return null;

                buildLayerBufferData = preparedLayerBufferData;
            } catch (Throwable throwable) {
                SimpleStructureScanner.LOGGER.warn(
                    "Failed to prepare preview layer buffers asynchronously, falling back to client-thread upload",
                    throwable);
            }

            if (PROFILE_PREPARE_PREVIEW) tLayerBufferBuild = System.nanoTime() - t;
        }

        if (PROFILE_PREPARE_PREVIEW) {
            long total = System.nanoTime() - startNano;
            double totalMs = total / 1_000_000.0;
            double addBlocksMs = tAddBlocks / 1_000_000.0;
            double loopMs = tLoopTotal / 1_000_000.0;
            double tileEntityMs = tTileEntity / 1_000_000.0;
            double actualStateMs = tActualState / 1_000_000.0;
            double layerDispatchMs = tLayerDispatch / 1_000_000.0;
            double layerBufferBuildMs = tLayerBufferBuild / 1_000_000.0;
            SimpleStructureScanner.LOGGER.info(
                "preparePreview took {} ms :\n" +
                "  - addBlocks:        {} ms\n" +
                "  - Loop:             {} ms (count: {}, {} ms per)\n" +
                "    -> Tile Entities: {} ms (count: {}, {} ms per)\n" +
                "    -> actualState:   {} ms\n" +
                "    -> layerDispatch: {} ms\n" +
                "  - layerBuffers:     {} ms\n",
                totalMs, addBlocksMs,
                loopMs, loopCount, loopCount > 0 ? loopMs / loopCount : 0.0,
                tileEntityMs, tileEntityCount, tileEntityCount > 0 ? tileEntityMs / tileEntityCount : 0.0,
                actualStateMs, layerDispatchMs, layerBufferBuildMs);
        }

        return new PreparedPreview(buildWorld, buildLayerEntries, buildTileEntityEntries, buildLayerBufferData,
                previewSnapshot);
    }

    /**
     * Builds CPU-side layer vertex data on the worker thread so the first GUI render only needs the GL upload.
     * This is a UX optimization to avoid blocking the GUI thread for large structures.
     */
    @Nullable
    private EnumMap<BlockRenderLayer, PreparedLayerBufferData> prepareLayerUploadData(DummyWorld buildWorld,
            EnumMap<BlockRenderLayer, List<RenderBlockEntry>> buildLayerEntries) {
        BlockRendererDispatcher blockRenderer = Minecraft.getMinecraft().getBlockRendererDispatcher();
        BlockRenderLayer previousLayer = MinecraftForgeClient.getRenderLayer();
        EnumMap<BlockRenderLayer, PreparedLayerBufferData> buildLayerBufferData = new EnumMap<>(BlockRenderLayer.class);

        try {
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                if (shouldCancelBuild()) return null;

                List<RenderBlockEntry> entries = buildLayerEntries.get(layer);
                if (entries == null || entries.isEmpty()) continue;

                ForgeHooksClient.setRenderLayer(layer);

                BufferBuilder buffer = new BufferBuilder(CACHE_BUFFER_SIZE);
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

                for (RenderBlockEntry entry : entries) {
                    if (shouldCancelBuild()) return null;

                    blockRenderer.renderBlock(entry.state, entry.pos, buildWorld, buffer);
                }

                if (buffer.getVertexCount() <= 0) {
                    buffer.reset();
                    continue;
                }

                buffer.finishDrawing();
                buildLayerBufferData.put(layer,
                    new PreparedLayerBufferData(copyVertexData(buffer.getByteBuffer()), buffer.getDrawMode()));
            }

            return buildLayerBufferData;
        } finally {
            ForgeHooksClient.setRenderLayer(previousLayer);
        }
    }

    private ByteBuffer copyVertexData(ByteBuffer sourceBuffer) {
        ByteBuffer copy = BufferUtils.createByteBuffer(sourceBuffer.limit());
        ByteBuffer source = sourceBuffer.duplicate();
        source.position(0);
        copy.put(source);
        copy.flip();
        return copy;
    }
            

    private void installPreparedPreview(PreparedPreview preparedPreview) {
        long t = PROFILE_PREPARE_PREVIEW ? System.nanoTime() : 0L;

        for (List<RenderBlockEntry> entries : layerEntries.values()) entries.clear();

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            layerEntries.get(layer).addAll(preparedPreview.layerEntries.get(layer));
            layerBuffers.get(layer).preparedData = preparedPreview.layerBufferData.get(layer);
        }

        tileEntityEntries.clear();
        tileEntityEntries.addAll(preparedPreview.tileEntityEntries);
        world.clear();
        world = preparedPreview.world;
        centerX = preparedPreview.centerX;
        centerY = preparedPreview.centerY;
        centerZ = preparedPreview.centerZ;
        maxDimension = preparedPreview.maxDimension;
        buffersUploaded = false;

        if (PROFILE_PREPARE_PREVIEW) {
            long total = System.nanoTime() - t;
            double totalMs = total / 1_000_000.0;
            SimpleStructureScanner.LOGGER.info("installPreparedPreview took {} ms", totalMs);
        }
    }

    private boolean shouldCancelBuild() {
        return released || Thread.currentThread().isInterrupted();
    }

    private TileEntity createRenderTileEntity(DummyWorld previewWorld, BlockPos pos, IBlockState state,
            @Nullable NBTTagCompound blockEntityData) {
        if (!state.getBlock().hasTileEntity(state)) return null;

        try {
            NBTTagCompound tileEntityTag = blockEntityData != null && !blockEntityData.isEmpty()
                ? createTileEntityData(pos, blockEntityData)
                : null;

            TileEntity tileEntity = null;
            if (tileEntityTag != null && blockEntityData.hasKey("id", Constants.NBT.TAG_STRING)) {
                tileEntity = TileEntity.create(previewWorld, tileEntityTag);
            }

            if (tileEntity == null) tileEntity = state.getBlock().createTileEntity(previewWorld, state);
            if (tileEntity == null) return null;

            tileEntity.setWorld(previewWorld);
            tileEntity.setPos(pos);

            if (tileEntityTag != null) {
                tileEntity.readFromNBT(tileEntityTag);
                tileEntity.setWorld(previewWorld);
                tileEntity.setPos(pos);
            }

            tileEntity.updateContainingBlockInfo();
            return tileEntity;
        } catch (Exception ignored) {
            return null;
        }
    }

    private NBTTagCompound createTileEntityData(BlockPos pos, NBTTagCompound blockEntityData) {
        NBTTagCompound tileEntityTag = blockEntityData.copy();
        tileEntityTag.setInteger("x", pos.getX());
        tileEntityTag.setInteger("y", pos.getY());
        tileEntityTag.setInteger("z", pos.getZ());
        return tileEntityTag;
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

    /**
     * Render simple state-backed tile entities after the block pass.
     * Global renderers are skipped because they depend on player-relative context.
     */
    private void renderTileEntities(float partialTicks) {
        if (tileEntityEntries.isEmpty()) return;

        TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
        World previousWorld = dispatcher.world;
        dispatcher.setWorld(world);

        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);

        try {
            for (int pass = 0; pass <= 1; pass++) {
                ForgeHooksClient.setRenderPass(pass);

                for (RenderTileEntityEntry entry : tileEntityEntries) {
                    TileEntity tileEntity = entry.tileEntity;
                    if (tileEntity == null || tileEntity.isInvalid()) continue;
                    if (!tileEntity.shouldRenderInPass(pass)) continue;

                    TileEntitySpecialRenderer<TileEntity> renderer = dispatcher.getRenderer(tileEntity);
                    if (renderer == null) continue;
                    if (renderer.isGlobalRenderer(tileEntity)) continue;

                    tileEntity.setWorld(world);
                    tileEntity.setPos(entry.pos);

                    try {
                        int light = world.getCombinedLight(entry.pos, 0);
                        int lightX = light % 65536;
                        int lightY = light / 65536;
                        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
                        GlStateManager.color(1f, 1f, 1f, 1f);

                        dispatcher.render(tileEntity, entry.pos.getX(), entry.pos.getY(), entry.pos.getZ(), partialTicks);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } finally {
            dispatcher.setWorld(previousWorld);
            ForgeHooksClient.setRenderPass(-1);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    private void ensureLayerBuffersUploaded(BlockRendererDispatcher blockRenderer) {
        if (buffersUploaded) return;

        long t = PROFILE_PREPARE_PREVIEW ? System.nanoTime() : 0L;
        int preparedLayerCount = 0;
        int fallbackLayerCount = 0;

        deleteLayerBuffers();

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            LayerBufferCache layerBuffer = layerBuffers.get(layer);
            PreparedLayerBufferData preparedData = layerBuffer.preparedData;

            if (preparedData != null) {
                layerBuffer.vertexBuffer = new VertexBuffer(DefaultVertexFormats.BLOCK);
                layerBuffer.drawMode = preparedData.drawMode;
                layerBuffer.vertexBuffer.bufferData(preparedData.vertexData.duplicate());
                layerBuffer.preparedData = null;
                preparedLayerCount++;
                continue;
            }

            List<RenderBlockEntry> entries = layerEntries.get(layer);
            if (entries == null || entries.isEmpty()) continue;

            fallbackLayerCount++;

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

            layerBuffer.vertexBuffer = new VertexBuffer(DefaultVertexFormats.BLOCK);
            layerBuffer.drawMode = buffer.getDrawMode();
            layerBuffer.vertexBuffer.bufferData(buffer.getByteBuffer());
        }

        buffersUploaded = true;

        if (PROFILE_PREPARE_PREVIEW) {
            long total = System.nanoTime() - t;
            double totalMs = total / 1_000_000.0;
            SimpleStructureScanner.LOGGER.info(
                "ensureLayerBuffersUploaded took {} ms (prepared layers: {}, fallback layers: {})",
                totalMs, preparedLayerCount, fallbackLayerCount);
        }
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

    private void clearPendingLayerUploadData() {
        for (LayerBufferCache layerBuffer : layerBuffers.values()) {
            layerBuffer.preparedData = null;
        }
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

    private static class RenderTileEntityEntry {
        private final BlockPos pos;
        private final TileEntity tileEntity;

        private RenderTileEntityEntry(BlockPos pos, TileEntity tileEntity) {
            this.pos = pos;
            this.tileEntity = tileEntity;
        }
    }

    private static class LayerBufferCache {
        private VertexBuffer vertexBuffer;
        private int drawMode = GL11.GL_QUADS;
        @Nullable
        private PreparedLayerBufferData preparedData;
    }

    private static class PreparedLayerBufferData {
        private final ByteBuffer vertexData;
        private final int drawMode;

        private PreparedLayerBufferData(ByteBuffer vertexData, int drawMode) {
            this.vertexData = vertexData;
            this.drawMode = drawMode;
        }
    }

    private interface PreviewBuildTask {
        @Nullable
        PreparedPreview prepare();
    }

    private static class PreparedPreview {
        private final DummyWorld world;
        private final EnumMap<BlockRenderLayer, List<RenderBlockEntry>> layerEntries;
        private final List<RenderTileEntityEntry> tileEntityEntries;
        private final EnumMap<BlockRenderLayer, PreparedLayerBufferData> layerBufferData;
        private final float centerX;
        private final float centerY;
        private final float centerZ;
        private final float maxDimension;

        private PreparedPreview(DummyWorld world, EnumMap<BlockRenderLayer, List<RenderBlockEntry>> layerEntries,
                List<RenderTileEntityEntry> tileEntityEntries,
                EnumMap<BlockRenderLayer, PreparedLayerBufferData> layerBufferData, float centerX, float centerY,
                float centerZ, float maxDimension) {
            this.world = world;
            this.layerEntries = layerEntries;
            this.tileEntityEntries = tileEntityEntries;
            this.layerBufferData = layerBufferData;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.maxDimension = maxDimension;
        }

        private PreparedPreview(DummyWorld world, EnumMap<BlockRenderLayer, List<RenderBlockEntry>> layerEntries,
                List<RenderTileEntityEntry> tileEntityEntries,
                EnumMap<BlockRenderLayer, PreparedLayerBufferData> layerBufferData, PreviewSnapshot previewSnapshot) {
            float minX = previewSnapshot.getMinX();
            float minY = previewSnapshot.getMinY();
            float minZ = previewSnapshot.getMinZ();
            float maxX = previewSnapshot.getMaxX();
            float maxY = previewSnapshot.getMaxY();
            float maxZ = previewSnapshot.getMaxZ();
            float previewCenterX = (minX + maxX) / 2f + 0.5f;
            float previewCenterY = (minY + maxY) / 2f + 0.5f;
            float previewCenterZ = (minZ + maxZ) / 2f + 0.5f;
            float previewMaxDimension = Math.max(Math.max(maxX - minX + 1.0f, maxY - minY + 1.0f), maxZ - minZ + 1.0f);

            this.world = world;
            this.layerEntries = layerEntries;
            this.tileEntityEntries = tileEntityEntries;
            this.layerBufferData = layerBufferData;
            this.centerX = previewCenterX;
            this.centerY = previewCenterY;
            this.centerZ = previewCenterZ;
            this.maxDimension = previewMaxDimension;
        }
    }
}
