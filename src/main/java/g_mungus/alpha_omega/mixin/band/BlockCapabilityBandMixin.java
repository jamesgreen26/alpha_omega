package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.band.CopyLinks;
import g_mungus.alpha_omega.band.Ownership;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Capabilities at a non-owner copy (RS §3.4): {@code Level.getCapability(cap, pos, side)} is answered by the owner's
 * position, with {@code side} turned by the motion between them. Covers item, fluid and energy handlers, and with them
 * hoppers and most modded pipes.
 */
@Mixin(value = BlockCapability.class, remap = false)
abstract class BlockCapabilityBandMixin {

    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void alpha_omega$atOwner(Level level, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity entity, Object context,
        CallbackInfoReturnable<Object> cir) {
        if (level.isClientSide || Band.ignoreOwnership) return;
        LevelChunk chunk = Band.linkedChunk(level, pos);
        if (chunk == null || Ownership.isOwner(chunk, pos)) return;
        CopyLinks.Link owner = Ownership.owner(level, chunk, pos);
        if (owner == null) return;
        if (owner.chunk(level) == null) {
            cir.setReturnValue(null);
            return;
        }
        Object side = context instanceof Direction direction ? Band.turn(direction, owner.turned) : context;
        BandCounters.capabilityRedirects++;
        cir.setReturnValue(((BlockCapability) (Object) this).getCapability(level, owner.map(pos), null, null, side));
    }
}
