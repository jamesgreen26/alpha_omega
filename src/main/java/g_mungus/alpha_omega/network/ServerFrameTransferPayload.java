package g_mungus.alpha_omega.network;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.orbifold.Motion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The server moved a client's player (and the vehicle it rides) by an element of {@code Γ}: its group took another
 * frame, an interaction pulled it into the owner's, or a vehicle the server moves crossed. It carries the element, not a
 * position, so the client applies it to whatever state it has and nothing snaps back.
 *
 * <p>{@code index} counts the server-driven transfers of this player from 1; {@code claims} is how many of the
 * client's own claims the server had handled before this one. Claims after that were made in a frame the server has
 * left, and it drops them: the client undoes them before applying this element. Until the client acknowledges
 * ({@link FrameTransferAckPayload}), the server ignores its movement, which is in the old frame.
 */
public record ServerFrameTransferPayload(Motion motion, int index, int claims) implements CustomPacketPayload {

    public static final Type<ServerFrameTransferPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "server_frame_transfer"));
    public static final StreamCodec<ByteBuf, ServerFrameTransferPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            FrameTransferPayload.writeMotion(buffer, payload.motion);
            ByteBufCodecs.VAR_INT.encode(buffer, payload.index);
            ByteBufCodecs.VAR_INT.encode(buffer, payload.claims);
        },
        buffer -> new ServerFrameTransferPayload(FrameTransferPayload.readMotion(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
            ByteBufCodecs.VAR_INT.decode(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
