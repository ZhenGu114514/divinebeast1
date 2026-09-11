package com.divinebeast.divinebeast.curio;

import com.divinebeast.divinebeast.item.ModItems;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.Optional;
import java.util.UUID;

/**
 * 懦弱的抉择 —— "你可能还未准备好"的安全饰品。
 *
 * <p><b>获得方式</b>：<b>只在第一次进入这个世界时发放一次</b>——标记写下之后，无论之后是丢失、
 * 被烧掉、被丢进箱子还是重新登录，都不会再补发。发放时自动装进 Curios 的
 * <b>通用 {@code curio} 槽</b>；玩家可以随后自行把它取下来放回背包，那时诅咒会恢复生效
 * （{@link #wearing} 只看是否真装着）。
 *
 * <p><b>效果</b>：装备期间无效『祂』诅咒与『兽』诅咒的全部负面
 * （见 {@link CuriosEffects#curseActive} 与 {@code BeastEffects#curseActive} 中的短路）。
 *
 * <p><b>槽位</b>：Curios 内置的通用 {@code curio} 槽默认 0 格，本类每 tick 幂等地补 +1 格
 * （与 {@code HeTrueEffects#syncCurioHoard} 的 +99 是同一种 transient 槽位修饰符）。
 * transient 修饰符在跨维度/重生后可能被 Curios 清空，故采用"每 tick 自愈式补写"。
 *
 * <p>本类含 Curios API 引用，只允许在确认 Curios 已安装后由
 * {@code DivineBeastMod} 调用 {@link #register()} 加载。
 */
public final class CowardChoice {

    private static final Logger LOGGER = LogManager.getLogger();

    /** 目标槽位：Curios 内置的通用饰品槽 */
    private static final String SLOT_ID = "curio";
    /** 通用槽补格修饰符的固定 UUID（与 万藏 的 CURIO_HOARD_MOD 区分开） */
    private static final UUID SLOT_MOD = UUID.fromString("d1e6be4d-6f6c-4f6b-a4b1-0000000000b9");
    /** 通用槽至少补足的格数 */
    private static final double SLOT_AMOUNT = 1.0D;
    /**
     * 是否已经"成功自动装备过一次"。置位后不再自动装回，
     * 玩家可以随时取下来放背包（诅咒随之恢复），不会被打扰。
     */
    private static final String TAG_EQUIPPED = "divinebeast.coward_choice_equipped";
    /**
     * 是否已经发放过。**只在第一次进入世界时发放一次**：标记写下后，
     * 之后无论丢失、销毁还是重新登录都不再补发。
     */
    private static final String TAG_GIVEN = "divinebeast.coward_choice_given";

    private CowardChoice() {
    }

    /** 仅当 Curios 已安装时被调用。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(CowardChoice::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(CowardChoice::onPlayerTick);
        LOGGER.info("[divinebeast] 懦弱的抉择：进入世界自动发放并装备到通用 curio 槽。");
    }

    /**
     * 该生物是否正装备着「懦弱的抉择」（通用 {@code curio} 槽内）。
     *
     * <p>被 {@link CuriosEffects#curseActive} 与 {@code BeastEffects#curseActive} 调用，
     * 用于短路掉『祂』与『兽』的一切诅咒负面。
     */
    public static boolean wearing(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isEmpty()) {
            return false;
        }
        for (SlotResult result : optional.get().findCurios(SLOT_ID)) {
            ItemStack stack = result.stack();
            if (!stack.isEmpty() && stack.is(ModItems.COWARD_CHOICE.get())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 发放 + 装备
    // ------------------------------------------------------------------

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // 注意：PlayerLoggedInEvent#getEntity() 的返回类型本身就是 Player，
        // 这里不能再写 `instanceof Player player`（Java 17 会报"模式无条件"编译错误）。
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide) {
            return;
        }
        // 只在"第一次进入这个世界"时发放一次：标记写下后，丢失/销毁/重登都不再补发。
        // （hasAnywhere 只是极端兜底：万一标记被外部工具清掉而东西还在，也不重复发放。）
        if (player.getPersistentData().getBoolean(TAG_GIVEN) || hasAnywhere(player)) {
            return;
        }
        // 先落标记再发东西：即使中途出意外也不会变成"每次登录都发一件"
        player.getPersistentData().putBoolean(TAG_GIVEN, true);
        ensureGenericSlot(player); // 先把通用槽补上，否则第一件放不进去
        ItemStack stack = new ItemStack(ModItems.COWARD_CHOICE.get());
        if (equipIntoGenericSlot(player, stack)) {
            player.getPersistentData().putBoolean(TAG_EQUIPPED, true);
        } else if (!player.getInventory().add(stack)) {
            player.drop(stack, false); // 兜底：背包也满了就掉在脚下，绝不吞物品
        }
        player.sendSystemMessage(Component.translatable("divinebeast.msg.coward_choice"));
    }

    /** 玩家身上（通用饰品槽或背包）是否已经有这件饰品 */
    private static boolean hasAnywhere(Player player) {
        if (wearing(player)) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(ModItems.COWARD_CHOICE.get())) {
                return true;
            }
        }
        return false;
    }

    /** 尝试放进通用 curio 槽的第一个空位；成功返回 true */
    private static boolean equipIntoGenericSlot(Player player, ItemStack stack) {
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isEmpty()) {
            return false;
        }
        Optional<ICurioStacksHandler> handler = optional.get().getStacksHandler(SLOT_ID);
        if (handler.isEmpty()) {
            return false;
        }
        var stacks = handler.get().getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (stacks.getStackInSlot(i).isEmpty()) {
                stacks.setStackInSlot(i, stack.copy());
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 每 tick：保底通用槽 + 首次装备补装
    // ------------------------------------------------------------------

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        // 与 万藏 同理：有外部容器/界面打开时不能改槽数，否则客户端已打开的菜单仍是旧槽数
        // → ClientboundContainerSetContentPacket 越界报错。关掉容器后下个 tick 自会补上。
        if (player.containerMenu != player.inventoryMenu) {
            return;
        }
        ensureGenericSlot(player);
        // 登录那一刻 Curios 可能尚未就绪（槽位还是 0 格）→ 之后补装一次。
        // 只在"从未成功装备过"时尝试；成功一次后置位，玩家此后可自由取下。
        if (!player.getPersistentData().getBoolean(TAG_EQUIPPED)) {
            if (wearing(player)) {
                player.getPersistentData().putBoolean(TAG_EQUIPPED, true); // 已经装着了，收工
            } else {
                tryEquipFromInventory(player);
            }
        }
    }

    /** 背包里还有这件饰品、且通用槽有空位 → 装进去（仅用于首次自动装备） */
    private static void tryEquipFromInventory(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty() || !stack.is(ModItems.COWARD_CHOICE.get())) {
                continue;
            }
            ItemStack single = stack.copy();
            single.setCount(1);
            if (equipIntoGenericSlot(player, single)) {
                stack.shrink(1);
                player.getPersistentData().putBoolean(TAG_EQUIPPED, true);
                return;
            }
            return; // 槽位已满：本 tick 放弃（下个 tick 会再试一次）
        }
    }

    /**
     * 幂等地保证通用 {@code curio} 槽至少有 {@link #SLOT_AMOUNT} 格。
     * 已是目标值就直接返回，避免每 tick 触发槽位更新与容器同步。
     */
    private static void ensureGenericSlot(Player player) {
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isEmpty()) {
            return;
        }
        ICuriosItemHandler handler = optional.get();
        if (handler.getStacksHandler(SLOT_ID).isEmpty()) {
            return; // 未给玩家分配通用槽（entities/player.json 缺 curio）时本效果无意义
        }
        if (hasSlotModifier(handler)) {
            return;
        }
        Multimap<String, AttributeModifier> map = LinkedHashMultimap.create();
        map.put(SLOT_ID, new AttributeModifier(SLOT_MOD, "divinebeast_coward_choice_slot",
                SLOT_AMOUNT, AttributeModifier.Operation.ADDITION));
        handler.addTransientSlotModifiers(map);
    }

    /** 通用槽上是否已存在我们这条 +1 修饰符（查询失败按"不存在"处理） */
    private static boolean hasSlotModifier(ICuriosItemHandler handler) {
        try {
            for (AttributeModifier modifier : handler.getModifiers().get(SLOT_ID)) {
                if (SLOT_MOD.equals(modifier.getId())
                        && Math.abs(modifier.getAmount() - SLOT_AMOUNT) < 1.0E-6D) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // 退回原行为（当作不存在）
        }
        return false;
    }
}
