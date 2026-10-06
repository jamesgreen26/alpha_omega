package g_mungus.alpha_omega.network;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.orbifold.Motion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A client telling the server its player (or the vehicle it drives) crossed a seam: the element it moved by, where it
 * was just before (in the old frame) and which way it was looking. Movement after this is in the new frame.
 *
 * <p>{@code claim} numbers the client's claims from 1; {@code seen} is how many server-driven transfers
 * ({@link ServerFrameTransferPayload}) the client had applied when it crossed. A claim made before the client knew of
 * a server-driven transfer is stale: the server drops it, and the client undoes it when that transfer arrives.
 */
public record FrameTransferPayload(Motion motion, double x, double y, double z, float yRot, float xRot, int claim, int seen)
    implements CustomPacketPayload {

    public static final Type<FrameTransferPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "frame_transfer"));
    public static final StreamCodec<ByteBuf, FrameTransferPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            writeMotion(buffer, payload.motion);
            buffer.writeDouble(payload.x);
            buffer.writeDouble(payload.y);
            buffer.writeDouble(payload.z);
            buffer.writeFloat(payload.yRot);
            buffer.writeFloat(payload.xRot);
            ByteBufCodecs.VAR_INT.encode(buffer, payload.claim);
            ByteBufCodecs.VAR_INT.encode(buffer, payload.seen);
        },
        buffer -> new FrameTransferPayload(readMotion(buffer), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(),
            buffer.readFloat(), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer)));

    static void writeMotion(ByteBuf buffer, Motion motion) {
        buffer.writeBoolean(motion.turned());
        ByteBufCodecs.VAR_INT.encode(buffer, motion.tx());
        ByteBufCodecs.VAR_INT.encode(buffer, motion.tz());
    }

    static Motion readMotion(ByteBuf buffer) {
        return new Motion(buffer.readBoolean(), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
