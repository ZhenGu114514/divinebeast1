package com.divinebeast.divinebeast.curio;

import com.divinebeast.divinebeast.item.ModItems;
import com.divinebeast.divinebeast.net.CuriosEffectsState;
import com.divinebeast.divinebeast.reward.ProgressGrants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.List;
import java.util.UUID;

/**
 * 『祂』完整效果引擎（仅在 Curios 已安装时由 DivineBeastMod 触发加载）。
 *
 * <p><b>阶段一（未集齐 5 件"时刻"饰品，佩戴『祂』即承受，对应时刻饰品戴上则解除该项）：</b>
 * <ol>
 *   <li>不存在不存在时刻：生命上限=2、经验恒定 0、工具耐久恒为 1、护甲=0</li>
 *   <li>不存在存在时刻：攻击命中→目标回满血，自身承受本次伤害</li>
 *   <li>可能存在存在时刻：造成/承受伤害时随机传送（1 秒冷却）</li>
 *   <li>已存在存在时刻：身上存在药水效果时每秒扣 50% 剩余生命（至少 1 点）</li>
 *   <li>不可能存在不可能存在时刻：不死图腾等复活触发时改为死亡</li>
 * </ol>
 *
 * <p><b>阶段二（救赎：5 件时刻饰品全部佩戴）：</b>
 * 获得飞行；所有生物不再主动攻击；攻击你的生物被清除效果、生命上限锁 100
 * 并打上救赎印记；你攻击生物不再造成伤害而是使其回满血；攻击带印记者无视
 * 护甲与免伤造成真伤；击杀带印记者（默认开启，C 键可关）会在原地重生一个
 * 同样的生物。
 */
public final class CuriosEffects {

    private static final Logger LOGGER = LogManager.getLogger();

    // 槽位（与 data/divinebeast/curios/slots/*.json 及 ModItems 一一对应）
    private static final String DEITY_SLOT = "divine";
    private static final String[] MOMENT_SLOTS = {
            "nonexist_nonexist", "nonexist_exist", "maybe_exist", "exist_exist", "impossible_nonexist"
    };

    // 固定修饰符 UUID（属性锁使用）
    private static final UUID MAX_HEALTH_LOCK = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-000000000001");
    private static final UUID ARMOR_LOCK = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-000000000002");
    private static final UUID MOB_HEALTH_100 = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-000000000003");
    private static final UUID KNOCKBACK_RES = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-000000000004");
    private static final UUID REDEEM_ATK = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-000000000005");
    private static final UUID REDEEM_ATK_SPEED = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-000000000006");
    private static final UUID REDEEM_MOVE_SPEED = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-000000000007");
    private static final UUID HE_FIRST_SLOT_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000a1");
    private static final UUID HE_EXTREME_SLOT_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000a2");
    private static final UUID HE_TRUE_SLOT_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000a3");
    private static final UUID REDEMPTION_SLOT_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000a4");
    private static final UUID TRUEHEART_SLOT_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000a5");
    private static final String ADV_REDEMPTION_TAG = "divinebeast.adv.deity_redemption";

    // 效果索引（与 MOMENT_SLOTS 顺序一致）
    private static final int CURSE_LIFE_LOCK = 0;   // 不存在不存在时刻
    private static final int CURSE_HEAL_HURT = 1;   // 不存在存在时刻
    private static final int CURSE_TELEPORT = 2;    // 可能存在存在时刻
    private static final int CURSE_DRAIN = 3;       // 已存在存在时刻
    private static final int CURSE_DEATH = 4;       // 不可能存在不可能存在时刻

    /** 传送冷却（tick） */
    /** 可能存在存在时刻（诅咒触发）冷却：60 秒 */
    private static final int TELEPORT_COOLDOWN = 1200;
    /** 随机传送半径 */
    private static final int TELEPORT_RADIUS = 24;

    /** 防递归：phase2 真伤内部调用 hurt 时跳过本类攻击处理 */
    private static boolean applyingTrueDamage = false;

    private CuriosEffects() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(CuriosEffects::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(CuriosEffects::onLivingAttack);
        MinecraftForge.EVENT_BUS.addListener(CuriosEffects::onLivingHurt);
        MinecraftForge.EVENT_BUS.addListener(CuriosEffects::onLivingDamage);
        MinecraftForge.EVENT_BUS.addListener(CuriosEffects::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(CuriosEffects::onLivingFall);
        MinecraftForge.EVENT_BUS.addListener(CuriosEffects::onLivingKnockBack);
        MinecraftForge.EVENT_BUS.addListener(CuriosEffects::onEffectApplicable);
        MinecraftForge.EVENT_BUS.addListener(CuriosEffects::onTargetChange);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CuriosEffects::onXpChange);
        // 跨维度（含末地传送门返回主世界）后立即重建证悟阶段槽
        MinecraftForge.EVENT_BUS.addListener(CuriosEffects::onPlayerChangedDimension);
        LOGGER.info("[divinebeast] 『祂』效果引擎已注册（阶段一负面 ×5 / 救赎阶段）。");
    }

    // ==================================================================
    // 状态读取
    // ==================================================================

    /** 玩家当前是否佩戴『祂』（divine 槽内有 deity） */
    private static boolean wearingDeity(LivingEntity entity) {
        return hasInSlot(entity, DEITY_SLOT, ModItems.DEITY.get());
    }

    private static boolean wearingMoment(LivingEntity entity, int index) {
        return hasInSlot(entity, MOMENT_SLOTS[index], ModItems.MOMENT_ITEMS.get(index).get());
    }

    private static boolean hasInSlot(LivingEntity entity, String slotId, net.minecraft.world.item.Item item) {
        java.util.Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(entity).resolve();
        if (optional.isPresent()) {
            // findCurios(identifier) 返回该槽位当前已装备的物品结果
            for (top.theillusivec4.curios.api.SlotResult result : optional.get().findCurios(slotId)) {
                net.minecraft.world.item.ItemStack stack = result.stack();
                if (!stack.isEmpty() && stack.is(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 阶段二：佩戴『祂』且 5 件时刻饰品全部就位 */
    private static boolean phaseTwo(LivingEntity entity) {
        if (!wearingDeity(entity)) {
            return false;
        }
        for (int i = 0; i < MOMENT_SLOTS.length; i++) {
            if (!wearingMoment(entity, i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 某项负面是否仍生效（阶段一且对应时刻饰品未佩戴）。
     *
     * <p>真者祂形态已超越祂系一切阶段：佩戴『真者祂』期间，祂系全部阶段一负面一律不生效，
     * 否则会出现"真者祂的效果反而被低阶诅咒废掉"的情况（经验被归零、攻击变成给目标回血、
     * 工具耐久锁 1、生命上限被锁到 2 等）。
     */
    private static boolean curseActive(LivingEntity entity, int curseIndex) {
        // 懦弱的抉择：装备期间无效『祂』系全部诅咒负面
        if (CowardChoice.wearing(entity)) {
            return false;
        }
        if (phaseTwo(entity)) {
            return false;
        }
        if (isHeTrueActive(entity)) {
            return false;
        }
        return wearingDeity(entity) && !wearingMoment(entity, curseIndex);
    }

    /** 该生物是否是"处于真者祂形态的玩家"（且未被救赎/本心封印）。 */
    private static boolean isHeTrueActive(LivingEntity entity) {
        return entity instanceof Player player
                && AscensionEffects.wearingHeTrue(player)
                && !AscensionEffects.effectsDisabled(player);
    }

    /** 已就位的时刻饰品数量 */
    private static int equippedMoments(LivingEntity entity) {
        if (!wearingDeity(entity)) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < MOMENT_SLOTS.length; i++) {
            if (wearingMoment(entity, i)) {
                count++;
            }
        }
        return count;
    }

    // ==================================================================
    // 服务器刻：诅咒持续效果 + 飞行
    // ==================================================================

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;

        // 证悟槽同步（he_first / he_extreme / he_true 按阶段开合）
        syncAscensionSlots(player);

        // 攻击力×1000 奖励到期收回
        expireDivineSwing(player, player.level().getGameTime());

        // 救赎形态：饰品栏中『祂』改名为彩色『祂』（每秒随机换色）
        renameDeityInCurio(player, phaseTwo(player));

        // 救赎/本心 完全封印：除 救赎/本心 自身外，一切饰品效果失效
        if (AscensionEffects.effectsDisabled(player)) {
            // 收回『祂』已施加的属性/能力（含 divine swing 残留）
            ensureFlight(player, false);
            clearKnockbackResistance(player);
            clearSpeedBuffs(player);
            unlockAttribute(player, MAX_HEALTH_LOCK);
            unlockAttribute(player, ARMOR_LOCK);
            AttributeInstance sealedAtk = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (sealedAtk != null) {
                sealedAtk.removeModifier(REDEEM_ATK);
            }
            REDEEM_SWING_UNTIL.remove(player.getUUID());
            return;
        }

        // 飞行 / 收回
        if (phaseTwo(player)) {
            ensureFlight(player, true);
        } else {
            ensureFlight(player, false);
        }

        // 经验恒定为零
        // 成就：进入祂·救赎
        if (phaseTwo(player) && player instanceof ServerPlayer serverPlayer
                && !player.getPersistentData().getBoolean(ADV_REDEMPTION_TAG)) {
            player.getPersistentData().putBoolean(ADV_REDEMPTION_TAG, true);
            ProgressGrants.grant(serverPlayer, "deity_redemption");
        }
        // 同时满足「祂救赎＋兽自·我」→ 自动获得『祂者初』并进入阶段 1（一次）
        if (phaseTwo(player) && BeastEffects.stageOf(player) == 3
                && AscensionEffects.stageOf(player) == AscensionEffects.STAGE_NONE) {
            if (player.getPersistentData().getBoolean("divinebeast.got_he_first")) {
                // 旧档兼容：已获得过 祂者初 → 只推进阶段，不重复发放
                AscensionEffects.setStage(player, AscensionEffects.STAGE_HE_FIRST);
            } else if (player instanceof ServerPlayer serverPlayer2) {
                player.getPersistentData().putBoolean("divinebeast.got_he_first", true);
                AscensionEffects.setStage(player, AscensionEffects.STAGE_HE_FIRST);
                ItemStack gift = new ItemStack(ModItems.HE_FIRST.get());
                gift.enchant(net.minecraft.world.item.enchantment.Enchantments.BINDING_CURSE, 1);
                if (!player.getInventory().add(gift)) {
                    player.drop(gift, false);
                }
                player.sendSystemMessage(Component.translatable("divinebeast.msg.got_he_first"));
            }
        }
        if (curseActive(player, CURSE_LIFE_LOCK)) {
            if (player.totalExperience != 0 || player.experienceLevel != 0 || player.experienceProgress != 0.0F) {
                player.totalExperience = 0;
                player.experienceLevel = 0;
                player.experienceProgress = 0.0F;
            }
        }
        if (player.tickCount % 10 != 0) {
            return;
        }

        // 救赎常驻：免疫负面效果 + 击退抗性 1 + 攻速×10 + 移速×2
        if (phaseTwo(player)) {
            ensureKnockbackResistance(player);
            clearHarmfulEffects(player);
            ensureSpeedBuffs(player);
        } else {
            clearKnockbackResistance(player);
            clearSpeedBuffs(player);
        }

        // 生命上限 2 / 护甲 0 / 工具耐久 1
        if (curseActive(player, CURSE_LIFE_LOCK)) {
            lockMaxHealth(player, 2.0D, MAX_HEALTH_LOCK);
            lockArmorZero(player);
            forceDurabilityOne(player);
        } else {
            unlockAttribute(player, MAX_HEALTH_LOCK);
            unlockAttribute(player, ARMOR_LOCK);
        }

        // 已存在存在时刻（诅咒）：有药水效果时每秒扣 50% 剩余生命（至少 1 点）；剩余生命 <2 时不触发
        if (curseActive(player, CURSE_DRAIN) && player.getHealth() >= 2.0F
                && player.tickCount % 20 == 0 && !player.getActiveEffects().isEmpty()) {
            float amount = Math.max(1.0F, (float) Math.ceil(player.getHealth() * 0.5F));
            player.hurt(player.damageSources().magic(), amount);
        }
    }

    private static void ensureFlight(Player player, boolean enabled) {
        ServerPlayer serverPlayer = (player instanceof ServerPlayer sp) ? sp : null;
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (enabled) {
            // 救赎阶段：仅授予"可飞行"（mayfly），起飞/降落由玩家双击空格控制（同创造）
            boolean changed = false;
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                changed = true;
            }
            if (changed && serverPlayer != null) {
                serverPlayer.onUpdateAbilities();
            }
        } else if (player.getAbilities().mayfly) {
            // 【重要】真者祂形态的「3 永恒之翼」飞行由 HeTrueEffects 维持。
            // 本方法在"非救赎阶段"每 tick 都会走到这里，若此时回收飞行，
            // 会与 HeTrueEffects 的授予形成每 tick 互相打架：
            // HeTrueEffects 只会重开 mayfly、不会重开 flying，于是玩家双击空格起飞后
            // 每 tick 都被强制 flying=false 踢下来 —— 表现为"真者祂无法飞行"。
            if (AscensionEffects.wearingHeTrue(player) && !AscensionEffects.effectsDisabled(player)) {
                return; // 飞行交由真者祂引擎维持，这里不动
            }
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            if (serverPlayer != null) {
                serverPlayer.onUpdateAbilities();
            }
        }
    }

    /** 救赎阶段无摔落伤害 */
    private static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player && phaseTwo(player)
                && !AscensionEffects.effectsDisabled(player)) {
            event.setCanceled(true);
        }
    }

    /** 救赎：不受攻击/爆炸击退 */
    private static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player && phaseTwo(player)
                && !AscensionEffects.effectsDisabled(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * 救赎：免疫负面药水/效果（阻止施加）。
     *
     * <p>必须用 {@link MobEffectEvent.Applicable}（HasResult，可 setResult(DENY)）在
     * 施加前拦截；{@code MobEffectEvent.Added} 是"已施加"通知且**不可取消**，
     * 对其调用 setCanceled() 会抛 UnsupportedOperationException 导致服务器崩溃。
     */
    private static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player && phaseTwo(player)
                && !AscensionEffects.effectsDisabled(player)
                && !event.getEffectInstance().getEffect().isBeneficial()) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /** 救赎：击退抗性拉满（配合事件双保险） */
    private static void ensureKnockbackResistance(Player player) {
        AttributeInstance inst = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (inst != null && inst.getModifier(KNOCKBACK_RES) == null) {
            inst.addPermanentModifier(new AttributeModifier(KNOCKBACK_RES, "divinebeast_salvation_kb",
                    1.0D, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void clearKnockbackResistance(Player player) {
        AttributeInstance inst = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (inst != null) {
            inst.removeModifier(KNOCKBACK_RES);
        }
    }

    /** 救赎常驻：攻击速度 ×10、移动速度 ×2 */
    private static void ensureSpeedBuffs(Player player) {
        scaleAttribute(player, Attributes.ATTACK_SPEED, REDEEM_ATK_SPEED, 10.0D);
        scaleAttribute(player, Attributes.MOVEMENT_SPEED, REDEEM_MOVE_SPEED, 2.0D);
    }

    private static void clearSpeedBuffs(Player player) {
        AttributeInstance atkSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atkSpeed != null) {
            atkSpeed.removeModifier(REDEEM_ATK_SPEED);
        }
        AttributeInstance moveSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            moveSpeed.removeModifier(REDEEM_MOVE_SPEED);
        }
    }

    /** 把属性当前值放大 multiplier 倍（固定 UUID，可反复校正） */
    private static void scaleAttribute(Player player,
                                       net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                       UUID uuid, double multiplier) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(uuid);
        double natural = instance.getValue();
        double delta = natural * (multiplier - 1.0D);
        if (Math.abs(delta) > 0.001D) {
            instance.addPermanentModifier(new AttributeModifier(uuid, "divinebeast_salvation_speed", delta,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    /** 救赎：清除身上残留的负面效果 */
    private static void clearHarmfulEffects(Player player) {
        for (MobEffectInstance effect : new java.util.ArrayList<>(player.getActiveEffects())) {
            if (!effect.getEffect().isBeneficial()) {
                player.removeEffect(effect.getEffect());
            }
        }
    }

    private static void lockMaxHealth(Player player, double target, UUID uuid) {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            return;
        }
        instance.removeModifier(uuid);
        double current = instance.getValue();
        double delta = target - current;
        if (Math.abs(delta) > 0.001D) {
            instance.addPermanentModifier(new AttributeModifier(uuid, "divinebeast_curse", delta,
                    AttributeModifier.Operation.ADDITION));
        }
        if (player.getHealth() > target) {
            player.setHealth((float) target);
        }
    }

    private static void lockArmorZero(Player player) {
        AttributeInstance instance = player.getAttribute(Attributes.ARMOR);
        if (instance == null) {
            return;
        }
        instance.removeModifier(ARMOR_LOCK);
        double current = instance.getValue();
        double delta = -current;
        if (Math.abs(delta) > 0.001D) {
            instance.addPermanentModifier(new AttributeModifier(ARMOR_LOCK, "divinebeast_curse_armor", delta,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    private static void unlockAttribute(Player player, UUID uuid) {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance != null) {
            instance.removeModifier(uuid);
        }
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(uuid);
        }
    }

    /** 工具耐久恒为 1 点：主背包与副手中的可损耗物品均强制 damage=maxDamage-1 */
    private static void forceDurabilityOne(Player player) {
        List<ItemStack> stacks = new java.util.ArrayList<>(player.getInventory().items);
        stacks.add(player.getOffhandItem());
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                if (stack.getDamageValue() != max - 1) {
                    stack.setDamageValue(max - 1);
                }
            }
        }
    }

    // ==================================================================
    // 攻击事件
    // ==================================================================

    /** 解析造成攻击的玩家（近战/远程弹射物主人） */
    private static Player attackingPlayer(DamageSource source) {
        Entity entity = source.getEntity();
        if (entity instanceof Player player) {
            return player;
        }
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    /** 解析伤害来源生物（近战实体 / 弹射物主人） */
    private static LivingEntity sourceLiving(DamageSource source) {
        Entity entity = source.getEntity();
        if (entity instanceof LivingEntity living) {
            return living;
        }
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private static void onLivingAttack(LivingAttackEvent event) {
        if (applyingTrueDamage || event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity victim = event.getEntity();
        Player attacker = attackingPlayer(event.getSource());
        if (attacker == null || victim.is(attacker) || AscensionEffects.effectsDisabled(attacker)) {
            return;
        }

        // 可能存在存在时刻：造成伤害时随机传送（1 秒冷却）
        if (curseActive(attacker, CURSE_TELEPORT)) {
            applyMaybeExistCurse(attacker);
        }

        if (curseActive(attacker, CURSE_HEAL_HURT)) {
            // 不存在存在时刻：目标满血，自身承受本次伤害
            event.setCanceled(true);
            victim.setHealth(victim.getMaxHealth());
            attacker.hurt(attacker.damageSources().magic(), event.getAmount());
            return;
        }

        if (phaseTwo(attacker)) {
            if (CuriosEffectsState.hasRedemptionMark(victim) && !isBossMob(victim)) {
                // 常驻（不受 C 键影响）：对带救赎印记生物 → 无视护甲/免伤真伤
                // BOSS 例外：它们的 hurt 覆写不接受无来源的真伤，取消普通伤害会让它们彻底打不动
                event.setCanceled(true);
                float before = victim.getHealth() + victim.getAbsorptionAmount();
                applyingTrueDamage = true;
                try {
                    victim.hurt(victim.damageSources().genericKill(), event.getAmount());
                } finally {
                    applyingTrueDamage = false;
                }
                // 兜底：真伤一段都没打出来（目标的 hurt 覆写不接受"无实体来源"的伤害类型，
                // 例如其它模组的 BOSS）→ 退回一段玩家攻击伤害。
                // 这里绝不能出现"普通伤害已被取消、替换的真伤又无效"= 目标完全无敌的情况。
                if (victim.isAlive()
                        && victim.getHealth() + victim.getAbsorptionAmount() >= before) {
                    victim.hurt(victim.damageSources().playerAttack(attacker), event.getAmount());
                }
                return;
            }
            // 未带印记：仅当「救赎之击」开启（C 键）时改为回满血；关闭则正常造成伤害
            // BOSS 例外：把 BOSS 治满血等于让它无敌，故不适用
            if (!isBossMob(victim) && CuriosEffectsState.respawnToggle(attacker)) {
                event.setCanceled(true);
                victim.setHealth(victim.getMaxHealth());
            }
        }
    }

    private static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DamageSource source = event.getSource();

        // 救赎/本心 封印：受击方无任何『祂』免伤/反制
        if (AscensionEffects.effectsDisabled(player)) {
            return;
        }

        // 救赎常驻：免疫带救赎印记生物对你造成的伤害
        if (phaseTwo(player)) {
            LivingEntity attacker = sourceLiving(source);
            if (attacker != null && CuriosEffectsState.hasRedemptionMark(attacker)) {
                event.setCanceled(true);
                return;
            }
        }

        // 可能存在存在时刻：受到伤害随机传送
        if (curseActive(player, CURSE_TELEPORT)) {
            applyMaybeExistCurse(player);
        }

        // 救赎阶段：攻击自身的生物 → 清效果 + 生命上限锁 100 + 打上救赎印记
        if (phaseTwo(player)) {
            Entity direct = source.getEntity();
            if (direct instanceof Mob mob) {
                if (isBossMob(mob)) {
                    // BOSS 不打印记、不锁血、不回满血；并顺手清掉旧存档里被误加的印记
                    clearBossMark(mob);
                } else {
                    mob.removeAllEffects();
                    setMaxHealth(mob, 100.0D, MOB_HEALTH_100);
                    mob.setHealth(mob.getMaxHealth());
                    CuriosEffectsState.setRedemptionMark(mob, true);
                }
            }
        }
    }

    /** 可能存在存在时刻（诅咒）：造成/受到伤害时获得一个随机负面药水效果（60 秒冷却） */
    private static void applyMaybeExistCurse(Player player) {
        long now = player.level().getGameTime();
        Long last = lastTeleportTick.get(player.getUUID());
        if (last != null && now - last < TELEPORT_COOLDOWN) {
            return;
        }
        lastTeleportTick.put(player.getUUID(), now);
        MobEffect[] pool = {
                MobEffects.POISON, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS,
                MobEffects.BLINDNESS, MobEffects.WITHER, MobEffects.HUNGER
        };
        MobEffect effect = pool[player.getRandom().nextInt(pool.length)];
        int duration = (effect == MobEffects.WITHER || effect == MobEffects.BLINDNESS) ? 60 : 120;
        player.addEffect(new MobEffectInstance(effect, duration, 0));
    }

    private static boolean isStandable(Level level, double x, int y, double z) {
        net.minecraft.core.BlockPos foot = net.minecraft.core.BlockPos.containing(x, y, z);
        return !level.getBlockState(foot).isAir()
                && level.getBlockState(foot.above()).isAir()
                && level.getBlockState(foot.above(2)).isAir();
    }

    private static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player player) || player.isDeadOrDying()
                || AscensionEffects.effectsDisabled(player)) {
            return;
        }
        // 不可能存在不可能存在时刻：致命伤 + 有图腾 → 取消复活直接死亡
        if (curseActive(player, CURSE_DEATH)
                && event.getAmount() >= player.getHealth()
                && (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING))) {
            event.setCanceled(true);
            player.die(player.damageSources().genericKill());
        }
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (!CuriosEffectsState.hasRedemptionMark(dead)) {
            return;
        }
        CuriosEffectsState.setRedemptionMark(dead, false);
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof Player player) || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // 常驻奖励：击败带救赎印记的生物 → 自身获得 5 秒攻击力 ×1000（不受 C 键影响，救赎阶段内必触发）
        if (!phaseTwo(player) || AscensionEffects.effectsDisabled(player)) {
            return;
        }
        grantDivineSwing(player);
    }

    private static void onTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getNewTarget() instanceof Player player && phaseTwo(player)
                && !AscensionEffects.effectsDisabled(player)) {
            event.setCanceled(true);
        }
    }

    private static void onXpChange(PlayerXpEvent.XpChange event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || AscensionEffects.effectsDisabled(player)) {
            return;
        }
        if (curseActive(player, CURSE_LIFE_LOCK)) {
            event.setCanceled(true);
        }
    }

    private static void setMaxHealth(LivingEntity entity, double target, UUID uuid) {
        AttributeInstance instance = entity.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            return;
        }
        instance.removeModifier(uuid);
        double current = instance.getValue();
        double delta = target - current;
        if (Math.abs(delta) > 0.001D) {
            instance.addPermanentModifier(new AttributeModifier(uuid, "divinebeast_salvation", delta,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    /**
     * 是否是"进程型 BOSS"（末影龙 / 凋灵）。
     *
     * <p>救赎印记那一整套流程（{@code removeAllEffects} + 生命上限锁 100 + 回满血 +
     * 之后普通伤害全部改走 {@code generic_kill} 真伤）对它们是<b>毁灭性</b>的：
     * <ul>
     *   <li>BOSS 会主动攻击玩家 → 每次命中都触发"回满血"，
     *       等于把 BOSS 变成永远满血的沙包（实测："装备真者祂后无法攻击末影龙"）；</li>
     *   <li>打上印记后普通伤害被 {@code event.setCanceled(true)} 取消、只走无实体的
     *       {@code generic_kill} 伤害，而 BOSS 的 {@code hurt} 覆写不接受这种来源 → 完全打不动；</li>
     *   <li>末影龙的最大生命会被锁成 100，BOSS 血条显示异常。</li>
     * </ul>
     * 因此对 BOSS 一律跳过印记流程，让其按原版普通伤害结算
     * （与 {@code BeastEffects} 里"对末影龙等 BOSS 走普通伤害"的处理保持一致）。
     */
    private static boolean isBossMob(LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss;
    }

    /** 清掉 BOSS 身上被误加的救赎印记与"生命上限锁 100"修饰符（修复旧存档里已被打上印记的龙/凋灵） */
    private static void clearBossMark(LivingEntity entity) {
        CuriosEffectsState.setRedemptionMark(entity, false);
        AttributeInstance instance = entity.getAttribute(Attributes.MAX_HEALTH);
        if (instance != null) {
            instance.removeModifier(MOB_HEALTH_100);
        }
    }

    private static final java.util.Map<java.util.UUID, Long> lastTeleportTick = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Long> REDEEM_SWING_UNTIL = new java.util.HashMap<>();

    /** 击败带救赎印记的生物 → 5 秒攻击力 ×1000 */
    private static void grantDivineSwing(Player player) {
        AttributeInstance atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk == null) {
            return;
        }
        atk.removeModifier(REDEEM_ATK);
        double natural = atk.getValue();
        double delta = natural * 999.0D;
        if (delta > 0.001D) {
            atk.addPermanentModifier(new AttributeModifier(REDEEM_ATK, "divinebeast_redeem_swing", delta,
                    AttributeModifier.Operation.ADDITION));
        }
        REDEEM_SWING_UNTIL.put(player.getUUID(), player.level().getGameTime() + 100L);
    }

    private static void expireDivineSwing(Player player, long now) {
        Long until = REDEEM_SWING_UNTIL.get(player.getUUID());
        if (until != null && now >= until) {
            REDEEM_SWING_UNTIL.remove(player.getUUID());
            AttributeInstance atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atk != null) {
                atk.removeModifier(REDEEM_ATK);
            }
        }
    }

    private static final ChatFormatting[] DEITY_COLORS = {
            ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW, ChatFormatting.GREEN,
            ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE, ChatFormatting.BLUE, ChatFormatting.WHITE
    };
    private static final String DEITY_DISPLAY = "『祂』";

    /** 救赎形态：饰品栏中的『祂』每秒随机变色（未救赎则恢复原名） */
    private static void renameDeityInCurio(Player player, boolean redemption) {
        java.util.Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isEmpty()) {
            return;
        }
        for (top.theillusivec4.curios.api.SlotResult result : optional.get().findCurios(DEITY_SLOT)) {
            ItemStack stack = result.stack();
            if (!stack.is(ModItems.DEITY.get())) {
                continue;
            }
            if (redemption) {
                if (player.tickCount % 20 == 0 || !DEITY_DISPLAY.equals(stack.getHoverName().getString())) {
                    ChatFormatting color = DEITY_COLORS[player.getRandom().nextInt(DEITY_COLORS.length)];
                    stack.setHoverName(Component.literal(DEITY_DISPLAY).withStyle(color));
                }
            } else if (DEITY_DISPLAY.equals(stack.getHoverName().getString())) {
                stack.resetHoverName();
            }
            break;
        }
    }

    /** 证悟阶段槽：按 AscensionEffects.stageOf 解锁 he_first / he_extreme / he_true，
     *  redemption / trueheart 则跟随核心是否正佩戴（穿 祂者初 → 救赎槽开；
     *  穿 祂者极 → 本心槽开；销毁/离槽即关）。
     *  <p>无状态自愈式：按 handler 中的实际修饰符状态增删（幂等），不依赖任何内存记忆集，
     *  因此跨维度/重生/换实体后，只要下一个服务端 tick 运行即可把槽位收敛到当前阶段。 */
    private static void syncAscensionSlots(Player player) {
        // 有外部容器/界面打开时暂不改动槽位数量：增删槽位会改变容器槽数，而客户端
        // "已打开"的菜单仍是旧槽数，服务端随后发的 ClientboundContainerSetContentPacket
        // 会在客户端 AbstractContainerMenu.getSlot() 越界（IndexOutOfBoundsException）。
        // 关掉容器后下个 tick 自会补上（本方法每 tick 都跑）。
        if (player.containerMenu != player.inventoryMenu) {
            return;
        }
        int stage = AscensionEffects.stageOf(player);
        java.util.Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isEmpty()) {
            return;
        }
        ICuriosItemHandler handler = optional.get();
        syncOneSlot(handler, "he_first", stage == 1, HE_FIRST_SLOT_MOD);
        syncOneSlot(handler, "redemption", AscensionEffects.wearingHeFirst(player), REDEMPTION_SLOT_MOD);
        syncOneSlot(handler, "he_extreme", stage == 2, HE_EXTREME_SLOT_MOD);
        syncOneSlot(handler, "trueheart", AscensionEffects.wearingHeExtreme(player), TRUEHEART_SLOT_MOD);
        syncOneSlot(handler, "he_true", stage == 3, HE_TRUE_SLOT_MOD);
    }

    private static void syncOneSlot(ICuriosItemHandler handler, String slotId, boolean wanted,
                                    UUID modUuid) {
        // 仅当"实际状态"与"期望状态"不符时才改动。原来每 tick 无条件 add/remove，
        // 会反复触发槽位更新与容器同步，是容器槽数错位（客户端越界报错）的主要来源。
        if (hasSlotModifier(handler, slotId, modUuid, 1.0D) == wanted) {
            return;
        }
        com.google.common.collect.Multimap<String, AttributeModifier> map = com.google.common.collect.LinkedHashMultimap.create();
        map.put(slotId, new AttributeModifier(modUuid, "divinebeast_" + slotId, 1.0D,
                AttributeModifier.Operation.ADDITION));
        if (wanted) {
            handler.addTransientSlotModifiers(map);
        } else {
            handler.removeSlotModifiers(map);
        }
    }

    /** 该槽位上是否已存在我们用 modUuid 施加、数值为 amount 的修饰符（查询失败按"不存在"处理）。 */
    private static boolean hasSlotModifier(ICuriosItemHandler handler, String slotId,
                                           UUID modUuid, double amount) {
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

    /** 玩家更换维度（如经末地传送门从末地返回主世界）后，立即强制重同步证悟槽位。
     *  Curios 的 transient 修饰符在跨维度/实体重载后可能被清除，这里在下个服务端 tick
     *  前（事件同刻，紧接普通 tick 同步）再补一次，避免槽栏短暂退回旧阶段。 */
    private static void onPlayerChangedDimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        net.minecraft.world.entity.player.Player player = event.getEntity();
        if (player == null || player.level().isClientSide) {
            return;
        }
        syncAscensionSlots(player);
    }

    /** 供 AscensionEffects 迁移成功后立刻把阶段槽收敛到当前 stage（幂等）。 */
    public static void resyncAscension(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        syncAscensionSlots(player);
    }

    /** 供客户端 tooltip：某件时刻饰品是否已佩戴（其诅咒已解除） */
    public static boolean momentWorn(LivingEntity entity, int index) {
        return index >= 0 && index < MOMENT_SLOTS.length && wearingMoment(entity, index);
    }

    /** 供客户端 tooltip 使用的轻量状态查询（需玩家实体，client/server 均可） */
    public static int momentProgress(LivingEntity entity) {
        return equippedMoments(entity);
    }

    public static boolean isPhaseTwo(LivingEntity entity) {
        return phaseTwo(entity);
    }

    public static boolean isWearingDeity(LivingEntity entity) {
        return wearingDeity(entity);
    }
}
