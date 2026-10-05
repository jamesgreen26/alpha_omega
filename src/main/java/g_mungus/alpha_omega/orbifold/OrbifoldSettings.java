package g_mungus.alpha_omega.orbifold;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * An orbifold world's shape, chosen when the world is created and kept in its overworld generator (so in
 * {@code level.dat}). {@code sizeFactor} is the wrapping plan's {@code k} (2, 4 or 8); {@code bandChunks} is the band
 * depth {@code H} in chunks.
 */
public record OrbifoldSettings(int sizeFactor, int bandChunks) {

    public static final OrbifoldSettings DEFAULT = new OrbifoldSettings(OrbifoldGeometry.DEFAULT_SIZE_FACTOR, OrbifoldGeometry.DEFAULT_BAND_CHUNKS);

    public static final StreamCodec<ByteBuf, OrbifoldSettings> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, OrbifoldSettings::sizeFactor,
        ByteBufCodecs.VAR_INT, OrbifoldSettings::bandChunks,
        OrbifoldSettings::new);

    private static final Codec<Integer> SIZE_FACTOR = Codec.INT.validate(k -> OrbifoldGeometry.SIZE_FACTORS.contains(k)
        ? DataResult.success(k) : DataResult.error(() -> "Size factor must be one of " + OrbifoldGeometry.SIZE_FACTORS + ": " + k));

    public OrbifoldSettings {
        if (!OrbifoldGeometry.SIZE_FACTORS.contains(sizeFactor)) {
            throw new IllegalArgumentException("Size factor must be one of " + OrbifoldGeometry.SIZE_FACTORS + ": " + sizeFactor);
        }
        if (bandChunks < OrbifoldGeometry.MIN_BAND_CHUNKS || bandChunks > OrbifoldGeometry.MAX_BAND_CHUNKS) {
            throw new IllegalArgumentException("Band must be " + OrbifoldGeometry.MIN_BAND_CHUNKS + " to " + OrbifoldGeometry.MAX_BAND_CHUNKS
                + " chunks: " + bandChunks);
        }
    }

    /**
     * Fields of a generator's codec. Missing fields (a world preset that leaves them out) take the defaults from
     * {@code defaults}, read when the codec decodes; they are written out explicitly, so a saved world keeps them.
     */
    public static MapCodec<OrbifoldSettings> fieldsCodec(Supplier<OrbifoldSettings> defaults) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
            SIZE_FACTOR.optionalFieldOf("size_factor").forGetter(s -> Optional.of(s.sizeFactor)),
            Codec.intRange(OrbifoldGeometry.MIN_BAND_CHUNKS, OrbifoldGeometry.MAX_BAND_CHUNKS).optionalFieldOf("band_chunks")
                .forGetter(s -> Optional.of(s.bandChunks))
        ).apply(instance, (sizeFactor, bandChunks) -> {
            OrbifoldSettings fallback = defaults.get();
            return new OrbifoldSettings(sizeFactor.orElse(fallback.sizeFactor), bandChunks.orElse(fallback.bandChunks));
        }));
    }

    public OrbifoldSettings withSizeFactor(int sizeFactor) {
        return new OrbifoldSettings(sizeFactor, this.bandChunks);
    }

    public OrbifoldGeometry geometry() {
        return new OrbifoldGeometry(this.sizeFactor, this.bandChunks);
    }
}
