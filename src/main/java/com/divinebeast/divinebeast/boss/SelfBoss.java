package com.divinebeast.divinebeast.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/** 『我』——主世界的中立 boss（泥土台面，收绿宝石）。 */
public class SelfBoss extends DivineBoss {

    public SelfBoss(EntityType<? extends SelfBoss> type, Level level) {
        super(type, level, Kind.SELF);
    }
}
