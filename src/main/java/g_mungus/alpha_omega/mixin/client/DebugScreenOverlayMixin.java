package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import g_mungus.alpha_omega.sky.LocalSky;
import g_mungus.alpha_omega.wrap.Wrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * F3 shows canonical coordinates (§9.1): the client's own coordinates grow by the world size every lap, which
 * would only confuse. The lap is shown on its own line, followed by the local sun in the overworld.
 */
@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenOverlayMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyReturnValue(method = "getGameInformation", at = @At("RETURN"))
    private List<String> alpha_omega$canonicalCoordinates(List<String> lines) {
        Entity camera = this.minecraft.getCameraEntity();
        if (camera == null) return lines;
        Wrap wrap = Wrap.of(camera.level());
        if (!wrap.enabled()) return alpha_omega$withSun(lines, camera);
        BlockPos block = wrap.canon(camera.blockPosition());
        ChunkPos chunk = new ChunkPos(block);
        List<String> result = new ArrayList<>(lines.size() + 1);
        for (String line : lines) {
            if (line.startsWith("XYZ: ")) {
                result.add(String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f", wrap.canon(camera.getX()), camera.getY(), wrap.canon(camera.getZ())));
                result.add(String.format(Locale.ROOT, "Lap: %d %d (world wraps every %d)", wrap.lap(camera.getBlockX()), wrap.lap(camera.getBlockZ()), wrap.period));
            } else if (line.startsWith("Block: ")) {
                result.add(String.format(Locale.ROOT, "Block: %d %d %d [%d %d %d]", block.getX(), block.getY(), block.getZ(), block.getX() & 15, block.getY() & 15, block.getZ() & 15));
            } else if (line.startsWith("Chunk: ")) {
                result.add(String.format(Locale.ROOT, "Chunk: %d %d %d [%d %d in r.%d.%d.mca]", chunk.x, block.getY() >> 4, chunk.z,
                    chunk.getRegionLocalX(), chunk.getRegionLocalZ(), chunk.getRegionX(), chunk.getRegionZ()));
            } else {
                result.add(line);
            }
        }
        return alpha_omega$withSun(result, camera);
    }

    @Unique
    private static List<String> alpha_omega$withSun(List<String> lines, Entity camera) {
        if (!LocalSky.active(camera.level())) return lines;
        LocalSky.Sample sun = LocalSky.sample(camera.level(), camera.getX(), camera.getZ());
        long ticks = Math.floorMod((long) Math.floor(sun.clock()), 24000L);
        long minutes = (ticks + 6000L) % 24000L * 60L / 1000L;
        double latitude = Math.toDegrees(sun.latitude());
        double longitude = 360.0 * sun.longitude();
        String line = String.format(Locale.ROOT, "Sun: %02d:%02d day %d, %.1f°%s %.1f°%s, alt %.1f° az %.0f°", minutes / 60, minutes % 60,
            sun.day(), Math.abs(latitude), latitude >= 0 ? "N" : "S", Math.abs(longitude), longitude >= 0 ? "E" : "W",
            Math.toDegrees(sun.altitude()), Math.toDegrees(sun.azimuth()));
        List<String> result = new ArrayList<>(lines);
        int at = 0;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).startsWith("XYZ: ") || result.get(i).startsWith("Lap: ")) at = i + 1;
        }
        result.add(at, line);
        return result;
    }
}
