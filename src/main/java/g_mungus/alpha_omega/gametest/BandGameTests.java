package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandCheck;
import g_mungus.alpha_omega.band.BandChunk;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.band.BandData;
import g_mungus.alpha_omega.band.BandWrites;
import g_mungus.alpha_omega.band.Ownership;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Band copies across every kind of seam (orbifold plan §3 phase 4, RS §3): the east–west translation, the north fold,
 * the south fold, and a corner by cone point F, where cells have three copies.
 *
 * <p>Most contraptions are built twice in the same tick: across a seam ({@link Site}), and at a reference site 200
 * blocks inside the tile with the same orientation. Every tick, the seam build must equal the reference build cell for
 * cell, and its other copy must equal it, turned. At the end the copy check is clean, nothing reacted at a non-owner
 * copy, and the promotion gate was never breached.
 *
 * <p>Cells along a site's {@code across} direction are numbered {@code u}: {@code u < 0} is tile, {@code u ≥ 0} band
 * (a copy, owned by its source on the far side of the tile unless claimed). Each test runs in its own batch, because the
 * counters are global.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class BandGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final int Y = 200;
    /** Build box: {@code u} in ±SPAN, lateral ±3, {@code dy} −1..5. */
    private static final int SPAN = 8;

    /** A place to build; {@code toOther} maps the seam view to its source copy, or null for a reference. */
    record Site(String name, BlockPos origin, Direction across, Direction lateral, Motion toOther) {

        BlockPos at(int u, int l, int dy) {
            return this.origin.relative(this.across, u).relative(this.lateral, l).above(dy);
        }

        Transform turn() {
            return Transform.of(this.toOther);
        }

        BlockPos other(BlockPos pos) {
            return this.turn().block(pos);
        }

        /** The same build in the tile interior, 200 blocks back from the seam, oriented the same way. */
        Site reference() {
            return new Site(this.name + "-reference", this.origin.relative(this.across, -200), this.across, this.lateral, Motion.IDENTITY);
        }
    }

    enum Seam {
        EAST, NORTH, SOUTH, CORNER
    }

    /** Lanes keep sites apart along the seam; the corner site has no room for that and uses height instead. */
    private static Site site(GameTestHelper helper, Seam seam, int lane) {
        OrbifoldGeometry g = Orbifold.of(helper.getLevel());
        if (g == null) throw new IllegalStateException("the gametest overworld should be an orbifold");
        Site site = switch (seam) {
            case EAST -> new Site("east", new BlockPos(g.maxX, Y, TestPlaces.at(g, -2000, g.northRow + 192) + 32 * lane), Direction.EAST, Direction.SOUTH, Motion.IDENTITY);
            case NORTH -> new Site("north", new BlockPos(1000 + 32 * lane, Y, g.northRow - 1), Direction.NORTH, Direction.EAST, Motion.IDENTITY);
            case SOUTH -> new Site("south", new BlockPos(TestPlaces.at(g, -1500, -1000) - 32 * lane, Y, g.southRow), Direction.SOUTH, Direction.WEST, Motion.IDENTITY);
            // Past the north fold 48 blocks west of F: the source is by F's other side, and has copies in the east band too.
            case CORNER -> new Site("corner", new BlockPos(g.maxX - 48, Y + 4 * lane, g.northRow - 1), Direction.NORTH, Direction.EAST, Motion.IDENTITY);
        };
        // The motion from the band cell u = 0 to its source (a translation, a fold, or a fold and a translation).
        return new Site(site.name, site.origin, site.across, site.lateral, g.frame(site.origin.getX(), site.origin.getZ()));
    }

    /** A running scenario: the seam site, its reference, the forced chunks and what the per-tick comparison found. */
    static final class Run {
        final GameTestHelper helper;
        final ServerLevel level;
        final Site seam;
        final Site reference;
        final Set<ChunkPos> forced = new HashSet<>();
        final List<String> divergences = new ArrayList<>();
        final List<String> copyDisagreements = new ArrayList<>();
        boolean released;

        Run(GameTestHelper helper, Site seam) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.seam = seam;
            this.reference = seam.reference();
        }

        /** Forces the chunks round both sites and every copy of the seam site, clears the box, lays a floor. */
        Run prepare() {
            OrbifoldGeometry g = Orbifold.of(this.level);
            Set<ChunkPos> chunks = new HashSet<>();
            for (int u = -SPAN - 2; u <= SPAN + 2; u++) {
                for (int l = -4; l <= 4; l++) {
                    BlockPos pos = this.seam.at(u, l, 0);
                    chunks.add(new ChunkPos(pos));
                    OrbifoldGeometry.Cell source = g.canon(pos.getX(), pos.getZ());
                    chunks.add(new ChunkPos(new BlockPos(source.x(), 0, source.z())));
                    for (OrbifoldGeometry.Cell copy : g.copies(source.x(), source.z())) chunks.add(new ChunkPos(new BlockPos(copy.x(), 0, copy.z())));
                    chunks.add(new ChunkPos(this.reference.at(u, l, 0)));
                }
            }
            for (ChunkPos chunk : chunks) {
                TestChunks.force(this.level, chunk);
                this.forced.add(chunk);
            }
            for (Site site : List.of(this.seam, this.reference)) {
                for (int u = -SPAN - 1; u <= SPAN + 1; u++) {
                    for (int l = -4; l <= 4; l++) {
                        for (int dy = -1; dy <= 6; dy++) {
                            this.level.setBlock(site.at(u, l, dy), dy == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
                        }
                    }
                }
            }
            for (Entity entity : this.level.getEntitiesOfClass(Entity.class, new AABB(this.seam.at(-SPAN, -4, -1).getCenter(), this.seam.at(SPAN, 4, 6).getCenter()).inflate(2))) {
                entity.discard();
            }
            BandCounters.reset();
            return this;
        }

        /** Places a state at {@code (u, l, dy)} in both sites (flags 3). */
        void set(int u, int l, int dy, BlockState state) {
            this.level.setBlock(this.reference.at(u, l, dy), state, 3);
            this.level.setBlock(this.seam.at(u, l, dy), state, 3);
        }

        void both(BiConsumer<Site, BlockPos> action, int u, int l, int dy) {
            action.accept(this.reference, this.reference.at(u, l, dy));
            action.accept(this.seam, this.seam.at(u, l, dy));
        }

        BlockState seamState(int u, int l, int dy) {
            return this.level.getBlockState(this.seam.at(u, l, dy));
        }

        BlockState referenceState(int u, int l, int dy) {
            return this.level.getBlockState(this.reference.at(u, l, dy));
        }

        /** Compares the builds every tick from now on. */
        void compareEachTick() {
            this.helper.onEachTick(this::compare);
        }

        void compare() {
            if (this.released) return;
            long tick = this.level.getGameTime();
            for (int u = -SPAN; u <= SPAN; u++) {
                for (int l = -3; l <= 3; l++) {
                    for (int dy = -1; dy <= 5; dy++) {
                        BlockState here = this.seamState(u, l, dy);
                        BlockState there = this.referenceState(u, l, dy);
                        if (here != there && this.divergences.size() < 6) {
                            this.divergences.add("tick " + tick + " u=" + u + " l=" + l + " dy=" + dy + ": seam " + here + " vs reference " + there);
                        }
                        BlockPos pos = this.seam.at(u, l, dy);
                        BlockState copy = this.level.getBlockState(this.seam.other(pos));
                        if (copy != this.seam.turn().state(here) && this.copyDisagreements.size() < 6) {
                            this.copyDisagreements.add("tick " + tick + " u=" + u + " l=" + l + " dy=" + dy + ": " + here + " but copy " + copy);
                        }
                    }
                }
            }
        }

        /** Fails the test (releasing its chunks first) unless {@code condition}. */
        void check(boolean condition, String message) {
            if (condition) return;
            this.release();
            throw new net.minecraft.gametest.framework.GameTestAssertException(message);
        }

        /** The end of every scenario: builds matched every tick, copies agree, nothing reacted at a non-owner, the gate held. */
        void finish(String what) {
            this.compare();
            BandCheck.Result check = BandCheck.check(this.level, this.forced);
            String counters = String.join("; ", BandCounters.lines());
            AlphaOmegaMod.LOGGER.info("Band {} {}: {}; {}; divergences {}; copy disagreements {}", what, this.seam.name(), check, counters,
                this.divergences, this.copyDisagreements);
            String where = what + " across " + this.seam.name();
            this.check(this.divergences.isEmpty(), where + " differs from the reference: " + this.divergences);
            this.check(this.copyDisagreements.isEmpty(), where + ": copies disagree: " + this.copyDisagreements);
            this.check(check.clean(), where + ": " + check);
            this.check(!Band.DETECTORS || BandCounters.ranAtNonOwnerTotal() == 0, where + ": reactions ran at a non-owner: " + BandCounters.ranAtNonOwner
                + " (first at " + (BandCounters.ranAtNonOwnerWhere.isEmpty() ? "?" : BandCounters.ranAtNonOwnerWhere.get(0).split(" via ")[0]) + "; stacks in the log)");
            this.check(BandCounters.gateViolationTotal() == 0, where + ": promotion gate breached: " + BandCounters.gateViolations);
            this.check(BandCounters.mirrorsMissed == 0, where + ": mirrored writes missed: " + BandCounters.mirrorsMissed);
            this.release();
            this.helper.succeed();
        }

        void release() {
            if (this.released) return;
            this.released = true;
            TestChunks.release(this.level, this.forced);
            this.forced.clear();
            Band.ignoreOwnership = false;
        }
    }

    /**
     * Whether a portal POI is recorded at exactly {@code pos}, read from its chunk's records: lookups by position at a
     * copy report the owner's record (the phase 7 POI bridge), so they cannot tell where it is stored.
     */
    private static boolean poiRecorded(ServerLevel level, BlockPos pos) {
        return level.getPoiManager().getInChunk(type -> type.is(PoiTypes.NETHER_PORTAL), new ChunkPos(pos), net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
            .anyMatch(record -> record.getPos().equals(pos));
    }

    private static Run run(GameTestHelper helper, Seam seam, int lane) {
        return new Run(helper, site(helper, seam, lane)).prepare();
    }

    // ---- Block states, oriented along a site ----

    /** Dust running along {@code across}. */
    private static BlockState dust(Site site) {
        return Blocks.REDSTONE_WIRE.defaultBlockState()
            .setValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(site.across()), RedstoneSide.SIDE)
            .setValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(site.across().getOpposite()), RedstoneSide.SIDE);
    }

    /** A repeater taking input from {@code u − 1} and powering {@code u + 1}. */
    private static BlockState repeater(Site site) {
        return Blocks.REPEATER.defaultBlockState().setValue(DiodeBlock.FACING, site.across().getOpposite());
    }

    // ---- 1. Dust, repeaters, comparator reading a chest ----

    private static void dustLine(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane);
        for (int u = -5; u <= 5; u++) run.set(u, 0, 0, dust(run.seam));
        run.compareEachTick();
        run.set(-6, 0, 0, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAtTickTime(10, () -> {
            for (int u = -5; u <= 5; u++) {
                int power = run.seamState(u, 0, 0).getValue(RedStoneWireBlock.POWER);
                run.check(power == 10 - u, "dust at u=" + u + " across " + run.seam.name() + " has power " + power + ", expected " + (10 - u)
                    + "; reference " + run.referenceState(u, 0, 0).getValue(RedStoneWireBlock.POWER) + "; " + BandCounters.lines());
            }
            run.both((site, pos) -> helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3), -6, 0, 0);
        });
        helper.runAtTickTime(20, () -> {
            for (int u = -5; u <= 5; u++) {
                int power = run.seamState(u, 0, 0).getValue(RedStoneWireBlock.POWER);
                run.check(power == 0, "dust at u=" + u + " across " + run.seam.name() + " kept power " + power + " after the source went");
            }
            run.finish("dust line");
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_dust_east", timeoutTicks = 200)
    public static void dustLineEast(GameTestHelper helper) {
        dustLine(helper, Seam.EAST, 0);
    }

    @GameTest(template = TEMPLATE, batch = "band_dust_north", timeoutTicks = 200)
    public static void dustLineNorth(GameTestHelper helper) {
        dustLine(helper, Seam.NORTH, 0);
    }

    @GameTest(template = TEMPLATE, batch = "band_dust_south", timeoutTicks = 200)
    public static void dustLineSouth(GameTestHelper helper) {
        dustLine(helper, Seam.SOUTH, 0);
    }

    @GameTest(template = TEMPLATE, batch = "band_dust_corner", timeoutTicks = 200)
    public static void dustLineCorner(GameTestHelper helper) {
        dustLine(helper, Seam.CORNER, 0);
    }

    /**
     * The detectors' regression check: with ownership ignored (every copy reacts), the dust line must be seen reacting at
     * non-owner copies. Only meaningful with detectors on (always, in gametests).
     */
    @GameTest(template = TEMPLATE, batch = "band_dust_unowned", timeoutTicks = 200)
    public static void noOwnershipIsDetected(GameTestHelper helper) {
        if (!Band.DETECTORS) {
            helper.succeed();
            return;
        }
        Run run = run(helper, Seam.EAST, 8);
        Band.ignoreOwnership = true;
        for (int u = -5; u <= 5; u++) run.set(u, 0, 0, dust(run.seam));
        run.set(-6, 0, 0, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAtTickTime(10, () -> {
            long seen = BandCounters.ranAtNonOwnerTotal();
            run.release();
            if (seen == 0) helper.fail("with ownership ignored the detectors saw no reaction at a non-owner copy");
            else helper.succeed();
        });
    }

    private static void repeaterChain(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane);
        for (int u = -4; u <= 3; u++) run.set(u, 0, 0, repeater(run.seam));
        run.compareEachTick();
        long[] onAt = {-1, -1}, offAt = {-1, -1};
        long start = helper.getLevel().getGameTime();
        run.set(-5, 0, 0, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.onEachTick(() -> {
            if (run.released) return;
            long t = helper.getLevel().getGameTime() - start;
            boolean seamOn = run.seamState(3, 0, 0).getValue(DiodeBlock.POWERED), refOn = run.referenceState(3, 0, 0).getValue(DiodeBlock.POWERED);
            if (seamOn && onAt[0] < 0) onAt[0] = t;
            if (refOn && onAt[1] < 0) onAt[1] = t;
            if (!seamOn && onAt[0] >= 0 && offAt[0] < 0) offAt[0] = t;
            if (!refOn && onAt[1] >= 0 && offAt[1] < 0) offAt[1] = t;
        });
        helper.runAtTickTime(30, () -> run.both((site, pos) -> helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3), -5, 0, 0));
        helper.runAtTickTime(60, () -> {
            run.check(onAt[0] == onAt[1] && offAt[0] == offAt[1] && onAt[0] == 16, "repeaters across " + run.seam.name() + ": last on at " + onAt[0]
                + " (reference " + onAt[1] + ", expected 16), off at " + offAt[0] + " (reference " + offAt[1] + ")");
            run.finish("repeater chain");
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_repeaters_east", timeoutTicks = 200)
    public static void repeaterChainEast(GameTestHelper helper) {
        repeaterChain(helper, Seam.EAST, 1);
    }

    @GameTest(template = TEMPLATE, batch = "band_repeaters_north", timeoutTicks = 200)
    public static void repeaterChainNorth(GameTestHelper helper) {
        repeaterChain(helper, Seam.NORTH, 1);
    }

    @GameTest(template = TEMPLATE, batch = "band_repeaters_south", timeoutTicks = 200)
    public static void repeaterChainSouth(GameTestHelper helper) {
        repeaterChain(helper, Seam.SOUTH, 1);
    }

    @GameTest(template = TEMPLATE, batch = "band_repeaters_corner", timeoutTicks = 200)
    public static void repeaterChainCorner(GameTestHelper helper) {
        repeaterChain(helper, Seam.CORNER, 1);
    }

    /** The copy that holds the block entity of the seam site's cell {@code (u, l, dy)}, checked to be its owner and the only one. */
    private static String blockEntityHome(Run run, int u, int l, int dy) {
        BlockPos band = run.seam.at(u, l, dy), other = run.seam.other(band);
        boolean here = run.level.getChunkAt(band).getBlockEntities().containsKey(band);
        boolean there = run.level.getChunkAt(other).getBlockEntities().containsKey(other);
        if (here == there) return "block entity at " + (here ? "both copies" : "neither copy");
        BlockPos home = here ? band : other;
        return Ownership.isOwner(run.level, home) ? null : "block entity at " + home.toShortString() + ", which does not own the cell";
    }

    private static void comparatorReadsChest(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane);
        run.set(0, 0, 0, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, run.seam.across().getOpposite()));
        run.set(-1, 0, 0, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, run.seam.across()));
        run.set(-2, 0, 0, dust(run.seam));
        run.set(-3, 0, 0, dust(run.seam));
        run.compareEachTick();
        // Fill both chests through the cell each comparator reads (across the seam: the band copy, u = 0).
        helper.runAtTickTime(2, () -> run.both((site, pos) -> {
            BlockEntity chest = helper.getLevel().getBlockEntity(pos);
            run.check(chest instanceof Container, "no container reached at " + site.name() + " u=0: " + chest);
            for (int i = 0; i < 10; i++) ((Container) chest).setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }, 0, 0, 0));
        helper.runAtTickTime(12, () -> {
            BlockEntity comparator = helper.getLevel().getBlockEntity(run.seam.at(-1, 0, 0));
            int output = comparator instanceof ComparatorBlockEntity entity ? entity.getOutputSignal() : -1;
            int dust = run.seamState(-2, 0, 0).getValue(RedStoneWireBlock.POWER);
            run.check(output == 6 && dust == 6, "comparator across " + run.seam.name() + " outputs " + output + ", dust " + dust + ", expected 6; reference dust "
                + run.referenceState(-2, 0, 0).getValue(RedStoneWireBlock.POWER));
            String home = blockEntityHome(run, 0, 0, 0);
            run.check(home == null, "chest across " + run.seam.name() + ": " + home);
            run.both((site, pos) -> {
                Container container = (Container) helper.getLevel().getBlockEntity(pos);
                for (int i = 0; i < 10; i++) container.setItem(i, ItemStack.EMPTY);
            }, 0, 0, 0);
        });
        helper.runAtTickTime(22, () -> {
            int dust = run.seamState(-2, 0, 0).getValue(RedStoneWireBlock.POWER);
            run.check(dust == 0, "comparator across " + run.seam.name() + " still powers dust at " + dust + " after the chest emptied");
            run.finish("comparator");
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_comparator_east", timeoutTicks = 200)
    public static void comparatorReadsChestEast(GameTestHelper helper) {
        comparatorReadsChest(helper, Seam.EAST, 2);
    }

    @GameTest(template = TEMPLATE, batch = "band_comparator_north", timeoutTicks = 200)
    public static void comparatorReadsChestNorth(GameTestHelper helper) {
        comparatorReadsChest(helper, Seam.NORTH, 2);
    }

    @GameTest(template = TEMPLATE, batch = "band_comparator_south", timeoutTicks = 200)
    public static void comparatorReadsChestSouth(GameTestHelper helper) {
        comparatorReadsChest(helper, Seam.SOUTH, 2);
    }

    @GameTest(template = TEMPLATE, batch = "band_comparator_corner", timeoutTicks = 200)
    public static void comparatorReadsChestCorner(GameTestHelper helper) {
        comparatorReadsChest(helper, Seam.CORNER, 2);
    }

    // ---- 2. Sticky piston ----

    private static void pistonPushAndPull(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane);
        run.set(-2, 0, 0, Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, run.seam.across()));
        run.set(-1, 0, 0, Blocks.EMERALD_BLOCK.defaultBlockState());
        run.compareEachTick();
        run.set(-2, 1, 0, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAtTickTime(10, () -> {
            run.check(run.seamState(0, 0, 0).is(Blocks.EMERALD_BLOCK) && run.seamState(-1, 0, 0).is(Blocks.PISTON_HEAD), "push across " + run.seam.name()
                + ": u=-1 " + run.seamState(-1, 0, 0) + ", u=0 " + run.seamState(0, 0, 0) + "; reference u=0 " + run.referenceState(0, 0, 0) + "; " + BandCounters.lines());
            run.both((site, pos) -> helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3), -2, 1, 0);
        });
        helper.runAtTickTime(20, () -> {
            run.check(run.seamState(-1, 0, 0).is(Blocks.EMERALD_BLOCK) && run.seamState(0, 0, 0).isAir(), "pull back across " + run.seam.name() + ": u=-1 "
                + run.seamState(-1, 0, 0) + ", u=0 " + run.seamState(0, 0, 0));
            run.finish("piston");
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_piston_east", timeoutTicks = 200)
    public static void pistonEast(GameTestHelper helper) {
        pistonPushAndPull(helper, Seam.EAST, 3);
    }

    @GameTest(template = TEMPLATE, batch = "band_piston_north", timeoutTicks = 200)
    public static void pistonNorth(GameTestHelper helper) {
        pistonPushAndPull(helper, Seam.NORTH, 3);
    }

    @GameTest(template = TEMPLATE, batch = "band_piston_south", timeoutTicks = 200)
    public static void pistonSouth(GameTestHelper helper) {
        pistonPushAndPull(helper, Seam.SOUTH, 3);
    }

    @GameTest(template = TEMPLATE, batch = "band_piston_corner", timeoutTicks = 200)
    public static void pistonCorner(GameTestHelper helper) {
        pistonPushAndPull(helper, Seam.CORNER, 3);
    }

    // ---- Observers (from the spike) ----

    private static void observerClock(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane);
        BlockState watchingAcross = Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, run.seam.across());
        BlockState watchingBack = Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, run.seam.across().getOpposite());
        run.set(-2, 0, 0, dust(run.seam));
        run.set(1, 0, 0, dust(run.seam));
        run.both((site, pos) -> helper.getLevel().setBlock(pos, watchingAcross, 2 | 16), -1, 0, 0);
        run.compareEachTick();
        run.set(0, 0, 0, watchingBack);
        int[] pulses = {0, 0};
        boolean[] was = {false, false};
        helper.onEachTick(() -> {
            if (run.released) return;
            boolean seamOn = run.seamState(-1, 0, 0).getValue(ObserverBlock.POWERED), refOn = run.referenceState(-1, 0, 0).getValue(ObserverBlock.POWERED);
            if (seamOn && !was[0]) pulses[0]++;
            if (refOn && !was[1]) pulses[1]++;
            was[0] = seamOn;
            was[1] = refOn;
        });
        helper.runAtTickTime(40, () -> {
            run.check(pulses[0] == pulses[1] && pulses[0] >= 5, "observer clock across " + run.seam.name() + ": " + pulses[0] + " pulses, reference " + pulses[1]);
            run.finish("observer clock");
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_observer_east", timeoutTicks = 200)
    public static void observerClockEast(GameTestHelper helper) {
        observerClock(helper, Seam.EAST, 5);
    }

    @GameTest(template = TEMPLATE, batch = "band_observer_north", timeoutTicks = 200)
    public static void observerClockNorth(GameTestHelper helper) {
        observerClock(helper, Seam.NORTH, 5);
    }

    // ---- 3. Water and lava ----

    /** Lava flows from the band towards the seam; then water poured on the tile side meets it: cobblestone at u = 0. */
    private static void waterMeetsLava(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane);
        // A channel one cell wide along u.
        for (int u = -SPAN; u <= SPAN; u++) {
            run.set(u, -1, 0, Blocks.STONE.defaultBlockState());
            run.set(u, 1, 0, Blocks.STONE.defaultBlockState());
        }
        run.compareEachTick();
        run.set(3, 0, 0, Blocks.LAVA.defaultBlockState());
        helper.runAtTickTime(100, () -> {
            run.check(run.seamState(0, 0, 0).is(Blocks.LAVA), "lava did not flow to the seam across " + run.seam.name() + ": u=0 " + run.seamState(0, 0, 0)
                + ", reference " + run.referenceState(0, 0, 0));
            run.set(-3, 0, 0, Blocks.WATER.defaultBlockState());
        });
        helper.runAtTickTime(140, () -> {
            run.check(run.seamState(0, 0, 0).is(Blocks.COBBLESTONE), "no cobblestone at the seam across " + run.seam.name() + ": u=0 " + run.seamState(0, 0, 0)
                + ", reference " + run.referenceState(0, 0, 0));
            run.check(run.seamState(-1, 0, 0).getFluidState().isSource() == false && run.seamState(-1, 0, 0).is(Blocks.WATER), "water did not reach the seam across "
                + run.seam.name() + ": u=-1 " + run.seamState(-1, 0, 0));
            run.finish("water and lava");
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_fluids_east", timeoutTicks = 300)
    public static void waterMeetsLavaEast(GameTestHelper helper) {
        waterMeetsLava(helper, Seam.EAST, 6);
    }

    @GameTest(template = TEMPLATE, batch = "band_fluids_north", timeoutTicks = 300)
    public static void waterMeetsLavaNorth(GameTestHelper helper) {
        waterMeetsLava(helper, Seam.NORTH, 6);
    }

    @GameTest(template = TEMPLATE, batch = "band_fluids_south", timeoutTicks = 300)
    public static void waterMeetsLavaSouth(GameTestHelper helper) {
        waterMeetsLava(helper, Seam.SOUTH, 6);
    }

    @GameTest(template = TEMPLATE, batch = "band_fluids_corner", timeoutTicks = 300)
    public static void waterMeetsLavaCorner(GameTestHelper helper) {
        waterMeetsLava(helper, Seam.CORNER, 6);
    }

    // ---- 4. Nether portal and beacon ----

    /**
     * An obsidian frame straddling the seam (interior u = −1 and 0, dy 1 to 3) is lit at the band copy; it fills with
     * portal blocks in both builds, its POIs are registered at owners only, and a pig standing in it on the tile side
     * goes to the nether.
     */
    private static void portal(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane);
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        for (int dy = 0; dy <= 4; dy++) {
            run.set(-2, 0, dy, obsidian);
            run.set(1, 0, dy, obsidian);
        }
        for (int u = -1; u <= 0; u++) {
            run.set(u, 0, 0, obsidian);
            run.set(u, 0, 4, obsidian);
        }
        run.compareEachTick();
        run.set(0, 0, 1, Blocks.FIRE.defaultBlockState());
        Entity[] pigs = new Entity[2];
        helper.runAtTickTime(5, () -> {
            for (int u = -1; u <= 0; u++) {
                for (int dy = 1; dy <= 3; dy++) {
                    run.check(run.seamState(u, 0, dy).is(Blocks.NETHER_PORTAL), "portal across " + run.seam.name() + " did not light at u=" + u + " dy=" + dy + ": "
                        + run.seamState(u, 0, dy) + "; reference " + run.referenceState(u, 0, dy));
                }
            }
            // POIs: the band cells' at their owners only. The portal was lit from the tile side, so its band cells are
            // claimed by the band copy (RS §3.3).
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos band = run.seam.at(0, 0, dy), other = run.seam.other(band);
                run.check(Ownership.isOwner(run.level, band), "the portal's band cell " + band.toShortString() + " was not claimed by the band copy");
                run.check(poiRecorded(run.level, band), "no portal POI at the owner " + band.toShortString());
                run.check(!poiRecorded(run.level, other), "a portal POI at the non-owner copy " + other.toShortString());
            }
            int i = 0;
            for (Site site : List.of(run.seam, run.reference)) {
                Entity pig = EntityType.PIG.create(run.level);
                BlockPos at = site.at(-1, 0, 1);
                pig.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                run.level.addFreshEntity(pig);
                pigs[i++] = pig;
            }
        });
        helper.runAtTickTime(80, () -> {
            run.check(pigs[0].isRemoved() && pigs[0].getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION, "the pig in the portal across " + run.seam.name()
                + " did not change dimension: " + pigs[0] + " (reference pig " + pigs[1].getRemovalReason() + ")");
            run.finish("nether portal");
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_portal_east", timeoutTicks = 300)
    public static void portalEast(GameTestHelper helper) {
        portal(helper, Seam.EAST, 7);
    }

    @GameTest(template = TEMPLATE, batch = "band_portal_north", timeoutTicks = 300)
    public static void portalNorth(GameTestHelper helper) {
        portal(helper, Seam.NORTH, 7);
    }

    @GameTest(template = TEMPLATE, batch = "band_portal_south", timeoutTicks = 300)
    public static void portalSouth(GameTestHelper helper) {
        portal(helper, Seam.SOUTH, 7);
    }

    @GameTest(template = TEMPLATE, batch = "band_portal_corner", timeoutTicks = 300)
    public static void portalCorner(GameTestHelper helper) {
        portal(helper, Seam.CORNER, 7);
    }

    private static int beaconLevels(BlockEntity entity) {
        try {
            Field levels = BeaconBlockEntity.class.getDeclaredField("levels");
            levels.setAccessible(true);
            return levels.getInt(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A beacon on a 3×3 iron pyramid centred on the seam's first band cell activates, as the reference does. */
    private static void beacon(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane);
        for (int u = -1; u <= 1; u++) {
            for (int l = -1; l <= 1; l++) run.set(u, l, 0, Blocks.IRON_BLOCK.defaultBlockState());
        }
        run.set(0, 0, 1, Blocks.BEACON.defaultBlockState());
        // Open sky above both beacons, whatever the terrain.
        for (Site site : List.of(run.seam, run.reference)) {
            for (int y = site.at(0, 0, 2).getY(); y < run.level.getMaxBuildHeight(); y++) {
                run.level.setBlock(site.at(0, 0, 0).atY(y), Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
        run.compareEachTick();
        helper.runAtTickTime(170, () -> {
            BlockEntity seamBeacon = run.level.getBlockEntity(run.seam.at(0, 0, 1)), refBeacon = run.level.getBlockEntity(run.reference.at(0, 0, 1));
            run.check(seamBeacon instanceof BeaconBlockEntity && refBeacon instanceof BeaconBlockEntity, "no beacon reached: " + seamBeacon + ", " + refBeacon);
            int levels = beaconLevels(seamBeacon), reference = beaconLevels(refBeacon);
            run.check(levels == 1 && reference == 1, "beacon across " + run.seam.name() + " has " + levels + " levels (reference " + reference + ")");
            String home = blockEntityHome(run, 0, 0, 1);
            run.check(home == null, "beacon across " + run.seam.name() + ": " + home);
            run.finish("beacon");
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_beacon_east", timeoutTicks = 300)
    public static void beaconEast(GameTestHelper helper) {
        beacon(helper, Seam.EAST, 8);
    }

    @GameTest(template = TEMPLATE, batch = "band_beacon_north", timeoutTicks = 300)
    public static void beaconNorth(GameTestHelper helper) {
        beacon(helper, Seam.NORTH, 8);
    }

    @GameTest(template = TEMPLATE, batch = "band_beacon_south", timeoutTicks = 300)
    public static void beaconSouth(GameTestHelper helper) {
        beacon(helper, Seam.SOUTH, 8);
    }

    @GameTest(template = TEMPLATE, batch = "band_beacon_corner", timeoutTicks = 300)
    public static void beaconCorner(GameTestHelper helper) {
        beacon(helper, Seam.CORNER, 8);
    }

    // ---- 5. Hopper into a chest across, by capability ----

    /**
     * A hopper on the tile side (u = −1) pushes into the chest at u = 0, whose block entity lives at its owner on the far
     * side of the tile (the chest is placed there): the hopper's capability lookup at the band copy is redirected.
     */
    private static void hopperFillsChest(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane);
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, run.seam.across().getOpposite());
        run.level.setBlock(run.reference.at(0, 0, 0), chest, 3);
        BlockPos owner = run.seam.other(run.seam.at(0, 0, 0));
        run.level.setBlock(owner, run.seam.turn().state(chest), 3);
        run.set(-1, 0, 0, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, run.seam.across()));
        run.compareEachTick();
        run.both((site, pos) -> ((Container) run.level.getBlockEntity(pos)).setItem(0, new ItemStack(Items.DIAMOND, 5)), -1, 0, 0);
        helper.runAtTickTime(60, () -> {
            String home = blockEntityHome(run, 0, 0, 0);
            run.check(home == null, "chest across " + run.seam.name() + ": " + home);
            run.check(run.level.getChunkAt(owner).getBlockEntities().containsKey(owner), "the chest's block entity should be at " + owner.toShortString());
            Container seamChest = (Container) run.level.getBlockEntity(run.seam.at(0, 0, 0)), refChest = (Container) run.level.getBlockEntity(run.reference.at(0, 0, 0));
            int moved = seamChest.countItem(Items.DIAMOND), reference = refChest.countItem(Items.DIAMOND);
            run.check(moved == 5 && reference == 5, "hopper across " + run.seam.name() + " moved " + moved + " diamonds (reference " + reference + ")");
            run.check(BandCounters.capabilityRedirects > 0, "the hopper did not go through the capability redirect");
            run.finish("hopper");
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_hopper_east", timeoutTicks = 200)
    public static void hopperFillsChestEast(GameTestHelper helper) {
        hopperFillsChest(helper, Seam.EAST, 9);
    }

    @GameTest(template = TEMPLATE, batch = "band_hopper_north", timeoutTicks = 200)
    public static void hopperFillsChestNorth(GameTestHelper helper) {
        hopperFillsChest(helper, Seam.NORTH, 9);
    }

    @GameTest(template = TEMPLATE, batch = "band_hopper_south", timeoutTicks = 200)
    public static void hopperFillsChestSouth(GameTestHelper helper) {
        hopperFillsChest(helper, Seam.SOUTH, 9);
    }

    @GameTest(template = TEMPLATE, batch = "band_hopper_corner", timeoutTicks = 200)
    public static void hopperFillsChestCorner(GameTestHelper helper) {
        hopperFillsChest(helper, Seam.CORNER, 9);
    }

    // ---- 6. Breaking ----

    /** Items dropped round a cell and its other copy, counted and removed. */
    private static int takeDrops(ServerLevel level, Site site, BlockPos pos) {
        int count = 0;
        for (BlockPos at : List.of(pos, site.other(pos))) {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3))) {
                count += item.getItem().getCount();
                item.discard();
            }
        }
        return count;
    }

    private static void breaking(GameTestHelper helper, Seam seam, int lane) {
        Run run = run(helper, seam, lane);
        ServerLevel level = helper.getLevel();
        BlockPos band = run.seam.at(0, 0, 0), owner = run.seam.other(band);
        List<String> wrong = new ArrayList<>();
        for (boolean placeAtCopy : new boolean[] {true, false}) {
            for (boolean breakAtCopy : new boolean[] {true, false}) {
                String where = "placed at " + (placeAtCopy ? "the band copy" : "the source") + ", broken at " + (breakAtCopy ? "the band copy" : "the source");
                level.setBlock(placeAtCopy ? band : owner, Blocks.STONE.defaultBlockState(), 3);
                level.destroyBlock(breakAtCopy ? band : owner, true);
                int drops = takeDrops(level, run.seam, band);
                if (drops != 1) wrong.add("stone " + where + " dropped " + drops);
                if (!level.getBlockState(band).isAir() || !level.getBlockState(owner).isAir()) wrong.add("stone " + where + " left a copy");
                // A chest with contents: its block entity lives where it was placed, which then owns the cell.
                level.setBlock(placeAtCopy ? band : owner, Blocks.CHEST.defaultBlockState(), 3);
                if (!(level.getBlockEntity(breakAtCopy ? band : owner) instanceof Container container)) {
                    wrong.add("chest " + where + ": no container reachable");
                    continue;
                }
                container.setItem(0, new ItemStack(Items.DIAMOND, 5));
                level.destroyBlock(breakAtCopy ? band : owner, true);
                drops = takeDrops(level, run.seam, band);
                if (drops != 6) wrong.add("chest with 5 diamonds " + where + " dropped " + drops + " items");
                if (!level.getBlockState(band).isAir() || !level.getBlockState(owner).isAir()) wrong.add("chest " + where + " left a copy");
                if (level.getChunkAt(owner).getBlockEntities().containsKey(owner) || level.getChunkAt(band).getBlockEntities().containsKey(band)) {
                    wrong.add("chest " + where + " left a block entity");
                }
                if (!Ownership.isOwner(level, owner)) wrong.add("chest " + where + ": the cell did not return to its source");
            }
        }
        run.check(wrong.isEmpty(), "breaking across " + run.seam.name() + ": " + wrong);
        helper.runAtTickTime(2, () -> run.finish("breaking"));
    }

    @GameTest(template = TEMPLATE, batch = "band_breaking_east", timeoutTicks = 200)
    public static void breakingEast(GameTestHelper helper) {
        breaking(helper, Seam.EAST, 10);
    }

    @GameTest(template = TEMPLATE, batch = "band_breaking_north", timeoutTicks = 200)
    public static void breakingNorth(GameTestHelper helper) {
        breaking(helper, Seam.NORTH, 10);
    }

    @GameTest(template = TEMPLATE, batch = "band_breaking_south", timeoutTicks = 200)
    public static void breakingSouth(GameTestHelper helper) {
        breaking(helper, Seam.SOUTH, 10);
    }

    @GameTest(template = TEMPLATE, batch = "band_breaking_corner", timeoutTicks = 200)
    public static void breakingCorner(GameTestHelper helper) {
        breaking(helper, Seam.CORNER, 10);
    }

    // ---- 8. Recovery ----

    private static boolean unloaded(ServerLevel level, ChunkPos pos) {
        return level.getChunkSource().chunkMap.getVisibleChunkIfPresent(pos.toLong()) == null;
    }

    /**
     * Save, corrupt, reload: a band chunk and its source hold a stone and a chest with items, both placed at the band
     * copy (claims: the band copy owns them). Before they unload and save, one chunk is made to look as if it
     * had been saved earlier, before a crash: its stamp is set behind the other's and its copy is stale. With the source
     * newer, the band copy of the stone is a diamond block; with the band newer, the source has lost its mask bit and its
     * copy of the chest. After reloading, the newer chunk's masks must win, every cell must take the newer chunk's
     * content (a non-owner from its owner; an owner from a newer source), and the chest must keep its items.
     */
    private static void recovery(GameTestHelper helper, Seam seam, int lane, boolean bandNewer) {
        ServerLevel level = helper.getLevel();
        Site site = site(helper, seam, lane);
        BlockPos stone = site.at(1, 0, 0), chest = site.at(2, 0, 0);
        ChunkPos band = new ChunkPos(stone), source = new ChunkPos(site.other(stone));
        Set<ChunkPos> forced = new HashSet<>(List.of(band, source));
        for (ChunkPos chunk : forced) TestChunks.force(level, chunk);
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ((Container) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 7));
        BandCounters.reset();
        LevelChunk bandChunk = level.getChunkSource().getChunkNow(band.x, band.z), sourceChunk = level.getChunkSource().getChunkNow(source.x, source.z);
        BandData bandData = ((BandChunk) bandChunk).alpha_omega$data(true), sourceData = ((BandChunk) sourceChunk).alpha_omega$data(true);
        long stamp = sourceData.stamp(band.toLong());
        if (bandNewer) {
            // The source was saved before the chest was claimed: its mask does not know, and its copy of the cell is stale.
            BlockPos at = site.other(chest);
            sourceData.setFlip(sourceChunk.getSectionIndex(at.getY()), BandData.cell(at.getX(), at.getY(), at.getZ()), false);
            BandWrites.asMirror(() -> sourceChunk.setBlockState(at, Blocks.AIR.defaultBlockState(), false));
            sourceData.setStamp(band.toLong(), stamp - 5);
        } else {
            // The band chunk was saved before the stone was placed.
            BandWrites.asMirror(() -> bandChunk.setBlockState(stone, Blocks.DIAMOND_BLOCK.defaultBlockState(), false));
            bandData.setStamp(source.toLong(), stamp - 5);
        }
        bandChunk.setUnsaved(true);
        sourceChunk.setUnsaved(true);
        TestChunks.release(level, forced);
        boolean[] reloaded = {false};
        helper.onEachTick(() -> {
            if (reloaded[0] || !unloaded(level, band) || !unloaded(level, source)) return;
            reloaded[0] = true;
            for (ChunkPos chunk : forced) TestChunks.force(level, chunk);
            List<String> wrong = new ArrayList<>();
            if (!level.getBlockState(stone).is(Blocks.STONE)) wrong.add("band copy of the stone is " + level.getBlockState(stone));
            if (!level.getBlockState(site.other(stone)).is(Blocks.STONE)) wrong.add("source copy of the stone is " + level.getBlockState(site.other(stone)));
            if (!level.getBlockState(chest).is(Blocks.CHEST) || !level.getBlockState(site.other(chest)).is(Blocks.CHEST)) {
                wrong.add("chest copies: " + level.getBlockState(chest) + ", " + level.getBlockState(site.other(chest)));
            }
            if (!Ownership.isOwner(level, chest) || Ownership.isOwner(level, site.other(chest))) wrong.add("the band copy should still own the chest");
            BlockEntity entity = level.getBlockEntity(site.other(chest));
            if (!(entity instanceof Container container) || container.countItem(Items.DIAMOND) != 7) wrong.add("the chest lost its items: " + entity);
            BandCheck.Result check = BandCheck.check(level, forced);
            if (!check.clean()) wrong.add(check.toString());
            if (BandCounters.stampMismatches == 0) wrong.add("no stamp mismatch was seen on reload");
            AlphaOmegaMod.LOGGER.info("Band recovery {} ({} newer): {}; {}", site.name(), bandNewer ? "band" : "source", wrong, String.join("; ", BandCounters.lines()));
            // Clean up for the next run.
            level.destroyBlock(chest, false);
            level.setBlock(stone, Blocks.AIR.defaultBlockState(), 3);
            TestChunks.release(level, forced);
            if (wrong.isEmpty()) helper.succeed();
            else helper.fail("recovery across " + site.name() + ": " + wrong);
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_recovery_source_newer", timeoutTicks = 1200)
    public static void recoverySourceNewerEast(GameTestHelper helper) {
        recovery(helper, Seam.EAST, 20, false);
    }

    @GameTest(template = TEMPLATE, batch = "band_recovery_band_newer", timeoutTicks = 1200)
    public static void recoveryBandNewerNorth(GameTestHelper helper) {
        recovery(helper, Seam.NORTH, 20, true);
    }

    // ---- 9. Promotion gate ----

    /**
     * A band chunk never loaded before is asked for on its own: it must not become a full chunk before its source, and
     * when it does it holds its source's blocks (the generator made it empty), with no generated ticks or block entities.
     */
    @GameTest(template = TEMPLATE, batch = "band_gate_fresh", timeoutTicks = 400)
    public static void gateFillsFreshBandChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OrbifoldGeometry g = Orbifold.of(level);
        // Deep in the east band, far from every other test.
        ChunkPos band = new ChunkPos((g.maxX >> 4) + 1, (g.northRow >> 4) + TestPlaces.at(g, 200, 9));
        OrbifoldGeometry.Cell cell = g.canonChunk(band.x, band.z);
        ChunkPos source = new ChunkPos(cell.x(), cell.z());
        if (!unloaded(level, band) || !unloaded(level, source)) {
            helper.fail("the gate test's chunks should start unloaded");
            return;
        }
        BandCounters.reset();
        TestChunks.force(level, band);
        LevelChunk chunk = level.getChunkSource().getChunkNow(band.x, band.z);
        List<String> wrong = new ArrayList<>();
        if (chunk == null || !Band.filled(chunk)) wrong.add("the band chunk is not loaded and filled: " + chunk);
        if (level.getChunkSource().getChunkNow(source.x, source.z) == null) wrong.add("its source is not loaded");
        if (BandCounters.gateFills == 0) wrong.add("no gate fill");
        if (BandCounters.gateFallbacks != 0) wrong.add("gate fallbacks " + BandCounters.gateFallbacks);
        if (BandCounters.gateViolationTotal() != 0) wrong.add("gate violations " + BandCounters.gateViolations);
        if (BandCounters.cellsFilled == 0) wrong.add("the fill changed nothing (was the band generated full?)");
        BandCheck.Result check = BandCheck.check(level, List.of(band));
        if (!check.clean() || check.chunks() != 1) wrong.add(check.toString());
        AlphaOmegaMod.LOGGER.info("Band gate, fresh chunk: {}; {}", wrong, String.join("; ", BandCounters.lines()));
        TestChunks.release(level, band);
        if (wrong.isEmpty()) helper.succeed();
        else helper.fail("promotion gate: " + wrong);
    }

    /**
     * A tile chunk at the north seam is forced on its own, which makes its band neighbours full (level 32) without any
     * ticket of theirs; water poured at the seam flows into them. The band copies must match their sources throughout,
     * and nothing may load, tick or go full unfilled.
     */
    @GameTest(template = TEMPLATE, batch = "band_gate_neighbour", timeoutTicks = 400)
    public static void gateHoldsBesideForcedTileChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OrbifoldGeometry g = Orbifold.of(level);
        ChunkPos tile = new ChunkPos(TestPlaces.at(g, 3000, -1000) >> 4, g.northRow >> 4);
        BandCounters.reset();
        TestChunks.force(level, tile);
        BlockPos seamCell = new BlockPos(tile.getMinBlockX() + 8, Y + 20, g.northRow);
        for (int dz = -2; dz <= 6; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos at = seamCell.offset(dx, dy, -dz);
                    level.setBlock(at, dy == -1 || dz == -2 || Math.abs(dx) == 2 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }
        ChunkPos bandChunk = new ChunkPos(seamCell.north());
        long[] pouredAt = {-1};
        long startedAt = level.getGameTime();
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) return;
            long now = level.getGameTime();
            if (pouredAt[0] < 0) {
                // Pour once the forced chunk ticks: its band neighbours are full (and filled) by then.
                if (!level.isPositionEntityTicking(seamCell)) return;
                AlphaOmegaMod.LOGGER.info("Band gate test: the forced tile chunk ticks after {} ticks", now - startedAt);
                level.setBlock(seamCell.south(), Blocks.WATER.defaultBlockState(), 3);
                pouredAt[0] = now;
                return;
            }
            if (now - pouredAt[0] < 40) {
                return;
            }
            done[0] = true;
            List<ChunkPos> around = new ArrayList<>();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) around.add(new ChunkPos(tile.x + dx, tile.z + dz));
            }
            List<String> wrong = new ArrayList<>();
            BandCheck.Result check = BandCheck.check(level, around);
            if (!check.clean() || check.chunks() == 0) wrong.add(check.toString());
            if (!level.getBlockState(seamCell.north()).is(Blocks.WATER)) wrong.add("the water did not flow over the seam: " + level.getBlockState(seamCell.south()) + ", " + level.getBlockState(seamCell) + ", " + level.getBlockState(seamCell.north()) + ", below " + level.getBlockState(seamCell.below()) + ", at " + seamCell.toShortString());
            if (BandCounters.gateViolationTotal() != 0) wrong.add("gate violations " + BandCounters.gateViolations);
            if (BandCounters.gateFallbacks != 0) wrong.add("gate fallbacks " + BandCounters.gateFallbacks);
            AlphaOmegaMod.LOGGER.info("Band gate, beside a forced tile chunk: {}; {}", wrong, String.join("; ", BandCounters.lines()));
            TestChunks.release(level, tile);
            if (wrong.isEmpty()) helper.succeed();
            else helper.fail("promotion gate beside a forced tile chunk: " + wrong);
        });
    }
}
