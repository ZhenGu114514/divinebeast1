package com.divinebeast.divinebeast.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务器端切换玩家 persistent NBT 中的"救赎重生"开关：
 * {@code divinebeast.respawn_toggle}，默认开启。
 * 仅影响"击败带有救赎印记的生物时是否在原地生成一个同样的生物"。
 */
public class ToggleRespawnMessage {

    public static void encode(ToggleRespawnMessage message, FriendlyByteBuf buffer) {
    }

    public static ToggleRespawnMessage decode(FriendlyByteBuf buffer) {
        return new ToggleRespawnMessage();
    }

    public static void handle(ToggleRespawnMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            boolean next = !CuriosEffectsState.respawnToggle(player);
            CuriosEffectsState.setRespawnToggle(player, next);
            player.sendSystemMessage(Component.translatable(
                    next ? "divinebeast.msg.redemption_respawn_on" : "divinebeast.msg.redemption_respawn_off"));
        });
        context.setPacketHandled(true);
    }
}
