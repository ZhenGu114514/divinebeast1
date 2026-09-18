package com.divinebeast.divinebeast.item;

import com.divinebeast.divinebeast.boss.BossArena;
import com.divinebeast.divinebeast.boss.BossArenaData;
import com.divinebeast.divinebeast.boss.DivineBoss;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 指路罗盘：右键显示对应 boss 的竞技场坐标与距离。
 *
 * <p>合成配方：**指南针 + 一圈 8 个**对应物品（我=绿宝石 / 兽=熟猪排 / 祂=泥土）。
 * 只能在对应维度里生效（主世界 / 地狱 / 末地），在别的维度会提示该去哪。
 */
public class BossCompassItem extends Item {

    private final DivineBoss.Kind kind;

    public BossCompassItem(DivineBoss.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public DivineBoss.Kind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel) {
            DivineBoss.Kind here = BossArena.kindFor(serverLevel.dimension());
            if (here != this.kind) {
                player.displayClientMessage(Component.translatable("divinebeast.msg.compass.wrong_dim",
                        bossName(), dimensionName()), false);
            } else {
                BossArenaData data = BossArenaData.of(serverLevel);
                if (!data.isChosen()) {
                    player.displayClientMessage(
                            Component.translatable("divinebeast.msg.compass.unknown", bossName()), false);
                } else {
                    int dx = data.siteX() - player.getBlockX();
                    int dz = data.siteZ() - player.getBlockZ();
                    int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
                    player.displayClientMessage(Component.translatable("divinebeast.msg.compass.found",
                            bossName(), data.siteX(), data.siteZ(), distance)
                            .withStyle(ChatFormatting.AQUA), false);
                }
            }
            player.getCooldowns().addCooldown(this, 10);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private Component bossName() {
        return Component.translatable(switch (this.kind) {
            case SELF -> "entity.divinebeast.self_boss";
            case BEAST -> "entity.divinebeast.beast_boss";
            case HE -> "entity.divinebeast.he_boss";
        });
    }

    private Component dimensionName() {
        return Component.translatable(switch (this.kind) {
            case SELF -> "dimension.minecraft.overworld";
            case BEAST -> "dimension.minecraft.the_nether";
            case HE -> "dimension.minecraft.the_end";
        });
    }
}
