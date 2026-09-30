package com.divinebeast.divinebeast.item;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/**
 * 三个 boss（我 / 兽 / 祂）与「三相」的<b>武器 / 工具材质</b>。
 *
 * <p>1.20.1 的 {@link Tier} 只有 6 个抽象方法（没有 default 方法），自己用枚举实现即可，无需注册。
 * 盔甲走 {@link ModArmorMaterials}（原版 {@code ArmorMaterial}），武器与工具走本类。
 *
 * <p><b>统一数值（1.7.19 起）</b>
 * <ul>
 *   <li><b>耐久：全部 10000</b>（剑 / 镐 / 斧 / 锹 / 锄 都是；见 {@link #DURABILITY}）。</li>
 *   <li><b>挖掘等级：全部 4</b> —— 原版最高（等同下界合金），任何"需要石 / 铁 / 钻石工具"的
 *       方块都能采（黑曜石、远古残骸等）。见 {@link #MAX_MINING_LEVEL}。</li>
 *   <li>挖掘速度 / 攻击加成 / 附魔性：<b>保留各自原来的档位</b>
 *       （我 = 原铁档 6.0 / +2 / 14，兽 = 原金档 12.0 / +1 / 25，
 *        祂 = 原下界合金档 9.0 / +4 / 15，三相 = 9.0 / +4 / 20）。
 *       因为攻击加成与原来完全一致，武器显示伤害（111 / 222 / 333 / 999）与本次改动前没有任何变化。</li>
 *   <li>修理材料：改用各自体系的锭（我锭 / 兽锭 / 祂锭 / 三相锭），与盔甲一致
 *       （原先是各自借用原版档位的修理材料，例如「我」的工具要用原版铁锭修）。</li>
 * </ul>
 *
 * <p><b>关于"挖掘等级最高"会不会被 Forge 的等级排序表挡住</b>：不会。
 * Forge 的 {@code TierSortingRegistry.isCorrectTierForDrops(tier, state)} 对<b>未注册进排序表</b>
 * 的自定义材质会退回 {@code isCorrectTierVanilla(...)}（官方注释写明"复制自
 * DiggerItem.isCorrectToolForDrops 的逻辑"），也就是只比较 {@link #getLevel()} 与原版的
 * "需要石/铁/钻石工具"方块标签。等级 4 时三个比较全部通过，因此可以采任何方块。
 * 这里刻意不调用 {@code TierSortingRegistry.registerTier}：那需要在世界加载前完成注册，
 * 时机不对会直接抛错崩端，而本模组的材质只给自家物品用，没有跨模组排序需求。
 */
public enum ModTiers implements Tier {

    /** 『我』：原铁档手感（速度 6.0 / 攻击 +2 / 附魔 14），耐久 10000、挖掘等级 4 */
    SELF(6.0F, 2.0F, 14, () -> Ingredient.of(ModItems.SELF_INGOT.get())),

    /** 『兽』：原金档手感（挖掘最快 12.0 / 攻击 +1 / 附魔 25），耐久 10000、挖掘等级 4 */
    BEAST(12.0F, 1.0F, 25, () -> Ingredient.of(ModItems.BEAST_INGOT.get())),

    /** 『祂』：原下界合金档（速度 9.0 / 攻击 +4 / 附魔 15），耐久 10000、挖掘等级 4 */
    HE(9.0F, 4.0F, 15, () -> Ingredient.of(ModItems.HE_INGOT.get())),

    /** 『三相』：原下界合金档但附魔性更高（速度 9.0 / 攻击 +4 / 附魔 20），耐久 10000、挖掘等级 4 */
    THREE_PHASE(9.0F, 4.0F, 20, () -> Ingredient.of(ModItems.THREE_PHASE_INGOT.get()));

    /** 所有武器 / 工具的耐久上限。 */
    public static final int DURABILITY = 10000;

    /** 挖掘等级：原版最高值（下界合金 = 4）。 */
    public static final int MAX_MINING_LEVEL = 4;

    private final float speed;
    private final float attackDamageBonus;
    private final int enchantmentValue;
    private final Supplier<Ingredient> repairIngredient;

    ModTiers(float speed, float attackDamageBonus, int enchantmentValue,
             Supplier<Ingredient> repairIngredient) {
        this.speed = speed;
        this.attackDamageBonus = attackDamageBonus;
        this.enchantmentValue = enchantmentValue;
        this.repairIngredient = repairIngredient;
    }

    /** 耐久上限：一律 10000。 */
    @Override
    public int getUses() {
        return DURABILITY;
    }

    /** 挖掘速度倍率（保留各自原档位）。 */
    @Override
    public float getSpeed() {
        return speed;
    }

    /** 攻击伤害加成（保留各自原档位，因此武器总伤害不变）。 */
    @Override
    public float getAttackDamageBonus() {
        return attackDamageBonus;
    }

    /**
     * 挖掘等级：<b>一律返回最高值 4</b>。
     *
     * <p>原版 {@code DiggerItem#isCorrectToolForDrops} 用它判断能否采"需要石 / 铁 / 钻石工具"的方块
     * （三个判断分别是 {@code level < 1 / < 2 / < 3} 时拒绝），等级 4 时全部通过。
     */
    @Override
    public int getLevel() {
        return MAX_MINING_LEVEL;
    }

    /** 附魔性（保留各自原档位）。 */
    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    /** 修理材料：各自体系的锭（用 Supplier 延迟取值，避免与 ModItems 的静态初始化互相牵扯）。 */
    @Override
    public Ingredient getRepairIngredient() {
        return repairIngredient.get();
    }
}
