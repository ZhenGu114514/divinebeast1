package com.divinebeast.divinebeast.reward;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 由代码触发的模组成就发放（进度 JSON 中 criteria 用 impossible，由本类手动 award）。
 */
public final class ProgressGrants {

    public static final String MOD_ID = "divinebeast";

    private ProgressGrants() {
    }

    public static void grant(ServerPlayer player, String id) {
        Advancement advancement = player.server.getAdvancements()
                .getAdvancement(new ResourceLocation(MOD_ID, id));
        if (advancement == null) {
            return;
        }
        var progress = player.getAdvancements();
        if (!progress.getOrStartProgress(advancement).isDone()) {
            progress.award(advancement, "impossible");
        }
    }
}
