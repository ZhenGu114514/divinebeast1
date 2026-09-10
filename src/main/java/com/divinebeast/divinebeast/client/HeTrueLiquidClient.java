package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.CompatChecks;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * 真者祂「蹈水履火」的客户端实现：<b>在水与岩浆中视野不受遮挡、移速不降低</b>。
 *
 * <p>为什么必须放在客户端：雾效、全屏遮罩、以及"本地玩家自身的位移"都由客户端计算，
 * 服务端改速度不会影响本地玩家的实际移动。
 *
 * <ul>
 *   <li><b>视野</b>：拦截 {@link ViewportEvent.RenderFog}（WATER / LAVA），把雾的
 *       近/远平面推到极远并取消事件 → 水中、岩浆中都不再有雾；再取消
 *       {@link RenderBlockScreenEffectEvent} 的 WATER 遮罩 → 去掉水下的蓝色全屏着色。</li>
 *   <li><b>移速</b>：原版在水里（0.02 基础加速度）和岩浆里（固定 0.02）都会大幅降速，
 *       且岩浆分支不接受任何属性加成。这里改为在"玩家正按住移动键且确实在液体中"时，
 *       把水平速度补到与陆地行走相当的水平（目标值由玩家自身的 MOVEMENT_SPEED 换算，
 *       因此会随神行等加成同步）。松手即不再补偿，交给原版阻力自然减速。</li>
 * </ul>
 *
 * <p>纯客户端类，只通过 DistExecutor 在 CLIENT 侧加载。
 */
public final class HeTrueLiquidClient {

    /** 陆地行走速度与 MOVEMENT_SPEED 属性的换算系数（0.1 → 约 0.215 格/tick）。 */
    private static final double LAND_SPEED_PER_ATTR = 2.15D;
    /** 液体中的最低目标水平速度（格/tick），避免属性过低时补偿失效。 */
    private static final double MIN_TARGET_SPEED = 0.16D;
    /** 单帧最大放大倍数：防止一帧内速度暴涨导致瞬移/服务端回拉。 */
    private static final double MAX_BOOST_PER_TICK = 1.5D;
    /** 速度低于该值时先交给原版加速，不做补偿（避免刚起步就瞬移）。 */
    private static final double START_EPSILON = 1.0E-4D;
    /** 取消雾效时使用的极远平面距离。 */
    private static final float NO_FOG_DISTANCE = 1.0E5F;

    private HeTrueLiquidClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(HeTrueLiquidClient::onRenderFog);
        MinecraftForge.EVENT_BUS.addListener(HeTrueLiquidClient::onBlockScreenEffect);
        MinecraftForge.EVENT_BUS.addListener(HeTrueLiquidClient::onPlayerTick);
    }

    /** 玩家是否为"真者祂"（含封印判定）。客户端查询 Curios，缺 Curios 时直接返回 false。 */
    private static boolean isHeTrue(LocalPlayer player) {
        if (player == null || !CompatChecks.curiosLoaded()) {
            return false;
        }
        return com.divinebeast.divinebeast.curio.AscensionEffects.wearingHeTrue(player)
                && !com.divinebeast.divinebeast.curio.AscensionEffects.effectsDisabled(player);
    }

    private static LocalPlayer activePlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !isHeTrue(player)) {
            return null;
        }
        return player;
    }

    /** 去掉水/岩浆的雾：把近远平面推到极远，并取消事件使其生效。 */
    private static void onRenderFog(ViewportEvent.RenderFog event) {
        FogType type = event.getType();
        if (type != FogType.WATER && type != FogType.LAVA) {
            return;
        }
        if (activePlayer() == null) {
            return;
        }
        event.setNearPlaneDistance(NO_FOG_DISTANCE);
        event.setFarPlaneDistance(NO_FOG_DISTANCE);
        event.setCanceled(true);
    }

    /** 去掉水下的蓝色全屏遮罩（岩浆没有该遮罩，靠上面的雾处理）。 */
    private static void onBlockScreenEffect(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() != RenderBlockScreenEffectEvent.OverlayType.WATER) {
            return;
        }
        if (!(event.getPlayer() instanceof LocalPlayer local) || !isHeTrue(local)) {
            return;
        }
        event.setCanceled(true);
    }

    /** 液体中按住移动键时补偿水平速度，使其不低于陆地行走水平。 */
    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        LocalPlayer player = activePlayer();
        if (player == null || event.player != player) {
            return; // 只处理本地玩家自身
        }
        if (!player.isInWater() && !player.isInLava()) {
            return;
        }
        if (!isPressingMove(Minecraft.getInstance())) {
            return; // 没按方向键 → 交给原版阻力自然减速
        }
        Vec3 motion = player.getDeltaMovement();
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontal >= targetSpeed(player) || horizontal < START_EPSILON) {
            return;
        }
        double factor = Math.min(targetSpeed(player) / horizontal, MAX_BOOST_PER_TICK);
        player.setDeltaMovement(motion.x * factor, motion.y, motion.z * factor);
    }

    /** 目标水平速度：与陆地行走相当（随 MOVEMENT_SPEED 属性同步缩放）。 */
    private static double targetSpeed(LocalPlayer player) {
        double attr = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return Math.max(MIN_TARGET_SPEED, attr * LAND_SPEED_PER_ATTR);
    }

    /** 是否按住了任一水平移动键。用键位状态而非输入向量，避免依赖内部字段。 */
    private static boolean isPressingMove(Minecraft mc) {
        return isDown(mc.options.keyUp) || isDown(mc.options.keyDown)
                || isDown(mc.options.keyLeft) || isDown(mc.options.keyRight);
    }

    private static boolean isDown(KeyMapping mapping) {
        return mapping != null && mapping.isDown();
    }
}
