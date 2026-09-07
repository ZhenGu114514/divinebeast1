package com.divinebeast.divinebeast;

import com.divinebeast.divinebeast.client.ClientKeybinds;
import com.divinebeast.divinebeast.curio.BeastEffects;
import com.divinebeast.divinebeast.curio.CuriosCompat;
import com.divinebeast.divinebeast.curio.CuriosEffects;
import com.divinebeast.divinebeast.curio.MomentEffects;
import com.divinebeast.divinebeast.item.ModItems;
import com.divinebeast.divinebeast.net.Networking;
import com.divinebeast.divinebeast.reward.BossRewards;
import com.divinebeast.divinebeast.reward.FirstChestReward;
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
        FirstChestReward.register(); // 每个玩家打开的第一个箱子 → 『祂』『兽』
        BossRewards.register();      // 末影龙→时刻、凋灵→法则 掉落
        // C 键（救赎重生开关）键位仅在客户端注册
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> ClientKeybinds.init(modBus));

        if (CompatChecks.curiosLoaded()) {
            // 只有走到这里才会真正加载这几个类（内含 Curios API 引用）
            CuriosCompat.register();
            CuriosEffects.register();
            MomentEffects.register();
            BeastEffects.register();
        } else {
            LOGGER.info("[{}] 未检测到 Curios，槽位联动与『祂』效果关闭；物品仍可正常注册与使用。", MOD_ID);
        }
    }
}
