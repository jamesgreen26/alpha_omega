package g_mungus.alpha_omega.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Vanilla's {@link GameTestHelper#makeMockServerPlayerInLevel()}, with a connection that accepts every mod's
 * payloads, so other mods (Sable) can send to the player as it joins.
 */
final class TestPlayers {

    private TestPlayers() {
    }

    static ServerPlayer mock(GameTestHelper helper) {
        return mock(helper, UUID.randomUUID());
    }

    /** A mock player with a given id, which loads any player data saved under it, as a returning player does. */
    static ServerPlayer mock(GameTestHelper helper, UUID id) {
        return mock(helper, id, true);
    }

    /** A mock player in survival: mobs target it. */
    static ServerPlayer survival(GameTestHelper helper) {
        ServerPlayer player = mock(helper, UUID.randomUUID(), false);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return player;
    }

    private static ServerPlayer mock(GameTestHelper helper, UUID id, boolean creative) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return creative;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    /**
     * Every tick, what a connected client's player does: acknowledges chunk batches (so chunks keep being sent) and
     * moves through the chunk map (which is when vanilla re-checks which entities it is sent).
     */
    static void receiveChunks(GameTestHelper helper, ServerPlayer player) {
        helper.onEachTick(() -> {
            player.connection.chunkSender.onChunkBatchReceivedByClient(64.0F);
            player.serverLevel().getChunkSource().move(player);
        });
    }
}
