package g_mungus.alpha_omega.compat.sodium;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import g_mungus.alpha_omega.client.NeighbourRenderer;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.mixin.compat.sodium.RenderSectionManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import java.util.ArrayDeque;
import java.util.EnumMap;
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
 * The neighbouring faces drawn through Sodium (design §6.4). Sodium is told of every chunk the client holds, so it
 * keeps a section for each, on every face; the neighbouring faces' sections are then built by Sodium like the camera's
 * own, and a crossing of an edge finds the face it comes onto already built.
 *
 * <p>When Sodium searches for visible sections, each neighbouring face's are collected here from the camera as seen
 * from that face: per chunk column within view, on its face and loaded, with its boxes seen from home and the depth
 * below which it is buried, worked out again only when its chunk or one near it changes (as in
 * {@link NeighbourRenderer}). Sections in view go into the face's own render lists; those out of view only queue to
 * build, so that whatever the view turns to on crossing is ready. Each terrain layer of a face then draws with its
 * rotation in the model-view matrix and Sodium's camera at the face's virtual camera.
 */
public final class SodiumNeighbours {

    /** One loaded column: its chunk, its boxes seen from home (whole and per section), and where buried begins. */
    private record Column(int chunkX, int chunkZ, AABB box, AABB[] boxes, int buriedBelow) {
    }

    /** A column slot worked out to hold nothing worth drawing (off the face, outside the footprint, or not loaded). */
    private static final Column NOTHING = new Column(0, 0, new AABB(0, 0, 0, 0, 0, 0), new AABB[0], 0);

    /** A neighbouring face: its columns around the virtual camera, and what Sodium draws of it. */
    private static final class Face {
        final int size;
        /** Per column slot ({@code x * size + z}, wrapping chunk positions): null until worked out. */
        final Column[] columns;
        /** Per column slot, the chunk it was worked out for. */
        final long[] columnAt;
        @Nullable
        CubeFace builtFor;
        /** Collected in the current search, until Sodium finalises its lists. */
        @Nullable
        SectionCollector pending;
        @Nullable
        double[] pendingCamera;
        SortedRenderLists lists = SortedRenderLists.empty();
        /** The home face {@link #lists} were collected from; they draw only while it is still home. */
        @Nullable
        CubeFace listsFor;
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

    private static final Map<CubeFace, Face> FACES = new EnumMap<>(CubeFace.class);
    /** The section manager the faces' lists belong to; a new one (Sodium reloaded) starts them again. */
    @Nullable
    private static RenderSectionManager manager;
    /** The camera's face at the last search. */
    @Nullable
    private static CubeFace lastHome;

    private SodiumNeighbours() {
    }

    /** A chunk arrived: Sodium learns of it here, since the cube world's chunk store bypasses vanilla's. */
    public static void chunkLoaded(ClientLevel level, int chunkX, int chunkZ) {
        ChunkTrackerHolder.get(level).onChunkStatusAdded(chunkX, chunkZ, net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus.FLAG_HAS_BLOCK_DATA);
    }

    public static void chunkDropped(ClientLevel level, int chunkX, int chunkZ) {
        ChunkTrackerHolder.get(level).onChunkStatusRemoved(chunkX, chunkZ, net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus.FLAG_HAS_BLOCK_DATA);
    }

    /** Forgets every face (new level, view distance or renderer). */
    public static void reset() {
        FACES.clear();
    }

    /** A chunk or a block in it changed: the columns around it are worked out again. */
    public static void invalidateAround(CubeFace face, int chunkX, int chunkZ) {
        Face state = FACES.get(face);
        if (state != null) state.invalidateAround(chunkX, chunkZ);
    }

    /**
     * After Sodium has searched from the camera: collects each neighbouring face's sections, and adds those to build to
     * Sodium's queues. Returns whether a section in view is still building, so the search should run again.
     */
    public static boolean collect(RenderSectionManager sections, Camera camera, int frame) {
        if (sections != manager) {
            FACES.clear();
            manager = sections;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        CubeGeometry cube = level == null ? null : Cube.of(level);
        Vec3 cam = camera.getPosition();
        CubeFace home = cube == null ? null : cube.faceAt(cam.x, cam.z);
        lastHome = home;
        if (home == null) {
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
        // Buried sections can only be seen from underground (through caves meeting at the edge).
        boolean underground = level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(cam.x), Mth.floor(cam.z)) > cam.y + 2.0;
        String only = System.getProperty("alpha_omega.dev.onlyFace");
        boolean revisit = false;
        for (CubeFace face : CubeFace.values()) {
            Face state = FACES.get(face);
            if (!home.isNeighbour(face) || (only != null && !only.equals(face.name()))) {
                if (state != null) state.pending = null;
                continue;
            }
            if (state == null || state.size != viewDistance * 2 + 1) FACES.put(face, state = new Face(viewDistance));
            if (state.builtFor != home) {
                state.builtFor = home;
                state.invalidate();
            }
            double[] virtual = cube.transform(home, face, cam.x, cam.y, cam.z);
            build(state, face, home, level, cube, Math.floorDiv(Mth.floor(virtual[0]), 16), Math.floorDiv(Mth.floor(virtual[2]), 16));
            SectionCollector collector = new OcclusionSectionCollector(frame, rebuildQueue, sortQueue);
            // Out of view only queues to build; a build still running there need not search again.
            SectionCollector ahead = new OcclusionSectionCollector(frame, rebuildQueue, sortQueue);
            state.visible = 0;
            state.built = 0;
            for (Column column : state.columns) {
                if (column == NOTHING) continue;
                if (NeighbourRenderer.distance(column.box, cam) > reach + NeighbourRenderer.SECTION_RADIUS) continue;
                boolean columnInView = frustum.isVisible(column.box);
                for (int y = column.boxes.length - 1; y >= 0; y--) {
                    int sectionY = level.getMinSection() + y;
                    if (!underground && SectionPos.sectionToBlockCoord(sectionY) + 16 <= column.buriedBelow) break;
                    AABB box = column.boxes[y];
                    if (NeighbourRenderer.distance(box, cam) > reach) continue;
                    RenderSection section = byPosition.get(SectionPos.asLong(column.chunkX, sectionY, column.chunkZ));
                    if (section == null) continue;
                    if (columnInView && frustum.isVisible(box)) {
                        collector.visit(section);
                        state.visible++;
                        if (section.isBuilt()) state.built++;
                    } else {
                        // Out of view it only queues to build: crossing an edge turns the view a quarter turn, and
                        // the face then becomes Sodium's own, so whatever it shows should be ready.
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

    /** Works out the face's column slots around the virtual camera's chunk not yet worked out for their chunk. */
    private static void build(Face state, CubeFace face, CubeFace home, ClientLevel level, CubeGeometry cube, int centreX, int centreZ) {
        int size = state.size;
        int radius = size / 2;
        int height = level.getSectionsCount();
        for (int chunkX = centreX - radius; chunkX <= centreX + radius; chunkX++) {
            for (int chunkZ = centreZ - radius; chunkZ <= centreZ + radius; chunkZ++) {
                int slot = Math.floorMod(chunkX, size) * size + Math.floorMod(chunkZ, size);
                long at = ChunkPos.asLong(chunkX, chunkZ);
                if (state.columns[slot] != null && state.columnAt[slot] == at) continue;
                state.columnAt[slot] = at;
                if (cube.faceAtChunk(chunkX, chunkZ) != face || !cube.inFootprint(chunkX, chunkZ)
                    || level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                    state.columns[slot] = NOTHING;
                    continue;
                }
                AABB[] boxes = new AABB[height];
                for (int y = 0; y < height; y++) {
                    int minY = SectionPos.sectionToBlockCoord(level.getMinSection() + y);
                    boxes[y] = NeighbourRenderer.toHome(cube, face, home, new AABB(chunkX * 16, minY, chunkZ * 16, chunkX * 16 + 16, minY + 16, chunkZ * 16 + 16));
                }
                AABB whole = new AABB(chunkX * 16, level.getMinBuildHeight(), chunkZ * 16, chunkX * 16 + 16, level.getMaxBuildHeight(), chunkZ * 16 + 16);
                int ground = NeighbourRenderer.lowestGroundAround(level, chunkX, chunkZ);
                int buriedBelow = ground == Integer.MIN_VALUE ? Integer.MIN_VALUE : ground - NeighbourRenderer.BURIED_MARGIN;
                state.columns[slot] = new Column(chunkX, chunkZ, NeighbourRenderer.toHome(cube, face, home, whole), boxes, buriedBelow);
            }
        }
    }

    /** As Sodium finalises its render lists: the faces' lists from the sections just collected, sorted from their cameras. */
    public static void finalizeLists(RenderSectionManager sections) {
        if (sections != manager) return;
        for (Face state : FACES.values()) {
            if (state.pending == null) continue;
            double[] camera = state.pendingCamera;
            // Only the viewport's position is read in making the lists.
            state.lists = state.pending.createRenderLists(new Viewport(null, new Vector3d(camera[0], camera[1], camera[2])));
            state.listsFor = state.builtFor;
            state.pending = null;
        }
    }

    /** Keeps the animated textures in the faces' drawn sections ticking, as Sodium does for its own. */
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

    /** Draws one terrain layer of the neighbouring faces, after Sodium has drawn the camera's face's. */
    public static void draw(RenderSectionManager sections, RenderType layer, ChunkRenderMatrices matrices, double x, double y, double z) {
        if (sections != manager || FACES.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        CubeGeometry cube = level == null ? null : Cube.of(level);
        CubeFace home = cube == null ? null : cube.faceAt(x, z);
        if (home == null) return;
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
        // Terrain fog is a cylinder about the vertex's own up axis, which on another face is sideways.
        FogShape fogShape = RenderSystem.getShaderFogShape();
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            for (Map.Entry<CubeFace, Face> entry : FACES.entrySet()) {
                CubeFace face = entry.getKey();
                Face state = entry.getValue();
                if (state.listsFor != home || !home.isNeighbour(face)) continue;
                double[] virtual = cube.transform(home, face, x, y, z);
                Matrix4f modelView = new Matrix4f(matrices.modelView()).mul(new Matrix4f().set(NeighbourRenderer.rotation(face, home)));
                ChunkRenderMatrices rotated = new ChunkRenderMatrices(matrices.projection(), modelView);
                CameraTransform camera = new CameraTransform(virtual[0], virtual[1], virtual[2]);
                for (TerrainRenderPass pass : passes) renderer.render(rotated, commandList, state.lists, pass, camera, indexed);
            }
            commandList.flush();
        } finally {
            RenderSystem.setShaderFogShape(fogShape);
        }
    }

    /** The block entities in a face's drawn sections. */
    public static void forEachBlockEntity(CubeFace face, CubeFace home, Consumer<BlockEntity> action) {
        Face state = FACES.get(face);
        if (state == null || state.listsFor != home) return;
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

    /** For F3: per neighbouring face, sections built of those in view. */
    @Nullable
    public static String debugLine() {
        if (FACES.isEmpty() || lastHome == null) return null;
        StringBuilder line = new StringBuilder("Neighbours (Sodium):");
        FACES.forEach((face, state) -> {
            if (state.listsFor == lastHome && lastHome.isNeighbour(face)) {
                line.append(' ').append(face).append(' ').append(state.built).append('/').append(state.visible);
            }
        });
        return line.toString();
    }
}
