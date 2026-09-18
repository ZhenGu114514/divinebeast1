package com.divinebeast.divinebeast.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 每个维度一份的 boss 竞技场记录：选址坐标、是否已召唤过。
 *
 * <p>用 {@link SavedData}（存进存档的 {@code data/divinebeast_boss_arena.dat}），
 * 因此"每个维度只自动生成一次、被击杀后不再重生"这条能跨存档重载保持。
 */
public class BossArenaData extends SavedData {

    private static final String DATA_NAME = "divinebeast_boss_arena";

    private boolean chosen;
    private int siteX;
    private int siteZ;
    private boolean spawned;

    public BossArenaData() {
    }

    public static BossArenaData load(CompoundTag tag) {
        BossArenaData data = new BossArenaData();
        data.chosen = tag.getBoolean("Chosen");
        data.siteX = tag.getInt("SiteX");
        data.siteZ = tag.getInt("SiteZ");
        data.spawned = tag.getBoolean("Spawned");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Chosen", this.chosen);
        tag.putInt("SiteX", this.siteX);
        tag.putInt("SiteZ", this.siteZ);
        tag.putBoolean("Spawned", this.spawned);
        return tag;
    }

    public static BossArenaData of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BossArenaData::load, BossArenaData::new, DATA_NAME);
    }

    public boolean isChosen() {
        return chosen;
    }

    public int siteX() {
        return siteX;
    }

    public int siteZ() {
        return siteZ;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public void choose(int x, int z) {
        this.chosen = true;
        this.siteX = x;
        this.siteZ = z;
        this.setDirty();
    }

    public void setSpawned(boolean value) {
        this.spawned = value;
        this.setDirty();
    }
}
