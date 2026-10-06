package g_mungus.alpha_omega.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.sky.PlanetProjection;
import g_mungus.alpha_omega.sky.ProjectionInverse;
import java.util.Locale;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /orbifold tp <N|F|E|W>}: to a cone point, a few blocks into the tile, on the surface. {@code /orbifold tp <lat>
 * <lon>}: to the tile position at that latitude and longitude (degrees; north and east positive), found by running
 * the projection backwards ({@link ProjectionInverse}), on the surface. Permission level 2; moves the command's entity.
 */
public final class OrbifoldTeleport {

    /** How far into the tile from a cone point, along each axis it borders, in blocks. */
    public static final int CONE_OFFSET = 4;

    private OrbifoldTeleport() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        LiteralArgumentBuilder<CommandSourceStack> tp = Commands.literal("tp").requires(source -> source.hasPermission(2));
        for (String name : new String[] {"N", "F", "E", "W"}) tp.then(Commands.literal(name).executes(context -> cone(context, name)));
        return tp.then(Commands.argument("latitude", DoubleArgumentType.doubleArg(-90.0, 90.0))
            .then(Commands.argument("longitude", DoubleArgumentType.doubleArg(-180.0, 180.0))
                .executes(context -> place(context, DoubleArgumentType.getDouble(context, "latitude"), DoubleArgumentType.getDouble(context, "longitude")))));
    }

    /** Where {@code /orbifold tp <name>} goes, as {@code {x, z}}: {@link #CONE_OFFSET} into the tile from the cone point. */
    public static double[] conePlace(OrbifoldGeometry geometry, String name) {
        for (OrbifoldGeometry.ConePoint cone : geometry.conePoints()) {
            if (!cone.name().equals(name)) continue;
            // F is at x = a/2, which is x = −a/2 in the tile: step in from the west edge there.
            double x = cone.x() >= geometry.maxX ? geometry.minX + CONE_OFFSET + 0.5 : cone.x() + 0.5;
            double z = cone.z() == geometry.northRow ? geometry.northRow + CONE_OFFSET + 0.5 : geometry.southRow - CONE_OFFSET - 0.5;
            return new double[] {x, z};
        }
        throw new IllegalArgumentException("No cone point " + name);
    }

    /**
     * Where {@code /orbifold tp <lat> <lon>} goes, as {@code {x, z, chord}}: the tile point at that latitude and
     * longitude in degrees, kept half a block inside the tile, and how far (as a chord of the unit sphere) its
     * projection is from the target.
     */
    public static double[] latLonPlace(OrbifoldGeometry geometry, double latitude, double longitude) {
        PlanetProjection.Projection projection = PlanetProjection.of(geometry);
        ProjectionInverse.Result found = ProjectionInverse.find(geometry, projection, Math.toRadians(latitude), longitude / 360.0);
        double x = Math.max(geometry.minX + 0.5, Math.min(geometry.maxX - 0.5, found.x()));
        double z = Math.max(geometry.northRow + 0.5, Math.min(geometry.southRow - 0.5, found.z()));
        return new double[] {x, z, found.chord()};
    }

    private static int cone(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        OrbifoldGeometry geometry = Orbifold.of(source.getLevel());
        if (geometry == null) return notOrbifold(source);
        double[] at = conePlace(geometry, name);
        Entity entity = source.getEntityOrException();
        Vec3 landed = teleport(source.getLevel(), entity, at[0], at[1]);
        source.sendSuccess(() -> Component.translatable("commands.alpha_omega.orbifold.tp.cone", entity.getDisplayName(), name,
            format(landed.x), format(landed.y), format(landed.z)), true);
        return 1;
    }

    private static int place(CommandContext<CommandSourceStack> context, double latitude, double longitude) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        OrbifoldGeometry geometry = Orbifold.of(source.getLevel());
        if (geometry == null) return notOrbifold(source);
        Entity entity = source.getEntityOrException();
        double[] at = latLonPlace(geometry, latitude, longitude);
        Vec3 landed = teleport(source.getLevel(), entity, at[0], at[1]);
        String where = String.format(Locale.ROOT, "%.3f°%s %.3f°%s", Math.abs(latitude), latitude >= 0 ? "N" : "S", Math.abs(longitude), longitude >= 0 ? "E" : "W");
        source.sendSuccess(() -> Component.translatable("commands.alpha_omega.orbifold.tp.place", entity.getDisplayName(), where,
            format(landed.x), format(landed.y), format(landed.z)), true);
        return 1;
    }

    static int notOrbifold(CommandSourceStack source) {
        source.sendFailure(Component.translatable("commands.alpha_omega.orbifold.not_orbifold"));
        return 0;
    }

    /** Moves an entity to {@code (x, z)}, standing on the surface there (generating the chunk if need be). */
    public static Vec3 teleport(ServerLevel level, Entity entity, double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        level.getChunk(bx >> 4, bz >> 4);
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        entity.teleportTo(level, x, y, z, Set.of(), entity.getYRot(), entity.getXRot());
        return new Vec3(x, y, z);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
