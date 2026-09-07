package com.divinebeast.divinebeast.reward;

import net.minecraft.advancements.AdvancementHolder;
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
        AdvancementHolder holder = player.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(MOD_ID, id));
        if (holder == null) {
            return;
        }
        var progress = player.getAdvancements();
        if (!progress.getOrStartProgress(holder).isDone()) {
            progress.award(holder, "impossible");
        }
    }
}
