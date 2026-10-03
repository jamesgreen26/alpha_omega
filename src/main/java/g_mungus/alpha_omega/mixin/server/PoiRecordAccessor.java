package g_mungus.alpha_omega.mixin.server;

import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PoiRecord.class)
public interface PoiRecordAccessor {

    @Invoker("acquireTicket")
    boolean alpha_omega$acquireTicket();

    @Invoker("releaseTicket")
    boolean alpha_omega$releaseTicket();
}
