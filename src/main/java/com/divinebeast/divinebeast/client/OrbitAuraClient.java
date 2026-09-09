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
 * <p>圆环平面始终竖直（垂直于地面），法向取玩家本体水平朝向（yBodyRot，跟随本体转向、
 * 不随视角/镜头旋转）；圆心放在脑后、整体上移 1/4 格。低阶形态（衪/兽）各画一个大环，
 * 半径逐层外扩；证悟系（祂者初/极/真）不覆盖低阶，而是画成等比缩小、更向内、中心在大环
 * 上方 1/4 格的小环，环上的小圆与光线绕中心旋转（数量 = 已装备下级饰品数）。
 *
 * <p>每个环 = 1 条粗线 + 2 条细线，全部用线条建模、无粒子。
 * 形态颜色暂时为占位色，后续按"名字颜色"替换。
 *
 * <p>纯客户端渲染、无网络同步；Curios 调用全部以全限定名写在 curiosLoaded
 * 守卫内，未装 Curios 时类可安全加载并空转。
 *
 * <p><b>坐标与阶段</b>：用 {@code Stage.AFTER_PARTICLES}（相机空间仍有效）渲染世界锚定线条；
 * 不能用 {@code Stage.AFTER_LEVEL}，它触发时 poseStack 已丢失相机平移，会把图形画到错误位置。
 */
public final class OrbitAuraClient {

    private static final Logger LOGGER = LogManager.getLogger();

    /** Z 键本地开关状态（默认开）。 */
    private static boolean visible = true;

    /** 圆心相对头部中心向后（脑后方向）的偏移（格）。 */
    private static final double CENTER_BACK = 0.5D;
    /** 大环中心相对头部中心向上偏移（格）。 */
    private static final double CENTER_UP = 0.25D;
    /** 最内圈大环半径（格）。 */
    private static final double RING_BASE = 0.72D;
    /** 多形态共存时每层大环的半径增量（格）。 */
    private static final double RING_STEP = 0.42D;
    /** 证悟小环半径（相对最小的低阶大环，等比缩小、更小一号）。 */
    private static final double SMALL_RING_RADIUS = 0.38D;
    /** 证悟小环中心相对大环中心向上偏移（格）。 */
    private static final double SMALL_RING_UP = 0.25D;
    /** 证悟小环相对大环更向内（脑后偏移更小，即更靠近本体）。 */
    private static final double SMALL_RING_BACK = 0.18D;
    /** 每圈"粗线"叠加的细线层数（同半径微偏移 → 视觉成粗壮发光环带）。 */
    private static final int THICK_PASSES = 5;
    /** 粗线带相邻半径间距（格，越小越实心）。 */
    private static final double THICK_SPREAD = 0.006D;
    /** 两条细线相对主环半径的偏移（格）。 */
    private static final double THIN_OFFSET = 0.05D;
    /** 圆环分段数（越大越圆）。 */
    private static final int RING_SEGMENTS = 96;
    /** 圆环中心高度（格，头部中心）。 */
    private static final double HEAD_CENTER_Y = 1.42D;
    /** 小圆环绕大圆圆心转动的角速度（弧度/tick）。 */
    private static final double ORBIT_SPEED = 0.12D;
    /** 证悟小环上"小圆"的半径（格）。 */
    private static final double ORBIT_DOT_RADIUS = 0.05D;

    // ---- 中心光球（仅证悟小环）----
    /** 中心光球半径占该环半径的比例。 */
    private static final double CORE_FRAC = 0.16D;
    /** 中心光球同心光环数（内白外色，模拟发光光晕）。 */
    private static final int CORE_RINGS = 3;
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

    /** 玩家可处于的形态（自·我 = 兽七法则齐，用户要求补上）。 */
    private enum Form {
        DEITY_CURSE("衪诅咒", 1.0F, 0.25F, 0.1F, false),
        DEITY_REDEEM("衪救赎", 1.0F, 0.8F, 0.1F, false),
        BEAST_CURSE("兽诅咒", 0.4F, 0.2F, 0.9F, false),
        BEAST_SALVATION("兽拯救", 0.95F, 0.1F, 0.95F, false),
        BEAST_SELF("自·我", 1.0F, 0.85F, 0.0F, false),
        HE_FIRST("祂者初", 0.6F, 0.9F, 1.0F, true),
        HE_EXTREME("祂者极", 0.3F, 0.6F, 1.0F, true),
        HE_TRUE("真者祂", 1.0F, 1.0F, 1.0F, true);

        final String display;
        final float r, g, b;
        /** 证悟系（祂者初/极/真）：等比缩小、叠在低阶环外或内、绕心旋转的小环。 */
        final boolean awakening;

        Form(String display, float r, float g, float b, boolean awakening) {
            this.display = display;
            this.r = r;
            this.g = g;
            this.b = b;
            this.awakening = awakening;
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

    /** 当前玩家的全部"已达成形态"。证悟系（祂者初/极/真）与低阶环共存、不覆盖。 */
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

            // 证悟系：与低阶环共存（不覆盖），先加低阶大环，后加证悟小环
            // 衪系：救赎 = 佩戴祂且五时刻齐
            if (deity) {
                forms.add(moments >= 5 ? Form.DEITY_REDEEM : Form.DEITY_CURSE);
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
            // 证悟低阶大环最小也要小一号（见 renderOne），证悟小环叠于其上
            if (has(player, ModItems.HE_TRUE.get())) {
                forms.add(Form.HE_TRUE);
            } else if (has(player, ModItems.HE_EXTREME.get())) {
                forms.add(Form.HE_EXTREME);
            } else if (has(player, ModItems.HE_FIRST.get())) {
                forms.add(Form.HE_FIRST);
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

    /** 用玩家本体水平朝向（yBodyRot）而非镜头朝向（getYRot）→ 环跟随本体转向、不随视角旋转。 */
    private static Vec3 horizontalFacing(Player p) {
        double yaw = Math.toRadians(p.yBodyRot);
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
        // 必须在相机空间仍然生效的阶段渲染世界锚定几何。
        // AFTER_LEVEL 在 renderLevel 结束后才触发，此时 pose stack 已丢失相机平移，
        // translate(world - cam) 会画到 (world - cam) 的错误位置。故改用 AFTER_PARTICLES。
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
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

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        try {
            int subCount = equippedSubCount(target);
            // 1) 低阶大环：半径逐层外扩，圆心在脑后、上移
            int bigIndex = 0;
            for (Form form : forms) {
                if (form.awakening) {
                    continue; // 证悟系小环单独绘制
                }
                try {
                    double radius = RING_BASE + bigIndex * RING_STEP;
                    Vec3 center = head.add(facing.scale(-CENTER_BACK)).add(0.0D, CENTER_UP, 0.0D);
                    renderRing(pose, buffers, cam, center, facing, radius, form, gameTime, subCount, false);
                    bigIndex++;
                } catch (Throwable t) {
                    logThrottledError("渲染 " + form.display + " 圆环异常", t);
                }
            }
            // 2) 证悟小环：等比更小、平行、更向内、中心在大环上方 1/4 格
            int smallIndex = 0;
            for (Form form : forms) {
                if (!form.awakening) {
                    continue;
                }
                try {
                    double radius = SMALL_RING_RADIUS - smallIndex * RING_STEP * 0.3D;
                    if (radius < 0.12D) {
                        radius = 0.12D;
                    }
                    Vec3 center = head.add(facing.scale(-SMALL_RING_BACK))
                            .add(0.0D, CENTER_UP + SMALL_RING_UP, 0.0D);
                    renderRing(pose, buffers, cam, center, facing, radius, form, gameTime, subCount, true);
                    smallIndex++;
                } catch (Throwable t) {
                    logThrottledError("渲染 " + form.display + " 小环异常", t);
                }
            }
            buffers.endBatch();
        } finally {
            pose.popPose();
        }
    }

    /** 已装备的下级饰品数量（用于计算小圆数量）。 */
    private static int equippedSubCount(Player player) {
        int count = 0;
        if (!CompatChecks.curiosLoaded()) {
            return count;
        }
        try {
            java.util.Optional<top.theillusivec4.curios.api.type.capability.ICuriosItemHandler> optional =
                    top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
            if (optional.isPresent()) {
                for (top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler stacksHandler
                        : optional.get().getCurios().values()) {
                    net.minecraftforge.items.IItemHandlerModifiable stacks = stacksHandler.getStacks();
                    if (stacks == null) {
                        continue;
                    }
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (stack != null && !stack.isEmpty()) {
                            count++;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logThrottledError("统计饰品数量失败", t);
        }
        return count;
    }

    /**
     * 圆环：竖直主环（垂直于地面）。主环 = 1 条粗线（多道紧贴的细线叠加成粗环带）+ 2 条细线。
     * 证悟小环在此基础上额外画「沿环等距、绕玄环中心旋转的小圆与线条」。
     * 全部用 {@code RenderType.lines()} 线条建模，不使用粒子。
     */
    private static void renderRing(PoseStack pose, MultiBufferSource.BufferSource buffers,
                                   Vec3 cam, Vec3 center, Vec3 facing,
                                   double radius, Form form, long gameTime,
                                   int subCount, boolean isSmall) {
        Vec3[] basis = ringBasis(facing);
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);

        // 1) 主环：一条粗线 = 多道紧贴线束（THICK_PASSES 道，半径微偏移 → 视觉粗壮）
        for (int pass = 0; pass < THICK_PASSES; pass++) {
            double r = radius + (pass - (THICK_PASSES - 1) / 2.0D) * THICK_SPREAD;
            float a = 1.0F - Math.abs(pass - (THICK_PASSES - 1) / 2.0D) * 0.12F;
            drawCircle(consumer, pose, basis, r, form.r, form.g, form.b, a);
        }
        // 2) 两条细线：主环两侧各一根细亮线（居中偏白，突出轮廓）
        float thinM = 0.25F;
        drawCircle(consumer, pose, basis, radius + THIN_OFFSET,
                blend(form.r, thinM), blend(form.g, thinM), blend(form.b, thinM), 0.6F);
        drawCircle(consumer, pose, basis, radius - THIN_OFFSET,
                blend(form.r, thinM), blend(form.g, thinM), blend(form.b, thinM), 0.6F);

        // 3) 证悟小环：中心光球 + 绕中心旋转的小圆与光线（数量 = 已装备下级饰品数）
        if (isSmall) {
            double core = radius * CORE_FRAC;
            for (int pass = 0; pass < CORE_RINGS; pass++) {
                double r = core * (pass + 1.0D) / CORE_RINGS;
                float a = 1.0F - pass * 0.26F;
                float m = 1.0F - pass * 0.34F;
                drawCircle(consumer, pose, basis, r, blend(form.r, m), blend(form.g, m), blend(form.b, m), a);
            }
            float orbitM = 0.2F; // 装饰元素略偏白提亮
            float or = blend(form.r, orbitM);
            float og = blend(form.g, orbitM);
            float ob = blend(form.b, orbitM);
            int n = Math.max(1, Math.min(subCount, 12));
            for (int k = 0; k < n; k++) {
                double ang = ORBIT_SPEED * gameTime + (Math.PI * 2.0D * k) / n;
                // 小圆：位于主环上的一个小圆环点
                Vec3 p = ringPoint(basis, radius, ang);
                drawTinyCircle(consumer, pose, basis, p, ORBIT_DOT_RADIUS, or, og, ob, 0.9F);
                // 光线：从环心沿该角度延伸到环上一点（绕心旋转的芒线）
                Vec3 q0 = ringPoint(basis, radius * 0.15D, ang);
                line(consumer, pose, q0, p, or, og, ob, 0.5F);
            }
        }

        pose.popPose();
    }

    /** 以小点 p 为圆心、在竖直环平面上画一个极细小的圆（用于"小圆"装饰）。 */
    private static void drawTinyCircle(VertexConsumer consumer, PoseStack pose, Vec3[] basis,
                                       Vec3 p, double dotRadius, float r, float g, float b, float a) {
        int seg = 8;
        for (int i = 0; i < seg; i++) {
            double a0 = (Math.PI * 2.0D * i) / seg;
            double a1 = (Math.PI * 2.0D * (i + 1)) / seg;
            Vec3 c0 = p.add(basis[0].scale(Math.cos(a0) * dotRadius))
                    .add(basis[1].scale(Math.sin(a0) * dotRadius));
            Vec3 c1 = p.add(basis[0].scale(Math.cos(a1) * dotRadius))
                    .add(basis[1].scale(Math.sin(a1) * dotRadius));
            line(consumer, pose, c0, c1, r, g, b, a);
        }
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
