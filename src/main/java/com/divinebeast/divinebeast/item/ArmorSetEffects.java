package com.divinebeast.divinebeast.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 三个 boss 盔甲的套装效果（不需要 Curios，纯原版盔甲槽判定）。
 *
 * <p><b>每件</b>
 * <ul>
 *   <li>「我」每件 <b>+20 生命上限</b>（4 件 = +80）</li>
 *   <li>「兽」每件 <b>+15% 攻击</b>（乘算，4 件 = +60%）</li>
 *   <li>「祂」每件 <b>+1 级抗性提升</b>（4 件 = 抗性 IV）</li>
 * </ul>
 *
 * <p><b>全套</b>
 * <ul>
 *   <li>「我」：<b>每秒回复 20 点生命</b></li>
 *   <li>「兽」：<b>伤害再 +40%</b>（与每件的 +60% 合计 +100%）</li>
 *   <li>「祂」：<b>抗性再 +1 级</b>（抗性 V）并<b>获得飞行</b></li>
 * </ul>
 *
 * <p>全部按 tick 幂等重算：穿上/脱下/死亡/换维度都会自动收敛，不留残值。
 */
public final class ArmorSetEffects {

    private static final UUID SELF_HEALTH_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000d1");
    private static final UUID BEAST_ATTACK_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000d2");
    private static final UUID BEAST_FULL_ATTACK_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000d3");

    /** 每件「我」甲的生命上限加成 */
    public static final double SELF_HEALTH_PER_PIECE = 20.0D;
    /** 每件「兽」甲的攻击加成（乘算，0.15 = +15%） */
    public static final double BEAST_ATTACK_PER_PIECE = 0.15D;
    /** 「兽」全套额外攻击加成（0.40 = +40%） */
    public static final double BEAST_ATTACK_FULL_SET = 0.40D;
    /** 「我」全套每秒回复的生命值 */
    public static final float SELF_SET_HEAL = 20.0F;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** 由本类授予飞行能力的玩家（脱下全套时好精确收回，不误伤真者祂的飞行） */
    private static final Set<UUID> FLIGHT_GRANTED = new HashSet<>();

    private ArmorSetEffects() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ArmorSetEffects::onPlayerTick);
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        // 「三相」装备＝三套下级全都要：它的每一件都同时计入三个套装
        int threePhase = countPieces(player, ModItems.THREE_PHASE_HELMET.get(), ModItems.THREE_PHASE_CHESTPLATE.get(),
                ModItems.THREE_PHASE_LEGGINGS.get(), ModItems.THREE_PHASE_BOOTS.get());
        int self = threePhase + countPieces(player, ModItems.SELF_HELMET.get(), ModItems.SELF_CHESTPLATE.get(),
                ModItems.SELF_LEGGINGS.get(), ModItems.SELF_BOOTS.get());
        int beast = threePhase + countPieces(player, ModItems.BEAST_HELMET.get(), ModItems.BEAST_CHESTPLATE.get(),
                ModItems.BEAST_LEGGINGS.get(), ModItems.BEAST_BOOTS.get());
        int he = threePhase + countPieces(player, ModItems.HE_HELMET.get(), ModItems.HE_CHESTPLATE.get(),
                ModItems.HE_LEGGINGS.get(), ModItems.HE_BOOTS.get());

        // ---- 「我」：每件 +20 生命；全套每秒回 20 ----
        setAddition(player, Attributes.MAX_HEALTH, SELF_HEALTH_MOD, "divinebeast_armor_self_hp",
                SELF_HEALTH_PER_PIECE * self);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        if (self == 4 && player.tickCount % 20 == 0) {
            player.heal(SELF_SET_HEAL);
        }

        // ---- 「兽」：每件 +15% 攻击；全套再 +40% ----
        setMultiplier(player, Attributes.ATTACK_DAMAGE, BEAST_ATTACK_MOD, "divinebeast_armor_beast_atk",
                BEAST_ATTACK_PER_PIECE * beast);
        setMultiplier(player, Attributes.ATTACK_DAMAGE, BEAST_FULL_ATTACK_MOD, "divinebeast_armor_beast_full_atk",
                beast == 4 ? BEAST_ATTACK_FULL_SET : 0.0D);

        // ---- 「祂」：每件 +1 级抗性；全套再 +1 级并给飞行 ----
        if (he > 0) {
            int amplifier = (he - 1) + (he == 4 ? 1 : 0);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, amplifier, false, false));
        }
        applyFlight(player, he == 4);
    }

    private static int countPieces(Player player, net.minecraft.world.item.Item helmet,
                                    net.minecraft.world.item.Item chestplate,
                                    net.minecraft.world.item.Item leggings,
                                    net.minecraft.world.item.Item boots) {
        int count = 0;
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(helmet)) {
            count++;
        }
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(chestplate)) {
            count++;
        }
        if (player.getItemBySlot(EquipmentSlot.LEGS).is(leggings)) {
            count++;
        }
        if (player.getItemBySlot(EquipmentSlot.FEET).is(boots)) {
            count++;
        }
        return count;
    }

    /** 幂等地把某属性的 ADDITION 修饰符设成指定数值（0 表示移除）。 */
    private static void setAddition(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                    UUID uuid, String name, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(uuid);
        if (amount <= 0.0D) {
            if (existing != null) {
                instance.removeModifier(uuid);
            }
            return;
        }
        if (existing != null && existing.getOperation() == AttributeModifier.Operation.ADDITION
                && Math.abs(existing.getAmount() - amount) < 1.0E-6D) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(uuid);
        }
        instance.addPermanentModifier(new AttributeModifier(uuid, name, amount,
                AttributeModifier.Operation.ADDITION));
    }

    /** 幂等地把某属性的 MULTIPLY_TOTAL 修饰符设成指定数值（0 表示移除）。 */
    private static void setMultiplier(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                      UUID uuid, String name, double multiplyTotal) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(uuid);
        if (multiplyTotal <= 0.0D) {
            if (existing != null) {
                instance.removeModifier(uuid);
            }
            return;
        }
        if (existing != null && existing.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL
                && Math.abs(existing.getAmount() - multiplyTotal) < 1.0E-6D) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(uuid);
        }
        instance.addPermanentModifier(new AttributeModifier(uuid, name, multiplyTotal,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** 「祂」全套：给飞行；脱下时只收回"本类给的那一次"，不影响创造模式与真者祂自己的飞行。 */
    private static void applyFlight(Player player, boolean wanted) {
        boolean granted = FLIGHT_GRANTED.contains(player.getUUID());
        if (wanted) {
            if (!player.isCreative() && !player.isSpectator() && !player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.onUpdateAbilities();
                }
                FLIGHT_GRANTED.add(player.getUUID());
            }
            return;
        }
        if (granted) {
            FLIGHT_GRANTED.remove(player.getUUID());
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.onUpdateAbilities();
                }
            }
        }
    }
}
