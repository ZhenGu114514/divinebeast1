package com.divinebeast.divinebeast.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：#88「造化」——背包悬停格持续复制。
 *
 * <p>「造化」复制权能已按需求移除（改为：挖掘/击杀掉落的物品与经验 ×10，见
 * {@code HeTrueEffects}）。保留此消息类与通道注册以避免改动网络 ID，但服务端
 * {@link #handle} 一律空转、不再生成复制品。
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
            // #88「造化」复制权能已按需求移除（改为：挖掘/击杀掉落的物品与经验 ×10）。
            // 保留网络消息与寄存器以避免改动通道 ID，但服务端一律不再生成复制品。
        });
        context.setPacketHandled(true);
    }
}
