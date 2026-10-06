package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandCheck;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.band.Ownership;
import g_mungus.alpha_omega.gametest.BandGameTests.Site;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Claims across the east–west seam, the north fold and the south fold (orbifold plan §3 phase 8, RS §3.3): a placement
 * claims its cell for the copy it was made at, out to {@code C}; a removal returns it to its nominal owner; block
 * entities are created at the owner; moved blocks and flowing fluids are placements in the mover's frame.
 *
 * <p>Sites are {@link BandGameTests.Site}s: {@code u < 0} is tile, {@code u ≥ 0} band at depth {@code u + 1}, and
 * {@code other} maps a cell to its copy on the far side of the tile (the "far side" builder's frame). The builder "from
 * one side" stands in the tile by the seam and builds at the seam site's positions. Every test ends with the copy check
 * clean, no reaction at a non-owner, and the promotion gate held.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ClaimGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final int Y = 200;
    /** The multiblock: controller at {@code u = −3}, parts at {@code −2..2}, three of them past the seam. */
    private static final int CONTROLLER_U = -3;

    enum Seam {
        EAST, NORTH, SOUTH
    }

    /** Sites 16 apart per lane, clear of the band and bridge sites (see {@link TestPlaces}). */
    private static Site site(GameTestHelper helper, Seam seam, int lane) {
        OrbifoldGeometry g = Orbifold.of(helper.getLevel());
        if (g == null) throw new IllegalStateException("the gametest overworld should be an orbifold");
        Site site = switch (seam) {
            case EAST -> new Site("east", new BlockPos(g.maxX, Y, TestPlaces.at(g, -2800, g.northRow + 848) + 16 * lane), Direction.EAST, Direction.SOUTH, Motion.IDENTITY);
            case NORTH -> new Site("north", new BlockPos(TestPlaces.at(g, -2000, -700) - 16 * lane, Y, g.northRow - 1), Direction.NORTH, Direction.EAST, Motion.IDENTITY);
            case SOUTH -> new Site("south", new BlockPos(TestPlaces.at(g, 1000, 700) + 16 * lane, Y, g.southRow), Direction.SOUTH, Direction.WEST, Motion.IDENTITY);
        };
        return new Site(site.name(), site.origin(), site.across(), site.lateral(), g.frame(site.origin().getX(), site.origin().getZ()));
    }

    /** A scenario: its site, forced chunks and the end-of-test checks. */
    static final class Run {
        final GameTestHelper helper;
        final ServerLevel level;
        final OrbifoldGeometry geometry;
        final Site site;
        final int minU;
        final int maxU;
        final Set<ChunkPos> forced = new HashSet<>();
        boolean released;

        Run(GameTestHelper helper, Site site, int minU, int maxU) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.geometry = Orbifold.of(this.level);
            this.site = site;
            this.minU = minU;
            this.maxU = maxU;
        }

        /** The chunks round the site and round every copy of its cells. */
        Set<ChunkPos> chunks() {
            Set<ChunkPos> chunks = new HashSet<>();
            for (int u = this.minU - 2; u <= this.maxU + 2; u++) {
                for (int l = -4; l <= 4; l++) {
                    BlockPos pos = this.site.at(u, l, 0);
                    chunks.add(new ChunkPos(pos));
                    OrbifoldGeometry.Cell source = this.geometry.canon(pos.getX(), pos.getZ());
                    chunks.add(new ChunkPos(new BlockPos(source.x(), 0, source.z())));
                    for (OrbifoldGeometry.Cell copy : this.geometry.copies(source.x(), source.z())) chunks.add(new ChunkPos(new BlockPos(copy.x(), 0, copy.z())));
                }
            }
            return chunks;
        }

        void force() {
            for (ChunkPos chunk : this.chunks()) {
                TestChunks.force(this.level, chunk);
                this.forced.add(chunk);
            }
        }

        /** Forces the chunks, clears the box, lays a stone floor. */
        Run prepare() {
            this.force();
            for (int u = this.minU - 1; u <= this.maxU + 1; u++) {
                for (int l = -4; l <= 4; l++) {
                    for (int dy = -1; dy <= 4; dy++) {
                        this.level.setBlock(this.site.at(u, l, dy), dy == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
            for (Entity entity : this.level.getEntitiesOfClass(Entity.class, new AABB(this.site.at(this.minU, -4, -1).getCenter(), this.site.at(this.maxU, 4, 4).getCenter()).inflate(2))) {
                entity.discard();
            }
            BandCounters.reset();
            return this;
        }

        BlockPos at(int u, int l, int dy) {
            return this.site.at(u, l, dy);
        }

        BlockPos other(BlockPos pos) {
            return this.site.other(pos);
        }

        /** Placed by the builder by the seam, in the tile's frame. */
        void place(int u, int l, int dy, BlockState state) {
            this.level.setBlock(this.at(u, l, dy), state, 3);
        }

        /** Placed by a builder on the far side, in its frame: the cell's other copy, with the state turned. */
        void placeFromFar(int u, int l, int dy, BlockState state) {
            this.level.setBlock(this.other(this.at(u, l, dy)), this.site.turn().state(state), 3);
        }

        boolean ownedHere(BlockPos pos) {
            return Ownership.isOwner(this.level, pos);
        }

        /** Whether {@code pos}'s chunk holds a block entity at {@code pos} itself (not through the owner redirect). */
        boolean blockEntityAt(BlockPos pos) {
            return this.level.getChunkAt(pos).getBlockEntities().containsKey(pos);
        }

        String depth(int u) {
            return "u=" + u + (u >= 0 ? " (depth " + (u + 1) + ")" : " (tile)");
        }

        void check(boolean condition, String message) {
            if (condition) return;
            this.release();
            throw new GameTestAssertException(this.site.name() + ": " + message);
        }

        /** The copy check is clean, nothing reacted at a non-owner, the gate held; then the chunks are released. */
        void finish(String what) {
            BandCheck.Result check = BandCheck.check(this.level, this.forced);
            AlphaOmegaMod.LOGGER.info("Claims {} {}: {}; {}", what, this.site.name(), check, String.join("; ", BandCounters.lines()));
            String where = what + " across " + this.site.name();
            this.check(check.clean(), where + ": " + check);
            this.check(!Band.DETECTORS || BandCounters.ranAtNonOwnerTotal() == 0, where + ": reactions ran at a non-owner: " + BandCounters.ranAtNonOwner
                + " " + BandCounters.ranAtNonOwnerWhere);
            this.check(BandCounters.gateViolationTotal() == 0, where + ": promotion gate breached: " + BandCounters.gateViolations);
            this.release();
            this.helper.succeed();
        }

        void release() {
            if (this.released) return;
            this.released = true;
            TestChunks.release(this.level, this.forced);
            this.forced.clear();
        }
    }

    private static Run run(GameTestHelper helper, Seam seam, int lane, int minU, int maxU) {
        return new Run(helper, site(helper, seam, lane), minU, maxU).prepare();
    }

    // ---- The multiblock ----

    private static BlockState controller(Site site) {
        return TestMultiblock.CONTROLLER.get().defaultBlockState().setValue(TestMultiblock.ControllerBlock.FACING, site.across());
    }

    private static BlockState part() {
        return TestMultiblock.PART.get().defaultBlockState();
    }

    /** The controller's block entity, through the level (at its owner). */
    private static TestMultiblock.ControllerEntity controllerEntity(Run run) {
        return run.level.getBlockEntity(run.at(CONTROLLER_U, 0, 0)) instanceof TestMultiblock.ControllerEntity entity ? entity : null;
    }

    /** What is wrong with a build made from one side: it works, and every part past the seam is the builder's. */
    private static List<String> oneSideProblems(Run run) {
        List<String> wrong = new ArrayList<>();
        TestMultiblock.ControllerEntity controller = controllerEntity(run);
        if (controller == null) return List.of("no controller block entity");
        if (!controller.formed()) wrong.add("not formed (formed " + controller.formations() + " times)");
        if (controller.workTicks() <= 0) wrong.add("no working ticks");
        if (!run.blockEntityAt(run.at(CONTROLLER_U, 0, 0))) wrong.add("the controller's block entity is not at the controller");
        for (int u = CONTROLLER_U + 1; u < CONTROLLER_U + 1 + TestMultiblock.SIZE; u++) {
            BlockPos pos = run.at(u, 0, 0), other = run.other(pos);
            if (!run.ownedHere(pos) || run.ownedHere(other)) wrong.add("part at " + run.depth(u) + " is not owned by the builder's copy");
            if (!run.blockEntityAt(pos) || run.blockEntityAt(other)) {
                wrong.add("part at " + run.depth(u) + ": block entity at the builder's copy " + run.blockEntityAt(pos) + ", at the other " + run.blockEntityAt(other));
            }
            if (!(run.level.getBlockEntity(pos) instanceof TestMultiblock.PartEntity part) || !part.linked()) wrong.add("part at " + run.depth(u) + " is not linked");
        }
        return wrong;
    }

    private static void buildFromOneSide(Run run) {
        run.place(CONTROLLER_U, 0, 0, controller(run.site));
        for (int u = CONTROLLER_U + 1; u < CONTROLLER_U + 1 + TestMultiblock.SIZE; u++) run.place(u, 0, 0, part());
    }

    private static boolean unloaded(ServerLevel level, Set<ChunkPos> chunks) {
        for (ChunkPos pos : chunks) {
            if (level.getChunkSource().chunkMap.getVisibleChunkIfPresent(pos.toLong()) != null) return false;
        }
        return true;
    }

    /**
     * The central test: the multiblock built across the seam from the tile side forms and works, its parts past the
     * seam are owned by the builder's copy (their block entities there, in the builder's frame), and after its chunks
     * unload and reload it still works.
     */
    private static void multiblock(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane, CONTROLLER_U - 1, 3);
        buildFromOneSide(run);
        long[] before = {-1};
        Set<ChunkPos> chunks = new HashSet<>(run.forced);
        // 0 building, 1 waiting for the chunks to unload, 2 reloaded, 3 done.
        int[] state = {0};
        long[] reloadedAt = {0};
        helper.runAtTickTime(10, () -> {
            List<String> wrong = oneSideProblems(run);
            run.check(wrong.isEmpty(), "multiblock built from one side: " + wrong + "; " + BandCounters.lines());
            run.check(BandCounters.placementClaims >= 3, "expected the three parts past the seam to be claimed: " + BandCounters.placementClaims);
            before[0] = controllerEntity(run).workTicks();
            // Save and reload: let every chunk go, wait for them to unload, load them again.
            run.release();
            state[0] = 1;
        });
        helper.onEachTick(() -> {
            if (state[0] == 1 && unloaded(run.level, chunks)) {
                state[0] = 2;
                run.released = false;
                run.force();
                reloadedAt[0] = run.level.getGameTime();
            } else if (state[0] == 2 && run.level.getGameTime() >= reloadedAt[0] + 10) {
                state[0] = 3;
                TestMultiblock.ControllerEntity controller = controllerEntity(run);
                run.check(controller != null && controller.workTicks() > before[0] && controller.formations() == 1, "after reloading, the controller "
                    + (controller == null ? "is gone" : "has " + controller.workTicks() + " working ticks (" + before[0] + " before), formed " + controller.formations() + " times"));
                List<String> after = oneSideProblems(run);
                run.check(after.isEmpty(), "multiblock after reloading: " + after);
                for (int u = CONTROLLER_U; u < CONTROLLER_U + 1 + TestMultiblock.SIZE; u++) run.level.setBlock(run.at(u, 0, 0), Blocks.AIR.defaultBlockState(), 3);
                run.finish("multiblock");
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = "claim_multiblock_east", timeoutTicks = 1500)
    public static void multiblockEast(GameTestHelper helper) {
        multiblock(helper, Seam.EAST, 0);
    }

    @GameTest(template = TEMPLATE, batch = "claim_multiblock_north", timeoutTicks = 1500)
    public static void multiblockNorth(GameTestHelper helper) {
        multiblock(helper, Seam.NORTH, 0);
    }

    @GameTest(template = TEMPLATE, batch = "claim_multiblock_south", timeoutTicks = 1500)
    public static void multiblockSouth(GameTestHelper helper) {
        multiblock(helper, Seam.SOUTH, 0);
    }

    /**
     * RS §9 limit 1, recorded: the controller and the parts before the seam are placed from the tile side, the three
     * parts past it by a builder on the far side, in its own frame (its tile cells). Expected: those three parts are the
     * far copy's (nominal), with their block entities there; the controller still forms and works, because its lookups
     * at its stored positions reach the parts' block entities through the copies; but the far parts are not linked,
     * since each compares its own {@code getBlockPos()}, in the far frame, with the controller's list, in the near one.
     * A mod whose controller compared positions the same way would not form.
     */
    private static void bothSides(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane, CONTROLLER_U - 1, 3);
        run.place(CONTROLLER_U, 0, 0, controller(run.site));
        for (int u = CONTROLLER_U + 1; u < 0; u++) run.place(u, 0, 0, part());
        for (int u = 0; u < CONTROLLER_U + 1 + TestMultiblock.SIZE; u++) run.placeFromFar(u, 0, 0, part());
        helper.runAtTickTime(10, () -> {
            List<String> observed = new ArrayList<>();
            List<String> unexpected = new ArrayList<>();
            TestMultiblock.ControllerEntity controller = controllerEntity(run);
            run.check(controller != null, "no controller block entity");
            observed.add("controller formed " + controller.formed() + ", working ticks " + controller.workTicks());
            if (!controller.formed() || controller.workTicks() <= 0) unexpected.add("the controller did not form and work");
            for (int u = CONTROLLER_U + 1; u < CONTROLLER_U + 1 + TestMultiblock.SIZE; u++) {
                BlockPos pos = run.at(u, 0, 0), other = run.other(pos);
                boolean far = u >= 0;
                BlockPos home = far ? other : pos;
                boolean owned = run.ownedHere(home) && !run.ownedHere(far ? pos : other);
                boolean linked = run.level.getBlockEntity(pos) instanceof TestMultiblock.PartEntity part && part.linked();
                observed.add(run.depth(u) + ": owned by the " + (far ? "far" : "near") + " builder's copy " + owned + ", block entity there " + run.blockEntityAt(home)
                    + ", linked " + linked);
                if (!owned || !run.blockEntityAt(home)) unexpected.add("part at " + run.depth(u) + " is not owned by its builder's copy");
                if (linked == far) unexpected.add("part at " + run.depth(u) + (far ? " is linked across frames" : " is not linked"));
            }
            AlphaOmegaMod.LOGGER.info("Claims: multiblock built from both sides across {} (RS §9 limit 1): {}", run.site.name(), observed);
            run.check(unexpected.isEmpty(), "multiblock built from both sides differs from the recorded limit: " + unexpected + "; observed " + observed);
            for (int u = CONTROLLER_U; u < CONTROLLER_U + 1 + TestMultiblock.SIZE; u++) run.level.setBlock(run.at(u, 0, 0), Blocks.AIR.defaultBlockState(), 3);
            run.finish("multiblock from both sides");
        });
    }

    @GameTest(template = TEMPLATE, batch = "claim_both_sides_east", timeoutTicks = 200)
    public static void bothSidesEast(GameTestHelper helper) {
        bothSides(helper, Seam.EAST, 1);
    }

    @GameTest(template = TEMPLATE, batch = "claim_both_sides_north", timeoutTicks = 200)
    public static void bothSidesNorth(GameTestHelper helper) {
        bothSides(helper, Seam.NORTH, 1);
    }

    /**
     * Breaking the build, at either copy: the controller unforms when a part goes, every cell returns to its nominal
     * owner, and no block entity is left at either copy.
     */
    private static void breaking(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane, CONTROLLER_U - 1, 3);
        buildFromOneSide(run);
        helper.runAtTickTime(10, () -> {
            List<String> wrong = oneSideProblems(run);
            run.check(wrong.isEmpty(), "multiblock before breaking: " + wrong);
            // At the owner, past the seam.
            run.level.destroyBlock(run.at(0, 0, 0), true);
        });
        helper.runAtTickTime(13, () -> {
            TestMultiblock.ControllerEntity controller = controllerEntity(run);
            run.check(controller != null && !controller.formed(), "the controller is still formed with a part gone");
            // At the non-owner copy (the far side's view of a claimed part), with and without drops; and in the tile.
            run.level.destroyBlock(run.other(run.at(1, 0, 0)), true);
            run.level.setBlock(run.other(run.at(2, 0, 0)), Blocks.AIR.defaultBlockState(), 3);
            run.level.destroyBlock(run.at(-1, 0, 0), true);
            run.level.setBlock(run.at(-2, 0, 0), Blocks.AIR.defaultBlockState(), 3);
            run.level.destroyBlock(run.at(CONTROLLER_U, 0, 0), true);
            List<String> left = new ArrayList<>();
            for (int u = CONTROLLER_U; u < CONTROLLER_U + 1 + TestMultiblock.SIZE; u++) {
                BlockPos pos = run.at(u, 0, 0), other = run.other(pos);
                if (!run.level.getBlockState(pos).isAir() || !run.level.getBlockState(other).isAir()) left.add(run.depth(u) + " not air");
                BlockPos tile = u < 0 ? pos : other;
                if (!run.ownedHere(tile)) left.add(run.depth(u) + " did not return to its nominal owner");
                if (run.blockEntityAt(pos) || run.blockEntityAt(other)) left.add(run.depth(u) + " left a block entity");
            }
            run.check(left.isEmpty(), "after breaking: " + left);
            for (Entity entity : run.level.getEntitiesOfClass(Entity.class, new AABB(run.at(CONTROLLER_U, -4, -1).getCenter(), run.at(3, 4, 4).getCenter()).inflate(3))) {
                entity.discard();
            }
            run.finish("breaking the multiblock");
        });
    }

    @GameTest(template = TEMPLATE, batch = "claim_breaking_east", timeoutTicks = 200)
    public static void breakingEast(GameTestHelper helper) {
        breaking(helper, Seam.EAST, 2);
    }

    @GameTest(template = TEMPLATE, batch = "claim_breaking_north", timeoutTicks = 200)
    public static void breakingNorth(GameTestHelper helper) {
        breaking(helper, Seam.NORTH, 2);
    }

    /**
     * Placements at the band copy at depth {@code C} claim; at depth {@code C + 1} they keep the nominal owner, and a
     * chest placed there gets its block entity at the owner (the source), reachable through the band copy.
     */
    private static void beyondClaim(GameTestHelper helper, Seam seam, int lane) {
        OrbifoldGeometry g = Orbifold.of(helper.getLevel());
        int c = g.claim;
        Run run = run(helper, seam, lane, c - 3, c + 2);
        int inside = c - 1, beyond = c;
        run.place(inside, 0, 0, Blocks.STONE.defaultBlockState());
        run.place(beyond, 0, 0, Blocks.STONE.defaultBlockState());
        run.place(inside, 2, 0, Blocks.CHEST.defaultBlockState());
        run.place(beyond, 2, 0, Blocks.CHEST.defaultBlockState());
        List<String> wrong = new ArrayList<>();
        for (int l : new int[] {0, 2}) {
            BlockPos in = run.at(inside, l, 0), out = run.at(beyond, l, 0);
            if (!run.ownedHere(in)) wrong.add(run.depth(inside) + " l=" + l + " was not claimed");
            if (run.ownedHere(out) || !run.ownedHere(run.other(out))) wrong.add(run.depth(beyond) + " l=" + l + " did not stay with its nominal owner");
        }
        BlockPos chestIn = run.at(inside, 2, 0), chestOut = run.at(beyond, 2, 0);
        if (!run.blockEntityAt(chestIn) || run.blockEntityAt(run.other(chestIn))) wrong.add("the chest at depth C should have its block entity at the band copy");
        if (run.blockEntityAt(chestOut) || !run.blockEntityAt(run.other(chestOut))) wrong.add("the chest at depth C + 1 should have its block entity at the source");
        if (!(run.level.getBlockEntity(chestOut) instanceof Container)) wrong.add("the chest at depth C + 1 is not reachable at the band copy");
        if (BandCounters.blockEntitiesAtOwner < 1) wrong.add("no block entity was created at the owner");
        run.check(wrong.isEmpty(), "placements by C: " + wrong);
        helper.runAtTickTime(2, () -> {
            for (int l : new int[] {0, 2}) {
                run.level.setBlock(run.at(inside, l, 0), Blocks.AIR.defaultBlockState(), 3);
                run.level.destroyBlock(run.at(beyond, l, 0), false);
            }
            for (int l : new int[] {0, 2}) {
                for (int u : new int[] {inside, beyond}) {
                    BlockPos pos = run.at(u, l, 0);
                    run.check(run.ownedHere(run.other(pos)) && !run.blockEntityAt(pos) && !run.blockEntityAt(run.other(pos)), run.depth(u) + " l=" + l
                        + " after breaking: not nominal, or a block entity left");
                }
            }
            run.finish("placements by C");
        });
    }

    @GameTest(template = TEMPLATE, batch = "claim_beyond_east", timeoutTicks = 200)
    public static void beyondClaimEast(GameTestHelper helper) {
        beyondClaim(helper, Seam.EAST, 3);
    }

    @GameTest(template = TEMPLATE, batch = "claim_beyond_north", timeoutTicks = 200)
    public static void beyondClaimNorth(GameTestHelper helper) {
        beyondClaim(helper, Seam.NORTH, 3);
    }

    /**
     * Water poured on one side flows into the other side's band: in a channel along {@code u}, a source three cells from
     * the seam. From the tile side, the flowing cells past the seam are claimed by the band copy (the pourer's frame);
     * from the far side, the cells before the seam, which are band cells in the far frame, are claimed by the far copies.
     * When the source goes, the water drains and every cell is nominal again.
     */
    private static void water(GameTestHelper helper, Seam seam, int lane, boolean fromFar) {
        Run run = run(helper, seam, lane, -8, 8);
        for (int u = -8; u <= 8; u++) {
            run.place(u, -1, 0, Blocks.STONE.defaultBlockState());
            run.place(u, 1, 0, Blocks.STONE.defaultBlockState());
        }
        run.place(-8, 0, 0, Blocks.STONE.defaultBlockState());
        run.place(8, 0, 0, Blocks.STONE.defaultBlockState());
        int sourceU = fromFar ? 2 : -3;
        if (fromFar) run.placeFromFar(sourceU, 0, 0, Blocks.WATER.defaultBlockState());
        else run.place(sourceU, 0, 0, Blocks.WATER.defaultBlockState());
        helper.runAtTickTime(60, () -> {
            List<String> wrong = new ArrayList<>();
            int flowing = 0;
            for (int u = -7; u <= 7; u++) {
                BlockPos pos = run.at(u, 0, 0);
                if (!run.level.getBlockState(pos).is(Blocks.WATER)) continue;
                // The pourer's copy of the cell: the seam site's position for the near pourer, the other one for the far.
                BlockPos pourers = fromFar ? run.other(pos) : pos;
                boolean pastSeam = fromFar ? u < 0 : u >= 0;
                if (pastSeam) flowing++;
                if (!run.ownedHere(pourers)) wrong.add("water at " + run.depth(u) + " is not owned by the pourer's copy");
            }
            if (flowing < 3) wrong.add("only " + flowing + " water cells past the seam");
            run.check(wrong.isEmpty(), "water poured from the " + (fromFar ? "far" : "near") + " side: " + wrong);
            if (fromFar) run.level.setBlock(run.other(run.at(sourceU, 0, 0)), Blocks.AIR.defaultBlockState(), 3);
            else run.level.setBlock(run.at(sourceU, 0, 0), Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAtTickTime(140, () -> {
            for (int u = -7; u <= 7; u++) {
                BlockPos pos = run.at(u, 0, 0);
                run.check(run.level.getBlockState(pos).isAir(), "water left at " + run.depth(u) + ": " + run.level.getBlockState(pos));
                run.check(run.ownedHere(u < 0 ? pos : run.other(pos)), run.depth(u) + " is not nominal after the water drained");
            }
            run.finish("water from the " + (fromFar ? "far" : "near") + " side");
        });
    }

    @GameTest(template = TEMPLATE, batch = "claim_water_east", timeoutTicks = 300)
    public static void waterEast(GameTestHelper helper) {
        water(helper, Seam.EAST, 4, false);
    }

    @GameTest(template = TEMPLATE, batch = "claim_water_north", timeoutTicks = 300)
    public static void waterNorth(GameTestHelper helper) {
        water(helper, Seam.NORTH, 4, false);
    }

    @GameTest(template = TEMPLATE, batch = "claim_water_far_north", timeoutTicks = 300)
    public static void waterFromFarSideNorth(GameTestHelper helper) {
        water(helper, Seam.NORTH, 5, true);
    }

    /**
     * A sticky piston in the tile pushes a block across the seam and pulls it back. The landed block is owned by the
     * band copy (the piston's frame) with no block entity left anywhere; pulled back, the cell is air and nominal.
     */
    private static void piston(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane, -4, 3);
        run.place(-2, 0, 0, Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, run.site.across()));
        run.place(-1, 0, 0, Blocks.EMERALD_BLOCK.defaultBlockState());
        run.place(-2, 1, 0, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAtTickTime(10, () -> {
            BlockPos landed = run.at(0, 0, 0);
            run.check(run.level.getBlockState(landed).is(Blocks.EMERALD_BLOCK), "not pushed across: " + run.level.getBlockState(landed));
            run.check(run.ownedHere(landed) && !run.ownedHere(run.other(landed)), "the pushed block is not owned by the piston's copy");
            run.check(!run.blockEntityAt(landed) && !run.blockEntityAt(run.other(landed)), "a moving piston block entity was left");
            run.level.setBlock(run.at(-2, 1, 0), Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAtTickTime(20, () -> {
            BlockPos cell = run.at(0, 0, 0);
            run.check(run.level.getBlockState(run.at(-1, 0, 0)).is(Blocks.EMERALD_BLOCK) && run.level.getBlockState(cell).isAir(), "not pulled back");
            run.check(run.ownedHere(run.other(cell)), "the emptied cell is not nominal");
            run.finish("piston");
        });
    }

    @GameTest(template = TEMPLATE, batch = "claim_piston_east", timeoutTicks = 200)
    public static void pistonEast(GameTestHelper helper) {
        piston(helper, Seam.EAST, 6);
    }

    @GameTest(template = TEMPLATE, batch = "claim_piston_north", timeoutTicks = 200)
    public static void pistonNorth(GameTestHelper helper) {
        piston(helper, Seam.NORTH, 6);
    }

    /**
     * A piston claimed by the band copy at depth {@code C − 1} pushes a block from depth {@code C} to {@code C + 1}. While
     * it moves, the moving piston's block entity claims the cell beyond {@code C} (block entities only sit at owners);
     * when it lands the claim ends with the block entity, and the landed block is the nominal owner's. The head, at depth
     * {@code C}, is the band copy's.
     */
    private static void pistonPastClaim(GameTestHelper helper, Seam seam, int lane) {
        OrbifoldGeometry g = Orbifold.of(helper.getLevel());
        int c = g.claim;
        Run run = run(helper, seam, lane, c - 4, c + 2);
        int pistonU = c - 2, headU = c - 1, landU = c;
        run.place(pistonU, 0, 0, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, run.site.across()));
        run.place(headU, 0, 0, Blocks.EMERALD_BLOCK.defaultBlockState());
        boolean[] movingClaimed = {false};
        helper.onEachTick(() -> {
            BlockPos moving = run.at(landU, 0, 0);
            if (!run.released && run.level.getBlockState(moving).is(Blocks.MOVING_PISTON) && run.ownedHere(moving) && run.blockEntityAt(moving)) movingClaimed[0] = true;
        });
        run.place(pistonU, 1, 0, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAtTickTime(10, () -> {
            BlockPos landed = run.at(landU, 0, 0), head = run.at(headU, 0, 0);
            run.check(run.ownedHere(run.at(pistonU, 0, 0)), "the piston at " + run.depth(pistonU) + " was not claimed");
            run.check(run.level.getBlockState(landed).is(Blocks.EMERALD_BLOCK) && run.level.getBlockState(head).is(Blocks.PISTON_HEAD), "not pushed: "
                + run.level.getBlockState(head) + ", " + run.level.getBlockState(landed));
            run.check(movingClaimed[0], "the moving piston beyond C was not seen claimed by its block entity at the band copy");
            run.check(!run.ownedHere(landed) && run.ownedHere(run.other(landed)), "the block landed at " + run.depth(landU) + " is not the nominal owner's");
            run.check(run.ownedHere(head), "the head at " + run.depth(headU) + " is not the band copy's");
            run.check(!run.blockEntityAt(landed) && !run.blockEntityAt(run.other(landed)), "a moving piston block entity was left");
            run.level.setBlock(run.at(pistonU, 1, 0), Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAtTickTime(20, () -> {
            run.check(run.level.getBlockState(run.at(headU, 0, 0)).isAir(), "the head did not retract");
            run.check(run.ownedHere(run.other(run.at(headU, 0, 0))), "the head's cell is not nominal after retracting");
            run.level.setBlock(run.at(landU, 0, 0), Blocks.AIR.defaultBlockState(), 3);
            run.level.setBlock(run.at(pistonU, 0, 0), Blocks.AIR.defaultBlockState(), 3);
            run.finish("piston past C");
        });
    }

    @GameTest(template = TEMPLATE, batch = "claim_piston_past_east", timeoutTicks = 200)
    public static void pistonPastClaimEast(GameTestHelper helper) {
        pistonPastClaim(helper, Seam.EAST, 7);
    }

    @GameTest(template = TEMPLATE, batch = "claim_piston_past_north", timeoutTicks = 200)
    public static void pistonPastClaimNorth(GameTestHelper helper) {
        pistonPastClaim(helper, Seam.NORTH, 7);
    }
}
