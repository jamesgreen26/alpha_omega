package g_mungus.alpha_omega.network;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSettings;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The orbifold settings of the overworld and the Nether, sent to clients during configuration; each empty when that
 * dimension is not an orbifold.
 */
public record OrbifoldPayload(Optional<OrbifoldSettings> settings, Optional<OrbifoldSettings> nether) implements CustomPacketPayload {

    public static final Type<OrbifoldPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "orbifold"));
    public static final StreamCodec<ByteBuf, OrbifoldPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.optional(OrbifoldSettings.STREAM_CODEC), OrbifoldPayload::settings,
        ByteBufCodecs.optional(OrbifoldSettings.STREAM_CODEC), OrbifoldPayload::nether,
        OrbifoldPayload::new);

    /** The overworld's geometry only. */
    public static OrbifoldPayload of(@Nullable OrbifoldGeometry geometry) {
        return of(geometry, null);
    }

    public static OrbifoldPayload of(@Nullable OrbifoldGeometry overworld, @Nullable OrbifoldGeometry nether) {
        return new OrbifoldPayload(Optional.ofNullable(overworld).map(OrbifoldSettings::of), Optional.ofNullable(nether).map(OrbifoldSettings::of));
    }

    /** What a server tells its clients. */
    public static OrbifoldPayload of(@Nullable MinecraftServer server) {
        if (server == null) return of(null, null);
        ServerLevel nether = server.getLevel(Level.NETHER);
        return of(Orbifold.of(server.overworld()), nether == null ? null : Orbifold.of(nether));
    }

    /** The overworld's geometry. */
    public Optional<OrbifoldGeometry> geometry() {
        return this.settings.map(OrbifoldSettings::geometry);
    }

    /** The Nether's geometry. */
    public Optional<OrbifoldGeometry> netherGeometry() {
        return this.nether.map(OrbifoldSettings::geometry);
    }

    /** Every orbifold dimension's geometry. */
    public Map<ResourceKey<Level>, OrbifoldGeometry> geometries() {
        Map<ResourceKey<Level>, OrbifoldGeometry> geometries = new HashMap<>();
        this.geometry().ifPresent(g -> geometries.put(Level.OVERWORLD, g));
        this.netherGeometry().ifPresent(g -> geometries.put(Level.NETHER, g));
        return geometries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
