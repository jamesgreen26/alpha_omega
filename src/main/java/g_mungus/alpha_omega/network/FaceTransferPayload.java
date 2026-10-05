package g_mungus.alpha_omega.network;

import g_mungus.alpha_omega.AlphaOmegaMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A client telling the server its player (or the vehicle it drives) crossed an edge: from which face to which,
 * where it was just before (in the old face's storage) and which way it was looking. Movement after this is in the
 * new face's storage.
 */
public record FaceTransferPayload(int from, int to, double x, double y, double z, float yRot, float xRot) implements CustomPacketPayload {

    public static final Type<FaceTransferPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "face_transfer"));
    public static final StreamCodec<ByteBuf, FaceTransferPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            ByteBufCodecs.VAR_INT.encode(buffer, payload.from);
            ByteBufCodecs.VAR_INT.encode(buffer, payload.to);
            buffer.writeDouble(payload.x);
            buffer.writeDouble(payload.y);
            buffer.writeDouble(payload.z);
            buffer.writeFloat(payload.yRot);
            buffer.writeFloat(payload.xRot);
        },
        buffer -> new FaceTransferPayload(ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
            buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
