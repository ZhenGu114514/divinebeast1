package com.divinebeast.divinebeast.net;

import com.divinebeast.divinebeast.CompatChecks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：抉择界面按钮。
 * kind：0=普通重生（不迁移）；1=自我救赎；2=找回自我。
 */
public class AscensionChoiceMessage {

    private final int kind;

    public AscensionChoiceMessage(int kind) {
        this.kind = kind;
    }

    public static void encode(AscensionChoiceMessage message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.kind);
    }

    public static AscensionChoiceMessage decode(FriendlyByteBuf buffer) {
        return new AscensionChoiceMessage(buffer.readInt());
    }

    public static void handle(AscensionChoiceMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (CompatChecks.curiosLoaded()) {
                // 只在 Curios 存在时才解析 AscensionEffects（避免无 Curios 环境类加载错误）
                com.divinebeast.divinebeast.curio.AscensionEffects.handleChoice(player, message.kind);
            } else if (message.kind == 0) {
                AscensionEffectsNoCurios.revive(player);
            }
        });
        context.setPacketHandled(true);
    }
}
