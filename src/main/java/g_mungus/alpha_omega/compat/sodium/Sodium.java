package g_mungus.alpha_omega.compat.sodium;

import net.neoforged.fml.loading.LoadingModList;

/** Whether Sodium is installed. Safe to call without it: this class names none of Sodium's. */
public final class Sodium {

    private static final boolean LOADED = LoadingModList.get().getModFileById("sodium") != null;

    private Sodium() {
    }

    public static boolean loaded() {
        return LOADED;
    }
}
