package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.CompatChecks;
import com.divinebeast.divinebeast.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 脑后"形态圆环"纯客户端装饰 —— 第二轮（Z 键本地开关，默认开）。
 *
 * <p>本轮落地两项：
 * <ul>
 *   <li><b>形态表补全（含「自·我」）</b>：衪诅咒 → 衪救赎（拯救）、
 *       兽诅咒 → 兽拯救 → 自·我、祂者初 → 祂者极 → 真者祂。每种形态拥有
 *       一个专属圆环，颜色先用对应名字风格的占位色（待你提供参考图后统一替换）。</li>
 *   <li><b>修复"看不到环"</b>：上一版用 {@code catch(Throwable ignored)} 吞掉所有
 *       渲染异常导致无声无环；本版只对本地玩家渲染（多人下他人 Curios 数据不可靠），
 *       每玩家独立 try、异常改为限频日志；圆环叠加多层细线并外扩一圈以保证可见。</li>
 * </ul>
 *
 * <p>圆环平面始终竖直（垂直于地面），法向取玩家水平朝向；圆心放在脑后。
 * 当前玩家可能同时满足多个体系形态（衪系 + 兽系），每个形态各画一环、半径逐层外扩。
 *
 * <p>环参照"诡厄巫法：终末之环"的表现（参考图）：竖直大圆环为中心，
 * 环心有一枚发光光球，竖直平面内向外放射光芒线。全程用线条建模、无粒子。
 * 形态颜色暂时为占位色，后续按"名字颜色"替换。
 *
 * <p>纯客户端渲染、无网络同步；Curios 调用全部以全限定名写在 curiosLoaded
 * 守卫内，未装 Curios 时类可安全加载并空转。
 */
public final class OrbitAuraClient {

    private static final Logger LOGGER = LogManager.getLogger();

    /** Z 键本地开关状态（默认开）。 */
    private static boolean visible = true;

    /** 圆心相对头部中心向后（脑后方向）的偏移（格）。 */
    private static final double CENTER_BACK = 0.35D;
    /** 最内圈圆环半径（格）。 */
    private static final double RING_BASE = 0.9D;
    /** 多形态共存时每层环的半径增量（格）。 */
    private static final double RING_STEP = 0.16D;
    /** 每圈叠加的细线层数（半径微偏移三线束，模拟更醒目的粗环）。 */
    private static final int LINE_PASSES = 3;
    /** 三线束相邻半径间距（格）。 */
    private static final double LINE_SPREAD = 0.02D;
    /** 圆环分段数（越大越圆）。 */
    private static final int RING_SEGMENTS = 96;
    /** 圆环中心高度（格，头部中心）。 */
    private static final double HEAD_CENTER_Y = 1.42D;

    // ---- 终末之环式样（参考图：主环 + 中心光球 + 放射光芒线，全部线条建模、无粒子） ----
    /** 中心光球半径占该环半径的比例。 */
    private static final double CORE_FRAC = 0.16D;
    /** 中心光球同心光环数（内白外色，模拟发光光晕）。 */
    private static final int CORE_RINGS = 3;
    /** 放射光芒线数量（竖直环平面内，0=正上方，8 条含上下左右+斜角）。 */
    private static final int RAYS = 8;
    /** 光芒线向外延伸长度系数（相对环半径）。 */
    private static final double SPIKE_SCALE = 1.45D;
    /** 光芒线内侧段（核缘→环）透明度。 */
    private static final float SPIKE_INNER_ALPHA = 0.85F;
    /** 光芒线外侧段（环→外伸）透明度。 */
    private static final float SPIKE_OUTER_ALPHA = 0.5F;
    /** 只对本地玩家渲染（他人 Curios 数据客户端不可靠）。 */
    private static final boolean ONLY_LOCAL = true;
    /** 异常日志限频间隔（tick）。 */
    private static final int LOG_EVERY_TICKS = 200;

    private static long lastErrorTick = Long.MIN_VALUE;
    private static long lastDebugTick = Long.MIN_VALUE;

    private OrbitAuraClient() {
    }

    public static boolean isVisible() {
        return visible;
    }

    public static void toggle() {
        visible = !visible;
    }

    /** 客户端初始化：注册渲染事件。任意客户端都执行，Curios 缺失时内部空转。 */
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(OrbitAuraClient::onRenderLevelStage);
    }

    // ==================================================================
    // 形态
    // ==================================================================

    /** 玩家可处于的八种形态（自·我 = 兽七法则齐，用户要求补上）。 */
    private enum Form {
        DEITY_CURSE("衪诅咒", 1.0F, 0.25F, 0.1F),
        DEITY_REDEEM("衪救赎", 1.0F, 0.8F, 0.1F),
        BEAST_CURSE("兽诅咒", 0.4F, 0.2F, 0.9F),
        BEAST_SALVATION("兽拯救", 0.95F, 0.1F, 0.95F),
        BEAST_SELF("自·我", 1.0F, 0.85F, 0.0F),
        HE_FIRST("祂者初", 0.6F, 0.9F, 1.0F),
        HE_EXTREME("祂者极", 0.3F, 0.6F, 1.0F),
        HE_TRUE("真者祂", 1.0F, 1.0F, 1.0F);

        final String display;
        final float r, g, b;

        Form(String display, float r, float g, float b) {
            this.display = display;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    /** 某玩家 Curios 中是否佩戴指定物品（返回其中一件的副本）。 */
    private static ItemStack findWorn(Player player, Item item) {
        if (!CompatChecks.curiosLoaded()) {
            return ItemStack.EMPTY;
        }
        try {
            java.util.Optional<top.theillusivec4.curios.api.type.capability.ICuriosItemHandler> optional =
                    top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
            if (optional.isPresent()) {
                for (top.theillusivec4.curios.api.SlotResult result : optional.get().findCurios(item)) {
                    ItemStack stack = result.stack();
                    if (stack != null && !stack.isEmpty()) {
                        return stack.copy();
                    }
                }
            }
        } catch (Throwable t) {
            logThrottledError("查询 Curios 佩戴失败 (item=" + item + ")", t);
        }
        return ItemStack.EMPTY;
    }

    private static boolean has(Player p, Item item) {
        return !findWorn(p, item).isEmpty();
    }

    /** 当前玩家的全部"已达成形态"（可同时含衪系/兽系/证悟系各一）。 */
    private static List<Form> detectForms(Player player) {
        List<Form> forms = new ArrayList<>();
        if (!CompatChecks.curiosLoaded()) {
            return forms;
        }
        try {
            boolean deity = has(player, ModItems.DEITY.get());
            boolean beast = has(player, ModItems.BEAST.get());

            // 时刻数量（衪系）
            int moments = 0;
            for (net.minecraftforge.registries.RegistryObject<Item> reg : ModItems.MOMENT_ITEMS) {
                if (has(player, reg.get())) {
                    moments++;
                }
            }
            // 法则数量 + 是否有自我（兽系；拯救=除自我外六件，自·我=七件含自我）
            int laws = 0;
            boolean hasSelf = false;
            for (net.minecraftforge.registries.RegistryObject<Item> reg : List.of(
                    ModItems.SUPREME, ModItems.WISDOM, ModItems.LIFE, ModItems.CHAOS,
                    ModItems.SELF, ModItems.DEVOUR, ModItems.SAMSARA)) {
                if (has(player, reg.get())) {
                    laws++;
                    if (reg.get() == ModItems.SELF.get()) {
                        hasSelf = true;
                    }
                }
            }

            // 证悟系（优先度最高：同时达成衪救赎+兽自·我后获得祂者初，随后进阶）
            if (has(player, ModItems.HE_TRUE.get())) {
                forms.add(Form.HE_TRUE);
                return forms;
            }
            if (has(player, ModItems.HE_EXTREME.get())) {
                forms.add(Form.HE_EXTREME);
                return forms;
            }
            if (has(player, ModItems.HE_FIRST.get())) {
                forms.add(Form.HE_FIRST);
                return forms;
            }

            // 衪系：救赎 = 佩戴祂且五时刻齐
            if (deity) {
                if (moments >= 5) {
                    forms.add(Form.DEITY_REDEEM);
                } else {
                    forms.add(Form.DEITY_CURSE);
                }
            }
            // 兽系：自·我(七法则) / 拯救(六法则无自我) / 诅咒
            if (beast) {
                if (laws >= 7) {
                    forms.add(Form.BEAST_SELF);
                } else if (laws >= 6 && !hasSelf) {
                    forms.add(Form.BEAST_SALVATION);
                } else {
                    forms.add(Form.BEAST_CURSE);
                }
            }
            return forms;
        } catch (Throwable t) {
            logThrottledError("形态检测异常", t);
            return forms;
        }
    }

    // ==================================================================
    // 圆环几何：竖直（垂直于地面），法向 = 玩家水平朝向
    // ==================================================================

    private static Vec3 headCenter(Player p, float partial) {
        double hx = p.xo + (p.getX() - p.xo) * partial;
        double hy = p.yo + (p.getY() - p.yo) * partial + HEAD_CENTER_Y;
        double hz = p.zo + (p.getZ() - p.zo) * partial;
        return new Vec3(hx, hy, hz);
    }

    private static Vec3 horizontalFacing(Player p) {
        double yaw = Math.toRadians(p.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw)).normalize();
    }

    private static Vec3[] ringBasis(Vec3 facing) {
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = facing.cross(up);
        if (right.lengthSqr() < 1.0E-8D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        return new Vec3[]{up, right};
    }

    // ==================================================================
    // 每帧：世界空间圆环
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
        if (!(mc.level instanceof ClientLevel)) {
            return;
        }
        ClientLevel level = (ClientLevel) mc.level;
        Player me = mc.player;

        try {
            long gameTime = level.getGameTime();
            if (ONLY_LOCAL) {
                renderOne(event, me, level, gameTime);
            } else {
                for (Player target : level.players()) {
                    if (target.isAlive()) {
                        renderOne(event, target, level, gameTime);
                    }
                }
            }
        } catch (Throwable t) {
            logThrottledError("渲染圆环异常", t);
        }
    }

    private static void renderOne(RenderLevelStageEvent event, Player target,
                                  ClientLevel level, long gameTime) {
        List<Form> forms = detectForms(target);
        if (forms.isEmpty()) {
            return;
        }
        if (gameTime - lastDebugTick > LOG_EVERY_TICKS) {
            lastDebugTick = gameTime;
            StringBuilder sb = new StringBuilder();
            for (Form f : forms) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(f.display);
            }
            LOGGER.debug("检测到 {} 形态: {}", target.getName().getString(), sb);
        }

        Vec3 cam = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        Vec3 head = headCenter(target, partialTick);
        Vec3 facing = horizontalFacing(target);
        Vec3 center = head.add(facing.scale(-CENTER_BACK));

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        try {
            int formIndex = 0;
            for (Form form : forms) {
                try {
                    double radius = RING_BASE + formIndex * RING_STEP;
                    renderRing(pose, buffers, cam, center, facing, radius, form);
                    formIndex++;
                } catch (Throwable t) {
                    logThrottledError("渲染 " + form.display + " 圆环异常", t);
                }
            }
            buffers.endBatch();
        } finally {
            pose.popPose();
        }
    }

    /**
     * 终末之环式样：竖直大圆环（垂直于地面）+ 中心发光光球 + 放射光芒线。
     * 全部用 {@code RenderType.lines()} 线条建模，不使用粒子。
     */
    private static void renderRing(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                   Vec3 cam, Vec3 center, Vec3 facing,
                                   double radius, Form form) {
        Vec3[] basis = ringBasis(facing);
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);

        // 1) 主环：三线束（中心亮线 + 两侧稍淡线），半径微偏移 → 视觉成粗壮发光环
        for (int pass = 0; pass < LINE_PASSES; pass++) {
            float a = pass == 1 ? 1.0F : 0.45F;
            double r = radius + (pass - 1) * LINE_SPREAD;
            drawCircle(consumer, pose, basis, r, form.r, form.g, form.b, a);
        }

        // 2) 中心光球：同心光环，内圈偏白（发光核心）、外圈渐变为形态色
        double core = radius * CORE_FRAC;
        for (int pass = 0; pass < CORE_RINGS; pass++) {
            double r = core * (pass + 1.0D) / CORE_RINGS;
            float a = 1.0F - pass * 0.26F;          // 越外越淡
            float m = 1.0F - pass * 0.34F;          // 混合白度的权重：内白外色
            float cr = blend(form.r, m);
            float cg = blend(form.g, m);
            float cb = blend(form.b, m);
            drawCircle(consumer, pose, basis, r, cr, cg, cb, a);
        }

        // 3) 放射光芒线：竖直环平面内从核缘向外放射（0=正上，8 条含斜角）
        for (int ray = 0; ray < RAYS; ray++) {
            double ang = (Math.PI * 2.0D * ray) / RAYS;
            double rIn = core * 0.6D;                    // 核缘内侧起点
            double rOut = radius * SPIKE_SCALE;          // 外伸终点（越过环）
            Vec3 p0 = ringPoint(basis, rIn, ang);
            Vec3 p1 = ringPoint(basis, radius, ang);
            Vec3 p2 = ringPoint(basis, rOut, ang);
            // 内段（核缘→环）：亮、偏白
            float mi = 0.35F;
            line(consumer, pose, p0, p1, blend(form.r, mi), blend(form.g, mi), blend(form.b, mi), SPIKE_INNER_ALPHA);
            // 外段（环→外伸）：形态色、稍淡
            line(consumer, pose, p1, p2, form.r, form.g, form.b, SPIKE_OUTER_ALPHA);
        }

        pose.popPose();
    }

    /** 在竖直环平面上画一整圈细线。 */
    private static void drawCircle(VertexConsumer consumer, PoseStack pose, Vec3[] basis,
                                   double radius, float r, float g, float b, float a) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a0 = (Math.PI * 2.0D * i) / RING_SEGMENTS;
            double a1 = (Math.PI * 2.0D * (i + 1)) / RING_SEGMENTS;
            line(consumer, pose, ringPoint(basis, radius, a0), ringPoint(basis, radius, a1), r, g, b, a);
        }
    }

    /** 画一条线（POSITION_COLOR_NORMAL，需带法线）。 */
    private static void line(VertexConsumer consumer, PoseStack pose, Vec3 from, Vec3 to,
                             float r, float g, float b, float a) {
        consumer.vertex(pose.last().pose(), (float) from.x, (float) from.y, (float) from.z)
                .color(r, g, b, a)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
        consumer.vertex(pose.last().pose(), (float) to.x, (float) to.y, (float) to.z)
                .color(r, g, b, a)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    /** 颜色按权重 m 向白色混合（m=0 → 纯色，m=1 → 纯白）。 */
    private static float blend(float channel, float m) {
        return channel + (1.0F - channel) * m;
    }

    private static Vec3 ringPoint(Vec3[] basis, double radius, double angle) {
        return basis[0].scale(Math.cos(angle) * radius)
                .add(basis[1].scale(Math.sin(angle) * radius));
    }

    private static void logThrottledError(String what, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastErrorTick > 5000L) {
            lastErrorTick = now;
            LOGGER.error("{}: {}", what, t.toString());
        }
    }
}
