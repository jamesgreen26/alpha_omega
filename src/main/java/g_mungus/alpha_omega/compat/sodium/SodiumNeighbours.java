package g_mungus.alpha_omega.compat.sodium;

import g_mungus.alpha_omega.client.ImageRenderer;
import g_mungus.alpha_omega.mixin.compat.sodium.RenderSectionManagerAccessor;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import net.caffeinemc.mods.sodium.api.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.TaskQueueType;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.OcclusionSectionCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SectionCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/**
 * Images of the tile drawn through Sodium (phase 6). Sodium is told of every chunk the client holds, so it keeps a
 * section for each; an image's sections are then built by Sodium like the camera's own.
 *
 * <p>When Sodium searches for visible sections, each image's are collected here from the camera as seen from that
 * image ({@code g⁻¹(camera)}): per chunk column within view, shown by the image and loaded, with its boxes as the camera
 * sees them and the depth below which it is buried, worked out again only when its chunk or one near it changes (as
 * in {@link ImageRenderer}). Sections in view go into the image's own render lists; those out of view only queue
 * to build. Each terrain layer of an image then draws with its turn in the model-view matrix and Sodium's camera at
 * the image's virtual camera.
 *
 * <p>Inactive until phase 6: {@link ImageRenderer#images} gives no images.
 */
public final class SodiumNeighbours {

    /** One loaded column: its chunk, its boxes seen from home (whole and per section), and where buried begins. */
    private record Column(int chunkX, int chunkZ, AABB box, AABB[] boxes, int buriedBelow) {
    }

    /** A column slot worked out to hold nothing worth drawing (not shown by its image, or not loaded). */
    private static final Column NOTHING = new Column(0, 0, new AABB(0, 0, 0, 0, 0, 0), new AABB[0], 0);

    /** An image: its columns around the virtual camera, and what Sodium draws of it. */
    private static final class Face {
        final int size;
        /** Per column slot ({@code x * size + z}, wrapping chunk positions): null until worked out. */
        final Column[] columns;
        /** Per column slot, the chunk it was worked out for. */
        final long[] columnAt;
        /** Collected in the current search, until Sodium finalises its lists. */
        @Nullable
        SectionCollector pending;
        @Nullable
        double[] pendingCamera;
        SortedRenderLists lists = SortedRenderLists.empty();
        int visible;
        int built;

        Face(int viewDistance) {
            this.size = viewDistance * 2 + 1;
            this.columns = new Column[this.size * this.size];
            this.columnAt = new long[this.size * this.size];
        }

        void invalidate() {
            java.util.Arrays.fill(this.columns, null);
        }

        void invalidateAround(int chunkX, int chunkZ) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    this.columns[Math.floorMod(chunkX + dx, this.size) * this.size + Math.floorMod(chunkZ + dz, this.size)] = null;
                }
            }
        }
    }

    private static final Map<Motion, Face> FACES = new LinkedHashMap<>();
    /** The section manager the faces' lists belong to; a new one (Sodium reloaded) starts them again. */
    @Nullable
    private static RenderSectionManager manager;

    private SodiumNeighbours() {
    }

    /** A chunk arrived: Sodium learns of it here, since the orbifold's chunk store bypasses vanilla's. */
    public static void chunkLoaded(ClientLevel level, int chunkX, int chunkZ) {
        ChunkTrackerHolder.get(level).onChunkStatusAdded(chunkX, chunkZ, net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus.FLAG_HAS_BLOCK_DATA);
    }

    public static void chunkDropped(ClientLevel level, int chunkX, int chunkZ) {
        ChunkTrackerHolder.get(level).onChunkStatusRemoved(chunkX, chunkZ, net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus.FLAG_HAS_BLOCK_DATA);
    }

    /** Forgets every image (new level, view distance or renderer). */
    public static void reset() {
        FACES.clear();
    }

    /** A chunk or a block in it changed: the columns around it are worked out again. */
    public static void invalidateAround(int chunkX, int chunkZ) {
        for (Face state : FACES.values()) state.invalidateAround(chunkX, chunkZ);
    }

    /**
     * After Sodium has searched from the camera: collects each image's sections, and adds those to build to
     * Sodium's queues. Returns whether a section in view is still building, so the search should run again.
     */
    public static boolean collect(RenderSectionManager sections, Camera camera, int frame) {
        if (sections != manager) {
            FACES.clear();
            manager = sections;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        OrbifoldGeometry cube = level == null ? null : Orbifold.of(level);
        Vec3 cam = camera.getPosition();
        List<Motion> images = cube == null ? List.of() : ImageRenderer.images(cube, cam);
        if (images.isEmpty()) {
            FACES.clear();
            return false;
        }
        RenderSectionManagerAccessor access = (RenderSectionManagerAccessor) sections;
        Long2ReferenceMap<RenderSection> byPosition = access.alpha_omega$sections();
        Map<TaskQueueType, ArrayDeque<RenderSection>> taskLists = access.alpha_omega$taskLists();
        TaskQueueType rebuildQueue = SodiumClientMod.options().performance.chunkBuildDeferMode.getImportantRebuildQueueType();
        TaskQueueType sortQueue = access.alpha_omega$sortBehavior().getDeferMode().getImportantRebuildQueueType();
        Frustum frustum = minecraft.levelRenderer.getFrustum();
        int viewDistance = minecraft.options.getEffectiveRenderDistance();
        double reach = (viewDistance + 1) * 16.0;
        // Buried sections can only be seen from underground (through caves meeting at the seam).
        boolean underground = level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(cam.x), Mth.floor(cam.z)) > cam.y + 2.0;
        boolean revisit = false;
        FACES.keySet().retainAll(images);
        for (Motion image : images) {
            Face state = FACES.get(image);
            if (state == null || state.size != viewDistance * 2 + 1) FACES.put(image, state = new Face(viewDistance));
            Vec3 v = Transform.of(image.inverse()).position(cam);
            double[] virtual = {v.x, v.y, v.z};
            build(state, image, level, Math.floorDiv(Mth.floor(virtual[0]), 16), Math.floorDiv(Mth.floor(virtual[2]), 16));
            SectionCollector collector = new OcclusionSectionCollector(frame, rebuildQueue, sortQueue);
            // Out of view only queues to build; a build still running there need not search again.
            SectionCollector ahead = new OcclusionSectionCollector(frame, rebuildQueue, sortQueue);
            state.visible = 0;
            state.built = 0;
            for (Column column : state.columns) {
                if (column == NOTHING) continue;
                if (ImageRenderer.distance(column.box, cam) > reach + ImageRenderer.SECTION_RADIUS) continue;
                boolean columnInView = frustum.isVisible(column.box);
                for (int y = column.boxes.length - 1; y >= 0; y--) {
                    int sectionY = level.getMinSection() + y;
                    if (!underground && SectionPos.sectionToBlockCoord(sectionY) + 16 <= column.buriedBelow) break;
                    AABB box = column.boxes[y];
                    if (ImageRenderer.distance(box, cam) > reach) continue;
                    RenderSection section = byPosition.get(SectionPos.asLong(column.chunkX, sectionY, column.chunkZ));
                    if (section == null) continue;
                    if (columnInView && frustum.isVisible(box)) {
                        collector.visit(section);
                        state.visible++;
                        if (section.isBuilt()) state.built++;
                    } else {
                        // Out of view it only queues to build: a crossing can make the image Sodium's own, so
                        // whatever it shows should be ready.
                        ahead.visitWithFlags(section, 0);
                    }
                }
            }
            collector.getTaskLists().forEach((type, queue) -> taskLists.get(type).addAll(queue));
            ahead.getTaskLists().forEach((type, queue) -> taskLists.get(type).addAll(queue));
            revisit |= collector.needsRevisitForPendingUpdates();
            state.pending = collector;
            state.pendingCamera = virtual;
        }
        return revisit;
    }

    /** Works out the image's column slots around the virtual camera's chunk not yet worked out for their chunk. */
    private static void build(Face state, Motion image, ClientLevel level, int centreX, int centreZ) {
        int size = state.size;
        int radius = size / 2;
        int height = level.getSectionsCount();
        for (int chunkX = centreX - radius; chunkX <= centreX + radius; chunkX++) {
            for (int chunkZ = centreZ - radius; chunkZ <= centreZ + radius; chunkZ++) {
                int slot = Math.floorMod(chunkX, size) * size + Math.floorMod(chunkZ, size);
                long at = ChunkPos.asLong(chunkX, chunkZ);
                if (state.columns[slot] != null && state.columnAt[slot] == at) continue;
                state.columnAt[slot] = at;
                if (level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                    state.columns[slot] = NOTHING;
                    continue;
                }
                AABB[] boxes = new AABB[height];
                for (int y = 0; y < height; y++) {
                    int minY = SectionPos.sectionToBlockCoord(level.getMinSection() + y);
                    boxes[y] = ImageRenderer.toHome(image, new AABB(chunkX * 16, minY, chunkZ * 16, chunkX * 16 + 16, minY + 16, chunkZ * 16 + 16));
                }
                AABB whole = new AABB(chunkX * 16, level.getMinBuildHeight(), chunkZ * 16, chunkX * 16 + 16, level.getMaxBuildHeight(), chunkZ * 16 + 16);
                int ground = ImageRenderer.lowestGroundAround(level, chunkX, chunkZ);
                int buriedBelow = ground == Integer.MIN_VALUE ? Integer.MIN_VALUE : ground - ImageRenderer.BURIED_MARGIN;
                state.columns[slot] = new Column(chunkX, chunkZ, ImageRenderer.toHome(image, whole), boxes, buriedBelow);
            }
        }
    }

    /** As Sodium finalises its render lists: the images' lists from the sections just collected, sorted from their cameras. */
    public static void finalizeLists(RenderSectionManager sections) {
        if (sections != manager) return;
        for (Face state : FACES.values()) {
            if (state.pending == null) continue;
            double[] camera = state.pendingCamera;
            // Only the viewport's position is read in making the lists.
            state.lists = state.pending.createRenderLists(new Viewport(null, new Vector3d(camera[0], camera[1], camera[2])));
            state.pending = null;
        }
    }

    /** Keeps the animated textures in the images' drawn sections ticking, as Sodium does for its own. */
    public static void tickVisible(RenderSectionManager sections) {
        if (sections != manager) return;
        for (Face state : FACES.values()) {
            Iterator<ChunkRenderList> lists = state.lists.iterator();
            while (lists.hasNext()) {
                ChunkRenderList list = lists.next();
                RenderRegion region = list.getRegion();
                ByteIterator iterator = list.sectionsWithSpritesIterator();
                if (iterator == null) continue;
                while (iterator.hasNext()) {
                    RenderSection section = region.getSection(iterator.nextByteAsInt());
                    TextureAtlasSprite[] sprites = section == null ? null : section.getAnimatedSprites();
                    if (sprites == null) continue;
                    for (TextureAtlasSprite sprite : sprites) SpriteUtil.INSTANCE.markSpriteActive(sprite);
                }
            }
        }
    }

    public static void forget(RenderSectionManager sections) {
        if (sections != manager) return;
        FACES.clear();
        manager = null;
    }

    /** Draws one terrain layer of the images, after Sodium has drawn the camera's own. */
    public static void draw(RenderSectionManager sections, RenderType layer, ChunkRenderMatrices matrices, double x, double y, double z) {
        if (sections != manager || FACES.isEmpty()) return;
        TerrainRenderPass[] passes;
        if (layer == RenderType.solid()) {
            passes = new TerrainRenderPass[] {DefaultTerrainRenderPasses.SOLID, DefaultTerrainRenderPasses.CUTOUT};
        } else if (layer == RenderType.translucent()) {
            passes = new TerrainRenderPass[] {DefaultTerrainRenderPasses.TRANSLUCENT};
        } else {
            return;
        }
        RenderSectionManagerAccessor access = (RenderSectionManagerAccessor) sections;
        ChunkRenderer renderer = access.alpha_omega$chunkRenderer();
        boolean indexed = access.alpha_omega$sortBehavior() != SortBehavior.OFF;
        // A turn about Y keeps the cylindrical terrain fog right, so the fog shape stays.
        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            for (Map.Entry<Motion, Face> entry : FACES.entrySet()) {
                Motion image = entry.getKey();
                Face state = entry.getValue();
                Vec3 virtual = Transform.of(image.inverse()).position(new Vec3(x, y, z));
                Matrix4f modelView = new Matrix4f(matrices.modelView()).mul(new Matrix4f().set(ImageRenderer.rotation(image)));
                ChunkRenderMatrices rotated = new ChunkRenderMatrices(matrices.projection(), modelView);
                CameraTransform camera = new CameraTransform(virtual.x, virtual.y, virtual.z);
                for (TerrainRenderPass pass : passes) renderer.render(rotated, commandList, state.lists, pass, camera, indexed);
            }
            commandList.flush();
        }
    }

    /** The block entities in an image's drawn sections. */
    public static void forEachBlockEntity(Motion image, Consumer<BlockEntity> action) {
        Face state = FACES.get(image);
        if (state == null) return;
        Iterator<ChunkRenderList> lists = state.lists.iterator();
        while (lists.hasNext()) {
            ChunkRenderList list = lists.next();
            RenderRegion region = list.getRegion();
            ByteIterator iterator = list.sectionsWithEntitiesIterator();
            if (iterator == null) continue;
            while (iterator.hasNext()) {
                RenderSection section = region.getSection(iterator.nextByteAsInt());
                BlockEntity[] blockEntities = section == null ? null : section.getCulledBlockEntities();
                if (blockEntities == null) continue;
                for (BlockEntity blockEntity : blockEntities) action.accept(blockEntity);
            }
        }
    }

    /** For F3: per image, sections built of those in view. */
    @Nullable
    public static String debugLine() {
        if (FACES.isEmpty()) return null;
        StringBuilder line = new StringBuilder("Images (Sodium):");
        FACES.forEach((image, state) -> line.append(' ').append(image).append(' ').append(state.built).append('/').append(state.visible));
        return line.toString();
    }
}
