package com.divinebeast.divinebeast.curio;

import com.divinebeast.divinebeast.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 『兽』效果引擎（仅在 Curios 已安装时由 DivineBeastMod 加载）。三阶段：
 * <ol>
 *   <li><b>诅咒</b>（生于奇迹，行于混沌）：六件拯救法则有缺 → 缺失法则失控负面生效；佩戴同名法则即解除；</li>
 *   <li><b>拯救</b>（内生轮回，超脱永恒）：至高·智慧·生命·混沌·吞噬·轮回 齐（不含『自我』）；</li>
 *   <li><b>自·我</b>（生于轮回之内，超脱自我）：七件全齐。</li>
 * </ol>
 * 法则饰品同时作为钥匙；佩戴时提供各自的<b>驯服效果</b>（任何阶段生效）：
 * 至高=真伤+10%×10层增伤；智慧=受伤减半且 90% 免伤；生命=回复翻倍+受伤回半血；
 * 混沌=90% 双倍 / 9% 50% / 1% 99%（按敌方最大生命、无视护甲）；自我=最高 888 层（每层 +20 吸收/+20 攻击，
 * 每扣 2 点吸收减 1 层）；吞噬=饱食度作血盾（>4 触发），无饱食度（自·我）时不受伤害；轮回=每 180−等级/10 秒 +1 层，
 * 死亡消耗 1 层复活并刷新冷却、叠层拉满。
 */
public final class BeastEffects {

    private static final String BEAST_SLOT = "beast";
    private static final String TAG_SELF_LAYERS = "divinebeast.self_layers";      // 自·我 死亡层数
    private static final String TAG_SAMSARA_LAYERS = "divinebeast.samsara_layers"; // 轮回 层数
    private static final String TAG_SAMSARA_LAST = "divinebeast.samsara_last";     // 轮回 上次获得时刻
    private static final String TAG_M5_REVIVE = "divinebeast.m5_last_revive";      // 时刻5 复活冷却(可被轮回刷新)

    // 顺序：supreme wisdom life chaos self devour samsara
    private static final String[] LAW_SLOTS = {
            "supreme", "wisdom", "life", "chaos", "self", "devour", "samsara"
    };
    private static final net.minecraftforge.registries.RegistryObject<Item>[] LAW_ITEMS = newArray(
            ModItems.SUPREME, ModItems.WISDOM, ModItems.LIFE, ModItems.CHAOS,
            ModItems.SELF, ModItems.DEVOUR, ModItems.SAMSARA);
    private static final int[] REDEMPTION_LAWS = {0, 1, 2, 3, 5, 6};

    private static final int REDEMPTION_STUN_TICKS = 100;
    private static final int SELF_STUN_TICKS = 200;
    private static final int AGGRO_RADIUS = 16;
    private static final int SELF_CURSE_STACK_MAX = 88;
    private static final int SELF_LAYER_MAX = 100;
    private static final int SAMSARA_LAYER_MAX = 100;
    private static final int BUFF_GRACE_TICKS = 200;     // 10 秒
    private static final int SUPREME_STACK_MAX = 10;     // 至高层数上限
    private static final int SELF_LAW_STACK_MAX = 888;   // 自我(法则)层数上限

    private static final UUID ATTACK_BONUS_SELF_CURSE = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-000000002002");
    private static final UUID SELF_HEALTH_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-000000002003");
    private static final UUID SELF_ATTACK_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-000000002004");
    private static final UUID ARMOR_ZERO_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-000000002005");
    private static final UUID SELF_LAW_ATTACK_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-000000002006");

    private static final java.util.Map<Integer, Long> STUNNED_UNTIL = new java.util.HashMap<>();
    private static final java.util.Set<Integer> ARMLESS = new java.util.HashSet<>();
    private static final java.util.Map<UUID, Float> LAST_ATTACK_DAMAGE = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Long> LAST_DEALT_TICK = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Integer> SELF_CURSE_STACKS = new java.util.HashMap<>(); // 诅咒·自我
    private static final java.util.Map<UUID, Integer> SELF_LAW_STACKS = new java.util.HashMap<>();   // 法则·自我
    private static final java.util.Map<UUID, Integer> SUPREME_STACKS = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Long> SUPREME_TIMER = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Integer> FOOD_CAP = new java.util.HashMap<>();
    private static boolean applyingTrueDamage = false;
    private static boolean applyingSplash = false;

    private BeastEffects() {
    }

    @SuppressWarnings("unchecked")
    private static net.minecraftforge.registries.RegistryObject<Item>[] newArray(
            net.minecraftforge.registries.RegistryObject<Item>... items) {
        return items;
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(BeastEffects::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(BeastEffects::onLivingAttack);
        MinecraftForge.EVENT_BUS.addListener(BeastEffects::onLivingHurt);
        MinecraftForge.EVENT_BUS.addListener(BeastEffects::onLivingDamage);
        MinecraftForge.EVENT_BUS.addListener(BeastEffects::onLivingHeal);
        MinecraftForge.EVENT_BUS.addListener(BeastEffects::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(BeastEffects::onTargetChange);
    }

    // ==================================================================
    // 状态
    // ==================================================================

    private static boolean worn(LivingEntity entity, String slotId, Item item) {
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(entity).resolve();
        if (optional.isPresent()) {
            for (SlotResult result : optional.get().findCurios(slotId)) {
                ItemStack stack = result.stack();
                if (!stack.isEmpty() && stack.is(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean wearingBeast(LivingEntity entity) {
        return worn(entity, BEAST_SLOT, ModItems.BEAST.get());
    }

    private static boolean lawWorn(LivingEntity entity, int index) {
        return worn(entity, LAW_SLOTS[index], LAW_ITEMS[index].get());
    }

    /** 0=未戴『兽』 1=诅咒 2=拯救 3=自·我 */
    public static int stageOf(LivingEntity entity) {
        if (!wearingBeast(entity)) {
            return 0;
        }
        for (int index : REDEMPTION_LAWS) {
            if (!lawWorn(entity, index)) {
                return 1;
            }
        }
        return lawWorn(entity, 4) ? 3 : 2;
    }

    private static boolean curseActive(LivingEntity entity, int index) {
        return stageOf(entity) == 1 && !lawWorn(entity, index);
    }

    private static int lawCount(LivingEntity entity) {
        int count = 0;
        for (int i = 0; i < LAW_SLOTS.length; i++) {
            if (lawWorn(entity, i)) {
                count++;
            }
        }
        return count;
    }

    // ==================================================================
    // 刻循环
    // ==================================================================

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        long now = player.level().getGameTime();

        updateStuns(player.level(), now);

        if (!wearingBeast(player)) {
            return;
        }
        int stage = stageOf(player);

        // ---------- 诅咒持续项 ----------
        if (stage == 1) {
            if (curseActive(player, 5)) { // 吞噬诅咒
                if (!FOOD_CAP.containsKey(player.getUUID())) {
                    FOOD_CAP.put(player.getUUID(), player.getFoodData().getFoodLevel());
                }
                player.getFoodData().setSaturation(0.0F);
                int cap = FOOD_CAP.get(player.getUUID());
                if (player.getFoodData().getFoodLevel() > cap) {
                    player.getFoodData().setFoodLevel(cap);
                }
                if (player.getFoodData().getFoodLevel() <= 0 && player.tickCount % 20 == 0) {
                    player.hurt(player.damageSources().magic(), 2.0F);
                }
                player.causeFoodExhaustion(0.05F);
            } else {
                FOOD_CAP.remove(player.getUUID());
            }
            if (curseActive(player, 4)) { // 自我诅咒
                int stacks = SELF_CURSE_STACKS.getOrDefault(player.getUUID(), 0);
                if (player.tickCount % 200 == 0 && stacks < SELF_CURSE_STACK_MAX) {
                    SELF_CURSE_STACKS.put(player.getUUID(), ++stacks);
                    applySelfCurseStacks(player, stacks, ATTACK_BONUS_SELF_CURSE, "divinebeast_self_curse");
                }
                // 吸收心每被扣 2 点 → 减少一层（吸收会随受击自然下降，不每 tick 回满）
                float target = stacks * 2.0F;
                if (stacks > 0 && player.getAbsorptionAmount() < target - 2.0F) {
                    SELF_CURSE_STACKS.put(player.getUUID(), --stacks);
                    applySelfCurseStacks(player, stacks, ATTACK_BONUS_SELF_CURSE, "divinebeast_self_curse");
                }
            } else {
                if (SELF_CURSE_STACKS.remove(player.getUUID()) != null) {
                    clearAttackStacks(player, ATTACK_BONUS_SELF_CURSE);
                    player.setAbsorptionAmount(0.0F);
                }
            }
        } else {
            FOOD_CAP.remove(player.getUUID());
            if (SELF_CURSE_STACKS.remove(player.getUUID()) != null) {
                clearAttackStacks(player, ATTACK_BONUS_SELF_CURSE);
                player.setAbsorptionAmount(0.0F);
            }
        }

        // ---------- 法则（佩戴即生效）持续项 ----------
        // 至高：层数 10 秒无命中清零
        if (lawWorn(player, 0)) {
            Long lastSupreme = SUPREME_TIMER.get(player.getUUID());
            if (lastSupreme != null && now - lastSupreme > BUFF_GRACE_TICKS) {
                SUPREME_STACKS.remove(player.getUUID());
                SUPREME_TIMER.remove(player.getUUID());
            }
        } else {
            SUPREME_STACKS.remove(player.getUUID());
            SUPREME_TIMER.remove(player.getUUID());
        }
        // 自我（法则）：每 5 秒 +1 层（上限 888）；每扣 2 点吸收减 1 层；同步属性
        if (lawWorn(player, 4)) {
            int stacks = SELF_LAW_STACKS.getOrDefault(player.getUUID(), 0);
            if (player.tickCount % 100 == 0 && stacks < SELF_LAW_STACK_MAX) {
                SELF_LAW_STACKS.put(player.getUUID(), ++stacks);
                syncSelfLaw(player, stacks);
            }
            // 每扣 2 点吸收 → 减 1 层（吸收随受击自然下降，不每 tick 回满）
            float target = stacks * 20.0F;
            if (stacks > 0 && player.getAbsorptionAmount() < target - 2.0F) {
                SELF_LAW_STACKS.put(player.getUUID(), --stacks);
                syncSelfLaw(player, stacks);
            }
        } else {
            if (SELF_LAW_STACKS.remove(player.getUUID()) != null) {
                clearAttackStacks(player, SELF_LAW_ATTACK_MOD);
                player.setAbsorptionAmount(0.0F);
            }
        }
        // 轮回（法则）：每 (180 − 等级/10) 秒 +1 层
        if (lawWorn(player, 6)) {
            regenSamsara(player, now);
        }

        // ---------- 拯救持续项 ----------
        if (stage == 2) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5.0F);
            if (player.tickCount % 60 == 0) {
                driveMobRampage(player);
            }
        }

        // ---------- 自·我 持续项 ----------
        if (stage == 3) {
            tickSelfStage(player, now);
        } else {
            clearSelfStageAttributes(player);
        }
    }

    // ---------------------------------------------------------------
    // 自·我 持续
    // ---------------------------------------------------------------

    private static void tickSelfStage(Player player, long now) {
        // 饱食度消除：直接没有饱食度（不触发饥饿与恢复）
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);

        int layers = Math.min(SELF_LAYER_MAX, selfLayers(player));
        double bonus = 1.0D + layers * 0.1D;
        applyMultiplier(player, Attributes.MAX_HEALTH, SELF_HEALTH_MOD, bonus);
        applyMultiplier(player, Attributes.ATTACK_DAMAGE, SELF_ATTACK_MOD, bonus);

        Long lastDealt = LAST_DEALT_TICK.get(player.getUUID());
        if (lastDealt != null && now - lastDealt <= BUFF_GRACE_TICKS) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BUFF_GRACE_TICKS, 5));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, BUFF_GRACE_TICKS, 4));
        } else if (lastDealt != null) {
            LAST_DEALT_TICK.remove(player.getUUID());
            player.removeEffect(MobEffects.DAMAGE_BOOST);
            player.removeEffect(MobEffects.ABSORPTION);
        }
    }

    private static void applyMultiplier(Player player,
                                        net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                        UUID uuid, double bonusMultiplier) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(uuid);
        double natural = instance.getValue();
        double delta = natural * bonusMultiplier;
        if (delta > 0.001D) {
            instance.addPermanentModifier(new AttributeModifier(uuid, "divinebeast_self", delta,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    private static void clearSelfStageAttributes(Player player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(SELF_HEALTH_MOD);
        }
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(SELF_ATTACK_MOD);
        }
    }

    private static int selfLayers(Player player) {
        return player.getPersistentData().contains(TAG_SELF_LAYERS)
                ? player.getPersistentData().getInt(TAG_SELF_LAYERS) : 0;
    }

    private static void addSelfLayer(Player player) {
        CompoundTag tag = player.getPersistentData();
        tag.putInt(TAG_SELF_LAYERS, Math.min(SELF_LAYER_MAX, selfLayers(player) + 1));
    }

    // ---------------------------------------------------------------
    // 自我/至高 层数工具
    // ---------------------------------------------------------------

    private static void syncSelfLaw(Player player, int stacks) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(SELF_LAW_ATTACK_MOD);
            if (stacks > 0) {
                attack.addPermanentModifier(new AttributeModifier(SELF_LAW_ATTACK_MOD,
                        "divinebeast_self_law", stacks * 20.0D, AttributeModifier.Operation.ADDITION));
            }
        }
        player.setAbsorptionAmount(stacks * 20.0F);
    }

    private static void applySelfCurseStacks(Player player, int stacks, UUID uuid, String name) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(uuid);
            if (stacks > 0) {
                attack.addPermanentModifier(new AttributeModifier(uuid, name, stacks * 2.0D,
                        AttributeModifier.Operation.ADDITION));
            }
        }
        player.setAbsorptionAmount(stacks * 2.0F);
    }

    private static void clearAttackStacks(Player player, UUID uuid) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(uuid);
        }
    }

    // ---------------------------------------------------------------
    // 轮回（法则）：层数生成 / 死亡复活
    // ---------------------------------------------------------------

    private static void regenSamsara(Player player, long now) {
        CompoundTag tag = player.getPersistentData();
        int layers = tag.contains(TAG_SAMSARA_LAYERS) ? tag.getInt(TAG_SAMSARA_LAYERS) : 0;
        if (layers >= SAMSARA_LAYER_MAX) {
            return;
        }
        long last = tag.getLong(TAG_SAMSARA_LAST);
        int cdSeconds = Math.max(1, 180 - player.experienceLevel / 10);
        if (last == 0L || now - last >= cdSeconds * 20L) {
            tag.putInt(TAG_SAMSARA_LAYERS, layers + 1);
            tag.putLong(TAG_SAMSARA_LAST, now);
        }
    }

    private static int samsaraLayers(Player player) {
        return player.getPersistentData().contains(TAG_SAMSARA_LAYERS)
                ? player.getPersistentData().getInt(TAG_SAMSARA_LAYERS) : 0;
    }

    // ==================================================================
    // 攻击命中：停顿（拯救/自·我）
    // ==================================================================

    private static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide || applyingSplash) {
            return;
        }
        LivingEntity victim = event.getEntity();
        Player attacker = attackingPlayer(event.getSource());
        if (attacker == null || victim.is(attacker) || !wearingBeast(attacker)) {
            return;
        }
        if (curseActive(attacker, 3) && attacker.level().getRandom().nextDouble() < 0.5D) {
            event.setCanceled(true); // 混沌诅咒：50% 落空
            return;
        }
        if (victim instanceof Player) {
            return;
        }
        int stage = stageOf(attacker);
        if (stage == 2) {
            stun(victim, REDEMPTION_STUN_TICKS);
        } else if (stage == 3) {
            stun(victim, SELF_STUN_TICKS);
            ARMLESS.add(victim.getId());
        }
    }

    // ==================================================================
    // 受到伤害（佩戴『兽』玩家自身）
    // ==================================================================

    private static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player player) || !wearingBeast(player)) {
            return;
        }
        DamageSource source = event.getSource();
        if (curseActive(player, 1) && isNonPhysical(source)) {
            event.setAmount(event.getAmount() * 2.0F); // 智慧诅咒：非物理翻倍
        }
        if (curseActive(player, 3) && player.level().getRandom().nextDouble() < 0.5D) {
            event.setAmount(event.getAmount() * 2.0F); // 混沌诅咒：受击 50% 翻倍
        }
        // 智慧（法则）：受到伤害减半，并有 90% 概率完全无效
        if (lawWorn(player, 1)) {
            if (player.level().getRandom().nextDouble() < 0.9D) {
                event.setCanceled(true);
                return;
            }
            event.setAmount(event.getAmount() * 0.5F);
        }
        // 吞噬（法则）：饱食度血盾；自·我(无饱食) 时不受伤害（在 damage 结算前优先拦截）
        if (lawWorn(player, 5)) {
            int stage = stageOf(player);
            if (stage == 3) {
                event.setCanceled(true); // 无饱食度 → 免疫本次伤害
                return;
            }
            int food = player.getFoodData().getFoodLevel();
            if (food > 4) {
                float amount = event.getAmount();
                int block = (int) Math.ceil(amount);
                if (block > food - 4) {
                    block = food - 4;
                }
                player.getFoodData().setFoodLevel(food - block);
                event.setAmount(Math.max(0.0F, amount - block));
            }
        }
    }

    private static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player player) || !wearingBeast(player)) {
            return;
        }
        if (curseActive(player, 2)) {
            float amount = event.getAmount();
            event.setCanceled(true);
            player.hurt(player.damageSources().magic(), amount); // 生命诅咒：治疗反噬
        } else if (lawWorn(player, 2)) {
            event.setAmount(event.getAmount() * 2.0F); // 生命（法则）：任何来源恢复翻倍
        }
    }

    // ==================================================================
    // 造成伤害（结算）
    // ==================================================================

    private static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || applyingTrueDamage || applyingSplash) {
            return;
        }
        LivingEntity victim = event.getEntity();

        // ---------- 佩戴『兽』玩家作为受击方 ----------
        if (victim instanceof Player victimPlayer && wearingBeast(victimPlayer)) {
            if (curseActive(victimPlayer, 6) && !victimPlayer.isDeadOrDying()
                    && event.getAmount() >= victimPlayer.getHealth()) {
                event.setCanceled(true); // 轮回诅咒：传送回出生点、损失 50% 当前生命
                sendToSpawn(victimPlayer);
                victimPlayer.setHealth(Math.max(1.0F, victimPlayer.getHealth() * 0.5F));
                return;
            }
            // 生命（法则）：受到伤害时回复该伤害一半的生命值
            if (lawWorn(victimPlayer, 2)) {
                victimPlayer.heal(event.getAmount() * 0.5F);
            }
        }

        // ---------- 佩戴『兽』玩家作为攻击方 ----------
        Player attacker = attackingPlayer(event.getSource());
        if (attacker == null || victim.is(attacker) || !wearingBeast(attacker)) {
            return;
        }
        int stage = stageOf(attacker);
        boolean curseSupreme = curseActive(attacker, 0);
        boolean lawSupreme = lawWorn(attacker, 0);
        boolean lawChaos = lawWorn(attacker, 3);
        boolean lawSelf = lawWorn(attacker, 4);

        float amount = event.getAmount();
        boolean chaosPercentTrue = false;

        // 至高诅咒：固定 1 点
        if (curseSupreme) {
            event.setAmount(1.0F);
            amount = 1.0F;
        } else {
            // 混沌（法则）：90% ×2 / 9% 50% / 1% 99%（按敌方最大生命，无视护甲）
            if (lawChaos) {
                double roll = attacker.level().getRandom().nextDouble();
                if (roll < 0.01D) {
                    amount = victim.getMaxHealth() * 0.99F;
                    chaosPercentTrue = true;
                } else if (roll < 0.10D) {
                    amount = victim.getMaxHealth() * 0.50F;
                    chaosPercentTrue = true;
                } else {
                    amount = amount * 2.0F;
                }
            }
            // 至高（法则）：层数增伤 +10%×层
            if (lawSupreme) {
                int stacks = SUPREME_STACKS.getOrDefault(attacker.getUUID(), 0);
                amount = amount * (1.0F + 0.1F * Math.min(stacks, SUPREME_STACK_MAX));
            }
        }

        // 停顿目标增伤：拯救 +50% / 自·我 +200%
        if (isStunned(victim)) {
            if (stage == 2) {
                amount = amount * 1.5F;
            } else if (stage == 3) {
                amount = amount * 3.0F;
            }
        }

        float finalAmount = Math.max(0.0F, amount);
        boolean trueHit = lawSupreme || chaosPercentTrue || curseSupreme;

        // 叠加层 / 记录
        if (lawSupreme && finalAmount > 0.0F) {
            SUPREME_STACKS.put(attacker.getUUID(),
                    Math.min(SUPREME_STACK_MAX, SUPREME_STACKS.getOrDefault(attacker.getUUID(), 0) + 1));
            SUPREME_TIMER.put(attacker.getUUID(), attacker.level().getGameTime());
        }
        if (lawSelf && finalAmount > 0.0F) {
            SELF_LAW_STACKS.put(attacker.getUUID(),
                    Math.min(SELF_LAW_STACK_MAX, SELF_LAW_STACKS.getOrDefault(attacker.getUUID(), 0) + 1));
        }
        LAST_ATTACK_DAMAGE.put(attacker.getUUID(), finalAmount);
        LAST_DEALT_TICK.put(attacker.getUUID(), attacker.level().getGameTime());

        // 施放伤害（真伤或普通）
        if (trueHit) {
            event.setCanceled(true);
            applyingTrueDamage = true;
            try {
                victim.hurt(victim.damageSources().genericKill(), finalAmount);
            } finally {
                applyingTrueDamage = false;
            }
        } else {
            event.setAmount(finalAmount);
        }

        // 吸血：自·我 100% / 拯救 25%
        float steal = 0.0F;
        if (stage == 3) {
            steal = finalAmount;
        } else if (stage == 2) {
            steal = finalAmount * 0.25F;
        }
        if (steal > 0.0F) {
            attacker.heal(steal);
        }

        // 自·我 AoE：16 格内敌对生物承受等额伤害
        if (stage == 3 && finalAmount > 0.0F) {
            splashDamage(attacker, victim, finalAmount);
        }
    }

    private static void splashDamage(Player attacker, LivingEntity primaryVictim, float amount) {
        Level level = attacker.level();
        List<Monster> mobs = level.getEntitiesOfClass(Monster.class,
                AABB.ofSize(attacker.position(), AGGRO_RADIUS * 2, AGGRO_RADIUS * 2, AGGRO_RADIUS * 2));
        applyingSplash = true;
        try {
            for (Monster mob : mobs) {
                if (mob.is(primaryVictim) || !mob.isAlive()) {
                    continue;
                }
                mob.hurt(attacker.damageSources().mobAttack(attacker), amount);
            }
        } finally {
            applyingSplash = false;
        }
    }

    // ==================================================================
    // 死亡：轮回（法则）复活 / 拯救·自·我·轮回 不掉落 / 自·我叠层
    // ==================================================================

    private static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity dead = event.getEntity();

        // ---------- 佩戴『兽』者自身死亡 ----------
        if (dead instanceof ServerPlayer serverPlayer && wearingBeast(serverPlayer)) {
            int stage = stageOf(serverPlayer);
            // 轮回（法则）：消耗 1 层复活并刷新（本模组可刷新项 + 叠层拉满）
            if (lawWorn(serverPlayer, 6) && samsaraLayers(serverPlayer) > 0) {
                event.setCanceled(true);
                CompoundTag tag = serverPlayer.getPersistentData();
                tag.putInt(TAG_SAMSARA_LAYERS, samsaraLayers(serverPlayer) - 1);
                tag.putLong(TAG_M5_REVIVE, 0L); // 刷新时刻5 复活冷却
                refreshLawStacks(serverPlayer);
                serverPlayer.setHealth(1.0F);
                serverPlayer.fallDistance = 0.0F;
                serverPlayer.sendSystemMessage(
                        net.minecraft.network.chat.Component.translatable("divinebeast.msg.samsara_revive"));
                return;
            }
            if (stage == 3) {
                addSelfLayer(serverPlayer); // 自·我：每次死亡 +10% 层数
            }
            boolean keepAll = stage == 2 || stage == 3; // 拯救/自·我：死亡不掉落
            if (lawWorn(serverPlayer, 6)) {
                keepAll = true; // 轮回（法则）：死亡不掉落/经验
            }
            if (keepAll) {
                temporaryKeepInventory(serverPlayer);
            }
        }

        // ---------- 玩家击杀其它实体 ----------
        Player killer = null;
        Entity source = event.getSource().getEntity();
        if (source instanceof Player player) {
            killer = player;
        } else if (source instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof Player player) {
            killer = player;
        }
        if (killer == null || !wearingBeast(killer)) {
            return;
        }
        Float last = LAST_ATTACK_DAMAGE.get(killer.getUUID());
        if (stageOf(killer) == 2) {
            float heal = last == null ? 4.0F : last;
            killer.heal(heal); // 拯救击杀：全额回血 + 力量V/吸收IV 5 秒
            killer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 3));
            killer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 4));
        }
    }

    /** 轮回复活：将本模组可控的"叠层"拉满 */
    private static void refreshLawStacks(Player player) {
        SUPREME_STACKS.put(player.getUUID(), SUPREME_STACK_MAX);
        SUPREME_TIMER.put(player.getUUID(), player.level().getGameTime());
        int self = SELF_LAW_STACKS.getOrDefault(player.getUUID(), 0);
        if (self < SELF_LAW_STACK_MAX) {
            SELF_LAW_STACKS.put(player.getUUID(), SELF_LAW_STACK_MAX);
            syncSelfLaw(player, SELF_LAW_STACK_MAX);
        }
    }

    private static void temporaryKeepInventory(ServerPlayer player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        GameRules.BooleanValue keep = serverLevel.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
        if (!keep.get()) {
            keep.set(true, serverLevel.getServer());
            serverLevel.getServer().execute(() -> keep.set(false, serverLevel.getServer()));
        }
    }

    // ==================================================================
    // 目标拦截 / 兽群互斗
    // ==================================================================

    private static void onTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getNewTarget() instanceof Player player && stageOf(player) == 2) {
            event.setCanceled(true); // 拯救：不再优先攻击你
        }
    }

    private static void driveMobRampage(Player player) {
        Level level = player.level();
        List<Monster> mobs = level.getEntitiesOfClass(Monster.class,
                AABB.ofSize(player.position(), AGGRO_RADIUS * 2, AGGRO_RADIUS * 2, AGGRO_RADIUS * 2));
        for (Monster mob : mobs) {
            if (mob.getTarget() == player || mob.getTarget() == null) {
                Monster best = null;
                double bestDist = Double.MAX_VALUE;
                for (Monster other : mobs) {
                    if (other == mob) {
                        continue;
                    }
                    double dist = mob.distanceToSqr(other);
                    if (dist < bestDist) {
                        best = other;
                        bestDist = dist;
                    }
                }
                if (best != null) {
                    mob.setTarget(best);
                }
            }
        }
    }

    // ==================================================================
    // 停顿 & 护甲消除
    // ==================================================================

    private static void stun(LivingEntity entity, int ticks) {
        STUNNED_UNTIL.put(entity.getId(), entity.level().getGameTime() + ticks);
        if (entity instanceof Mob mob && !mob.isNoAi()) {
            mob.setNoAi(true);
        }
    }

    private static boolean isStunned(LivingEntity entity) {
        Long until = STUNNED_UNTIL.get(entity.getId());
        return until != null && entity.level().getGameTime() < until;
    }

    private static void updateStuns(Level level, long now) {
        if (STUNNED_UNTIL.isEmpty()) {
            return;
        }
        java.util.Iterator<java.util.Map.Entry<Integer, Long>> it = STUNNED_UNTIL.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, Long> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || !entity.isAlive() || now >= entry.getValue()) {
                if (entity instanceof Mob mob && mob.isNoAi()) {
                    mob.setNoAi(false);
                }
                if (entity instanceof LivingEntity living) {
                    if (ARMLESS.remove(entity.getId())) {
                        restoreArmor(living);
                    }
                }
                it.remove();
                continue;
            }
            entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
            if (ARMLESS.contains(entity.getId()) && entity instanceof LivingEntity living) {
                zeroArmor(living);
            }
        }
    }

    private static void zeroArmor(LivingEntity entity) {
        AttributeInstance armor = entity.getAttribute(Attributes.ARMOR);
        if (armor == null) {
            return;
        }
        armor.removeModifier(ARMOR_ZERO_MOD);
        double value = armor.getValue();
        if (value > 0.0D) {
            armor.addPermanentModifier(new AttributeModifier(ARMOR_ZERO_MOD, "divinebeast_armor_off",
                    -value, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void restoreArmor(LivingEntity entity) {
        AttributeInstance armor = entity.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(ARMOR_ZERO_MOD);
        }
    }

    // ==================================================================
    // 工具
    // ==================================================================

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

    /** 非物理伤害近似：没有直接攻击实体（火焰/岩浆/溺水/坠落/魔法等），或来源非近战接触 */
    private static boolean isNonPhysical(DamageSource source) {
        return source.getDirectEntity() == null;
    }

    private static void sendToSpawn(Player player) {
        BlockPos respawn = null;
        if (player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.getRespawnPosition() != null
                    && serverPlayer.getRespawnDimension() == player.level().dimension()) {
                respawn = serverPlayer.getRespawnPosition();
            }
        }
        if (respawn == null) {
            respawn = player.level().getSharedSpawnPos();
        }
        player.teleportTo(respawn.getX() + 0.5D, respawn.getY() + 1.0D, respawn.getZ() + 0.5D);
        player.fallDistance = 0.0F;
    }

    // ==================================================================
    // 供 tooltip
    // ==================================================================

    public static int lawProgress(LivingEntity entity) {
        return wearingBeast(entity) ? lawCount(entity) : 0;
    }

    /** 供客户端 tooltip：某条法则是否已佩戴（其诅咒已解除） */
    public static boolean lawEquipped(LivingEntity entity, int index) {
        return index >= 0 && index < LAW_SLOTS.length && lawWorn(entity, index);
    }
}
