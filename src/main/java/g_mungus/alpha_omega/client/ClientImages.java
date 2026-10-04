package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side bridge for effects (mod-compatibility §5.3): sounds and particles may be placed at any image, often a
 * canonical block entity position from another mod, and are moved to the image nearest the camera, where they can
 * be seen and heard.
 */
public final class ClientImages {

    private ClientImages() {
    }

    public static Wrap wrap() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? Wrap.NONE : Wrap.of(level);
    }

    /** The camera position, or null before the camera has been set up. */
    @Nullable
    public static Vec3 camera() {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        return camera.isInitialized() ? camera.getPosition() : null;
    }

    /** The image of x nearest the camera; x itself if there is no camera yet. */
    public static double nearCameraX(double x) {
        Vec3 camera = camera();
        return camera == null ? x : wrap().nearest(x, camera.x);
    }

    public static double nearCameraZ(double z) {
        Vec3 camera = camera();
        return camera == null ? z : wrap().nearest(z, camera.z);
    }
}
