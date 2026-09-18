package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.boss.DivineBoss;
import com.divinebeast.divinebeast.net.BossTradeRequestMessage;
import com.divinebeast.divinebeast.net.Networking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 中立 boss 的交易界面（空手右键打开）。
 *
 * <p>列出该 boss 的全部交易项 —— 第一行是主交易（16 货币 → 3 同名方块），
 * 『兽』额外多一行"喂食"（1 熟猪排 → 还需吸取 -20）。点一行就把请求发给服务端结算，
 * 冷却 / 数量不足等判定全部在服务端做，客户端只负责显示。
 *
 * <p>界面不暂停游戏（{@code isPauseScreen = false}），所以战斗中可以随时开着交易。
 */
public class BossTradeScreen extends Screen {

    private static final int PANEL_W = 224;
    private static final int PANEL_H = 118;
    private static final int ROW_H = 26;

    private final DivineBoss boss;
    private final int rows;
    private int left;
    private int top;

    public BossTradeScreen(DivineBoss boss) {
        super(Component.translatable("divinebeast.trade.title", boss.getDisplayName()));
        this.boss = boss;
        this.rows = boss.kind() == DivineBoss.Kind.BEAST ? 2 : 1;
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        gui.fill(this.left - 1, this.top - 1, this.left + PANEL_W + 1, this.top + PANEL_H + 1, 0xFF14141A);
        gui.fill(this.left, this.top, this.left + PANEL_W, this.top + PANEL_H, 0xFF2B2B36);
        gui.drawCenteredString(this.font, this.title, this.left + PANEL_W / 2, this.top + 8, 0xFFE8E8F0);
        gui.drawString(this.font, Component.translatable("divinebeast.trade.cost"),
                this.left + 18, this.top + 24, 0xFF9A9AA8);
        gui.drawString(this.font, Component.translatable("divinebeast.trade.result"),
                this.left + 112, this.top + 24, 0xFF9A9AA8);

        for (int i = 0; i < this.rows; i++) {
            int rowY = this.top + 36 + i * ROW_H;
            boolean hovered = mouseX >= this.left + 8 && mouseX <= this.left + PANEL_W - 8
                    && mouseY >= rowY && mouseY <= rowY + ROW_H - 3;
            gui.fill(this.left + 8, rowY, this.left + PANEL_W - 8, rowY + ROW_H - 3,
                    hovered ? 0xFF3C4A6A : 0xFF23232C);
            this.drawRow(gui, i, rowY);
        }
        gui.drawCenteredString(this.font, Component.translatable("divinebeast.trade.hint"),
                this.left + PANEL_W / 2, this.top + PANEL_H - 13, 0xFF8A8A98);
        super.render(gui, mouseX, mouseY, partialTick);
    }

    private void drawRow(GuiGraphics gui, int index, int rowY) {
        int iconY = rowY + 4;
        if (index == 0) {
            ItemStack cost = new ItemStack(this.boss.kind().currency(), DivineBoss.TRADE_COST);
            ItemStack result = new ItemStack(this.boss.kind().blockItem(), DivineBoss.DROP_BLOCKS);
            gui.renderItem(cost, this.left + 18, iconY);
            gui.renderItemDecorations(this.font, cost, this.left + 18, iconY);
            gui.drawString(this.font, "→", this.left + 44, rowY + 8, 0xFFCCCCCC);
            gui.renderItem(result, this.left + 112, iconY);
            gui.renderItemDecorations(this.font, result, this.left + 112, iconY);
            gui.drawString(this.font, result.getHoverName(), this.left + 134, rowY + 8, 0xFFDDDDDD);
            return;
        }
        ItemStack cost = new ItemStack(Items.COOKED_PORKCHOP);
        gui.renderItem(cost, this.left + 18, iconY);
        gui.renderItemDecorations(this.font, cost, this.left + 18, iconY);
        gui.drawString(this.font, "→", this.left + 44, rowY + 8, 0xFFCCCCCC);
        gui.drawString(this.font, Component.translatable("divinebeast.trade.feed",
                (int) DivineBoss.BEAST_FEED_REDUCTION), this.left + 112, rowY + 8, 0xFFDDDDDD);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < this.rows; i++) {
            int rowY = this.top + 36 + i * ROW_H;
            if (mouseX >= this.left + 8 && mouseX <= this.left + PANEL_W - 8
                    && mouseY >= rowY && mouseY <= rowY + ROW_H - 3) {
                Networking.sendToServer(new BossTradeRequestMessage(this.boss.getId(), i));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
