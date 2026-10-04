package g_mungus.alpha_omega.compat.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Sub-level poses arrive in the server's frame; like every other position the client receives, they are moved to the
 * image nearest the camera (§8). Rotation points are plot coordinates and stay put.
 */
public final class SableClientFrames {

    private SableClientFrames() {
    }

    /** {@code pose} with its position moved to the image nearest the camera; a copy if it moves. */
    public static Pose3dc nearCamera(Pose3dc pose) {
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        if (pose == null || mc.level == null || camera == null) return pose;
        Wrap wrap = Wrap.of(mc.level);
        double x = wrap.nearest(pose.position().x(), camera.getX());
        double z = wrap.nearest(pose.position().z(), camera.getZ());
        if (x == pose.position().x() && z == pose.position().z()) return pose;
        Pose3d moved = new Pose3d(pose);
        moved.position().set(x, moved.position().y(), z);
        return moved;
    }
}
