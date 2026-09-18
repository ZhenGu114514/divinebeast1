package com.divinebeast.divinebeast.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/**
 * 三个 boss 的盔甲材质（1.20.1 的 {@link ArmorMaterial} 是接口，用枚举实现即可，无需注册）。
 *
 * <p><b>材质名即贴图路径</b>：{@code getName()} 返回 {@code "divinebeast:self"}，渲染器会去
 * {@code assets/divinebeast/textures/models/armor/self_layer_1.png / self_layer_2.png} 取图
 * ——那两张图由 {@code tools/gen_textures.js} 生成。
 *
 * <p>数值刻意对齐原版铁 / 金 / 下界合金，这样换材质不会改变手感；修理材料换成了各自的锭。
 */
public enum ModArmorMaterials implements ArmorMaterial {

    /** 『我』：铁档（耐久 15 倍，防御 2/5/6/2） */
    SELF("divinebeast:self", 15, 2, 5, 6, 2, 14,
            SoundEvents.ARMOR_EQUIP_IRON, 0.0F, 0.0F,
            () -> Ingredient.of(ModItems.SELF_INGOT.get())),

    /** 『兽』：金档（耐久 7 倍，防御 1/3/5/2，附魔性最高） */
    BEAST("divinebeast:beast", 7, 1, 3, 5, 2, 25,
            SoundEvents.ARMOR_EQUIP_GOLD, 0.0F, 0.0F,
            () -> Ingredient.of(ModItems.BEAST_INGOT.get())),

    /** 『祂』：下界合金档（耐久 37 倍，防御 3/6/8/3，韧性与击退抗性） */
    HE("divinebeast:he", 37, 3, 6, 8, 3, 15,
            SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0F, 0.1F,
            () -> Ingredient.of(ModItems.HE_INGOT.get())),

    /** 『三相』：三套效果合一（数值取最高档，韧性与击退抗性再上一级） */
    THREE_PHASE("divinebeast:three_phase", 45, 4, 7, 9, 4, 20,
            SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0F, 0.15F,
            () -> Ingredient.of(ModItems.THREE_PHASE_INGOT.get()));

    private final String name;
    private final int durabilityMultiplier;
    private final int bootsDefense;
    private final int leggingsDefense;
    private final int chestplateDefense;
    private final int helmetDefense;
    private final int enchantmentValue;
    private final SoundEvent equipSound;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repairIngredient;

    ModArmorMaterials(String name, int durabilityMultiplier,
                      int bootsDefense, int leggingsDefense, int chestplateDefense, int helmetDefense,
                      int enchantmentValue, SoundEvent equipSound, float toughness, float knockbackResistance,
                      Supplier<Ingredient> repairIngredient) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.bootsDefense = bootsDefense;
        this.leggingsDefense = leggingsDefense;
        this.chestplateDefense = chestplateDefense;
        this.helmetDefense = helmetDefense;
        this.enchantmentValue = enchantmentValue;
        this.equipSound = equipSound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repairIngredient = repairIngredient;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> bootsDefense;
            case LEGGINGS -> leggingsDefense;
            case CHESTPLATE -> chestplateDefense;
            case HELMET -> helmetDefense;
            default -> 0;
        };
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public SoundEvent getEquipSound() {
        return equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repairIngredient.get();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockbackResistance;
    }
}
