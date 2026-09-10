package com.divinebeast.divinebeast.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务器端切换玩家 persistent NBT 中的"真者祂 · 10 光之领域"范围伤害开关：
 * {@code divinebeast.htrue_beacon_toggle}，默认开启（V 键）。
 * 关闭后，真者祂引擎不再每秒对周围敌对生物造成范围伤害。
 */
public class ToggleBeaconMessage {

    public static void encode(ToggleBeaconMessage message, FriendlyByteBuf buffer) {
    }

    public static ToggleBeaconMessage decode(FriendlyByteBuf buffer) {
        return new ToggleBeaconMessage();
    }

    public static void handle(ToggleBeaconMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            boolean next = !CuriosEffectsState.htrueBeaconToggle(player);
            CuriosEffectsState.setHtrueBeaconToggle(player, next);
            player.sendSystemMessage(Component.translatable(
                    next ? "divinebeast.msg.htrue_beacon_on" : "divinebeast.msg.htrue_beacon_off"));
        });
        context.setPacketHandled(true);
    }
}
