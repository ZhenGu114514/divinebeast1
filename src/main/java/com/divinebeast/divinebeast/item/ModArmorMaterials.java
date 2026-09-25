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

    /**
     * 四件套的<b>护甲值总和</b> 与 <b>韧性总和</b>（1.20.1 里韧性按"每件都加一遍"结算，故总和 = 单件 ×4）：
     * <pre>
     *   我  = 66：单件 鞋 10 / 腿 23 / 胸 20 / 头 13，韧性单件 16.5
     *   兽  = 77：单件 鞋 12 / 腿 27 / 胸 23 / 头 15，韧性单件 19.25
     *   祂  = 88：单件 鞋 13 / 腿 31 / 胸 26 / 头 18，韧性单件 22.0
     *   三相 = 99：单件 鞋 15 / 腿 35 / 胸 30 / 头 19，韧性单件 24.75
     * </pre>
     * ⚠ 注意：原版 {@code generic.armor} 属性的取值上限是 30，因此实际减伤仍只按 30 点结算
     * （护甲条显示满格），超出的部分只在物品提示的"单件护甲值"里可见。
     */

    /** 『我』：四件套护甲 66 / 韧性 66 */
    SELF("divinebeast:self", 15, 10, 23, 20, 13, 14,
            SoundEvents.ARMOR_EQUIP_IRON, 16.5F, 0.0F,
            () -> Ingredient.of(ModItems.SELF_INGOT.get())),

    /** 『兽』：四件套护甲 77 / 韧性 77 */
    BEAST("divinebeast:beast", 7, 12, 27, 23, 15, 25,
            SoundEvents.ARMOR_EQUIP_GOLD, 19.25F, 0.0F,
            () -> Ingredient.of(ModItems.BEAST_INGOT.get())),

    /** 『祂』：四件套护甲 88 / 韧性 88 */
    HE("divinebeast:he", 37, 13, 31, 26, 18, 15,
            SoundEvents.ARMOR_EQUIP_NETHERITE, 22.0F, 0.1F,
            () -> Ingredient.of(ModItems.HE_INGOT.get())),

    /** 『三相』：三套效果合一，四件套护甲 99 / 韧性 99 */
    THREE_PHASE("divinebeast:three_phase", 45, 15, 35, 30, 19, 20,
            SoundEvents.ARMOR_EQUIP_NETHERITE, 24.75F, 0.15F,
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
