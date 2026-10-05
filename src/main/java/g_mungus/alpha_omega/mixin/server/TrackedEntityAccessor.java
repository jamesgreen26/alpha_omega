package g_mungus.alpha_omega.mixin.server;

import java.util.Set;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Who an entity is sent to, and re-checking one player against it. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {

    @Accessor("entity")
    net.minecraft.world.entity.Entity alpha_omega$entity();

    @Accessor("seenBy")
    Set<ServerPlayerConnection> alpha_omega$seenBy();

    @Invoker("updatePlayer")
    void alpha_omega$updatePlayer(net.minecraft.server.level.ServerPlayer player);

}
