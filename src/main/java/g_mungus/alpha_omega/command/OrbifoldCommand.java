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
 * skirt, its source and frame, its source's copy set, and how far past the nearest seam it is; and who owns the targeted
 * cell. {@code /orbifold scan}: counters, and the claims held in loaded chunks.
 */
public final class OrbifoldCommand {

    private OrbifoldCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("orbifold")
            .then(Commands.literal("info").executes(OrbifoldCommand::info))
            .then(Commands.literal("time").executes(g_mungus.alpha_omega.sky.LocalTimeCommand::time))
            .then(Commands.literal("scan").executes(OrbifoldCommand::scan))
            .then(Commands.literal("check").executes(context -> check(context, 4))
                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 32))
                    .executes(context -> check(context, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "radius"))))));
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        OrbifoldGeometry geometry = Orbifold.of(source.getLevel());
        if (geometry == null) {
            source.sendFailure(Component.literal("This dimension is not an orbifold"));
            return 0;
        }
        for (String line : lines(geometry, source.getPosition())) source.sendSuccess(() -> Component.literal(line), false);
        BlockPos target = BlockPos.containing(source.getPosition());
        if (source.getEntity() != null && source.getEntity().pick(20.0, 0.0F, false) instanceof net.minecraft.world.phys.BlockHitResult hit
            && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            target = hit.getBlockPos();
        }
        String owner = ownerLine(source.getLevel(), target);
        source.sendSuccess(() -> Component.literal(owner), false);
        return 1;
    }

    /** Who owns the cell at {@code pos} (RS §3.1, §3.3): this copy or another, nominally or by a claim, and where its block entity is. */
    public static String ownerLine(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        String cell = "Target " + pos.toShortString() + " " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        net.minecraft.world.level.chunk.LevelChunk chunk = g_mungus.alpha_omega.band.Band.linkedChunk(level, pos);
        if (chunk == null) return cell + ": no copies loaded; owned here";
        OrbifoldGeometry geometry = Orbifold.of(level);
        boolean here = g_mungus.alpha_omega.band.Ownership.isOwner(chunk, pos);
        BlockPos ownerPos = pos;
        if (!here) {
            var link = g_mungus.alpha_omega.band.Ownership.owner(level, chunk, pos);
            if (link == null) return cell + ": owner unresolved (masks disagree; see /orbifold check)";
            ownerPos = link.map(pos);
        }
        boolean nominal = geometry.isTile(ownerPos.getX(), ownerPos.getZ());
        var ownerChunk = g_mungus.alpha_omega.band.Band.loadedChunk(level, ownerPos);
        boolean blockEntity = ownerChunk != null && ownerChunk.getBlockEntities().containsKey(ownerPos);
        return String.format(Locale.ROOT, "%s: owned by %s at %s (%s), %s%s", cell, here ? "this copy" : "the copy", ownerPos.toShortString(),
            nominal ? "tile" : "band, depth " + geometry.cellDepth(ownerPos.getX(), ownerPos.getZ()),
            nominal ? "nominal" : "claimed", blockEntity ? "; its block entity is there" : "");
    }

    /** {@code /orbifold scan}: the band spike's counters. */
    private static int scan(CommandContext<CommandSourceStack> context) {
        for (String line : g_mungus.alpha_omega.band.BandCounters.lines()) context.getSource().sendSuccess(() -> Component.literal(line), false);
        String claimed = g_mungus.alpha_omega.band.BandCounters.claimedCells(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal(claimed), false);
        for (String line : g_mungus.alpha_omega.transfer.TransferCounters.lines()) context.getSource().sendSuccess(() -> Component.literal(line), false);
        for (String line : g_mungus.alpha_omega.bridge.BridgeCounters.lines()) context.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    /** {@code /orbifold check [radius]}: compares loaded band chunks round the caller with their sources. */
    private static int check(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();
        var result = g_mungus.alpha_omega.band.BandCheck.check(source.getLevel(), new net.minecraft.world.level.ChunkPos(BlockPos.containing(source.getPosition())), radius);
        source.sendSuccess(() -> Component.literal(result.toString()), false);
        return result.clean() ? 1 : 0;
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
