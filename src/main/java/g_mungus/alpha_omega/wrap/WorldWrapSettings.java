package g_mungus.alpha_omega.wrap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A world's wrapping, chosen when it is created and stored in its folder. The Overworld wraps at {@code period};
 * the Nether at {@code period / 8} so portals link one to one (§11); the End only if enabled; other dimensions
 * never (they may depend on absolute coordinates).
 */
public record WorldWrapSettings(int period, boolean nether, boolean end) {

    public static final WorldWrapSettings DISABLED = new WorldWrapSettings(0, false, false);
    /** Default: a multiple of 3072 (clouds) and 4096 (whole climate octaves); the Nether is then 1536 wide. */
    public static final int DEFAULT_PERIOD = 12288;
    public static final String FILE_NAME = "alpha_omega.json";

    public static final StreamCodec<ByteBuf, WorldWrapSettings> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, WorldWrapSettings::period,
        ByteBufCodecs.BOOL, WorldWrapSettings::nether,
        ByteBufCodecs.BOOL, WorldWrapSettings::end,
        WorldWrapSettings::new);

    public WorldWrapSettings {
        if (period != 0 && (period % 128 != 0)) {
            // The Nether's period (W / 8) must still be a whole number of chunks.
            throw new IllegalArgumentException("World period must be a multiple of 128: " + period);
        }
    }

    public boolean enabled() {
        return this.period > 0;
    }

    public int periodFor(ResourceKey<Level> dimension) {
        if (!this.enabled()) return 0;
        if (dimension == Level.OVERWORLD) return this.period;
        if (dimension == Level.NETHER) return this.nether ? this.period / 8 : 0;
        if (dimension == Level.END) return this.end ? this.period : 0;
        return 0;
    }

    public static WorldWrapSettings read(Path file) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        return new WorldWrapSettings(json.get("period").getAsInt(), json.get("nether").getAsBoolean(), json.get("end").getAsBoolean());
    }

    public void write(Path file) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("period", this.period);
        json.addProperty("nether", this.nether);
        json.addProperty("end", this.end);
        Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
    }
}
