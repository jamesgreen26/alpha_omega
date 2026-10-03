package g_mungus.alpha_omega.mixin.server;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Movement validation state, which must follow the player when it is re-framed. */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {

    @Accessor("firstGoodX") double alpha_omega$getFirstGoodX();
    @Accessor("firstGoodX") void alpha_omega$setFirstGoodX(double value);
    @Accessor("firstGoodZ") double alpha_omega$getFirstGoodZ();
    @Accessor("firstGoodZ") void alpha_omega$setFirstGoodZ(double value);
    @Accessor("lastGoodX") double alpha_omega$getLastGoodX();
    @Accessor("lastGoodX") void alpha_omega$setLastGoodX(double value);
    @Accessor("lastGoodZ") double alpha_omega$getLastGoodZ();
    @Accessor("lastGoodZ") void alpha_omega$setLastGoodZ(double value);
    @Accessor("vehicleFirstGoodX") double alpha_omega$getVehicleFirstGoodX();
    @Accessor("vehicleFirstGoodX") void alpha_omega$setVehicleFirstGoodX(double value);
    @Accessor("vehicleFirstGoodZ") double alpha_omega$getVehicleFirstGoodZ();
    @Accessor("vehicleFirstGoodZ") void alpha_omega$setVehicleFirstGoodZ(double value);
    @Accessor("vehicleLastGoodX") double alpha_omega$getVehicleLastGoodX();
    @Accessor("vehicleLastGoodX") void alpha_omega$setVehicleLastGoodX(double value);
    @Accessor("vehicleLastGoodZ") double alpha_omega$getVehicleLastGoodZ();
    @Accessor("vehicleLastGoodZ") void alpha_omega$setVehicleLastGoodZ(double value);
    @Accessor("awaitingPositionFromClient") Vec3 alpha_omega$getAwaitingPositionFromClient();
    @Accessor("awaitingPositionFromClient") void alpha_omega$setAwaitingPositionFromClient(Vec3 value);
}
