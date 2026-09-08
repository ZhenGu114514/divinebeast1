package com.divinebeast.divinebeast.curio;

import com.divinebeast.divinebeast.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 证悟系效果引擎（仅 Curios 存在时加载）。
 * 佩戴判断：证悟槽(insight)放 祂者初/祂者极；救赎槽(redemption)放 救赎；本心槽(trueheart)放 本心。
 * 按近似实现：
 *  - 祂者初：可放置不可破坏、不可造成伤害、不可受到伤害（含虚空）、隐身+无碰撞（近似）、禁 /tp 涉及你；
 *  - 祂者极：半径 5 格内敌对生物每秒受到 (10+经验等级) 且来源为你的伤害；同样全免伤；
 *  - 救赎 / 本心：判定为"所有饰品效果失效"（effectsDisabled，供其它引擎收敛）；本心额外：击杀生物时自己死亡；
 *  - 救赎 / 本心 附带绑定不可卸（由 CuriosCompat 提供），本类负责运行时状态。
 */
public final class AscensionEffects {

    private static final String INSIGHT_SLOT = "insight";
    private static final String REDEMPTION_SLOT = "redemption";
    private static final String TRUEHEART_SLOT = "trueheart";
    private static final double BEACON_RADIUS = 5.0D;

    /** 同步标记：当前伤害来自祂者极光柱（豁免"不可造成伤害"规则） */
    private static boolean beaconStrike = false;

    private AscensionEffects() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(AscensionEffects::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(AscensionEffects::onLivingAttack);
        MinecraftForge.EVENT_BUS.addListener(AscensionEffects::onLivingDamage);
        MinecraftForge.EVENT_BUS.addListener(AscensionEffects::onLeftClickBlock);
        MinecraftForge.EVENT_BUS.addListener(AscensionEffects::onTeleportCommand);
        MinecraftForge.EVENT_BUS.addListener(AscensionEffects::onDeath);
    }

    // ==================================================================
    // 佩戴判定
    // ==================================================================

    private static boolean wearsInSlot(Player player, String slotId, net.minecraft.world.item.Item item) {
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isEmpty()) {
            return false;
        }
        for (top.theillusivec4.curios.api.SlotResult result : optional.get().findCurios(slotId)) {
            ItemStack stack = result.stack();
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean wearingHeFirst(Player p) {
        return wearsInSlot(p, INSIGHT_SLOT, ModItems.HE_FIRST.get());
    }

    private static boolean wearingHeExtreme(Player p) {
        return wearsInSlot(p, INSIGHT_SLOT, ModItems.HE_EXTREME.get());
    }

    private static boolean wearingRedemption(Player p) {
        return wearsInSlot(p, REDEMPTION_SLOT, ModItems.REDEMPTION.get());
    }

    private static boolean wearingTrueHeart(Player p) {
        return wearsInSlot(p, TRUEHEART_SLOT, ModItems.TRUE_HEART.get());
    }

    /** 救赎/本心：自身装备的所有饰品效果失效 */
    public static boolean effectsDisabled(Player player) {
        return wearingRedemption(player) || wearingTrueHeart(player);
    }

    // ==================================================================
    // 每 tick：隐身/无碰撞近似 + 祂者极光柱伤害 + 虚空保护
    // ==================================================================

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        boolean heFirst = wearingHeFirst(player);
        boolean heExtreme = wearingHeExtreme(player);
        boolean phasing = heFirst || heExtreme;

        if (phasing) {
            // 近似：不可见（无碰撞需旁观模式能力，暂以隐身近似）
            if (!player.isInvisible()) {
                player.setInvisible(true);
            }
            // 虚空保护：低于世界底则送回上方
            if (player.getY() < player.level().getMinBuildHeight() - 4) {
                player.teleportTo(player.getX(), player.level().getMinBuildHeight() + 4, player.getZ());
            }
        } else {
            if (player.isInvisible() && !player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
                player.setInvisible(false);
            }
        }

        if (heExtreme && player.tickCount % 20 == 0) {
            float dmg = 10.0F + player.experienceLevel;
            for (Monster mob : player.level().getEntitiesOfClass(Monster.class,
                    net.minecraft.world.phys.AABB.ofSize(player.position(),
                            BEACON_RADIUS * 2, BEACON_RADIUS * 2, BEACON_RADIUS * 2))) {
                if (mob.isAlive()) {
                    beaconStrike = true;
                    try {
                        mob.hurt(mob.damageSources().playerAttack(player), dmg);
                    } finally {
                        beaconStrike = false;
                    }
                }
            }
        }
    }

    // ==================================================================
    // 攻击 / 受击
    // ==================================================================

    private static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide || beaconStrike) {
            return;
        }
        Player attacker = null;
        if (event.getSource().getEntity() instanceof Player player) {
            attacker = player;
        }
        // 祂者初/祂者极：无法对其他生物造成伤害
        if (attacker != null && (wearingHeFirst(attacker) || wearingHeExtreme(attacker))
                && !event.getEntity().is(attacker)) {
            event.setCanceled(true);
        }
    }

    private static boolean wearingHeFirstOrExtreme(DamageSource source) {
        Player attacker = null;
        Entity e = source.getEntity();
        if (e instanceof Player player) {
            attacker = player;
        } else if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof Player player2) {
            attacker = player2;
        }
        if (attacker == null) {
            return false;
        }
        return wearingHeFirst(attacker) || wearingHeExtreme(attacker);
    }

    private static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || beaconStrike) {
            return;
        }
        // 祂者初/祂者极：不可受到伤害（含虚空等一切）
        if (event.getEntity() instanceof Player player
                && (wearingHeFirst(player) || wearingHeExtreme(player))) {
            event.setCanceled(true);
            return;
        }
        // 阻止穿戴者造成任何伤害（含弹射物来源）
        if (wearingHeFirstOrExtreme(event.getSource())
                && !event.getEntity().is(event.getSource().getEntity())) {
            event.setCanceled(true);
        }
    }

    // ==================================================================
    // 冒险式：可放置、不可破坏
    // ==================================================================

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && (wearingHeFirst(player) || wearingHeExtreme(player))) {
            event.setCanceled(true);
        }
    }

    // ==================================================================
    // 禁 /tp 涉及你（传送命令事件）
    // ==================================================================

    private static void onTeleportCommand(EntityTeleportEvent.TeleportCommand event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player
                && (wearingHeFirst(player) || wearingHeExtreme(player))) {
            event.setCanceled(true);
        }
    }

    // ==================================================================
    // 本心：击杀生物时自己死亡（复活流程见后续死亡界面批次）
    // ==================================================================

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player killer) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player player)) {
            return;
        }
        if (wearingTrueHeart(player) && !player.isDeadOrDying()) {
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        }
    }
}
