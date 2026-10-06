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
 * {@code level.dat}). {@code size} is one of the {@link OrbifoldSize#PRESETS}; {@code bandChunks} is the band depth
 * {@code H} in chunks.
 *
 * <p><b>Saved form.</b> A size with a size factor {@code k} is saved as {@code size_factor: k}, exactly as before
 * sizes had names, so worlds saved then load unchanged and saves of those sizes are unchanged too. A size without one
 * (the small size) is saved as {@code size: "<id>"}. When reading, {@code size} takes precedence over
 * {@code size_factor}; either may name any preset it can.
 *
 * <p><b>Scale.</b> The Nether's generator carries the same settings with {@code scale: 8}: its geometry is the
 * preset at 1:8 ({@link OrbifoldGeometry#NETHER_SCALE}). The overworld's scale is 1, never written. A Nether whose
 * tile is too small for the band ({@link OrbifoldGeometry#fitsBand}) gets the deepest band that fits.
 */
public record OrbifoldSettings(OrbifoldSize size, int bandChunks, int scale) {

    public static final OrbifoldSettings DEFAULT = new OrbifoldSettings(OrbifoldSize.DEFAULT, OrbifoldGeometry.DEFAULT_BAND_CHUNKS);

    /** A preset by id, as the network and the {@code size} field carry it. */
    public static final Codec<OrbifoldSize> SIZE = Codec.STRING.comapFlatMap(id -> OrbifoldSize.byId(id).map(DataResult::success)
        .orElseGet(() -> DataResult.error(() -> "Size must be one of " + OrbifoldSize.IDS + ": " + id)), OrbifoldSize::id);

    private static final StreamCodec<ByteBuf, OrbifoldSize> SIZE_STREAM = ByteBufCodecs.STRING_UTF8.map(
        id -> OrbifoldSize.byId(id).orElseThrow(() -> new IllegalArgumentException("Unknown orbifold size " + id)), OrbifoldSize::id);

    public static final StreamCodec<ByteBuf, OrbifoldSettings> STREAM_CODEC = StreamCodec.composite(
        SIZE_STREAM, OrbifoldSettings::size,
        ByteBufCodecs.VAR_INT, OrbifoldSettings::bandChunks,
        ByteBufCodecs.VAR_INT, OrbifoldSettings::scale,
        OrbifoldSettings::new);

    /** The scales a generator can have: the overworld's and the Nether's. */
    private static final Codec<Integer> SCALE = Codec.INT.validate(s -> s == 1 || s == OrbifoldGeometry.NETHER_SCALE
        ? DataResult.success(s) : DataResult.error(() -> "Scale must be 1 or " + OrbifoldGeometry.NETHER_SCALE + ": " + s));

    private static final Codec<Integer> SIZE_FACTOR = Codec.INT.validate(k -> OrbifoldSize.SIZE_FACTORS.contains(k)
        ? DataResult.success(k) : DataResult.error(() -> "Size factor must be one of " + OrbifoldSize.SIZE_FACTORS + ": " + k));

    public OrbifoldSettings(OrbifoldSize size, int bandChunks) {
        this(size, bandChunks, 1);
    }

    public OrbifoldSettings {
        if (scale != 1 && scale != OrbifoldGeometry.NETHER_SCALE) throw new IllegalArgumentException("Scale must be 1 or 8: " + scale);
        if (!OrbifoldSize.PRESETS.contains(size)) {
            throw new IllegalArgumentException("Size must be one of " + OrbifoldSize.IDS + ": " + size);
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
            SIZE.optionalFieldOf("size").forGetter(s -> s.size.sizeFactor() == 0 ? Optional.of(s.size) : Optional.empty()),
            SIZE_FACTOR.optionalFieldOf("size_factor").forGetter(s -> s.size.sizeFactor() != 0 ? Optional.of(s.size.sizeFactor()) : Optional.empty()),
            Codec.intRange(OrbifoldGeometry.MIN_BAND_CHUNKS, OrbifoldGeometry.MAX_BAND_CHUNKS).optionalFieldOf("band_chunks")
                .forGetter(s -> Optional.of(s.bandChunks)),
            SCALE.optionalFieldOf("scale").forGetter(s -> s.scale == 1 ? Optional.empty() : Optional.of(s.scale))
        ).apply(instance, (size, sizeFactor, bandChunks, scale) -> {
            OrbifoldSettings fallback = defaults.get();
            OrbifoldSize chosen = size.or(() -> sizeFactor.flatMap(OrbifoldSize::bySizeFactor)).orElse(fallback.size);
            return new OrbifoldSettings(chosen, bandChunks.orElse(fallback.bandChunks), scale.orElse(1));
        }));
    }

    public OrbifoldSettings withSize(OrbifoldSize size) {
        return new OrbifoldSettings(size, this.bandChunks, this.scale);
    }

    public OrbifoldSettings withScale(int scale) {
        return new OrbifoldSettings(this.size, this.bandChunks, scale);
    }

    /** The band this geometry gets: the setting, or at a scale whose tile is too small for it, the deepest that fits. */
    public int effectiveBandChunks() {
        int band = this.bandChunks;
        while (band > OrbifoldGeometry.MIN_BAND_CHUNKS && !OrbifoldGeometry.fitsBand(this.size, band, this.scale)) band--;
        return band;
    }

    public OrbifoldGeometry geometry() {
        return new OrbifoldGeometry(this.size, this.effectiveBandChunks(), this.scale);
    }

    /** The settings a geometry was made from (its band as it is, so they make the same geometry). */
    public static OrbifoldSettings of(OrbifoldGeometry geometry) {
        return new OrbifoldSettings(geometry.size, geometry.bandChunks, geometry.scale);
    }
}
