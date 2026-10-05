package g_mungus.alpha_omega.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.mixin.client.LevelRendererAccessor;
import g_mungus.alpha_omega.mixin.client.SectionOcclusionGraphAccessor;
import g_mungus.alpha_omega.mixin.client.ViewAreaAccessor;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws the neighbouring faces where they really are (design §6.3). The client's own face is vanilla's world; each
 * neighbouring face has its own {@link ViewArea}, centred on the camera's position seen from that face's storage,
 * whose sections compile from that face's chunks like any others. They are drawn in each terrain layer with the
 * face's rotation in the model-view matrix and offsets from the camera's position there; entities and block entities
 * there are drawn with the same rotation.
 */
public final class NeighbourRenderer {

    /** Out-of-view neighbour sections compiled per frame. */
    private static final int COMPILE_AHEAD_PER_FRAME = 24;

    private static final Map<CubeFace, ViewArea> AREAS = new EnumMap<>(CubeFace.class);
    private static final Map<CubeFace, List<SectionRenderDispatcher.RenderSection>> VISIBLE = new EnumMap<>(CubeFace.class);

    @Nullable
    private static SectionRenderDispatcher dispatcher;
    @Nullable
    private static ClientLevel areaLevel;
    private static int areaViewDistance;
    @Nullable
    private static CubeGeometry geometry;
    @Nullable
    private static CubeFace home;
    /** The face vanilla's own area holds, so a change of face can be caught before vanilla moves it. */
    @Nullable
    private static CubeFace vanillaFace;
    /** Vanilla's area was swapped this frame, and its visible-section graph starts again from nothing. */
    private static boolean swapped;

    private NeighbourRenderer() {
    }

    /** Releases every area, so they are rebuilt (new level, view distance or renderer). */
    public static void reset() {
        AREAS.values().forEach(ViewArea::releaseAllBuffers);
        AREAS.clear();
        VISIBLE.clear();
        home = null;
        vanillaFace = null;
        swapped = false;
        geometry = null;
    }

    /**
     * Before vanilla positions its area for the frame: if the camera has crossed to another face, hand vanilla the
     * area already built for that face and keep vanilla's old one as a neighbour, so neither face recompiles.
     */
    public static void beforeSetupRender(LevelRenderer renderer, Camera camera) {
        LevelRendererAccessor access = (LevelRendererAccessor) renderer;
        ClientLevel level = access.alpha_omega$level();
        CubeGeometry cube = level == null ? null : Cube.of(level);
        if (cube == null) return;
        Vec3 cam = camera.getPosition();
        CubeFace now = cube.faceAt(cam.x, cam.z);
        CubeFace was = vanillaFace;
        vanillaFace = now;
        if (was == null || now == null || was == now) return;
        ViewArea incoming = AREAS.remove(now);
        if (incoming == null) return;
        ViewArea outgoing = access.alpha_omega$viewArea();
        access.alpha_omega$setViewArea(incoming);
        access.alpha_omega$sectionOcclusionGraph().waitAndReset(incoming);
        swapped = true;
        if (outgoing != null) {
            if (was.isNeighbour(now)) AREAS.put(was, outgoing);
            else outgoing.releaseAllBuffers();
        }
    }

    /**
     * After vanilla has scheduled its visible-section graph: in the frame of a swap, wait for that first rebuild, which
     * runs in the background. Until it lands the graph is empty, and the face the camera has just come onto would not
     * draw at all.
     */
    public static void afterOcclusionUpdate(LevelRenderer renderer) {
        if (!swapped) return;
        swapped = false;
        java.util.concurrent.Future<?> task = ((SectionOcclusionGraphAccessor) ((LevelRendererAccessor) renderer).alpha_omega$sectionOcclusionGraph())
            .alpha_omega$fullUpdateTask();
        if (task == null) return;
        try {
            task.get();
        } catch (Exception e) {
            AlphaOmegaMod.LOGGER.warn("Visible sections after crossing an edge did not rebuild", e);
        }
    }

    /** After vanilla has chosen and compiled its sections: choose, and compile, those of the neighbouring faces. */
    public static void setup(LevelRenderer renderer, Camera camera, Frustum frustum, int viewDistance) {
        LevelRendererAccessor access = (LevelRendererAccessor) renderer;
        ClientLevel level = access.alpha_omega$level();
        SectionRenderDispatcher sections = access.alpha_omega$sectionRenderDispatcher();
        CubeGeometry cube = level == null ? null : Cube.of(level);
        if (cube == null || sections == null) {
            if (!AREAS.isEmpty()) reset();
            return;
        }
        if (sections != dispatcher || level != areaLevel || viewDistance != areaViewDistance) {
            reset();
            dispatcher = sections;
            areaLevel = level;
            areaViewDistance = viewDistance;
        }
        geometry = cube;
        Vec3 cam = camera.getPosition();
        home = cube.faceAt(cam.x, cam.z);
        VISIBLE.clear();
        if (home == null) return;
        releaseForgotten(level, cube);
        RenderRegionCache cache = new RenderRegionCache();
        int budget = COMPILE_AHEAD_PER_FRAME;
        double reach = (viewDistance + 1) * 16.0;
        String only = System.getProperty("alpha_omega.dev.onlyFace");
        for (CubeFace face : CubeFace.values()) {
            if (!home.isNeighbour(face)) continue;
            if (only != null && !only.equals(face.name())) continue;
            ViewArea area = AREAS.computeIfAbsent(face, f -> new ViewArea(sections, level, viewDistance, renderer));
            double[] virtual = cube.transform(home, face, cam.x, cam.y, cam.z);
            area.repositionCamera(virtual[0], virtual[2]);
            List<SectionRenderDispatcher.RenderSection> visible = new ArrayList<>();
            for (SectionRenderDispatcher.RenderSection section : area.sections) {
                BlockPos origin = section.getOrigin();
                int chunkX = origin.getX() >> 4, chunkZ = origin.getZ() >> 4;
                if (cube.faceAtChunk(chunkX, chunkZ) != face || !cube.inFootprint(chunkX, chunkZ)) continue;
                if (level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) continue;
                AABB box = toHome(cube, face, home, new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 16, origin.getY() + 16, origin.getZ() + 16));
                if (box.getCenter().distanceTo(cam) > reach) continue;
                boolean inView = frustum.isVisible(box);
                if (inView) visible.add(section);
                // Compile out of view too (within a budget): crossing an edge turns the view a quarter turn, and the
                // face then becomes vanilla's, so whatever it shows should be ready.
                if (section.isDirty() && (inView || budget > 0) && level.getLightEngine().lightOnInSection(SectionPos.of(origin)) && section.hasAllNeighbors()) {
                    if (!inView) budget--;
                    section.rebuildSectionAsync(sections, cache);
                    section.setNotDirty();
                }
            }
            VISIBLE.put(face, visible);
        }
    }

    /**
     * Keeps the area of a face that is no longer a neighbour (the far side, after crossing) while the client still
     * holds any of its chunks, as it does while the server lets them linger: crossing back then finds it built.
     * Once they are all gone, so is the area.
     */
    private static void releaseForgotten(ClientLevel level, CubeGeometry cube) {
        AREAS.entrySet().removeIf(entry -> {
            CubeFace face = entry.getKey();
            if (home.isNeighbour(face) || face == home) return false;
            for (SectionRenderDispatcher.RenderSection section : entry.getValue().sections) {
                BlockPos origin = section.getOrigin();
                int chunkX = origin.getX() >> 4, chunkZ = origin.getZ() >> 4;
                if (cube.faceAtChunk(chunkX, chunkZ) == face && level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null) return false;
            }
            entry.getValue().releaseAllBuffers();
            return true;
        });
    }

    /** A box of one face's storage, in another's. */
    private static AABB toHome(CubeGeometry cube, CubeFace face, CubeFace home, AABB box) {
        double[] a = cube.transform(face, home, box.minX, box.minY, box.minZ);
        double[] b = cube.transform(face, home, box.maxX, box.maxY, box.maxZ);
        return new AABB(a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    private static Matrix3f rotation(CubeFace face, CubeFace home) {
        Matrix3f m = new Matrix3f();
        for (int j = 0; j < 3; j++) {
            double[] r = CubeGeometry.rotate(face, home, j == 0 ? 1 : 0, j == 1 ? 1 : 0, j == 2 ? 1 : 0);
            m.setColumn(j, (float) r[0], (float) r[1], (float) r[2]);
        }
        return m;
    }

    /** Draws one terrain layer of the neighbouring faces, with the shader vanilla has just drawn its own with. */
    public static void drawLayer(RenderType type, ShaderInstance shader, double camX, double camY, double camZ, Matrix4f modelView) {
        if (home == null || geometry == null || VISIBLE.isEmpty()) return;
        Uniform offset = shader.CHUNK_OFFSET;
        Uniform modelViewUniform = shader.MODEL_VIEW_MATRIX;
        // Vanilla's terrain fog is a cylinder about the vertex's own up axis, which on another face is sideways.
        Uniform fogShape = shader.FOG_SHAPE;
        if (fogShape != null) {
            fogShape.set(FogShape.SPHERE.getIndex());
            fogShape.upload();
        }
        boolean forward = type != RenderType.translucent();
        for (Map.Entry<CubeFace, List<SectionRenderDispatcher.RenderSection>> entry : VISIBLE.entrySet()) {
            List<SectionRenderDispatcher.RenderSection> sections = entry.getValue();
            if (sections.isEmpty()) continue;
            CubeFace face = entry.getKey();
            if (modelViewUniform != null) {
                modelViewUniform.set(new Matrix4f(modelView).mul(new Matrix4f().set(rotation(face, home))));
                modelViewUniform.upload();
            }
            double[] virtual = geometry.transform(home, face, camX, camY, camZ);
            for (int i = 0; i < sections.size(); i++) {
                SectionRenderDispatcher.RenderSection section = sections.get(forward ? i : sections.size() - 1 - i);
                if (section.getCompiled().isEmpty(type)) continue;
                BlockPos origin = section.getOrigin();
                if (offset != null) {
                    offset.set((float) (origin.getX() - virtual[0]), (float) (origin.getY() - virtual[1]), (float) (origin.getZ() - virtual[2]));
                    offset.upload();
                }
                VertexBuffer buffer = section.getBuffer(type);
                buffer.bind();
                buffer.draw();
            }
        }
        if (modelViewUniform != null) {
            modelViewUniform.set(modelView);
            modelViewUniform.upload();
        }
        if (fogShape != null) {
            fogShape.set(RenderSystem.getShaderFogShape().getIndex());
            fogShape.upload();
        }
    }

    /** Draws the entities standing on the neighbouring faces. */
    public static void renderEntities(LevelRenderer renderer, Camera camera, Frustum frustum, DeltaTracker delta, PoseStack pose, MultiBufferSource buffers) {
        if (home == null || geometry == null || VISIBLE.isEmpty()) return;
        ClientLevel level = ((LevelRendererAccessor) renderer).alpha_omega$level();
        Vec3 cam = camera.getPosition();
        for (Entity entity : level.entitiesForRendering()) {
            CubeFace face = geometry.faceAt(entity.getX(), entity.getZ());
            if (face == null || !VISIBLE.containsKey(face)) continue;
            double[] virtual = geometry.transform(home, face, cam.x, cam.y, cam.z);
            if (!entity.shouldRender(virtual[0], virtual[1], virtual[2])) continue;
            if (!frustum.isVisible(toHome(geometry, face, home, entity.getBoundingBoxForCulling()))) continue;
            float partialTick = delta.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity));
            pose.pushPose();
            pose.mulPose(new Quaternionf().setFromNormalized(rotation(face, home)));
            ((LevelRendererAccessor) renderer).alpha_omega$renderEntity(entity, virtual[0], virtual[1], virtual[2], partialTick, pose, buffers);
            pose.popPose();
        }
    }

    /** Draws the block entities in the neighbouring faces' visible sections. */
    public static void renderBlockEntities(LevelRenderer renderer, Camera camera, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                           BlockEntityRenderDispatcher dispatcher) {
        if (home == null || geometry == null || VISIBLE.isEmpty()) return;
        ClientLevel level = ((LevelRendererAccessor) renderer).alpha_omega$level();
        Vec3 cam = camera.getPosition();
        for (Map.Entry<CubeFace, List<SectionRenderDispatcher.RenderSection>> entry : VISIBLE.entrySet()) {
            CubeFace face = entry.getKey();
            double[] virtual = geometry.transform(home, face, cam.x, cam.y, cam.z);
            Vec3 virtualCam = new Vec3(virtual[0], virtual[1], virtual[2]);
            Quaternionf rotation = new Quaternionf().setFromNormalized(rotation(face, home));
            for (SectionRenderDispatcher.RenderSection section : entry.getValue()) {
                for (BlockEntity blockEntity : section.getCompiled().getRenderableBlockEntities()) {
                    BlockEntityRenderer<BlockEntity> blockEntityRenderer = dispatcher.getRenderer(blockEntity);
                    if (blockEntityRenderer == null || !blockEntityRenderer.shouldRender(blockEntity, virtualCam)) continue;
                    BlockPos pos = blockEntity.getBlockPos();
                    pose.pushPose();
                    pose.mulPose(rotation);
                    pose.translate(pos.getX() - virtual[0], pos.getY() - virtual[1], pos.getZ() - virtual[2]);
                    blockEntityRenderer.render(blockEntity, partialTick, pose, buffers, LevelRenderer.getLightColor(level, pos), OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
            }
        }
    }

    /** For F3: per neighbouring face, sections drawn (with any geometry) of those in view. */
    @Nullable
    public static String debugLine() {
        if (home == null || VISIBLE.isEmpty()) return null;
        StringBuilder line = new StringBuilder("Neighbours:");
        for (Map.Entry<CubeFace, List<SectionRenderDispatcher.RenderSection>> entry : VISIBLE.entrySet()) {
            long built = entry.getValue().stream().filter(section -> section.getCompiled() != SectionRenderDispatcher.CompiledSection.UNCOMPILED).count();
            line.append(' ').append(entry.getKey()).append(' ').append(built).append('/').append(entry.getValue().size());
        }
        return line.toString();
    }

    /**
     * A section changed. Vanilla's area indexes sections modulo its size, so a section of another face would mark an
     * unrelated one of ours: sections of other faces go to their own area instead. Returns whether it was handled.
     */
    public static boolean setDirty(ClientLevel level, int sectionX, int sectionY, int sectionZ, boolean playerChanged) {
        CubeGeometry cube = Cube.of(level);
        if (cube == null || home == null) return false;
        CubeFace face = cube.faceAtChunk(sectionX, sectionZ);
        if (face == home) return false;
        ViewArea area = face == null ? null : AREAS.get(face);
        if (area != null) {
            BlockPos origin = new BlockPos(sectionX << 4, sectionY << 4, sectionZ << 4);
            SectionRenderDispatcher.RenderSection section = ((ViewAreaAccessor) area).alpha_omega$getRenderSectionAt(origin);
            if (section != null && section.getOrigin().equals(origin)) section.setDirty(playerChanged);
        }
        return true;
    }
}
