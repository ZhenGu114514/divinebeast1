package com.divinebeast.divinebeast.net;

import com.divinebeast.divinebeast.DivineBeastMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 简单网络通道：C 键开关 + 证悟死亡抉择界面。
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
        CHANNEL.registerMessage(nextId++, AscensionScreenMessage.class,
                AscensionScreenMessage::encode,
                AscensionScreenMessage::decode,
                AscensionScreenMessage::handle);
        CHANNEL.registerMessage(nextId++, AscensionChoiceMessage.class,
                AscensionChoiceMessage::encode,
                AscensionChoiceMessage::decode,
                AscensionChoiceMessage::handle);
        CHANNEL.registerMessage(nextId++, ToggleSpeedMessage.class,
                ToggleSpeedMessage::encode,
                ToggleSpeedMessage::decode,
                ToggleSpeedMessage::handle);
        CHANNEL.registerMessage(nextId++, ToggleBeaconMessage.class,
                ToggleBeaconMessage::encode,
                ToggleBeaconMessage::decode,
                ToggleBeaconMessage::handle);
        CHANNEL.registerMessage(nextId++, ItemCopyMessage.class,
                ItemCopyMessage::encode,
                ItemCopyMessage::decode,
                ItemCopyMessage::handle);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    /** 发送到指定玩家（S2C）。 */
    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
