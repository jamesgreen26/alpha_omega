package g_mungus.alpha_omega.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSize;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** {@link Orbifold}'s mappings agree with the geometry they wrap. */
class OrbifoldApiTest {

    private static final List<OrbifoldGeometry> SIZES = List.of(new OrbifoldGeometry(OrbifoldSize.SMALL, 4), new OrbifoldGeometry(OrbifoldSize.NORMAL, 4));

    /** A random footprint cell, often near an edge or corner. */
    private static BlockPos cell(OrbifoldGeometry g, Random random) {
        int x = random.nextBoolean() ? g.minX - g.reach + random.nextInt(g.a + 2 * g.reach)
            : (random.nextBoolean() ? g.minX : g.maxX) + random.nextInt(2 * g.reach) - g.reach;
        int z = random.nextBoolean() ? g.northRow - g.reach + random.nextInt(g.b / 2 + 2 * g.reach)
            : (random.nextBoolean() ? g.northRow : g.southRow) + random.nextInt(2 * g.reach) - g.reach;
        return new BlockPos(x, 64, z);
    }

    @Test
    void canonAndCopiesMatchTheGeometry() {
        Random random = new Random(7);
        for (OrbifoldGeometry g : SIZES) {
            Orbifold o = Orbifold.of(g);
            for (int i = 0; i < 2000; i++) {
                BlockPos pos = cell(g, random);
                OrbifoldGeometry.Cell canon = g.canon(pos.getX(), pos.getZ());
                BlockPos source = o.canon(pos);
                assertEquals(new BlockPos(canon.x(), 64, canon.z()), source);
                assertTrue(o.isTile(source));
                assertEquals(g.isTile(pos.getX(), pos.getZ()), o.isTile(pos));
                assertEquals(g.isBand(pos.getX(), pos.getZ()), o.isBand(pos));
                assertEquals(g.isSkirt(pos.getX(), pos.getZ()), o.isSkirt(pos));
                assertEquals(canon.frame(), o.frame(pos));
                List<BlockPos> copies = o.copies(pos);
                assertEquals(source, copies.get(0));
                assertTrue(copies.contains(pos), "every stored position of a block is among its copies: " + pos);
                assertEquals(1 + g.copies(canon.x(), canon.z()).size(), copies.size());
                for (BlockPos copy : copies) assertEquals(source, o.canon(copy));
                // Points: the centre of the cell canonicalises to the centre of its source.
                Vec3 centre = Vec3.atCenterOf(pos);
                assertEquals(Vec3.atCenterOf(source), o.canon(centre));
            }
        }
    }

    @Test
    void toFrameFollowsTheReferenceFrame() {
        Random random = new Random(8);
        for (OrbifoldGeometry g : SIZES) {
            Orbifold o = Orbifold.of(g);
            for (int i = 0; i < 500; i++) {
                BlockPos pos = cell(g, random);
                BlockPos ref = cell(g, random);
                BlockPos mapped = o.toFrame(pos, Vec3.atCenterOf(ref));
                // Same block, and in the reference's frame: the reference's frame takes it to the source.
                assertEquals(o.canon(pos), Orbifold.transform(o.frame(ref), mapped));
            }
            // Concretely: a source by the east edge, in the frame of a position in the west band, is its band copy there.
            int z = g.northRow + 300;
            assertEquals(new BlockPos(g.minX - 5, 70, z), o.toFrame(new BlockPos(g.maxX - 5, 70, z), new Vec3(g.minX - 20.5, 70, z + 0.5)));
            // In the tile's frame, a band position is its source.
            assertEquals(new BlockPos(g.maxX - 5, 70, z), o.toFrame(new BlockPos(g.minX - 5, 70, z), new Vec3(0.5, 70, z)));
        }
    }

    @Test
    void nearestAndDistance() {
        for (OrbifoldGeometry g : SIZES) {
            Orbifold o = Orbifold.of(g);
            int z = g.northRow + 300;
            Vec3 holder = new Vec3(g.minX - 20.5, 70, z + 0.5);
            assertEquals(new BlockPos(g.minX - 5, 70, z), o.nearest(new BlockPos(g.maxX - 5, 70, z), holder));
            assertEquals(15.0, o.distance(new Vec3(g.maxX - 5.5, 0, z + 0.5), holder), 1e-9);
            Motion h = o.nearestMotion(new Vec3(400.5, 0, g.northRow + 10.5), new Vec3(-400.5, 0, g.northRow - 20.5));
            assertEquals(g.northFold, h);
            assertFalse(o.nearestMotion(Vec3.ZERO, new Vec3(1, 0, 1)).turned());
        }
    }

    @Test
    void transformsAreTheGeometrysMotions() {
        OrbifoldGeometry g = SIZES.get(1);
        BlockPos pos = new BlockPos(10, 70, g.northRow - 5);
        assertEquals(new BlockPos(g.northFold.cellX(10), 70, g.northFold.cellZ(g.northRow - 5)), Orbifold.transform(g.northFold, pos));
        assertEquals(new Vec3(-10.25, 70, 2 * g.northRow + 5.5), Orbifold.transform(g.northFold, new Vec3(10.25, 70, -5.5)));
        assertEquals(new Vec3(-1, 0, -2), Orbifold.transformVector(g.northFold, new Vec3(1, 0, 2)));
        assertEquals(270.0F, Orbifold.transformYaw(g.southFold, 90.0F));
        assertEquals(new Vec3(1, 0, 2).add(g.a, 0, 0), Orbifold.transform(g.east, new Vec3(1, 0, 2)));
    }
}
