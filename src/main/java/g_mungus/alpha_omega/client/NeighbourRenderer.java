package g_mungus.alpha_omega.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.compat.sodium.Sodium;
import g_mungus.alpha_omega.compat.sodium.SodiumNeighbours;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.mixin.client.CompiledSectionAccessor;
import g_mungus.alpha_omega.mixin.client.LevelRendererAccessor;
import g_mungus.alpha_omega.mixin.client.SectionOcclusionGraphAccessor;
import g_mungus.alpha_omega.mixin.client.ViewAreaAccessor;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
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
 *
 * <p>Per frame this only walks what can be drawn: each area keeps, per column, whether it lies on its face and is
 * loaded, with its boxes seen from home; a column is worked out again only when the area's move puts another chunk in
 * its slot, a chunk near it comes or goes, a block near it changes, or the camera changes face. Columns are culled
 * whole before their sections are. Sections buried well below the ground around them are skipped while the camera is
 * above ground. Areas are never freed while the
 * world lasts: one a face no longer needs is handed to the next face that does, so crossing edges neither frees nor
 * allocates GPU buffers.
 */
public final class NeighbourRenderer {

    /** Out-of-view neighbour sections compiled per frame. */
    private static final int COMPILE_AHEAD_PER_FRAME = 24;
    /** How far below the lowest ground around its chunk a section's top must be to count as buried. */
    public static final int BURIED_MARGIN = 16;
    /** Half the diagonal of a section, for culling whole columns by distance. */
    public static final double SECTION_RADIUS = 14.0;

    /** One loaded column of an area: its sections bottom to top, their boxes seen from home, and where buried begins. */
    private record Column(AABB box, SectionRenderDispatcher.RenderSection[] sections, AABB[] boxes, int buriedBelow) {
    }

    /** A column slot worked out to hold nothing worth drawing (off the face, outside the footprint, or not loaded). */
    private static final Column NOTHING = new Column(new AABB(0, 0, 0, 0, 0, 0), new SectionRenderDispatcher.RenderSection[0], new AABB[0], 0);

    /** A neighbouring face's view area, with its columns worked out. */
    private static final class Area {
        final ViewArea view;
        final int size;
        CubeFace face;
        /** The virtual camera's section (as {@code ViewArea.repositionCamera} rounds it) the area was last placed at. */
        long placedAt = Long.MIN_VALUE;
        @Nullable
        CubeFace builtFor;
        /** Per column slot ({@code x * size + z}, as the view area wraps chunk positions): null until worked out. */
        final Column[] columns;
        /** Per column slot, the chunk it was worked out for: when the area moves, only slots whose chunk changed are redone. */
        final long[] columnAt;

        Area(ViewArea view, CubeFace face) {
            this.view = view;
            this.face = face;
            this.size = view.getViewDistance() * 2 + 1;
            this.columns = new Column[this.size * this.size];
            this.columnAt = new long[this.size * this.size];
        }

        void invalidate() {
            java.util.Arrays.fill(this.columns, null);
        }

        /** Forgets the columns of a chunk and the eight around it (their buried depth depends on it). */
        void invalidateAround(int chunkX, int chunkZ) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    this.columns[Math.floorMod(chunkX + dx, this.size) * this.size + Math.floorMod(chunkZ + dz, this.size)] = null;
                }
            }
        }
    }

    /** The lowest ground in each chunk the renderer has looked at, until the chunk changes. */
    private static final Long2IntOpenHashMap GROUND = new Long2IntOpenHashMap();

    /** What one neighbouring face draws this frame. */
    private record Draw(CubeFace face, Matrix4f rotation, Quaternionf quaternion, double[] camera, List<SectionRenderDispatcher.RenderSection> visible,
                        Map<RenderType, List<SectionRenderDispatcher.RenderSection>> layers) {
    }

    private static final Map<CubeFace, Area> AREAS = new EnumMap<>(CubeFace.class);
    private static final Map<CubeFace, Draw> DRAWS = new EnumMap<>(CubeFace.class);

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
        AREAS.values().forEach(area -> area.view.releaseAllBuffers());
        AREAS.clear();
        DRAWS.clear();
        GROUND.clear();
        if (Sodium.loaded()) SodiumNeighbours.reset();
        home = null;
        vanillaFace = null;
        swapped = false;
        geometry = null;
    }

    /**
     * Before vanilla positions its area for the frame: if the camera has crossed to another face, hand vanilla the
     * area already built for that face and keep vanilla's old one for the face it left, so neither face recompiles.
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
        Area incoming = AREAS.remove(now);
        if (incoming == null) return;
        ViewArea outgoing = access.alpha_omega$viewArea();
        access.alpha_omega$setViewArea(incoming.view);
        access.alpha_omega$sectionOcclusionGraph().waitAndReset(incoming.view);
        swapped = true;
        if (outgoing != null) AREAS.put(was, new Area(outgoing, was));
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
        DRAWS.clear();
        if (home == null) return;
        if (Sodium.loaded()) {
            // Sodium collects and draws the neighbours' terrain (SodiumNeighbours); entities and block entities still
            // draw from here, in each face's frame.
            for (CubeFace face : CubeFace.values()) {
                if (!home.isNeighbour(face)) continue;
                DRAWS.put(face, new Draw(face, new Matrix4f().set(rotation(face, home)), new Quaternionf().setFromNormalized(rotation(face, home)),
                    cube.transform(home, face, cam.x, cam.y, cam.z), List.of(), Map.of()));
            }
            return;
        }
        RenderRegionCache cache = new RenderRegionCache();
        int[] budget = {COMPILE_AHEAD_PER_FRAME};
        double reach = (viewDistance + 1) * 16.0;
        // Buried sections can only be seen from underground (through caves meeting at the edge).
        boolean underground = level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(cam.x), Mth.floor(cam.z)) > cam.y + 2.0;
        String only = System.getProperty("alpha_omega.dev.onlyFace");
        for (CubeFace face : CubeFace.values()) {
            if (!home.isNeighbour(face)) continue;
            if (only != null && !only.equals(face.name())) continue;
            Area area = areaFor(face, level, sections, viewDistance, renderer);
            double[] virtual = cube.transform(home, face, cam.x, cam.y, cam.z);
            long placedAt = ChunkPos.asLong(Math.floorDiv(Mth.ceil(virtual[0]), 16), Math.floorDiv(Mth.ceil(virtual[2]), 16));
            if (placedAt != area.placedAt) {
                // Sections that stay put keep their origins, so their columns stand; build redoes the slots that moved.
                area.view.repositionCamera(virtual[0], virtual[2]);
                area.placedAt = placedAt;
            }
            if (area.builtFor != home) {
                area.builtFor = home;
                area.invalidate();
            }
            build(area, level, cube);
            Draw draw = new Draw(face, new Matrix4f().set(rotation(face, home)), new Quaternionf().setFromNormalized(rotation(face, home)), virtual,
                new ArrayList<>(), new IdentityHashMap<>());
            for (Column column : area.columns) {
                if (column == NOTHING) continue;
                if (distance(column.box, cam) > reach + SECTION_RADIUS) continue;
                boolean columnInView = frustum.isVisible(column.box);
                // Out of view, only compile ahead (within a budget): crossing an edge turns the view a quarter turn,
                // and the face then becomes vanilla's, so whatever it shows should be ready.
                if (!columnInView && budget[0] <= 0) continue;
                for (int y = column.sections.length - 1; y >= 0; y--) {
                    SectionRenderDispatcher.RenderSection section = column.sections[y];
                    if (!underground && section.getOrigin().getY() + 16 <= column.buriedBelow) break;
                    AABB box = column.boxes[y];
                    if (distance(box, cam) > reach) continue;
                    boolean inView = columnInView && frustum.isVisible(box);
                    if (inView) {
                        draw.visible.add(section);
                        for (RenderType layer : ((CompiledSectionAccessor) section.getCompiled()).alpha_omega$hasBlocks()) {
                            draw.layers.computeIfAbsent(layer, l -> new ArrayList<>()).add(section);
                        }
                    }
                    if (section.isDirty() && (inView || budget[0] > 0) && level.getLightEngine().lightOnInSection(SectionPos.of(section.getOrigin()))
                        && section.hasAllNeighbors()) {
                        if (!inView) budget[0]--;
                        section.rebuildSectionAsync(sections, cache);
                        section.setNotDirty();
                    }
                }
            }
            DRAWS.put(face, draw);
        }
    }

    /**
     * The area for a face: its own if it has one, else one a face no longer near has left behind (its buffers are
     * reused, its sections recompile for the new face), else a new one.
     */
    private static Area areaFor(CubeFace face, ClientLevel level, SectionRenderDispatcher sections, int viewDistance, LevelRenderer renderer) {
        Area area = AREAS.get(face);
        if (area != null) return area;
        for (Area spare : AREAS.values()) {
            if (spare.face == home || home.isNeighbour(spare.face)) continue;
            AREAS.remove(spare.face);
            spare.face = face;
            spare.placedAt = Long.MIN_VALUE;
            spare.invalidate();
            AREAS.put(face, spare);
            return spare;
        }
        area = new Area(new ViewArea(sections, level, viewDistance, renderer), face);
        AREAS.put(face, area);
        return area;
    }

    /** Works out the area's column slots not yet worked out: whether each is on its face and loaded, its boxes, its buried depth. */
    private static void build(Area area, ClientLevel level, CubeGeometry cube) {
        ViewArea view = area.view;
        int size = area.size;
        int height = level.getSectionsCount();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                SectionRenderDispatcher.RenderSection bottom = view.sections[z * height * size + x];
                int chunkX = bottom.getOrigin().getX() >> 4, chunkZ = bottom.getOrigin().getZ() >> 4;
                int slot = Math.floorMod(chunkX, size) * size + Math.floorMod(chunkZ, size);
                long at = ChunkPos.asLong(chunkX, chunkZ);
                if (area.columns[slot] != null && area.columnAt[slot] == at) continue;
                area.columnAt[slot] = at;
                if (cube.faceAtChunk(chunkX, chunkZ) != area.face || !cube.inFootprint(chunkX, chunkZ)
                    || level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                    area.columns[slot] = NOTHING;
                    continue;
                }
                SectionRenderDispatcher.RenderSection[] column = new SectionRenderDispatcher.RenderSection[height];
                AABB[] boxes = new AABB[height];
                for (int y = 0; y < height; y++) {
                    column[y] = view.sections[(z * height + y) * size + x];
                    boxes[y] = toHome(cube, area.face, home, column[y].getBoundingBox());
                }
                AABB whole = new AABB(chunkX * 16, level.getMinBuildHeight(), chunkZ * 16, chunkX * 16 + 16, level.getMaxBuildHeight(), chunkZ * 16 + 16);
                int ground = lowestGroundAround(level, chunkX, chunkZ);
                int buriedBelow = ground == Integer.MIN_VALUE ? Integer.MIN_VALUE : ground - BURIED_MARGIN;
                area.columns[slot] = new Column(toHome(cube, area.face, home, whole), column, boxes, buriedBelow);
            }
        }
    }

    /** The lowest ground in a chunk and the eight around it, or the bottom of the world if any of them is missing. */
    public static int lowestGroundAround(ClientLevel level, int chunkX, int chunkZ) {
        int ground = Integer.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long key = ChunkPos.asLong(chunkX + dx, chunkZ + dz);
                int chunkGround;
                if (GROUND.containsKey(key)) {
                    chunkGround = GROUND.get(key);
                } else {
                    LevelChunk chunk = level.getChunkSource().getChunk(chunkX + dx, chunkZ + dz, ChunkStatus.FULL, false);
                    chunkGround = chunk == null ? Integer.MIN_VALUE : lowestGround(chunk);
                    // A missing chunk is not remembered: it is worked out again once it arrives.
                    if (chunk != null) GROUND.put(key, chunkGround);
                }
                ground = Math.min(ground, chunkGround);
            }
        }
        return ground;
    }

    private static int lowestGround(LevelChunk chunk) {
        int ground = Integer.MAX_VALUE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) ground = Math.min(ground, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
        }
        return ground;
    }

    /** How much further than its true distance a neighbour section counts when sections queue to compile, in blocks. */
    private static final double HOME_LEAD_BLOCKS = 32.0;

    /**
     * The squared distance a section counts as from the camera when it queues to compile: for a section of a
     * neighbouring face, from the camera as seen from that face's storage, {@link #HOME_LEAD_BLOCKS} further; NaN for
     * one of the camera's own face (or outside a cube world), where vanilla's own distance applies.
     */
    public static double compileDistanceSqr(AABB box) {
        Minecraft minecraft = Minecraft.getInstance();
        CubeGeometry cube = minecraft.level == null ? null : Cube.of(minecraft.level);
        if (cube == null) return Double.NaN;
        Vec3 cam = minecraft.gameRenderer.getMainCamera().getPosition();
        CubeFace camera = cube.faceAt(cam.x, cam.z);
        CubeFace face = cube.faceAt(box.minX + 8.0, box.minZ + 8.0);
        if (camera == null || face == null || face == camera) return Double.NaN;
        double[] virtual = cube.transform(camera, face, cam.x, cam.y, cam.z);
        double distance = Math.sqrt(Mth.square(box.minX + 8.0 - virtual[0]) + Mth.square(box.minY + 8.0 - virtual[1]) + Mth.square(box.minZ + 8.0 - virtual[2]));
        return Mth.square(distance + HOME_LEAD_BLOCKS);
    }

    /** Distance from a point to the nearest point of a box. */
    public static double distance(AABB box, Vec3 point) {
        double dx = Math.max(0.0, Math.max(box.minX - point.x, point.x - box.maxX));
        double dy = Math.max(0.0, Math.max(box.minY - point.y, point.y - box.maxY));
        double dz = Math.max(0.0, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** A box of one face's storage, in another's. */
    public static AABB toHome(CubeGeometry cube, CubeFace face, CubeFace home, AABB box) {
        double[] a = cube.transform(face, home, box.minX, box.minY, box.minZ);
        double[] b = cube.transform(face, home, box.maxX, box.maxY, box.maxZ);
        return new AABB(a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    /** The rotation taking directions of one face's storage to another's. */
    public static Matrix3f rotation(CubeFace face, CubeFace home) {
        Matrix3f m = new Matrix3f();
        for (int j = 0; j < 3; j++) {
            double[] r = CubeGeometry.rotate(face, home, j == 0 ? 1 : 0, j == 1 ? 1 : 0, j == 2 ? 1 : 0);
            m.setColumn(j, (float) r[0], (float) r[1], (float) r[2]);
        }
        return m;
    }

    /** Draws one terrain layer of the neighbouring faces, with the shader vanilla has just drawn its own with. */
    public static void drawLayer(RenderType type, ShaderInstance shader, double camX, double camY, double camZ, Matrix4f modelView) {
        if (home == null || DRAWS.isEmpty()) return;
        Uniform offset = shader.CHUNK_OFFSET;
        Uniform modelViewUniform = shader.MODEL_VIEW_MATRIX;
        // Vanilla's terrain fog is a cylinder about the vertex's own up axis, which on another face is sideways.
        Uniform fogShape = shader.FOG_SHAPE;
        if (fogShape != null) {
            fogShape.set(FogShape.SPHERE.getIndex());
            fogShape.upload();
        }
        boolean forward = type != RenderType.translucent();
        for (Draw draw : DRAWS.values()) {
            List<SectionRenderDispatcher.RenderSection> sections = draw.layers.get(type);
            if (sections == null) continue;
            if (modelViewUniform != null) {
                modelViewUniform.set(new Matrix4f(modelView).mul(draw.rotation));
                modelViewUniform.upload();
            }
            double[] virtual = draw.camera;
            for (int i = 0; i < sections.size(); i++) {
                SectionRenderDispatcher.RenderSection section = sections.get(forward ? i : sections.size() - 1 - i);
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
        if (home == null || geometry == null || DRAWS.isEmpty()) return;
        ClientLevel level = ((LevelRendererAccessor) renderer).alpha_omega$level();
        for (Entity entity : level.entitiesForRendering()) {
            CubeFace face = geometry.faceAt(entity.getX(), entity.getZ());
            Draw draw = face == null ? null : DRAWS.get(face);
            if (draw == null) continue;
            double[] virtual = draw.camera;
            if (!entity.shouldRender(virtual[0], virtual[1], virtual[2])) continue;
            if (!frustum.isVisible(toHome(geometry, face, home, entity.getBoundingBoxForCulling()))) continue;
            float partialTick = delta.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity));
            pose.pushPose();
            pose.mulPose(draw.quaternion);
            ((LevelRendererAccessor) renderer).alpha_omega$renderEntity(entity, virtual[0], virtual[1], virtual[2], partialTick, pose, buffers);
            pose.popPose();
        }
    }

    /** Draws the block entities in the neighbouring faces' visible sections. */
    public static void renderBlockEntities(LevelRenderer renderer, Camera camera, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                           BlockEntityRenderDispatcher dispatcher) {
        if (home == null || DRAWS.isEmpty()) return;
        ClientLevel level = ((LevelRendererAccessor) renderer).alpha_omega$level();
        for (Draw draw : DRAWS.values()) {
            double[] virtual = draw.camera;
            Vec3 virtualCam = new Vec3(virtual[0], virtual[1], virtual[2]);
            Quaternionf rotation = draw.quaternion;
            Consumer<BlockEntity> render = blockEntity -> {
                BlockEntityRenderer<BlockEntity> blockEntityRenderer = dispatcher.getRenderer(blockEntity);
                if (blockEntityRenderer == null || !blockEntityRenderer.shouldRender(blockEntity, virtualCam)) return;
                BlockPos pos = blockEntity.getBlockPos();
                pose.pushPose();
                pose.mulPose(rotation);
                pose.translate(pos.getX() - virtual[0], pos.getY() - virtual[1], pos.getZ() - virtual[2]);
                blockEntityRenderer.render(blockEntity, partialTick, pose, buffers, LevelRenderer.getLightColor(level, pos), OverlayTexture.NO_OVERLAY);
                pose.popPose();
            };
            if (Sodium.loaded()) {
                SodiumNeighbours.forEachBlockEntity(draw.face, home, render);
            } else {
                for (SectionRenderDispatcher.RenderSection section : draw.visible) section.getCompiled().getRenderableBlockEntities().forEach(render);
            }
        }
    }

    /** For F3: per neighbouring face, sections drawn (with any geometry) of those in view. */
    @Nullable
    public static String debugLine() {
        if (Sodium.loaded()) return SodiumNeighbours.debugLine();
        if (home == null || DRAWS.isEmpty()) return null;
        StringBuilder line = new StringBuilder("Neighbours:");
        for (Draw draw : DRAWS.values()) {
            long built = draw.visible.stream().filter(section -> section.getCompiled() != SectionRenderDispatcher.CompiledSection.UNCOMPILED).count();
            line.append(' ').append(draw.face).append(' ').append(built).append('/').append(draw.visible.size());
        }
        return line.toString();
    }

    /**
     * A section changed. Vanilla's area indexes sections modulo its size, so a section of another face would mark an
     * unrelated one of ours: sections of other faces go to their own area instead. Returns whether it was handled.
     * Most of these are light changes (every section of an arriving chunk, with its neighbours), which move no ground:
     * that is left to {@link #blockChanged}.
     */
    public static boolean setDirty(ClientLevel level, int sectionX, int sectionY, int sectionZ, boolean playerChanged) {
        // Sodium keeps its sections by position, so every face's are its own to mark.
        if (Sodium.loaded()) return false;
        CubeGeometry cube = Cube.of(level);
        if (cube == null || home == null) return false;
        CubeFace face = cube.faceAtChunk(sectionX, sectionZ);
        // Sections outside every face (Sable's plots) are not ours: vanilla's path is where their own renderer hears of them.
        if (face == home || face == null) return false;
        Area area = AREAS.get(face);
        if (area != null) {
            BlockPos origin = new BlockPos(sectionX << 4, sectionY << 4, sectionZ << 4);
            SectionRenderDispatcher.RenderSection section = ((ViewAreaAccessor) area.view).alpha_omega$getRenderSectionAt(origin);
            if (section != null && section.getOrigin().equals(origin)) section.setDirty(playerChanged);
        }
        return true;
    }

    /** A block changed: its chunk's ground may have moved, and with it the buried depth of the columns around it. */
    public static void blockChanged(ClientLevel level, BlockPos pos) {
        CubeGeometry cube = Cube.of(level);
        if (cube == null) return;
        int chunkX = pos.getX() >> 4, chunkZ = pos.getZ() >> 4;
        // On the home face too, which becomes a neighbour once the camera crosses.
        GROUND.remove(ChunkPos.asLong(chunkX, chunkZ));
        CubeFace face = cube.faceAtChunk(chunkX, chunkZ);
        Area area = face == null ? null : AREAS.get(face);
        if (area != null) area.invalidateAround(chunkX, chunkZ);
        if (face != null && Sodium.loaded()) SodiumNeighbours.invalidateAround(face, chunkX, chunkZ);
    }

    /** A chunk arrived or was forgotten: the columns around it are worked out again. */
    public static void chunkChanged(ClientLevel level, int chunkX, int chunkZ) {
        GROUND.remove(ChunkPos.asLong(chunkX, chunkZ));
        CubeGeometry cube = Cube.of(level);
        CubeFace face = cube == null ? null : cube.faceAtChunk(chunkX, chunkZ);
        Area area = face == null ? null : AREAS.get(face);
        if (area != null) area.invalidateAround(chunkX, chunkZ);
        if (face != null && Sodium.loaded()) SodiumNeighbours.invalidateAround(face, chunkX, chunkZ);
    }
}
