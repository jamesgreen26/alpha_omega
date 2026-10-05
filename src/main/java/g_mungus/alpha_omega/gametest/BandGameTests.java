package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandCheck;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.band.BandFill;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Phase 4 spike: redstone across a seam (orbifold plan §3 phase 4, RS §3). Each contraption is built twice in the same
 * tick: across a seam ({@link Site}), and at a reference site in the tile interior with the same orientation. Every
 * tick, the seam build must equal the reference build cell for cell, and the seam build's other copies must equal it,
 * turned. At the end the copy check is clean and no reaction ran at a non-owner copy.
 *
 * <p>Cells along a site's {@code across} direction are numbered {@code u}: {@code u < 0} is tile (owner), {@code u ≥ 0}
 * is band (a copy; its owner is on the far side of the tile). Each test runs in its own batch, because the counters
 * and the band mode are global.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class BandGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final int Y = 200;
    /** Build box: {@code u} in ±SPAN, lateral ±2, {@code dy} −1..2. */
    private static final int SPAN = 8;

    /** A place to build: {@code at(u, l, dy)}; {@code toOther} maps the seam view to the other copy, or null for a reference. */
    record Site(String name, BlockPos origin, Direction across, Direction lateral, @Nullable Motion toOther) {

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
            return new Site(this.name + "-reference", this.origin.relative(this.across, -200), this.across, this.lateral, null);
        }
    }

    private static Site east(OrbifoldGeometry geometry, int lane) {
        return new Site("east", new BlockPos(geometry.maxX, Y, -2000 + 32 * lane), Direction.EAST, Direction.SOUTH, geometry.west);
    }

    private static Site north(OrbifoldGeometry geometry, int lane) {
        return new Site("north", new BlockPos(1000 + 32 * lane, Y, geometry.northRow - 1), Direction.NORTH, Direction.EAST, geometry.northFold);
    }

    private static Site site(GameTestHelper helper, String seam, int lane) {
        OrbifoldGeometry geometry = Orbifold.of(helper.getLevel());
        if (geometry == null) throw new IllegalStateException("the gametest overworld should be an orbifold");
        return seam.equals("east") ? east(geometry, lane) : north(geometry, lane);
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

        Run(GameTestHelper helper, Site seam) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.seam = seam;
            this.reference = seam.reference();
        }

        /** Forces and fills the chunks round both sites and the seam site's other copy, clears the box, lays a floor. */
        Run prepare(Band.Mode mode, boolean claims) {
            Band.mode = mode;
            Band.blockEntityClaims = claims;
            Set<ChunkPos> chunks = new HashSet<>();
            for (int u = -SPAN - 2; u <= SPAN + 2; u++) {
                for (int l = -3; l <= 3; l++) {
                    BlockPos pos = this.seam.at(u, l, 0);
                    chunks.add(new ChunkPos(pos));
                    chunks.add(new ChunkPos(this.seam.other(pos)));
                    chunks.add(new ChunkPos(this.reference.at(u, l, 0)));
                }
            }
            for (ChunkPos chunk : chunks) {
                TestChunks.force(this.level, chunk);
                this.forced.add(chunk);
            }
            BandFill.flush(this.level);
            for (ChunkPos chunk : chunks) BandFill.fillNow(this.level, chunk);
            for (Site site : List.of(this.seam, this.reference)) {
                for (int u = -SPAN - 1; u <= SPAN + 1; u++) {
                    for (int l = -3; l <= 3; l++) {
                        for (int dy = -1; dy <= 3; dy++) {
                            this.level.setBlock(site.at(u, l, dy), dy == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
                        }
                    }
                }
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
            long tick = this.level.getGameTime();
            for (int u = -SPAN; u <= SPAN; u++) {
                for (int l = -2; l <= 2; l++) {
                    for (int dy = -1; dy <= 2; dy++) {
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

        /** The end of every scenario: builds matched every tick, copies agree, nothing reacted at a non-owner. */
        void finish(String what) {
            this.compare();
            BandCheck.Result check = BandCheck.check(this.level, this.forced);
            String counters = String.join("; ", BandCounters.lines());
            AlphaOmegaMod.LOGGER.info("Band {} {}: {}; {}; divergences {}; copy disagreements {}", what, this.seam.name(), check, counters,
                this.divergences, this.copyDisagreements);
            try {
                this.helper.assertTrue(this.divergences.isEmpty(), what + " across " + this.seam.name() + " differs from the reference: " + this.divergences);
                this.helper.assertTrue(this.copyDisagreements.isEmpty(), what + " across " + this.seam.name() + ": copies disagree: " + this.copyDisagreements);
                this.helper.assertTrue(check.clean(), what + " across " + this.seam.name() + ": " + check);
                this.helper.assertTrue(BandCounters.ranAtNonOwnerTotal() == 0,
                    what + " across " + this.seam.name() + ": reactions ran at a non-owner: " + BandCounters.ranAtNonOwner + " " + BandCounters.ranAtNonOwnerWhere);
                this.helper.assertTrue(BandCounters.mirrorsMissed == 0, "mirrored writes missed: " + BandCounters.mirrorsMissed);
            } finally {
                this.release();
            }
            this.helper.succeed();
        }

        void release() {
            TestChunks.release(this.level, this.forced);
            this.forced.clear();
            Band.mode = Band.Mode.FORWARD;
            Band.blockEntityClaims = false;
        }
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

    // ---- 1. Dust ----

    private static void dustLine(GameTestHelper helper, String seam, int lane, Band.Mode mode) {
        Run run = new Run(helper, site(helper, seam, lane)).prepare(mode, false);
        for (int u = -5; u <= 5; u++) run.set(u, 0, 0, dust(run.seam));
        run.compareEachTick();
        run.set(-6, 0, 0, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAtTickTime(10, () -> {
            for (int u = -5; u <= 5; u++) {
                int power = run.seamState(u, 0, 0).getValue(RedStoneWireBlock.POWER);
                if (power != 10 - u) {
                    run.release();
                    helper.fail("dust at u=" + u + " across " + run.seam.name() + " has power " + power + ", expected " + (10 - u)
                        + "; reference " + run.referenceState(u, 0, 0).getValue(RedStoneWireBlock.POWER) + "; " + BandCounters.lines());
                }
            }
            run.both((site, pos) -> helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3), -6, 0, 0);
        });
        helper.runAtTickTime(20, () -> {
            for (int u = -5; u <= 5; u++) {
                int power = run.seamState(u, 0, 0).getValue(RedStoneWireBlock.POWER);
                if (power != 0) {
                    run.release();
                    helper.fail("dust at u=" + u + " across " + run.seam.name() + " kept power " + power + " after the source went");
                }
            }
            run.finish("dust line");
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_dust_east", timeoutTicks = 200)
    public static void dustLineEast(GameTestHelper helper) {
        dustLine(helper, "east", 0, Band.Mode.FORWARD);
    }

    @GameTest(template = TEMPLATE, batch = "band_dust_north", timeoutTicks = 200)
    public static void dustLineNorth(GameTestHelper helper) {
        dustLine(helper, "north", 0, Band.Mode.FORWARD);
    }

    /** RS §3.5 as written (skip at non-owners, copies send their own updates). Expected to fail: see the report. */
    @GameTest(template = TEMPLATE, batch = "band_dust_skip_east", timeoutTicks = 200, required = false)
    public static void dustLineSkipRuleEast(GameTestHelper helper) {
        dustLine(helper, "east", 7, Band.Mode.SKIP);
    }

    // ---- 2. Repeaters ----

    private static void repeaterChain(GameTestHelper helper, String seam, int lane) {
        Run run = new Run(helper, site(helper, seam, lane)).prepare(Band.Mode.FORWARD, false);
        for (int u = -4; u <= 3; u++) run.set(u, 0, 0, repeater(run.seam));
        run.compareEachTick();
        long[] onAt = {-1, -1}, offAt = {-1, -1};
        long start = helper.getLevel().getGameTime();
        run.set(-5, 0, 0, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.onEachTick(() -> {
            long t = helper.getLevel().getGameTime() - start;
            boolean seamOn = run.seamState(3, 0, 0).getValue(DiodeBlock.POWERED), refOn = run.referenceState(3, 0, 0).getValue(DiodeBlock.POWERED);
            if (seamOn && onAt[0] < 0) onAt[0] = t;
            if (refOn && onAt[1] < 0) onAt[1] = t;
            if (!seamOn && onAt[0] >= 0 && offAt[0] < 0) offAt[0] = t;
            if (!refOn && onAt[1] >= 0 && offAt[1] < 0) offAt[1] = t;
        });
        helper.runAtTickTime(30, () -> run.both((site, pos) -> helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3), -5, 0, 0));
        helper.runAtTickTime(60, () -> {
            AlphaOmegaMod.LOGGER.info("Band repeaters {}: last on at {} (reference {}), off at {} (reference {})", run.seam.name(), onAt[0], onAt[1], offAt[0], offAt[1]);
            if (onAt[0] != onAt[1] || offAt[0] != offAt[1] || onAt[0] != 16) {
                run.release();
                helper.fail("repeaters across " + run.seam.name() + ": last on at " + onAt[0] + " (reference " + onAt[1] + ", expected 16), off at "
                    + offAt[0] + " (reference " + offAt[1] + ")");
            }
            run.finish("repeater chain");
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_repeaters_east", timeoutTicks = 200)
    public static void repeaterChainEast(GameTestHelper helper) {
        repeaterChain(helper, "east", 1);
    }

    @GameTest(template = TEMPLATE, batch = "band_repeaters_north", timeoutTicks = 200)
    public static void repeaterChainNorth(GameTestHelper helper) {
        repeaterChain(helper, "north", 1);
    }

    // ---- 3. Comparator reading a chest across ----

    private static void comparatorReadsChest(GameTestHelper helper, String seam, int lane) {
        Run run = new Run(helper, site(helper, seam, lane)).prepare(Band.Mode.FORWARD, false);
        run.set(0, 0, 0, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, run.seam.across().getOpposite()));
        run.set(-1, 0, 0, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, run.seam.across()));
        run.set(-2, 0, 0, dust(run.seam));
        run.set(-3, 0, 0, dust(run.seam));
        run.compareEachTick();
        // Fill both chests through the cell each comparator reads: across the seam that is the band copy (u = 0).
        helper.runAtTickTime(2, () -> run.both((site, pos) -> {
            BlockEntity chest = helper.getLevel().getBlockEntity(pos);
            if (!(chest instanceof Container container)) {
                run.release();
                helper.fail("no container reached at " + site.name() + " u=0: " + chest);
                return;
            }
            for (int i = 0; i < 10; i++) container.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }, 0, 0, 0));
        helper.runAtTickTime(12, () -> {
            BlockEntity comparator = helper.getLevel().getBlockEntity(run.seam.at(-1, 0, 0));
            int output = comparator instanceof ComparatorBlockEntity entity ? entity.getOutputSignal() : -1;
            int dust = run.seamState(-2, 0, 0).getValue(RedStoneWireBlock.POWER);
            if (output != 6 || dust != 6) {
                run.release();
                helper.fail("comparator across " + run.seam.name() + " outputs " + output + ", dust " + dust + ", expected 6; reference dust "
                    + run.referenceState(-2, 0, 0).getValue(RedStoneWireBlock.POWER));
            }
            BlockPos owner = run.seam.other(run.seam.at(0, 0, 0));
            if (!(helper.getLevel().getChunkAt(owner).getBlockEntities().get(owner) instanceof Container)) {
                run.release();
                helper.fail("the chest's block entity should live at its owner " + owner.toShortString());
            }
            // Empty it again: the comparator must follow.
            run.both((site, pos) -> {
                Container container = (Container) helper.getLevel().getBlockEntity(pos);
                for (int i = 0; i < 10; i++) container.setItem(i, ItemStack.EMPTY);
            }, 0, 0, 0);
        });
        helper.runAtTickTime(22, () -> {
            int dust = run.seamState(-2, 0, 0).getValue(RedStoneWireBlock.POWER);
            if (dust != 0) {
                run.release();
                helper.fail("comparator across " + run.seam.name() + " still powers dust at " + dust + " after the chest emptied");
            }
            run.finish("comparator");
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_comparator_east", timeoutTicks = 200)
    public static void comparatorReadsChestEast(GameTestHelper helper) {
        comparatorReadsChest(helper, "east", 2);
    }

    @GameTest(template = TEMPLATE, batch = "band_comparator_north", timeoutTicks = 200)
    public static void comparatorReadsChestNorth(GameTestHelper helper) {
        comparatorReadsChest(helper, "north", 2);
    }

    // ---- 4. Pistons ----

    private static void pistonPushAndPull(GameTestHelper helper, String seam, int lane, boolean claims) {
        Run run = new Run(helper, site(helper, seam, lane)).prepare(Band.Mode.FORWARD, claims);
        run.set(-2, 0, 0, Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, run.seam.across()));
        run.set(-1, 0, 0, Blocks.EMERALD_BLOCK.defaultBlockState());
        run.compareEachTick();
        run.set(-2, 1, 0, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAtTickTime(10, () -> {
            if (!run.seamState(0, 0, 0).is(Blocks.EMERALD_BLOCK) || !run.seamState(-1, 0, 0).is(Blocks.PISTON_HEAD)) {
                String why = "push across " + run.seam.name() + ": u=-1 " + run.seamState(-1, 0, 0) + ", u=0 " + run.seamState(0, 0, 0)
                    + "; reference u=0 " + run.referenceState(0, 0, 0) + "; " + BandCounters.lines();
                run.release();
                helper.fail(why);
            }
            run.both((site, pos) -> helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3), -2, 1, 0);
        });
        helper.runAtTickTime(20, () -> {
            if (!run.seamState(-1, 0, 0).is(Blocks.EMERALD_BLOCK) || !run.seamState(0, 0, 0).isAir()) {
                String why = "pull back across " + run.seam.name() + ": u=-1 " + run.seamState(-1, 0, 0) + ", u=0 " + run.seamState(0, 0, 0);
                run.release();
                helper.fail(why);
            }
            run.finish(claims ? "piston (block entity claims)" : "piston");
        });
    }

    /** The strict rule: no block entity ticks at a non-owner. The moving piston's block entity lands at one. */
    @GameTest(template = TEMPLATE, batch = "band_piston_strict_east", timeoutTicks = 200, required = false)
    public static void pistonStrictEast(GameTestHelper helper) {
        pistonPushAndPull(helper, "east", 3, false);
    }

    @GameTest(template = TEMPLATE, batch = "band_piston_strict_north", timeoutTicks = 200, required = false)
    public static void pistonStrictNorth(GameTestHelper helper) {
        pistonPushAndPull(helper, "north", 3, false);
    }

    @GameTest(template = TEMPLATE, batch = "band_piston_claims_east", timeoutTicks = 200)
    public static void pistonClaimsEast(GameTestHelper helper) {
        pistonPushAndPull(helper, "east", 4, true);
    }

    @GameTest(template = TEMPLATE, batch = "band_piston_claims_north", timeoutTicks = 200)
    public static void pistonClaimsNorth(GameTestHelper helper) {
        pistonPushAndPull(helper, "north", 4, true);
    }

    // ---- 5. Observers ----

    private static void observerClock(GameTestHelper helper, String seam, int lane) {
        Run run = new Run(helper, site(helper, seam, lane)).prepare(Band.Mode.FORWARD, false);
        BlockState watchingAcross = Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, run.seam.across());
        BlockState watchingBack = Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, run.seam.across().getOpposite());
        // Two observers facing each other across the seam, each with dust behind it: placing the second starts the clock.
        run.set(-2, 0, 0, dust(run.seam));
        run.set(1, 0, 0, dust(run.seam));
        run.both((site, pos) -> helper.getLevel().setBlock(pos, watchingAcross, 2 | 16), -1, 0, 0);
        run.compareEachTick();
        run.set(0, 0, 0, watchingBack);
        int[] pulses = {0, 0};
        boolean[] was = {false, false};
        helper.onEachTick(() -> {
            boolean seamOn = run.seamState(-1, 0, 0).getValue(ObserverBlock.POWERED), refOn = run.referenceState(-1, 0, 0).getValue(ObserverBlock.POWERED);
            if (seamOn && !was[0]) pulses[0]++;
            if (refOn && !was[1]) pulses[1]++;
            was[0] = seamOn;
            was[1] = refOn;
        });
        helper.runAtTickTime(40, () -> {
            AlphaOmegaMod.LOGGER.info("Band observer clock {}: {} pulses (reference {})", run.seam.name(), pulses[0], pulses[1]);
            if (pulses[0] != pulses[1] || pulses[0] < 5) {
                run.release();
                helper.fail("observer clock across " + run.seam.name() + ": " + pulses[0] + " pulses, reference " + pulses[1]);
            }
            run.finish("observer clock");
        });
    }

    @GameTest(template = TEMPLATE, batch = "band_observer_east", timeoutTicks = 200)
    public static void observerClockEast(GameTestHelper helper) {
        observerClock(helper, "east", 5);
    }

    @GameTest(template = TEMPLATE, batch = "band_observer_north", timeoutTicks = 200)
    public static void observerClockNorth(GameTestHelper helper) {
        observerClock(helper, "north", 5);
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

    private static void breaking(GameTestHelper helper, String seam, int lane) {
        Run run = new Run(helper, site(helper, seam, lane)).prepare(Band.Mode.FORWARD, false);
        ServerLevel level = helper.getLevel();
        BlockPos band = run.seam.at(0, 0, 0), owner = run.seam.other(band);
        List<String> wrong = new ArrayList<>();
        for (boolean atCopy : new boolean[] {true, false}) {
            String where = atCopy ? "the band copy" : "the owner";
            // A plain block.
            level.setBlock(band, Blocks.STONE.defaultBlockState(), 3);
            level.destroyBlock(atCopy ? band : owner, true);
            int drops = takeDrops(level, run.seam, band);
            if (drops != 1) wrong.add("stone broken at " + where + " dropped " + drops);
            if (!level.getBlockState(band).isAir() || !level.getBlockState(owner).isAir()) wrong.add("stone broken at " + where + " left a copy");
            // A chest with contents: the block entity lives at the owner.
            level.setBlock(band, Blocks.CHEST.defaultBlockState(), 3);
            if (!(level.getBlockEntity(band) instanceof Container container)) {
                wrong.add("chest placed at the band copy: no container reachable there");
                continue;
            }
            container.setItem(0, new ItemStack(Items.DIAMOND, 5));
            level.destroyBlock(atCopy ? band : owner, true);
            drops = takeDrops(level, run.seam, band);
            if (drops != 6) wrong.add("chest with 5 diamonds broken at " + where + " dropped " + drops + " items");
            if (!level.getBlockState(band).isAir() || !level.getBlockState(owner).isAir()) wrong.add("chest broken at " + where + " left a copy");
            if (level.getChunkAt(owner).getBlockEntities().containsKey(owner) || level.getChunkAt(band).getBlockEntities().containsKey(band)) {
                wrong.add("chest broken at " + where + " left a block entity");
            }
        }
        if (!wrong.isEmpty()) {
            run.release();
            helper.fail("breaking across " + run.seam.name() + ": " + wrong);
        }
        helper.runAtTickTime(2, () -> run.finish("breaking"));
    }

    @GameTest(template = TEMPLATE, batch = "band_breaking_east", timeoutTicks = 200)
    public static void breakingEast(GameTestHelper helper) {
        breaking(helper, "east", 6);
    }

    @GameTest(template = TEMPLATE, batch = "band_breaking_north", timeoutTicks = 200)
    public static void breakingNorth(GameTestHelper helper) {
        breaking(helper, "north", 6);
    }
}
