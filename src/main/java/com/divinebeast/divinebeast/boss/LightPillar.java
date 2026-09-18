package com.divinebeast.divinebeast.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 「光柱」——被 {@code he_sword}（祂剑被动）与『祂』boss 的招式共用。
 *
 * <p>两种用法：
 * <ul>
 *   <li><b>祂剑被动</b>：命中即落柱，{@code hurtPlayers = false}（<b>不会伤害玩家</b>），
 *       每 <b>1 tick</b> 结算 10 点（持续 1 秒 = 20 tick，合计 200 点），魔法伤害，冷却 2 秒。</li>
 *   <li><b>祂的招式</b>：先画一圈警示（1.5 秒）再落柱，{@code hurtPlayers = true}；
 *       <b>每秒结算 10 点、持续 2 秒</b>，而且是 {@link ModDamageTypes#bossPillar}
 *       ——<b>无视防御 / 伤免 / 护甲</b>（可躲）。</li>
 * </ul>
 *
 * <p>永远跳过光柱的施法者本人。
 */
public final class LightPillar {

    /** 光柱作用半径（格） */
    private static final double RADIUS = 3.0D;
    /** 光柱纵向范围（格） */
    private static final double HEIGHT = 12.0D;

    private static final List<Pillar> ACTIVE = new ArrayList<>();

    private LightPillar() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(LightPillar::onLevelTick);
    }

    /**
     * 落下一道光柱。
     *
     * @param warnTicks   警示时长（0 = 立刻落柱）
     * @param lifeTicks   光柱持续时长
     * @param damage      每次结算的伤害
     * @param periodTicks 结算间隔（1 = 每 tick 一次；20 = 每秒一次）
     * @param bypass      是否使用"无视防御/伤免/护甲"的伤害类型（{@link ModDamageTypes}）
     */
    public static void strike(ServerLevel level, Vec3 pos, LivingEntity caster, boolean hurtPlayers,
                              int warnTicks, int lifeTicks, float damage, int periodTicks, boolean bypass) {
        ACTIVE.add(new Pillar(level, pos, caster, hurtPlayers, warnTicks, lifeTicks,
                damage, Math.max(1, periodTicks), bypass));
    }

    private static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Pillar> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Pillar pillar = it.next();
            if (pillar.level != level) {
                continue;
            }
            if (!pillar.advance()) {
                it.remove();
            }
        }
    }

    private static final class Pillar {

        private final ServerLevel level;
        private final Vec3 pos;
        private final LivingEntity caster;
        private final boolean hurtPlayers;
        private final float damage;
        private final int periodTicks;
        private final boolean bypass;
        private final int lifeTicks;
        private int warnLeft;
        private int age;

        Pillar(ServerLevel level, Vec3 pos, LivingEntity caster, boolean hurtPlayers,
               int warnTicks, int lifeTicks, float damage, int periodTicks, boolean bypass) {
            this.level = level;
            this.pos = pos;
            this.caster = caster;
            this.hurtPlayers = hurtPlayers;
            this.warnLeft = warnTicks;
            this.lifeTicks = lifeTicks;
            this.damage = damage;
            this.periodTicks = periodTicks;
            this.bypass = bypass;
        }

        /** @return false 表示该光柱已结束 */
        boolean advance() {
            if (this.warnLeft > 0) {
                this.warnLeft--;
                this.drawWarning();
                if (this.warnLeft == 0) {
                    this.level.playSound(null, this.pos.x, this.pos.y, this.pos.z,
                            SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 1.4F, 0.9F);
                }
                return true;
            }
            if (this.age % this.periodTicks == 0) {
                this.damageHit();
            }
            this.drawBeam();
            this.age++;
            return this.age < this.lifeTicks;
        }

        /** 警示圈：地面上一圈粒子 + 蜂鸣 */
        private void drawWarning() {
            if (this.warnLeft % 10 == 0) {
                this.level.playSound(null, this.pos.x, this.pos.y, this.pos.z,
                        SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.4F);
            }
            for (int i = 0; i < 16; i++) {
                double angle = Math.PI * 2.0D * i / 16.0D;
                this.level.sendParticles(ParticleTypes.END_ROD,
                        this.pos.x + Math.cos(angle) * RADIUS, this.pos.y + 0.15D,
                        this.pos.z + Math.sin(angle) * RADIUS,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            this.level.sendParticles(ParticleTypes.CRIT,
                    this.pos.x, this.pos.y + 0.4D, this.pos.z, 8, RADIUS * 0.5D, 0.2D, RADIUS * 0.5D, 0.02D);
        }

        /** 光柱本体：竖直一列亮粒子 */
        private void drawBeam() {
            for (double y = -1.0D; y <= HEIGHT; y += 1.0D) {
                this.level.sendParticles(ParticleTypes.END_ROD,
                        this.pos.x, this.pos.y + y, this.pos.z, 2, 0.25D, 0.0D, 0.25D, 0.01D);
            }
            if (this.age % 10 == 0) {
                this.level.sendParticles(ParticleTypes.FLASH, this.pos.x, this.pos.y + 1.0D, this.pos.z,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        private void damageHit() {
            AABB box = AABB.ofSize(this.pos.add(0.0D, HEIGHT * 0.5D, 0.0D),
                    RADIUS * 2.0D, HEIGHT, RADIUS * 2.0D);
            for (LivingEntity target : this.level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (!target.isAlive() || target == this.caster) {
                    continue;
                }
                if (!this.hurtPlayers && target instanceof Player) {
                    continue;   // 祂剑的被动不伤玩家
                }
                DamageSource source = this.bypass
                        ? ModDamageTypes.bossPillar(this.level, this.caster)
                        : target.damageSources().magic();
                target.hurt(source, this.damage);
            }
        }
    }
}
