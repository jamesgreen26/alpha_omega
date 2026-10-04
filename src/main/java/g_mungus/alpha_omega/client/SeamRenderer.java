package g_mungus.alpha_omega.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Debug aid drawn with the chunk borders (F3+G): the world wrap seam, at the image nearest the camera, as a
 * translucent wall with a chunk grid. Each axis's seam is drawn while it is within render distance.
 */
public final class SeamRenderer {

    private static final float R = 1.0F, G = 0.2F, B = 0.9F;
    private static final float FILL_ALPHA = 0.12F;
    private static final float GRID_ALPHA = 1.0F;
    private static final double GRID_OFFSET = 0.05;

    private SeamRenderer() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, double camX, double camY, double camZ) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Wrap wrap = Wrap.of(level);
        if (!wrap.enabled() || Wrap.offTorus(camX) || Wrap.offTorus(camZ)) return;

        int range = mc.options.getEffectiveRenderDistance() * 16;
        float minY = (float) (level.getMinBuildHeight() - camY);
        float maxY = (float) (level.getMaxBuildHeight() - camY);
        Matrix4f pose = poseStack.last().pose();

        // Seam positions relative to the camera, and the stretch along each seam that is drawn (chunk aligned).
        double seamX = wrap.nearest(wrap.minBlock, camX) - camX;
        double seamZ = wrap.nearest(wrap.minBlock, camZ) - camZ;
        int fromZ = (((int) Math.floor(camZ) - range) >> 4) << 4, toZ = fromZ + 2 * range + 16;
        int fromX = (((int) Math.floor(camX) - range) >> 4) << 4, toX = fromX + 2 * range + 16;
        boolean drawX = Math.abs(seamX) <= range;
        boolean drawZ = Math.abs(seamZ) <= range;
        if (!drawX && !drawZ) return;

        // The grid sits just on the camera's side of the wall, or the two z-fight.
        VertexConsumer lines = buffers.getBuffer(RenderType.debugLineStrip(3.0));
        if (drawX) {
            float x = (float) (seamX - Math.signum(seamX) * GRID_OFFSET);
            for (int z = fromZ; z <= toZ; z += 16) {
                float zf = (float) (z - camZ);
                line(lines, pose, x, minY, zf, x, maxY, zf);
            }
            for (int y = level.getMinBuildHeight(); y <= level.getMaxBuildHeight(); y += 16) {
                float yf = (float) (y - camY);
                line(lines, pose, x, yf, (float) (fromZ - camZ), x, yf, (float) (toZ - camZ));
            }
        }
        if (drawZ) {
            float z = (float) (seamZ - Math.signum(seamZ) * GRID_OFFSET);
            for (int x = fromX; x <= toX; x += 16) {
                float xf = (float) (x - camX);
                line(lines, pose, xf, minY, z, xf, maxY, z);
            }
            for (int y = level.getMinBuildHeight(); y <= level.getMaxBuildHeight(); y += 16) {
                float yf = (float) (y - camY);
                line(lines, pose, (float) (fromX - camX), yf, z, (float) (toX - camX), yf, z);
            }
        }
    }

    /** One segment of a line strip, with transparent ends so it does not join its neighbours. */
    private static void line(VertexConsumer lines, Matrix4f pose, float x0, float y0, float z0, float x1, float y1, float z1) {
        lines.addVertex(pose, x0, y0, z0).setColor(R, G, B, 0.0F);
        lines.addVertex(pose, x0, y0, z0).setColor(R, G, B, GRID_ALPHA);
        lines.addVertex(pose, x1, y1, z1).setColor(R, G, B, GRID_ALPHA);
        lines.addVertex(pose, x1, y1, z1).setColor(R, G, B, 0.0F);
    }
}
