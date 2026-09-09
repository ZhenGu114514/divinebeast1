package com.divinebeast.divinebeast.curio;

import com.divinebeast.divinebeast.item.ModItems;
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
    private static final UUID FLYING_SPEED_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b6");
    /** 万藏：通用(curio)槽固定修饰符 UUID */
    private static final UUID CURIO_HOARD_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b7");
    /** 神行 X 开关开启时，飞行能力速度放大系数（相对玩家原 flyingSpeed） */
    private static final double STRIDE_FLY_BOOST = 25.0D;
    /** 记录每个玩家原始的 Abilities#flyingSpeed，用于 X 关闭/脱离形态时还原 */
    private static final java.util.Map<java.util.UUID, Float> STRIDE_ORIG_FLY = new java.util.HashMap<>();
    /** 旧伤：同一目标伤害叠加记忆（attacker → victim → 上次造成伤害） */
    private static final java.util.Map<java.util.UUID, java.util.Map<java.util.UUID, Float>> OLD_WOUNDS =
            new java.util.HashMap<>();
    /** 太初：玩家 → [自然秒 id, 该秒已累计伤害]，每秒最多 1 点 */
    private static final java.util.Map<java.util.UUID, double[]> SEC_DAMAGE = new java.util.HashMap<>();

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
        // X 键开关：神行 行/飞/游 移速加成（默认开，见 CuriosEffectsState）
        boolean strideSpeed = CuriosEffectsState.htrueSpeedToggle(player);
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
        // ---- 8 神能 / 12 神行（行/飞/泳同速）/ 6 磐石 / 23 无量之躯：属性强化（幂等覆盖）----
        ensureAttribute(player, Attributes.ATTACK_DAMAGE, DAMAGE_MOD, "divinebeast_he_true_dmg", 5000.0D);
        ensureAttribute(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MOD, "divinebeast_he_true_atk_speed", 60.0D);
        // 行走 / 飞行 / 游泳移速：X 键关闭时不维持并回收。
        // 行走/游泳读 MOVEMENT_SPEED；创造式飞行的水平速度由 Abilities#flyingSpeed 提供，
        // 因此开关须同时改写能力字段并通过 onUpdateAbilities 同步（否则飞行吃不到加成）。
        if (strideSpeed) {
            ensureAttribute(player, Attributes.MOVEMENT_SPEED, SPEED_MOD, "divinebeast_he_true_speed", 2.4D);
            ensureAttribute(player, Attributes.FLYING_SPEED, FLYING_SPEED_MOD, "divinebeast_he_true_fly_speed", 2.4D);
            applyStrideAbilities(player, true);
        } else {
            removeAttributeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MOD);
            removeAttributeModifier(player, Attributes.FLYING_SPEED, FLYING_SPEED_MOD);
            applyStrideAbilities(player, false);
        }
        ensureAttribute(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RES_MOD,
                "divinebeast_he_true_kb", 1.0D);
        ensureAttribute(player, Attributes.MAX_HEALTH, MAX_HEALTH_MOD,
                "divinebeast_he_true_maxhp", 2000.0D);
        // 蹈水履火：可于水面与岩浆表面行走（不深潜/不飞行时生效）
        walkOnLiquids(player);
        // 万藏：动态通用(curio)槽 = 99 + 已装通用饰品件数
        syncCurioHoard(player);

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
     * 因此 X 开时把能力值放大 STRIDE_FLY_BOOST 倍（与行走 +2.4 同为约 25 倍提升），
     * 关时还原为原始值，并用 onUpdateAbilities 同步客户端。仅在值变化时发送。
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

    // ==================================================================
    // 76 旧伤：对同一目标的伤害叠加
    // ==================================================================

    /** 读取上次对该目标造成的伤害（无则 0）。 */
    private static float oldWound(Player attacker, LivingEntity victim) {
        java.util.Map<java.util.UUID, Float> perTarget = OLD_WOUNDS.get(attacker.getUUID());
        if (perTarget == null) {
            return 0.0F;
        }
        Float last = perTarget.get(victim.getUUID());
        return last == null ? 0.0F : last;
    }

    /** 记录本次对该目标造成的伤害。 */
    private static void rememberOldWound(Player attacker, LivingEntity victim, float amount) {
        OLD_WOUNDS.computeIfAbsent(attacker.getUUID(), k -> new java.util.HashMap<>())
                .put(victim.getUUID(), amount);
    }

    /** 目标死亡（或被移除形态）后清空其旧伤记忆。 */
    private static void forgetVictim(java.util.UUID victimUuid) {
        for (java.util.Map<java.util.UUID, Float> perTarget : OLD_WOUNDS.values()) {
            perTarget.remove(victimUuid);
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
     * 无状态自愈式（同 CuriosEffects#syncOneSlot）：每个服务端 tick 用同一 UUID
     * 幂等覆盖应用 99，不依赖内存记忆，因此跨维度/重生后 transient 修饰符
     * 被 Curios 清除时，下个 tick 会自动补回。
     */
    private static void syncCurioHoard(Player player) {
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
        com.google.common.collect.Multimap<String, AttributeModifier> map =
                com.google.common.collect.LinkedHashMultimap.create();
        map.put("curio", new AttributeModifier(CURIO_HOARD_MOD, "divinebeast_he_true_curio",
                target, AttributeModifier.Operation.ADDITION));
        handler.addTransientSlotModifiers(map); // 同一 UUID 幂等覆盖
    }

    private static void removeMods(Player player) {
        applyStrideAbilities(player, false); // 还原被放大的飞行能力值
        for (UUID uuid : new UUID[]{DAMAGE_MOD, SPEED_MOD, ATTACK_SPEED_MOD, KNOCKBACK_RES_MOD,
                MAX_HEALTH_MOD, FLYING_SPEED_MOD}) {
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
            instance = player.getAttribute(Attributes.FLYING_SPEED);
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
        // 回收万藏：移除动态通用槽修饰符
        OLD_WOUNDS.remove(player.getUUID()); // 离开形态：清空自身旧伤记忆
        SEC_DAMAGE.remove(player.getUUID()); // 太初每秒伤害额度随形态结束重置
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

    // ==================================================================
    // 事件
    // ==================================================================

    /** 1 太初（含 17 归墟 的环境伤害）/ 9 天罚 / 11 血之回响 / 76 旧伤 */
    private static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || applyingTrueDamage) {
            return;
        }
        LivingEntity victim = event.getEntity();
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
        // 76 旧伤：本次伤害 += 上次对该生物造成的伤害（同 attacker→victim 记忆；目标死亡即清零）
        float last = oldWound(attacker, victim);
        if (last > 0.0F) {
            amount += last;
            event.setAmount(amount);
        }
        rememberOldWound(attacker, victim, amount);
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

    /** 2 不朽：死亡无效；同时清除 76 旧伤 对已死亡目标的记忆 */
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
            return;
        }
        // 其它生物死亡：遗忘所有玩家对它的旧伤记忆（换目标/目标死亡即归零）
        forgetVictim(event.getEntity().getUUID());
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
