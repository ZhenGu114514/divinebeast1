package com.divinebeast.divinebeast.client;

import com.divinebeast.divinebeast.boss.ModEntities;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** 中立 boss 的客户端渲染注册（只会由 DivineBeastMod 在客户端侧调用）。 */
public final class BossClient {

    private BossClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(BossClient::onRegisterRenderers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 三个 boss 共用同一套 Steve 渲染，各自 new 一个实例
        event.registerEntityRenderer(ModEntities.SELF_BOSS.get(), BossRenderer::new);
        event.registerEntityRenderer(ModEntities.BEAST_BOSS.get(), BossRenderer::new);
        event.registerEntityRenderer(ModEntities.HE_BOSS.get(), BossRenderer::new);
    }
}
