package com.divinebeast.divinebeast.net;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 无 Curios 依赖的"回出生点复活"工具（普通重生与迁移共用）。
 */
public final class AscensionEffectsNoCurios {

    private AscensionEffectsNoCurios() {
    }

    /** 普通重生：在当前维度复活（与原逻辑一致）。 */
    public static void revive(ServerPlayer player) {
        revive(player, false);
    }

    /**
     * 证悟迁移复活：当玩家身处地狱/末地（非主世界）触发自我救赎/找回自我时，
     * 把玩家送返主世界出生点；在主世界触发则与普通重生一致。
     */
    public static void reviveHome(ServerPlayer player) {
        revive(player, true);
    }

    private static void revive(ServerPlayer player, boolean homeOverworld) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        if (player.level() instanceof ServerLevel level) {
            boolean crossDim = homeOverworld
                    && player.level().dimension() != Level.OVERWORLD;
            if (crossDim) {
                ServerLevel overworld = player.getServer().overworld();
                BlockPos home = player.getRespawnPosition() != null
                        && player.getRespawnDimension() == Level.OVERWORLD
                        ? player.getRespawnPosition()
                        : overworld.getSharedSpawnPos();
                // ServerLevel 版 teleportTo：跨维度传送玩家到主世界出生点
                player.teleportTo(overworld, home.getX() + 0.5D, home.getY() + 1.0D,
                        home.getZ() + 0.5D, player.getYRot(), player.getXRot());
            } else {
                BlockPos respawn = null;
                if (player.getRespawnPosition() != null
                        && player.getRespawnDimension() == player.level().dimension()) {
                    respawn = player.getRespawnPosition();
                }
                if (respawn == null) {
                    respawn = level.getSharedSpawnPos();
                }
                player.teleportTo(respawn.getX() + 0.5D, respawn.getY() + 1.0D, respawn.getZ() + 0.5D);
            }
        }
        player.setHealth(player.getMaxHealth());
        player.fallDistance = 0.0F;
        player.removeAllEffects();
        player.clearFire();
        player.setInvulnerable(false);
    }
}
