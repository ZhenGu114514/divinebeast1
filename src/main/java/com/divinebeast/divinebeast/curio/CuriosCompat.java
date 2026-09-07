package com.divinebeast.divinebeast.curio;

import com.divinebeast.divinebeast.item.ModItems;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;

import java.util.List;
import java.util.UUID;

/**
 * Curios 联动（可选前置集成）。
 *
 * <p><b>加载时机</b>：本类包含大量 Curios API 引用，只能由
 * {@code DivineBeastMod} 在确认 Curios 已安装后调用 {@link #register()} 触发加载。
 * Curios 未安装时本类永远不会被加载，因此不会出现 NoClassDefFoundError。
 *
 * <p><b>职责</b>：给核心饰品『祂』(deity) 与『兽』(beast) 挂上 Curios 的
 * {@link ICurio} 能力，并通过 {@code getAttributeModifiers} 返回"槽位修饰"：
 * 佩戴期间为其体系的每个隐藏槽位（默认 0 格，见 data 下 slots/*.json）临时 +1，
 * 卸下后槽位自动收回。
 */
public final class CuriosCompat {

    private static final Logger LOGGER = LogManager.getLogger();

    /** 佩戴『祂』(deity) 时解锁的槽位（与 slots/*.json、entities、tag、lang 一一对应） */
    private static final String[] DEITY_UNLOCKED_SLOTS = {
            "nonexist_nonexist", "nonexist_exist", "maybe_exist", "exist_exist", "impossible_nonexist"
    };

    /** 佩戴『兽』(beast) 时解锁的槽位 */
    private static final String[] BEAST_UNLOCKED_SLOTS = {
            "supreme", "wisdom", "life", "chaos", "self", "devour", "samsara"
    };

    private CuriosCompat() {
    }

    /** 仅当 Curios 已安装时被调用。注册 ItemStack 能力附加事件（泛型事件须用 addGenericListener）。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, CuriosCompat::onAttachCapabilities);
        LOGGER.info("[divinebeast] Curios 联动已启用：佩戴『祂』『兽』将按佩戴状态动态解锁/收回槽位。");
    }

    /**
     * 『祂』物品的动态提示（仅客户端调用）：
     * 未集齐 → 诅咒形态正文 + 仍生效的时刻诅咒行（已佩戴的诅咒行直接消失）；
     * 集齐 → 救赎形态文本（加『』彩色）。
     */
    public static void appendDeityTooltip(ItemStack stack, List<Component> tooltip) {
        if (!stack.is(ModItems.DEITY.get())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Player player = minecraft.player;
        int progress = Math.min(5, CuriosEffects.momentProgress(player));
        if (CuriosEffects.isPhaseTwo(player)) {
            tooltip.add(wrap("item.divinebeast.deity.lore_phase2", ChatFormatting.GOLD));
            tooltip.add(Component.translatable("divinebeast.stage.deity_redemption")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.divinebeast.deity.progress", progress, 5)
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.divinebeast.deity.lore_phase1")
                .withStyle(ChatFormatting.DARK_RED));
        for (int i = 0; i < 5; i++) {
            // 已佩戴同名时刻 → 该诅咒行直接消失（不再置灰）
            if (!CuriosEffects.momentWorn(player, i)) {
                tooltip.add(Component.translatable("divinebeast.tooltip.deity_curse_" + i)
                        .withStyle(ChatFormatting.RED));
            }
        }
        tooltip.add(Component.translatable("item.divinebeast.deity.progress", progress, 5)
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * 『兽』物品的动态提示（仅客户端调用）：
     * 诅咒 → 仍生效的法则诅咒行（已佩戴的行直接消失）；
     * 拯救 → 救赎正文（『』彩色），最下方仍保留『自我』诅咒文本；
     * 自·我 → 『』彩色正文（名称另由佩戴改名实现）。
     */
    public static void appendBeastTooltip(ItemStack stack, List<Component> tooltip) {
        if (!stack.is(ModItems.BEAST.get())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Player player = minecraft.player;
        int stage = BeastEffects.stageOf(player);
        int progress = BeastEffects.lawProgress(player);
        switch (stage) {
            case 1 -> {
                tooltip.add(Component.translatable("item.divinebeast.beast.lore_curse")
                        .withStyle(ChatFormatting.RED));
                for (int i = 0; i < 7; i++) {
                    // 已佩戴同名法则 → 该诅咒行直接消失（不再置灰）
                    if (!BeastEffects.lawEquipped(player, i)) {
                        tooltip.add(Component.translatable("divinebeast.tooltip.beast_curse_" + i)
                                .withStyle(ChatFormatting.RED));
                    }
                }
            }
            case 2 -> {
                tooltip.add(wrap("item.divinebeast.beast.lore_redemption", ChatFormatting.GOLD));
                tooltip.add(Component.translatable("divinebeast.stage.beast_salvation")
                        .withStyle(ChatFormatting.GRAY));
                // 最下方：『自我』的文本仍存在
                tooltip.add(Component.translatable("divinebeast.tooltip.beast_curse_4")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            case 3 -> {
                tooltip.add(wrap("item.divinebeast.beast.lore_self", ChatFormatting.DARK_PURPLE));
                tooltip.add(Component.translatable("divinebeast.stage.beast_self")
                        .withStyle(ChatFormatting.GRAY));
            }
            default -> {
                // 未佩戴『兽』不展示阶段文本
            }
        }
        if (stage != 0) {
            tooltip.add(Component.translatable("item.divinebeast.beast.progress", progress, 7)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** 用 『』 包裹翻译文本并上色 */
    private static Component wrap(String key, ChatFormatting color) {
        return Component.literal("『").withStyle(color)
                .append(Component.translatable(key).withStyle(color))
                .append(Component.literal("』").withStyle(color));
    }

    private static void onAttachCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        Item item = stack.getItem();
        if (item != ModItems.DEITY.get() && item != ModItems.BEAST.get()) {
            return;
        }
        event.addCapability(CuriosCapability.ID_ITEM, CuriosApi.createCurioProvider(new SlotUnlockCurio(stack)));
    }

    /**
     * 佩戴期间为指定槽位各 +1 格、卸下即收回的 Curio 实现。
     * 槽位本身在 data 中以 size=0 注册并已挂到玩家实体，因此平时不可见，
     * 仅当佩戴对应核心饰品时出现。
     */
    private static final class SlotUnlockCurio implements ICurio {

        private final ItemStack stack;

        private SlotUnlockCurio(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public ItemStack getStack() {
            return stack;
        }

        @Override
        public boolean canUnequip(SlotContext slotContext) {
            // 绑定诅咒：『祂』『兽』一经佩戴无法从饰品栏卸下
            return false;
        }

        @Override
        public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid) {
            Multimap<Attribute, AttributeModifier> modifiers = LinkedHashMultimap.create();
            Item item = stack.getItem();
            String[] unlockedSlots = null;
            if (item == ModItems.DEITY.get()) {
                unlockedSlots = DEITY_UNLOCKED_SLOTS;
            } else if (item == ModItems.BEAST.get()) {
                unlockedSlots = BEAST_UNLOCKED_SLOTS;
            }
            if (unlockedSlots != null) {
                for (String slotId : unlockedSlots) {
                    // amount=1：佩戴期间为每个槽位临时增加 1 格；卸下自动移除
                    CuriosApi.addSlotModifier(modifiers, slotId, uuid, 1.0D, AttributeModifier.Operation.ADDITION);
                }
            }
            return modifiers;
        }
    }
}
