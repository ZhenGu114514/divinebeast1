package com.divinebeast.divinebeast.curio;

import com.divinebeast.divinebeast.DivineBeastConfig;
import com.divinebeast.divinebeast.item.DivineBeastItem;
import com.divinebeast.divinebeast.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 车万女仆权能引擎（仅在装了女仆模组 + Curios 时由 {@code DivineBeastMod} 注册）。
 *
 * <p><b>核心规则（用户需求）</b>
 * <ol>
 *   <li><b>女仆不经历诅咒阶段</b>：『祂』『兽』放进女仆饰品栏后<b>直接按救赎 / 拯救形态生效</b>，
 *       既不需要集齐 5 件时刻 / 6 件法则，也不会吃到任何诅咒负面
 *       （诅咒逻辑全部写在 Player 专属的 CuriosEffects / BeastEffects 里，女仆天然碰不到）。</li>
 *   <li><b>女仆获得完整权能</b>：见下表的三个档位。</li>
 *   <li><b>女仆的任何权能都不会伤到玩家</b>：玩家保护是硬约束 ——
 *       光之领域、神威·诛灭的范围段、以及女仆本人（含其射出的弹射物）造成的一切伤害，
 *       只要打的是 {@link Player} 一律作废。（{@link #onLivingAttack} / {@link #onLivingDamage} 双重拦截。）</li>
 *   <li><b>外观</b>：女仆饰品栏里的『祂』『兽』会被改写成救赎形态的显示名与 tooltip
 *       （NBT 标记 {@link DivineBeastItem#TAG_MAID_REDEEMED}，离槽 / 掉落时自动还原）。</li>
 * </ol>
 *
 * <p><b>档位</b>（女仆饰品栏由数据包直接给出 {@code divine / beast / he_true_maid} 三个槽）
 * <ul>
 *   <li><b>救赎基础档</b>（戴『祂』『兽』『真者祂』任意一件）：
 *       净世（免疫并每秒清除负面）、免击退、免摔落/火焰/岩浆/溺水/冻结/虚空/必杀、
 *       攻击力 +5000、攻速 +1000、移速 ×2、击退抗性 +100、生命上限 +2000、
 *       每秒回满生命与吸收 40、常驻增益（夜视/抗性IV/跳跃V/幸运V/防火/急迫200）、
 *       磁界（吸物品与经验）、不朽之器（装备耐久即时修复）、全知之眼（周围生物发光）、
 *       威慑（敌对生物不再把女仆选为目标）。</li>
 *   <li><b>兽·拯救档</b>（额外，戴『兽』）：智慧法则 90% 减伤 + 生命法则 治疗效果 ×2。</li>
 *   <li><b>真者档</b>（额外，戴『真者祂』）：至圣（免疫一切伤害）、不灭（死亡无效并回满）、
 *       一不可说不可转（攻击力 + 双精度最大值）、血之回响（伤害 × 当前生命值）、
 *       天罚 + 归墟（附加等值真伤与等值虚空伤害，受配置 {@code htrue_true_damage} 控制）、
 *       神威·诛灭（多段 / 范围 / 逻辑致死 / 兜底抹除，受配置 {@code htrue_execution} 控制）、
 *       光之领域（每秒范围伤害）、斥力场（弹开弹射物）、造化（击杀掉落 ×10）。</li>
 * </ul>
 *
 * <p><b>刻意不给女仆的玩家专属权能</b>：创造式飞行（走 {@code Player#getAbilities}，非玩家实体没有），
 * 按键开关（X/V/B 只对玩家有状态），万藏 +99 通用饰品栏（女仆饰品界面开着时改槽数会崩），
 * 经验 ×10 / 挖掘掉落（女仆不用经验等级、不挖方块），食物饱足、蹈水履火（纯客户端视野）。
 *
 * <p>本类只在 Curios 已安装时被引用（内含 Curios API 引用），未装女仆模组时 {@link #register()} 直接空转。
 */
public final class MaidEffects {

    private static final Logger LOGGER = LogManager.getLogger();

    /** 车万女仆的 modid 与女仆实体注册名 */
    private static final String MAID_MOD_ID = "touhou_little_maid";
    private static final String MAID_ENTITY_PATH = "maid";

    // ---- 属性修饰符（UUID 与玩家侧全部错开）----
    private static final UUID DMG_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000c1");
    private static final UUID MIGHT_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000c2");
    private static final UUID ATK_SPEED_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000c3");
    private static final UUID SPEED_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000c4");
    private static final UUID KB_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000c5");
    private static final UUID MAX_HP_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000c6");

    private static final double DMG_ADD = 5000.0D;
    /** 22 神威 →「一不可说不可转」：属性是 double，取最大值（转 float 时会被夹回有限值） */
    private static final double MIGHT_ADD = Double.MAX_VALUE;
    private static final double ATK_SPEED_ADD = 1000.0D;
    /** MULTIPLY_TOTAL：1.0 = +100% = ×2 */
    private static final double SPEED_MULTIPLY_TOTAL = 1.0D;
    private static final double KB_ADD = 100.0D;
    private static final double MAX_HP_ADD = 2000.0D;
    private static final float ABSORPTION = 40.0F;

    private static final int EFFECT_DURATION = 1200;
    private static final int EFFECT_REFRESH_TICKS = 20;
    private static final double BEACON_RADIUS = 16.0D;
    /** 女仆没有经验等级，光之领域固定 50 点（玩家侧是 50 + 等级） */
    private static final float BEACON_DAMAGE = 50.0F;
    private static final double MAGNET_RADIUS = 16.0D;
    private static final double REPEL_RADIUS = 6.0D;
    private static final double REPEL_SPEED = 0.6D;
    private static final int EXECUTION_EXTRA_HITS = 8;
    private static final double EXECUTION_RADIUS = 16.0D;
    private static final int DROP_MULTIPLIER = 10;

    /** 防递归：真伤 / 诛灭内部再触发伤害事件时直接返回。 */
    private static boolean applying;

    /** 已被改成救赎形态外观的栈（女仆 UUID → 槽内真实栈），离槽 / 掉落时还原。 */
    private static final Map<UUID, ItemStack> REDEEMED_DEITY = new HashMap<>();
    private static final Map<UUID, ItemStack> REDEEMED_BEAST = new HashMap<>();

    /** 女仆实体类型懒解析（未装女仆模组时为 null） */
    private static boolean maidTypeResolved;
    private static EntityType<?> maidType;

    private MaidEffects() {
    }

    /** 仅在 Curios 已安装时调用；未装女仆模组时直接不注册（零开销）。 */
    public static void register() {
        if (!ModList.get().isLoaded(MAID_MOD_ID)) {
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(MaidEffects::onLivingTick);
        // 防护类监听抢最后一位：别的模组无法在我们取消之后再"救回来"
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, MaidEffects::onLivingAttack);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, MaidEffects::onLivingDamage);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, MaidEffects::onDeath);
        MinecraftForge.EVENT_BUS.addListener(MaidEffects::onLivingHeal);
        MinecraftForge.EVENT_BUS.addListener(MaidEffects::onFall);
        MinecraftForge.EVENT_BUS.addListener(MaidEffects::onKnockBack);
        MinecraftForge.EVENT_BUS.addListener(MaidEffects::onTargetChange);
        MinecraftForge.EVENT_BUS.addListener(MaidEffects::onEffectApplicable);
        MinecraftForge.EVENT_BUS.addListener(MaidEffects::onLivingDrops);
        LOGGER.info("[divinebeast] 女仆权能引擎已注册（救赎基础档 / 兽·拯救档 / 真者档；对玩家零伤害）。");
    }

    // ==================================================================
    // 识别与查询
    // ==================================================================

    private static EntityType<?> maidType() {
        if (!maidTypeResolved) {
            maidTypeResolved = true;
            if (ModList.get().isLoaded(MAID_MOD_ID)) {
                maidType = ForgeRegistries.ENTITY_TYPES.getValue(
                        new ResourceLocation(MAID_MOD_ID, MAID_ENTITY_PATH));
            }
        }
        return maidType;
    }

    /** 是否是车万女仆的女仆实体（供 CuriosEffects / BeastEffects 判定"女仆不吃诅咒"）。 */
    static boolean isMaid(Entity entity) {
        EntityType<?> type = maidType();
        return entity != null && type != null && entity.getType() == type;
    }

    private static boolean worn(LivingEntity entity, Item item) {
        return !liveWorn(entity, item).isEmpty();
    }

    private static boolean hasDeity(LivingEntity entity) {
        return worn(entity, ModItems.DEITY.get());
    }

    private static boolean hasBeast(LivingEntity entity) {
        return worn(entity, ModItems.BEAST.get());
    }

    private static boolean hasTrue(LivingEntity entity) {
        return worn(entity, ModItems.HE_TRUE.get());
    }

    /** 戴了任意一件核心饰品（= 救赎基础档） */
    private static boolean powered(LivingEntity entity) {
        return hasDeity(entity) || hasBeast(entity) || hasTrue(entity);
    }

    /**
     * 返回饰品栏中该物品的<b>真实栈</b>（不是副本 —— 改名 / 写 NBT 会直接落到槽里）；
     * 没有佩戴则返回 {@link ItemStack#EMPTY}。
     */
    private static ItemStack liveWorn(LivingEntity entity, Item item) {
        if (entity == null) {
            return ItemStack.EMPTY;
        }
        try {
            Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(entity).resolve();
            if (optional.isPresent()) {
                for (SlotResult result : optional.get().findCurios(item)) {
                    ItemStack stack = result.stack();
                    if (stack != null && !stack.isEmpty()) {
                        return stack;
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[divinebeast] 查询女仆饰品失败: {}", t.toString());
        }
        return ItemStack.EMPTY;
    }

    // ==================================================================
    // 每 tick：救赎基础档 + 真者档
    // ==================================================================

    private static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity maid = event.getEntity();
        if (maid.level().isClientSide || !isMaid(maid)) {
            return;
        }
        boolean deity = hasDeity(maid);
        boolean beast = hasBeast(maid);
        boolean heTrue = hasTrue(maid);
        if (!deity && !beast && !heTrue) {
            releaseRedeemed(maid);
            return;
        }
        // 『祂』『兽』按救赎形态显示
        syncRedeemedLook(maid, ModItems.DEITY.get(), REDEEMED_DEITY);
        syncRedeemedLook(maid, ModItems.BEAST.get(), REDEEMED_BEAST);

        // ---- 5 净世 ----
        clearNegativeEffects(maid);
        // ---- 17 归墟：灭火 + 补空气 ----
        maid.clearFire();
        if (maid.getAirSupply() < maid.getMaxAirSupply()) {
            maid.setAirSupply(maid.getMaxAirSupply());
        }
        // ---- 2 不朽 / 13 命泉：生命与吸收回满 ----
        restoreVitals(maid);
        // ---- 8 神能 / 12 神行 / 6 磐石 / 23 无量之躯 ----
        ensureAttribute(maid, Attributes.ATTACK_DAMAGE, DMG_MOD, "divinebeast_maid_dmg", DMG_ADD);
        if (heTrue) {
            ensureAttribute(maid, Attributes.ATTACK_DAMAGE, MIGHT_MOD, "divinebeast_maid_might", MIGHT_ADD);
        }
        ensureAttribute(maid, Attributes.ATTACK_SPEED, ATK_SPEED_MOD, "divinebeast_maid_atk_speed", ATK_SPEED_ADD);
        ensureAttributeMultiplier(maid, Attributes.MOVEMENT_SPEED, SPEED_MOD,
                "divinebeast_maid_speed", SPEED_MULTIPLY_TOTAL);
        ensureAttribute(maid, Attributes.KNOCKBACK_RESISTANCE, KB_MOD, "divinebeast_maid_kb", KB_ADD);
        ensureAttribute(maid, Attributes.MAX_HEALTH, MAX_HP_MOD, "divinebeast_maid_maxhp", MAX_HP_ADD);

        // ---- 每秒：常驻增益 / 全知之眼 ----
        if (maid.tickCount % EFFECT_REFRESH_TICKS == 0) {
            refreshBuff(maid, MobEffects.NIGHT_VISION, 0);
            refreshBuff(maid, MobEffects.DAMAGE_RESISTANCE, 4);
            refreshBuff(maid, MobEffects.JUMP, 5);
            refreshBuff(maid, MobEffects.LUCK, 5);
            refreshBuff(maid, MobEffects.FIRE_RESISTANCE, 0);
            refreshBuff(maid, MobEffects.DIG_SPEED, 200);
            applyGlowSense(maid);
        }
        // ---- 10 光之领域（真者档；每秒）----
        if (heTrue && maid.tickCount % 20 == 0) {
            applyLightDomain(maid);
        }
        // ---- 19 不朽之器（每秒）----
        if (maid.tickCount % 20 == 0) {
            repairEquipment(maid);
        }
        // ---- 15 磁界（每 10 tick）----
        if (maid.tickCount % 10 == 0) {
            applyMagnet(maid);
        }
        // ---- 斥力场（每 2 tick）----
        if (maid.tickCount % 2 == 0) {
            repelProjectiles(maid);
        }
    }

    /**
     * 2 不朽 / 13 命泉：生命与吸收回满（已满不重复写，减少网络同步）。
     */
    private static void restoreVitals(LivingEntity entity) {
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
        if (entity.getAbsorptionAmount() < ABSORPTION) {
            entity.setAbsorptionAmount(ABSORPTION);
        }
    }

    /** 5 净世：清除负面效果，保留正面增益（否则自家的夜视/抗性会被"清了又补"地抖动）。 */
    private static void clearNegativeEffects(LivingEntity entity) {
        for (MobEffectInstance effect : new java.util.ArrayList<>(entity.getActiveEffects())) {
            if (!effect.getEffect().isBeneficial()) {
                entity.removeEffect(effect.getEffect());
            }
        }
    }

    private static void ensureAttribute(LivingEntity entity, Attribute attribute,
                                        UUID uuid, String name, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null || instance.getModifier(uuid) != null) {
            return;
        }
        instance.addPermanentModifier(new AttributeModifier(uuid, name, amount,
                AttributeModifier.Operation.ADDITION));
    }

    private static void ensureAttributeMultiplier(LivingEntity entity, Attribute attribute,
                                                  UUID uuid, String name, double multiplyTotal) {
        AttributeInstance instance = entity.getAttribute(attribute);
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

    /** 补发正面药水，剩余时长不满就拉满（每秒调用 → 恒定贴着 1200，不会闪烁）。 */
    private static void refreshBuff(LivingEntity entity, MobEffect effect, int amplifier) {
        MobEffectInstance current = entity.getEffect(effect);
        if (current == null || current.getDuration() < EFFECT_DURATION) {
            entity.addEffect(new MobEffectInstance(effect, EFFECT_DURATION, amplifier, false, false));
        }
    }

    /** 7 全知之眼：周围 48 格内的其它生物发光标记（不标记玩家，免得一直描边主人）。 */
    private static void applyGlowSense(LivingEntity maid) {
        for (LivingEntity other : maid.level().getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(maid.position(), 48.0D, 48.0D, 48.0D))) {
            if (!other.isAlive() || other.is(maid) || other instanceof Player) {
                continue;
            }
            MobEffectInstance glow = other.getEffect(MobEffects.GLOWING);
            if (glow == null || glow.getDuration() < EFFECT_DURATION / 2) {
                other.addEffect(new MobEffectInstance(MobEffects.GLOWING, EFFECT_DURATION, 0, false, false));
            }
        }
    }

    /**
     * 10 光之领域：半径内敌对生物每秒受 50 点伤害。
     * <b>玩家一律跳过</b>（本模组女仆权能的硬约束），BOSS 与试验假人也跳过
     * （BOSS 交给手动攻击结算；假人不该被范围伤害打掉）。
     */
    private static void applyLightDomain(LivingEntity maid) {
        for (LivingEntity target : maid.level().getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(maid.position(), BEACON_RADIUS * 2, BEACON_RADIUS * 2, BEACON_RADIUS * 2))) {
            if (!target.isAlive() || target.is(maid) || !(target instanceof Enemy)) {
                continue;
            }
            if (target instanceof Player || isMaid(target) || isBossMob(target) || isProtectedDummy(target)) {
                continue;
            }
            target.hurt(target.damageSources().mobAttack(maid), BEACON_DAMAGE);
        }
    }

    /** 15 磁界：把半径内的掉落物与经验球吸到女仆脚下。 */
    private static void applyMagnet(LivingEntity maid) {
        AABB box = AABB.ofSize(maid.position(), MAGNET_RADIUS, MAGNET_RADIUS, MAGNET_RADIUS);
        for (ItemEntity item : maid.level().getEntitiesOfClass(ItemEntity.class, box)) {
            item.setPickUpDelay(0);
            item.setPos(maid.getX(), maid.getY() + 0.5D, maid.getZ());
        }
        for (ExperienceOrb orb : maid.level().getEntitiesOfClass(ExperienceOrb.class, box)) {
            orb.setPos(maid.getX(), maid.getY() + 0.5D, maid.getZ());
        }
    }

    /** 19 不朽之器：装备与手持物耐久即时修复。 */
    private static void repairEquipment(LivingEntity maid) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = maid.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() > 0) {
                stack.setDamageValue(0);
            }
        }
    }

    /** 斥力场：把半径内除女仆自己射出的之外的一切弹射物向外弹开。 */
    private static void repelProjectiles(LivingEntity maid) {
        for (Projectile proj : maid.level().getEntitiesOfClass(Projectile.class,
                AABB.ofSize(maid.position(), REPEL_RADIUS, REPEL_RADIUS, REPEL_RADIUS))) {
            if (!proj.isAlive() || proj.getOwner() == maid) {
                continue;
            }
            net.minecraft.world.phys.Vec3 away = proj.position().subtract(maid.position());
            if (away.lengthSqr() < 1.0E-4D) {
                away = new net.minecraft.world.phys.Vec3(0.0D, 1.0D, 0.0D);
            } else {
                away = away.normalize();
            }
            proj.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            proj.push(away.x * REPEL_SPEED, away.y * REPEL_SPEED, away.z * REPEL_SPEED);
        }
    }

    // ==================================================================
    // 救赎形态外观（女仆饰品栏里的『祂』『兽』）
    // ==================================================================

    private static void syncRedeemedLook(LivingEntity maid, Item item, Map<UUID, ItemStack> tracked) {
        ItemStack current = liveWorn(maid, item);
        ItemStack old = tracked.get(maid.getUUID());
        if (old != null && old != current) {
            clearRedeemedLook(old);
            tracked.remove(maid.getUUID());
        }
        if (current.isEmpty()) {
            return;
        }
        tracked.put(maid.getUUID(), current);
        if (!DivineBeastItem.isMaidRedeemed(current)) {
            current.getOrCreateTag().putBoolean(DivineBeastItem.TAG_MAID_REDEEMED, true);
        }
        String name = item == ModItems.DEITY.get() ? "『祂』" : "『兽』";
        if (!name.equals(current.getHoverName().getString())) {
            current.setHoverName(Component.literal(name).withStyle(ChatFormatting.GOLD));
        }
    }

    /** 女仆不再佩戴时，把记录过的栈还原成普通形态。 */
    private static void releaseRedeemed(LivingEntity maid) {
        ItemStack deity = REDEEMED_DEITY.remove(maid.getUUID());
        if (deity != null) {
            clearRedeemedLook(deity);
        }
        ItemStack beast = REDEEMED_BEAST.remove(maid.getUUID());
        if (beast != null) {
            clearRedeemedLook(beast);
        }
    }

    /** 去掉"女仆救赎形态"标记与自定义名字（对副本、掉落物同样适用）。 */
    static void clearRedeemedLook(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !DivineBeastItem.isMaidRedeemed(stack)) {
            return;
        }
        stack.getTag().remove(DivineBeastItem.TAG_MAID_REDEEMED);
        stack.resetHoverName();
    }

    // ==================================================================
    // 事件
    // ==================================================================

    /**
     * ① 女仆（含其弹射物）的攻击对玩家一律无效；
     * ② 女仆自身：真者档至圣（全免），基础档免环境 / 虚空 / 必杀伤害。
     */
    private static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        if (victim instanceof Player && attackerIsPoweredMaid(event.getSource())) {
            event.setCanceled(true);
            return;
        }
        if (!isMaid(victim) || !powered(victim)) {
            return;
        }
        if (hasTrue(victim)) {
            event.setCanceled(true);
            return;
        }
        if (isEnvironmentDamage(event.getSource()) || isInVoid(victim)) {
            event.setCanceled(true);
        }
    }

    private static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || applying) {
            return;
        }
        // ---- 玩家保护 ----
        if (victim instanceof Player) {
            if (attackerIsPoweredMaid(event.getSource())) {
                event.setAmount(0.0F);
                event.setCanceled(true);
            }
            return;
        }
        // ---- 女仆受击 ----
        if (isMaid(victim) && powered(victim)) {
            if (hasTrue(victim)) {
                event.setCanceled(true); // 21 至圣
            } else if (hasBeast(victim)) {
                event.setAmount(event.getAmount() * 0.1F); // 智慧法则：减免 90%
            }
            return;
        }
        // ---- 女仆攻击（仅真者档有进攻权能）----
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || !isMaid(attacker)) {
            return;
        }
        if (!worn(attacker, ModItems.HE_TRUE.get()) || victim.is(attacker) || isProtectedDummy(victim)) {
            return;
        }
        float amount = event.getAmount();
        if (event.isCanceled() || amount <= 0.0F) {
            return;
        }
        // 11 血之回响：伤害 × 当前生命值
        amount = amount * Math.max(1.0F, attacker.getHealth());
        if (!Float.isFinite(amount)) {
            amount = Float.MAX_VALUE;
        }
        event.setAmount(amount);

        boolean trueDamage = DivineBeastConfig.HTRUE_TRUE_DAMAGE.get();
        boolean execute = DivineBeastConfig.HTRUE_EXECUTION.get();
        if (!trueDamage && !execute) {
            return;
        }
        if (trueDamage) {
            // BOSS 的 hurt 覆写不吃"无实体来源"的伤害类型 → 折进普通伤害（总倍率同为 3 倍）
            if (isBossMob(victim)) {
                event.setAmount(amount * 3.0F);
            } else {
                applying = true;
                try {
                    victim.hurt(victim.damageSources().genericKill(), amount);
                    victim.hurt(victim.damageSources().fellOutOfWorld(), amount);
                } finally {
                    applying = false;
                }
            }
        }
        if (execute) {
            applyExecutionChain(attacker, victim, amount);
        }
    }

    /** 2 不朽：真者档死亡无效（回满并只清负面）。 */
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !isMaid(entity)) {
            return;
        }
        if (!worn(entity, ModItems.HE_TRUE.get())) {
            return;
        }
        event.setCanceled(true);
        restoreVitals(entity);
        clearNegativeEffects(entity);
    }

    /** 兽·生命法则：治疗效果 ×2。 */
    private static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !isMaid(entity) || !hasBeast(entity)) {
            return;
        }
        event.setAmount(event.getAmount() * 2.0F);
    }

    /** 3 永恒之翼（弱化版）：免疫摔落伤害。 */
    private static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !isMaid(entity) || !powered(entity)) {
            return;
        }
        event.setCanceled(true);
    }

    /** 6 磐石：免疫击退。 */
    private static void onKnockBack(LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !isMaid(entity) || !powered(entity)) {
            return;
        }
        event.setCanceled(true);
    }

    /** 16 威慑：敌对生物不再把女仆选为目标。 */
    private static void onTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        // getNewTarget() 本身就已经是 LivingEntity，不能再写 instanceof 模式（会报"表达式类型是模式类型的子类型"）
        LivingEntity target = event.getNewTarget();
        if (isMaid(target) && powered(target)) {
            event.setCanceled(true);
        }
    }

    /** 5 净世：负面效果禁止上身（施加前拦截）。 */
    private static void onEffectApplicable(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !isMaid(entity) || !powered(entity)) {
            return;
        }
        if (!event.getEffectInstance().getEffect().isBeneficial()) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * 造化（击杀掉落 ×10，仅真者档）；同时把女仆掉落的『祂』『兽』还原成普通形态，
     * 免得饰品被玩家捡回去后还挂着"救赎形态"的名字。
     */
    private static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            if (drop == null || drop.getItem().isEmpty()) {
                continue;
            }
            clearRedeemedLook(drop.getItem());
        }
        Entity source = event.getSource() == null ? null : event.getSource().getEntity();
        if (!(source instanceof LivingEntity killer) || !isMaid(killer)
                || !worn(killer, ModItems.HE_TRUE.get())) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            if (drop == null || drop.getItem().isEmpty()) {
                continue;
            }
            ItemStack stack = drop.getItem();
            int max = stack.getMaxStackSize();
            stack.setCount(Math.min(max, stack.getCount() * DROP_MULTIPLIER));
        }
    }

    // ==================================================================
    // 神威·诛灭（女仆版）
    // ==================================================================

    /**
     * 与玩家侧同一套五重手段（多段 / 范围 / 逻辑致死 / 兜底抹除），差别只有两点：
     * 伤害来源用 {@code mobAttack(女仆)}，以及<b>范围段与主目标都跳过玩家</b>。
     */
    private static void applyExecutionChain(LivingEntity attacker, LivingEntity victim, float amount) {
        applying = true;
        try {
            DamageSource maidAttack = victim.damageSources().mobAttack(attacker);
            for (int i = 0; i < EXECUTION_EXTRA_HITS && victim.isAlive(); i++) {
                victim.hurt(maidAttack, amount);
                victim.setHealth(Math.max(0.0F, victim.getHealth() - amount));
            }
            for (LivingEntity other : attacker.level().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(attacker.position(),
                            EXECUTION_RADIUS * 2, EXECUTION_RADIUS * 2, EXECUTION_RADIUS * 2))) {
                if (other == victim || other.is(attacker) || !other.isAlive()) {
                    continue;
                }
                if (other instanceof Player || isMaid(other) || !(other instanceof Enemy)
                        || isProtectedDummy(other) || isBossMob(other)) {
                    continue;
                }
                other.hurt(other.damageSources().genericKill(), amount);
                other.setHealth(Math.max(0.0F, other.getHealth() - amount));
                killWithDrops(other);
            }
            killWithDrops(victim);
        } catch (Throwable t) {
            LOGGER.warn("[divinebeast] 女仆 神威·诛灭 执行链异常", t);
        } finally {
            applying = false;
        }
    }

    /** 逻辑致死三步：等血真伤 → 血量置零 → 兜底 {@code kill()}（不用 discard，保留掉落物）。 */
    private static void killWithDrops(LivingEntity target) {
        if (target instanceof Player || target.isRemoved()) {
            return;
        }
        target.hurt(target.damageSources().genericKill(), Math.max(1.0F, target.getHealth()));
        if (!target.isRemoved() && target.getHealth() > 0.0F) {
            target.setHealth(0.0F);
        }
        if (!target.isRemoved() && target.getHealth() > 0.0F) {
            target.kill();
        }
    }

    // ==================================================================
    // 判定工具
    // ==================================================================

    /** 伤害来源（含弹射物的发射者）是否是"有权能的女仆"。 */
    private static boolean attackerIsPoweredMaid(DamageSource source) {
        if (source == null) {
            return false;
        }
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof LivingEntity owner) {
            return isMaid(owner) && powered(owner);
        }
        return source.getEntity() instanceof LivingEntity living && isMaid(living) && powered(living);
    }

    /** 是否是"虚空伤害"（跌出世界）。 */
    private static boolean isVoidDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        String id = source.getMsgId();
        return "outOfWorld".equals(id) || "fellOutOfWorld".equals(id);
    }

    /** 18 界缚：是否是 /kill 这类"必杀"伤害。 */
    private static boolean isKillDamage(DamageSource source) {
        return source != null && "genericKill".equals(source.getMsgId());
    }

    /** 基础档免疫的环境类伤害：火焰 / 岩浆 / 溺水 / 冻结 / 摔落 / 虚空 / 必杀。
     *  <p>冻结用 message id 判定（{@code freeze}），不依赖 {@code DamageTypeTags} 里
     *  那个常量在不同映射下的拼写，少一处编译期风险。 */
    private static boolean isEnvironmentDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        return source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypeTags.IS_FALL)
                || "freeze".equals(source.getMsgId())
                || isVoidDamage(source)
                || isKillDamage(source);
    }

    private static boolean isInVoid(LivingEntity entity) {
        return entity.getY() < (double) entity.level().getMinBuildHeight();
    }

    private static boolean isBossMob(LivingEntity entity) {
        // 本模组的中立 boss 也算 BOSS：女仆的「光之领域」等范围权能不去自动清掉它们。
        return entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || entity instanceof com.divinebeast.divinebeast.boss.DivineBoss;
    }

    /** 试验假人（{@code mmm:dummy}）：不施加任何附加伤害。 */
    private static boolean isProtectedDummy(Entity entity) {
        if (entity == null) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key != null && "mmm".equals(key.getNamespace()) && "dummy".equals(key.getPath());
    }
}
