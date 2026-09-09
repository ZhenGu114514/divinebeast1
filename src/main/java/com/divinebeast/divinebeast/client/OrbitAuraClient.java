package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.CompatChecks;
import com.divinebeast.divinebeast.item.DivineBeastItem;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 脑后"光环圆环"纯客户端装饰 —— 预览版（Z 键本地开关，默认开）。
 *
 * <p>当前只做第一阶段立绘预览，供确认观感，暂不实现颜色/七形态/下级小圆：
 * <ul>
 *   <li>已<b>去除</b>：卫星物品漂浮、中心核心物品漂浮、粒子圆环/粒子光球；</li>
 *   <li>改为<b>建模渲染</b>：对每个可见且佩戴任意本模组饰品的玩家，在脑后绘制
 *       一圈<b>垂直于地面的细线发光圆环</b>（不再使用粒子）。</li>
 * </ul>
 *
 * <p>圆环平面始终竖直（垂直于地面），法向取玩家水平朝向，随玩家转身；
 * 站在玩家正前/正后方可看到整圆，侧面为竖直线（后续“小圆”会让侧面也有内容）。
 * 圆心默认放在头部中心略向后，半径/颜色等参数集中在下方常量便于调优。
 *
 * <p>纯客户端渲染、无网络同步；Curios 调用全部以全限定名写在 curiosLoaded
 * 守卫内（与 HeTrueEffects 同款写法），未装 Curios 时类可安全加载并空转。
 */
public final class OrbitAuraClient {

    /** Z 键本地开关状态（默认开）。 */
    private static boolean visible = true;

    /** 圆心相对头部中心向后（脑后方向）的偏移（格）。 */
    private static final double CENTER_BACK = 0.35D;
    /** 预览圆环半径（格）。 */
    private static final double RING_RADIUS = 0.9D;
    /** 圆环分段数（越大越圆）。 */
    private static final int RING_SEGMENTS = 96;
    /** 预览临时颜色（金色，待确认后按“名字颜色”逐形态替换）。 */
    private static final float R = 1.0F, G = 0.84F, B = 0.1F, A = 0.95F;
    /** 圆环中心距头顶的高度（格，粗略取头部中心）。 */
    private static final double HEAD_CENTER_Y = 1.42D;
    /** 只渲染距本地玩家这个距离以内的目标。 */
    private static final double MAX_RANGE = 64.0D;
    private static final double MAX_RANGE_SQ = MAX_RANGE * MAX_RANGE;
    /** 佩戴状态缓存刷新间隔（tick）。 */
    private static final int CACHE_REFRESH = 5;

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
    // 佩戴检测缓存
    // ==================================================================

    private static final Map<UUID, Boolean> WEAR_CACHE = new HashMap<>();
    private static final Map<UUID, Long> CACHE_TIME = new HashMap<>();

    /** 某玩家 Curios 中是否佩戴了任意本模组饰品（全限定 + 守卫，失败按否）。 */
    private static boolean wearingAny(Player player) {
        if (!CompatChecks.curiosLoaded()) {
            return false;
        }
        try {
            java.util.Optional<top.theillusivec4.curios.api.type.capability.ICuriosItemHandler> optional =
                    top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
            if (optional.isEmpty()) {
                return false;
            }
            top.theillusivec4.curios.api.type.capability.ICuriosItemHandler handler = optional.get();
            for (net.minecraftforge.registries.RegistryObject<Item> reg : ModItems.ALL_ITEMS) {
                Item item = reg.get();
                if (item instanceof DivineBeastItem) {
                    for (top.theillusivec4.curios.api.SlotResult result : handler.findCurios(item)) {
                        ItemStack stack = result.stack();
                        if (stack != null && !stack.isEmpty()) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Curios 缺失 / 客户端拿不到该实体数据
        }
        return false;
    }

    /** 带缓存的佩戴判定：同一 tick 内不重复查 Curios。 */
    private static boolean wearsTrinkets(Player player, long gameTime) {
        UUID id = player.getUUID();
        Boolean cached = WEAR_CACHE.get(id);
        Long last = CACHE_TIME.get(id);
        if (cached != null && last != null && gameTime - last < CACHE_REFRESH) {
            return cached;
        }
        boolean value = wearingAny(player);
        WEAR_CACHE.put(id, value);
        CACHE_TIME.put(id, gameTime);
        return value;
    }

    // ==================================================================
    // 圆环几何：竖直（垂直于地面），法向 = 玩家水平朝向
    // ==================================================================

    /**
     * 圆环中心（脑后头部中心）。
     * 只做水平插值：a.x + (b.x-a.x)*t 等，保证移动平滑。
     */
    private static Vec3 headCenter(Player p, float partial) {
        double hx = p.xo + (p.getX() - p.xo) * partial;
        double hy = p.yo + (p.getY() - p.yo) * partial + HEAD_CENTER_Y;
        double hz = p.zo + (p.getZ() - p.zo) * partial;
        return new Vec3(hx, hy, hz);
    }

    /** 玩家水平朝向（忽略俯仰）。 */
    private static Vec3 horizontalFacing(Player p) {
        double yaw = Math.toRadians(p.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw)).normalize();
    }

    /**
     * 圆环平面内的两个基向量：e1=上、e2=右（e2 = facing × up 水平归一）。
     * 圆环点 = center + e1*r*cos + e2*r*sin，平面法向 = facing（竖直环）。
     */
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

    private static Vec3 ringPoint(Vec3 center, Vec3[] basis, double radius, double angle) {
        return center.add(basis[0].scale(Math.cos(angle) * radius))
                .add(basis[1].scale(Math.sin(angle) * radius));
    }

    // ==================================================================
    // 每帧：世界空间细线圆环
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
            Vec3 cam = event.getCamera().getPosition();
            float partialTick = event.getPartialTick();
            long gameTime = level.getGameTime();

            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            try {
                for (Player target : level.players()) {
                    if (!target.isAlive() || me.distanceToSqr(target) > MAX_RANGE_SQ) {
                        continue;
                    }
                    if (!wearsTrinkets(target, gameTime)) {
                        continue;
                    }
                    Vec3 head = headCenter(target, partialTick);
                    // 圆心放到脑后（沿水平朝向的反方向偏移）
                    Vec3 facing = horizontalFacing(target);
                    Vec3 center = head.add(facing.scale(-CENTER_BACK));

                    pose.pushPose();
                    pose.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
                    VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
                    Vec3[] basis = ringBasis(facing);
                    for (int i = 0; i < RING_SEGMENTS; i++) {
                        double a0 = (Math.PI * 2.0D * i) / RING_SEGMENTS;
                        double a1 = (Math.PI * 2.0D * (i + 1)) / RING_SEGMENTS;
                        Vec3 p0 = ringPoint(Vec3.ZERO, basis, RING_RADIUS, a0);
                        Vec3 p1 = ringPoint(Vec3.ZERO, basis, RING_RADIUS, a1);
                        // 世界空间线（中心已平移到圆心）：lines 每两个顶点一条线段
                        consumer.vertex(pose.last().pose(), (float) p0.x, (float) p0.y, (float) p0.z)
                                .color(R, G, B, A)
                                .endVertex();
                        consumer.vertex(pose.last().pose(), (float) p1.x, (float) p1.y, (float) p1.z)
                                .color(R, G, B, A)
                                .endVertex();
                    }
                    pose.popPose();
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
