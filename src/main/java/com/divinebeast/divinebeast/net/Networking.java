package com.divinebeast.divinebeast.net;

import com.divinebeast.divinebeast.DivineBeastMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 简单网络通道：客户端 → 服务器 传递 C 键开关（救赎重生）。
 * 本类不引用 Curios，任何环境下都安全。
 */
public final class Networking {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DivineBeastMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextId = 0;

    private Networking() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++, ToggleRespawnMessage.class,
                ToggleRespawnMessage::encode,
                ToggleRespawnMessage::decode,
                ToggleRespawnMessage::handle);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
