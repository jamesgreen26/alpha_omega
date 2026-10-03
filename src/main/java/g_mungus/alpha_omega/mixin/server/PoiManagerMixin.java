package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapHolder;
import g_mungus.alpha_omega.wrap.poi.PoiRecordImage;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** R1/R2: POI records hold canonical positions. */
@Mixin(PoiManager.class)
abstract class PoiManagerMixin {

    @ModifyVariable(method = {"add", "remove", "release", "exists", "getType", "getFreeTickets"}, at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$canonPos(BlockPos pos) {
        return WrapHolder.of(this).canon(pos);
    }

    /**
     * R5: POI reads. Every range query walks chunks around its origin through {@code getInChunk}; records are stored
     * canonically, so present them at the image of the chunk that was asked for. Distance filters, sorting and the
     * returned positions then all happen in the caller's frame. Tickets still go to the stored record.
     */
    @ModifyReturnValue(method = "getInChunk", at = @At("RETURN"))
    private Stream<PoiRecord> alpha_omega$atRequestedImage(Stream<PoiRecord> records, @Local(argsOnly = true) ChunkPos chunk) {
        Wrap wrap = WrapHolder.of(this);
        int dx = (chunk.x - wrap.canonChunk(chunk.x)) << 4;
        int dz = (chunk.z - wrap.canonChunk(chunk.z)) << 4;
        return dx == 0 && dz == 0 ? records : records.map(record -> new PoiRecordImage(record, dx, dz));
    }
}
