package com.divinebeast.divinebeast.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** 『祂』——末地的中立 boss（末地石台面，收泥土）。 */
public class HeBoss extends DivineBoss {

    public HeBoss(EntityType<? extends HeBoss> type, Level level) {
        super(type, level, Kind.HE);
    }
}
