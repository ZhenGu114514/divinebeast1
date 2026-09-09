package com.divinebeast.divinebeast.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：玩家在证悟阶段死亡，打开「自我救赎 / 找回自我」抉择界面。
 * kind：1=救赎阶段（自我救赎），2=本心阶段（找回自我）。
 */
public class AscensionScreenMessage {

    public static final int KIND_REDEMPTION = 1;
    public static final int KIND_TRUE_HEART = 2;

    private final int kind;

    public AscensionScreenMessage(int kind) {
        this.kind = kind;
    }

    public static void encode(AscensionScreenMessage message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.kind);
    }

    public static AscensionScreenMessage decode(FriendlyByteBuf buffer) {
        return new AscensionScreenMessage(buffer.readInt());
    }

    public static void handle(AscensionScreenMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.divinebeast.divinebeast.client.AscensionDeathClient.open(message.kind));
            }
        });
        context.setPacketHandled(true);
    }
}
