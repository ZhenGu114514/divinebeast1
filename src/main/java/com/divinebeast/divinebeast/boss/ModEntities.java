package com.divinebeast.divinebeast.boss;

import com.divinebeast.divinebeast.DivineBeastMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 三个中立 boss 的实体注册。
 *
 * <p>『我』(self_boss) / 『兽』(beast_boss) / 『祂』(he_boss)，都使用 Steve 模型与皮肤
 * （客户端渲染见 {@code client/BossRenderer}），中立：被玩家攻击前完全不动。
 *
 * <p>实体类别用 {@link MobCategory#MISC}：不参与自然刷怪、不被生物容量上限统计，
 * 也不会因为离玩家远而消失（另外构造里还调用了 {@code setPersistenceRequired()}）。
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DivineBeastMod.MOD_ID);

    /** 『我』：主世界 1000 格外，泥土台面 */
    public static final RegistryObject<EntityType<SelfBoss>> SELF_BOSS = ENTITY_TYPES.register("self_boss",
            () -> EntityType.Builder.of(SelfBoss::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("divinebeast:self_boss"));

    /** 『兽』：地狱 1000 格外，下界岩台面 */
    public static final RegistryObject<EntityType<BeastBoss>> BEAST_BOSS = ENTITY_TYPES.register("beast_boss",
            () -> EntityType.Builder.of(BeastBoss::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("divinebeast:beast_boss"));

    /** 『祂』：末地 1000 格外，末地石台面 */
    public static final RegistryObject<EntityType<HeBoss>> HE_BOSS = ENTITY_TYPES.register("he_boss",
            () -> EntityType.Builder.of(HeBoss::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("divinebeast:he_boss"));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(ModEntities::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SELF_BOSS.get(), DivineBoss.createAttributes(DivineBoss.Kind.SELF).build());
        event.put(BEAST_BOSS.get(), DivineBoss.createAttributes(DivineBoss.Kind.BEAST).build());
        event.put(HE_BOSS.get(), DivineBoss.createAttributes(DivineBoss.Kind.HE).build());
    }
}
