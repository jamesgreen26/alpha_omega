package g_mungus.alpha_omega.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import g_mungus.alpha_omega.island.Invariants;
import g_mungus.alpha_omega.island.IslandGraph;
import g_mungus.alpha_omega.island.IslandManager;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.Wraps;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Debug tooling (§13.2): {@code /wrap info}, {@code /wrap islands}, {@code /wrap check}, and
 * {@code /wrap shift <island> <dx> <dz>} (shift an island by whole laps).
 */
public final class WrapCommand {

    private WrapCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wrap")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("info").executes(WrapCommand::info))
            .then(Commands.literal("islands").executes(WrapCommand::islands))
            .then(Commands.literal("check").executes(WrapCommand::check))
            .then(Commands.literal("shift")
                .then(Commands.argument("island", IntegerArgumentType.integer(1))
                    .then(Commands.argument("dx", IntegerArgumentType.integer())
                        .then(Commands.argument("dz", IntegerArgumentType.integer()).executes(WrapCommand::shift))))));
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        Wrap wrap = Wrap.of(level);
        if (!wrap.enabled()) {
            context.getSource().sendSuccess(() -> Component.literal(level.dimension().location() + " does not wrap"), false);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(level.dimension().location() + " wraps every " + wrap.period
            + " blocks (" + wrap.chunkPeriod + " chunks), canonical x and z in [" + wrap.minBlock + ", " + (wrap.minBlock + wrap.period)
            + "); world settings " + Wraps.settings()), false);
        return wrap.period;
    }

    private static int islands(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        IslandGraph graph = IslandManager.of(level).graph();
        if (graph == null) return info(context);
        List<IslandGraph.Island> islands = graph.islands().stream().sorted(Comparator.comparingInt(island -> -island.size())).toList();
        context.getSource().sendSuccess(() -> Component.literal(islands.size() + " islands, " + graph.chunkCount() + " chunks, cuts x" + graph.cuts(true) + " z" + graph.cuts(false)), false);
        for (IslandGraph.Island island : islands.subList(0, Math.min(10, islands.size()))) {
            long key = island.chunks().iterator().nextLong();
            long laps = graph.laps(IslandGraph.keyX(key), IslandGraph.keyZ(key));
            long players = level.players().stream()
                .filter(player -> graph.islandOf(Wrap.of(level).canonChunk(player.chunkPosition().x), Wrap.of(level).canonChunk(player.chunkPosition().z)) == island.id)
                .count();
            context.getSource().sendSuccess(() -> Component.literal(String.format("  #%d: %d chunks, extent %dx%d, lap %d,%d, %d players%s",
                island.id, island.size(), island.extentX(), island.extentZ(), IslandGraph.lapX(laps), IslandGraph.lapZ(laps), players,
                graph.loops().contains(island.id) ? ", LOOPING" : "")), false);
        }
        return islands.size();
    }

    private static int check(CommandContext<CommandSourceStack> context) {
        List<String> violations = Invariants.check(context.getSource().getLevel());
        if (violations.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("All invariants hold"), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal(violations.size() + " invariant violations"));
        violations.stream().limit(10).forEach(violation -> context.getSource().sendFailure(Component.literal("  " + violation)));
        return 0;
    }

    private static int shift(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        IslandManager manager = IslandManager.of(level);
        int island = IntegerArgumentType.getInteger(context, "island");
        if (manager.graph() == null || manager.graph().island(island) == null) {
            context.getSource().sendFailure(Component.literal("No island #" + island));
            return 0;
        }
        manager.shift(island, IntegerArgumentType.getInteger(context, "dx"), IntegerArgumentType.getInteger(context, "dz"));
        context.getSource().sendSuccess(() -> Component.literal("Shifted island #" + island), true);
        return 1;
    }
}
