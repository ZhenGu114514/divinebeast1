package com.divinebeast.divinebeast.integration;

import com.divinebeast.divinebeast.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 车万女仆（Touhou Little Maid）适配。
 *
 * <p><b>槽位侧</b>：女仆不走证悟链，也不需要『祂者初』等阶段槽。她们的『真者祂』饰品栏
 * 由数据包直接给（{@code data/divinebeast/curios/entities/maid.json} 把
 * {@code he_true_maid} 槽挂到 {@code touhou_little_maid:maid}；
 * {@code data/curios/tags/items/he_true_maid.json} 让『真者祂』能放进去）。
 * 该槽是<b>常驻</b>的（size 1），不像玩家那样靠阶段修饰符动态开合，
 * 因此与 CuriosCompat 里那条"界面开着时收回槽位会崩"的路径无关。
 *
 * <p><b>物品侧</b>：本模组只发一件『真者祂』（证悟终点奖励），给玩家自己戴上后就没了。
 * 装载女仆模组时，玩家每<b>净获得</b>一件『真者祂』，本类额外再补发一件，
 * 让玩家可以分一件给女仆。老存档（更新前就已经持有）在更新后首次进入时补发一份，仅一次。
 *
 * <p><b>"净获得"的判定</b>：按「背包（含护甲/副手）+ Curios 饰品栏」中『真者祂』的总件数
 * 只增不减地统计 —— 因此把物品丢出去再捡回来、放进箱子再拿出来都不会重复补发。
 * 每次进入世界重新采样基线（以当时持有数为准），离线期间的获得不会追溯。
 *
 * <p>仅在 Curios 已安装时由 {@code DivineBeastMod} 触发加载（内含 Curios API 引用）。
 */
public final class MaidCompat {

    /** 车万女仆的 modid */
    public static final String MAID_MOD_ID = "touhou_little_maid";

    private static final Logger LOGGER = LogManager.getLogger();

    /** 已补发标记（持久 NBT，随玩家重生保留）：老存档补发一次后不再补 */
    private static final String TAG_COPY_GIVEN = "divinebeast.maid_copy_given";

    /** 采样间隔（tick）：每 0.5 秒检查一次背包/饰品栏 */
    private static final int POLL_INTERVAL = 10;

    /** 玩家 UUID → 见过的『真者祂』总件数（只增不减；登出即清除，下次进入以当时持有数为基线） */
    private static final Map<UUID, Integer> SEEN = new HashMap<>();

    private MaidCompat() {
    }

    public static boolean maidLoaded() {
        return ModList.get().isLoaded(MAID_MOD_ID);
    }

    /** 仅在 Curios 已安装时调用；未装女仆模组时直接不注册（零开销）。 */
    public static void register() {
        if (!maidLoaded()) {
            LOGGER.info("[divinebeast] 未检测到车万女仆，女仆适配（真者祂饰品栏 / 额外补发）关闭。");
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(MaidCompat::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(MaidCompat::onPlayerLoggedOut);
        LOGGER.info("[divinebeast] 女仆适配已启用：女仆直接拥有『真者祂』饰品栏；玩家获得『真者祂』时额外补发一件。");
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (player.tickCount % POLL_INTERVAL != 0) {
            return;
        }
        UUID id = player.getUUID();
        int count = countHeTrue(player);
        Integer last = SEEN.get(id);
        if (last == null) {
            // 本次进入世界的第一次采样：只记录基线（离线/重生期间的变化不追溯）。
            // 例外：老存档在本功能出现之前就已持有『真者祂』→ 补发一次（持久标记保证只发一次）。
            SEEN.put(id, count);
            if (count > 0 && !player.getPersistentData().getBoolean(TAG_COPY_GIVEN)) {
                grant(player, 1);
                SEEN.put(id, count + 1);
            }
            return;
        }
        if (count > last) {
            int delta = count - last;
            grant(player, delta);
            // 补发的一并计入基线，避免下一轮把自己的补发当成"又获得了一件"。
            SEEN.put(id, count + delta);
        }
    }

    /** 额外补发 count 件『真者祂』并提示；同时写入"已补发"持久标记。 */
    private static void grant(Player player, int count) {
        player.getPersistentData().putBoolean(TAG_COPY_GIVEN, true);
        ItemStack gift = new ItemStack(ModItems.HE_TRUE.get(), count);
        if (!player.getInventory().add(gift)) {
            player.drop(gift, false);
        }
        player.sendSystemMessage(Component.translatable("divinebeast.msg.maid_copy", count));
    }

    /** 背包（含护甲/副手）+ Curios 饰品栏中『真者祂』的总件数。 */
    private static int countHeTrue(Player player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(ModItems.HE_TRUE.get())) {
                count += stack.getCount();
            }
        }
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isPresent()) {
            for (SlotResult result : optional.get().findCurios(ModItems.HE_TRUE.get())) {
                count += result.stack().getCount();
            }
        }
        return count;
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player != null) {
            SEEN.remove(player.getUUID());
        }
    }
}
