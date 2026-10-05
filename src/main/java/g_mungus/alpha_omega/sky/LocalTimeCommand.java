package g_mungus.alpha_omega.sky;

import com.mojang.brigadier.context.CommandContext;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.sky.PlanetProjection.Position;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /orbifold time}: where the caller is on the planet, the local and global clocks, the sun, and how fast the sky
 * turns there.
 */
public final class LocalTimeCommand {

    private LocalTimeCommand() {
    }

    public static int time(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!LocalSky.local(source.getLevel())) {
            source.sendFailure(Component.literal("This dimension has vanilla's sun"));
            return 0;
        }
        for (String line : lines(source.getLevel(), source.getPosition())) source.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    /** What {@code /orbifold time} says at a position of an orbifold world's overworld. */
    public static List<String> lines(Level level, Vec3 pos) {
        OrbifoldGeometry geometry = Orbifold.of(level);
        Position position = PlanetProjection.of(geometry).project(pos.x, pos.z);
        LocalSky.Sample sun = LocalSky.sample(level.getDayTime(), position);
        double latitude = Math.toDegrees(position.latitude());
        double longitude = 360.0 * position.longitude();
        double speed = Math.toDegrees(HexOrbifoldProjection.of(geometry).skySpeed(pos.x, pos.z)) * 100.0;
        return List.of(
            String.format(Locale.ROOT, "Planet: %.2f°%s %.2f°%s; world north is %.1f° east of geographic north",
                Math.abs(latitude), latitude >= 0 ? "N" : "S", Math.abs(longitude), longitude >= 0 ? "E" : "W", Math.toDegrees(position.heading())),
            String.format(Locale.ROOT, "Local time %s day %d (%+.2f h); global %s day %d", clock(sun.clock()), sun.day(),
                24.0 * position.longitude(), clock(level.getDayTime()), Math.floorDiv(level.getDayTime(), 24000L)),
            String.format(Locale.ROOT, "Sun: altitude %.1f°, azimuth %.0f° from world north; %s", Math.toDegrees(sun.altitude()),
                Math.toDegrees(sun.azimuth()), LocalSky.skyDarken(sun.sunY(), 0.0F, 0.0F) < 4 ? "day" : "night"),
            String.format(Locale.ROOT, "Sky turns %.2f° per 100 blocks here", speed));
    }

    /** A clock value as hh:mm, with day time 0 at 06:00 as vanilla's. */
    private static String clock(double ticks) {
        long minutes = (Math.floorMod((long) Math.floor(ticks), 24000L) + 6000L) % 24000L * 60L / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
