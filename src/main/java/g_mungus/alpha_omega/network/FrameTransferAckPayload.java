package g_mungus.alpha_omega.network;

import g_mungus.alpha_omega.AlphaOmegaMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A client has applied the server-driven transfer {@code index}: its movement from here on is in the new frame. */
public record FrameTransferAckPayload(int index) implements CustomPacketPayload {

    public static final Type<FrameTransferAckPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "frame_transfer_ack"));
    public static final StreamCodec<ByteBuf, FrameTransferAckPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(FrameTransferAckPayload::new,
        FrameTransferAckPayload::index);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
