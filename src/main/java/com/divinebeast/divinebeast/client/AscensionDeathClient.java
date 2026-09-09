package com.divinebeast.divinebeast.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 证悟抉择界面客户端入口（仅客户端，由网络消息在 Dist 侧调用）。
 */
public final class AscensionDeathClient {

    private AscensionDeathClient() {
    }

    /** 客户端侧注册钩子（预留；无额外事件需要挂载）。 */
    public static void init() {
    }

    /** 打开「自我救赎 / 找回自我」抉择界面。 */
    public static void open(int kind) {
        Minecraft.getInstance().setScreen(new AscensionDeathScreen(kind));
    }

    /** 主按钮文案 */
    public static Component mainButton(int kind) {
        return kind == 1
                ? Component.translatable("divinebeast.death.self_redeem")
                : Component.translatable("divinebeast.death.find_self");
    }
}
