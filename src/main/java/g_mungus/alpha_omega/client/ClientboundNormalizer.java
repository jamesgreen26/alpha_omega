package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.mixin.network.AddEntityPacketAccessor;
import g_mungus.alpha_omega.mixin.network.AddExperienceOrbPacketAccessor;
import g_mungus.alpha_omega.mixin.network.BlockDestructionPacketAccessor;
import g_mungus.alpha_omega.mixin.network.BlockEntityDataPacketAccessor;
import g_mungus.alpha_omega.mixin.network.BlockEventPacketAccessor;
import g_mungus.alpha_omega.mixin.network.BlockUpdatePacketAccessor;
import g_mungus.alpha_omega.mixin.network.ChunksBiomesPacketAccessor;
import g_mungus.alpha_omega.mixin.network.ClientboundMoveVehiclePacketAccessor;
import g_mungus.alpha_omega.mixin.network.ForgetLevelChunkPacketAccessor;
import g_mungus.alpha_omega.mixin.network.LevelChunkWithLightPacketAccessor;
import g_mungus.alpha_omega.mixin.network.LevelEventPacketAccessor;
import g_mungus.alpha_omega.mixin.network.LevelParticlesPacketAccessor;
import g_mungus.alpha_omega.mixin.network.LightUpdatePacketAccessor;
import g_mungus.alpha_omega.mixin.network.PlayerPositionPacketAccessor;
import g_mungus.alpha_omega.mixin.network.SectionBlocksUpdatePacketAccessor;
import g_mungus.alpha_omega.mixin.network.SetChunkCacheCenterPacketAccessor;
import g_mungus.alpha_omega.mixin.network.SoundPacketAccessor;
import g_mungus.alpha_omega.mixin.network.TeleportEntityPacketAccessor;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Normalizes clientbound positions into the client's frame (design doc §8.2). Chunk-level packets are
 * normalized against the chunk cache's view center, which is exactly what {@code ClientChunkCache} range-checks
 * against; everything else against the camera, or for existing entities their current client position.
 */
public final class ClientboundNormalizer {

    private ClientboundNormalizer() {
    }

    public static void normalize(Packet<?> packet, PacketListener listener) {
        if (!(listener instanceof ClientPacketListener)) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        ViewCenter center = (ViewCenter) level.getChunkSource();
        int cx = center.alpha_omega$viewCenterX();
        int cz = center.alpha_omega$viewCenterZ();

        switch (packet) {
            // ---- chunks ----
            case ClientboundLevelChunkWithLightPacket p -> {
                LevelChunkWithLightPacketAccessor a = (LevelChunkWithLightPacketAccessor) p;
                a.alpha_omega$setX(Wrap.nearestChunk(p.getX(), cx));
                a.alpha_omega$setZ(Wrap.nearestChunk(p.getZ(), cz));
            }
            case ClientboundLightUpdatePacket p -> {
                LightUpdatePacketAccessor a = (LightUpdatePacketAccessor) p;
                a.alpha_omega$setX(Wrap.nearestChunk(p.getX(), cx));
                a.alpha_omega$setZ(Wrap.nearestChunk(p.getZ(), cz));
            }
            case ClientboundForgetLevelChunkPacket p ->
                ((ForgetLevelChunkPacketAccessor) (Object) p).alpha_omega$setPos(Wrap.nearest(p.pos(), cx, cz));
            case ClientboundChunksBiomesPacket p ->
                ((ChunksBiomesPacketAccessor) (Object) p).alpha_omega$setChunkBiomeData(p.chunkBiomeData().stream()
                    .map(d -> new ClientboundChunksBiomesPacket.ChunkBiomeData(Wrap.nearest(d.pos(), cx, cz), d.buffer()))
                    .toList());
            case ClientboundSetChunkCacheCenterPacket p -> {
                // The center follows the player, so it is resolved against the player's chunk.
                if (mc.player == null) return;
                ChunkPos player = mc.player.chunkPosition();
                SetChunkCacheCenterPacketAccessor a = (SetChunkCacheCenterPacketAccessor) p;
                a.alpha_omega$setX(Wrap.nearestChunk(p.getX(), player.x));
                a.alpha_omega$setZ(Wrap.nearestChunk(p.getZ(), player.z));
            }

            // ---- blocks ----
            case ClientboundBlockUpdatePacket p ->
                ((BlockUpdatePacketAccessor) p).alpha_omega$setPos(Wrap.nearest(p.getPos(), reference(mc, cx, cz)));
            case ClientboundSectionBlocksUpdatePacket p -> {
                SectionBlocksUpdatePacketAccessor a = (SectionBlocksUpdatePacketAccessor) p;
                SectionPos s = a.alpha_omega$getSectionPos();
                a.alpha_omega$setSectionPos(
                    SectionPos.of(Wrap.nearestChunk(s.x(), cx), s.y(), Wrap.nearestChunk(s.z(), cz)));
            }
            case ClientboundBlockEntityDataPacket p ->
                ((BlockEntityDataPacketAccessor) p).alpha_omega$setPos(Wrap.nearest(p.getPos(), reference(mc, cx, cz)));
            case ClientboundBlockEventPacket p ->
                ((BlockEventPacketAccessor) p).alpha_omega$setPos(Wrap.nearest(p.getPos(), reference(mc, cx, cz)));
            case ClientboundBlockDestructionPacket p ->
                ((BlockDestructionPacketAccessor) p).alpha_omega$setPos(Wrap.nearest(p.getPos(), reference(mc, cx, cz)));

            // ---- entities ----
            case ClientboundAddEntityPacket p -> {
                Vec3 ref = reference(mc, cx, cz);
                AddEntityPacketAccessor a = (AddEntityPacketAccessor) p;
                a.alpha_omega$setX(Wrap.nearest(p.getX(), ref.x));
                a.alpha_omega$setZ(Wrap.nearest(p.getZ(), ref.z));
            }
            case ClientboundAddExperienceOrbPacket p -> {
                Vec3 ref = reference(mc, cx, cz);
                AddExperienceOrbPacketAccessor a = (AddExperienceOrbPacketAccessor) p;
                a.alpha_omega$setX(Wrap.nearest(p.getX(), ref.x));
                a.alpha_omega$setZ(Wrap.nearest(p.getZ(), ref.z));
            }
            case ClientboundTeleportEntityPacket p -> {
                // Existing entity: stay continuous with where the client already has it.
                Entity entity = level.getEntity(p.getId());
                Vec3 ref = entity != null ? entity.position() : reference(mc, cx, cz);
                TeleportEntityPacketAccessor a = (TeleportEntityPacketAccessor) p;
                a.alpha_omega$setX(Wrap.nearest(p.getX(), ref.x));
                a.alpha_omega$setZ(Wrap.nearest(p.getZ(), ref.z));
            }
            case ClientboundMoveVehiclePacket p -> {
                if (mc.player == null) return;
                Vec3 ref = mc.player.getRootVehicle().position();
                ClientboundMoveVehiclePacketAccessor a = (ClientboundMoveVehiclePacketAccessor) p;
                a.alpha_omega$setX(Wrap.nearest(p.getX(), ref.x));
                a.alpha_omega$setZ(Wrap.nearest(p.getZ(), ref.z));
            }
            case ClientboundPlayerPositionPacket p -> {
                // A server-side shift by k*W normalizes to a zero-distance update.
                if (mc.player == null) return;
                PlayerPositionPacketAccessor a = (PlayerPositionPacketAccessor) p;
                if (!p.getRelativeArguments().contains(RelativeMovement.X)) a.alpha_omega$setX(Wrap.nearest(p.getX(), mc.player.getX()));
                if (!p.getRelativeArguments().contains(RelativeMovement.Z)) a.alpha_omega$setZ(Wrap.nearest(p.getZ(), mc.player.getZ()));
            }

            // ---- effects ----
            case ClientboundSoundPacket p -> {
                // Positions are fixed point (x8).
                Vec3 ref = reference(mc, cx, cz);
                SoundPacketAccessor a = (SoundPacketAccessor) p;
                a.alpha_omega$setX(WrapMath.nearestImage((int) (p.getX() * 8), (int) (ref.x * 8), Wrap.PERIOD * 8));
                a.alpha_omega$setZ(WrapMath.nearestImage((int) (p.getZ() * 8), (int) (ref.z * 8), Wrap.PERIOD * 8));
            }
            case ClientboundLevelParticlesPacket p -> {
                Vec3 ref = reference(mc, cx, cz);
                LevelParticlesPacketAccessor a = (LevelParticlesPacketAccessor) p;
                a.alpha_omega$setX(Wrap.nearest(p.getX(), ref.x));
                a.alpha_omega$setZ(Wrap.nearest(p.getZ(), ref.z));
            }
            case ClientboundLevelEventPacket p ->
                ((LevelEventPacketAccessor) p).alpha_omega$setPos(Wrap.nearest(p.getPos(), reference(mc, cx, cz)));
            default -> {
            }
        }
    }

    /** The camera position, or the chunk cache's view center before there is a camera. */
    private static Vec3 reference(Minecraft mc, int viewCenterX, int viewCenterZ) {
        Entity camera = mc.getCameraEntity();
        if (camera != null) return camera.position();
        return new Vec3(SectionPos.sectionToBlockCoord(viewCenterX, 8), 0, SectionPos.sectionToBlockCoord(viewCenterZ, 8));
    }
}
