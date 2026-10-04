package g_mungus.alpha_omega.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/**
 * The night stars, from Genesis: three layers of smaller stars than vanilla's, each with its own seed and a faint
 * white, blue or yellow tint. Drawn in place of vanilla's star buffer, so they share its rotation, fog and brightness.
 */
public final class StarField {

    /** Tint (r, g, b, a) per layer. */
    private static final float[][] COLORS = {
        {1.0F, 1.0F, 1.0F, 0.8F},
        {0.8F, 0.8F, 1.0F, 0.8F},
        {1.0F, 1.0F, 0.8F, 0.8F},
    };

    private static VertexBuffer[] layers;

    private StarField() {
    }

    /**
     * Draws the stars with the shader vanilla would use. The shader colour on entry is vanilla's star colour, its
     * brightness in every channel; each layer's tint is scaled by it.
     */
    public static void draw(Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        if (layers == null) layers = build();
        float brightness = RenderSystem.getShaderColor()[3];
        for (int i = 0; i < layers.length; i++) {
            float[] color = COLORS[i];
            RenderSystem.setShaderColor(color[0] * brightness, color[1] * brightness, color[2] * brightness, color[3] * brightness);
            layers[i].bind();
            layers[i].drawWithShader(modelView, projection, shader);
        }
        RenderSystem.setShaderColor(brightness, brightness, brightness, brightness);
    }

    private static VertexBuffer[] build() {
        VertexBuffer[] buffers = new VertexBuffer[COLORS.length];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffers[i].bind();
            buffers[i].upload(mesh(10842L / (i + 4)));
        }
        VertexBuffer.unbind();
        return buffers;
    }

    /** Vanilla's 1.20 star placement with Genesis's count and size range. */
    private static MeshData mesh(long seed) {
        RandomSource random = RandomSource.create(seed);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (int i = 0; i < 1600; i++) {
            double x = random.nextFloat() * 2.0F - 1.0F;
            double y = random.nextFloat() * 2.0F - 1.0F;
            double z = random.nextFloat() * 2.0F - 1.0F;
            double size = 0.05F + random.nextFloat() * 0.2F;
            double lengthSq = x * x + y * y + z * z;
            if (lengthSq >= 1.0 || lengthSq <= 0.01) continue;

            double inv = 1.0 / Math.sqrt(lengthSq);
            x *= inv;
            y *= inv;
            z *= inv;
            double yaw = Math.atan2(x, z);
            double sinYaw = Math.sin(yaw);
            double cosYaw = Math.cos(yaw);
            double pitch = Math.atan2(Math.sqrt(x * x + z * z), y);
            double sinPitch = Math.sin(pitch);
            double cosPitch = Math.cos(pitch);
            double roll = random.nextDouble() * Math.PI * 2.0;
            double sinRoll = Math.sin(roll);
            double cosRoll = Math.cos(roll);

            for (int j = 0; j < 4; j++) {
                double u = ((j & 2) - 1) * size;
                double v = ((j + 1 & 2) - 1) * size;
                double a = u * cosRoll - v * sinRoll;
                double b = v * cosRoll + u * sinRoll;
                double up = a * sinPitch;
                double out = -a * cosPitch;
                builder.addVertex(
                    (float) (x * 100.0 + out * sinYaw - b * cosYaw),
                    (float) (y * 100.0 + up),
                    (float) (z * 100.0 + b * sinYaw + out * cosYaw));
            }
        }
        return builder.buildOrThrow();
    }
}
