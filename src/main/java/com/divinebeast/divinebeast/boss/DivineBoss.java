package com.divinebeast.divinebeast.boss;

import com.divinebeast.divinebeast.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 三个中立 boss 的共同基类：『我』/『兽』/『祂』。
 *
 * <p><b>通用规则</b>
 * <ol>
 *   <li><b>中立</b>：AI 里没有任何"主动挑玩家"的目标选择器；只有被玩家打过之后才会还手。</li>
 *   <li><b>被玩家攻击前不移动</b>：出生 {@code setNoAi(true)}；被打中 → 解锁 AI 并锁定该玩家。</li>
 *   <li><b>只有玩家能伤到它</b>：其它来源（怪物、火焰、摔落、虚空、{@code /kill}）一律无效。</li>
 *   <li><b>推开敌对生物</b>：每 0.5 秒把 50 格内的敌对生物往外推，并清掉它们对本 boss 的仇恨。</li>
 *   <li><b>分阶段</b>：血量 ≤66% 第二阶段、≤33% 第三阶段，攻击与移速各 +25%/+50%。</li>
 *   <li><b>被"击杀"只影响战斗状态</b>（{@link #onDefeated}）：掉落同名武器 ×1 + 同名方块 ×3、觉醒 +10%（上限 10 层）、
 *       回满血回原位。<b>boss 永远不会从世界里消失</b> —— 一场战斗结束后它只是"退回无敌对状态"，
 *       再被攻击就会开始新的一场（命数 / 吸取计数 / 时限全部重置）。</li>
 *   <li><b>交易</b>：右键交 16 个对应物品（我=绿宝石 / 兽=熟猪排 / 祂=泥土）换 3 个同名方块，
 *       冷却 0.5 秒。</li>
 * </ol>
 *
 * <p><b>各自的杀法</b>
 * <ul>
 *   <li><b>『我』</b>：每次被玩家攻击，<b>伤害结算后回复所受伤害的一半</b>，并且<b>血量上限翻倍</b>
 *       （封顶 1000）——所以必须用"单次超过它当前上限"的爆发一次打死。
 *       它每 20 秒召分身，<b>第一阶段 4 个、每进一阶段 +2 个</b>；<b>分身一被打就消失</b>，
 *       并把这次伤害等量回血给本体。</li>
 *   <li><b>『兽』</b>：<b>每秒最多只受 1 点伤害</b>。结束战斗有两条路：① 硬砍（800 点血，
 *       按 1/秒要很久）；② 让它<b>累计吸取玩家 200 点生命</b>（汲取量就显示在它的血条上），
 *       或者<b>在战斗中喂它熟猪排</b>——每 0.5 秒可喂 1 个，每个让"还需吸取"减少 20 点。
 *       任意一条达成，这场战斗就结束。</li>
 *   <li><b>『祂』</b>：被攻击后开始计时。计量期间被打散只是「重新生成」（计时不停）；
 *       <b>满 100 秒时祂自行死亡</b>。</li>
 * </ul>
 */
public class DivineBoss extends PathfinderMob {

    /** 三个 boss 各自的台面方块、交易货币与战斗数值。 */
    public enum Kind {
        /** 『我』：主世界 · 泥土台面 · 铁质材料（我锭/我剑/我盔甲）· 收绿宝石 */
        SELF(Blocks.DIRT, Items.EMERALD, 500.0D, 20.0D, 10.0D, BossEvent.BossBarColor.GREEN),
        /** 『兽』：地狱 · 下界岩台面 · 金质材料 · 收熟猪排 */
        BEAST(Blocks.NETHERRACK, Items.COOKED_PORKCHOP, 800.0D, 30.0D, 15.0D, BossEvent.BossBarColor.RED),
        /** 『祂』：末地 · 末地石台面 · 下界合金材料 · 收泥土 */
        HE(Blocks.END_STONE, Items.DIRT, 1200.0D, 40.0D, 20.0D, BossEvent.BossBarColor.WHITE);

        private final Block floor;
        private final Item currency;
        private final double maxHealth;
        private final double attackDamage;
        private final double armor;
        private final BossEvent.BossBarColor barColor;

        Kind(Block floor, Item currency, double maxHealth, double attackDamage, double armor,
             BossEvent.BossBarColor barColor) {
            this.floor = floor;
            this.currency = currency;
            this.maxHealth = maxHealth;
            this.attackDamage = attackDamage;
            this.armor = armor;
            this.barColor = barColor;
        }

        public Block floor() {
            return floor;
        }

        public Item currency() {
            return currency;
        }

        public BossEvent.BossBarColor barColor() {
            return barColor;
        }

        // 下面两个延迟到调用时才取 RegistryObject（枚举在类加载时就初始化，那时物品表可能还没填好）

        /** 击杀掉落的同名武器（我 / 兽 / 祂） */
        public Item weapon() {
            return switch (this) {
                case SELF -> ModItems.SELF_SWORD.get();
                case BEAST -> ModItems.BEAST_SWORD.get();
                case HE -> ModItems.HE_SWORD.get();
            };
        }

        /** 交易 / 击杀获得的同名方块 */
        public Item blockItem() {
            return switch (this) {
                case SELF -> ModItems.SELF_BLOCK_ITEM.get();
                case BEAST -> ModItems.BEAST_BLOCK_ITEM.get();
                case HE -> ModItems.HE_BLOCK_ITEM.get();
            };
        }
    }

    /** 推离敌对生物的半径（格） */
    public static final double REPEL_RADIUS = 50.0D;
    private static final double REPEL_STRENGTH = 1.4D;

    /** 交易一次消耗的数量（我=绿宝石 / 兽=熟猪排 / 祂=泥土） */
    public static final int TRADE_COST = 16;
    /** 交易冷却（tick）：0.5 秒 */
    public static final int TRADE_COOLDOWN_TICKS = 10;
    /** 交易或击杀时给的同名方块数量 */
    public static final int DROP_BLOCKS = 3;

    /** 阶段阈值 */
    private static final float PHASE_TWO_RATIO = 0.66F;
    private static final float PHASE_THREE_RATIO = 0.33F;
    private static final double PHASE_BONUS = 0.25D;
    /** 重生觉醒 */
    private static final double AWAKEN_BONUS_PER_STACK = 0.1D;
    public static final int AWAKEN_MAX_STACKS = 10;

    // ---- 『我』----
    /** 本体血量上限的封顶值（被攻击翻倍到此为止） */
    public static final double SELF_HEALTH_CAP = 1000.0D;
    /** 第一阶段的分身数；每进一阶段 +2 */
    private static final int CLONE_BASE_COUNT = 4;
    private static final int CLONE_COUNT_PER_PHASE = 2;
    private static final int CLONE_LIFE_TICKS = 400;
    private static final int SELF_MOVE_COOLDOWN = 400;
    /** 『我』在一次战斗里的命数：打完 4 条命这场战斗就结束 */
    public static final int SELF_LIVES = 4;

    // ---- 『兽』----
    /** 合计需要吸取的生命值；吸取或喂食把它减到 0 时『兽』死亡 */
    public static final float BEAST_DRAIN_TOTAL = 200.0F;
    /** 一个熟猪排减少的"还需吸取"量 */
    public static final float BEAST_FEED_REDUCTION = 20.0F;
    /** 喂食冷却（tick）：0.5 秒 */
    public static final int BEAST_FEED_COOLDOWN_TICKS = 10;
    /** 每秒最多受到的伤害 */
    private static final float BEAST_DAMAGE_CAP = 1.0F;

    // ---- 『祂』----
    /** 被攻击后自行"变得可杀"的初始时限（tick）：100 秒 */
    public static final int HE_LIFE_LIMIT_TICKS = 2000;
    /** 战斗中玩家每死一次，时限上限减少的秒数（tick）：10 秒 */
    public static final int HE_DEATH_REDUCTION_TICKS = 200;
    /** 时限下限（tick）：10 秒 */
    public static final int HE_MIN_LIMIT_TICKS = 200;
    /** 光柱招式冷却（12 秒）与警示时长（1.5 秒） */
    private static final int HE_MOVE_COOLDOWN = 240;
    private static final int HE_WARN_TICKS = 30;
    /** 祂的光柱：每秒 10 点、持续 2 秒、无视防御 / 伤免 / 护甲 */
    private static final int HE_PILLAR_LIFE_TICKS = 40;
    private static final int HE_PILLAR_PERIOD_TICKS = 20;
    private static final float HE_PILLAR_DAMAGE_PER_SECOND = 10.0F;

    private static final UUID PHASE_ATTACK_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000e1");
    private static final UUID PHASE_SPEED_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000e2");
    private static final UUID AWAKEN_HEALTH_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000e3");
    private static final UUID AWAKEN_ATTACK_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000e4");
    private static final UUID CLONE_HEALTH_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000e5");
    private static final UUID CLONE_ATTACK_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000e6");
    private static final UUID SELF_DOUBLE_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000e7");

    private final Kind kind;
    private final ServerBossEvent bossBar;
    private boolean provoked;
    private double homeX;
    private double homeY;
    private double homeZ;
    private boolean homeSet;
    private boolean respawning;
    private long lastTradeTick = -1000L;
    private long lastFeedTick = -1000L;
    private int phase = 1;
    private int awakenings;
    private int moveCooldown;

    /** 分身：无掉落、限时、被打就消散并把伤害转成本体治疗 */
    private boolean clone;
    private int cloneLife;
    private UUID ownerUuid;

    /** 本次战斗还剩几条命（『我』= 4 条；『兽』『祂』由各自机制决定何时结束战斗） */
    private int livesLeft = SELF_LIVES;

    /** 『兽』：还需吸取的生命值 */
    private float beastDrainRemaining = BEAST_DRAIN_TOTAL;
    /** 『兽』：本秒是否已经吃过伤害（每秒最多 1 点） */
    private long beastLastDamageSecond = -1L;

    /** 『祂』：计时起点（-1 = 还没被攻击过） */
    private long heTimerStart = -1L;
    /** 『祂』：当前的上限（玩家每死一次 -10 秒，最低 10 秒） */
    private int heLimitTicks = HE_LIFE_LIMIT_TICKS;
    /** 是否已经播报过"『祂』不再重生" */
    private boolean killableAnnounced;

    public DivineBoss(EntityType<? extends PathfinderMob> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        this.setCustomName(this.getType().getDescription());
        this.bossBar = new ServerBossEvent(this.getDisplayName(), kind.barColor(),
                BossEvent.BossBarOverlay.PROGRESS);
        this.bossBar.setProgress(1.0F);
        this.setPersistenceRequired();
        this.setNoAi(true);   // 被玩家攻击前不移动
        this.xpReward = 500;
        this.setHealth(this.getMaxHealth());
    }

    public static AttributeSupplier.Builder createAttributes(Kind kind) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, kind.maxHealth)
                .add(Attributes.ATTACK_DAMAGE, kind.attackDamage)
                .add(Attributes.ARMOR, kind.armor)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    public Kind kind() {
        return kind;
    }

    public boolean isProvoked() {
        return provoked;
    }

    public boolean isClone() {
        return clone;
    }

    public int getPhase() {
        return phase;
    }

    public int getAwakenings() {
        return awakenings;
    }

    /** 『兽』还需吸取的生命值（血条按它显示） */
    public float getBeastDrainRemaining() {
        return beastDrainRemaining;
    }

    public void setHome(double x, double y, double z) {
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.homeSet = true;
    }

    /** 变成『我』的限时分身：血攻 20%、无经验无掉落、被打即散、{@code lifeTicks} 后自行消散。 */
    public void becomeClone(UUID owner, int lifeTicks) {
        this.clone = true;
        this.ownerUuid = owner;
        this.cloneLife = lifeTicks;
        this.provoked = true;
        this.setNoAi(false);
        this.xpReward = 0;
        this.setAttributeMultiplier(Attributes.MAX_HEALTH, CLONE_HEALTH_MOD, "divinebeast_boss_clone_hp", -0.8D);
        this.setAttributeMultiplier(Attributes.ATTACK_DAMAGE, CLONE_ATTACK_MOD, "divinebeast_boss_clone_atk", -0.8D);
        this.setHealth(this.getMaxHealth());
    }

    // ==================================================================
    // AI / 主循环
    // ==================================================================

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (!this.homeSet) {
            this.setHome(this.getX(), this.getY(), this.getZ());
        }
        if (this.clone) {
            if (--this.cloneLife <= 0) {
                this.discard();
                return;
            }
        }
        if (this.tickCount % 10 == 0) {
            this.updateBossBar();
            this.repelHostiles();
        }
        if (!this.clone && this.tickCount % 20 == 0) {
            this.updatePhase();
        }
        // 『祂』：计时满当前时限的那一刻播报一次"不再重生"（此后它才允许真正死亡）
        if (this.kind == Kind.HE && !this.clone && !this.killableAnnounced && this.heTimerExpired()) {
            this.killableAnnounced = true;
            this.announce("divinebeast.msg.boss.he_expired");
        }
        this.tickMoves();
    }

    private void updateBossBar() {
        float progress;
        if (this.kind == Kind.BEAST && !this.clone) {
            // 『兽』的血条显示"还需吸取多少生命"（吸满/喂饱即死）
            progress = 1.0F - Math.max(0.0F, Math.min(1.0F,
                    this.beastDrainRemaining / BEAST_DRAIN_TOTAL));
        } else {
            progress = this.getMaxHealth() > 0.0F ? this.getHealth() / this.getMaxHealth() : 0.0F;
        }
        this.bossBar.setProgress(progress);
    }

    private void repelHostiles() {
        double limit = REPEL_RADIUS * REPEL_RADIUS;
        AABB box = AABB.ofSize(this.position(), REPEL_RADIUS * 2, REPEL_RADIUS * 2, REPEL_RADIUS * 2);
        for (Mob mob : this.level().getEntitiesOfClass(Mob.class, box)) {
            if (mob == this || !mob.isAlive() || !(mob instanceof Enemy)) {
                continue;
            }
            if (this.distanceToSqr(mob) > limit) {
                continue;
            }
            Vec3 away = mob.position().subtract(this.position());
            away = away.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : away.normalize();
            mob.push(away.x * REPEL_STRENGTH, 0.35D, away.z * REPEL_STRENGTH);
            mob.hurtMarked = true;
            if (mob.getTarget() == this) {
                mob.setTarget(null);
            }
        }
    }

    // ==================================================================
    // 阶段 / 觉醒 / 招式
    // ==================================================================

    private void updatePhase() {
        float ratio = this.getMaxHealth() > 0.0F ? this.getHealth() / this.getMaxHealth() : 1.0F;
        int target = ratio <= PHASE_THREE_RATIO ? 3 : (ratio <= PHASE_TWO_RATIO ? 2 : 1);
        if (target == this.phase) {
            return;
        }
        boolean advanced = target > this.phase;
        this.phase = target;
        this.applyPhase();
        if (advanced) {
            this.bossBar.setName(Component.translatable("divinebeast.boss.phase_name",
                    this.getDisplayName(), this.phase));
            this.announce("divinebeast.msg.boss.phase", this.getDisplayName(), this.phase);
            this.level().playSound(null, this.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 1.4F);
        }
    }

    private void applyPhase() {
        double bonus = this.phase <= 1 ? 0.0D : (this.phase == 2 ? PHASE_BONUS : PHASE_BONUS * 2.0D);
        this.setAttributeMultiplier(Attributes.ATTACK_DAMAGE, PHASE_ATTACK_MOD, "divinebeast_boss_phase_atk", bonus);
        this.setAttributeMultiplier(Attributes.MOVEMENT_SPEED, PHASE_SPEED_MOD, "divinebeast_boss_phase_spd", bonus);
    }

    private void applyAwakening() {
        double bonus = AWAKEN_BONUS_PER_STACK * this.awakenings;
        this.setAttributeMultiplier(Attributes.MAX_HEALTH, AWAKEN_HEALTH_MOD, "divinebeast_boss_awaken_hp", bonus);
        this.setAttributeMultiplier(Attributes.ATTACK_DAMAGE, AWAKEN_ATTACK_MOD, "divinebeast_boss_awaken_atk", bonus);
    }

    /** 招式：被激怒后即可使用（不再限制阶段）。 */
    private void tickMoves() {
        if (this.clone || !this.provoked) {
            return;
        }
        if (this.moveCooldown > 0) {
            this.moveCooldown--;
            return;
        }
        switch (this.kind) {
            case SELF -> this.summonClones();
            case HE -> this.callLightPillar();
            default -> {
                // 『兽』的招式在命中玩家时结算（吸取生命）
            }
        }
    }

    /** 当前阶段该召几个分身：第一阶段 4 个，每进一阶段 +2。 */
    public int cloneCountForPhase() {
        return CLONE_BASE_COUNT + CLONE_COUNT_PER_PHASE * (this.phase - 1);
    }

    private void summonClones() {
        if (!(this.level() instanceof ServerLevel serverLevel) || this.getTarget() == null) {
            return;
        }
        int count = this.cloneCountForPhase();
        for (int i = 0; i < count; i++) {
            SelfBoss copy = ModEntities.SELF_BOSS.get().create(serverLevel);
            if (copy == null) {
                continue;
            }
            double angle = Math.PI * 2.0D * i / count;
            copy.moveTo(this.getX() + Math.cos(angle) * 2.5D, this.getY(),
                    this.getZ() + Math.sin(angle) * 2.5D, this.getYRot(), 0.0F);
            copy.setHome(copy.getX(), copy.getY(), copy.getZ());
            copy.becomeClone(this.getUUID(), CLONE_LIFE_TICKS);
            copy.setTarget(this.getTarget());
            serverLevel.addFreshEntity(copy);
        }
        this.moveCooldown = SELF_MOVE_COOLDOWN;
        this.announce("divinebeast.msg.boss.clones", this.getDisplayName(), count);
    }

    /** 『祂』：在最近玩家脚下画警示圈，1.5 秒后落下光柱（每秒 10 点、持续 2 秒、无视防御/伤免/护甲）。 */
    private void callLightPillar() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player target = serverLevel.getNearestPlayer(this, 64.0D);
        if (target == null) {
            return;
        }
        LightPillar.strike(serverLevel, target.position(), this, true, HE_WARN_TICKS,
                HE_PILLAR_LIFE_TICKS, HE_PILLAR_DAMAGE_PER_SECOND, HE_PILLAR_PERIOD_TICKS, true);
        this.moveCooldown = HE_MOVE_COOLDOWN;
        this.announce("divinebeast.msg.boss.pillar", this.getDisplayName(),
                (int) target.getX(), (int) target.getZ());
    }

    private void setAttributeMultiplier(Attribute attribute, UUID uuid, String name, double multiplyTotal) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (instance.getModifier(uuid) != null) {
            instance.removeModifier(uuid);
        }
        if (Math.abs(multiplyTotal) > 1.0E-6D) {
            instance.addPermanentModifier(new AttributeModifier(uuid, name, multiplyTotal,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private void announce(String key, Object... args) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(this) < 128.0D * 128.0D) {
                player.sendSystemMessage(Component.translatable(key, args));
            }
        }
    }

    private void broadcast(String key, Object... args) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.getServer().getPlayerList()
                .broadcastSystemMessage(Component.translatable(key, args), false);
    }

    // ==================================================================
    // 受击
    // ==================================================================

    /**
     * 只有玩家能伤到它，且各 boss 有自己的受击规则：
     * <ul>
     *   <li><b>分身</b>：一被打到就消散，并把本次伤害等量回血给本体。</li>
     *   <li><b>『我』</b>：结算后回复所受伤害的一半，且血量上限翻倍（封顶 1000）。</li>
     *   <li><b>『兽』</b>：每秒最多只受 1 点伤害。</li>
     * </ul>
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(source.getEntity() instanceof Player player)) {
            return false;
        }
        if (this.clone) {
            // 分身：被打即散，并把伤害等量治疗本体
            if (!this.level().isClientSide) {
                DivineBoss owner = this.findOwner();
                if (owner != null) {
                    owner.heal(amount);
                    owner.announce("divinebeast.msg.boss.clone_absorbed",
                            this.getDisplayName(), owner.getDisplayName(), (int) amount);
                }
                this.discard();
            }
            return true;
        }
        if (this.kind == Kind.BEAST) {
            long second = this.level().getGameTime() / 20L;
            if (this.beastLastDamageSecond == second) {
                return false;   // 本秒已经吃过伤害了
            }
            this.beastLastDamageSecond = second;
            amount = Math.min(amount, BEAST_DAMAGE_CAP);
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide) {
            this.provoke(player);
            if (this.kind == Kind.SELF) {
                // 结算后回复所受伤害的一半，并让血量上限翻倍（封顶 1000）
                this.heal(amount * 0.5F);
                this.doubleSelfMaxHealth();
            }
            if (this.kind == Kind.HE && this.heTimerStart < 0L) {
                this.heTimerStart = this.level().getGameTime();   // 被攻击后开始计时
                this.announce("divinebeast.msg.boss.he_timer");
            }
        }
        return hurt;
    }

    /** 『我』：每次受击把血量上限翻倍（封顶 {@link #SELF_HEALTH_CAP}）。 */
    private void doubleSelfMaxHealth() {
        AttributeInstance instance = this.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(SELF_DOUBLE_MOD);
        if (existing != null) {
            instance.removeModifier(SELF_DOUBLE_MOD);
        }
        double base = instance.getValue();   // 去掉本修饰符之后的当前上限
        double target = Math.min(SELF_HEALTH_CAP, base * 2.0D);
        if (target - base > 0.01D) {
            instance.addPermanentModifier(new AttributeModifier(SELF_DOUBLE_MOD,
                    "divinebeast_boss_self_double", target - base, AttributeModifier.Operation.ADDITION));
        }
    }

    private DivineBoss findOwner() {
        if (this.ownerUuid == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity owner = serverLevel.getEntity(this.ownerUuid);
        return owner instanceof DivineBoss boss ? boss : null;
    }

    private void provoke(Player player) {
        if (!this.provoked) {
            // 新一轮战斗开始：命数、吸取计数、祂的时限全部回到初始
            this.provoked = true;
            this.setNoAi(false);
            this.resetCombatState();
            if (this.kind == Kind.HE) {
                this.heTimerStart = this.level().getGameTime();
                this.announce("divinebeast.msg.boss.he_timer");
            }
        }
        this.setLastHurtByMob(player);
        this.setTarget(player);
    }

    /** 一次战斗的初始状态：『我』4 条命、『兽』需吸取 200 点、『祂』时限 100 秒。 */
    private void resetCombatState() {
        this.livesLeft = this.kind == Kind.SELF ? SELF_LIVES : Integer.MAX_VALUE;
        this.beastDrainRemaining = BEAST_DRAIN_TOTAL;
        this.heLimitTicks = HE_LIFE_LIMIT_TICKS;
        this.heTimerStart = -1L;
        this.killableAnnounced = false;
        this.beastLastDamageSecond = -1L;
    }

    public int getLivesLeft() {
        return this.kind == Kind.SELF ? Math.max(0, this.livesLeft) : 0;
    }

    /** 『兽』的招式：命中玩家时吸取玩家一半当前生命，并回复"吸取量 ×10"的血。 */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide && this.kind == Kind.BEAST && target instanceof Player player) {
            float drain = player.getHealth() * 0.5F;
            if (drain > 0.0F) {
                player.hurt(player.damageSources().mobAttack(this), drain);
                this.heal(drain * 10.0F);
                this.consumeDrain(drain);
                this.announce("divinebeast.msg.boss.drain", this.getDisplayName(), (int) drain);
            }
        }
        return hit;
    }

    /**
     * 记录『兽』吸取到的生命；凑满 {@link #BEAST_DRAIN_TOTAL} 点它就死去。
     * 喂食也走这里（减少同一个计数）。
     */
    public void consumeDrain(float amount) {
        if (this.kind != Kind.BEAST || !this.provoked) {
            return;
        }
        this.beastDrainRemaining = Math.max(0.0F, this.beastDrainRemaining - amount);
        if (this.beastDrainRemaining <= 0.0F) {
            // 吸满 200 点：这场战斗到此为止（它"死去"＝退回无敌对状态）
            this.onDefeated(null);
        }
    }

    // ==================================================================
    // 重生 / 真死
    // ==================================================================

    /** 战利品：同名武器 ×1 + 同名方块 ×3（掉在当前位置）。 */
    private void dropBossLoot() {
        this.spawnAtLocation(new ItemStack(this.kind.weapon()));
        this.spawnAtLocation(new ItemStack(this.kind.blockItem(), DROP_BLOCKS));
    }

    /**
     * 被击败（本场战斗的一次"死亡"）的结算。
     *
     * <p><b>真死只针对战斗状态</b>：boss 永远不会从世界里消失 —— 结算时只是"退回静止"。
     * <ul>
     *   <li><b>『我』</b>：一场战斗有 {@link #SELF_LIVES} 条命。还有命 → 原地满血续战（仇恨不清）；
     *       4 条命打完 → 这场战斗结束，回归无敌对状态。</li>
     *   <li><b>『兽』</b>：被伤害打死、或吸满 200 点（含被喂饱），任意一种都让这场战斗结束。</li>
     *   <li><b>『祂』</b>：计时没满之前被打散只是续战；满时限之后被打死＝这场战斗结束。</li>
     * </ul>
     * 无论哪种，都会掉战利品、觉醒 +1、回满血、回原位；战斗结束后再被攻击即开始新的一场
     * （命数 / 吸取计数 / 时限全部重置）。
     */
    public void onDefeated(Component killerName) {
        if (this.level().isClientSide || this.respawning) {
            return;
        }
        this.respawning = true;
        try {
            this.dropBossLoot();
            if (this.awakenings < AWAKEN_MAX_STACKS) {
                this.awakenings++;
                this.applyAwakening();
            }
            if (this.kind == Kind.SELF && this.livesLeft > 0 && this.livesLeft != Integer.MAX_VALUE) {
                this.livesLeft--;
            }
            boolean combatOver = this.combatOverOnDefeat();

            // 通用收尾
            this.phase = 1;
            this.applyPhase();
            this.moveCooldown = 0;
            this.setHealth(this.getMaxHealth());
            this.clearFire();
            this.setRemainingFireTicks(0);
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.fallDistance = 0.0F;
            this.hurtTime = 0;
            if (this.homeSet) {
                this.moveTo(this.homeX, this.homeY, this.homeZ, this.getYRot(), this.getXRot());
            }
            this.bossBar.setProgress(1.0F);
            this.bossBar.setName(this.getDisplayName());

            Component killer = killerName == null
                    ? Component.translatable("divinebeast.boss.unknown_killer") : killerName;

            if (combatOver) {
                // 回归无敌对状态：清仇恨、恢复静止，等玩家再打它才会开始新的一场
                this.provoked = false;
                this.setTarget(null);
                this.setLastHurtByMob(null);
                this.setLastHurtByPlayer(null);
                this.setNoAi(true);
                this.resetCombatState();
                this.broadcast("divinebeast.msg.boss.defeated_broadcast",
                        this.getDisplayName(), (int) this.getX(), (int) this.getZ(),
                        killer, this.awakenings);
                this.announce("divinebeast.msg.boss.defeated", this.getDisplayName(), this.awakenings);
            } else if (this.kind == Kind.SELF) {
                // 还有命：保持仇恨与 AI，原地续战
                this.setNoAi(false);
                this.broadcast("divinebeast.msg.boss.respawn_broadcast_lives",
                        this.getDisplayName(), (int) this.getX(), (int) this.getZ(),
                        killer, this.awakenings, this.livesLeft);
                this.announce("divinebeast.msg.boss.respawn_lives", this.getDisplayName(), this.livesLeft);
            } else {
                this.setNoAi(false);
                this.broadcast("divinebeast.msg.boss.respawn_broadcast",
                        this.getDisplayName(), (int) this.getX(), (int) this.getZ(),
                        killer, this.awakenings);
                this.announce("divinebeast.msg.boss.respawn", this.getDisplayName());
            }
        } finally {
            this.respawning = false;
        }
    }

    /** 这次"死亡"是否意味着整场战斗结束。 */
    private boolean combatOverOnDefeat() {
        return switch (this.kind) {
            case SELF -> this.livesLeft <= 0;
            // 『兽』：被伤害打死同样算这场战斗结束（另一条路是吸满 / 喂饱 200 点）
            case BEAST -> true;
            case HE -> this.heTimerExpired();
        };
    }

    /** 是否已经打完"整场战斗"（供外部判断，例如绘制/播报）。 */
    public boolean isCombatOver() {
        return !this.provoked;
    }

    /**
     * 『祂』是否已经活满当前时限（从被玩家第一次攻击开始计时）。
     * <b>只有满了时限，祂才允许真正死亡</b>；在此之前被打散一律只是"重新生成"。
     */
    public boolean heTimerExpired() {
        if (this.kind != Kind.HE || this.heTimerStart < 0L) {
            return false;
        }
        return this.level().getGameTime() - this.heTimerStart >= this.heLimitTicks;
    }

    /**
     * 与祂战斗的玩家死亡时调用：<b>计时重新回到上限，但上限本身减少 10 秒</b>（最低 10 秒）。
     */
    public void onFighterDeath() {
        if (this.kind != Kind.HE || this.clone || !this.provoked || this.heTimerStart < 0L) {
            return;
        }
        this.heLimitTicks = Math.max(HE_MIN_LIMIT_TICKS, this.heLimitTicks - HE_DEATH_REDUCTION_TICKS);
        this.heTimerStart = this.level().getGameTime();   // 计时重新回到（新的）上限
        this.killableAnnounced = false;
        this.announce("divinebeast.msg.boss.he_allow", this.heLimitTicks / 20);
    }

    /** 兜底：即使死亡事件没被取消，boss 也不会真的消失（分身除外）。 */
    @Override
    public void die(DamageSource source) {
        if (this.clone) {
            this.bossBar.removeAllPlayers();
            super.die(source);
            return;
        }
        this.onDefeated(null);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.bossBar.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("DivineHomeSet", this.homeSet);
        tag.putDouble("DivineHomeX", this.homeX);
        tag.putDouble("DivineHomeY", this.homeY);
        tag.putDouble("DivineHomeZ", this.homeZ);
        tag.putBoolean("DivineProvoked", this.provoked);
        tag.putInt("DivineAwakenings", this.awakenings);
        tag.putInt("DivinePhase", this.phase);
        tag.putBoolean("DivineClone", this.clone);
        tag.putInt("DivineCloneLife", this.cloneLife);
        tag.putInt("DivineLives", this.livesLeft);
        tag.putFloat("DivineDrain", this.beastDrainRemaining);
        tag.putLong("DivineHeTimer", this.heTimerStart);
        tag.putInt("DivineHeLimit", this.heLimitTicks);
        if (this.ownerUuid != null) {
            tag.putUUID("DivineOwner", this.ownerUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("DivineHomeSet")) {
            this.homeSet = tag.getBoolean("DivineHomeSet");
            this.homeX = tag.getDouble("DivineHomeX");
            this.homeY = tag.getDouble("DivineHomeY");
            this.homeZ = tag.getDouble("DivineHomeZ");
        }
        this.provoked = tag.getBoolean("DivineProvoked");
        this.awakenings = Math.min(AWAKEN_MAX_STACKS, tag.getInt("DivineAwakenings"));
        this.phase = Math.max(1, Math.min(3, tag.getInt("DivinePhase")));
        this.clone = tag.getBoolean("DivineClone");
        this.cloneLife = tag.getInt("DivineCloneLife");
        this.livesLeft = tag.contains("DivineLives") ? tag.getInt("DivineLives") : SELF_LIVES;
        if (tag.contains("DivineDrain")) {
            this.beastDrainRemaining = tag.getFloat("DivineDrain");
        }
        if (tag.contains("DivineHeTimer")) {
            this.heTimerStart = tag.getLong("DivineHeTimer");
        }
        if (tag.contains("DivineHeLimit")) {
            this.heLimitTicks = Math.max(HE_MIN_LIMIT_TICKS, tag.getInt("DivineHeLimit"));
        }
        if (tag.hasUUID("DivineOwner")) {
            this.ownerUuid = tag.getUUID("DivineOwner");
        }
        this.applyAwakening();
        this.applyPhase();
    }

    /** 展示给能看到它的玩家（分身没有血条）。 */
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!this.clone) {
            this.bossBar.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossBar.removePlayer(player);
    }

    // ==================================================================
    // 交易 / 喂食
    // ==================================================================

    public boolean canTrade() {
        return !this.clone && this.level().getGameTime() - this.lastTradeTick >= TRADE_COOLDOWN_TICKS;
    }

    public void markTraded() {
        this.lastTradeTick = this.level().getGameTime();
    }

    public boolean canFeed() {
        return !this.clone && this.level().getGameTime() - this.lastFeedTick >= BEAST_FEED_COOLDOWN_TICKS;
    }

    public void markFed() {
        this.lastFeedTick = this.level().getGameTime();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // 空手右键 → 打开交易界面；手持货币 → 直接即时交易；战斗中拿熟猪排右键『兽』→ 喂食
        if (player.getItemInHand(hand).isEmpty() && !this.clone && player instanceof ServerPlayer serverPlayer) {
            com.divinebeast.divinebeast.net.Networking.sendToPlayer(serverPlayer,
                    new com.divinebeast.divinebeast.net.OpenBossTradeMessage(this.getId()));
            return InteractionResult.CONSUME;
        }
        return BossTrade.handle(this, player, hand);
    }

    // ==================================================================
    // 杂项
    // ==================================================================

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }
}
