package com.divinebeast.divinebeast.net;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** 无 Curios 依赖的"回出生点复活"工具（普通重生与迁移共用）。 */
public final class AscensionEffectsNoCurios {

    private AscensionEffectsNoCurios() {
    }

    public static void revive(ServerPlayer player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        if (player.level() instanceof ServerLevel level) {
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
        player.setHealth(player.getMaxHealth());
        player.fallDistance = 0.0F;
        player.removeAllEffects();
        player.setFireTicks(0);
        player.setInvulnerable(false);
    }
}
