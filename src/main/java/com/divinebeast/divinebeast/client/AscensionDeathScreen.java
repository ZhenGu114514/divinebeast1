package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.net.AscensionChoiceMessage;
import com.divinebeast.divinebeast.net.AscensionScreenMessage;
import com.divinebeast.divinebeast.net.Networking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 证悟死亡抉择界面：佩戴 救赎 →「自我救赎」；佩戴 本心 →「找回自我」。
 * 提供「普通重生」作为不迁移的备选。
 *
 * <p><b>「找回自我」（本心 → 真者『祂』）专属</b>：
 * <ul>
 *   <li>按钮下方追加一段说明：真者『祂』一旦装备<b>无法取下</b>，装备前请做好准备；</li>
 *   <li>背景不断浮现大小不一、倾斜角度各异的「你准备好了吗！」飘字。</li>
 * </ul>
 * 飘字与说明都只在 KIND_TRUE_HEART 出现（该界面才是"获得真者祂前"的最后一步）。
 *
 * <p>动画按真实时间推进（{@code Util.getMillis()}），不依赖 {@code tick()} 是否被调用，
 * 因此即使界面在不暂停的死亡状态下也能平滑播放。
 */
public class AscensionDeathScreen extends Screen {

    /** 背景飘字同时存在的上限 */
    private static final int READY_MAX = 26;
    /** 每多少毫秒生成一条飘字 */
    private static final float READY_SPAWN_MS = 170.0F;
    /** 说明文本的折行宽度（像素） */
    private static final int NOTE_WRAP_W = 300;

    private final int kind;
    /** 仅「找回自我」界面播放"你准备好了吗！"飘字与追加说明 */
    private final boolean heTrueStep;
    private final RandomSource rng = RandomSource.create();
    private final List<Floater> floaters = new ArrayList<>();

    private long lastMillis = net.minecraft.Util.getMillis();
    private float spawnAccumulator;

    public AscensionDeathScreen(int kind) {
        super(Component.translatable("divinebeast.death.title"));
        this.kind = kind;
        this.heTrueStep = kind == AscensionScreenMessage.KIND_TRUE_HEART;
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
        advanceAnimation();
        int cx = this.width / 2;
        int cy = this.height / 2;
        if (this.heTrueStep) {
            renderReadyTexts(gui);
        }
        gui.drawCenteredString(this.font, Component.translatable("divinebeast.death.title")
                .withStyle(ChatFormatting.DARK_PURPLE), cx, cy - 60, 0xFFFFFF);
        gui.drawCenteredString(this.font, Component.translatable("divinebeast.death.hint")
                .withStyle(ChatFormatting.GRAY), cx, cy - 40, 0xFFFFFF);
        super.render(gui, mouseX, mouseY, partialTick);
        if (this.heTrueStep) {
            renderHeTrueNote(gui, cx, cy);
        }
    }

    // ------------------------------------------------------------------
    // 「找回自我」界面：真者祂 无法取下 的说明
    // ------------------------------------------------------------------

    /** 在按钮下方居中绘制说明（自动折行） */
    private void renderHeTrueNote(GuiGraphics gui, int cx, int cy) {
        List<FormattedCharSequence> lines = this.font.split(
                Component.translatable("divinebeast.death.he_true_note"), NOTE_WRAP_W);
        int y = cy + 34;
        for (FormattedCharSequence line : lines) {
            gui.drawString(this.font, line, cx - this.font.width(line) / 2, y, 0xFFFF6B6B);
            y += 11;
        }
    }

    // ------------------------------------------------------------------
    // 背景飘字：「你准备好了吗！」
    // ------------------------------------------------------------------

    /** 一条飘字：位置/速度（像素 与 像素每秒）、大小、倾角、自转、寿命 */
    private static final class Floater {
        float x;
        float y;
        float vx;
        float vy;
        float scale;
        float angle;
        float spin;
        float ageMs;
        float lifeMs;
    }

    /** 按真实时间推进：生成新飘字、移动与自转、淘汰过期飘字 */
    private void advanceAnimation() {
        long now = net.minecraft.Util.getMillis();
        // 单帧最多按 4 tick 计，避免卡顿后一次性跳变
        float deltaMs = Mth.clamp((float) (now - this.lastMillis), 0.0F, 200.0F);
        this.lastMillis = now;

        this.spawnAccumulator += deltaMs;
        while (this.spawnAccumulator >= READY_SPAWN_MS) {
            this.spawnAccumulator -= READY_SPAWN_MS;
            if (this.floaters.size() < READY_MAX) {
                this.floaters.add(newFloater());
            }
        }

        Iterator<Floater> it = this.floaters.iterator();
        while (it.hasNext()) {
            Floater f = it.next();
            f.ageMs += deltaMs;
            if (f.ageMs >= f.lifeMs) {
                it.remove();
                continue;
            }
            float dt = deltaMs / 1000.0F;
            f.x += f.vx * dt;
            f.y += f.vy * dt;
            f.angle += f.spin * dt;
        }
    }

    private Floater newFloater() {
        Floater f = new Floater();
        // 大小不一：0.7 ~ 2.4 倍
        f.scale = 0.7F + this.rng.nextFloat() * 1.7F;
        // 倾斜角度不同：-38° ~ +38°，并带一点缓慢自转
        f.angle = (this.rng.nextFloat() - 0.5F) * 76.0F;
        f.spin = (this.rng.nextFloat() - 0.5F) * 14.0F; // 度/秒
        // 从屏幕内随机位置缓缓上浮（越大的字飘得越慢）
        f.x = this.width * (0.06F + this.rng.nextFloat() * 0.88F);
        f.y = this.height * (0.12F + this.rng.nextFloat() * 0.86F);
        f.vx = (this.rng.nextFloat() - 0.5F) * 9.0F;   // 像素/秒
        f.vy = -(6.0F + this.rng.nextFloat() * 12.0F) / f.scale;
        f.lifeMs = 2200.0F + this.rng.nextFloat() * 2400.0F; // 2.2 ~ 4.6 秒
        return f;
    }

    private void renderReadyTexts(GuiGraphics gui) {
        Component text = Component.translatable("divinebeast.death.ready");
        for (Floater f : this.floaters) {
            float t = Mth.clamp(f.ageMs / f.lifeMs, 0.0F, 1.0F);
            // 淡入淡出：前 15% 淡入、后 30% 淡出
            float fade = Mth.clamp(Math.min(t / 0.15F, (1.0F - t) / 0.30F), 0.0F, 1.0F);
            int alpha = (int) (fade * 0x96);
            if (alpha <= 3) {
                continue;
            }
            int color = (alpha << 24) | 0xB86CE0; // 半透明紫色
            gui.pose().pushPose();
            gui.pose().translate(f.x, f.y, 0.0F);
            gui.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(f.angle));
            gui.pose().scale(f.scale, f.scale, 1.0F);
            gui.drawCenteredString(this.font, text, 0, 0, color);
            gui.pose().popPose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
