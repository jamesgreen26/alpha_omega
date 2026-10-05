package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cube World is the first world type on the Create World screen, and the one a new world starts with. Vanilla asks
 * for its normal preset by name when it opens the screen afresh; that request becomes Cube World. Re-creating an
 * existing world keeps that world's own type.
 */
@Mixin(WorldCreationUiState.class)
abstract class WorldCreationUiStateMixin {

    @Shadow
    @Final
    private List<WorldCreationUiState.WorldTypeEntry> normalPresetList;
    @Shadow
    @Final
    private List<WorldCreationUiState.WorldTypeEntry> altPresetList;

    @Shadow
    public abstract WorldCreationContext getSettings();

    @Shadow
    public abstract void setWorldType(WorldCreationUiState.WorldTypeEntry type);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void alpha_omega$startAsCube(Path saves, WorldCreationContext context, Optional<ResourceKey<WorldPreset>> preset,
                                         OptionalLong seed, CallbackInfo ci) {
        if (!preset.equals(Optional.of(WorldPresets.NORMAL))) return;
        this.getSettings().worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET).getHolder(CubeChunkGenerator.PRESET)
            .ifPresent(cube -> this.setWorldType(new WorldCreationUiState.WorldTypeEntry(cube)));
    }

    @Inject(method = "updatePresetLists", at = @At("TAIL"))
    private void alpha_omega$cubeFirst(CallbackInfo ci) {
        alpha_omega$moveCubeFirst(this.normalPresetList);
        alpha_omega$moveCubeFirst(this.altPresetList);
    }

    @Unique
    private static void alpha_omega$moveCubeFirst(List<WorldCreationUiState.WorldTypeEntry> types) {
        for (int i = 1; i < types.size(); i++) {
            Holder<WorldPreset> preset = types.get(i).preset();
            if (preset != null && preset.is(CubeChunkGenerator.PRESET)) {
                types.add(0, types.remove(i));
                return;
            }
        }
    }
}
