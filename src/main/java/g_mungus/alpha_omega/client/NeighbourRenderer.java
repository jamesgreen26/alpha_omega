package g_mungus.alpha_omega.client;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.compat.sodium.Sodium;
import g_mungus.alpha_omega.compat.sodium.SodiumNeighbours;
import g_mungus.alpha_omega.mixin.client.CompiledSectionAccessor;
import g_mungus.alpha_omega.mixin.client.LevelRendererAccessor;
import g_mungus.alpha_omega.mixin.client.SectionOcclusionGraphAccessor;
import g_mungus.alpha_omega.mixin.client.ViewAreaAccessor;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * Draws images of the tile beyond the band ({@code orbifold-implementation.md} phase 6, "image renderer"). The camera's
 * own view is vanilla's; each active element {@code g} of {@code Γ} has its own {@link ViewArea}, centred on
 * {@code g⁻¹(camera)} in the same storage, whose sections compile like any others. They are drawn in each terrain
 * layer with {@code g}'s turn in the model-view matrix and offsets from that virtual camera; entities and block
 * entities there are drawn with the same turn.
 *
 * <p>Per frame this only walks what can be drawn: each area keeps, per column, whether its image shows it and it is
 * loaded, with its boxes as the camera sees them; a column is worked out again only when the area's move puts another
 * chunk in its slot, a chunk near it comes or goes, or a block near it changes. Columns are culled whole before their
 * sections are. Sections buried well below the ground around them are skipped while the camera is above ground.
 * Areas are never freed while the world lasts: one an image no longer needs is handed to the next image that does.
 *
 * <p>Inactive until phase 6: {@link #images} gives no images, so nothing is drawn. The area handling is v2's
 * neighbour renderer, keyed by image instead of face.
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

    /** A column slot worked out to hold nothing worth drawing (not shown by its image, or not loaded). */
    private static final Column NOTHING = new Column(new AABB(0, 0, 0, 0, 0, 0), new SectionRenderDispatcher.RenderSection[0], new AABB[0], 0);

    /** An image's view area, with its columns worked out. */
    private static final class Area {
        final ViewArea view;
        final int size;
        Motion image;
        /** The virtual camera's section (as {@code ViewArea.repositionCamera} rounds it) the area was last placed at. */
        long placedAt = Long.MIN_VALUE;
        /** Per column slot ({@code x * size + z}, as the view area wraps chunk positions): null until worked out. */
        final Column[] columns;
        /** Per column slot, the chunk it was worked out for: when the area moves, only slots whose chunk changed are redone. */
        final long[] columnAt;

        Area(ViewArea view, Motion image) {
            this.view = view;
            this.image = image;
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

    /** What one image draws this frame. */
    private record Draw(Motion image, Matrix4f rotation, Quaternionf quaternion, double[] camera, List<SectionRenderDispatcher.RenderSection> visible,
                        Map<RenderType, List<SectionRenderDispatcher.RenderSection>> layers) {
    }

    private static final Map<Motion, Area> AREAS = new LinkedHashMap<>();
    private static final Map<Motion, Draw> DRAWS = new LinkedHashMap<>();

    @Nullable
    private static SectionRenderDispatcher dispatcher;
    @Nullable
    private static ClientLevel areaLevel;
    private static int areaViewDistance;
    @Nullable
    private static OrbifoldGeometry geometry;
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
        swapped = false;
        geometry = null;
    }

    /**
     * The images the camera needs drawn: the elements {@code g} whose view around {@code g⁻¹(camera)} reaches tile
     * cells the band does not show (at most four). Phase 6 works them out; none until then.
     */
    public static List<Motion> images(OrbifoldGeometry geometry, Vec3 camera) {
        return List.of();
    }

    /** Whether an image's area draws a chunk column: phase 6 skips those whose image falls inside the home footprint. */
    private static boolean shows(OrbifoldGeometry geometry, Motion image, int chunkX, int chunkZ) {
        return true;
    }

    /**
     * Before vanilla positions its area for the frame. With faces, a crossing handed vanilla the area built for the face
     * the camera came onto. Phase 6: when the camera crosses by {@code g}, hand vanilla the area built for {@code g}.
     */
    public static void beforeSetupRender(LevelRenderer renderer, Camera camera) {
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

    /** After vanilla has chosen and compiled its sections: choose, and compile, those of the images. */
    public static void setup(LevelRenderer renderer, Camera camera, Frustum frustum, int viewDistance) {
        LevelRendererAccessor access = (LevelRendererAccessor) renderer;
        ClientLevel level = access.alpha_omega$level();
        SectionRenderDispatcher sections = access.alpha_omega$sectionRenderDispatcher();
        OrbifoldGeometry cube = level == null ? null : Orbifold.of(level);
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
        DRAWS.clear();
        List<Motion> images = images(cube, cam);
        if (images.isEmpty()) return;
        if (Sodium.loaded()) {
            // Sodium collects and draws the images' terrain (SodiumNeighbours); entities and block entities still
            // draw from here, each with its image's turn.
            for (Motion image : images) {
                DRAWS.put(image, new Draw(image, new Matrix4f().set(rotation(image)), new Quaternionf().setFromNormalized(rotation(image)),
                    virtualCamera(image, cam), List.of(), Map.of()));
            }
            return;
        }
        RenderRegionCache cache = new RenderRegionCache();
        int[] budget = {COMPILE_AHEAD_PER_FRAME};
        double reach = (viewDistance + 1) * 16.0;
        // Buried sections can only be seen from underground (through caves meeting at the seam).
        boolean underground = level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(cam.x), Mth.floor(cam.z)) > cam.y + 2.0;
        for (Motion image : images) {
            Area area = areaFor(image, images, level, sections, viewDistance, renderer);
            double[] virtual = virtualCamera(image, cam);
            long placedAt = ChunkPos.asLong(Math.floorDiv(Mth.ceil(virtual[0]), 16), Math.floorDiv(Mth.ceil(virtual[2]), 16));
            if (placedAt != area.placedAt) {
                // Sections that stay put keep their origins, so their columns stand; build redoes the slots that moved.
                area.view.repositionCamera(virtual[0], virtual[2]);
                area.placedAt = placedAt;
            }
            build(area, level, cube);
            Draw draw = new Draw(image, new Matrix4f().set(rotation(image)), new Quaternionf().setFromNormalized(rotation(image)), virtual,
                new ArrayList<>(), new IdentityHashMap<>());
            for (Column column : area.columns) {
                if (column == NOTHING) continue;
                if (distance(column.box, cam) > reach + SECTION_RADIUS) continue;
                boolean columnInView = frustum.isVisible(column.box);
                // Out of view, only compile ahead (within a budget): a crossing can make the image vanilla's, so
                // whatever it shows should be ready.
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
            DRAWS.put(image, draw);
        }
    }

    /** Where the camera is as seen from an image's storage: {@code g⁻¹(camera)}. */
    private static double[] virtualCamera(Motion image, Vec3 cam) {
        Vec3 v = Transform.of(image.inverse()).position(cam);
        return new double[] {v.x, v.y, v.z};
    }

    /**
     * The area for an image: its own if it has one, else one an image no longer needed has left behind (its buffers are
     * reused, its sections recompile for the new image), else a new one.
     */
    private static Area areaFor(Motion image, List<Motion> images, ClientLevel level, SectionRenderDispatcher sections, int viewDistance,
                                LevelRenderer renderer) {
        Area area = AREAS.get(image);
        if (area != null) return area;
        for (Area spare : AREAS.values()) {
            if (images.contains(spare.image)) continue;
            AREAS.remove(spare.image);
            spare.image = image;
            spare.placedAt = Long.MIN_VALUE;
            spare.invalidate();
            AREAS.put(image, spare);
            return spare;
        }
        area = new Area(new ViewArea(sections, level, viewDistance, renderer), image);
        AREAS.put(image, area);
        return area;
    }

    /** Works out the area's column slots not yet worked out: whether its image shows each and it is loaded, its boxes, its buried depth. */
    private static void build(Area area, ClientLevel level, OrbifoldGeometry cube) {
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
                if (!shows(cube, area.image, chunkX, chunkZ) || level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                    area.columns[slot] = NOTHING;
                    continue;
                }
                SectionRenderDispatcher.RenderSection[] column = new SectionRenderDispatcher.RenderSection[height];
                AABB[] boxes = new AABB[height];
                for (int y = 0; y < height; y++) {
                    column[y] = view.sections[(z * height + y) * size + x];
                    boxes[y] = toHome(area.image, column[y].getBoundingBox());
                }
                AABB whole = new AABB(chunkX * 16, level.getMinBuildHeight(), chunkZ * 16, chunkX * 16 + 16, level.getMaxBuildHeight(), chunkZ * 16 + 16);
                int ground = lowestGroundAround(level, chunkX, chunkZ);
                int buriedBelow = ground == Integer.MIN_VALUE ? Integer.MIN_VALUE : ground - BURIED_MARGIN;
                area.columns[slot] = new Column(toHome(area.image, whole), column, boxes, buriedBelow);
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

    /**
     * The squared distance a section counts as from the camera when it queues to compile: NaN where vanilla's own
     * distance applies. Phase 6: an image's sections count from that image's virtual camera, a little further.
     */
    public static double compileDistanceSqr(AABB box) {
        return Double.NaN;
    }

    /** Distance from a point to the nearest point of a box. */
    public static double distance(AABB box, Vec3 point) {
        double dx = Math.max(0.0, Math.max(box.minX - point.x, point.x - box.maxX));
        double dy = Math.max(0.0, Math.max(box.minY - point.y, point.y - box.maxY));
        double dz = Math.max(0.0, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** A box of storage, where an image shows it. */
    public static AABB toHome(Motion image, AABB box) {
        return Transform.of(image).box(box);
    }

    /** The turn taking directions of storage to where an image shows them. */
    public static Matrix3f rotation(Motion image) {
        return image.turned() ? new Matrix3f().scaling(-1.0F, 1.0F, -1.0F) : new Matrix3f();
    }

    /** Draws one terrain layer of the images, with the shader vanilla has just drawn its own with. */
    public static void drawLayer(RenderType type, ShaderInstance shader, double camX, double camY, double camZ, Matrix4f modelView) {
        if (DRAWS.isEmpty()) return;
        Uniform offset = shader.CHUNK_OFFSET;
        Uniform modelViewUniform = shader.MODEL_VIEW_MATRIX;
        // A turn about Y keeps vanilla's cylindrical terrain fog right, so the fog shape stays.
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
    }

    /** Draws the entities each image shows, with its turn. */
    public static void renderEntities(LevelRenderer renderer, Camera camera, Frustum frustum, DeltaTracker delta, PoseStack pose, MultiBufferSource buffers) {
        if (geometry == null || DRAWS.isEmpty()) return;
        ClientLevel level = ((LevelRendererAccessor) renderer).alpha_omega$level();
        for (Entity entity : level.entitiesForRendering()) {
            for (Draw draw : DRAWS.values()) {
                if (!shows(geometry, draw.image, entity.getBlockX() >> 4, entity.getBlockZ() >> 4)) continue;
                double[] virtual = draw.camera;
                if (!entity.shouldRender(virtual[0], virtual[1], virtual[2])) continue;
                if (!frustum.isVisible(toHome(draw.image, entity.getBoundingBoxForCulling()))) continue;
                float partialTick = delta.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity));
                pose.pushPose();
                pose.mulPose(draw.quaternion);
                ((LevelRendererAccessor) renderer).alpha_omega$renderEntity(entity, virtual[0], virtual[1], virtual[2], partialTick, pose, buffers);
                pose.popPose();
            }
        }
    }

    /** Draws the block entities in the images' visible sections. */
    public static void renderBlockEntities(LevelRenderer renderer, Camera camera, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                           BlockEntityRenderDispatcher dispatcher) {
        if (DRAWS.isEmpty()) return;
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
                SodiumNeighbours.forEachBlockEntity(draw.image, render);
            } else {
                for (SectionRenderDispatcher.RenderSection section : draw.visible) section.getCompiled().getRenderableBlockEntities().forEach(render);
            }
        }
    }

    /** For F3: per image, sections drawn (with any geometry) of those in view. */
    @Nullable
    public static String debugLine() {
        if (Sodium.loaded()) return SodiumNeighbours.debugLine();
        if (DRAWS.isEmpty()) return null;
        StringBuilder line = new StringBuilder("Images:");
        for (Draw draw : DRAWS.values()) {
            long built = draw.visible.stream().filter(section -> section.getCompiled() != SectionRenderDispatcher.CompiledSection.UNCOMPILED).count();
            line.append(' ').append(draw.image).append(' ').append(built).append('/').append(draw.visible.size());
        }
        return line.toString();
    }

    /**
     * A section changed. It is one storage, so vanilla marks its own; the same section in each image's area is marked
     * here too (Sodium keeps its sections by position, so it needs nothing). Returns whether vanilla should skip it:
     * never. Most of these are light changes (every section of an arriving chunk, with its neighbours), which move no
     * ground: that is left to {@link #blockChanged}.
     */
    public static boolean setDirty(ClientLevel level, int sectionX, int sectionY, int sectionZ, boolean playerChanged) {
        if (Sodium.loaded() || AREAS.isEmpty()) return false;
        BlockPos origin = new BlockPos(sectionX << 4, sectionY << 4, sectionZ << 4);
        for (Area area : AREAS.values()) {
            SectionRenderDispatcher.RenderSection section = ((ViewAreaAccessor) area.view).alpha_omega$getRenderSectionAt(origin);
            if (section != null && section.getOrigin().equals(origin)) section.setDirty(playerChanged);
        }
        return false;
    }

    /** A block changed: its chunk's ground may have moved, and with it the buried depth of the columns around it. */
    public static void blockChanged(ClientLevel level, BlockPos pos) {
        chunkChanged(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** A chunk arrived or was forgotten: the columns around it are worked out again. */
    public static void chunkChanged(ClientLevel level, int chunkX, int chunkZ) {
        GROUND.remove(ChunkPos.asLong(chunkX, chunkZ));
        for (Area area : AREAS.values()) area.invalidateAround(chunkX, chunkZ);
        if (Sodium.loaded()) SodiumNeighbours.invalidateAround(chunkX, chunkZ);
    }
}
