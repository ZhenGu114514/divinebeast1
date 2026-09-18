package com.divinebeast.divinebeast.item;

import com.divinebeast.divinebeast.DivineBeastMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品注册中心。
 *
 * <p>命名对照（内部 id —— 游戏内中文名，均写在语言文件里）：
 *
 * <p><b>核心饰品</b>（各自放入同名槽位 divine / beast）：
 * <pre>
 *   deity —— 『祂』       beast —— 『兽』
 * </pre>
 *
 * <p><b>祂系 · 「时刻」饰品</b>（佩戴『祂』后解锁同名槽位）：
 * <pre>
 *   nonexist_nonexist —— 不存在不存在时刻
 *   nonexist_exist    —— 不存在存在时刻
 *   maybe_exist       —— 可能存在存在时刻
 *   exist_exist       —— 已存在存在时刻
 *   impossible_nonexist —— 不可能存在不可能存在时刻
 * </pre>
 *
 * <p><b>兽系 · 「法则」饰品</b>（佩戴『兽』后解锁同名槽位）：
 * <pre>
 *   supreme —— 至高     wisdom —— 智慧     life —— 生命
 *   chaos   —— 混沌     self   —— 自我     devour —— 吞噬
 *   samsara —— 轮回
 * </pre>
 */
public final class ModItems {

    private ModItems() {
    }

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, DivineBeastMod.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DivineBeastMod.MOD_ID);

    // ------------------------------------------------------------------
    // 核心饰品（佩戴后解锁各自体系）
    // ------------------------------------------------------------------
    public static final RegistryObject<Item> DEITY =
            ITEMS.register("deity", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));
    public static final RegistryObject<Item> BEAST =
            ITEMS.register("beast", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));

    // ------------------------------------------------------------------
    // 祂系 · 「时刻」饰品（5 件）
    // ------------------------------------------------------------------
    public static final RegistryObject<Item> NONEXIST_NONEXIST =
            ITEMS.register("nonexist_nonexist", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));
    public static final RegistryObject<Item> NONEXIST_EXIST =
            ITEMS.register("nonexist_exist", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));
    public static final RegistryObject<Item> MAYBE_EXIST =
            ITEMS.register("maybe_exist", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));
    public static final RegistryObject<Item> EXIST_EXIST =
            ITEMS.register("exist_exist", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));
    public static final RegistryObject<Item> IMPOSSIBLE_NONEXIST =
            ITEMS.register("impossible_nonexist", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));

    // ------------------------------------------------------------------
    // 兽系 · 「法则」饰品（7 件）
    // ------------------------------------------------------------------
    public static final RegistryObject<Item> SUPREME =
            ITEMS.register("supreme", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));
    public static final RegistryObject<Item> WISDOM =
            ITEMS.register("wisdom", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));
    public static final RegistryObject<Item> LIFE =
            ITEMS.register("life", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));
    public static final RegistryObject<Item> CHAOS =
            ITEMS.register("chaos", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));
    public static final RegistryObject<Item> SELF =
            ITEMS.register("self", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));
    public static final RegistryObject<Item> DEVOUR =
            ITEMS.register("devour", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));
    public static final RegistryObject<Item> SAMSARA =
            ITEMS.register("samsara", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));

    // ------------------------------------------------------------------
    // 证悟系 · 进阶链（祂者初 → 救赎 → 祂者极 → 本心）
    // ------------------------------------------------------------------
    public static final RegistryObject<Item> HE_FIRST =
            ITEMS.register("he_first", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));   // 祂者初
    public static final RegistryObject<Item> PERCEPTION =
            ITEMS.register("perception", () -> new DivineBeastItem(DivineBeastItem.curioProperties())); // 感知
    public static final RegistryObject<Item> CONSCIOUSNESS =
            ITEMS.register("consciousness", () -> new DivineBeastItem(DivineBeastItem.curioProperties())); // 意识
    public static final RegistryObject<Item> REBELLION =
            ITEMS.register("rebellion", () -> new DivineBeastItem(DivineBeastItem.curioProperties())); // 反叛
    public static final RegistryObject<Item> REDEMPTION =
            ITEMS.register("redemption", () -> new DivineBeastItem(DivineBeastItem.curioProperties())); // 救赎（阶段物品）
    public static final RegistryObject<Item> HE_EXTREME =
            ITEMS.register("he_extreme", () -> new DivineBeastItem(DivineBeastItem.curioProperties())); // 祂者极
    public static final RegistryObject<Item> GRIEF =
            ITEMS.register("grief", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));     // 悲
    public static final RegistryObject<Item> PAIN =
            ITEMS.register("pain", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));     // 伤
    public static final RegistryObject<Item> TRUE_HEART =
            ITEMS.register("true_heart", () -> new DivineBeastItem(DivineBeastItem.curioProperties())); // 本心
    public static final RegistryObject<Item> HE_TRUE =
            ITEMS.register("he_true", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));    // 真者祂

    // ------------------------------------------------------------------
    // 懦弱的抉择（安全饰品，装备在 Curios 通用 curio 槽）
    // ------------------------------------------------------------------
    /** 懦弱的抉择：进入世界即自动获得并装备，装备期间无效『祂』与『兽』的一切诅咒负面 */
    public static final RegistryObject<Item> COWARD_CHOICE =
            ITEMS.register("coward_choice", () -> new DivineBeastItem(DivineBeastItem.curioProperties()));

    // ------------------------------------------------------------------
    // 三个中立 boss 的材料体系：同名方块 → 锭 → 武器 / 盔甲
    // （外观分别借用 铁 / 金 / 下界合金 的原版贴图与盔甲材质，不额外做美术资源）
    // ------------------------------------------------------------------
    public static final RegistryObject<Item> SELF_BLOCK_ITEM = ITEMS.register("self_block",
            () -> new BlockItem(com.divinebeast.divinebeast.block.ModBlocks.SELF_BLOCK.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> BEAST_BLOCK_ITEM = ITEMS.register("beast_block",
            () -> new BlockItem(com.divinebeast.divinebeast.block.ModBlocks.BEAST_BLOCK.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> HE_BLOCK_ITEM = ITEMS.register("he_block",
            () -> new BlockItem(com.divinebeast.divinebeast.block.ModBlocks.HE_BLOCK.get(),
                    new Item.Properties()));

    public static final RegistryObject<Item> SELF_INGOT =
            ITEMS.register("self_ingot", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BEAST_INGOT =
            ITEMS.register("beast_ingot", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> HE_INGOT =
            ITEMS.register("he_ingot", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> SELF_SWORD = ITEMS.register("self_sword",
            () -> new SwordItem(Tiers.IRON, 3, -2.4F, new Item.Properties()));
    public static final RegistryObject<Item> BEAST_SWORD = ITEMS.register("beast_sword",
            () -> new SwordItem(Tiers.GOLD, 3, -2.4F, new Item.Properties()));
    public static final RegistryObject<Item> HE_SWORD = ITEMS.register("he_sword",
            () -> new SwordItem(Tiers.NETHERITE, 3, -2.4F, new Item.Properties()));

    public static final RegistryObject<Item> SELF_HELMET = ITEMS.register("self_helmet",
            () -> new ArmorItem(ModArmorMaterials.SELF, ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> SELF_CHESTPLATE = ITEMS.register("self_chestplate",
            () -> new ArmorItem(ModArmorMaterials.SELF, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> SELF_LEGGINGS = ITEMS.register("self_leggings",
            () -> new ArmorItem(ModArmorMaterials.SELF, ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> SELF_BOOTS = ITEMS.register("self_boots",
            () -> new ArmorItem(ModArmorMaterials.SELF, ArmorItem.Type.BOOTS, new Item.Properties()));

    public static final RegistryObject<Item> BEAST_HELMET = ITEMS.register("beast_helmet",
            () -> new ArmorItem(ModArmorMaterials.BEAST, ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> BEAST_CHESTPLATE = ITEMS.register("beast_chestplate",
            () -> new ArmorItem(ModArmorMaterials.BEAST, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> BEAST_LEGGINGS = ITEMS.register("beast_leggings",
            () -> new ArmorItem(ModArmorMaterials.BEAST, ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> BEAST_BOOTS = ITEMS.register("beast_boots",
            () -> new ArmorItem(ModArmorMaterials.BEAST, ArmorItem.Type.BOOTS, new Item.Properties()));

    public static final RegistryObject<Item> HE_HELMET = ITEMS.register("he_helmet",
            () -> new ArmorItem(ModArmorMaterials.HE, ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> HE_CHESTPLATE = ITEMS.register("he_chestplate",
            () -> new ArmorItem(ModArmorMaterials.HE, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> HE_LEGGINGS = ITEMS.register("he_leggings",
            () -> new ArmorItem(ModArmorMaterials.HE, ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> HE_BOOTS = ITEMS.register("he_boots",
            () -> new ArmorItem(ModArmorMaterials.HE, ArmorItem.Type.BOOTS, new Item.Properties()));

    // ------------------------------------------------------------------
    // 三相：三锭合一 → 三相锭 → 三相武器 / 三相盔甲
    // 「三相」装备拥有三套下级（我 / 兽 / 祂）的全部效果
    // ------------------------------------------------------------------
    public static final RegistryObject<Item> THREE_PHASE_INGOT =
            ITEMS.register("three_phase_ingot", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> THREE_PHASE_SWORD = ITEMS.register("three_phase_sword",
            () -> new SwordItem(Tiers.NETHERITE, 5, -2.2F, new Item.Properties()));
    public static final RegistryObject<Item> THREE_PHASE_HELMET = ITEMS.register("three_phase_helmet",
            () -> new ArmorItem(ModArmorMaterials.THREE_PHASE, ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> THREE_PHASE_CHESTPLATE = ITEMS.register("three_phase_chestplate",
            () -> new ArmorItem(ModArmorMaterials.THREE_PHASE, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> THREE_PHASE_LEGGINGS = ITEMS.register("three_phase_leggings",
            () -> new ArmorItem(ModArmorMaterials.THREE_PHASE, ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> THREE_PHASE_BOOTS = ITEMS.register("three_phase_boots",
            () -> new ArmorItem(ModArmorMaterials.THREE_PHASE, ArmorItem.Type.BOOTS, new Item.Properties()));

    // 三相工具全套（自有材质：三色棱镜钢 + 三色光点）
    public static final RegistryObject<Item> THREE_PHASE_PICKAXE = ITEMS.register("three_phase_pickaxe",
            () -> new PickaxeItem(Tiers.NETHERITE, 2, -2.8F, new Item.Properties()));
    public static final RegistryObject<Item> THREE_PHASE_AXE = ITEMS.register("three_phase_axe",
            () -> new AxeItem(Tiers.NETHERITE, 7.0F, -3.0F, new Item.Properties()));
    public static final RegistryObject<Item> THREE_PHASE_SHOVEL = ITEMS.register("three_phase_shovel",
            () -> new ShovelItem(Tiers.NETHERITE, 2.5F, -3.0F, new Item.Properties()));
    public static final RegistryObject<Item> THREE_PHASE_HOE = ITEMS.register("three_phase_hoe",
            () -> new HoeItem(Tiers.NETHERITE, -1, -1.0F, new Item.Properties()));

    // ------------------------------------------------------------------
    // 同名工具全套（镐 / 斧 / 锹 / 锄）—— 用各自的锭合成
    // ------------------------------------------------------------------
    public static final RegistryObject<Item> SELF_PICKAXE = ITEMS.register("self_pickaxe",
            () -> new PickaxeItem(Tiers.IRON, 1, -2.8F, new Item.Properties()));
    public static final RegistryObject<Item> SELF_AXE = ITEMS.register("self_axe",
            () -> new AxeItem(Tiers.IRON, 6.0F, -3.0F, new Item.Properties()));
    public static final RegistryObject<Item> SELF_SHOVEL = ITEMS.register("self_shovel",
            () -> new ShovelItem(Tiers.IRON, 1.5F, -3.0F, new Item.Properties()));
    public static final RegistryObject<Item> SELF_HOE = ITEMS.register("self_hoe",
            () -> new HoeItem(Tiers.IRON, -2, -1.0F, new Item.Properties()));

    public static final RegistryObject<Item> BEAST_PICKAXE = ITEMS.register("beast_pickaxe",
            () -> new PickaxeItem(Tiers.GOLD, 1, -2.8F, new Item.Properties()));
    public static final RegistryObject<Item> BEAST_AXE = ITEMS.register("beast_axe",
            () -> new AxeItem(Tiers.GOLD, 6.0F, -3.0F, new Item.Properties()));
    public static final RegistryObject<Item> BEAST_SHOVEL = ITEMS.register("beast_shovel",
            () -> new ShovelItem(Tiers.GOLD, 1.5F, -3.0F, new Item.Properties()));
    public static final RegistryObject<Item> BEAST_HOE = ITEMS.register("beast_hoe",
            () -> new HoeItem(Tiers.GOLD, -2, -1.0F, new Item.Properties()));

    public static final RegistryObject<Item> HE_PICKAXE = ITEMS.register("he_pickaxe",
            () -> new PickaxeItem(Tiers.NETHERITE, 1, -2.8F, new Item.Properties()));
    public static final RegistryObject<Item> HE_AXE = ITEMS.register("he_axe",
            () -> new AxeItem(Tiers.NETHERITE, 6.0F, -3.0F, new Item.Properties()));
    public static final RegistryObject<Item> HE_SHOVEL = ITEMS.register("he_shovel",
            () -> new ShovelItem(Tiers.NETHERITE, 1.5F, -3.0F, new Item.Properties()));
    public static final RegistryObject<Item> HE_HOE = ITEMS.register("he_hoe",
            () -> new HoeItem(Tiers.NETHERITE, -2, -1.0F, new Item.Properties()));

    // ------------------------------------------------------------------
    // 建筑的台阶 / 楼梯 / 墙（三种材料各一套）
    // ------------------------------------------------------------------
    public static final RegistryObject<Item> SELF_STAIRS_ITEM = ITEMS.register("self_stairs",
            () -> new BlockItem(com.divinebeast.divinebeast.block.ModBlocks.SELF_STAIRS.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> SELF_SLAB_ITEM = ITEMS.register("self_slab",
            () -> new BlockItem(com.divinebeast.divinebeast.block.ModBlocks.SELF_SLAB.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> SELF_WALL_ITEM = ITEMS.register("self_wall",
            () -> new BlockItem(com.divinebeast.divinebeast.block.ModBlocks.SELF_WALL.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> BEAST_STAIRS_ITEM = ITEMS.register("beast_stairs",
            () -> new BlockItem(com.divinebeast.divinebeast.block.ModBlocks.BEAST_STAIRS.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> BEAST_SLAB_ITEM = ITEMS.register("beast_slab",
            () -> new BlockItem(com.divinebeast.divinebeast.block.ModBlocks.BEAST_SLAB.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> BEAST_WALL_ITEM = ITEMS.register("beast_wall",
            () -> new BlockItem(com.divinebeast.divinebeast.block.ModBlocks.BEAST_WALL.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> HE_STAIRS_ITEM = ITEMS.register("he_stairs",
            () -> new BlockItem(com.divinebeast.divinebeast.block.ModBlocks.HE_STAIRS.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> HE_SLAB_ITEM = ITEMS.register("he_slab",
            () -> new BlockItem(com.divinebeast.divinebeast.block.ModBlocks.HE_SLAB.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> HE_WALL_ITEM = ITEMS.register("he_wall",
            () -> new BlockItem(com.divinebeast.divinebeast.block.ModBlocks.HE_WALL.get(),
                    new Item.Properties()));

    // ------------------------------------------------------------------
    // 指路罗盘：指南针 + 一圈 8 个对应物品（我=绿宝石 / 兽=熟猪排 / 祂=泥土）
    // ------------------------------------------------------------------
    public static final RegistryObject<Item> SELF_COMPASS = ITEMS.register("self_compass",
            () -> new BossCompassItem(com.divinebeast.divinebeast.boss.DivineBoss.Kind.SELF,
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BEAST_COMPASS = ITEMS.register("beast_compass",
            () -> new BossCompassItem(com.divinebeast.divinebeast.boss.DivineBoss.Kind.BEAST,
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HE_COMPASS = ITEMS.register("he_compass",
            () -> new BossCompassItem(com.divinebeast.divinebeast.boss.DivineBoss.Kind.HE,
                    new Item.Properties().stacksTo(1)));

    // ------------------------------------------------------------------
    // 三个中立 boss 的刷怪蛋（野外那三只由 BossArena 自动在 1000 格外召唤；
    // 刷怪蛋只是为了方便你随时手动召唤来测试，不会触发清场铺地）
    // ------------------------------------------------------------------
    public static final RegistryObject<Item> SELF_BOSS_SPAWN_EGG =
            ITEMS.register("self_boss_spawn_egg", () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    com.divinebeast.divinebeast.boss.ModEntities.SELF_BOSS,
                    0x3FA34D, 0xB9F6CA, new Item.Properties()));
    public static final RegistryObject<Item> BEAST_BOSS_SPAWN_EGG =
            ITEMS.register("beast_boss_spawn_egg", () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    com.divinebeast.divinebeast.boss.ModEntities.BEAST_BOSS,
                    0xC1440E, 0xFFD8B0, new Item.Properties()));
    public static final RegistryObject<Item> HE_BOSS_SPAWN_EGG =
            ITEMS.register("he_boss_spawn_egg", () -> new net.minecraftforge.common.ForgeSpawnEggItem(
                    com.divinebeast.divinebeast.boss.ModEntities.HE_BOSS,
                    0x8B5A2B, 0xE8D8B0, new Item.Properties()));

    // ------------------------------------------------------------------
    // 全部物品（用于创造模式标签等）
    // ------------------------------------------------------------------
    /** 祂系「时刻」饰品（顺序与槽位/效果索引一一对应：nonexist_nonexist…impossible_nonexist） */
    public static final List<RegistryObject<Item>> MOMENT_ITEMS = List.of(
            NONEXIST_NONEXIST, NONEXIST_EXIST, MAYBE_EXIST, EXIST_EXIST, IMPOSSIBLE_NONEXIST);

    public static final List<RegistryObject<Item>> ALL_ITEMS = List.of(
            DEITY, BEAST,
            NONEXIST_NONEXIST, NONEXIST_EXIST, MAYBE_EXIST, EXIST_EXIST, IMPOSSIBLE_NONEXIST,
            SUPREME, WISDOM, LIFE, CHAOS, SELF, DEVOUR, SAMSARA,
            HE_FIRST, PERCEPTION, CONSCIOUSNESS, REBELLION, REDEMPTION, HE_EXTREME, GRIEF, PAIN, TRUE_HEART,
            HE_TRUE,
            COWARD_CHOICE,
            SELF_BLOCK_ITEM, BEAST_BLOCK_ITEM, HE_BLOCK_ITEM,
            SELF_INGOT, BEAST_INGOT, HE_INGOT,
            SELF_SWORD, BEAST_SWORD, HE_SWORD,
            SELF_HELMET, SELF_CHESTPLATE, SELF_LEGGINGS, SELF_BOOTS,
            BEAST_HELMET, BEAST_CHESTPLATE, BEAST_LEGGINGS, BEAST_BOOTS,
            HE_HELMET, HE_CHESTPLATE, HE_LEGGINGS, HE_BOOTS,
            THREE_PHASE_INGOT, THREE_PHASE_SWORD,
            THREE_PHASE_HELMET, THREE_PHASE_CHESTPLATE, THREE_PHASE_LEGGINGS, THREE_PHASE_BOOTS,
            THREE_PHASE_PICKAXE, THREE_PHASE_AXE, THREE_PHASE_SHOVEL, THREE_PHASE_HOE,
            SELF_PICKAXE, SELF_AXE, SELF_SHOVEL, SELF_HOE,
            BEAST_PICKAXE, BEAST_AXE, BEAST_SHOVEL, BEAST_HOE,
            HE_PICKAXE, HE_AXE, HE_SHOVEL, HE_HOE,
            SELF_STAIRS_ITEM, SELF_SLAB_ITEM, SELF_WALL_ITEM,
            BEAST_STAIRS_ITEM, BEAST_SLAB_ITEM, BEAST_WALL_ITEM,
            HE_STAIRS_ITEM, HE_SLAB_ITEM, HE_WALL_ITEM,
            SELF_COMPASS, BEAST_COMPASS, HE_COMPASS,
            SELF_BOSS_SPAWN_EGG, BEAST_BOSS_SPAWN_EGG, HE_BOSS_SPAWN_EGG);

    public static final RegistryObject<CreativeModeTab> TAB =
            TABS.register("divinebeast_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.divinebeast"))
                    .icon(() -> new ItemStack(DEITY.get()))
                    .displayItems((parameters, output) ->
                            ALL_ITEMS.forEach(holder -> output.accept(new ItemStack(holder.get()))))
                    .build());

    /** 挂载两个 DeferredRegister 到 mod 事件总线（在 @Mod 构造函数中调用）。 */
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
