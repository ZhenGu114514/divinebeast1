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
 * 客户端键位：救赎之击 / 神行移速 / 脑后星环 / 领域与真伤 / 神威·诛灭 五个开关。
 *
 * <p><b>五个开关默认全部"未设置"</b>（不绑定任何按键），由玩家自行在
 * 「选项 → 控制 → 祂 · 兽」里分配，任务书的「按键开关一览」也写明了这一点。
 * 纯客户端类，只通过 DistExecutor 在 CLIENT 侧加载。
 */
public final class ClientKeybinds {

    private static final String CATEGORY = "key.categories.divinebeast";

    /**
     * 所有开关默认<b>不绑定任何按键</b>（{@code GLFW_KEY_UNKNOWN} → 控制界面显示"未设置"）。
     *
     * <p>按需求改为"由玩家自行设置"：请到「选项 → 控制 → 祂 · 兽」里，
     * 按下面的功能名分配按键，任务书的「按键开关一览」也写明了同样的事。
     */
    private static final int UNBOUND = GLFW.GLFW_KEY_UNKNOWN;

    private static final KeyMapping TOGGLE_RESPAWN = new KeyMapping(
            "key.divinebeast.toggle_respawn", InputConstants.Type.KEYSYM, UNBOUND, CATEGORY);

    private static final KeyMapping TOGGLE_SPEED = new KeyMapping(
            "key.divinebeast.toggle_speed", InputConstants.Type.KEYSYM, UNBOUND, CATEGORY);

    private static final KeyMapping TOGGLE_AURA = new KeyMapping(
            "key.divinebeast.toggle_aura", InputConstants.Type.KEYSYM, UNBOUND, CATEGORY);

    /** 真者祂「10 光之领域」范围伤害开关 */
    private static final KeyMapping TOGGLE_BEACON = new KeyMapping(
            "key.divinebeast.toggle_beacon", InputConstants.Type.KEYSYM, UNBOUND, CATEGORY);

    /** 真者祂「神威·诛灭」五重伤害手段开关 */
    private static final KeyMapping TOGGLE_KILL = new KeyMapping(
            "key.divinebeast.toggle_kill", InputConstants.Type.KEYSYM, UNBOUND, CATEGORY);

    private ClientKeybinds() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientKeybinds::onRegisterKeyMappings);
        MinecraftForge.EVENT_BUS.addListener(ClientKeybinds::onClientTick);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_RESPAWN);
        event.register(TOGGLE_SPEED);
        event.register(TOGGLE_AURA);
        event.register(TOGGLE_BEACON);
        event.register(TOGGLE_KILL);
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
        while (TOGGLE_AURA.consumeClick()) {
            OrbitAuraClient.toggle();
        }
        while (TOGGLE_BEACON.consumeClick()) {
            Networking.sendToServer(new com.divinebeast.divinebeast.net.ToggleBeaconMessage());
        }
        while (TOGGLE_KILL.consumeClick()) {
            Networking.sendToServer(new com.divinebeast.divinebeast.net.ToggleKillMessage());
        }
    }
}
