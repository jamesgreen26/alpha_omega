package g_mungus.alpha_omega.network;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSettings;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** The overworld's orbifold settings, sent to clients during configuration; empty when the world is not an orbifold. */
public record OrbifoldPayload(Optional<OrbifoldSettings> settings) implements CustomPacketPayload {

    public static final Type<OrbifoldPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "orbifold"));
    public static final StreamCodec<ByteBuf, OrbifoldPayload> STREAM_CODEC =
        ByteBufCodecs.optional(OrbifoldSettings.STREAM_CODEC).map(OrbifoldPayload::new, OrbifoldPayload::settings);

    public static OrbifoldPayload of(@Nullable OrbifoldGeometry geometry) {
        return new OrbifoldPayload(Optional.ofNullable(geometry).map(g -> new OrbifoldSettings(g.size, g.bandChunks)));
    }

    public Optional<OrbifoldGeometry> geometry() {
        return this.settings.map(OrbifoldSettings::geometry);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
