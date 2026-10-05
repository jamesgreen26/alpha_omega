package g_mungus.alpha_omega.client.sky;

import g_mungus.alpha_omega.client.FaceCamera;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The local sun as the client sees it: at the camera, or at a given position. Just after the camera crosses an edge,
 * the new face's sun eases in from the old face's as the view turns ({@link FaceCamera}), so the sky's brightness
 * and colour do not jump from one face's time of day to the other's.
 */
public final class ClientSky {

    private ClientSky() {
    }

    /** Whether the client's sky follows the local sun (the overworld of a cube world). */
    public static boolean applies(Level level) {
        return level != null && LocalSky.local(level);
    }

    public static LocalSky.Sample at(Level level, Vec3 pos) {
        return easeOverEdge(level, LocalSky.sample(level, pos.x, pos.z));
    }

    public static LocalSky.Sample atCamera(Level level) {
        return at(level, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }

    private static LocalSky.Sample easeOverEdge(Level level, LocalSky.Sample sample) {
        CubeFace from = FaceCamera.from();
        if (from == null || sample.face() != FaceCamera.to() || !LocalSky.local(level)) return sample;
        float weight = FaceCamera.weight(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        return LocalSky.blend(LocalSky.sample(level, from), sample, weight);
    }

    /** Compass bearing of the sun as a rotation about +Y that takes +X (vanilla's sunrise side) to the sun's side. */
    public static float sunYaw(LocalSky.Sample sun) {
        if (Math.abs(sun.sunX()) < 1e-6 && Math.abs(sun.sunZ()) < 1e-6) return 0.0F;
        return (float) Math.atan2(-sun.sunZ(), sun.sunX());
    }
}
