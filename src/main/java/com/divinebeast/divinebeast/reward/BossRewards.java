package com.divinebeast.divinebeast.reward;

import com.divinebeast.divinebeast.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.Item;

/**
 * 猎杀获取路径（与合成/锻造并存）：
 * 末影龙 → 随机掉落 1 件「时刻」饰品；凋灵 → 随机掉落 1 件「法则」饰品。
 */
public final class BossRewards {

    private static final RegistryObject<Item>[] MOMENTS = newArray(
            ModItems.NONEXIST_NONEXIST, ModItems.NONEXIST_EXIST, ModItems.MAYBE_EXIST,
            ModItems.EXIST_EXIST, ModItems.IMPOSSIBLE_NONEXIST);
    private static final RegistryObject<Item>[] LAWS = newArray(
            ModItems.SUPREME, ModItems.WISDOM, ModItems.LIFE, ModItems.CHAOS,
            ModItems.SELF, ModItems.DEVOUR, ModItems.SAMSARA);

    private BossRewards() {
    }

    @SuppressWarnings("unchecked")
    private static RegistryObject<Item>[] newArray(RegistryObject<Item>... items) {
        return items;
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(BossRewards::onDeath);
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
        if (killer == null) {
            return;
        }
        RegistryObject<Item>[] pool = null;
        if (dead instanceof EnderDragon) {
            pool = MOMENTS;   // 末影龙 → 随机时刻
        } else if (dead instanceof WitherBoss) {
            pool = LAWS;      // 凋灵 → 随机法则
        }
        if (pool == null || pool.length == 0) {
            return;
        }
        RegistryObject<Item> chosen = pool[level.random.nextInt(pool.length)];
        level.addFreshEntity(new ItemEntity(level, dead.getX(), dead.getY() + 0.5D, dead.getZ(),
                new ItemStack(chosen.get())));
    }
}
