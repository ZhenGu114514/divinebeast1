package com.divinebeast.divinebeast.reward;

import com.divinebeast.divinebeast.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * 证悟系获取：玩家击杀的"第 1 只中立生物"掉落『悲』；"第 1 只敌对生物"掉落『伤』（各一次）。
 */
public final class FirstKillDrops {

    private static final String TAG_GRIEF = "divinebeast.fk_grief";
    private static final String TAG_PAIN = "divinebeast.fk_pain";

    private FirstKillDrops() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(FirstKillDrops::onDeath);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Entity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        Player killer = null;
        Entity source = event.getSource().getEntity();
        if (source instanceof Player player) {
            killer = player;
        }
        if (killer == null || dead instanceof Player) {
            return;
        }
        var tag = killer.getPersistentData();
        if (dead instanceof Monster) {
            if (!tag.getBoolean(TAG_PAIN)) {
                tag.putBoolean(TAG_PAIN, true);
                drop(level, dead, new ItemStack(ModItems.PAIN.get()));   // 伤
            }
        } else {
            // 中立/被动生物（非敌对、非玩家）
            if (!tag.getBoolean(TAG_GRIEF)) {
                tag.putBoolean(TAG_GRIEF, true);
                drop(level, dead, new ItemStack(ModItems.GRIEF.get()));  // 悲
            }
        }
    }

    private static void drop(ServerLevel level, Entity origin, ItemStack stack) {
        level.addFreshEntity(new ItemEntity(level, origin.getX(), origin.getY() + 0.5D, origin.getZ(), stack));
    }
}
