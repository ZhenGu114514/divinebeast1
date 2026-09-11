package com.divinebeast.divinebeast.item;

import com.divinebeast.divinebeast.DivineBeastMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
            COWARD_CHOICE);

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
