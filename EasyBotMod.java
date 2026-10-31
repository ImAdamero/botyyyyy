package pl.easybot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.easybot.bot.BotManager;
import pl.easybot.screen.BotScreen;
import pl.easybot.screen.ErrorsScreen;

public class EasyBotMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("EasyBot");

    public static KeyBinding openKey;
    public static KeyBinding errorsKey;

    @Override
    public void onInitializeClient() {
        Config.get();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("easybot", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.easybot.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, category));
        errorsKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.easybot.errors", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new BotScreen(null));
                }
            }
            while (errorsKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ErrorsScreen(null));
                }
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> BotManager.shutdown());
        LOGGER.info("EasyBot zaladowany: X = menu, B = konsola bledow");
    }
}
