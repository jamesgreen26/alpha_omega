package g_mungus.alpha_omega.client.sky;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.sky.AtmosphereModel;
import g_mungus.alpha_omega.sky.AtmosphereParams;
import g_mungus.alpha_omega.sky.SkyColors;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Draws the overworld sky from the atmosphere. The CPU's transmittance and multiple-scattering tables are uploaded once
 * as float textures; each frame a small pass fills Hillaire's sky-view table (radiance by azimuth from the sun and
 * elevation), and a full-screen pass looks it up per pixel in place of vanilla's flat-coloured dome.
 */
public final class AtmosphereRenderer {

    private static final int SKY_VIEW_WIDTH = 192;
    private static final int SKY_VIEW_HEIGHT = 108;

    private static ShaderInstance skyViewShader;
    private static ShaderInstance skyShader;
    private static int transmittanceTexture = -1;
    private static int multiScatteringTexture = -1;
    private static int skyViewTexture = -1;
    private static int skyViewFramebuffer = -1;
    private static boolean failed;

    private AtmosphereRenderer() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(AtmosphereRenderer::registerShaders);
    }

    private static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "atmo_skyview"), DefaultVertexFormat.POSITION), s -> skyViewShader = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "atmo_sky"), DefaultVertexFormat.POSITION), s -> skyShader = s);
        } catch (IOException e) {
            AlphaOmegaMod.LOGGER.error("Could not load the atmosphere shaders", e);
        }
    }

    /**
     * Draw the sky for a camera with this rotation and projection, in place of vanilla's dome. Returns false (and draws
     * nothing) when the GPU sky is unavailable, so the caller draws vanilla's.
     */
    public static boolean drawSky(ClientLevel level, Matrix4f modelView, Matrix4f projection) {
        SkyColors colors = SkyState.colors();
        if (failed || colors == null || skyViewShader == null || skyShader == null) return false;
        if (transmittanceTexture < 0 && !createResources(colors.model)) return false;

        AtmosphereParams params = colors.model.params;
        double[] sun = SkyState.sun(level);
        float altitude = (float) SkyState.altitude(level);
        for (ShaderInstance shader : new ShaderInstance[] {skyViewShader, skyShader}) {
            shader.safeGetUniform("Radii").set((float) params.groundRadius(), (float) params.topRadius());
            shader.safeGetUniform("RayleighScattering").set((float) params.rayleighScattering()[0], (float) params.rayleighScattering()[1],
                (float) params.rayleighScattering()[2]);
            shader.safeGetUniform("RayleighScaleHeight").set((float) params.rayleighScaleHeight());
            shader.safeGetUniform("Mie").set((float) params.mieScattering(), (float) params.mieExtinction(), (float) params.mieScaleHeight(),
                (float) params.mieG());
            shader.safeGetUniform("OzoneAbsorption").set((float) params.ozoneAbsorption()[0], (float) params.ozoneAbsorption()[1],
                (float) params.ozoneAbsorption()[2]);
            shader.safeGetUniform("Ozone").set((float) params.ozoneCenter(), (float) params.ozoneHalfWidth());
            shader.safeGetUniform("SkyViewSize").set((float) SKY_VIEW_WIDTH, (float) SKY_VIEW_HEIGHT);
            shader.safeGetUniform("SunDir").set((float) sun[0], (float) sun[1], (float) sun[2]);
            shader.safeGetUniform("Altitude").set(altitude);
            shader.setSampler("Transmittance", transmittanceTexture);
            shader.setSampler("MultiScattering", multiScatteringTexture);
        }

        // The sky-view table, into its own framebuffer.
        skyViewShader.safeGetUniform("MoonIlluminance").set((float) SkyState.moonIlluminance(level));
        skyViewShader.safeGetUniform("Airglow").set((float) SkyColors.AIRGLOW[0], (float) SkyColors.AIRGLOW[1], (float) SkyColors.AIRGLOW[2]);
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, buffer);
            buffer.get(viewport);
        }
        RenderSystem.disableBlend();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, skyViewFramebuffer);
        GlStateManager._viewport(0, 0, SKY_VIEW_WIDTH, SKY_VIEW_HEIGHT);
        drawFullScreen(skyViewShader);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        // The sky itself.
        Matrix4f inverse = new Matrix4f(projection).mul(modelView).invert();
        float rain = level.getRainLevel(1.0F);
        float thunder = level.getThunderLevel(1.0F);
        float flash = 0.0F;
        if (level.getSkyFlashTime() > 0) {
            flash = Math.min(1.0F, level.getSkyFlashTime() - Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) * 0.45F;
        }
        int renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
        skyShader.safeGetUniform("InvViewProj").set(inverse);
        skyShader.safeGetUniform("Exposure").set((float) SkyState.exposure(level));
        skyShader.safeGetUniform("Weather").set(rain, thunder, flash);
        skyShader.safeGetUniform("Density").set((float) SpaceFade.atCamera(level));
        // Vanilla's dome fades into the fog over more of the sky at short render distances.
        skyShader.safeGetUniform("HorizonFade").set(0.12F + 0.25F * Math.max(0.0F, 1.0F - renderDistance / 16.0F));
        skyShader.setSampler("SkyView", skyViewTexture);
        drawFullScreen(skyShader);
        return true;
    }

    private static void drawFullScreen(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(-1.0F, -1.0F, 0.0F);
        builder.addVertex(1.0F, -1.0F, 0.0F);
        builder.addVertex(1.0F, 1.0F, 0.0F);
        builder.addVertex(-1.0F, 1.0F, 0.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static boolean createResources(AtmosphereModel model) {
        try {
            transmittanceTexture = floatTexture(AtmosphereModel.TRANSMITTANCE_WIDTH, AtmosphereModel.TRANSMITTANCE_HEIGHT, model.transmittanceTable());
            multiScatteringTexture = floatTexture(AtmosphereModel.MULTI_SCATTERING_SIZE, AtmosphereModel.MULTI_SCATTERING_SIZE, model.multiScatteringTable());
            skyViewTexture = GlStateManager._genTexture();
            GlStateManager._bindTexture(skyViewTexture);
            linearClamp();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, SKY_VIEW_WIDTH, SKY_VIEW_HEIGHT, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
            int previousFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            skyViewFramebuffer = GlStateManager.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, skyViewFramebuffer);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, skyViewTexture, 0);
            int status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            GlStateManager._bindTexture(0);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("sky-view framebuffer incomplete: " + status);
            return true;
        } catch (RuntimeException e) {
            AlphaOmegaMod.LOGGER.error("Atmosphere sky disabled: could not create its textures", e);
            failed = true;
            return false;
        }
    }

    private static int floatTexture(int width, int height, float[] rgb) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        linearClamp();
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        FloatBuffer buffer = MemoryUtil.memAllocFloat(rgb.length);
        try {
            buffer.put(rgb).flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGB32F, width, height, 0, GL11.GL_RGB, GL11.GL_FLOAT, buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
        return id;
    }

    private static void linearClamp() {
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
    }
}
