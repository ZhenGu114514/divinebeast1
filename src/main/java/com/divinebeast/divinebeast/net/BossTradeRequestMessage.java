package com.divinebeast.divinebeast.net;

import com.divinebeast.divinebeast.boss.BossTrade;
import com.divinebeast.divinebeast.boss.DivineBoss;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：在交易界面里点了第 {@code index} 项。
 * {@code index == 0} = 主交易（16 货币 → 3 方块），{@code index == 1} = 『兽』的喂食。
 */
public class BossTradeRequestMessage {

    private final int entityId;
    private final int index;

    public BossTradeRequestMessage(int entityId, int index) {
        this.entityId = entityId;
        this.index = index;
    }

    public static void encode(BossTradeRequestMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
        buffer.writeVarInt(message.index);
    }

    public static BossTradeRequestMessage decode(FriendlyByteBuf buffer) {
        return new BossTradeRequestMessage(buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(BossTradeRequestMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (player.level().getEntity(message.entityId) instanceof DivineBoss boss) {
                if (message.index == 1) {
                    BossTrade.performFeed(boss, player);
                } else {
                    BossTrade.performTrade(boss, player);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
