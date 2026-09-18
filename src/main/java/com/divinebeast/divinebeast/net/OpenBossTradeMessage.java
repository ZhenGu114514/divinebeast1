package com.divinebeast.divinebeast.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：打开中立 boss（我 / 兽 / 祂）的交易界面。
 * 只传实体 id，界面内容由客户端按实体自己的 {@code Kind} 推导，因此无需再同步数据。
 */
public class OpenBossTradeMessage {

    private final int entityId;

    public OpenBossTradeMessage(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(OpenBossTradeMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
    }

    public static OpenBossTradeMessage decode(FriendlyByteBuf buffer) {
        return new OpenBossTradeMessage(buffer.readVarInt());
    }

    public static void handle(OpenBossTradeMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.divinebeast.divinebeast.client.ClientTradeScreens.open(message.entityId)));
        context.setPacketHandled(true);
    }
}
