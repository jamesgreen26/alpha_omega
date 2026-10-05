package g_mungus.alpha_omega.orbifold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A {@link Motion} applied to Minecraft's types: the thin adapter over its pure maps. A half turn uses
 * {@link Rotation#CLOCKWISE_180} for block states, directions and shapes, and adds 180° to yaw.
 */
public record Transform(Motion motion) {

    public static Transform of(Motion motion) {
        return new Transform(motion);
    }

    /** The block cell at {@code pos} maps to this cell. */
    public BlockPos block(BlockPos pos) {
        return new BlockPos(this.motion.cellX(pos.getX()), pos.getY(), this.motion.cellZ(pos.getZ()));
    }

    public ChunkPos chunk(ChunkPos pos) {
        return new ChunkPos(this.motion.chunkX(pos.x), this.motion.chunkZ(pos.z));
    }

    public Vec3 position(Vec3 pos) {
        return new Vec3(this.motion.pointX(pos.x), pos.y, this.motion.pointZ(pos.z));
    }

    /** A direction or velocity: turned, not moved. */
    public Vec3 vector(Vec3 vector) {
        return new Vec3(this.motion.vectorX(vector.x), vector.y, this.motion.vectorZ(vector.z));
    }

    public float yaw(float yaw) {
        return this.motion.yaw(yaw);
    }

    public Rotation rotation() {
        return this.motion.turned() ? Rotation.CLOCKWISE_180 : Rotation.NONE;
    }

    public Direction direction(Direction direction) {
        return this.rotation().rotate(direction);
    }

    public BlockState state(BlockState state) {
        return this.motion.turned() ? state.rotate(Rotation.CLOCKWISE_180) : state;
    }

    public AABB box(AABB box) {
        double x1 = this.motion.pointX(box.minX), x2 = this.motion.pointX(box.maxX);
        double z1 = this.motion.pointZ(box.minZ), z2 = this.motion.pointZ(box.maxZ);
        return new AABB(Math.min(x1, x2), box.minY, Math.min(z1, z2), Math.max(x1, x2), box.maxY, Math.max(z1, z2));
    }
}
