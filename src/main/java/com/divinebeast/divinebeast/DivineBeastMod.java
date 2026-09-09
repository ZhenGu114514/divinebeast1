package com.divinebeast.divinebeast;

import com.divinebeast.divinebeast.client.AutoCombatClient;
import com.divinebeast.divinebeast.client.ClientKeybinds;
import com.divinebeast.divinebeast.curio.BeastEffects;
import com.divinebeast.divinebeast.curio.AscensionEffects;
import com.divinebeast.divinebeast.curio.CuriosCompat;
import com.divinebeast.divinebeast.curio.CuriosEffects;
import com.divinebeast.divinebeast.curio.HeTrueEffects;
import com.divinebeast.divinebeast.curio.MomentEffects;
import com.divinebeast.divinebeast.item.ModItems;
import com.divinebeast.divinebeast.net.Networking;
import com.divinebeast.divinebeast.reward.BossRewards;
import com.divinebeast.divinebeast.reward.FirstChestReward;
import com.divinebeast.divinebeast.reward.FirstKillDrops;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Divine &amp; Beast —— 基于 Curios API 的饰品模组（Forge 1.20.1）。
 *
 * <p><b>槽位层</b>（数据驱动，见 data/divinebeast/curios/…）
 * <p><b>物品层</b>：核心饰品『祂』(deity)、『兽』(beast) 及各自体系饰品。
 * <p><b>效果层</b>：{@link CuriosEffects} 实现『祂』阶段一负面与救赎阶段效果。
 * <p><b>联动层</b>：{@link CuriosCompat}（槽位修饰解锁）。以上两类仅当 Curios
 * 已安装时加载（详见 CompatChecks）。
 *
 * <p>Curios 为可选前置：未安装时物品照常存在与使用，只是没有槽位与效果。
 */
@Mod(DivineBeastMod.MOD_ID)
public class DivineBeastMod {

    public static final String MOD_ID = "divinebeast";
    private static final Logger LOGGER = LogManager.getLogger();

    public DivineBeastMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modBus);
        Networking.register();
        FirstChestReward.register(); // 首箱→『祂』『兽』；分维度首箱→感知/意识/反叛
        BossRewards.register();      // 末影龙→时刻、凋灵→法则 掉落
        FirstKillDrops.register();   // 击杀首只中立/敌对 → 悲/伤
        // 死亡重生（PlayerEvent.Clone）时 Forge 不会自动带上持久 NBT：
        // 阶段/首箱/首杀等 divinebeast.* 键必须手动复制，否则换维度返回等场景会“退回初”。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
                .addListener(DivineBeastMod::onPlayerClone);
        // C 键（救赎重生开关）键位仅在客户端注册
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> ClientKeybinds.init(modBus));
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> AutoCombatClient.init());

        if (CompatChecks.curiosLoaded()) {
            // 只有走到这里才会真正加载这几个类（内含 Curios API 引用）
            CuriosCompat.register();
            CuriosEffects.register();
            MomentEffects.register();
            BeastEffects.register();
            AscensionEffects.register();
            HeTrueEffects.register();
            // 碎片门槛：感知/意识/反叛 需已装备『祂者初』
            FirstChestReward.setFragmentGate(AscensionEffects::wearingHeFirst);
            // 证悟抉择界面仅客户端显示
            DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.divinebeast.divinebeast.client.AscensionDeathClient.init());
        } else {
            LOGGER.info("[{}] 未检测到 Curios，槽位联动与『祂』效果关闭；物品仍可正常注册与使用。", MOD_ID);
        }
    }

    /** 死亡/重生后把旧玩家 persistent data 里的 divinebeast.* 全部复制给新玩家，
     *  否则阶段、已领取标记等在重生后会丢失（如从末地经传送门返回时被当作一次新实体）。 */
    private static void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        net.minecraft.world.entity.player.Player original = event.getOriginal();
        net.minecraft.world.entity.player.Player player = event.getEntity();
        if (original == null || player == null || player.level().isClientSide) {
            return;
        }
        net.minecraft.nbt.CompoundTag src = original.getPersistentData();
        net.minecraft.nbt.CompoundTag dst = player.getPersistentData();
        for (String key : src.getAllKeys()) {
            // 只带回长期状态；死亡抉择的临时挂起标记（asc_pending）不跨重生保留，
            // 否则重生的瞬间会被残留的超时逻辑当作“未选择”再次传送。
            if (key.startsWith(MOD_ID + ".") && !key.equals("divinebeast.asc_pending")
                    && !dst.contains(key)) {
                dst.put(key, src.get(key).copy());
            }
        }
    }
}
