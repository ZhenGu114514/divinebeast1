package com.divinebeast.divinebeast.curio;

import com.divinebeast.divinebeast.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 五件「时刻」饰品的<b>佩戴效果</b>引擎（Curios 存在时由 DivineBeastMod 加载）。
 *
 * <p>规则背景：『祂』阶段逻辑不变（时刻未佩戴 → 对应负面生效；佩戴 → 解除该负面）；
 * 本类实现"佩戴时"额外获得的效果，阶段二（集齐救赎）仍与本类效果叠加：
 *
 * <ul>
 *   <li>不存在不存在时刻：生命上限 / 经验 / 工具耐久 / 护甲值 四项完全不会下降
 *       （耐久即时修复；生命上限与护甲按"运行中最高值"水位线补偿；经验按最高水位恢复）</li>
 *   <li>不存在存在时刻：攻击命中的目标 3 秒禁疗；自身受到伤害时伤害来源承受等额真伤</li>
 *   <li>可能存在存在时刻：受到伤害时 90% 概率完全不受此伤害</li>
 *   <li>已存在存在时刻：正面药水效果每秒 +2 秒持续；每秒回复已损失生命 (10 + 效果数)%，至少 1 点；
 *       药水可存储（潜行+右键药水存入，手持本物品右键清空）；佩戴时自动施加存储效果并持续维持</li>
 *   <li>不可能存在不可能存在时刻：死亡时复活并获得 20 秒无敌；冷却 180 秒 − 经验等级 秒</li>
 * </ul>
 */
public final class MomentEffects {

    private static final String SLOT_NONEXIST_NONEXIST = "nonexist_nonexist";
    private static final String SLOT_NONEXIST_EXIST = "nonexist_exist";
    private static final String SLOT_MAYBE_EXIST = "maybe_exist";
    private static final String SLOT_EXIST_EXIST = "exist_exist";
    private static final String SLOT_IMPOSSIBLE_NONEXIST = "impossible_nonexist";

    // 药水存储 NBT
    private static final String TAG_STORED_EFFECTS = "divinebeast.stored_effects";
    private static final String TAG_EFFECT_ID = "effect";
    private static final String TAG_AMPLIFIER = "amplifier";
    private static final String TAG_DURATION = "duration";
    // 复活冷却持久化
    private static final String TAG_LAST_REVIVE = "divinebeast.m5_last_revive";

    // 属性水位补偿用固定 UUID
    private static final UUID COMP_MAX_HEALTH = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-moment0000001");
    private static final UUID COMP_ARMOR = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-moment0000002");

    private static final int NO_HEAL_TICKS = 60;          // 禁疗 3 秒
    private static final double NEGATE_CHANCE = 0.9D;      // 90% 免伤
    private static final int MAX_REFLECT_DEPTH = 4;
    private static final int EXTEND_TICKS_PER_SECOND = 40; // +2 秒/秒
    private static final long EXTEND_CAP_TICKS = 3_600_000L;
    private static final int REVIVE_INVULN_TICKS = 400;    // 20 秒无敌
    private static final int BASE_REVIVE_CD_SECONDS = 180; // 基础冷却 180 秒
    private static final int MAX_STORED_EFFECTS = 12;

    // 禁疗：entityId -> 到期 gameTime
    private static final java.util.Map<Integer, Long> NO_HEAL_UNTIL = new java.util.HashMap<>();
    // 水位：player uuid -> double
    private static final java.util.Map<UUID, Double> MAX_HEALTH_WATERMARK = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Double> ARMOR_WATERMARK = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Integer> XP_TOTAL_WATERMARK = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Integer> XP_LEVEL_WATERMARK = new java.util.HashMap<>();
    // 复活无敌到期：player uuid -> gameTime
    private static final java.util.Map<UUID, Long> INVULN_UNTIL = new java.util.HashMap<>();
    // 反射递归深度
    private static int reflectDepth = 0;

    private MomentEffects() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(MomentEffects::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(MomentEffects::onLivingAttack);
        MinecraftForge.EVENT_BUS.addListener(MomentEffects::onLivingHurt);
        MinecraftForge.EVENT_BUS.addListener(MomentEffects::onLivingHeal);
        MinecraftForge.EVENT_BUS.addListener(MomentEffects::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(MomentEffects::onRightClick);
    }

    // ==================================================================
    // 佩戴判定
    // ==================================================================

    private static boolean isWorn(LivingEntity entity, String slotId, Item item) {
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(entity);
        if (optional.isEmpty()) {
            return false;
        }
        for (SlotResult result : optional.get().findCurios(slotId)) {
            if (!result.getStack().isEmpty() && result.getStack().is(item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean wornNonExistNonExist(LivingEntity e) {
        return isWorn(e, SLOT_NONEXIST_NONEXIST, ModItems.NONEXIST_NONEXIST.get());
    }

    private static boolean wornNonExistExist(LivingEntity e) {
        return isWorn(e, SLOT_NONEXIST_EXIST, ModItems.NONEXIST_EXIST.get());
    }

    private static boolean wornMaybeExist(LivingEntity e) {
        return isWorn(e, SLOT_MAYBE_EXIST, ModItems.MAYBE_EXIST.get());
    }

    private static boolean wornExistExist(LivingEntity e) {
        return isWorn(e, SLOT_EXIST_EXIST, ModItems.EXIST_EXIST.get());
    }

    private static boolean wornImpossibleNonExist(LivingEntity e) {
        return isWorn(e, SLOT_IMPOSSIBLE_NONEXIST, ModItems.IMPOSSIBLE_NONEXIST.get());
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

        // 无敌到期自动解除
        Long invulnUntil = INVULN_UNTIL.get(player.getUUID());
        if (invulnUntil != null) {
            if (now >= invulnUntil) {
                player.setInvulnerable(false);
                INVULN_UNTIL.remove(player.getUUID());
            }
        }

        if (wornNonExistNonExist(player)) {
            if (player.tickCount % 10 == 0) {
                enforceNoStatDecrease(player);
            }
        } else if (hasNoStatDecreaseData(player)) {
            clearNoStatDecrease(player);
        }

        if (wornExistExist(player) && player.tickCount % 20 == 0) {
            tickExistExist(player);
        }
    }

    // ---------------------------------------------------------------
    // 不存在不存在时刻：四项完全不会下降
    // ---------------------------------------------------------------

    private static void enforceNoStatDecrease(Player player) {
        // 1) 工具耐久：即时修复为满
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().items);
        stacks.add(player.getOffhandItem());
        stacks.addAll(player.getInventory().armor);
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() > 0) {
                stack.setDamageValue(0);
            }
        }
        // 2) 生命上限与护甲：水位线补偿（取佩戴期间出现过的最新值）
        lockToWatermark(player, player.getAttribute(Attributes.MAX_HEALTH), COMP_MAX_HEALTH, MAX_HEALTH_WATERMARK);
        lockToWatermark(player, player.getAttribute(Attributes.ARMOR), COMP_ARMOR, ARMOR_WATERMARK);
        // 3) 经验：总量与等级水位恢复（含防死亡/防附魔扣除）
        int total = player.totalExperience;
        int level = player.experienceLevel;
        XP_TOTAL_WATERMARK.merge(player.getUUID(), total, Math::max);
        XP_LEVEL_WATERMARK.merge(player.getUUID(), level, Math::max);
        int floorTotal = XP_TOTAL_WATERMARK.get(player.getUUID());
        int floorLevel = XP_LEVEL_WATERMARK.get(player.getUUID());
        if (total < floorTotal) {
            player.giveExperiencePoints(floorTotal - total);
        }
        if (player.experienceLevel < floorLevel) {
            player.giveExperienceLevels(floorLevel - player.experienceLevel);
        }
    }

    private static void lockToWatermark(Player player, AttributeInstance instance, UUID compUuid,
                                        java.util.Map<UUID, Double> watermark) {
        if (instance == null) {
            return;
        }
        instance.removeModifier(compUuid);
        double natural = instance.getValue();
        double floor = watermark.getOrDefault(player.getUUID(), natural);
        floor = Math.max(floor, natural);
        watermark.put(player.getUUID(), floor);
        double delta = floor - natural;
        if (Math.abs(delta) > 0.001D) {
            instance.addPermanentModifier(new AttributeModifier(compUuid, "divinebeast_moment1", delta,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    private static boolean hasNoStatDecreaseData(Player player) {
        UUID uuid = player.getUUID();
        if (MAX_HEALTH_WATERMARK.containsKey(uuid) || ARMOR_WATERMARK.containsKey(uuid)
                || XP_TOTAL_WATERMARK.containsKey(uuid) || XP_LEVEL_WATERMARK.containsKey(uuid)) {
            return true;
        }
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        return (health != null && health.getModifier(COMP_MAX_HEALTH) != null)
                || (armor != null && armor.getModifier(COMP_ARMOR) != null);
    }

    private static void clearNoStatDecrease(Player player) {
        UUID uuid = player.getUUID();
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(COMP_MAX_HEALTH);
        }
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(COMP_ARMOR);
        }
        MAX_HEALTH_WATERMARK.remove(uuid);
        ARMOR_WATERMARK.remove(uuid);
        XP_TOTAL_WATERMARK.remove(uuid);
        XP_LEVEL_WATERMARK.remove(uuid);
    }

    // ---------------------------------------------------------------
    // 不存在存在时刻：禁疗 + 反射
    // ---------------------------------------------------------------

    private static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Player attacker = attackingPlayer(event.getSource());
        if (attacker == null || victim.is(attacker)) {
            return;
        }
        if (wornNonExistExist(attacker)) {
            NO_HEAL_UNTIL.put(victim.getId(), victim.level().getGameTime() + NO_HEAL_TICKS);
        }
    }

    private static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity entity = event.getEntity();
        Long until = NO_HEAL_UNTIL.get(entity.getId());
        if (until != null) {
            if (entity.level().getGameTime() < until) {
                event.setCanceled(true);
            } else {
                NO_HEAL_UNTIL.remove(entity.getId());
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

        // 可能存在存在时刻：90% 概率不受此伤害
        if (wornMaybeExist(player) && player.level().getRandom().nextDouble() < NEGATE_CHANCE) {
            event.setCanceled(true);
            return;
        }

        // 不存在存在时刻：受击时让伤害来源承受等额伤害
        if (wornNonExistExist(player) && reflectDepth < MAX_REFLECT_DEPTH) {
            LivingEntity sourceLiving = livingAttacker(source);
            if (sourceLiving != null && !sourceLiving.is(player)) {
                reflectDepth++;
                try {
                    sourceLiving.hurt(sourceLiving.damageSources().genericKill(), event.getAmount());
                } finally {
                    reflectDepth--;
                }
            }
        }
    }

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

    private static LivingEntity livingAttacker(DamageSource source) {
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

    // ---------------------------------------------------------------
    // 已存在存在时刻：药水 +2 秒/秒、百分比回血、药水存储/自动施加
    // ---------------------------------------------------------------

    private static void tickExistExist(Player player) {
        // 1) 正面药水效果每秒 +2 秒
        List<MobEffectInstance> active = new ArrayList<>(player.getActiveEffects());
        for (MobEffectInstance instance : active) {
            MobEffect effect = instance.getEffect();
            if (!effect.isBeneficial()) {
                continue;
            }
            long newDuration = Math.min(EXTEND_CAP_TICKS, (long) instance.getDuration() + EXTEND_TICKS_PER_SECOND);
            if (newDuration != instance.getDuration()) {
                player.removeEffect(effect);
                player.addEffect(new MobEffectInstance(effect, (int) newDuration, instance.getAmplifier()));
            }
        }

        // 2) 自动施加存储的正面药水
        applyStoredEffects(player);

        // 3) 回血：(10 + 正面效果数)% 已损失生命，至少 1 点
        int beneficialCount = 0;
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (instance.getEffect().isBeneficial()) {
                beneficialCount++;
            }
        }
        float missing = player.getMaxHealth() - player.getHealth();
        if (missing > 0.0F) {
            float percent = 10.0F + beneficialCount;
            float heal = Math.max(1.0F, missing * percent / 100.0F);
            player.heal(heal);
        }
    }

    /** 在物品/饰品栏/物品栏中查找一叠「已存在存在时刻」 */
    private static ItemStack findExistExistContainer(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.EXIST_EXIST.get())) {
                return stack;
            }
        }
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(player);
        if (optional.isPresent()) {
            for (SlotResult result : optional.get().findCurios(ModItems.EXIST_EXIST.get())) {
                return result.getStack();
            }
        }
        return ItemStack.EMPTY;
    }

    private static void storePotion(Player player, ItemStack potion, ItemStack container) {
        List<MobEffectInstance> effects = new ArrayList<>();
        effects.addAll(PotionUtils.getMobEffects(potion));
        effects.addAll(PotionUtils.getCustomEffects(potion));
        if (effects.isEmpty()) {
            player.sendSystemMessage(Component.translatable("divinebeast.msg.potion_empty"));
            return;
        }
        CompoundTag tag = container.getOrCreateTag();
        ListTag list = tag.getList(TAG_STORED_EFFECTS, 10);
        while (list.size() >= MAX_STORED_EFFECTS) {
            list.remove(0);
        }
        int stored = 0;
        for (MobEffectInstance instance : effects) {
            net.minecraft.resources.ResourceLocation key =
                    ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect());
            if (key == null) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_EFFECT_ID, key.toString());
            entry.putInt(TAG_AMPLIFIER, instance.getAmplifier());
            entry.putInt(TAG_DURATION, instance.getDuration());
            list.add(entry);
            stored++;
        }
        tag.put(TAG_STORED_EFFECTS, list);
        // 消耗这瓶药水（创造模式除外）
        if (!player.getAbilities().instabuild) {
            potion.shrink(1);
        }
        player.sendSystemMessage(Component.translatable("divinebeast.msg.potion_stored", stored));
    }

    private static void applyStoredEffects(Player player) {
        ItemStack container = findExistExistContainer(player);
        if (container.isEmpty()) {
            return;
        }
        CompoundTag tag = container.getTag();
        if (tag == null || !tag.contains(TAG_STORED_EFFECTS)) {
            return;
        }
        ListTag list = tag.getList(TAG_STORED_EFFECTS, 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(
                    net.minecraft.resources.ResourceLocation.tryParse(entry.getString(TAG_EFFECT_ID)));
            if (effect == null) {
                continue;
            }
            int duration = entry.getInt(TAG_DURATION);
            int amplifier = entry.getInt(TAG_AMPLIFIER);
            MobEffectInstance existing = player.getEffect(effect);
            if (existing == null || existing.getDuration() < duration) {
                player.addEffect(new MobEffectInstance(effect, duration, amplifier));
            }
        }
    }

    /** 右键交互：手持本物品→清空；潜行手持药水→存入（需包内/佩戴存在『已存在存在时刻』） */
    private static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());
        if (held.is(ModItems.EXIST_EXIST.get())) {
            // 手持『已存在存在时刻』右键 → 清空存储
            CompoundTag tag = held.getTag();
            if (tag != null) {
                tag.remove(TAG_STORED_EFFECTS);
            }
            player.sendSystemMessage(Component.translatable("divinebeast.msg.potion_cleared"));
            event.setCanceled(true);
            return;
        }
        if (player.isShiftKeyDown() && isPotion(held)) {
            ItemStack container = findExistExistContainer(player);
            if (!container.isEmpty() && !container.is(held)) {
                storePotion(player, held, container);
                event.setCanceled(true);
            }
        }
    }

    private static boolean isPotion(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof PotionItem || item instanceof SplashPotionItem || item instanceof LingeringPotionItem;
    }

    // ---------------------------------------------------------------
    // 不可能存在不可能存在时刻：死亡复活 + 20 秒无敌（冷却 180 - 等级 秒）
    // ---------------------------------------------------------------

    private static void onLivingDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!wornImpossibleNonExist(player)) {
            return;
        }
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLong(TAG_LAST_REVIVE);
        int cooldownSeconds = Math.max(1, BASE_REVIVE_CD_SECONDS - player.experienceLevel);
        long cooldownTicks = cooldownSeconds * 20L;
        if (last != 0L && now - last < cooldownTicks) {
            long remaining = (cooldownTicks - (now - last)) / 20L;
            player.sendSystemMessage(Component.translatable("divinebeast.msg.m5_cooldown", remaining));
            return;
        }
        event.setCanceled(true);
        player.getPersistentData().putLong(TAG_LAST_REVIVE, now);
        player.setHealth(1.0F);
        player.fallDistance = 0.0F;
        player.removeAllEffects();
        player.setInvulnerable(true);
        INVULN_UNTIL.put(player.getUUID(), now + REVIVE_INVULN_TICKS);
        player.sendSystemMessage(Component.translatable("divinebeast.msg.m5_revive"));
    }
}
