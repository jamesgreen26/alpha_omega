package g_mungus.alpha_omega.network;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.wrap.WorldWrapSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The world's wrapping, sent to clients during configuration, before any play packet needs normalizing. */
public record WrapSettingsPayload(WorldWrapSettings settings) implements CustomPacketPayload {

    public static final Type<WrapSettingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "wrap_settings"));
    public static final StreamCodec<ByteBuf, WrapSettingsPayload> STREAM_CODEC =
        WorldWrapSettings.STREAM_CODEC.map(WrapSettingsPayload::new, WrapSettingsPayload::settings);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
