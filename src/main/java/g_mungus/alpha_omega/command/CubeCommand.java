package g_mungus.alpha_omega.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** {@code /cube info}: the face under the caller and where on it they are. */
public final class CubeCommand {

    private CubeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cube")
            .then(Commands.literal("info").executes(CubeCommand::info)));
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CubeGeometry geometry = Cube.of(source.getLevel());
        if (geometry == null) {
            source.sendFailure(Component.literal("This dimension is not a cube"));
            return 0;
        }
        Vec3 pos = source.getPosition();
        CubeFace face = geometry.faceAt(pos.x, pos.z);
        source.sendSuccess(() -> Component.literal(geometry.toString()), false);
        if (face == null) {
            source.sendSuccess(() -> Component.literal("Not over any face"), false);
            return 1;
        }
        BlockPos block = BlockPos.containing(pos);
        int owner = geometry.cellOwner(face, block.getX(), block.getY(), block.getZ());
        double[] cube = geometry.toCube(face, pos.x, pos.y, pos.z);
        String cell = owner == CubeGeometry.BARRIER ? "barrier" : owner == face.slot() ? "owned" : "owned by " + CubeFace.bySlot(owner);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Face %s: u %.1f, h %.1f, v %.1f (%s); cube %.1f %.1f %.1f",
            face, pos.x - geometry.centerX(face), pos.y - geometry.planeY, pos.z - geometry.centerZ(), cell, cube[0], cube[1], cube[2])), false);
        return 1;
    }
}
