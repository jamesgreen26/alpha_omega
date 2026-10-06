package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.orbifold.OrbifoldSettings;
import g_mungus.alpha_omega.orbifold.OrbifoldSize;
import g_mungus.alpha_omega.worldgen.OrbifoldChunkGenerator;
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

/** "Customize" on the Create World screen for the orbifold preset: the size. */
public final class OrbifoldPresetScreen extends Screen {

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final CreateWorldScreen parent;
    private OrbifoldSettings settings;

    private OrbifoldPresetScreen(CreateWorldScreen parent, OrbifoldSettings settings) {
        super(Component.translatable("alpha_omega.orbifold.customize"));
        this.parent = parent;
        this.settings = settings;
    }

    public static Screen create(CreateWorldScreen parent, WorldCreationContext context) {
        OrbifoldSettings settings = context.selectedDimensions().overworld() instanceof OrbifoldChunkGenerator generator
            ? generator.orbifold() : AlphaOmegaConfig.defaults();
        return new OrbifoldPresetScreen(parent, settings);
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);
        LinearLayout rows = this.layout.addToContents(LinearLayout.vertical().spacing(8));
        rows.defaultCellSetting().alignHorizontallyCenter();
        rows.addChild(CycleButton.<OrbifoldSize>builder(OrbifoldPresetScreen::sizeName)
            .withValues(OrbifoldSize.PRESETS)
            .withInitialValue(this.settings.size())
            .create(0, 0, 210, 20, Component.translatable("alpha_omega.orbifold.size"),
                (button, size) -> this.settings = this.settings.withSize(size)));
        rows.addChild(new StringWidget(Component.translatable("alpha_omega.orbifold.hint").withColor(0xA0A0A0), this.font));
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
            this.apply();
            this.onClose();
        }).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    /** A size as the button shows it: {@code a × b} and its name, such as "3584 × 3072 (small)". */
    private static Component sizeName(OrbifoldSize size) {
        return Component.translatable("alpha_omega.orbifold.size.value", size.a(), size.b(),
            Component.translatable("alpha_omega.orbifold.size." + size.id()));
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
        OrbifoldSettings chosen = this.settings;
        this.parent.getUiState().updateDimensions((registries, dimensions) -> {
            if (!(dimensions.overworld() instanceof OrbifoldChunkGenerator old)) return dimensions;
            return dimensions.replaceOverworldGenerator(registries, new OrbifoldChunkGenerator(old.getBiomeSource(), old.generatorSettings(), chosen));
        });
    }
}
