package g_mungus.alpha_omega.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /orbifold info}: the tile, its cone points, and for the cell the caller is in: whether it is tile, band or
 * skirt, its source and frame, its source's copy set, and how far past the nearest seam it is.
 */
public final class OrbifoldCommand {

    private OrbifoldCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("orbifold")
            .then(Commands.literal("info").executes(OrbifoldCommand::info)));
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        OrbifoldGeometry geometry = Orbifold.of(source.getLevel());
        if (geometry == null) {
            source.sendFailure(Component.literal("This dimension is not an orbifold"));
            return 0;
        }
        for (String line : lines(geometry, source.getPosition())) source.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    /** What {@code /orbifold info} says at a position. */
    public static List<String> lines(OrbifoldGeometry geometry, Vec3 pos) {
        BlockPos block = BlockPos.containing(pos);
        int x = block.getX(), z = block.getZ();
        String cones = geometry.conePoints().stream().map(c -> c.name() + " (" + c.x() + ", " + c.z() + ")").collect(Collectors.joining(", "));
        String region = geometry.isTile(x, z) ? "tile" : geometry.isBand(x, z) ? "band" : geometry.isSkirt(x, z) ? "skirt" : "outside the footprint";
        double depth = geometry.seamDepth(pos.x, pos.z);
        String seam = depth > 0 ? String.format(Locale.ROOT, "%.1f past the nearest seam", depth)
            : String.format(Locale.ROOT, "%.1f short of the nearest seam", -depth);
        String spawn = "spawn (" + geometry.spawnX + ", " + geometry.spawnZ + ")";
        if (!geometry.inFootprint(x, z)) {
            return List.of(geometry.toString(), "Cone points: " + cones + "; " + spawn, "Cell " + x + " " + z + ": " + region + ", " + seam);
        }
        OrbifoldGeometry.Cell canon = geometry.canon(x, z);
        String copies = geometry.copies(canon.x(), canon.z()).stream()
            .map(c -> "(" + c.x() + ", " + c.z() + ") by " + c.frame()).collect(Collectors.joining(", "));
        return List.of(
            geometry.toString(),
            "Cone points: " + cones + "; " + spawn,
            String.format(Locale.ROOT, "Cell %d %d: %s, %s; frame %s", x, z, region, seam, canon.frame()),
            String.format(Locale.ROOT, "Source (%d, %d); its copies: %s", canon.x(), canon.z(), copies.isEmpty() ? "none" : copies));
    }
}
