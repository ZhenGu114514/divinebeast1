package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.boss.DivineBoss;
import net.minecraft.client.Minecraft;

/** 客户端：按实体 id 打开中立 boss 的交易界面（由 {@code OpenBossTradeMessage} 调用）。 */
public final class ClientTradeScreens {

    private ClientTradeScreens() {
    }

    public static void open(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(entityId) instanceof DivineBoss boss) {
            minecraft.setScreen(new BossTradeScreen(boss));
        }
    }
}
