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
            .then(Commands.literal("info").executes(CubeCommand::info))
            .then(Commands.literal("scan").executes(CubeCommand::scan)));
    }

    /** Debug: where the loaded chunks hold waterlogged edge air, per face, and whether it sits on the barrier. */
    private static int scan(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        net.minecraft.server.level.ServerLevel level = source.getLevel();
        CubeGeometry geometry = Cube.of(level);
        if (geometry == null) return 0;
        java.util.Map<String, int[]> found = new java.util.TreeMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (net.minecraft.server.level.ChunkHolder holder : ((g_mungus.alpha_omega.mixin.server.ChunkMapAccessor) level.getChunkSource().chunkMap).alpha_omega$visibleChunks()) {
            net.minecraft.world.level.chunk.LevelChunk chunk = holder.getTickingChunk();
            if (chunk == null) continue;
            CubeFace face = geometry.faceAtChunk(chunk.getPos().x, chunk.getPos().z);
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                net.minecraft.world.level.chunk.LevelChunkSection section = chunk.getSection(i);
                if (!section.maybeHas(state -> state.is(g_mungus.alpha_omega.block.CubeBlocks.EDGE_AIR.get()) || state.is(net.minecraft.world.level.block.Blocks.WATER))) continue;
                int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    net.minecraft.world.level.block.state.BlockState state = section.getBlockState(x, y, z);
                    pos.set(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                    String key;
                    if (state.is(g_mungus.alpha_omega.block.CubeBlocks.EDGE_AIR.get()) && state.getValue(g_mungus.alpha_omega.block.EdgeAirBlock.WATERLOGGED)) {
                        boolean onBarrier = face != null && geometry.isBarrier(face, pos.getX(), pos.getY(), pos.getZ());
                        key = "waterlogged edge air " + face + (onBarrier ? " barrier" : " NOT barrier");
                    } else if (state.is(net.minecraft.world.level.block.Blocks.WATER) && face != null
                        && Math.max(Math.abs(pos.getX() - geometry.centerX(face) + 0.5), Math.abs(pos.getZ() - geometry.centerZ() + 0.5)) > geometry.radius) {
                        key = "water past the base square " + face + (state.getFluidState().isSource() ? " source" : " flowing");
                    } else {
                        continue;
                    }
                    int[] box = found.computeIfAbsent(key, k -> new int[] {0, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE});
                    box[0]++;
                    box[1] = Math.min(box[1], pos.getX()); box[2] = Math.min(box[2], pos.getY()); box[3] = Math.min(box[3], pos.getZ());
                    box[4] = Math.max(box[4], pos.getX()); box[5] = Math.max(box[5], pos.getY()); box[6] = Math.max(box[6], pos.getZ());
                }
            }
        }
        found.forEach((key, box) -> source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s: %d in %d %d %d .. %d %d %d",
            key, box[0], box[1], box[2], box[3], box[4], box[5], box[6])), false));
        if (found.isEmpty()) source.sendSuccess(() -> Component.literal("no waterlogged edge air loaded"), false);
        return found.size();
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
