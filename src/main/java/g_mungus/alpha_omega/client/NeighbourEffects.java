package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Particles and sounds that start on a neighbouring face are moved to where that face really is, in the storage of
 * the face the camera is on (design §6.3). Particles then fly by the camera face's rules, which is close enough.
 */
public final class NeighbourEffects {

    private NeighbourEffects() {
    }

    /** The transform for a point, as {@code {from, to}}, or null when it is already on the camera's face. */
    @Nullable
    public static CubeFace[] faces(Level level, double x, double z) {
        CubeGeometry geometry = Cube.of(level);
        if (geometry == null) return null;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        CubeFace home = geometry.faceAt(camera.x, camera.z);
        CubeFace there = geometry.faceAt(x, z);
        if (home == null || there == null || home == there) return null;
        return new CubeFace[] {there, home};
    }

    public static double[] position(Level level, CubeFace[] faces, double x, double y, double z) {
        return Cube.of(level).transform(faces[0], faces[1], x, y, z);
    }

    public static double[] direction(CubeFace[] faces, double x, double y, double z) {
        return CubeGeometry.rotate(faces[0], faces[1], x, y, z);
    }
}
