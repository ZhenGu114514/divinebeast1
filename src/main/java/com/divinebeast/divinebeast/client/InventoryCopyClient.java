package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.CompatChecks;
import com.divinebeast.divinebeast.net.ItemCopyMessage;
import com.divinebeast.divinebeast.net.Networking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

/**
 * 真者祂 #88「造化」客户端交互：打开任意容器/背包界面，把鼠标悬停在
 * <b>自己物品栏</b>的一个物品格上，长按鼠标右键并按住 Shift，即可每 0.1 秒
 * 向服务端发送一次复制请求（服务端在脚下生成一整组，不消耗原件）。
 *
 * <p>悬停格通过反射读取 {@link AbstractContainerScreen#hoveredSlot}（无公开 getter）；
 * 触发条件还需悬停格属于玩家自己的 {@link Inventory}，避免把他人容器里的物品复制出去。
 */
public final class InventoryCopyClient {

    /** 反射句柄：AbstractContainerScreen.hoveredSlot（mojmap 1.20.1 字段名） */
    private static Field hoveredSlotField;

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
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
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
        // 长按右键 且 按住 Shift；约每 0.1 秒（2 tick）复制一次
        if (!Screen.hasShiftDown()
                || GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),
                GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS) {
            return;
        }
        if (player.tickCount % 2 != 0) {
            return;
        }
        Slot hovered = getHoveredSlot(screen);
        if (hovered == null || !hovered.hasItem()) {
            return;
        }
        // 只允许“玩家自己的物品栏/快捷栏”里的物品被复制
        if (!(hovered.container instanceof Inventory inv) || inv != player.getInventory()) {
            return;
        }
        ItemStack stack = hovered.getItem();
        if (stack.isEmpty()) {
            return;
        }
        Networking.sendToServer(new ItemCopyMessage(stack.copy()));
    }

    /** 反射读取悬停格（每次读取成本可忽略）。 */
    private static Slot getHoveredSlot(AbstractContainerScreen<?> screen) {
        try {
            if (hoveredSlotField == null) {
                hoveredSlotField = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
                hoveredSlotField.setAccessible(true);
            }
            Object slot = hoveredSlotField.get(screen);
            return slot instanceof Slot s ? s : null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
