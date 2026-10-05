package g_mungus.alpha_omega.gametest;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.TestFunction;
import org.jetbrains.annotations.Nullable;

/**
 * Runs part of the gametest suite: {@code -Dalpha_omega.gametests=NeighbourGameTests,TransferGameTests.mobsWaitForRoom}
 * (Gradle: {@code -Pgametests=...}) registers only the named classes, and of a class named with a method, only that
 * method. Unset, everything runs. Mod loading checks always run.
 */
public final class GameTestFilter {

    private static final String PROPERTY = "alpha_omega.gametests";
    /** Test names (method names, lower case) to keep, or null for all. */
    @Nullable
    private static Set<String> allowed;

    private GameTestFilter() {
    }

    /** Registers the classes the filter lets through. */
    public static void register(List<Class<?>> classes, Consumer<Class<?>> registrar) {
        String spec = System.getProperty(PROPERTY);
        if (spec == null || spec.isBlank()) {
            classes.forEach(registrar);
            return;
        }
        List<String> tokens = Arrays.stream(spec.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
        Set<String> names = new HashSet<>();
        for (Class<?> type : classes) {
            boolean always = type == ModLoadGameTests.class;
            boolean whole = always || tokens.contains(type.getSimpleName());
            Set<String> methods = new HashSet<>();
            for (String token : tokens) {
                if (token.startsWith(type.getSimpleName() + ".")) methods.add(token.substring(type.getSimpleName().length() + 1).toLowerCase(Locale.ROOT));
            }
            if (!whole && methods.isEmpty()) continue;
            registrar.accept(type);
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(GameTest.class)) continue;
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (whole || methods.contains(name)) names.add(name);
            }
        }
        allowed = names;
    }

    public static boolean keep(TestFunction function) {
        return allowed == null || allowed.contains(function.testName().toLowerCase(Locale.ROOT));
    }
}
