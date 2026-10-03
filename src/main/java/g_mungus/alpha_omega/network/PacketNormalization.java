package g_mungus.alpha_omega.network;

import java.util.function.BiConsumer;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * R6: positions may cross the wire as any image. The receiver rewrites each incoming position, in place, to the
 * nearest image of a local reference before any vanilla handler logic reads it. Mutating in place is safe even if
 * a packet instance is shared, because every image is equally valid to every receiver.
 */
public final class PacketNormalization {

    /** Installed by the client entry point; the dedicated server never sees client listeners. */
    private static BiConsumer<Packet<?>, PacketListener> clientNormalizer = (packet, listener) -> {};

    private PacketNormalization() {
    }

    public static void setClientNormalizer(BiConsumer<Packet<?>, PacketListener> normalizer) {
        clientNormalizer = normalizer;
    }

    public static void normalize(Packet<?> packet, PacketListener listener) {
        if (listener instanceof ServerGamePacketListenerImpl server) {
            ServerboundNormalizer.normalize(packet, server.player);
        } else {
            clientNormalizer.accept(packet, listener);
        }
    }
}
