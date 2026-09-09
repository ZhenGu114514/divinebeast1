package com.divinebeast.divinebeast.net;

import com.divinebeast.divinebeast.CompatChecks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：#88「造化」——背包悬停格持续复制。
 * 客户端在背包界面悬停一个玩家背包物品格、按住右键并按住 Shift 时，
 * 每 0.1 秒发送一次本消息；服务端校验真者祂形态后，在玩家脚底生成
 * 一整组（可堆叠取最大堆叠数，不可堆叠为 1 个）该物品的复制品，不消耗原件。
 */
public class ItemCopyMessage {

    private final ItemStack stack;

    public ItemCopyMessage(ItemStack stack) {
        this.stack = stack;
    }

    public static void encode(ItemCopyMessage message, FriendlyByteBuf buffer) {
        buffer.writeItem(message.stack);
    }

    public static ItemCopyMessage decode(FriendlyByteBuf buffer) {
        return new ItemCopyMessage(buffer.readItem());
    }

    public static void handle(ItemCopyMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || message.stack == null || message.stack.isEmpty()) {
                return;
            }
            // 无 Curios 环境没有真者祂形态，直接忽略（不在此处解析 Curios 类）
            if (!CompatChecks.curiosLoaded()) {
                return;
            }
            if (!com.divinebeast.divinebeast.curio.AscensionEffects.wearingHeTrue(player)
                    || com.divinebeast.divinebeast.curio.AscensionEffects.effectsDisabled(player)) {
                return;
            }
            // 只复制背包中真实存在的物品类型（防凭空刷出未知物品）
            boolean has = false;
            for (ItemStack s : player.getInventory().items) {
                if (!s.isEmpty() && s.is(message.stack.getItem())) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                return;
            }
            ItemStack copy = message.stack.copy();
            copy.setCount(copy.getMaxStackSize());
            if (player.level() instanceof ServerLevel level) {
                level.addFreshEntity(new ItemEntity(level, player.getX(),
                        player.getY() + 0.2D, player.getZ(), copy));
            }
        });
        context.setPacketHandled(true);
    }
}
