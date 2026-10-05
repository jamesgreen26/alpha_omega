package g_mungus.alpha_omega.network;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.cube.CubeSettings;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The overworld's cube geometry, sent to clients during configuration; empty when the world is not a cube. */
public record CubePayload(Optional<Spec> spec) implements CustomPacketPayload {

    public static final Type<CubePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "cube"));
    public static final StreamCodec<ByteBuf, CubePayload> STREAM_CODEC =
        ByteBufCodecs.optional(Spec.STREAM_CODEC).map(CubePayload::new, CubePayload::spec);

    public static CubePayload of(CubeGeometry geometry) {
        return new CubePayload(Optional.ofNullable(geometry).map(g -> new Spec(g.settings, g.planeY, g.minY, g.maxY)));
    }

    public Optional<CubeGeometry> geometry() {
        return this.spec.map(s -> new CubeGeometry(s.settings, s.planeY, s.minY, s.maxY));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Spec(CubeSettings settings, int planeY, int minY, int maxY) {

        static final StreamCodec<ByteBuf, Spec> STREAM_CODEC = StreamCodec.composite(
            CubeSettings.STREAM_CODEC, Spec::settings,
            ByteBufCodecs.VAR_INT, Spec::planeY,
            ByteBufCodecs.VAR_INT, Spec::minY,
            ByteBufCodecs.VAR_INT, Spec::maxY,
            Spec::new);
    }
}
