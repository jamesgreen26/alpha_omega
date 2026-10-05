package g_mungus.alpha_omega.network;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.cube.Cube;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Tells a connecting client the cube's shape before it receives any chunk. */
public record CubeConfigurationTask(ServerConfigurationPacketListener listener) implements ICustomConfigurationTask {

    public static final Type TYPE = new Type(ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "cube"));

    @Override
    public void run(Consumer<CustomPacketPayload> sender) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        sender.accept(CubePayload.of(server == null ? null : Cube.of(server.overworld())));
        this.listener.finishCurrentTask(TYPE);
    }

    @Override
    public Type type() {
        return TYPE;
    }
}
