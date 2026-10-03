package g_mungus.alpha_omega.island;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;

/**
 * Entities hold absolute positions beyond their own (path targets, memories, homes). When an entity moves between
 * frames, every handler registered for its type translates that state by the same whole-lap offset (design doc
 * §6.4). Other mods register handlers for their entities through the public API.
 */
public final class FrameTranslators {

    /** Translates every absolute position {@code entity} holds by {@code (dx, dz)} blocks. */
    @FunctionalInterface
    public interface FrameTranslator<T extends Entity> {
        void translate(T entity, double dx, double dz);
    }

    private record Registration<T extends Entity>(Class<T> type, FrameTranslator<? super T> translator) {
        void apply(Entity entity, double dx, double dz) {
            if (this.type.isInstance(entity)) this.translator.translate(this.type.cast(entity), dx, dz);
        }
    }

    private static final List<Registration<?>> REGISTRATIONS = new ArrayList<>();

    private FrameTranslators() {
    }

    public static synchronized <T extends Entity> void register(Class<T> type, FrameTranslator<? super T> translator) {
        REGISTRATIONS.add(new Registration<>(type, translator));
    }

    static void translate(Entity entity, double dx, double dz) {
        EntityFrames.translateGeneric(entity, dx, dz);
        for (Registration<?> registration : REGISTRATIONS) registration.apply(entity, dx, dz);
    }
}
