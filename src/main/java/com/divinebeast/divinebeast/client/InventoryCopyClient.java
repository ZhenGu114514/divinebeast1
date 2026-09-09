package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.CompatChecks;
import com.divinebeast.divinebeast.net.ItemCopyMessage;
import com.divinebeast.divinebeast.net.Networking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * 真者祂 #88「造化」客户端交互：把要复制的物品放入<b>副手</b>，
 * 打开自己的物品栏（按 E），长按 Shift，每 0.1 秒向服务端发送一次复制请求
 * （服务端在脚下生成一整组，不消耗原件）。
 *
 * <p>仅在自己物品栏界面（InventoryScreen）生效，避免在他人容器界面误触发。
 */
public final class InventoryCopyClient {

    private InventoryCopyClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(InventoryCopyClient::onClientTick);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen == null) {
            return;
        }
        // 只在自己物品栏（按 E）中触发
        if (!(mc.screen instanceof InventoryScreen)) {
            return;
        }
        Player player = mc.player;
        // 服务端会再校验真者祂形态；客户端仅在 Curios 存在且确实处于该形态时才发包，减少无谓流量
        if (!CompatChecks.curiosLoaded()) {
            return;
        }
        if (!com.divinebeast.divinebeast.curio.AscensionEffects.wearingHeTrue(player)
                || com.divinebeast.divinebeast.curio.AscensionEffects.effectsDisabled(player)) {
            return;
        }
        // 长按 Shift；约每 0.1 秒（2 tick）复制一次副手中的物品
        if (!Screen.hasShiftDown()) {
            return;
        }
        if (player.tickCount % 2 != 0) {
            return;
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) {
            return;
        }
        Networking.sendToServer(new ItemCopyMessage(offhand.copy()));
    }
}
