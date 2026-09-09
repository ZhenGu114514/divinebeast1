package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.net.AscensionChoiceMessage;
import com.divinebeast.divinebeast.net.Networking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 证悟死亡抉择界面：佩戴 救赎 →「自我救赎」；佩戴 本心 →「找回自我」。
 * 提供「普通重生」作为不迁移的备选。
 */
public class AscensionDeathScreen extends Screen {

    private final int kind;

    public AscensionDeathScreen(int kind) {
        super(Component.translatable("divinebeast.death.title"));
        this.kind = kind;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        Component main = AscensionDeathClient.mainButton(kind);
        this.addRenderableWidget(Button.builder(main, b -> choose(kind))
                .bounds(cx - 110, cy - 24, 220, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("divinebeast.death.normal"), b -> choose(0))
                .bounds(cx - 110, cy + 4, 220, 20).build());
    }

    private void choose(int choice) {
        Networking.sendToServer(new AscensionChoiceMessage(choice));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        int cx = this.width / 2;
        int cy = this.height / 2;
        gui.drawCenteredString(this.font, Component.translatable("divinebeast.death.title")
                .withStyle(ChatFormatting.DARK_PURPLE), cx, cy - 60, 0xFFFFFF);
        gui.drawCenteredString(this.font, Component.translatable("divinebeast.death.hint")
                .withStyle(ChatFormatting.GRAY), cx, cy - 40, 0xFFFFFF);
        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
