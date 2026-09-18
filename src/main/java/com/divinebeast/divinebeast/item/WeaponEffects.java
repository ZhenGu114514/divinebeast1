package com.divinebeast.divinebeast.item;

import com.divinebeast.divinebeast.boss.LightPillar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 三个 boss 武器的被动（手持对应剑命中时结算）。
 *
 * <ul>
 *   <li><b>「我」剑</b>：<b>吸血 20%</b>（按实际造成的伤害回血）。</li>
 *   <li><b>「兽」剑</b>：给目标挂上<b>全部负面效果 3 秒</b>
 *       （遍历所有注册的负面药水效果，跳过"瞬间型"的，因为那类没有持续时间的意义）。</li>
 *   <li><b>「祂」剑</b>：命中处落下<b>光柱</b>——每 tick 10 点伤害、持续 1 秒、冷却 2 秒，
 *       并且<b>不会伤害玩家</b>（见 {@link LightPillar}，判定传 {@code hurtPlayers = false}）。</li>
 * </ul>
 */
public final class WeaponEffects {

    /** 「我」剑吸血比例（20%） */
    public static final float SELF_LIFESTEAL = 0.2F;
    /** 「兽」剑负面效果时长（3 秒） */
    public static final int BEAST_DEBUFF_TICKS = 60;
    /** 「祂」剑光柱冷却（2 秒） */
    public static final int HE_PILLAR_COOLDOWN_TICKS = 40;
    /** 「祂」剑光柱持续时间（1 秒） */
    public static final int HE_PILLAR_LIFE_TICKS = 20;
    /** 「祂」剑光柱每次结算的伤害（每 tick 10 点 × 20 tick = 200 点） */
    public static final float HE_PILLAR_DAMAGE_PER_TICK = 10.0F;

    /** 玩家 UUID → 上次落柱的游戏刻 */
    private static final Map<UUID, Long> HE_PILLAR_LAST = new HashMap<>();

    private WeaponEffects() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, WeaponEffects::onLivingDamage);
    }

    private static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player attacker) || victim.is(attacker)) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0F || event.isCanceled()) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        // 「三相」剑＝三把下级剑的效果全都要
        boolean threePhase = weapon.is(ModItems.THREE_PHASE_SWORD.get());

        if (threePhase || weapon.is(ModItems.SELF_SWORD.get())) {
            // 「我」：吸血 20%
            attacker.heal(amount * SELF_LIFESTEAL);
        }
        if (threePhase || weapon.is(ModItems.BEAST_SWORD.get())) {
            // 「兽」：目标获得全部负面效果 3 秒
            applyAllDebuffs(victim);
        }
        if (threePhase || weapon.is(ModItems.HE_SWORD.get())) {
            // 「祂」：落柱（不伤玩家），2 秒冷却
            if (!(attacker.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            long now = serverLevel.getGameTime();
            Long last = HE_PILLAR_LAST.get(attacker.getUUID());
            if (last != null && now - last < HE_PILLAR_COOLDOWN_TICKS) {
                return;
            }
            HE_PILLAR_LAST.put(attacker.getUUID(), now);
            LightPillar.strike(serverLevel, victim.position(), attacker, false, 0,
                    HE_PILLAR_LIFE_TICKS, HE_PILLAR_DAMAGE_PER_TICK, 1, false);
        }
    }

    /** 把当前注册表里所有"有持续时间的负面效果"都挂给目标（3 秒）。 */
    private static void applyAllDebuffs(LivingEntity victim) {
        for (MobEffect effect : ForgeRegistries.MOB_EFFECTS.getValues()) {
            if (effect.isBeneficial() || effect.isInstantenous()) {
                continue;
            }
            victim.addEffect(new MobEffectInstance(effect, BEAST_DEBUFF_TICKS, 0, false, false));
        }
    }
}
