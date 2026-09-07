package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.CompatChecks;
import com.divinebeast.divinebeast.curio.CuriosEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * 『祂』救赎 常驻（客户端辅助）：长按左键（攻击键）时——
 * 近战/武器自动挥舞；弓箭/弩在瞄准目标时自动蓄力并自动发射。
 * 仅在 Curios 已安装且本地玩家处于救赎阶段时工作。
 */
public final class AutoCombatClient {

    private AutoCombatClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(AutoCombatClient::onClientTick);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !CompatChecks.curiosLoaded()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        if (player == null || gameMode == null || minecraft.level == null) {
            return;
        }
        if (!minecraft.options.keyAttack.isDown()) {
            return;
        }
        if (!CuriosEffects.isPhaseTwo(player)) {
            return;
        }

        // 瞄准目标（十字准星命中的实体）
        HitResult hitResult = minecraft.hitResult;
        Entity target = hitResult instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;

        ItemStack held = player.getMainHandItem();
        boolean bow = held.is(Items.BOW);
        boolean crossbow = held.is(Items.CROSSBOW);

        // 弓 / 弩：自动蓄力并在蓄满后自动发射（需对准目标）
        if ((bow || crossbow) && player.isAlive() && !player.isUsingItem()) {
            if (target != null || bow) {
                gameMode.useItem(player, InteractionHand.MAIN_HAND);
            }
        }
        if ((bow || crossbow) && player.isUsingItem()) {
            int chargeTicks = crossbow ? 25 : 20;
            if (player.getTicksUsingItem() >= chargeTicks) {
                gameMode.releaseUsingItem(player);
            }
        }

        // 近战 / 武器：冷却就绪时自动攻击瞄准的实体（玩家互不自动攻击）
        if (target != null && target.isAlive() && !(target instanceof Player)) {
            double reach = 4.0D;
            if (player.distanceToSqr(target) <= reach * reach && player.getAttackStrengthScale(0.0F) >= 0.85F) {
                gameMode.attack(player, target);
                player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }
}
