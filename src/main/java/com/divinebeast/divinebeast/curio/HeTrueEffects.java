package com.divinebeast.divinebeast.curio;

import com.divinebeast.divinebeast.net.CuriosEffectsState;
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
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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

import java.util.UUID;

/**
 * 真者『祂』——证悟终点的终极形态引擎（仅 Curios 存在时由 DivineBeastMod 加载）。
 *
 * <p>佩戴（绑定不可卸下）即获得 20 项正面权能：
 * 太初/不朽/永恒之翼/万法附魔/净世/磐石/全知之眼/神能/天罚/光之领域/
 * 血之回响/神行/命泉/不灭战意/磁界/威慑/归墟/界缚/不朽之器/神之饱足。
 *
 * <p>本版按需求改写（均为"替换其中一个效果"）：
 * <ul>
 *   <li>11 血之回响 → <b>伤害 × 当前生命值</b>（有多少点生命就乘多少）；</li>
 *   <li>76 旧伤 → <b>22 神威：攻击力 +1 億</b>；</li>
 *   <li>圣火灼烧＋迟滞领域 → <b>斥力场：弹开一切非自身的远程弹射物</b>；</li>
 *   <li>1 太初 / 17 归墟 → <b>免疫虚空伤害</b>（不再"从虚空中拉回"）；
 *       且 17 归墟 追加一段<b>与本次伤害等值的虚空伤害</b>；</li>
 *   <li>8 神能 攻击速度 → <b>+1000</b>；</li>
 *   <li>6 磐石 击退抗性 → <b>+100</b>。</li>
 * </ul>
 *
 * <p>原 #88「造化」复制权能已按需求移除，改为：真者祂击杀任意生物时掉落物品×10、
 * 破坏方块时掉落经验×10（方块物品掉落 1.20.1 Forge 的 BreakEvent 无挂点，故无法×10）。
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
    private static final UUID FLYING_SPEED_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b6");
    /** 万藏：通用(curio)槽固定修饰符 UUID */
    private static final UUID CURIO_HOARD_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b7");
    /** 神威（由 76 旧伤 改写）：攻击力 +1 億 的固定修饰符 UUID */
    private static final UUID MIGHT_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b8");
    /**
     * 神行移速倍率（MULTIPLY_TOTAL 增量）。1.0 = +100%，即移速 ×2，
     * 与原版「速度 V」等价（药水每级 +20%，V 级 = amp4 = +100%）。
     */
    private static final double STRIDE_SPEED_MULTIPLIER = 1.0D;
    /** 神行 X 开关开启时，飞行能力速度放大系数（相对玩家原 flyingSpeed）：×2 ≈ 速度 V */
    private static final double STRIDE_FLY_BOOST = 2.0D;
    /** 记录每个玩家原始的 Abilities#flyingSpeed，用于 X 关闭/脱离形态时还原 */
    private static final java.util.Map<java.util.UUID, Float> STRIDE_ORIG_FLY = new java.util.HashMap<>();
    /** 太初：玩家 → [自然秒 id, 该秒已累计伤害]，每秒最多 1 点 */
    private static final java.util.Map<java.util.UUID, double[]> SEC_DAMAGE = new java.util.HashMap<>();
    /** 斥力场（由 圣火灼烧＋迟滞领域 改写）作用半径：AABB 边长（格） */
    private static final double REPEL_RADIUS = 12.0D;
    /** 斥力场把弹射物弹开的速度（格/tick） */
    private static final double REPEL_SPEED = 2.0D;

    /** 天罚追加真伤防递归标记 */
    private static boolean applyingTrueDamage = false;
    /** 光柱范围（格）——真者祂光之领域 */
    private static final double BEACON_RADIUS = 16.0D;
    /** 磁界拉取范围（格，AABB 边长） */
    private static final double MAGNET_RADIUS = 16.0D;
    /** 当前处于真者祂形态的玩家（用于离开形态时精确回收本引擎效果） */
    private static final java.util.Set<java.util.UUID> ACTIVE = new java.util.HashSet<>();

    private HeTrueEffects() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onLivingDamage);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onDeath);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onEffectApplicable);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onKnockBack);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onFall);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onTargetChange);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onXpChange);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onTeleportCommand);
        // 造化（复制）已移除 → 挖掘/击杀掉落 ×10
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onLivingDrops);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onBlockBreak);
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
        // X 键开关：神行 行/飞/游 移速加成（默认开，见 CuriosEffectsState）
        boolean strideSpeed = CuriosEffectsState.htrueSpeedToggle(player);
        // ---- 1 太初 / 17 归墟：免疫虚空伤害（不再"从虚空中拉回"，拦截见 onLivingDamage）----
        // 注：改为免疫后不再传送回安全高度；若坠入虚空会持续下坠，需自行飞回（本形态自带创造式飞行）。
        // ---- 3 永恒之翼：创造式飞行 ----
        if (!player.isCreative() && !player.isSpectator()) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                if (player instanceof ServerPlayer sp) {
                    sp.onUpdateAbilities();
                }
            }
        }
        // ---- 4 无相 → 万法附魔：穿戴装备 + 背包内可附魔物品全部获得可附上的正面附魔（无视冲突）----
        // 隐身效果已按需求移除。附魔在下方每 20 tick 幂等刷新写入。
        // ---- 5 净世：清负面（免疫在 onEffectApplicable）----
        clearNegativeEffects(player);
        // ---- 17 归墟：灭火 + 补空气 ----
        player.clearFire();
        if (player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        // ---- 2 不朽 / 13 命泉：生命与吸收回满 ----
        restoreVitals(player);
        // ---- 7 全知之眼：夜视 ----
        if (player.tickCount % 100 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
        }
        // ---- 万法之域：常驻增益族（时长到期前补发）----
        // 去重说明：力量/移速/再生/水下呼吸 原先同时用"药水"和"属性/直接写入"两条渠道，
        // 数值上完全被后者覆盖（ATTACK_DAMAGE +5000、MOVEMENT_SPEED +2.4、
        // 每 tick setHealth(max)、每 tick setAirSupply(max)），故不再重复给药水。
        // 防火保留：FIRE_RESISTANCE 额外降低岩浆伤害，是 clearFire() 覆盖不到的。
        if (player.tickCount % 80 == 0) {
            refreshBuff(player, MobEffects.DAMAGE_RESISTANCE, 4, 320);
            refreshBuff(player, MobEffects.JUMP, 5, 320);
            refreshBuff(player, MobEffects.LUCK, 5, 320);
            refreshBuff(player, MobEffects.FIRE_RESISTANCE, 0, 320);
            // 89 摧岳：极高挖掘速度（高等级急迫 = 高倍率，非创造式瞬破）
            refreshBuff(player, MobEffects.DIG_SPEED, 200, 320);
        }
        // ---- 20 神之饱足 ----
        if (player.getFoodData().getFoodLevel() < 20) {
            player.getFoodData().setFoodLevel(20);
        }
        if (player.getFoodData().getSaturationLevel() < 10.0F) {
            player.getFoodData().setSaturation(10.0F);
        }
        // ---- 8 神能 / 12 神行（行/飞/泳同速）/ 6 磐石 / 23 无量之躯：属性强化（幂等覆盖）----
        ensureAttribute(player, Attributes.ATTACK_DAMAGE, DAMAGE_MOD, "divinebeast_he_true_dmg", 5000.0D);
        // 22 神威（由 76 旧伤 改写）：攻击力 +1 億
        ensureAttribute(player, Attributes.ATTACK_DAMAGE, MIGHT_MOD, "divinebeast_he_true_might", 1.0E8D);
        ensureAttribute(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MOD, "divinebeast_he_true_atk_speed", 1000.0D);
        // 行走 / 飞行 / 游泳移速：X 键关闭时不维持并回收。
        // 行走/游泳读 MOVEMENT_SPEED；创造式飞行的水平速度由 Abilities#flyingSpeed 提供，
        // 因此开关须同时改写能力字段并通过 onUpdateAbilities 同步（否则飞行吃不到加成）。
        if (strideSpeed) {
            // 手感目标 ≈ 原版「速度 V」：改用 MULTIPLY_TOTAL 倍率（×2），
            // 原先用的是 ADDITION +2.4（≈基础 0.1 的 25 倍，过快）。
            ensureAttributeMultiplier(player, Attributes.MOVEMENT_SPEED, SPEED_MOD,
                    "divinebeast_he_true_speed", STRIDE_SPEED_MULTIPLIER);
            // 注：generic.flying_speed 对"玩家的创造式飞行"无效 —— 玩家飞行的水平速度走
            // Abilities#flyingSpeed（见 applyStrideAbilities 的 ×2 改写）。此处仍保留该
            // 属性修饰符，仅为兼容"自行读取 generic.flying_speed"的其它模组，不影响原版手感。
            ensureAttributeMultiplier(player, Attributes.FLYING_SPEED, FLYING_SPEED_MOD,
                    "divinebeast_he_true_fly_speed", STRIDE_SPEED_MULTIPLIER);
            applyStrideAbilities(player, true);
        } else {
            removeAttributeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MOD);
            removeAttributeModifier(player, Attributes.FLYING_SPEED, FLYING_SPEED_MOD);
            applyStrideAbilities(player, false);
        }
        // 注意：原版击退计算为 strength *= (1 - 击退抗性)，抗性 >1 会把击退"反向放大"。
        // 真正保证"完全免疫击退"的是 onKnockBack 里的事件取消；此属性按需求设为 +100。
        ensureAttribute(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RES_MOD,
                "divinebeast_he_true_kb", 100.0D);
        ensureAttribute(player, Attributes.MAX_HEALTH, MAX_HEALTH_MOD,
                "divinebeast_he_true_maxhp", 2000.0D);
        // 蹈水履火：可于水面与岩浆表面行走（不深潜/不飞行时生效）
        walkOnLiquids(player);
        // 万藏：动态通用(curio)槽 = 99 + 已装通用饰品件数
        syncCurioHoard(player);

        // 以下按各自周期执行。原先这里是一个统一的 `if (tickCount % 20 != 0) return;`，
        // 会把周期更细的磁界（%10）一并吞掉，使其实际只每 20 tick 跑一次。
        // ---- 万法附魔：穿戴装备 + 背包可附魔物品全部得到可附上的正面附魔（无视冲突），永久写入（每秒）----
        if (player.tickCount % 20 == 0) {
            applyDivineEnchantments(player);
        }
        // ---- 10 光之领域（每秒）----
        if (player.tickCount % 20 == 0) {
            float beaconDmg = 50.0F + player.experienceLevel;
            for (LivingEntity mob : player.level().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(player.position(), BEACON_RADIUS * 2, BEACON_RADIUS * 2, BEACON_RADIUS * 2))) {
                // 敌对单位（Monster 与 末影龙等 Enemy 生物，末影龙不是 Monster）
                if (mob instanceof net.minecraft.world.entity.monster.Enemy
                        && mob.isAlive() && !mob.is(player)) {
                    mob.hurt(mob.damageSources().playerAttack(player), beaconDmg);
                }
            }
        }
        // ---- 7 全知之眼：察觉众生（每 5 秒）----
        if (player.tickCount % 100 == 0) {
            for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(player.position(), 48, 48, 48))) {
                if (living.isAlive() && !living.is(player)
                        && living.getEffect(MobEffects.GLOWING) == null) {
                    living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
                }
            }
        }
        // ---- 15 磁界：拉近物品与经验（每 10 tick）----
        if (player.tickCount % 10 == 0) {
            AABB box = AABB.ofSize(player.position(), MAGNET_RADIUS, MAGNET_RADIUS, MAGNET_RADIUS);
            for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, box)) {
                item.setPickUpDelay(0);
                item.setPos(player.getX(), player.getY() + 0.5D, player.getZ());
            }
            for (ExperienceOrb orb : player.level().getEntitiesOfClass(ExperienceOrb.class, box)) {
                orb.setPos(player.getX(), player.getY() + 0.5D, player.getZ());
            }
        }
        // ---- 19 不朽之器：修复装备与手持物（每秒）----
        if (player.tickCount % 20 == 0) {
            repairAll(player);
        }
        // ---- 斥力场（由 圣火灼烧＋迟滞领域 改写）：弹开一切"非自身"的远程弹射物 ----
        if (player.tickCount % 2 == 0) {
            repelProjectiles(player);
        }
    }

    /**
     * 斥力（由「圣火灼烧＋迟滞领域」改写）：把半径内**除自己发射之外**的所有弹射物向外弹开。
     * 覆盖骷髅的箭、凋灵的凋零头颅、烈焰人的烈焰弹、雪球、三叉戟等一切 {@code Projectile}。
     * 通过"清零速度 + push 向外"实现，避免只是减速。
     */
    private static void repelProjectiles(Player player) {
        for (net.minecraft.world.entity.projectile.Projectile proj
                : player.level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                        AABB.ofSize(player.position(), REPEL_RADIUS, REPEL_RADIUS, REPEL_RADIUS))) {
            if (!proj.isAlive() || proj.getOwner() == player) {
                continue; // 除自身（自己射出的）之外
            }
            net.minecraft.world.phys.Vec3 away = proj.position().subtract(player.position());
            if (away.lengthSqr() < 1.0E-4D) {
                away = new net.minecraft.world.phys.Vec3(0.0D, 1.0D, 0.0D); // 重叠时向上弹开
            } else {
                away = away.normalize();
            }
            proj.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            proj.push(away.x * REPEL_SPEED, away.y * REPEL_SPEED, away.z * REPEL_SPEED);
        }
    }

    /**
     * 2 不朽 / 13 命泉：生命与吸收回满（已满不重复写，减少网络同步）。
     * 原先这段逻辑在"每 tick 维持"和"死亡拦截"两处各写了一遍，现统一在此。
     */
    private static void restoreVitals(Player player) {
        if (player.getHealth() < player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        if (player.getAbsorptionAmount() < 40.0F) {
            player.setAbsorptionAmount(40.0F);
        }
    }

    /**
     * 5 净世：清除身上残留的负面效果，**保留正面增益**。
     * 与 {@code removeAllEffects()} 的区别：后者会把万法之域/夜视等自家增益一起清掉，
     * 下个 tick 又被 refreshBuff 补回，形成"清了又补"的抖动。
     */
    private static void clearNegativeEffects(Player player) {
        for (MobEffectInstance effect : new java.util.ArrayList<>(player.getActiveEffects())) {
            if (!effect.getEffect().isBeneficial()) {
                player.removeEffect(effect.getEffect());
            }
        }
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

    /**
     * 以 {@code MULTIPLY_TOTAL}（与原版速度药水同款运算）维持某属性的倍率修饰符。
     * {@code multiplyTotal = 1.0} 表示 +100%（×2）。
     *
     * <p>若已存在的同 UUID 修饰符"运算方式或数值"不符（例如旧版本留下的是 ADDITION +2.4），
     * 会先移除再按新值重加，保证老存档也能收敛到新倍率。
     */
    private static void ensureAttributeMultiplier(Player player,
                                                  net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                                  UUID uuid, String name, double multiplyTotal) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(uuid);
        if (existing != null
                && existing.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL
                && Math.abs(existing.getAmount() - multiplyTotal) < 1.0E-6D) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(uuid);
        }
        instance.addPermanentModifier(new AttributeModifier(uuid, name, multiplyTotal,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** 按 UUID 移除属性修饰符（不存在时无副作用）。 */
    private static void removeAttributeModifier(Player player,
                                                net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                                UUID uuid) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(uuid);
        }
    }

    /**
     * 神行移速开关对「飞行能力」的同步：1.20.1 玩家创造式飞行水平速度由
     * {@code Abilities#flyingSpeed} 提供（generic.flying_speed 属性对玩家无效），
     * 因此 X 开时把能力值放大 STRIDE_FLY_BOOST 倍（= ×2，与行走的「速度 V」倍率一致，
     * 原先为 ×25，过快故下调），关时还原为原始值，并用 onUpdateAbilities 同步客户端。
     * 仅在值变化时发送。
     */
    private static void applyStrideAbilities(Player player, boolean on) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        float orig = STRIDE_ORIG_FLY.computeIfAbsent(sp.getUUID(),
                k -> sp.getAbilities().getFlyingSpeed());
        float target = (float) (orig * (on ? STRIDE_FLY_BOOST : 1.0D));
        if (Math.abs(sp.getAbilities().getFlyingSpeed() - target) > 1e-4F) {
            sp.getAbilities().setFlyingSpeed(target);
            sp.onUpdateAbilities();
        }
        if (!on) {
            STRIDE_ORIG_FLY.remove(sp.getUUID());
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

    /**
     * 蹈水履火：在水面或岩浆表面“站立行走”。
     * 逻辑：脚部位于对应液体时，只要玩家未潜行下潜、也未主动飞行，
     * 就把向下运动抵消并把脚部托回液面；潜行或飞行时正常下潜/飞行。
     */
    private static void walkOnLiquids(Player player) {
        if (player.isShiftKeyDown() || player.getAbilities().flying) {
            return;
        }
        net.minecraft.world.level.Level level = player.level();
        net.minecraft.core.BlockPos feet = player.blockPosition();
        net.minecraft.world.level.material.FluidState fluid = level.getFluidState(feet);
        boolean onWater = fluid.is(net.minecraft.tags.FluidTags.WATER);
        boolean onLava = fluid.is(net.minecraft.tags.FluidTags.LAVA);
        if (!onWater && !onLava) {
            return;
        }
        double surfaceY = feet.getY() + fluid.getHeight(level, feet);
        // 已没入过深（如主动下潜超过约半格）则交还正常浮力，避免强行抬升卡墙
        if (player.getY() < surfaceY - 0.45D) {
            return;
        }
        net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();
        if (player.getY() < surfaceY) {
            // 托回液面
            double lift = Math.min(0.2D, surfaceY - player.getY());
            player.setDeltaMovement(motion.x, Math.max(motion.y, lift), motion.z);
        } else if (motion.y < 0.0D) {
            // 抵消下沉，实现站立行走
            player.setDeltaMovement(motion.x, 0.0D, motion.z);
        }
    }

    /**
     * 万藏：固定为通用(curio)槽 +99 个（不再随已装件数增长）。
     * 无状态自愈式：按 handler 实际修饰符状态增删（幂等），不依赖内存记忆，
     * 因此跨维度/重生后 transient 修饰符被 Curios 清除时，下个 tick 会自动补回。
     */
    private static void syncCurioHoard(Player player) {
        // 有外部容器/界面打开时暂不加槽：+99 会改变容器槽数，而客户端已打开的菜单
        // 仍是旧槽数 → ClientboundContainerSetContentPacket 在客户端越界报错。
        // 关掉容器后下个 tick 自会补上。
        if (player.containerMenu != player.inventoryMenu) {
            return;
        }
        java.util.Optional<top.theillusivec4.curios.api.type.capability.ICuriosItemHandler> optional =
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isEmpty()) {
            return;
        }
        top.theillusivec4.curios.api.type.capability.ICuriosItemHandler handler = optional.get();
        // 若实体尚未分配通用槽（entities/player.json 缺 curio），则本效果无意义
        java.util.Optional<top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler> shOpt =
                handler.getStacksHandler("curio");
        if (shOpt.isEmpty()) {
            return;
        }
        int target = 99;
        // 已是 +99 则不再重复写入（避免每 tick 触发槽位更新/容器同步）
        if (hasSlotModifier(handler, "curio", CURIO_HOARD_MOD, (double) target)) {
            return;
        }
        com.google.common.collect.Multimap<String, AttributeModifier> map =
                com.google.common.collect.LinkedHashMultimap.create();
        map.put("curio", new AttributeModifier(CURIO_HOARD_MOD, "divinebeast_he_true_curio",
                target, AttributeModifier.Operation.ADDITION));
        handler.addTransientSlotModifiers(map);
    }

    /** 该槽位上是否已存在我们用 modUuid 施加、数值为 amount 的修饰符（查询失败按"不存在"处理）。 */
    private static boolean hasSlotModifier(
            top.theillusivec4.curios.api.type.capability.ICuriosItemHandler handler,
            String slotId, UUID modUuid, double amount) {
        try {
            for (AttributeModifier modifier : handler.getModifiers().get(slotId)) {
                if (modUuid.equals(modifier.getId())
                        && Math.abs(modifier.getAmount() - amount) < 1.0E-6D) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // 退回原行为（当作不存在）
        }
        return false;
    }

    private static void removeMods(Player player) {
        applyStrideAbilities(player, false); // 还原被放大的飞行能力值
        // 每个 UUID 只属于一条属性，按 1:1 精确移除即可。
        // （原先用一个"6 UUID × 6 属性"的双层循环，实际产生 36 次无意义尝试。）
        removeAttributeModifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_MOD);
        removeAttributeModifier(player, Attributes.ATTACK_DAMAGE, MIGHT_MOD);
        removeAttributeModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MOD);
        removeAttributeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MOD);
        removeAttributeModifier(player, Attributes.FLYING_SPEED, FLYING_SPEED_MOD);
        removeAttributeModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RES_MOD);
        removeAttributeModifier(player, Attributes.MAX_HEALTH, MAX_HEALTH_MOD);
        // 回收万法之域增益（只列本引擎实际发放的；原先多列了力量/再生/水下呼吸/移速，
        // 那几项已改为不再发放，留着反而会误删玩家自带的同类药水）。
        for (net.minecraft.world.effect.MobEffect effect : new net.minecraft.world.effect.MobEffect[]{
                MobEffects.DAMAGE_RESISTANCE, MobEffects.JUMP,
                MobEffects.LUCK, MobEffects.FIRE_RESISTANCE, MobEffects.DIG_SPEED}) {
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
        player.setAbsorptionAmount(0.0F);
        SEC_DAMAGE.remove(player.getUUID()); // 太初每秒伤害额度随形态结束重置
        // 回收万藏：移除动态通用槽修饰符
        java.util.Optional<top.theillusivec4.curios.api.type.capability.ICuriosItemHandler> optional =
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isPresent()) {
            com.google.common.collect.Multimap<String, AttributeModifier> map =
                    com.google.common.collect.LinkedHashMultimap.create();
            map.put("curio", new AttributeModifier(CURIO_HOARD_MOD, "divinebeast_he_true_curio",
                    0.0D, AttributeModifier.Operation.ADDITION));
            optional.get().removeSlotModifiers(map);
        }
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

    /**
     * 万法附魔：把玩家「身上穿戴(护甲/主副手) + 背包内」全部可附魔物品，永久写入
     * 它所能附上的每一种正面附魔（最高等级），并忽略附魔之间的冲突（Sharpness 与
     * Smite 可同存、Protection 全系同存等）。每 20 tick 幂等刷新一次，对已满级物品
     * 不重复写入 NBT。
     *
     * <p>注意：这是"永久写入"（库存物品本体被改），脱下真者祂后附魔仍保留。
     * 不触碰不可附魔物品（书/食物/方块等 {@code canEnchant} 为 false 的不理）。
     */
    private static void applyDivineEnchantments(Player player) {
        java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().armor);
        stacks.add(player.getOffhandItem());
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            java.util.Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(stack);
            java.util.Map<Enchantment, Integer> desired = new java.util.HashMap<>(existing);
            for (Enchantment enchantment : net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS) {
                if (enchantment == null || enchantment.isCurse()) {
                    continue; // 只加正面附魔（排除诅咒）
                }
                if (!enchantment.canEnchant(stack)) {
                    continue; // 该物品附不上
                }
                desired.put(enchantment, enchantment.getMaxLevel());
            }
            // 仅当确有新增或等级提升时才写 NBT，避免每 tick 重复序列化
            if (!desired.equals(existing)) {
                EnchantmentHelper.setEnchantments(desired, stack);
            }
        }
    }

    // ==================================================================
    // 事件
    // ==================================================================

    /** 1 太初（虚空免疫）/ 11 血之回响 / 9 天罚 / 17 归墟（附加虚空伤害） */
    private static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || applyingTrueDamage) {
            return;
        }
        LivingEntity victim = event.getEntity();
        // 1 太初 / 17 归墟：免疫虚空伤害（原先是从虚空中拉回，现改为直接免疫）
        if (victim instanceof Player player && wearing(player) && isVoidDamage(event.getSource())) {
            event.setCanceled(true);
            return;
        }
        // 太初：每个自然秒内自身累计最多受到 1 点伤害，同秒内多余伤害全部无视
        if (victim instanceof Player player && wearing(player)) {
            long second = player.level().getGameTime() / 20L;
            double[] budget = SEC_DAMAGE.get(player.getUUID());
            if (budget == null || budget[0] != second) {
                budget = new double[]{second, 0.0D};
                SEC_DAMAGE.put(player.getUUID(), budget);
            }
            double used = budget[1];
            if (used >= 1.0D) {
                event.setCanceled(true);
            } else {
                double left = 1.0D - used;
                double amount = Math.min(event.getAmount(), left);
                event.setAmount((float) amount);
                budget[1] = used + amount;
            }
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
        if (event.isCanceled() || amount <= 0.0F) {
            return;
        }
        // 11 血之回响：伤害 × 当前生命值（本身有多少点生命，攻击力就乘多少）
        amount = amount * Math.max(1.0F, attacker.getHealth());
        event.setAmount(amount);
        // 9 天罚：附加一段等值、无视护甲的真伤
        // 17 归墟：附加一段与本次伤害等值的虚空伤害
        applyingTrueDamage = true;
        try {
            victim.hurt(victim.damageSources().genericKill(), amount);
            victim.hurt(victim.damageSources().fellOutOfWorld(), amount);
        } finally {
            applyingTrueDamage = false;
        }
    }

    /** 是否是"虚空伤害"（跌出世界）。同时兼容 outOfWorld / fellOutOfWorld 两种 message id。 */
    private static boolean isVoidDamage(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) {
            return false;
        }
        String id = source.getMsgId();
        return "outOfWorld".equals(id) || "fellOutOfWorld".equals(id);
    }

    /** 2 不朽：死亡无效（回满并只清负面） */
    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player && wearing(player)) {
            event.setCanceled(true);
            restoreVitals(player);
            player.setInvulnerable(false);
            // 只清负面：原先的 removeAllEffects() 会连万法之域/夜视等自家增益一起清掉，
            // 下个 tick 又被 refreshBuff 补回，造成"清了又补"的抖动。
            clearNegativeEffects(player);
        }
    }

    /**
     * 5 净世：负面效果禁止上身。
     *
     * <p>用 {@link MobEffectEvent.Applicable}（HasResult，可 setResult(DENY)）在施加前拦截；
     * {@code MobEffectEvent.Added} 不可取消，对其 setCanceled() 会崩服。
     */
    private static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player && wearing(player)
                && !event.getEffectInstance().getEffect().isBeneficial()) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    // ==================================================================
    // 造化（复制）已移除 → 挖掘/击杀掉落 ×10
    // ==================================================================

    /** 击杀掉落 ×10：真者祂击杀任意生物时，掉落的物品数量乘以 10。 */
    private static void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Player killer = killerPlayer(event.getSource());
        if (killer == null || !wearing(killer)) {
            return;
        }
        for (net.minecraft.world.entity.item.ItemEntity item : event.getDrops()) {
            if (item == null || item.getItem().isEmpty()) {
                continue;
            }
            ItemStack stack = item.getItem();
            stack.setCount(stack.getCount() * 10);
        }
    }

    /** 挖掘掉落 ×10：真者祂破坏方块时，掉落的经验数量乘以 10。 */
    private static void onBlockBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null) {
            return;
        }
        if (event.getPlayer().level().isClientSide || !wearing(event.getPlayer())) {
            return;
        }
        int exp = event.getExpToDrop();
        if (exp > 0) {
            event.setExpToDrop(exp * 10);
        }
    }

    /** 从伤害来源取回击杀玩家；无则返回 null。 */
    private static Player killerPlayer(net.minecraft.world.damagesource.DamageSource source) {
        if (source != null && source.getEntity() instanceof Player player) {
            return player;
        }
        return null;
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
