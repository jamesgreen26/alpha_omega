package g_mungus.alpha_omega.mixin.worldgen.structure;

import g_mungus.alpha_omega.mixin.server.StructureManagerAccessor;
import g_mungus.alpha_omega.wrap.Wrap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Terrain adaptation around structures (villages flatten, ancient cities bury). Collects the same pieces and
 * junctions as vanilla (including NeoForge's {@link PieceBeardifierModifier}), but first moves each structure to
 * the image nearest the chunk, so terrain on both sides of the seam adapts to a structure that straddles it.
 */
@Mixin(Beardifier.class)
abstract class BeardifierMixin {

    @Inject(method = "forStructuresInChunk", at = @At("HEAD"), cancellable = true)
    private static void alpha_omega$forStructuresInChunk(StructureManager structureManager, ChunkPos chunkPos, CallbackInfoReturnable<Beardifier> cir) {
        Wrap wrap = Wrap.of(((StructureManagerAccessor) structureManager).alpha_omega$getLevel());
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        ObjectList<Beardifier.Rigid> rigids = new ObjectArrayList<>(10);
        ObjectList<JigsawJunction> junctions = new ObjectArrayList<>(32);
        structureManager.startsForStructure(chunkPos, structure -> structure.terrainAdaptation() != TerrainAdjustment.NONE).forEach(start -> {
            TerrainAdjustment adjustment = start.getStructure().terrainAdaptation();
            int dx = wrap.lapOffset(start.getBoundingBox().getCenter().getX(), chunkPos.getMiddleBlockX());
            int dz = wrap.lapOffset(start.getBoundingBox().getCenter().getZ(), chunkPos.getMiddleBlockZ());
            // The chunk, expressed in the structure's own frame, for the vanilla proximity test.
            ChunkPos local = new ChunkPos(chunkPos.x - (dx >> 4), chunkPos.z - (dz >> 4));

            for (StructurePiece piece : start.getPieces()) {
                if (!piece.isCloseToChunk(local, 12)) continue;
                if (piece instanceof PieceBeardifierModifier modifier) {
                    if (modifier.getTerrainAdjustment() != TerrainAdjustment.NONE) {
                        rigids.add(new Beardifier.Rigid(alpha_omega$move(modifier.getBeardifierBox(), dx, dz), modifier.getTerrainAdjustment(), modifier.getGroundLevelDelta()));
                    }
                } else if (piece instanceof PoolElementStructurePiece poolPiece) {
                    if (poolPiece.getElement().getProjection() == StructureTemplatePool.Projection.RIGID) {
                        rigids.add(new Beardifier.Rigid(alpha_omega$move(poolPiece.getBoundingBox(), dx, dz), adjustment, poolPiece.getGroundLevelDelta()));
                    }
                    for (JigsawJunction junction : poolPiece.getJunctions()) {
                        int x = junction.getSourceX() + dx;
                        int z = junction.getSourceZ() + dz;
                        if (x > minX - 12 && z > minZ - 12 && x < minX + 15 + 12 && z < minZ + 15 + 12) {
                            junctions.add(dx == 0 && dz == 0 ? junction
                                : new JigsawJunction(x, junction.getSourceGroundY(), z, junction.getDeltaY(), junction.getDestProjection()));
                        }
                    }
                } else {
                    rigids.add(new Beardifier.Rigid(alpha_omega$move(piece.getBoundingBox(), dx, dz), adjustment, 0));
                }
            }
        });
        cir.setReturnValue(new Beardifier(rigids.iterator(), junctions.iterator()));
    }

    @Unique
    private static BoundingBox alpha_omega$move(BoundingBox box, int dx, int dz) {
        return dx == 0 && dz == 0 ? box : box.moved(dx, 0, dz);
    }
}
