package g_mungus.alpha_omega.mixin.bridges;

import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A POI record's ticket counting, for records seen through an image ({@code bridge.ImagePoiRecord}). */
@Mixin(PoiRecord.class)
public interface PoiRecordInvoker {

    @Invoker("acquireTicket")
    boolean alpha_omega$acquireTicket();

    @Invoker("releaseTicket")
    boolean alpha_omega$releaseTicket();
}
