package com.divinebeast.divinebeast.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** 『兽』——地狱的中立 boss（下界岩台面，收熟猪排）。 */
public class BeastBoss extends DivineBoss {

    public BeastBoss(EntityType<? extends BeastBoss> type, Level level) {
        super(type, level, Kind.BEAST);
    }
}
