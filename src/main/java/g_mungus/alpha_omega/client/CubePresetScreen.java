package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.cube.CubeSettings;
import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** "Customize" on the Create World screen for the cube preset: face width and sun axis. */
public final class CubePresetScreen extends Screen {

    /** The slider's range; the config file allows the full range. */
    private static final int SLIDER_MIN = 4;
    private static final int SLIDER_MAX = 64;

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final CreateWorldScreen parent;
    private CubeSettings settings;

    private CubePresetScreen(CreateWorldScreen parent, CubeSettings settings) {
        super(Component.translatable("alpha_omega.cube.customize"));
        this.parent = parent;
        this.settings = settings;
    }

    public static Screen create(CreateWorldScreen parent, WorldCreationContext context) {
        CubeSettings settings = context.selectedDimensions().overworld() instanceof CubeChunkGenerator generator
            ? generator.cube() : AlphaOmegaConfig.defaults();
        return new CubePresetScreen(parent, settings);
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);
        LinearLayout rows = this.layout.addToContents(LinearLayout.vertical().spacing(8));
        rows.defaultCellSetting().alignHorizontallyCenter();
        rows.addChild(new FaceSlider(this.settings.faceChunks()));
        rows.addChild(new ScaleSlider(this.settings.horizontalScale()));
        rows.addChild(CycleButton.<CubeSettings.SunAxis>builder(axis -> Component.translatable("alpha_omega.cube.sun_axis." + axis.getSerializedName()))
            .withValues(CubeSettings.SunAxis.values())
            .withInitialValue(this.settings.sunAxis())
            .create(0, 0, 210, 20, Component.translatable("alpha_omega.cube.sun_axis"), (button, axis) -> this.settings = this.settings.withSunAxis(axis)));
        rows.addChild(new StringWidget(Component.translatable("alpha_omega.cube.hint").withColor(0xA0A0A0), this.font));
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
            this.apply();
            this.onClose();
        }).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private void apply() {
        CubeSettings chosen = this.settings;
        this.parent.getUiState().updateDimensions((registries, dimensions) -> {
            if (!(dimensions.overworld() instanceof CubeChunkGenerator old)) return dimensions;
            return dimensions.replaceOverworldGenerator(registries, new CubeChunkGenerator(old.getBiomeSource(), old.generatorSettings(), chosen));
        });
    }

    /** Terrain scale, in steps of a quarter from 0.25 to 4 (the config allows up to 16). */
    private final class ScaleSlider extends AbstractSliderButton {

        private static final double MIN = 0.25;
        private static final double MAX = 4.0;

        ScaleSlider(double scale) {
            super(0, 0, 210, 20, Component.empty(), Mth.clamp((scale - MIN) / (MAX - MIN), 0.0, 1.0));
            this.updateMessage();
        }

        private double scale() {
            return Math.round((MIN + this.value * (MAX - MIN)) * 4.0) / 4.0;
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("alpha_omega.cube.horizontal_scale", String.format(java.util.Locale.ROOT, "%.2f", this.scale())));
        }

        @Override
        protected void applyValue() {
            CubePresetScreen.this.settings = CubePresetScreen.this.settings.withHorizontalScale(this.scale());
        }
    }

    private final class FaceSlider extends AbstractSliderButton {

        FaceSlider(int faceChunks) {
            super(0, 0, 210, 20, Component.empty(), toValue(faceChunks));
            this.updateMessage();
        }

        private static double toValue(int faceChunks) {
            return Mth.clamp((faceChunks - SLIDER_MIN) / (double) (SLIDER_MAX - SLIDER_MIN), 0.0, 1.0);
        }

        private int faceChunks() {
            return SLIDER_MIN + (int) Math.round(this.value * (SLIDER_MAX - SLIDER_MIN));
        }

        @Override
        protected void updateMessage() {
            int chunks = this.faceChunks();
            this.setMessage(Component.translatable("alpha_omega.cube.face_width", chunks, chunks * 16));
        }

        @Override
        protected void applyValue() {
            CubePresetScreen.this.settings = CubePresetScreen.this.settings.withFaceChunks(this.faceChunks());
        }
    }
}
