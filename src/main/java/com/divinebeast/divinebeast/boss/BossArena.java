package com.divinebeast.divinebeast.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * 三个 boss 的选址、清场铺地与召唤。
 *
 * <p><b>规则（用户需求）</b>
 * <ul>
 *   <li>每个 boss 只在自己那个维度出现一次：主世界『我』/ 地狱『兽』/ 末地『祂』；</li>
 *   <li>选址在距世界中心 <b>1000~1400 格</b>的随机方向上（保证"离中心 1000 格外"）；</li>
 *   <li>地面：以它脚下为中心、<b>半径 100 格</b>的圆盘铺上各自方块（泥土 / 下界岩 / 末地石），
 *       并把该圆盘上方 <b>10 格高</b>的范围全部清空成空气；</li>
 *   <li>施工是<b>分 tick 推进</b>的：玩家进入该维度并靠近到 {@link #TRIGGER_DISTANCE} 格时开始，
 *       先按区块逐步生成/加载地形（1 区块/tick），再按列清场铺地，最后召唤 boss。
 *       触发距离刻意放在玩家视距之外，玩家看不到"方块凭空消失"。</li>
 * </ul>
 *
 * <p>所有破坏性操作只做一次（{@link BossArenaData#isSpawned()}），施工过程本身是幂等的：
 * 中途关服后重进会从头再铺一遍，结果一致。
 */
public final class BossArena {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final int MIN_DISTANCE = 1000;
    private static final int MAX_DISTANCE = 1400;
    /** 玩家水平距离小于该值时开始施工（约 19 个区块，正常视距之外） */
    private static final double TRIGGER_DISTANCE = 300.0D;
    /** 场地半径（格）—— 竞技场维护（清理敌对生物）也用这个值 */
    public static final int RADIUS = 100;
    /** 需要清空的高度（格） */
    private static final int CLEAR_HEIGHT = 10;
    /** 施工第一阶段：每 tick 生成/加载的区块数 */
    private static final int CHUNKS_PER_TICK = 1;
    /** 施工第二阶段：每 tick 处理的列数（每列 11 个方块） */
    private static final int COLUMNS_PER_TICK = 384;

    /** 每个维度一份的进行中任务（不持久化：丢了下个 tick 会从头重建，结果幂等） */
    private static final Map<ResourceKey<Level>, Job> JOBS = new HashMap<>();

    private BossArena() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(BossArena::onLevelTick);
        // 击杀不致死：取消死亡事件后交给 boss 自己结算（续战 / 结束战斗）
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, BossArena::onBossDeath);
        // 与祂战斗时玩家死亡 → 祂的时限上限 -10 秒，计时重新开始
        MinecraftForge.EVENT_BUS.addListener(BossArena::onPlayerDeath);
    }

    /**
     * 玩家在与『祂』战斗期间死亡：祂的计时<b>重新回到上限，但上限本身减少 10 秒</b>（最低 10 秒）。
     * 取玩家周围 128 格内的『祂』（已开战、还没进入真死状态的那些）。
     */
    private static void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)
                || player.level().isClientSide) {
            return;
        }
        for (DivineBoss boss : player.level().getEntitiesOfClass(DivineBoss.class,
                player.getBoundingBox().inflate(128.0D))) {
            if (boss.kind() == DivineBoss.Kind.HE && boss.isProvoked()) {
                boss.onFighterDeath();
            }
        }
    }

    /**
     * 死亡事件的统一入口：非分身的 boss 每一次"倒下"都交给 {@link DivineBoss#onDefeated} 结算 ——
     * <ul>
     *   <li>还有命 / 还没到时限 → <b>取消</b>这次死亡，原地满血继续战斗；</li>
     *   <li>这场战斗结束 → <b>放行</b>这次死亡（会播死亡动画、实体被移除），
     *       并在 {@link DivineBoss#RESPAWN_DELAY_TICKS}（10 秒）后由 {@link #respawnBoss} 原位重生。</li>
     * </ul>
     *
     * <p>放在 LOWEST（最后执行），确保别的模组没法在我们取消之后再把它改回来。
     */
    private static void onBossDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof DivineBoss boss) || boss.level().isClientSide) {
            return;
        }
        if (boss.isClone()) {
            return;   // 『我』的分身该死就死（正常死亡流程）
        }
        net.minecraft.world.entity.Entity killer = event.getSource().getEntity();
        // 返回 true = 这场战斗结束：放行这次死亡（播死亡动画、实体被移除），
        //             onDefeated 里已经登记了"10 秒后原位重新凝聚"
        // 返回 false = 还有命 / 还没到时限：取消死亡，原地满血继续战斗
        if (!boss.onDefeated(killer == null ? null : killer.getDisplayName())) {
            event.setCanceled(true);
        }
    }

    /** 该维度对应哪个 boss；不是这三个维度则返回 null。 */
    public static DivineBoss.Kind kindFor(ResourceKey<Level> dimension) {
        if (Level.OVERWORLD.equals(dimension)) {
            return DivineBoss.Kind.SELF;
        }
        if (Level.NETHER.equals(dimension)) {
            return DivineBoss.Kind.BEAST;
        }
        if (Level.END.equals(dimension)) {
            return DivineBoss.Kind.HE;
        }
        return null;
    }

    private static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        DivineBoss.Kind kind = kindFor(level.dimension());
        if (kind == null) {
            return;
        }
        BossArenaData data = BossArenaData.of(level);
        // 被击败后 10 秒：在原位重新凝聚（全新的实体、无仇恨、静止）
        if (data.respawnAt() > 0L) {
            if (level.getGameTime() >= data.respawnAt()) {
                respawnBoss(level, kind, data);
            }
            return;
        }
        if (data.isSpawned()) {
            return;   // 已经召唤过了
        }
        if (!data.isChosen()) {
            chooseSite(level, data);
            return;
        }
        if (!anyPlayerNear(level, data)) {
            return;
        }
        Job job = JOBS.computeIfAbsent(level.dimension(), key -> new Job());
        try {
            advance(level, kind, data, job);
        } catch (Throwable t) {
            LOGGER.error("[divinebeast] boss 竞技场施工异常", t);
            JOBS.remove(level.dimension());
        }
    }

    /** 在距中心 1000~1400 格的随机方向上选址（只记 x/z，y 等施工时按当地地形决定）。 */
    private static void chooseSite(ServerLevel level, BossArenaData data) {
        double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
        double distance = MIN_DISTANCE + level.getRandom().nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        int x = (int) Math.round(Math.cos(angle) * distance);
        int z = (int) Math.round(Math.sin(angle) * distance);
        data.choose(x, z);
        LOGGER.info("[divinebeast] {} 的 boss 竞技场选址: ({}, {})", level.dimension().location(), x, z);
    }

    private static boolean anyPlayerNear(ServerLevel level, BossArenaData data) {
        double limit = TRIGGER_DISTANCE * TRIGGER_DISTANCE;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - (data.siteX() + 0.5D);
            double dz = player.getZ() - (data.siteZ() + 0.5D);
            if (dx * dx + dz * dz <= limit) {
                return true;
            }
        }
        return false;
    }

    private static void advance(ServerLevel level, DivineBoss.Kind kind, BossArenaData data, Job job) {
        // ① 先确定台面高度：加载中心区块，从当地地表往下找一块实心方块
        if (job.y0 == Integer.MIN_VALUE) {
            level.getChunk(data.siteX() >> 4, data.siteZ() >> 4);
            job.y0 = findGround(level, data.siteX(), data.siteZ());
            LOGGER.info("[divinebeast] {} 竞技场台面高度 y={}", level.dimension().location(), job.y0);
        }
        // ② 分区块逐步生成/加载（1 区块/tick，避免一次性生成上百个区块把服务器卡死）
        if (!job.chunksLoaded) {
            if (job.chunkWidth == 0) {
                int radiusChunks = RADIUS / 16 + 2;
                int centerCx = data.siteX() >> 4;
                int centerCz = data.siteZ() >> 4;
                job.minCx = centerCx - radiusChunks;
                job.minCz = centerCz - radiusChunks;
                job.maxCx = centerCx + radiusChunks;
                job.maxCz = centerCz + radiusChunks;
                job.chunkWidth = job.maxCx - job.minCx + 1;
                job.chunkTotal = job.chunkWidth * (job.maxCz - job.minCz + 1);
            }
            int budget = CHUNKS_PER_TICK;
            while (budget-- > 0 && job.chunkCursor < job.chunkTotal) {
                int cx = job.minCx + job.chunkCursor % job.chunkWidth;
                int cz = job.minCz + job.chunkCursor / job.chunkWidth;
                level.getChunk(cx, cz);
                job.chunkCursor++;
            }
            if (job.chunkCursor >= job.chunkTotal) {
                job.chunksLoaded = true;
                LOGGER.info("[divinebeast] {} 竞技场区块已就绪（{} 个），开始清场铺地",
                        level.dimension().location(), job.chunkTotal);
            }
            return;
        }
        // ③ 按列清场 + 铺地
        if (job.columnsX == null) {
            job.centerX = data.siteX();
            job.centerZ = data.siteZ();
            buildColumns(job);
        }
        BlockState floor = kind.floor().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int budget = COLUMNS_PER_TICK;
        while (budget-- > 0 && job.cursor < job.columnsX.length) {
            int x = job.columnsX[job.cursor];
            int z = job.columnsZ[job.cursor];
            job.cursor++;
            level.setBlock(new BlockPos(x, job.y0, z), floor, 2);
            for (int y = job.y0 + 1; y <= job.y0 + CLEAR_HEIGHT; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, air, 2);
                }
            }
        }
        // ④ 完工：先在边缘立一圈矮墙，再召唤 boss
        if (job.cursor >= job.columnsX.length) {
            if (!job.wallDone) {
                buildWall(level, job, kind);
                job.wallDone = true;
            }
            spawnBoss(level, kind, data, job);
        }
    }

    /**
     * 在半径 {@link #RADIUS} 格的边缘立一圈 <b>1 格高</b>的同名方块矮墙（竞技场边界）。
     * 每 0.25° 放一块，保证圆周上没有缝隙。
     */
    private static void buildWall(ServerLevel level, Job job, DivineBoss.Kind kind) {
        BlockState wall = kind.floor().defaultBlockState();
        int y = job.y0 + 1;
        for (double deg = 0.0D; deg < 360.0D; deg += 0.25D) {
            double rad = Math.toRadians(deg);
            int x = job.centerX + (int) Math.round(Math.cos(rad) * RADIUS);
            int z = job.centerZ + (int) Math.round(Math.sin(rad) * RADIUS);
            level.setBlock(new BlockPos(x, y, z), wall, 2);
            level.setBlock(new BlockPos(x, job.y0, z), wall, 2);
        }
    }

    /** 预计算圆盘内的所有列（半径 100 → 约 3.1 万列）。 */
    private static void buildColumns(Job job) {
        int size = RADIUS * 2 + 1;
        int[] xs = new int[size * size];
        int[] zs = new int[size * size];
        int count = 0;
        int radiusSq = RADIUS * RADIUS;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }
                xs[count] = job.centerX + dx;
                zs[count] = job.centerZ + dz;
                count++;
            }
        }
        job.columnsX = java.util.Arrays.copyOf(xs, count);
        job.columnsZ = java.util.Arrays.copyOf(zs, count);
    }

    private static void spawnBoss(ServerLevel level, DivineBoss.Kind kind, BossArenaData data, Job job) {
        EntityType<? extends DivineBoss> type = switch (kind) {
            case SELF -> ModEntities.SELF_BOSS.get();
            case BEAST -> ModEntities.BEAST_BOSS.get();
            case HE -> ModEntities.HE_BOSS.get();
        };
        DivineBoss boss = type.create(level);
        if (boss == null) {
            return;   // 创建失败：不写 spawned，下个 tick 再试
        }
        boss.moveTo(data.siteX() + 0.5D, job.y0 + 1.0D, data.siteZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        // 记录重生点：被"击杀"后会回到这里
        boss.setHome(data.siteX() + 0.5D, job.y0 + 1.0D, data.siteZ() + 0.5D);
        data.setArenaY(job.y0 + 1.0D);
        level.addFreshEntity(boss);
        data.setSpawned(true);
        JOBS.remove(level.dimension());
        for (ServerPlayer player : level.players()) {
            // 顺带报坐标：否则 1000 格外的竞技场找起来太麻烦
            player.sendSystemMessage(Component.translatable("divinebeast.msg.boss.arena",
                    boss.getDisplayName(), data.siteX() + 0.5D, data.siteZ() + 0.5D));
        }
        LOGGER.info("[divinebeast] {} 已在中立 boss 竞技场 ({}, {}, {}) 降临",
                boss.getDisplayName().getString(), data.siteX(), job.y0 + 1, data.siteZ());
    }

    /**
     * 被击败 {@link DivineBoss#RESPAWN_DELAY_TICKS} tick（10 秒）后：在原地重新凝聚一个<b>全新的</b>
     * boss（无仇恨、静止，觉醒层数沿用存档，所以越打越强）。
     */
    private static void respawnBoss(ServerLevel level, DivineBoss.Kind kind, BossArenaData data) {
        EntityType<? extends DivineBoss> type = switch (kind) {
            case SELF -> ModEntities.SELF_BOSS.get();
            case BEAST -> ModEntities.BEAST_BOSS.get();
            case HE -> ModEntities.HE_BOSS.get();
        };
        DivineBoss boss = type.create(level);
        if (boss == null) {
            return;   // 创建失败：下个 tick 再试
        }
        double x = data.siteX() + 0.5D;
        double y = Double.isNaN(data.arenaY()) ? level.getSeaLevel() + 1.0D : data.arenaY();
        double z = data.siteZ() + 0.5D;
        boss.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        boss.setHome(x, y, z);
        boss.restoreAwakenings(data.awakenings());
        level.addFreshEntity(boss);
        data.setRespawnAt(-1L);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.translatable("divinebeast.msg.boss.reborn",
                    boss.getDisplayName()));
        }
        LOGGER.info("[divinebeast] {} 已在竞技场原位重新凝聚（觉醒 {} 层）",
                boss.getDisplayName().getString(), data.awakenings());
    }

    /**
     * 找台面高度：从给定起点往下找第一块"非空气、非流体、非基岩"的方块。
     *
     * <p>主世界用 WORLD_SURFACE 高度（并抬到海平面之上，免得造出"水下平台"）；
     * 地狱/末地从 y=100 往下找 —— 地狱这样才不会选到 127 层的基岩天花板，
     * 末地外岛的地表也正好在 100 以下。
     */
    private static int findGround(ServerLevel level, int x, int z) {
        boolean overworld = Level.OVERWORLD.equals(level.dimension());
        int startY;
        if (overworld) {
            startY = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z),
                    level.getMaxBuildHeight() - 2);
        } else {
            startY = 100;
        }
        int found = Math.max(level.getMinBuildHeight() + 2, startY);
        for (int y = startY; y > level.getMinBuildHeight() + 1; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (!state.isAir() && state.getFluidState().isEmpty() && !state.is(Blocks.BEDROCK)) {
                found = y;
                break;
            }
        }
        // 主世界落在海洋/河流里时抬到海平面之上，免得整座竞技场泡在水下
        return overworld ? Math.max(found, level.getSeaLevel() + 1) : found;
    }

    /** 单个维度的施工进度（内存态）。 */
    private static final class Job {
        int y0 = Integer.MIN_VALUE;
        boolean chunksLoaded;
        int chunkCursor;
        int chunkWidth;
        int chunkTotal;
        int minCx;
        int minCz;
        int maxCx;
        int maxCz;
        int centerX;
        int centerZ;
        int[] columnsX;
        int[] columnsZ;
        int cursor;
        boolean wallDone;

        Job() {
        }
    }
}
