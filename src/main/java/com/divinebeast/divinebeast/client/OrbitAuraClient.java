package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.CompatChecks;
import com.divinebeast.divinebeast.item.DivineBeastItem;
import com.divinebeast.divinebeast.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 脑后"星环光环"纯客户端装饰（Z 键本地开关，默认开启）。
 *
 * <p>对<b>每个</b>客户端可见、且在 Curios 槽位中佩戴了任意本模组饰品的
 * 玩家，在其脑后绘制：
 * <ul>
 *   <li>头后一个由粒子组成的光球（中心点）；若有"核心物品"（优先级
 *       真者祂 &gt; 祂者极 &gt; 本心 &gt; 救赎 &gt; 祂者初 &gt; 祂/兽）在场，
 *       则用最高优先级那件<b>替换</b>光球并自转；</li>
 *   <li>其余已佩戴饰品成为"卫星"，沿同心轨道环绕中心旋转，一轨可载多件
 *       （同一圈最多 {@link #PER_RING} 件，总共最多展示 {@link #MAX_SATS} 件）；
 *       每圈轨道都撒有一圈粒子圆环；</li>
 *   <li>圆盘平面始终面向本地摄像机（脑后光环视角；第三人称 / F5 看自己最清晰，
 *       多人下别人看你同样可见）。粒子寿命短：静止观看最完整，移动只带轻微拖尾。</li>
 * </ul>
 *
 * <p>全部在本地客户端完成、不做网络同步。本类顶层<b>不</b> import 任何 Curios
 * 类型 —— Curios 调用全部以全限定名写在 curiosLoaded 守卫内，未装 Curios 时
 * 类可安全加载并静默空转（与 HeTrueEffects 同款写法）。
 */
public final class OrbitAuraClient {

    /** Z 键本地开关状态（默认开）。 */
    private static boolean visible = true;

    /** 光环中心相对头部（眼睛高度）向脑后偏移量（格）。 */
    private static final double CENTER_BACK = 0.45D;
    /** 最内圈轨道半径（格）。 */
    private static final double RING_BASE = 0.55D;
    /** 轨道半径递增步长（格）。 */
    private static final double RING_STEP = 0.30D;
    /** 同一圈轨道最多载多少件饰品。 */
    private static final int PER_RING = 3;
    /** 最多展示多少颗"卫星"（防 99 槽囤积时粒子爆炸）。 */
    private static final int MAX_SATS = 12;
    /** 环绕角速度（弧度/tick）。 */
    private static final double ORBIT_SPEED = 0.035D;
    /** 中心核心物品自转速度（度/tick）。 */
    private static final double CORE_SPIN_SPEED = 2.5D;
    /** 卫星自转速度（度/tick）。 */
    private static final double SAT_SPIN_SPEED = 1.4D;
    /** 卫星物品渲染缩放。 */
    private static final float SAT_SCALE = 0.55F;
    /** 中心核心物品渲染缩放。 */
    private static final float CORE_SCALE = 0.9F;
    /** 每圈轨道粒子圆环撒点数量（每 2 tick 撒一整圈）。 */
    private static final int RING_DOTS = 14;
    /** 布局重建间隔（tick）。 */
    private static final int LAYOUT_REFRESH = 5;
    /** 只对距本地玩家这个距离以内的目标渲染/撒粒子。 */
    private static final double MAX_RANGE = 64.0D;
    private static final double MAX_RANGE_SQ = MAX_RANGE * MAX_RANGE;

    private OrbitAuraClient() {
    }

    public static boolean isVisible() {
        return visible;
    }

    public static void toggle() {
        visible = !visible;
    }

    /** 客户端初始化：注册事件。任意客户端都执行，Curios 缺失时内部空转。 */
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(OrbitAuraClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(OrbitAuraClient::onRenderLevelStage);
    }

    // ==================================================================
    // 布局（每个目标玩家各自缓存）
    // ==================================================================

    /** 一颗"卫星"：所在轨道序号 + 轨道内相位。 */
    private static final class Sat {
        final ItemStack stack;
        final int ringIndex;
        final double phase;

        Sat(ItemStack stack, int ringIndex, double phase) {
            this.stack = stack;
            this.ringIndex = ringIndex;
            this.phase = phase;
        }
    }

    /** 光环布局：center 为 null 表示中心使用粒子光球。 */
    private static final class Layout {
        ItemStack center;   // 可为 null
        final List<Sat> sats = new ArrayList<>();
    }

    private static final class Entry {
        Layout layout;
        long lastTime = -1;
    }

    private static final Map<UUID, Entry> LAYOUT_CACHE = new HashMap<>();

    /** 核心物品优先级（数值越小越优先：成为中心 / 更靠近中心）。 */
    private static int coreRank(Item item) {
        if (item == ModItems.HE_TRUE.get()) {
            return 0;
        }
        if (item == ModItems.HE_EXTREME.get()) {
            return 1;
        }
        if (item == ModItems.TRUE_HEART.get()) {
            return 2;
        }
        if (item == ModItems.REDEMPTION.get()) {
            return 3;
        }
        if (item == ModItems.HE_FIRST.get()) {
            return 4;
        }
        if (item == ModItems.DEITY.get()) {
            return 5;
        }
        if (item == ModItems.BEAST.get()) {
            return 6;
        }
        return 100; // 普通饰品
    }

    /**
     * 取回某玩家 Curios 中佩戴的全部本模组饰品（每种物品最多视为一件卫星）。
     * Curios 调用全限定 + curiosLoaded 守卫；失败/缺失时返回空表。
     */
    private static List<ItemStack> collectWorn(Player player) {
        List<ItemStack> out = new ArrayList<>();
        if (!CompatChecks.curiosLoaded()) {
            return out;
        }
        try {
            java.util.Optional<top.theillusivec4.curios.api.type.capability.ICuriosItemHandler> optional =
                    top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
            if (optional.isEmpty()) {
                return out;
            }
            top.theillusivec4.curios.api.type.capability.ICuriosItemHandler handler = optional.get();
            // 与本模组既有代码一致：handler.findCurios(Item)（Curios 5.14.1 存在）
            for (net.minecraftforge.registries.RegistryObject<Item> reg : ModItems.ALL_ITEMS) {
                Item item = reg.get();
                if (item instanceof DivineBeastItem) {
                    for (top.theillusivec4.curios.api.SlotResult result : handler.findCurios(item)) {
                        ItemStack stack = result.stack();
                        if (stack != null && !stack.isEmpty()) {
                            out.add(stack.copy());
                            break; // 该物品每种只算一颗卫星
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Curios 缺失 / 该客户端拿不到该实体数据：按无佩戴处理
        }
        return out;
    }

    /** 由佩戴物品构建光环布局：核心选最高优先级；卫星按重要性由内向外排。 */
    private static Layout buildLayout(List<ItemStack> worn) {
        Layout layout = new Layout();
        if (worn.isEmpty()) {
            return layout;
        }
        // 中心核心：已佩戴核心物品中优先级最高者
        ItemStack bestCore = null;
        int bestRank = Integer.MAX_VALUE;
        for (ItemStack stack : worn) {
            if (stack.getItem() instanceof DivineBeastItem) {
                int rank = coreRank(stack.getItem());
                if (rank < 100 && rank < bestRank) {
                    bestRank = rank;
                    bestCore = stack;
                }
            }
        }
        layout.center = bestCore;
        // 卫星：其余物品（按重要性排序后截断到 MAX_SATS）
        List<ItemStack> sats = new ArrayList<>();
        for (ItemStack stack : worn) {
            if (bestCore != null && ItemStack.isSameItem(bestCore, stack)) {
                continue;
            }
            sats.add(stack);
        }
        sats.sort(Comparator.comparingInt(s -> coreRank(s.getItem())));
        if (sats.size() > MAX_SATS) {
            sats = new ArrayList<>(sats.subList(0, MAX_SATS));
        }
        int ring = 0;
        int countInRing = 0;
        for (ItemStack stack : sats) {
            if (countInRing >= PER_RING) {
                ring++;
                countInRing = 0;
            }
            double phase = (Math.PI * 2.0D * countInRing) / PER_RING;
            layout.sats.add(new Sat(stack, ring, phase));
            countInRing++;
        }
        return layout;
    }

    /** 缓存并按需重建某玩家的布局（每 5 tick 刷新一次）。time = level.getGameTime()。 */
    private static Layout layoutFor(Player player, long time) {
        Entry entry = LAYOUT_CACHE.computeIfAbsent(player.getUUID(), k -> new Entry());
        if (entry.layout == null || time - entry.lastTime >= LAYOUT_REFRESH) {
            entry.lastTime = time;
            entry.layout = buildLayout(collectWorn(player));
        }
        return entry.layout;
    }

    // ==================================================================
    // 几何：圆盘平面（法向 = 摄像机看向头部，即光环面向本地玩家）
    // ==================================================================

    /** 返回 [中心点, 平面基向量 e1, 平面基向量 e2]。 */
    private static Object[] plane(Player target, Vec3 cam, float partialTick) {
        double hx = target.xo + (target.getX() - target.xo) * partialTick;
        double hy = target.yo + (target.getY() - target.yo) * partialTick + 1.42D;
        double hz = target.zo + (target.getZ() - target.zo) * partialTick;
        Vec3 head = new Vec3(hx, hy, hz);
        Vec3 toCam = cam.subtract(head);
        Vec3 normal = toCam.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : toCam.normalize();
        Vec3 e1 = normal.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (e1.lengthSqr() < 1.0E-8D) {
            e1 = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            e1 = e1.normalize();
        }
        Vec3 e2 = normal.cross(e1).normalize();
        Vec3 center = head.add(normal.scale(CENTER_BACK));
        return new Object[]{center, e1, e2};
    }

    private static Vec3 ringPoint(Object[] plane, double radius, double angle) {
        Vec3 center = (Vec3) plane[0];
        Vec3 e1 = (Vec3) plane[1];
        Vec3 e2 = (Vec3) plane[2];
        return center.add(e1.scale(Math.cos(angle) * radius))
                .add(e2.scale(Math.sin(angle) * radius));
    }

    // ==================================================================
    // 每 tick：中心光球粒子 + 每圈轨道粒子圆环
    // ==================================================================

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!visible || mc.level == null || mc.player == null || !(mc.level instanceof ClientLevel)) {
            return;
        }
        ClientLevel level = (ClientLevel) mc.level;
        Player me = mc.player;
        if (!CompatChecks.curiosLoaded()) {
            return;
        }
        try {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            long time = level.getGameTime();
            for (Player target : level.players()) {
                if (!target.isAlive() || me.distanceToSqr(target) > MAX_RANGE_SQ) {
                    continue;
                }
                Layout layout = layoutFor(target, time);
                if (layout.sats.isEmpty() && layout.center == null) {
                    continue;
                }
                Object[] pl = plane(target, cam, 1.0F);
                double tick = time;

                // 中心光球（未被核心物品替换时）
                if (layout.center == null) {
                    Vec3 c = (Vec3) pl[0];
                    for (int i = 0; i < 5; i++) {
                        double rx = (level.random.nextDouble() - 0.5D) * 0.18D;
                        double ry = (level.random.nextDouble() - 0.5D) * 0.18D;
                        double rz = (level.random.nextDouble() - 0.5D) * 0.18D;
                        level.addParticle(ParticleTypes.END_ROD,
                                c.x + rx, c.y + ry, c.z + rz, 0.0D, 0.0D, 0.0D);
                    }
                }
                // 每圈轨道的粒子圆环（每 2 tick 撒一圈；仅有核心而无卫星时也留一圈）
                if (me.tickCount % 2 == 0) {
                    int maxRing = -1;
                    for (Sat sat : layout.sats) {
                        maxRing = Math.max(maxRing, sat.ringIndex);
                    }
                    if (maxRing < 0 && layout.center != null) {
                        maxRing = 0;
                    }
                    for (int ring = 0; ring <= maxRing; ring++) {
                        double radius = RING_BASE + ring * RING_STEP;
                        double orbitAngle = tick * ORBIT_SPEED;
                        for (int d = 0; d < RING_DOTS; d++) {
                            double a = orbitAngle + (Math.PI * 2.0D * d) / RING_DOTS;
                            Vec3 p = ringPoint(pl, radius, a);
                            level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0.0D, 0.0D, 0.0D);
                        }
                    }
                }
            }
            // 清理已不在范围内的缓存
            LAYOUT_CACHE.keySet().removeIf(uuid -> {
                for (Player p : level.players()) {
                    if (p.getUUID().equals(uuid)) {
                        return false;
                    }
                }
                return true;
            });
        } catch (Throwable ignored) {
            // 装饰性粒子，异常不影响游戏
        }
    }

    // ==================================================================
    // 每帧：卫星物品 + 中心核心物品（世界空间渲染）
    // ==================================================================

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!visible || mc.level == null || mc.player == null) {
            return;
        }
        if (!CompatChecks.curiosLoaded()) {
            return;
        }
        ClientLevel level = (ClientLevel) mc.level;
        Player me = mc.player;
        try {
            Vec3 cam = event.getCamera().getPosition();
            float partialTick = event.getPartialTick();
            long time = mc.level.getGameTime();
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            int light = 0xF000F0; // 满亮度（无视昼夜，装饰始终清晰）
            try {
                for (Player target : level.players()) {
                    if (!target.isAlive() || me.distanceToSqr(target) > MAX_RANGE_SQ) {
                        continue;
                    }
                    Layout layout = layoutFor(target, time);
                    if (layout.sats.isEmpty() && layout.center == null) {
                        continue;
                    }
                    double tick = time + partialTick;
                    Object[] pl = plane(target, cam, partialTick);
                    Vec3 center = (Vec3) pl[0];

                    // ---- 中心核心物品：替换粒子光球，自转 ----
                    if (layout.center != null) {
                        pose.pushPose();
                        try {
                            pose.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
                            pose.mulPose(Axis.YP.rotationDegrees((float) (tick * CORE_SPIN_SPEED)));
                            pose.scale(CORE_SCALE, CORE_SCALE, CORE_SCALE);
                            pose.translate(-0.5F, -0.5F, -0.5F); // 让物品中心对准光球位置
                            mc.getItemRenderer().renderStatic(layout.center, ItemDisplayContext.FIXED,
                                    light, OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
                        } finally {
                            pose.popPose();
                        }
                    }
                    // ---- 卫星物品 ----
                    for (Sat sat : layout.sats) {
                        double radius = RING_BASE + sat.ringIndex * RING_STEP;
                        double orbitAngle = tick * ORBIT_SPEED + sat.phase;
                        Vec3 p = ringPoint(pl, radius, orbitAngle);
                        double bob = Math.sin(tick * 0.12D + sat.phase * 3.0D) * 0.05D;
                        pose.pushPose();
                        try {
                            pose.translate(p.x - cam.x, p.y - cam.y + bob, p.z - cam.z);
                            pose.mulPose(Axis.YP.rotationDegrees(
                                    (float) (tick * SAT_SPIN_SPEED + Math.toDegrees(orbitAngle))));
                            pose.scale(SAT_SCALE, SAT_SCALE, SAT_SCALE);
                            pose.translate(-0.5F, -0.5F, -0.5F); // 物品中心对准轨道点
                            mc.getItemRenderer().renderStatic(sat.stack, ItemDisplayContext.FIXED,
                                    light, OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
                        } finally {
                            pose.popPose();
                        }
                    }
                }
                buffers.endBatch();
            } finally {
                pose.popPose();
            }
        } catch (Throwable ignored) {
            // 渲染失败仅跳过该帧装饰，不影响游戏
        }
    }
}
