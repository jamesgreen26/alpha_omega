package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import g_mungus.alpha_omega.sky.LocalSky;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * F3 shows the local sun in the overworld, after the coordinates, then the orbifold lines (region, frame, depth past the
 * seam, source position, latitude, longitude and heading), and how much of the images is drawn.
 */
@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenOverlayMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyReturnValue(method = "getGameInformation", at = @At("RETURN"))
    private List<String> alpha_omega$sun(List<String> lines) {
        Entity camera = this.minecraft.getCameraEntity();
        if (camera == null) return lines;
        List<String> result = alpha_omega$withOrbifold(alpha_omega$withSun(lines, camera), camera);
        String neighbours = g_mungus.alpha_omega.client.ImageRenderer.debugLine();
        if (neighbours != null) {
            result = new ArrayList<>(result);
            result.add(neighbours);
        }
        return result;
    }

    /** The orbifold lines ({@link g_mungus.alpha_omega.client.OrbifoldDebug}) after the coordinates and the sun. */
    @Unique
    private static List<String> alpha_omega$withOrbifold(List<String> lines, Entity camera) {
        g_mungus.alpha_omega.orbifold.OrbifoldGeometry geometry = g_mungus.alpha_omega.orbifold.Orbifold.of(camera.level());
        if (geometry == null) return lines;
        List<String> result = new ArrayList<>(lines);
        int at = 0;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).startsWith("XYZ: ") || result.get(i).startsWith("Sun: ")) at = i + 1;
        }
        result.addAll(at, g_mungus.alpha_omega.client.OrbifoldDebug.lines(geometry, camera.getX(), camera.getY(), camera.getZ(), camera.getYRot(),
            LocalSky.local(camera.level())));
        return result;
    }

    @Unique
    private static List<String> alpha_omega$withSun(List<String> lines, Entity camera) {
        if (!LocalSky.active(camera.level())) return lines;
        LocalSky.Sample sun = LocalSky.sample(camera.level(), camera.getX(), camera.getZ());
        long ticks = Math.floorMod((long) Math.floor(sun.clock()), 24000L);
        long minutes = (ticks + 6000L) % 24000L * 60L / 1000L;
        double latitude = Math.toDegrees(sun.latitude());
        double longitude = -360.0 * sun.timeZone();
        String where = !sun.local() ? "" : String.format(Locale.ROOT, ", %.1f°%s %.1f°%s", Math.abs(latitude), latitude >= 0 ? "N" : "S",
            Math.abs(longitude), longitude >= 0 ? "E" : "W");
        String line = String.format(Locale.ROOT, "Sun: %02d:%02d day %d%s, alt %.1f° az %.0f°", minutes / 60, minutes % 60,
            sun.day(), where, Math.toDegrees(sun.altitude()), Math.toDegrees(sun.azimuth()));
        List<String> result = new ArrayList<>(lines);
        int at = 0;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).startsWith("XYZ: ")) at = i + 1;
        }
        result.add(at, line);
        return result;
    }
}
