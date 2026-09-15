package com.divinebeast.divinebeast.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务器端切换玩家 persistent NBT 中的「真者祂 · 神威·诛灭」开关：
 * {@code divinebeast.htrue_kill_toggle}，默认开启（B 键）。
 *
 * <p>关闭后，真者祂的攻击只保留原有的数值与真伤结算；
 * 开启时命中会依次施加另外 5 种非数值型手段（真伤穿透 / 多段 / 范围 /
 * 逻辑致死 / 兜底抹除），详见 {@code HeTrueEffects#applyExecutionChain}。
 */
public class ToggleKillMessage {

    public static void encode(ToggleKillMessage message, FriendlyByteBuf buffer) {
    }

    public static ToggleKillMessage decode(FriendlyByteBuf buffer) {
        return new ToggleKillMessage();
    }

    public static void handle(ToggleKillMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            boolean next = !CuriosEffectsState.htrueKillToggle(player);
            CuriosEffectsState.setHtrueKillToggle(player, next);
            player.sendSystemMessage(Component.translatable(
                    next ? "divinebeast.msg.htrue_kill_on" : "divinebeast.msg.htrue_kill_off"));
        });
        context.setPacketHandled(true);
    }
}
