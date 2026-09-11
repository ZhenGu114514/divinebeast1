package com.divinebeast.divinebeast.item;

import com.divinebeast.divinebeast.CompatChecks;
import com.divinebeast.divinebeast.curio.CuriosCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
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

    /** 真者『祂』的正面权能数量（lore/effect 文案行数与此一致） */
    private static final int HE_TRUE_EFFECT_COUNT = 99;
    /** 翻页间隔（毫秒）：每 2 秒轮换到下一页 */
    private static final long HE_TRUE_PAGE_INTERVAL_MS = 2000L;
    /** tooltip 单行预留高度（像素，含行距） */
    private static final int HE_TRUE_LINE_H = 10;
    /** tooltip 顶部/底部安全边距（像素） */
    private static final int HE_TRUE_SAFE_MARGIN = 36;
    /** tooltip 手动换行最大宽度（像素，与游戏内常规提示宽度相当） */
    private static final int HE_TRUE_WRAP_W = 200;

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

        // 真者『祂』专属：正常只显示诗词/叙事文本，按住 Shift 显示效果；
        // 每行颜色随秒流动（彩虹渐变，每秒整体偏移）。
        // 99 项权能过长且顶部叙事超长：每次轮换一页，每页条数按当前屏幕高度
        // 自动计算（顶部长文占多少行，就只留能放下多少条），每 2 秒翻页、到底回首页。
        if (stack.is(ModItems.HE_TRUE.get())) {
            if (level != null && level.isClientSide) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                net.minecraft.client.gui.Font font = mc.font;
                boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                long sec = System.currentTimeMillis() / 1000L;
                int screenH = mc.getWindow().getGuiScaledHeight();
                int wrapW = Math.min(HE_TRUE_WRAP_W,
                        Math.max(120, mc.getWindow().getGuiScaledWidth() - 48));
                // 可用总行数（每行按 HE_TRUE_LINE_H 像素估，留出屏幕上下边距）
                int maxLines = Math.max(15, (screenH - HE_TRUE_SAFE_MARGIN) / HE_TRUE_LINE_H);
                // 顶部叙事在“诗词”视图出现，按住 Shift 只列效果，不占这几十行
                String headerKey = shift ? null : "item.divinebeast.he_true.lore";
                int headerLines = headerKey == null ? 0
                        : wrappedLineCount(Component.translatable(headerKey).getString(), font, wrapW);
                int footerLines = 1; // 页码
                int budget = maxLines - headerLines - footerLines;
                // 每条约 2 行（诗两句各一行/效果一般一行），据此决定每页条数
                int linesPerItem = shift ? 1 : 2;
                int pageSize = Math.max(1, budget / linesPerItem);
                int totalPages = (HE_TRUE_EFFECT_COUNT + pageSize - 1) / pageSize;
                int page = (int) ((System.currentTimeMillis() / HE_TRUE_PAGE_INTERVAL_MS)
                        % totalPages);
                int from = page * pageSize + 1;
                int to = Math.min(HE_TRUE_EFFECT_COUNT, from + pageSize - 1);
                int row = 0;
                // 顶部超长叙事：按字体宽度拆成多行逐行取色（行宽受控，避免挤出屏幕）
                if (headerKey != null) {
                    row = addWrappedHueText(tooltip,
                            Component.translatable(headerKey).getString(), row, sec, true, font, wrapW,
                            maxLines - footerLines);
                }
                int usedRows = row;
                if (!shift) {
                    for (int i = from; i <= to; i++) {
                        int need = wrappedLineCount(
                                Component.translatable("item.divinebeast.he_true.lore_" + i).getString(),
                                font, wrapW);
                        if (usedRows + need > maxLines - footerLines) {
                            break; // 剩余行不够，等下一轮
                        }
                        row = addWrappedHueText(tooltip,
                                Component.translatable("item.divinebeast.he_true.lore_" + i).getString(),
                                row, sec, false, font, wrapW, maxLines - footerLines);
                        usedRows = row;
                    }
                    if (usedRows < maxLines - footerLines) {
                        addHueLine(tooltip, "divinebeast.tooltip.shift_hint", row, sec, false);
                    }
                } else {
                    for (int i = from; i <= to; i++) {
                        int need = wrappedLineCount(
                                Component.translatable("item.divinebeast.he_true.effect_" + i).getString(),
                                font, wrapW);
                        if (usedRows + need > maxLines - footerLines) {
                            break;
                        }
                        row = addWrappedHueText(tooltip,
                                Component.translatable("item.divinebeast.he_true.effect_" + i).getString(),
                                row, sec, false, font, wrapW, maxLines - footerLines);
                        usedRows = row;
                    }
                }
                // 页码提示（永远保留在最后一行）
                tooltip.add(Component.translatable("divinebeast.tooltip.he_true_page",
                        page + 1, totalPages)
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            } else {
                // 服务端等非渲染场景：普通灰色文本兜底，不引客户端类
                tooltip.add(Component.translatable("item.divinebeast.he_true.desc")
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }

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
        } else if (stack.is(ModItems.COWARD_CHOICE.get())) {
            tooltip.add(Component.translatable("item.divinebeast.coward_choice.lore")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        // 效果描述：非核心物品始终显示；核心物品由 CuriosCompat 按阶段动态显示
        //（无 Curios 时用通用描述兜底，避免诅咒阶段剧透后续阶段效果）
        boolean dynamicCore = level != null && level.isClientSide && CompatChecks.curiosLoaded()
                && (stack.is(ModItems.DEITY.get()) || stack.is(ModItems.BEAST.get()));
        // 证悟五阶段饰品（真者祂已单独处理）：正常只显示文本(lore)，按住 Shift 才显示效果描述
        boolean stageTrinket = stack.is(ModItems.HE_FIRST.get())
                || stack.is(ModItems.REDEMPTION.get())
                || stack.is(ModItems.HE_EXTREME.get())
                || stack.is(ModItems.TRUE_HEART.get());
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

    /**
     * 按“行号 + 秒”滚动色相为一行 text 上色：行间相差 24°，每秒整体前移 18°。
     * 纯文本(lore)用斜体，效果行不加斜体，方便区分。
     */
    private static void addHueLine(List<Component> tooltip, String key, int row, long sec, boolean italic) {
        addHueText(tooltip, Component.translatable(key).getString(), row, sec, italic);
    }

    /**
     * 按字体宽度把一段可含 \n 的文本拆成“渲染后实际行数”：
     * 先按 \n 分段，每段再按 wrapW 像素估算需要折行几次（用于行数预算）。
     */
    private static int wrappedLineCount(String text, net.minecraft.client.gui.Font font, int wrapW) {
        int lines = 0;
        for (String seg : text.split("\n", -1)) {
            if (!seg.isEmpty()) {
                lines += Math.max(1, (font.width(seg) + wrapW - 1) / wrapW);
            }
        }
        return Math.max(1, lines);
    }

    /**
     * 把一段（可能超长的）文本按字体宽度拆成多行逐行加入 tooltip 并取色。
     * limitRows > 0 时若超出限高则截断。返回下一个可用行号。
     */
    private static int addWrappedHueText(List<Component> tooltip, String text, int startRow,
                                         long sec, boolean italic,
                                         net.minecraft.client.gui.Font font, int wrapW,
                                         int limitRows) {
        int row = startRow;
        for (String seg : text.split("\n", -1)) {
            if (seg.isEmpty()) {
                continue;
            }
            seg = seg.trim();
            int width = font.width(seg);
            if (width <= wrapW) {
                if (limitRows > 0 && row >= limitRows) {
                    return row;
                }
                addHueText(tooltip, seg, row++, sec, italic);
            } else {
                // 逐段截断到 wrapW，保持按“行号”连续取色
                StringBuilder cur = new StringBuilder();
                for (int i = 0; i < seg.length(); i++) {
                    if (limitRows > 0 && row >= limitRows) {
                        return row;
                    }
                    cur.append(seg.charAt(i));
                    if (font.width(cur.toString()) > wrapW) {
                        cur.deleteCharAt(cur.length() - 1);
                        if (cur.length() > 0) {
                            addHueText(tooltip, cur.toString(), row++, sec, italic);
                        }
                        cur = new StringBuilder(String.valueOf(seg.charAt(i)));
                    }
                }
                if (cur.length() > 0 && (limitRows <= 0 || row < limitRows)) {
                    addHueText(tooltip, cur.toString(), row++, sec, italic);
                }
            }
        }
        return row;
    }

    private static void addHueText(List<Component> tooltip, String text, int row, long sec, boolean italic) {
        MutableComponent line = Component.literal(text);
        int hue = (int) (((sec * 18L) + (row * 24L)) % 360L);
        int color = hsvToRgb(hue, 0.85F, 1.0F);
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(color));
        if (italic) {
            style = style.withItalic(true);
        }
        tooltip.add(line.withStyle(style));
    }

    /** HSV → 0xRRGGBB（仅数值计算，避免依赖 AWT，服务端安全）。 */
    private static int hsvToRgb(int hue, float sat, float val) {
        float h = ((hue % 360) + 360) % 360 / 360.0F;
        int hi = (int) Math.floor(h * 6.0F) % 6;
        float f = h * 6.0F - (int) Math.floor(h * 6.0F);
        float p = val * (1 - sat);
        float q = val * (1 - f * sat);
        float t = val * (1 - (1 - f) * sat);
        float r, g, b;
        switch (hi) {
            case 0 -> { r = val; g = t; b = p; }
            case 1 -> { r = q; g = val; b = p; }
            case 2 -> { r = p; g = val; b = t; }
            case 3 -> { r = p; g = q; b = val; }
            case 4 -> { r = t; g = p; b = val; }
            default -> { r = val; g = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
