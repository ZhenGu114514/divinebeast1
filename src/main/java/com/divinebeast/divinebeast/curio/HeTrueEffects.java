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
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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
 *   <li>76 旧伤 → <b>22 神威：攻击力 + 一不可说不可转</b>（直接取 double 最大值，见 MIGHT_DAMAGE）；</li>
 *   <li>圣火灼烧＋迟滞领域 → <b>斥力场：弹开一切非自身的远程弹射物</b>；</li>
 *   <li>1 太初 / 17 归墟 → <b>免疫虚空伤害</b>（不再"从虚空中拉回"）；
 *       且 17 归墟 追加一段<b>与本次伤害等值的虚空伤害</b>；</li>
 *   <li>8 神能 攻击速度 → <b>+1000</b>；</li>
 *   <li>6 磐石 击退抗性 → <b>+100</b>；</li>
 *   <li>蹈水履火 → <b>在水与岩浆中视野不受遮挡、移速不降低</b>
 *       （纯客户端实现，见 {@code client.HeTrueLiquidClient}；原"吸收岩浆"已移除）；</li>
 *   <li>万法附魔 → <b>只对「木棍」生效</b>，并把名称改为「棍木」；</li>
 *   <li>18 界缚 → <b>只拦"别的玩家用 /tp 传送我"</b>；自己发的 /tp（传自己、传别人、传坐标）、
 *       以及<b>无玩家发起者的传送（控制台 / 命令方块 / 其它模组的强制传送）全部放行</b>；</li>
 *   <li>1 太初 / 18 界缚 → 追加 <b>免疫 /kill（genericKill）</b>；</li>
 *   <li>额外保护：<b>不对 MmmMmmMmmMmm 的试验假人（mmm:dummy）施加附加伤害</b>；</li>
 *   <li>10 光之领域 → 改为 <b>V 键开关</b>（默认开）；神行仍为 X 键（×2 ≈ 速度 V）。</li>
 *   <li>所有药水类效果 → 统一 <b>时长 1 分钟（1200 tick）、每 1 秒补发一次</b>，
 *       修复"夜视一闪一闪"（剩余时长落入原版夜视闪烁区间）。</li>
 * </ul>
 *
 * <p>原 #88「造化」复制权能已按需求移除，改为：真者祂击杀任意生物时掉落物品×10、
 * 破坏方块时掉落经验×10（方块物品掉落 1.20.1 Forge 的 BreakEvent 无挂点，故无法×10）。
 *
 * <p>实现以"每服务端 tick 维持 + 事件豁免"为主，数值取最强口径；
 * 全部效果只在 {@link AscensionEffects#wearingHeTrue} 时生效。
 */
public final class HeTrueEffects {

    /** 本类日志（神威·诛灭 执行链等处的异常记录用）。 */
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    // 属性修饰符固定 UUID
    private static final UUID DAMAGE_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b1");
    private static final UUID SPEED_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b2");
    private static final UUID ATTACK_SPEED_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b3");
    private static final UUID KNOCKBACK_RES_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b4");
    private static final UUID MAX_HEALTH_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b5");
    private static final UUID FLYING_SPEED_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b6");
    /**
     * 万藏：通用(curio)槽固定修饰符 UUID —— <b>本类已不再使用</b>（槽位现由
     * {@code CuriosEffects.grantHoardSlots} 在解锁「真者祂饰品栏」时一次性、常驻授予，用的是同一个 UUID，
     * 因此老存档不会叠加）。保留此常量仅作记载，便于对照老存档里的修饰符来源。
     */
    private static final UUID CURIO_HOARD_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b7");
    /** 神威（由 76 旧伤 改写）：攻击力「一不可说不可转」的固定修饰符 UUID */
    private static final UUID MIGHT_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b8");
    /**
     * 22 神威（由 76 旧伤 改写）：攻击力加成 —— <b>一不可说不可转</b>。
     *
     * <p>按需求<b>直接取最大值</b>：「不可说不可说转」是佛教大数序列的终点、远超 IEEE-754
     * double 上限，无法如实表达，所以用 double 能表示的最大有限值
     * （{@link Double#MAX_VALUE} ≈ 1.7976931348623157e308）作为它的工程实现 ——
     * 换算成攻击力就是"一不可说不可转"。
     *
     * <p>溢出保护：伤害在管线里是 <b>float</b>（上限 ≈3.4e38），属性值转成 float 时会饱和成
     * {@code Infinity}，再乘上「血之回响」的当前生命值必然还是 Infinity。
     * Infinity 一旦参与后续运算（击退、伤害显示、其它模组的减免计算）极易产生 NaN，
     * 因此在 {@code onLivingDamage} 里把最终数值夹到 {@link Float#MAX_VALUE}（有限值）——
     * 数值依然是天文级（3.4e38），但不会再污染整条管线。
     */
    private static final double MIGHT_DAMAGE = Double.MAX_VALUE;
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
    /** 最近一次"由玩家执行的指令"的发起者 UUID 与其发生的时间戳（用于判定传送发起人） */
    private static java.util.UUID lastCommandIssuer = null;
    private static long lastCommandIssuerMillis = 0L;
    /** 判定"本次传送是否由刚执行的指令触发"的时间窗口（毫秒）。指令是同步执行的，故取很小值。 */
    private static final long COMMAND_ISSUER_WINDOW_MS = 100L;

    /** 天罚追加真伤防递归标记 */
    private static boolean applyingTrueDamage = false;
    /** 光柱范围（格）——真者祂光之领域 */
    private static final double BEACON_RADIUS = 16.0D;
    /** 磁界拉取范围（格，AABB 边长） */
    private static final double MAGNET_RADIUS = 16.0D;
    /** 「神威·诛灭」多段手段：主目标额外补的段数（每段都会清零无敌帧） */
    private static final int EXECUTION_EXTRA_HITS = 8;
    /** 「神威·诛灭」范围手段：以玩家为中心的作用半径（格，AABB 边长） */
    private static final double EXECUTION_RADIUS = 16.0D;
    /**
     * 所有"给药水"效果的统一时长：<b>1 分钟</b>（1200 tick）。
     *
     * <p>原先夜视只有 400 tick 并且 100 tick 才补发一次，取整后在"补发 → 自然耗光"
     * 之间来回，剩余时长会长时间停在原版
     * {@code GameRenderer#getNightVisionScale} 的<b>闪烁区间</b>（剩余 &lt; 200 tick 时
     * 亮度按 {@code 0.7 + sin(duration × 0.2π) × 0.3} 振荡），视觉上就是"一闪一闪"。
     * 给足一分钟时长 + 每秒拉满，可彻底避开该区间。
     */
    private static final int EFFECT_DURATION = 1200;
    /** 效果补发周期：每 1 秒（20 tick）重新施加一次，使剩余时长恒定贴着满值 1200 */
    private static final int EFFECT_REFRESH_TICKS = 20;
    /** 当前处于真者祂形态的玩家（用于离开形态时精确回收本引擎效果） */
    private static final java.util.Set<java.util.UUID> ACTIVE = new java.util.HashSet<>();

    private HeTrueEffects() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onPlayerTick);
        // 防护类监听一律注册在 LOWEST：最后执行 → 别的模组无法在我们取消之后再把伤害"救回来"
        // （Forge 事件允许后续监听器 setCanceled(false)，所以"拦截"必须抢最后一个位置）。
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                HeTrueEffects::onLivingAttack);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                HeTrueEffects::onLivingDamage);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                HeTrueEffects::onDeath);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onEffectApplicable);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onKnockBack);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onFall);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onTargetChange);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onXpChange);
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onTeleportCommand);
        // 记录指令发起者（用于区分"自己传送"与"别人传送我"）
        MinecraftForge.EVENT_BUS.addListener(HeTrueEffects::onCommand);
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
        // V 键开关：10 光之领域 范围伤害（默认开）
        boolean beaconOn = CuriosEffectsState.htrueBeaconToggle(player);
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
        // ---- 7 全知之眼：夜视（时长 1 分钟，每 1 秒补发一次，恒不满期 → 不闪烁）----
        if (player.tickCount % EFFECT_REFRESH_TICKS == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                    EFFECT_DURATION, 0, false, false));
        }
        // ---- 万法之域：常驻增益族（时长 1 分钟，每 1 秒补发拉满）----
        // 去重说明：力量/移速/再生/水下呼吸 原先同时用"药水"和"属性/直接写入"两条渠道，
        // 数值上完全被后者覆盖（ATTACK_DAMAGE +5000、MOVEMENT_SPEED +2.4、
        // 每 tick setHealth(max)、每 tick setAirSupply(max)），故不再重复给药水。
        // 防火保留：FIRE_RESISTANCE 额外降低岩浆伤害，是 clearFire() 覆盖不到的。
        if (player.tickCount % EFFECT_REFRESH_TICKS == 0) {
            refreshBuff(player, MobEffects.DAMAGE_RESISTANCE, 4, EFFECT_DURATION);
            refreshBuff(player, MobEffects.JUMP, 5, EFFECT_DURATION);
            refreshBuff(player, MobEffects.LUCK, 5, EFFECT_DURATION);
            refreshBuff(player, MobEffects.FIRE_RESISTANCE, 0, EFFECT_DURATION);
            // 89 摧岳：极高挖掘速度（高等级急迫 = 高倍率，非创造式瞬破）
            refreshBuff(player, MobEffects.DIG_SPEED, 200, EFFECT_DURATION);
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
        // 22 神威（由 76 旧伤 改写）：攻击力 + 一不可说不可转
        ensureAttribute(player, Attributes.ATTACK_DAMAGE, MIGHT_MOD, "divinebeast_he_true_might", MIGHT_DAMAGE);
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
        // 蹈水履火（改写）：水/岩浆中视野无遮挡、移速不降低 —— 全客户端实现，见 HeTrueLiquidClient
        // 万藏：动态通用(curio)槽 = 99 + 已装通用饰品件数
        // 万藏：动态通用(curio)槽 +99 —— 已改为在解锁「真者祂饰品栏」时一次性授予（见 CuriosEffects）
        // 「饰品效果翻倍」：把身上其它饰品的属性修饰符各再复制一份
        syncCurioDoubling(player);

        // 以下按各自周期执行。原先这里是一个统一的 `if (tickCount % 20 != 0) return;`，
        // 会把周期更细的磁界（%10）一并吞掉，使其实际只每 20 tick 跑一次。
        // ---- 万法附魔：穿戴装备 + 背包可附魔物品全部得到可附上的正面附魔（无视冲突），永久写入（每秒）----
        if (player.tickCount % 20 == 0) {
            applyDivineEnchantments(player);
        }
        // ---- 10 光之领域（每秒；V 键可开关）----
        if (beaconOn && player.tickCount % 20 == 0) {
            float beaconDmg = 50.0F + player.experienceLevel;
            for (LivingEntity mob : player.level().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(player.position(), BEACON_RADIUS * 2, BEACON_RADIUS * 2, BEACON_RADIUS * 2))) {
                // 敌对单位（Monster 与 末影龙等 Enemy 生物，末影龙不是 Monster）
                // BOSS（末影龙 / 凋灵）除外：本领域每秒 50+等级 的伤害会在几秒内把 BOSS 自动打死，
                // 玩家还没出手 BOSS 就没了（也不需要玩家参与）。BOSS 一律交给手动攻击结算。
                if (mob instanceof net.minecraft.world.entity.monster.Enemy
                        && mob.isAlive() && !mob.is(player) && !isBossMob(mob)) {
                    mob.hurt(mob.damageSources().playerAttack(player), beaconDmg);
                }
            }
        }
        // ---- 7 全知之眼：察觉众生（每 1 秒扫描一次；发光时长 1 分钟）----
        // 说明：发光施加在**其它生物**身上，每 tick 秒都给半径内每一只生物重发一次
        // 药水包会白白刷屏（ServerLevel 要向所有追踪者广播），故这里用"半程补发"：
        // 剩余时长不足一半时才补满，既不会出现"亮一下灭一下"的空档，
        // 又把广播频率降到 1/30。玩家自身的增益仍严格每秒补发。
        if (player.tickCount % EFFECT_REFRESH_TICKS == 0) {
            for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(player.position(), 48, 48, 48))) {
                if (!living.isAlive() || living.is(player)) {
                    continue;
                }
                MobEffectInstance glow = living.getEffect(MobEffects.GLOWING);
                if (glow == null || glow.getDuration() < EFFECT_DURATION / 2) {
                    living.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                            EFFECT_DURATION, 0, false, false));
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

    /**
     * 补发一条正面药水，并把剩余时长直接拉满到 {@code ticks}（当前统一为
     * {@link #EFFECT_DURATION} = 1 分钟）。
     *
     * <p>调用点固定为每 {@link #EFFECT_REFRESH_TICKS} tick（1 秒）一次，
     * 因此只要"剩余时长 &lt; 满值"就补 —— 结果就是每秒重新施加一次，
     * 剩余时长恒定贴着 1200，永远不会进入夜视的闪烁区间。
     *
     * <p>旧实现的门槛是"剩余 &lt; 60 tick 才补"、周期 80 tick、时长 320 tick，
     * 实际会让效果走到<b>自然到期</b>后才重新施加，中间出现空档（血量/亮度抖动）。
     */
    private static void refreshBuff(Player player, net.minecraft.world.effect.MobEffect effect,
                                    int amplifier, int ticks) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < ticks) {
            player.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, false));
        }
    }

    /**
     * 蹈水履火（改写）：<b>在水与岩浆中视野不受遮挡、移速不降低</b>。
     * 该项完全由客户端实现（雾/遮罩/移动都在客户端计算），见
     * {@code com.divinebeast.divinebeast.client.HeTrueLiquidClient}。
     * 原先"吸收岩浆"的实现已按需求移除。
     */

    /**
     * 「真者祂 · 所有装备在身上的饰品效果翻倍」。
     *
     * <p>做法：遍历身上<b>除真者祂以外</b>的全部饰品，把它们提供的属性修饰符<b>再各加一份</b>
     * （下一轮先精确收回上一轮加的那份，再按当前佩戴情况重加，幂等且脱下即还原）。
     * 这样其它模组的饰品、以及本模组饰品上的属性部分都会翻倍；靠代码实现的行为类效果
     * （诅咒 / 法则 / 权能计数等）不在"属性翻倍"范围内。
     *
     * <p>注意：{@code +99 通用饰品栏}（万藏）已经<b>不再</b>由这里动态增删 —— 改成在获得
     * 「真者祂饰品栏」时一次性授予并常驻，避免"界面开着时收回槽位"把客户端容器槽数改崩。
     */
    private static void syncCurioDoubling(Player player) {
        // 【重要】只在"身上其它饰品发生变化"或"上一轮的复制修饰符已丢失（死亡重生、被其它模组清掉）"
        // 时才重建。本方法每 tick 都会跑，若无条件"先清后加"，就会每 tick 重算属性并反复向客户端
        // 同步属性包（真者祂身上饰品一多尤其明显）。
        java.util.Optional<top.theillusivec4.curios.api.type.capability.ICuriosItemHandler> optional =
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isEmpty()) {
            return;   // 暂时取不到 Curios 容器：保留现状，下个 tick 再收敛
        }
        long signature = curioSignature(optional.get());
        Long lastSignature = DOUBLED_SIGNATURE.get(player.getUUID());
        if (lastSignature != null && lastSignature == signature && doublingIntact(player)) {
            return;
        }
        clearCurioDoubling(player);
        java.util.List<DoubledModifier> added = new java.util.ArrayList<>();
        int counter = 0;
        for (java.util.Map.Entry<String, top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler> entry
                : optional.get().getCurios().entrySet()) {
            String slotId = entry.getKey();
            net.minecraftforge.items.IItemHandlerModifiable stacks = entry.getValue().getStacks();
            if (stacks == null) {
                continue;
            }
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.isEmpty() || stack.is(ModItems.HE_TRUE.get())) {
                    continue;
                }
                counter = doubleOneStack(player, stack, slotId, i, counter, added);
            }
        }
        // 即使这一轮什么都没加（身上只有『真者祂』），也要落一条空记录：
        // doublingIntact 以"有没有记录"判断上一轮是否跑过，否则会退化成每 tick 重建。
        DOUBLED.put(player.getUUID(), added);
        DOUBLED_SIGNATURE.put(player.getUUID(), signature);
    }

    /** 身上全部饰品（槽位 + 序号 + 物品）的指纹；与上一轮相同即无需重建翻倍修饰符。 */
    private static long curioSignature(
            top.theillusivec4.curios.api.type.capability.ICuriosItemHandler handler) {
        long hash = 1125899906842597L;
        for (java.util.Map.Entry<String, top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler> entry
                : handler.getCurios().entrySet()) {
            hash = hash * 31L + entry.getKey().hashCode();
            net.minecraftforge.items.IItemHandlerModifiable stacks = entry.getValue().getStacks();
            if (stacks == null) {
                continue;
            }
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                hash = hash * 31L + (stack.isEmpty() ? 0
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(stack.getItem()));
            }
        }
        return hash;
    }

    /** 上一轮加上的复制修饰符是否仍然全部挂在玩家身上（死亡重生会全部丢失）。 */
    private static boolean doublingIntact(Player player) {
        java.util.List<DoubledModifier> old = DOUBLED.get(player.getUUID());
        if (old == null) {
            return false;   // 上一轮没跑过 / 已被 clearCurioDoubling 清空 → 需要重建
        }
        for (DoubledModifier doubled : old) {
            AttributeInstance instance = player.getAttribute(doubled.attribute());
            if (instance == null || instance.getModifier(doubled.uuid()) == null) {
                return false;
            }
        }
        return true;
    }

    /** 把单个饰品提供的属性修饰符复制一份加到玩家身上；返回更新后的计数器。 */
    private static int doubleOneStack(Player player, ItemStack stack, String slotId, int index,
                                      int counter, java.util.List<DoubledModifier> added) {
        // 单一来源：Curios 自己的 getAttributeModifiers —— 它同时涵盖
        // 「物品实现 ICurio#getAttributeModifiers 声明的属性」与「写进物品 NBT 的属性」
        // （CuriosApi.addModifier(...) 那条路），正是"这件饰品实际提供了什么属性"。
        // 刻意不去另外读 ItemStack#getAttributeModifiers(MAINHAND)：那会让同时用两种方式
        // 声明的饰品被算两遍（本来该 ×2 的变成 ×4）。
        com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
                AttributeModifier> source;
        try {
            top.theillusivec4.curios.api.SlotContext context =
                    new top.theillusivec4.curios.api.SlotContext(slotId, player, index, false, true);
            source = top.theillusivec4.curios.api.CuriosApi
                    .getAttributeModifiers(context, java.util.UUID.randomUUID(), stack);
        } catch (Throwable ignored) {
            return counter;   // 取不到这件饰品的属性就跳过它（不影响其它饰品）
        }
        if (source == null) {
            return counter;
        }

        for (java.util.Map.Entry<net.minecraft.world.entity.ai.attributes.Attribute, AttributeModifier> e
                : source.entries()) {
            AttributeModifier modifier = e.getValue();
            if (modifier == null || modifier.getName().startsWith("divinebeast_")) {
                continue;   // 跳过本模组自己的内部修饰符
            }
            AttributeInstance instance = player.getAttribute(e.getKey());
            if (instance == null) {
                continue;   // 例如 Curios 的"槽位"伪属性：玩家身上没有这条属性，跳过
            }
            java.util.UUID id = new java.util.UUID(0x444F55424C450000L, counter++);
            try {
                instance.addTransientModifier(new AttributeModifier(id,
                        "divinebeast_curio_double_" + counter, modifier.getAmount(),
                        modifier.getOperation()));
            } catch (Throwable ignored) {
                continue;   // 极端情况下同 UUID 已在（不该发生）：跳过这一条，绝不让异常冒到 tick 循环
            }
            added.add(new DoubledModifier(e.getKey(), id));
        }
        return counter;
    }

    /** 收回本引擎为"饰品效果翻倍"添加的全部复制修饰符（脱下真者祂 / 退出形态时调用）。 */
    private static void clearCurioDoubling(Player player) {
        java.util.List<DoubledModifier> old = DOUBLED.remove(player.getUUID());
        if (old == null) {
            return;
        }
        for (DoubledModifier doubled : old) {
            AttributeInstance instance = player.getAttribute(doubled.attribute());
            if (instance != null) {
                instance.removeModifier(doubled.uuid());
            }
        }
    }

    /** 「饰品效果翻倍」记下的一条复制修饰符（属性 + UUID，方便精确收回）。 */
    private record DoubledModifier(net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                   java.util.UUID uuid) {
    }

    /** 玩家 → 本轮为"饰品效果翻倍"加上的复制修饰符 */
    private static final java.util.Map<java.util.UUID, java.util.List<DoubledModifier>> DOUBLED =
            new java.util.HashMap<>();
    /** 玩家 → 上一轮做"饰品效果翻倍"时身上饰品的指纹（用来判断是否需要重建） */
    private static final java.util.Map<java.util.UUID, Long> DOUBLED_SIGNATURE = new java.util.HashMap<>();
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
        // 夜视：本引擎每秒补发、时长 1 分钟，退出形态时一并回收（否则会白留最多 60 秒）。
        // 只在"有限时长且剩余 ≤ 本引擎发放的 1200"时删，以免误删玩家自己的夜视药水（3600 tick）
        // 或指令给的无限夜视（duration = -1）。
        MobEffectInstance night = player.getEffect(MobEffects.NIGHT_VISION);
        if (night != null && !night.isInfiniteDuration()
                && night.getDuration() <= EFFECT_DURATION) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        // 飞行：仅当没有其它形态（救赎 phaseTwo）在维持时才收回，
        // 否则刚退出真者祂就会把救赎的飞行一起掐掉（且对方的授予分支只重开 mayfly、不重开 flying）。
        if (player.getAbilities().mayfly && !CuriosEffects.isPhaseTwo(player)) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            if (player instanceof ServerPlayer sp) {
                sp.onUpdateAbilities();
            }
        }
        player.setAbsorptionAmount(0.0F);
        SEC_DAMAGE.remove(player.getUUID()); // 太初每秒伤害额度随形态结束重置
        // 收回"饰品效果翻倍"加上的全部复制修饰符
        clearCurioDoubling(player);
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

    /** 木棍被附魔/改名后的显示名称。 */
    private static final String STICK_DISPLAY = "棍木";

    /**
     * 万法附魔（改写）：<b>只对「木棍」生效</b>，不再改动身上装备与其它背包物品。
     * <ul>
     *   <li>把该木棍能附上的每一种<b>正面</b>附魔写入最高等级，并忽略附魔冲突；</li>
     *   <li>把木棍名称改为 {@value #STICK_DISPLAY}；</li>
     *   <li>每 20 tick 幂等刷新，已满足的木棍不重复写 NBT。</li>
     * </ul>
     *
     * <p>注意：原版木棍不属于任何附魔类别（{@code canEnchant} 几乎全为 false），
     * 若严格按 vanilla 规则则一根附魔都加不上；因此这里对木棍<b>强制</b>写入全部正面附魔
     * （用 {@code desired.put} 直接落 NBT，绕过兼容性检查），使其成为真正的「棍木」。
     * 仍是"永久写入"，脱下真者祂后保留。
     */
    private static void applyDivineEnchantments(Player player) {
        java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>(player.getInventory().items);
        stacks.addAll(player.getInventory().armor);
        stacks.add(player.getOffhandItem());
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || !stack.is(net.minecraft.world.item.Items.STICK)) {
                continue; // 只处理木棍
            }
            // 1) 名称改为「棍木」（幂等：已是该名则不重复写）
            if (!STICK_DISPLAY.equals(stack.getHoverName().getString())) {
                stack.setHoverName(net.minecraft.network.chat.Component.literal(STICK_DISPLAY));
            }
            // 2) 写入全部正面附魔的最高等级（忽略冲突；木棍无附魔类别，故不做 canEnchant 过滤）
            java.util.Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(stack);
            java.util.Map<Enchantment, Integer> desired = new java.util.HashMap<>(existing);
            for (Enchantment enchantment : net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS) {
                if (enchantment == null || enchantment.isCurse()) {
                    continue; // 只加正面附魔（排除诅咒）
                }
                desired.put(enchantment, enchantment.getMaxLevel());
            }
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
        // ---- 玩家自身防护（1 太初 / 17 归墟 / 18 界缚）----
        if (victim instanceof Player player && wearing(player)) {
            // ═══ 真者祂的「六种伤害全拦截」防御体系 ═══
            // 本处理器注册在 LOWEST（最后执行），任何模组都无法在我们取消之后再把它改回来。
            //
            // ① 堆数值（极高攻击力 / 巨大伤害数字）
            //    ② 真伤 / 类型穿透（bypasses_armor 之类的伤害类型）
            //    ③ 篡改事件（别的模组在事件里改数值 → 我们在最后再夹一次）
            //      → 三者统一由下面「太初」的每秒 1 点额度处理：无论数值多大，一秒只吃 1 点。
            // ④ 直接改血量 / 逻辑致死（setHealth(0) + 手动死亡流程，如寰宇支配之剑）
            //      → 由 onDeath 取消死亡事件 + 每 tick 的 restoreVitals 回满血兜住。
            // ⑤ 兜底抹除里的 kill()（= genericKill + Float.MAX_VALUE）
            //      → 由下面的 isKillDamage 拦截 + onLivingAttack 更早一层拦截。
            //
            // ⚠ 唯一拦不住的：直接 discard()/remove() 把实体从世界里删掉 ——
            //   那一步不产生任何伤害事件，Forge 也没有可取消的钩子（见本类 onDeath 注释）。

            // ⑤ /kill 类（genericKill，含 kill() 与 /kill 指令）与 虚空伤害：直接免疫
            if (isInVoid(player) || isVoidDamage(event.getSource()) || isKillDamage(event.getSource())) {
                event.setCanceled(true);
                return;
            }
            // ①②③ 太初：每个自然秒内自身累计最多受到 1 点伤害，同秒内多余伤害全部无视
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
        // 试验假人（MmmMmmMmmMmm / Target Dummy）：不施加任何附加伤害，避免被打掉
        if (isProtectedDummy(victim)) {
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
        // 溢出保护：属性取的是 double 最大值，转 float 时会饱和成 Infinity，
        // 再乘生命值仍是 Infinity → 夹回有限值，避免 NaN 污染击退/显示/别的模组计算。
        if (!Float.isFinite(amount)) {
            amount = Float.MAX_VALUE;
        }
        event.setAmount(amount);

        // 三项开关：
        //   · 「9 天罚 + 17 归墟」的真伤 → 模组列表配置 htrue_true_damage
        //   · 「神威·诛灭」五重手段     → 模组列表配置 htrue_execution（且可用绑定按键临时关）
        //   · 「10 光之领域」范围伤害   → 保持原样，仍由游戏内按键控制（见 onPlayerTick）
        boolean trueDamage = com.divinebeast.divinebeast.DivineBeastConfig.HTRUE_TRUE_DAMAGE.get();
        boolean execute = com.divinebeast.divinebeast.DivineBeastConfig.HTRUE_EXECUTION.get()
                && CuriosEffectsState.htrueKillToggle(attacker);
        if (!trueDamage && !execute) {
            return; // 两个开关都关：只保留最基础的数值伤害
        }

        if (trueDamage) {
            // BOSS（末影龙 / 凋灵）：generic_kill / fell_out_of_world 这两段打不出来
            // —— 它们的 hurt 覆写不接受这种"无实体来源"的伤害类型（实测末影龙完全免疫），
            // 而普通伤害是有效的。故把两段等值伤害折进普通伤害：总倍率同样是 3 倍。
            if (isBossMob(victim)) {
                event.setAmount(amount * 3.0F);
            } else {
                // 家族②：真伤 / 类型穿透 —— 换成无视护甲·附魔·抗性的伤害类型
                applyingTrueDamage = true;
                try {
                    victim.hurt(victim.damageSources().genericKill(), amount);
                    victim.hurt(victim.damageSources().fellOutOfWorld(), amount);
                } finally {
                    applyingTrueDamage = false;
                }
            }
        }

        if (execute) {
            // 家族③④⑤⑥：多段 / 范围 / 逻辑致死 / 兜底抹除
            applyExecutionChain(attacker, victim, amount);
        }
    }

    /**
     * 「神威·诛灭」（B 键）：把社区里那些"无敌武器"用到的、<b>除堆数值之外</b>的全部手段
     * 依次施加一遍。每种手段对应一类原理，逐层递进：
     *
     * <ol>
     *   <li><b>多段 + 直接改血量</b>（{@link #EXECUTION_EXTRA_HITS} 段）：每段先走一次正常
     *       {@code hurt()}（触发事件与伤害数字），再直接 {@code setHealth()} 削减一次 ——
     *       直接改血量完全绕开原版的"{@code invulnerableTime} + {@code lastHurt}"无敌帧判定
     *       与一切减免，所以每段都必然落实。用途：击穿"单次伤害上限"型 BOSS。</li>
     *   <li><b>范围</b>（半径 {@link #EXECUTION_RADIUS} 格内的其它敌对生物）：同等待遇，
     *       用"次数"淹没各种上限。</li>
     *   <li><b>逻辑致死</b>（寰宇支配之剑那三步）：先打一段"等于目标当前血量"的真伤，
     *       再 {@code setHealth(0)} 直接置零（绕过一切伤害计算），最后补一发
     *       {@code generic_kill + Float.MAX_VALUE} 把目标推入死亡流程（保留掉落物与经验）。</li>
     *   <li><b>兜底抹除</b>：仍没进死亡流程 → {@code kill()}（等价 {@code /kill}）。
     *       注：这里刻意<b>不用</b> {@code discard()} —— 那会跳过死亡流程、没有掉落物，
     *       还会破坏 BOSS 战与任务进度，而且没有任何事件可以拦截它。</li>
     * </ol>
     *
     * <p>全程持有 {@code applyingTrueDamage} 标记：嵌套的 {@code hurt()} 会再次触发
     * {@link #onLivingDamage}，靠这个标记直接返回，避免无限递归。
     *
     * <p><b>无法击杀的情况</b>（与社区里那些"杀不死"的玩意一致）：目标取消了死亡事件
     * （{@code LivingDeathEvent}）、或每 tick 把血量写回（锁血）、或根本没有可命中的判定。
     */
    private static void applyExecutionChain(Player attacker, LivingEntity victim, float amount) {
        applyingTrueDamage = true;
        try {
            net.minecraft.world.damagesource.DamageSource playerAttack =
                    victim.damageSources().playerAttack(attacker);
            // ——— ③ 多段 + ④ 直接改血量 ———
            // 每段"先走一次正常 hurt（触发伤害事件与伤害数字），再直接 setHealth 削减一次"。
            // 直接改血量完全绕开原版的 invulnerableTime / lastHurt 无敌帧判定和一切减免，
            // 因此 8 段一定会全部落实 —— 专治"单次伤害上限"型 BOSS。
            for (int i = 0; i < EXECUTION_EXTRA_HITS && victim.isAlive(); i++) {
                victim.hurt(playerAttack, amount);
                victim.setHealth(Math.max(0.0F, victim.getHealth() - amount));
            }
            // ——— ⑥ 范围：半径内其它敌对生物一并结算 ———
            for (LivingEntity other : attacker.level().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(attacker.position(),
                            EXECUTION_RADIUS * 2, EXECUTION_RADIUS * 2, EXECUTION_RADIUS * 2))) {
                if (other == victim || other.is(attacker) || !other.isAlive()
                        || !(other instanceof net.minecraft.world.entity.monster.Enemy)
                        || isProtectedDummy(other)) {
                    continue;
                }
                other.hurt(other.damageSources().genericKill(), amount);
                other.setHealth(Math.max(0.0F, other.getHealth() - amount));
                killWithDrops(other);
            }
            // ——— ⑤ 逻辑致死 + 兜底抹除 ———
            killWithDrops(victim);
        } catch (Throwable t) {
            // 任何异常都不该把整局游戏带崩，只记一次日志
            LOGGER.warn("[divinebeast] 神威·诛灭 执行链异常", t);
        } finally {
            applyingTrueDamage = false;
        }
    }

    /**
     * 「逻辑致死」三步（寰宇支配之剑的原理）：
     * <ol>
     *   <li><b>等血真伤</b>：用无视护甲/附魔/抗性的伤害类型打一段"等于目标当前血量"的伤害 ——
     *       走正常死亡流程，所以掉落物与经验球都正常产出；</li>
     *   <li><b>血量置零</b>：{@code setHealth(0)} 直接改血量，绕过一切减免与伤害上限
     *       （原版把生物血量数据改成 0 本身就足以造成致死效果）；</li>
     *   <li><b>兜底抹除</b>：仍没进入死亡流程就补一发 {@code kill()}
     *       （等价 {@code /kill}：{@code genericKill + Float.MAX_VALUE}）。</li>
     * </ol>
     * 刻意不用 {@code discard()}：那会跳过死亡流程、没有掉落物，还会破坏 BOSS 战与任务进度，
     * 而且没有任何事件能拦截它。
     */
    private static void killWithDrops(LivingEntity target) {
        if (target.isRemoved()) {
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

    /** 是否是"虚空伤害"（跌出世界）。同时兼容 outOfWorld / fellOutOfWorld 两种 message id。 */
    private static boolean isVoidDamage(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) {
            return false;
        }
        String id = source.getMsgId();
        return "outOfWorld".equals(id) || "fellOutOfWorld".equals(id);
    }

    /**
     * 1 太初 / 17 归墟：是否处于"虚空"（位于世界底部之下）。
     * 虚空伤害只可能在此发生，用坐标判定最稳（不依赖伤害类型的 message id，
     * 也不受其它模组自定义虚空伤害类型影响）。
     */
    private static boolean isInVoid(Player player) {
        return player.getY() < (double) player.level().getMinBuildHeight();
    }

    /** 18 界缚：是否是 /kill 这类"必杀"伤害（genericKill）。 */
    private static boolean isKillDamage(net.minecraft.world.damagesource.DamageSource source) {
        return source != null && "genericKill".equals(source.getMsgId());
    }

    /**
     * 是否是"进程型 BOSS"（末影龙 / 凋灵）。
     *
     * <p>用途：这两者的 {@code hurt} 覆写不接受无实体来源的伤害类型
     * （{@code generic_kill} / {@code fell_out_of_world}），附加真伤对它们完全无效，
     * 因此"天罚 + 归墟"必须折进普通伤害里才会在 BOSS 身上生效。
     */
    private static boolean isBossMob(LivingEntity entity) {
        // 本模组的中立 boss 也一并算进来：它们同样不接受"无实体来源"的真伤，
        // 所以「天罚 + 归墟」折进普通伤害（3 倍）才是对它们正确的结算方式。
        return entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || entity instanceof com.divinebeast.divinebeast.boss.DivineBoss;
    }

    /**
     * 是否是应当保护的"试验假人"（MmmMmmMmmMmm / Target Dummy，实体 id 为 {@code mmm:dummy}）。
     * 该类假人用于显示伤害数字，不应被本模组的高额附加真伤打掉，故跳过全部附加伤害
     * （只保留原版普通攻击结算，交由假人自身逻辑处理）。按注册名判定，不产生硬依赖。
     */
    private static boolean isProtectedDummy(Entity entity) {
        if (entity == null) {
            return false;
        }
        net.minecraft.resources.ResourceLocation key =
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key != null && "mmm".equals(key.getNamespace()) && "dummy".equals(key.getPath());
    }

    /**
     * 1 太初 / 17 归墟 / 18 界缚：在 {@code hurt()} 最早阶段拦截"虚空伤害"与"/kill"。
     * 放在 LivingAttackEvent（比 LivingDamageEvent 更早）可确保任何伤害类型命名下都生效。
     */
    private static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player && wearing(player)
                && (isInVoid(player) || isVoidDamage(event.getSource()) || isKillDamage(event.getSource()))) {
            event.setCanceled(true);
        }
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

    /**
     * 18 界缚：<b>只拦"别的玩家用 /tp 把你传送走"</b>。以下情况一律放行：
     * <ul>
     *   <li>自己发的传送指令（传自己、传别人、传坐标）；</li>
     *   <li>真者祂玩家发的传送指令（包含"我传送别人"）；</li>
     *   <li><b>没有玩家发起者的传送</b> —— 控制台、命令方块、以及其它模组的强制传送。</li>
     * </ul>
     *
     * <p>难点：{@code EntityTeleportEvent.TeleportCommand} 不携带命令来源，无法从事件本身
     * 区分"我 /tp 自己"与"别人 /tp 我"。因此改为在 {@link CommandEvent} 记录本次指令的
     * 玩家发起者，再在这里比对。用时间戳（而非 gameTime）做有效性窗口，避免跨维度不同步。
     */
    private static void onTeleportCommand(EntityTeleportEvent.TeleportCommand event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof Player target) || !wearing(target)) {
            return;
        }
        if (isForeignPlayerTeleport(target)) {
            event.setCanceled(true); // 别的玩家想把你传送走：禁止
        }
    }

    /** 记录"由玩家执行的指令"的发起者（供传送判定使用）。 */
    private static void onCommand(net.minecraftforge.event.CommandEvent event) {
        try {
            if (event.getParseResults().getContext().getSource().getEntity() instanceof Player player) {
                lastCommandIssuer = player.getUUID();
                lastCommandIssuerMillis = System.currentTimeMillis();
            } else {
                lastCommandIssuer = null;
                lastCommandIssuerMillis = 0L;
            }
        } catch (Throwable t) {
            lastCommandIssuer = null;
            lastCommandIssuerMillis = 0L;
        }
    }

    /**
     * 是否需要拦截本次传送：<b>仅当</b>"由别的玩家执行的指令"把真者祂玩家当作目标时才拦。
     * 指令是同步执行的，所以用很短的窗口（{@value #COMMAND_ISSUER_WINDOW_MS} ms）判定
     * "本次传送是否由刚才那条指令触发"；窗口外（含其它模组的强制传送）一律放行。
     */
    private static boolean isForeignPlayerTeleport(Player target) {
        if (lastCommandIssuer == null) {
            return false; // 无玩家发起者：控制台 / 命令方块 / 其它模组 → 放行
        }
        if (System.currentTimeMillis() - lastCommandIssuerMillis > COMMAND_ISSUER_WINDOW_MS) {
            return false; // 与本次传送无关（不是刚执行的指令触发的）→ 放行
        }
        if (lastCommandIssuer.equals(target.getUUID())) {
            return false; // 自己 /tp 自己（含坐标）→ 放行
        }
        Player issuer = target.level().getPlayerByUUID(lastCommandIssuer);
        if (issuer != null && wearing(issuer)) {
            return false; // 真者祂玩家传送别人 → 放行
        }
        return true; // 别的玩家 /tp 你 → 拦截
    }
}
