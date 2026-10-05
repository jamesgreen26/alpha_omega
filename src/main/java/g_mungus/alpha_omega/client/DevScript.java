package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.AlphaOmegaMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Development aid: scripted screenshots of a running client. {@code -Dalpha_omega.dev.script} holds steps
 * {@code tick:action} separated by {@code ;}, counted in client ticks after joining a world. An action is a command
 * ({@code /tp @s 0 100 0}), {@code shot <name>} (saved to {@code screenshots/<name>.png}), {@code hud on|off},
 * {@code forward on|off} (holds the walk key), {@code jump on|off} (holds the jump key),
 * {@code camera first|back|front} (the point of view), {@code pos} (logs the player), {@code debug} (toggles F3) or {@code quit}. Used with {@code -PquickPlay=<world>}. {@code @file} reads the steps from a file instead (one or more per line).
 */
public final class DevScript {

    private record Step(int tick, String action) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int ticks;

    private DevScript() {
    }

    public static void init() {
        String script = System.getProperty("alpha_omega.dev.script");
        if (script == null || script.isBlank()) return;
        if (script.startsWith("@")) {
            // @file: the steps are in that file (relative to the game directory), one or more per line.
            try {
                script = String.join(";", java.nio.file.Files.readAllLines(java.nio.file.Path.of(script.substring(1))));
            } catch (java.io.IOException e) {
                AlphaOmegaMod.LOGGER.error("Dev script file {} could not be read", script, e);
                return;
            }
        }
        for (String part : script.split(";")) {
            int colon = part.indexOf(':');
            if (colon <= 0) continue;
            STEPS.add(new Step(Integer.parseInt(part.substring(0, colon).trim()), part.substring(colon + 1).trim()));
        }
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
    }

    private static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        // The scripted window is usually in the background: never pause for that, and keep menus out of the shots.
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.screen != null && !(minecraft.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) minecraft.setScreen(null);
        ticks++;
        for (Step step : STEPS) {
            if (step.tick != ticks) continue;
            AlphaOmegaMod.LOGGER.info("Dev script at tick {}: {}", ticks, step.action);
            String action = step.action;
            if (action.startsWith("/")) {
                minecraft.player.connection.sendCommand(action.substring(1));
            } else if (action.startsWith("shot ")) {
                Screenshot.grab(minecraft.gameDirectory, action.substring(5) + ".png", minecraft.getMainRenderTarget(),
                    message -> AlphaOmegaMod.LOGGER.info("Dev script: {}", message.getString()));
            } else if (action.equals("hud off") || action.equals("hud on")) {
                minecraft.options.hideGui = action.equals("hud off");
            } else if (action.equals("forward on") || action.equals("forward off")) {
                minecraft.options.keyUp.setDown(action.equals("forward on"));
            } else if (action.equals("jump on") || action.equals("jump off")) {
                minecraft.options.keyJump.setDown(action.equals("jump on"));
            } else if (action.startsWith("camera ")) {
                minecraft.options.setCameraType(switch (action.substring(7)) {
                    case "back" -> net.minecraft.client.CameraType.THIRD_PERSON_BACK;
                    case "front" -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
                    default -> net.minecraft.client.CameraType.FIRST_PERSON;
                });
            } else if (action.equals("pos")) {
                var p = minecraft.player;
                AlphaOmegaMod.LOGGER.info("Dev script: pos {} motion {} rot {}/{} forward {} input {} flying {}", p.position(), p.getDeltaMovement(),
                    p.getYRot(), p.getXRot(), minecraft.options.keyUp.isDown(), p.input.forwardImpulse, p.getAbilities().flying);
            } else if (action.equals("debug")) {
                minecraft.getDebugOverlay().toggleOverlay();
            } else if (action.equals("quit")) {
                minecraft.stop();
            }
        }
    }
}
