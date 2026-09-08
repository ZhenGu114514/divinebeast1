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
        MinecraftForge.EVENT_BUS.addListener(CuriosEffects::onMobEffectAdded);
        MinecraftForge.EVENT_BUS.addListener(CuriosEffects::onTargetChange);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CuriosEffects::onXpChange);
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

    /** 某项负面是否仍生效（阶段一且对应时刻饰品未佩戴） */
    private static boolean curseActive(LivingEntity entity, int curseIndex) {
        if (phaseTwo(entity)) {
            return false;
        }
        return wearingDeity(entity) && !wearingMoment(entity, curseIndex);
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

        // 攻击力×1000 奖励到期收回
        expireDivineSwing(player, player.level().getGameTime());

        // 救赎形态：饰品栏中『祂』改名为彩色『祂』（每秒随机换色）
        renameDeityInCurio(player, phaseTwo(player));

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
        if (event.getEntity() instanceof Player player && phaseTwo(player)) {
            event.setCanceled(true);
        }
    }

    /** 救赎：不受攻击/爆炸击退 */
    private static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player && phaseTwo(player)) {
            event.setCanceled(true);
        }
    }

    /** 救赎：免疫负面药水/效果（阻止施加） */
    private static void onMobEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player && phaseTwo(player)
                && event.getEffectInstance() != null && !event.getEffectInstance().getEffect().isBeneficial()) {
            event.setCanceled(true);
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
        if (attacker == null || victim.is(attacker)) {
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
            if (CuriosEffectsState.hasRedemptionMark(victim)) {
                // 常驻（不受 C 键影响）：对带救赎印记生物 → 无视护甲/免伤真伤
                event.setCanceled(true);
                applyingTrueDamage = true;
                try {
                    victim.hurt(victim.damageSources().genericKill(), event.getAmount());
                } finally {
                    applyingTrueDamage = false;
                }
                return;
            }
            // 未带印记：仅当「救赎之击」开启（C 键）时改为回满血；关闭则正常造成伤害
            if (CuriosEffectsState.respawnToggle(attacker)) {
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
                mob.removeAllEffects();
                setMaxHealth(mob, 100.0D, MOB_HEALTH_100);
                mob.setHealth(mob.getMaxHealth());
                CuriosEffectsState.setRedemptionMark(mob, true);
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
        if (!(event.getEntity() instanceof Player player) || player.isDeadOrDying()) {
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
        if (!phaseTwo(player)) {
            return;
        }
        grantDivineSwing(player);
    }

    private static void onTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getNewTarget() instanceof Player player && phaseTwo(player)) {
            event.setCanceled(true);
        }
    }

    private static void onXpChange(PlayerXpEvent.XpChange event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
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
