package g_mungus.alpha_omega.cube;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * A cube world's shape, chosen when the world is created and kept in its overworld generator (so in
 * {@code level.dat}). {@code faceChunks} is the width of a face in chunks; the sun turns about {@code sunAxis}.
 */
public record CubeSettings(int faceChunks, SunAxis sunAxis) {

    public static final int MIN_FACE_CHUNKS = 2;
    public static final int MAX_FACE_CHUNKS = 256;
    public static final CubeSettings DEFAULT = new CubeSettings(16, SunAxis.DIAGONAL);

    public static final StreamCodec<ByteBuf, CubeSettings> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, CubeSettings::faceChunks,
        ByteBufCodecs.idMapper(i -> SunAxis.values()[i], SunAxis::ordinal), CubeSettings::sunAxis,
        CubeSettings::new);

    public CubeSettings {
        if (faceChunks < MIN_FACE_CHUNKS || faceChunks > MAX_FACE_CHUNKS) {
            throw new IllegalArgumentException("Face width must be " + MIN_FACE_CHUNKS + " to " + MAX_FACE_CHUNKS + " chunks: " + faceChunks);
        }
    }

    /**
     * Fields of a generator's codec. Missing fields (a world preset that leaves them out) take the defaults from
     * {@code defaults}, read when the codec decodes; they are written out explicitly, so a saved world keeps them.
     */
    public static MapCodec<CubeSettings> fieldsCodec(Supplier<CubeSettings> defaults) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(MIN_FACE_CHUNKS, MAX_FACE_CHUNKS).optionalFieldOf("face_chunks").forGetter(s -> Optional.of(s.faceChunks)),
            SunAxis.CODEC.optionalFieldOf("sun_axis").forGetter(s -> Optional.of(s.sunAxis))
        ).apply(instance, (faceChunks, sunAxis) -> {
            CubeSettings fallback = defaults.get();
            return new CubeSettings(faceChunks.orElse(fallback.faceChunks), sunAxis.orElse(fallback.sunAxis));
        }));
    }

    public CubeSettings withFaceChunks(int faceChunks) {
        return new CubeSettings(faceChunks, this.sunAxis);
    }

    public CubeSettings withSunAxis(SunAxis sunAxis) {
        return new CubeSettings(this.faceChunks, sunAxis);
    }

    /** The axis the sun turns about, in cube space. */
    public enum SunAxis implements StringRepresentable {
        /** Through two opposite corners: every face has a 12 hour day, noons 4 hours apart. */
        DIAGONAL("diagonal", 1, 1, 1),
        /** Through the centres of the north and south faces: the four others are equatorial, the poles in twilight. */
        POLAR("polar", 0, 0, 1);

        public static final Codec<SunAxis> CODEC = StringRepresentable.fromEnum(SunAxis::values);

        private final String name;
        /** Unit vector. */
        public final double x;
        public final double y;
        public final double z;

        SunAxis(String name, double x, double y, double z) {
            this.name = name;
            double length = Math.sqrt(x * x + y * y + z * z);
            this.x = x / length;
            this.y = y / length;
            this.z = z / length;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
