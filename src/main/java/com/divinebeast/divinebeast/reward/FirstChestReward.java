package com.divinebeast.divinebeast.reward;

import com.divinebeast.divinebeast.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;

/**
 * 新手礼盒：每个玩家（每个存档）打开的第一个"箱子类容器"（箱子/陷阱箱/木桶/潜影盒/末影箱）
 * 会在其中生成 『祂』 与 『兽』 各一件；容器满时掉落在箱子前。仅服务器侧生效。
 *
 * <p>感知/意识/反叛 的"分维度首箱"另有门槛：必须已装备『祂者初』（fragmentGate，由 Curios
 * 分支注入 AscensionEffects.wearingHeFirst），未装备时打开箱子不会消耗该维度首箱标记。
 */
public final class FirstChestReward {

    private static final String TAG_CLAIMED = "divinebeast.first_chest_claimed";

    /** 证悟碎片（感知/意识/反叛）的领取门槛：已装备『祂者初』。由 Curios 分支注入，默认拒绝。 */
    private static java.util.function.Predicate<Player> fragmentGate = p -> false;

    private FirstChestReward() {
    }

    public static void setFragmentGate(java.util.function.Predicate<Player> gate) {
        fragmentGate = gate;
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(FirstChestReward::onContainerOpen);
    }

    private static void onContainerOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        CompoundTag tag = player.getPersistentData();
        AbstractContainerMenu menu = event.getContainer();
        if (menu == null) {
            return;
        }
        // 找出本菜单对应的"箱子类方块容器"
        Container chest = null;
        for (Slot slot : menu.slots) {
            Object holder = slot.container;
            if (holder instanceof Container container
                    && container instanceof BlockEntity blockEntity
                    && isChestLike(blockEntity)) {
                chest = container;
                break;
            }
        }
        if (chest == null) {
            return; // 打开的并非箱子类容器（如自身背包/工作台等）
        }

        // ---------- 1) 新手礼盒：『祂』『兽』，整个存档仅一次 ----------
        if (!tag.getBoolean(TAG_CLAIMED)) {
            tag.putBoolean(TAG_CLAIMED, true);

            // 『祂』『兽』自带绑定诅咒
            ItemStack deity = new ItemStack(ModItems.DEITY.get());
            ItemStack beast = new ItemStack(ModItems.BEAST.get());
            deity.enchant(net.minecraft.world.item.enchantment.Enchantments.BINDING_CURSE, 1);
            beast.enchant(net.minecraft.world.item.enchantment.Enchantments.BINDING_CURSE, 1);

            boolean missed = false;
            for (ItemStack stack : new ItemStack[]{deity, beast}) {
                if (!addToChest(chest, stack)) {
                    missed = true;
                    dropNear(player, stack);
                }
            }
            if (!missed) {
                player.sendSystemMessage(Component.translatable("divinebeast.msg.first_chest"));
            }
        }

        // ---------- 2) 分维度首箱 → 感知/意识/反叛（每个维度各一次，须已装备『祂者初』） ----------
        String dimKey = player.level().dimension().location().toString();
        String dimFlag = "divinebeast.first_dim_" + dimKey;
        if (!tag.getBoolean(dimFlag) && fragmentGate.test(player)) {
            ItemStack fragment = null;
            if (dimKey.equals("minecraft:overworld")) {
                fragment = new ItemStack(ModItems.PERCEPTION.get());      // 主世界→感知
            } else if (dimKey.equals("minecraft:the_nether")) {
                fragment = new ItemStack(ModItems.CONSCIOUSNESS.get());   // 下界→意识
            } else if (dimKey.equals("minecraft:the_end")) {
                fragment = new ItemStack(ModItems.REBELLION.get());       // 末地→反叛
            }
            if (fragment != null) {
                tag.putBoolean(dimFlag, true);
                if (!addToChest(chest, fragment)) {
                    dropNear(player, fragment);
                }
                player.sendSystemMessage(Component.translatable("divinebeast.msg.first_dim_chest"));
            }
        }
    }

    private static boolean isChestLike(BlockEntity entity) {
        return entity instanceof ChestBlockEntity        // 箱子 / 陷阱箱
                || entity instanceof BarrelBlockEntity   // 木桶
                || entity instanceof ShulkerBoxBlockEntity // 潜影盒
                || entity instanceof EnderChestBlockEntity; // 末影箱
    }

    private static boolean addToChest(Container chest, ItemStack stack) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).isEmpty()) {
                chest.setItem(i, stack.copy());
                return true;
            }
        }
        return false;
    }

    private static void dropNear(Player player, ItemStack stack) {
        if (player.level().isClientSide) {
            return;
        }
        player.level().addFreshEntity(new ItemEntity(player.level(),
                player.getX(), player.getY() + 0.5D, player.getZ(), stack));
    }
}
