package com.simplestructurescanner.client;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import com.simplestructurescanner.Tags;
import com.simplestructurescanner.client.capture.StructureCaptureClientController;
import com.simplestructurescanner.client.gui.GuiStructureScanner;


public class KeybindHandler {
    private static final String KEYBIND_CATEGORY = "key.categories." + Tags.MODID;

    public static KeyBinding openGuiKey;

    public static void registerKeybinds() {
        openGuiKey = new KeyBinding(
            "key." + Tags.MODID + ".open_gui",
            KeyConflictContext.IN_GAME,
            Keyboard.KEY_P,
            KEYBIND_CATEGORY
        );
        ClientRegistry.registerKeyBinding(openGuiKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (openGuiKey.isPressed()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(new GuiStructureScanner());
            }
        }
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        // Capture selections are world-scoped and must not carry into the next world.
        StructureCaptureClientController.clearCaptureData();
    }
}
