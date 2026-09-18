package com.divinebeast.divinebeast.boss;

import com.divinebeast.divinebeast.DivineBeastMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 本模组自己的伤害类型。
 *
 * <p>目前只有一种 —— {@code divinebeast:boss_pillar}：『祂』的光柱。
 * 它<b>无视防御、伤免与护甲</b>（通过把它加进 {@code bypasses_armor} /
 * {@code bypasses_enchantments} / {@code bypasses_resistance} / {@code bypasses_effects} 四个标签实现），
 * 但<b>不是</b> {@code generic_kill} 那种 /kill 级伤害 —— 所以不会穿透"复活无敌帧"，
 * 也不会被本模组"免疫 generic_kill"的免伤体系整段吃掉。
 *
 * <p>取不到伤害类型时（例如数据包缺失）会退回 {@code genericKill}，绝不因此崩服。
 */
public final class ModDamageTypes {

    /** data/divinebeast/damage_type/boss_pillar.json */
    public static final ResourceKey<DamageType> BOSS_PILLAR =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(DivineBeastMod.MOD_ID, "boss_pillar"));

    private ModDamageTypes() {
    }

    /** 光柱伤害源：无视防御 / 伤免 / 护甲。 */
    public static DamageSource bossPillar(Level level, @Nullable Entity source) {
        try {
            Registry<DamageType> registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
            return new DamageSource(registry.getHolderOrThrow(BOSS_PILLAR), source);
        } catch (Throwable t) {
            return level.damageSources().genericKill();
        }
    }
}
