package com.divinebeast.divinebeast.item;

import com.divinebeast.divinebeast.CompatChecks;
import com.divinebeast.divinebeast.curio.CuriosCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 本模组饰品的基础物品类。
 *
 * <p>注意：此类<b>不</b>直接引用任何 Curios 类 —— 即使没有安装 Curios，
 * 所有物品也能正常注册、合成、堆叠，只是无法放入 Curios 槽位、也没有
 * Curios 侧的联动逻辑（联动全部收敛在 CuriosCompat / CuriosEffects 中，
 * 二者都只会在 Curios 存在时被加载）。
 *
 * <p>后续某件饰品需要专属行为（右键功能、特殊 NBT 等）时，直接派生
 * 新的子类，并在 ModItems 里把注册改为该子类。
 */
public class DivineBeastItem extends Item {

    public DivineBeastItem(Properties properties) {
        super(properties);
    }

    /** 饰品通用属性：单件不可堆叠 */
    public static Properties curioProperties() {
        return new Item.Properties().stacksTo(1);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        // 动态 tooltip 只在客户端 + Curios 已安装时启用；分支外的引用不会在
        // 未安装 Curios 或服务端被解析，因此不会触发类加载错误。
        if (level != null && level.isClientSide && CompatChecks.curiosLoaded()) {
            if (stack.is(ModItems.DEITY.get())) {
                CuriosCompat.appendDeityTooltip(stack, tooltip);
            } else if (stack.is(ModItems.BEAST.get())) {
                CuriosCompat.appendBeastTooltip(stack, tooltip);
            }
        }
        // 时刻饰品的文本描述（lore，纯本地化、可离线显示）
        if (stack.is(ModItems.NONEXIST_NONEXIST.get())) {
            tooltip.add(Component.translatable("item.divinebeast.nonexist_nonexist.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.NONEXIST_EXIST.get())) {
            tooltip.add(Component.translatable("item.divinebeast.nonexist_exist.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.MAYBE_EXIST.get())) {
            tooltip.add(Component.translatable("item.divinebeast.maybe_exist.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.EXIST_EXIST.get())) {
            tooltip.add(Component.translatable("item.divinebeast.exist_exist.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.IMPOSSIBLE_NONEXIST.get())) {
            tooltip.add(Component.translatable("item.divinebeast.impossible_nonexist.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.SUPREME.get())) {
            tooltip.add(Component.translatable("item.divinebeast.supreme.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.WISDOM.get())) {
            tooltip.add(Component.translatable("item.divinebeast.wisdom.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.LIFE.get())) {
            tooltip.add(Component.translatable("item.divinebeast.life.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.CHAOS.get())) {
            tooltip.add(Component.translatable("item.divinebeast.chaos.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.SELF.get())) {
            tooltip.add(Component.translatable("item.divinebeast.self.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.DEVOUR.get())) {
            tooltip.add(Component.translatable("item.divinebeast.devour.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.SAMSARA.get())) {
            tooltip.add(Component.translatable("item.divinebeast.samsara.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.HE_FIRST.get())) {
            tooltip.add(Component.translatable("item.divinebeast.he_first.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.PERCEPTION.get())) {
            tooltip.add(Component.translatable("item.divinebeast.perception.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.CONSCIOUSNESS.get())) {
            tooltip.add(Component.translatable("item.divinebeast.consciousness.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.REBELLION.get())) {
            tooltip.add(Component.translatable("item.divinebeast.rebellion.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.REDEMPTION.get())) {
            tooltip.add(Component.translatable("item.divinebeast.redemption.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.HE_EXTREME.get())) {
            tooltip.add(Component.translatable("item.divinebeast.he_extreme.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.GRIEF.get())) {
            tooltip.add(Component.translatable("item.divinebeast.grief.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.PAIN.get())) {
            tooltip.add(Component.translatable("item.divinebeast.pain.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.TRUE_HEART.get())) {
            tooltip.add(Component.translatable("item.divinebeast.true_heart.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else if (stack.is(ModItems.HE_TRUE.get())) {
            tooltip.add(Component.translatable("item.divinebeast.he_true.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        // 效果描述：非核心物品始终显示；核心物品由 CuriosCompat 按阶段动态显示
        //（无 Curios 时用通用描述兜底，避免诅咒阶段剧透后续阶段效果）
        boolean dynamicCore = level != null && level.isClientSide && CompatChecks.curiosLoaded()
                && (stack.is(ModItems.DEITY.get()) || stack.is(ModItems.BEAST.get()));
        // 证悟五阶段饰品：正常只显示文本(lore)，按住 Shift 才显示效果描述
        boolean stageTrinket = stack.is(ModItems.HE_FIRST.get())
                || stack.is(ModItems.REDEMPTION.get())
                || stack.is(ModItems.HE_EXTREME.get())
                || stack.is(ModItems.TRUE_HEART.get())
                || stack.is(ModItems.HE_TRUE.get());
        if (!dynamicCore) {
            boolean shiftDown = level != null && level.isClientSide
                    && net.minecraft.client.gui.screens.Screen.hasShiftDown();
            if (stageTrinket && !shiftDown) {
                tooltip.add(Component.translatable("divinebeast.tooltip.shift_hint")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            } else {
                tooltip.add(Component.translatable(stack.getDescriptionId() + ".desc")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
