package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.net.Networking;
import com.divinebeast.divinebeast.net.ToggleRespawnMessage;
import com.divinebeast.divinebeast.net.ToggleSpeedMessage;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端键位：按 C 键开关"救赎重生"；按 X 键开关真者祂「神行」移速加成。
 * 纯客户端类，只通过 DistExecutor 在 CLIENT 侧加载。
 */
public final class ClientKeybinds {

    private static final String CATEGORY = "key.categories.divinebeast";

    private static final KeyMapping TOGGLE_RESPAWN = new KeyMapping(
            "key.divinebeast.toggle_respawn", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);

    private static final KeyMapping TOGGLE_SPEED = new KeyMapping(
            "key.divinebeast.toggle_speed", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    private ClientKeybinds() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientKeybinds::onRegisterKeyMappings);
        MinecraftForge.EVENT_BUS.addListener(ClientKeybinds::onClientTick);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_RESPAWN);
        event.register(TOGGLE_SPEED);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        while (TOGGLE_RESPAWN.consumeClick()) {
            Networking.sendToServer(new ToggleRespawnMessage());
        }
        while (TOGGLE_SPEED.consumeClick()) {
            Networking.sendToServer(new ToggleSpeedMessage());
        }
    }
}
