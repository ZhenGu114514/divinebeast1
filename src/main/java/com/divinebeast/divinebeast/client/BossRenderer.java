package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.boss.DivineBoss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 三个中立 boss 的渲染器：<b>Steve 模型 + Steve 皮肤</b>。
 *
 * <p>直接复用原版"玩家"模型层（{@link ModelLayers#PLAYER}）——Steve 的 64×64 皮肤与人类模型
 * 一一对应，所以不需要自己注册模型层；外层（头发/外套/袖裤）会随 {@link PlayerModel} 一起渲染。
 *
 * <p>皮肤路径在 1.19.4 之后被挪到了 {@code wide/} 与 {@code slim/} 子目录，这里在首次渲染时
 * 用资源管理器探一下：优先新版路径，取不到再退回旧路径，最后兜底仍用新版路径。
 */
public class BossRenderer extends LivingEntityRenderer<DivineBoss, PlayerModel<DivineBoss>> {

    /** 1.19.4+ 的默认（经典/宽版）Steve 皮肤 */
    private static final ResourceLocation STEVE =
            new ResourceLocation("textures/entity/player/wide/steve.png");
    /** 1.19.3 及以前的路径，作为兜底 */
    private static final ResourceLocation STEVE_LEGACY =
            new ResourceLocation("textures/entity/player/steve.png");

    private static ResourceLocation resolved;

    public BossRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.6F);
    }

    @Override
    public ResourceLocation getTextureLocation(DivineBoss entity) {
        if (resolved == null) {
            resolved = resolveTexture();
        }
        return resolved;
    }

    private static ResourceLocation resolveTexture() {
        try {
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            if (manager.getResource(STEVE).isPresent()) {
                return STEVE;
            }
            if (manager.getResource(STEVE_LEGACY).isPresent()) {
                return STEVE_LEGACY;
            }
        } catch (Throwable ignored) {
            // 资源管理器此时不可用：按新版路径走即可
        }
        return STEVE;
    }
}
