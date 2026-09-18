package com.divinebeast.divinebeast.boss;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 三个中立 boss 的交易 / 喂食逻辑（交易界面与直接右键共用同一套结算）。
 *
 * <p><b>交易</b>：{@link DivineBoss#TRADE_COST} 个货币（我=绿宝石 / 兽=熟猪排 / 祂=泥土）
 * → {@link DivineBoss#DROP_BLOCKS} 个同名方块；冷却 0.5 秒。
 * 方块能分解成 9 个锭，锭再合武器 / 工具 / 盔甲。
 *
 * <p><b>喂食</b>（只有『兽』，且必须已开战）：1 个熟猪排 → "还需吸取" -20；冷却 0.5 秒。
 *
 * <p>入口有两种：空手右键打开交易界面（{@code OpenBossTradeMessage}），
 * 或者手持货币直接右键即时交易。
 */
public final class BossTrade {

    private BossTrade() {
    }

    /** 直接右键的入口（手里拿着东西）。 */
    public static InteractionResult handle(DivineBoss boss, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return held.is(boss.kind().currency()) ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        if (boss.kind() == DivineBoss.Kind.BEAST && boss.isProvoked()
                && held.is(Items.COOKED_PORKCHOP)) {
            performFeed(boss, serverPlayer);
            return InteractionResult.CONSUME;
        }
        if (held.is(boss.kind().currency())) {
            performTrade(boss, serverPlayer);
        } else {
            serverPlayer.displayClientMessage(Component.translatable("divinebeast.msg.boss.trade_need",
                    boss.getDisplayName(), DivineBoss.TRADE_COST,
                    new ItemStack(boss.kind().currency()).getHoverName()), true);
        }
        return InteractionResult.CONSUME;
    }

    /** 主交易：16 个货币 → 3 个同名方块。 */
    public static boolean performTrade(DivineBoss boss, ServerPlayer player) {
        if (boss.isClone()) {
            return false;
        }
        if (!boss.canTrade()) {
            player.displayClientMessage(Component.translatable("divinebeast.msg.boss.trade_cooldown",
                    boss.getDisplayName()), true);
            return false;
        }
        Item currency = boss.kind().currency();
        if (!consume(player, currency, DivineBoss.TRADE_COST)) {
            player.displayClientMessage(Component.translatable("divinebeast.msg.boss.trade_need",
                    boss.getDisplayName(), DivineBoss.TRADE_COST,
                    new ItemStack(currency).getHoverName()), true);
            return false;
        }
        boss.markTraded();
        ItemStack reward = new ItemStack(boss.kind().blockItem(), DivineBoss.DROP_BLOCKS);
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
        player.sendSystemMessage(Component.translatable("divinebeast.msg.boss.trade_done",
                boss.getDisplayName(), DivineBoss.TRADE_COST, new ItemStack(currency).getHoverName(),
                reward.getCount(), reward.getHoverName()));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** 『兽』的喂食：1 个熟猪排 → "还需吸取" -20（凑满 200 点它就死去）。 */
    public static boolean performFeed(DivineBoss boss, ServerPlayer player) {
        if (boss.isClone() || boss.kind() != DivineBoss.Kind.BEAST) {
            return false;
        }
        if (!boss.canFeed()) {
            player.displayClientMessage(Component.translatable("divinebeast.msg.boss.feed_cooldown",
                    boss.getDisplayName()), true);
            return false;
        }
        if (!consume(player, Items.COOKED_PORKCHOP, 1)) {
            player.displayClientMessage(Component.translatable("divinebeast.msg.boss.feed_need",
                    boss.getDisplayName()), true);
            return false;
        }
        boss.markFed();
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS, 0.8F, 1.2F);
        boss.consumeDrain(DivineBoss.BEAST_FEED_REDUCTION);
        player.sendSystemMessage(Component.translatable("divinebeast.msg.boss.feed",
                boss.getDisplayName(), (int) boss.getBeastDrainRemaining()));
        return true;
    }

    /** 从背包（含主手）扣除指定物品；数量不足返回 false 且不做任何改动。 */
    private static boolean consume(ServerPlayer player, Item item, int count) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        if (total < count) {
            return false;
        }
        int left = count;
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(item)) {
                continue;
            }
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
        }
        return true;
    }
}
