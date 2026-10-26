package de.spotifyglass;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SpotifyGlassClient implements ClientModInitializer {
    static KeyBinding kSettings, kPlay, kPrev, kNext, kUp, kDown;

    static KeyBinding key(String name, int code, KeyBinding.Category cat) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.spotifyglass." + name, InputUtil.Type.KEYSYM, code, cat));
    }

    @Override
    public void onInitializeClient() {
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("spotifyglass", "main"));
        kSettings = key("settings", GLFW.GLFW_KEY_K, cat);
        kPlay = key("playpause", GLFW.GLFW_KEY_P, cat);
        kPrev = key("prev", GLFW.GLFW_KEY_LEFT, cat);
        kNext = key("next", GLFW.GLFW_KEY_RIGHT, cat);
        kUp = key("volup", GLFW.GLFW_KEY_UP, cat);
        kDown = key("voldown", GLFW.GLFW_KEY_DOWN, cat);

        Spotify.I.start();

        HudElementRegistry.addLast(Identifier.of("spotifyglass", "overlay"), (ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!Cfg.I.visible || mc.options.hudHidden || mc.currentScreen instanceof SettingsScreen) return;
            Overlay.render(ctx, -1, -1);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean idle = mc.currentScreen == null;
            while (kSettings.wasPressed()) if (idle) mc.setScreen(new SettingsScreen());
            while (kPlay.wasPressed()) if (idle) Spotify.I.togglePlay();
            while (kPrev.wasPressed()) if (idle) Spotify.I.prev();
            while (kNext.wasPressed()) if (idle) Spotify.I.next();
            while (kUp.wasPressed()) if (idle) Spotify.I.setVolume(Spotify.I.volume + Cfg.I.volStep, true);
            while (kDown.wasPressed()) if (idle) Spotify.I.setVolume(Spotify.I.volume - Cfg.I.volStep, true);
        });
    }
}
