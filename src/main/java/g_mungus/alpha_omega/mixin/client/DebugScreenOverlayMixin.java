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

/** F3 shows the local sun in the overworld, after the coordinates, and how much of the neighbouring faces is drawn. */
@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenOverlayMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyReturnValue(method = "getGameInformation", at = @At("RETURN"))
    private List<String> alpha_omega$sun(List<String> lines) {
        Entity camera = this.minecraft.getCameraEntity();
        if (camera == null) return lines;
        List<String> result = alpha_omega$withSun(lines, camera);
        String neighbours = g_mungus.alpha_omega.client.NeighbourRenderer.debugLine();
        if (neighbours != null) {
            result = new ArrayList<>(result);
            result.add(neighbours);
        }
        return result;
    }

    @Unique
    private static List<String> alpha_omega$withSun(List<String> lines, Entity camera) {
        if (!LocalSky.active(camera.level())) return lines;
        LocalSky.Sample sun = LocalSky.sample(camera.level(), camera.getX(), camera.getZ());
        long ticks = Math.floorMod((long) Math.floor(sun.clock()), 24000L);
        long minutes = (ticks + 6000L) % 24000L * 60L / 1000L;
        String where = sun.face() == null ? "" : String.format(Locale.ROOT, " on %s (lat %.1f°, %+.1f h)",
            sun.face(), Math.toDegrees(sun.latitude()), -24.0 * sun.timeZone());
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
