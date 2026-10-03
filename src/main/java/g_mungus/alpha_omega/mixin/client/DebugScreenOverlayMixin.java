package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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
import org.spongepowered.asm.mixin.injection.At;

/**
 * F3 shows canonical coordinates (§9.1): the client's own coordinates grow by the world size every lap, which
 * would only confuse. The lap is shown on its own line.
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
        if (!wrap.enabled()) return lines;
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
        return result;
    }
}
