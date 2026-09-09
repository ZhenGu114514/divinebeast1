package com.divinebeast.divinebeast.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务器端切换玩家 persistent NBT 中的"真者祂·神行移速"开关：
 * {@code divinebeast.htrue_speed_toggle}，默认开启。
 * 关闭后，真者祂引擎不再维持行走/飞行/游动的 +2.4 移速加成。
 */
public class ToggleSpeedMessage {

    public static void encode(ToggleSpeedMessage message, FriendlyByteBuf buffer) {
    }

    public static ToggleSpeedMessage decode(FriendlyByteBuf buffer) {
        return new ToggleSpeedMessage();
    }

    public static void handle(ToggleSpeedMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            boolean next = !CuriosEffectsState.htrueSpeedToggle(player);
            CuriosEffectsState.setHtrueSpeedToggle(player, next);
            player.sendSystemMessage(Component.translatable(
                    next ? "divinebeast.msg.htrue_speed_on" : "divinebeast.msg.htrue_speed_off"));
        });
        context.setPacketHandled(true);
    }
}
