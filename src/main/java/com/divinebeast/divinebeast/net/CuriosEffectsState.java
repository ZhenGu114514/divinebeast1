package com.divinebeast.divinebeast.net;

import net.minecraft.world.entity.player.Player;

/**
 * C 键开关状态的读写（无 Curios 依赖，放在 net 包以便消息处理安全使用）。
 * 状态存于玩家 persistent NBT，仅服务器侧读写。
 */
public final class CuriosEffectsState {

    public static final String TAG_RESPAWN_TOGGLE = "divinebeast.respawn_toggle";
    public static final String TAG_REDEMPTION_MARK = "divinebeast.redemption_mark";

    private CuriosEffectsState() {
    }

    /** 默认开启 */
    public static boolean respawnToggle(Player player) {
        return !player.getPersistentData().contains(TAG_RESPAWN_TOGGLE)
                || player.getPersistentData().getBoolean(TAG_RESPAWN_TOGGLE);
    }

    public static void setRespawnToggle(Player player, boolean value) {
        player.getPersistentData().putBoolean(TAG_RESPAWN_TOGGLE, value);
    }

    public static boolean hasRedemptionMark(net.minecraft.world.entity.LivingEntity entity) {
        return entity.getPersistentData().getBoolean(TAG_REDEMPTION_MARK);
    }

    public static void setRedemptionMark(net.minecraft.world.entity.LivingEntity entity, boolean value) {
        entity.getPersistentData().putBoolean(TAG_REDEMPTION_MARK, value);
    }
}
