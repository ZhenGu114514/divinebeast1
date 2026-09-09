package com.divinebeast.divinebeast.curio;

import com.divinebeast.divinebeast.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;

import java.util.List;
import java.util.UUID;

/**
 * 真者『祂』——证悟终点的终极形态引擎（仅 Curios 存在时由 DivineBeastMod 加载）。
 *
 * <p>佩戴（绑定不可卸下）即获得 20 项正面权能：
 * 太初/不朽/永恒之翼/无相/净世/磐石/全知之眼/神能/天罚/光之领域/
 * 血之回响/神行/命泉/不灭战意/磁界/威慑/归墟/界缚/不朽之器/神之饱足。
 *
 * <p>实现以"每服务端 tick 维持 + 事件豁免"为主，数值取最强口径；
 * 全部效果只在 {@link AscensionEffects#wearingHeTrue} 时生效。
 */
public final class HeTrueEffects {

    // 属性修饰符固定 UUID
    private static final UUID DAMAGE_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b1");
    private static final UUID SPEED_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b2");
    private static final UUID ATTACK_SPEED_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b3");
    private static final UUID KNOCKBACK_RES_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b4");
    private static final UUID MAX_HEALTH_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b5");

    /** 天罚追加真伤防递归标记 */
    private static boolean applyingTrueDamage = false;
    /** 光柱范围（格）——真者祂光之领域 */
    private static final double BEACON_RADIUS = 16.0D;
    /** 磁界拉取范围（格） */
    private static final double MAGNET_RADIUS = 24.0D;
    /** 当前处于真者祂形态的玩家（用于离开形态时精确回收本引擎效果） */
    private static final java.util.Set<java.util.UUID> ACTIVE = new java.util.HashSet<>();

    private HeTrueEffects() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onLivingDamage);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onDeath);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onEffectAdded);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onKnockBack);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onFall);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onTargetChange);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onXpChange);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onTeleportCommand);
    }

    private static boolean wearing(Player player) {
        // 与祂者初/祂者极一致：若佩戴 救赎/本心（完全封印），真者祂 的效果同样失效
        return player != null && AscensionEffects.wearingHeTrue(player)
                && !AscensionEffects.effectsDisabled(player);
    }

    // ==================================================================
    // 每 tick：维持无敌态（1/2/3/4/5/6/7/8/10/12/13/17/19/20）
    // ==================================================================

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (!wearing(player)) {
            // 仅回收之前确由本引擎开启的效果，绝不触碰其他玩家的飞行/状态
            if (ACTIVE.remove(player.getUUID())) {
                removeMods(player);
            }
            return;
        }
        ACTIVE.add(player.getUUID());
        // ---- 1 太初 / 17 归墟：虚空拉回（伤害免疫在 onLivingDamage）----
        if (player.getY() < player.level().getMinBuildHeight() - 4) {
            player.teleportTo(player.getX(), player.level().getMinBuildHeight() + 4, player.getZ());
        }
        // ---- 3 永恒之翼：创造式飞行 ----
        if (!player.isCreative() && !player.isSpectator()) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                if (player instanceof ServerPlayer sp) {
                    sp.onUpdateAbilities();
                }
            }
        }
        // ---- 4 无相 ----
        if (!player.isInvisible()) {
            player.setInvisible(true);
        }
        // ---- 5 净世：清负面（免疫在 onEffectAdded）----
        List<MobEffectInstance> effects = new java.util.ArrayList<>(player.getActiveEffects());
        for (MobEffectInstance effect : effects) {
            if (!effect.getEffect().isBeneficial()) {
                player.removeEffect(effect.getEffect());
            }
        }
        // ---- 17 归墟：灭火 + 补空气 ----
        player.clearFire();
        if (player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        // ---- 2 不朽 / 13 命泉：生命与吸收回满（已满不重复写，减少网络同步）----
        if (player.getHealth() < player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        if (player.getAbsorptionAmount() < 40.0F) {
            player.setAbsorptionAmount(40.0F);
        }
        // ---- 7 全知之眼：夜视 ----
        if (player.tickCount % 100 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
        }
        // ---- 万法之域：常驻增益族（力量/抗性/跳跃/幸运/再生/水下呼吸/防火，时长到期前补发）----
        if (player.tickCount % 80 == 0) {
            refreshBuff(player, MobEffects.DAMAGE_BOOST, 5, 320);
            refreshBuff(player, MobEffects.DAMAGE_RESISTANCE, 4, 320);
            refreshBuff(player, MobEffects.JUMP, 5, 320);
            refreshBuff(player, MobEffects.LUCK, 5, 320);
            refreshBuff(player, MobEffects.REGENERATION, 4, 320);
            refreshBuff(player, MobEffects.WATER_BREATHING, 0, 320);
            refreshBuff(player, MobEffects.FIRE_RESISTANCE, 0, 320);
            refreshBuff(player, MobEffects.MOVEMENT_SPEED, 3, 320);
        }
        // ---- 20 神之饱足 ----
        if (player.getFoodData().getFoodLevel() < 20) {
            player.getFoodData().setFoodLevel(20);
        }
        if (player.getFoodData().getSaturationLevel() < 10.0F) {
            player.getFoodData().setSaturation(10.0F);
        }
        // ---- 8 神能 / 12 神行 / 6 磐石 / 23 无量之躯：属性强化（幂等覆盖）----
        ensureAttribute(player, Attributes.ATTACK_DAMAGE, DAMAGE_MOD, "divinebeast_he_true_dmg", 5000.0D);
        ensureAttribute(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MOD, "divinebeast_he_true_atk_speed", 60.0D);
        ensureAttribute(player, Attributes.MOVEMENT_SPEED, SPEED_MOD, "divinebeast_he_true_speed", 2.4D);
        ensureAttribute(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RES_MOD,
                "divinebeast_he_true_kb", 1.0D);
        ensureAttribute(player, Attributes.MAX_HEALTH, MAX_HEALTH_MOD,
                "divinebeast_he_true_maxhp", 2000.0D);

        if (player.tickCount % 20 != 0) {
            return;
        }
        // ---- 10 光之领域（每秒）----
        float beaconDmg = 50.0F + player.experienceLevel;
        for (Monster mob : player.level().getEntitiesOfClass(Monster.class,
                AABB.ofSize(player.position(), BEACON_RADIUS * 2, BEACON_RADIUS * 2, BEACON_RADIUS * 2))) {
            if (mob.isAlive() && !mob.is(player)) {
                mob.hurt(mob.damageSources().playerAttack(player), beaconDmg);
            }
        }
        // ---- 7 全知之眼：察觉众生（发光标记，避免使用不确定的实体 flag API）----
        if (player.tickCount % 100 == 0) {
            for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(player.position(), 48, 48, 48))) {
                if (living.isAlive() && !living.is(player)
                        && living.getEffect(MobEffects.GLOWING) == null) {
                    living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
                }
            }
        }
        // ---- 15 磁界：拉近物品与经验 ----
        if (player.tickCount % 10 == 0) {
            AABB box = AABB.ofSize(player.position(), 16, 16, 16);
            for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, box)) {
                item.setPickUpDelay(0);
                item.setPos(player.getX(), player.getY() + 0.5D, player.getZ());
            }
            for (ExperienceOrb orb : player.level().getEntitiesOfClass(ExperienceOrb.class, box)) {
                orb.setPos(player.getX(), player.getY() + 0.5D, player.getZ());
            }
        }
        // ---- 19 不朽之器：修复装备与手持物 ----
        repairAll(player);
    }

    private static void ensureAttribute(Player player,
                                        net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                        UUID uuid, String name, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (instance.getModifier(uuid) == null) {
            instance.addPermanentModifier(new AttributeModifier(uuid, name, amount,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    /** 仅在增益不足时补发，避免频繁 addEffect 覆盖计时。 */
    private static void refreshBuff(Player player, net.minecraft.world.effect.MobEffect effect,
                                    int amplifier, int ticks) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < 60) {
            player.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, false));
        }
    }

    private static void removeMods(Player player) {
        for (UUID uuid : new UUID[]{DAMAGE_MOD, SPEED_MOD, ATTACK_SPEED_MOD, KNOCKBACK_RES_MOD,
                MAX_HEALTH_MOD}) {
            AttributeInstance instance = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (instance != null) {
                instance.removeModifier(uuid);
            }
            instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (instance != null) {
                instance.removeModifier(uuid);
            }
            instance = player.getAttribute(Attributes.ATTACK_SPEED);
            if (instance != null) {
                instance.removeModifier(uuid);
            }
            instance = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (instance != null) {
                instance.removeModifier(uuid);
            }
            instance = player.getAttribute(Attributes.MAX_HEALTH);
            if (instance != null) {
                instance.removeModifier(uuid);
            }
        }
        for (net.minecraft.world.effect.MobEffect effect : new net.minecraft.world.effect.MobEffect[]{
                MobEffects.DAMAGE_BOOST, MobEffects.DAMAGE_RESISTANCE, MobEffects.JUMP,
                MobEffects.LUCK, MobEffects.REGENERATION, MobEffects.WATER_BREATHING,
                MobEffects.FIRE_RESISTANCE, MobEffects.MOVEMENT_SPEED}) {
            if (player.getEffect(effect) != null
                    && player.getEffect(effect).getAmplifier() >= 3) {
                player.removeEffect(effect);
            }
        }
        if (player.getAbilities().mayfly) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            if (player instanceof ServerPlayer sp) {
                sp.onUpdateAbilities();
            }
        }
        player.setInvisible(false);
        player.setAbsorptionAmount(0.0F);
    }

    private static void repairAll(Player player) {
        java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().armor);
        stacks.add(player.getOffhandItem());
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() > 0) {
                stack.setDamageValue(0);
            }
        }
    }

    // ==================================================================
    // 事件
    // ==================================================================

    /** 1 太初（含 17 归墟 的环境伤害）/ 9 天罚 / 11 血之回响 */
    private static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || applyingTrueDamage) {
            return;
        }
        LivingEntity victim = event.getEntity();
        // 太初：佩戴者不受任何伤害
        if (victim instanceof Player player && wearing(player)) {
            event.setCanceled(true);
            return;
        }
        // 攻击来源解析
        Player attacker = null;
        Entity sourceEntity = event.getSource().getEntity();
        if (sourceEntity instanceof Player p) {
            attacker = p;
        } else if (event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof Player p2) {
            attacker = p2;
        }
        if (attacker == null || !wearing(attacker) || victim.is(attacker)) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }
        // 11 血之回响：全额回血
        attacker.heal(amount);
        // 圣火灼烧 + 迟滞领域：命中目标着火并减速（仅对敌对生物）
        if (victim instanceof Monster && victim.isAlive()) {
            victim.setSecondsOnFire(3);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 4, false, false));
        }
        // 9 天罚：等额无视护甲真伤
        applyingTrueDamage = true;
        try {
            victim.hurt(victim.damageSources().genericKill(), amount);
        } finally {
            applyingTrueDamage = false;
        }
    }

    /** 2 不朽：死亡无效 */
    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player && wearing(player)) {
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
            player.setAbsorptionAmount(40.0F);
            player.setInvulnerable(false);
            player.removeAllEffects();
        }
    }

    /** 5 净世：负面效果禁止上身 */
    private static void onEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player && wearing(player)
                && event.getEffectInstance() != null
                && !event.getEffectInstance().getEffect().isBeneficial()) {
            event.setCanceled(true);
        }
    }

    /** 6 磐石：免疫击退 */
    private static void onKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player && wearing(player)) {
            event.setCanceled(true);
        }
    }

    /** 3 永恒之翼：无摔落伤害 */
    private static void onFall(LivingFallEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player && wearing(player)) {
            event.setCanceled(true);
        }
    }

    /** 16 威慑：敌对生物不再以你为目标 */
    private static void onTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getNewTarget() instanceof Player player && wearing(player)) {
            event.setCanceled(true);
        }
    }

    /** 14 不灭战意：经验获取 ×10 */
    private static void onXpChange(PlayerXpEvent.XpChange event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide || !wearing(player)) {
            return;
        }
        int amount = event.getAmount();
        event.setAmount(amount * 10);
    }

    /** 18 界缚：禁止一切 /tp 及传送指令影响你 */
    private static void onTeleportCommand(EntityTeleportEvent.TeleportCommand event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player && wearing(player)) {
            event.setCanceled(true);
        }
    }
}
