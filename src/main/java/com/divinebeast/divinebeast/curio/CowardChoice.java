package com.divinebeast.divinebeast.curio;

import com.divinebeast.divinebeast.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
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

/**
 * 懦弱的抉择 —— "你可能还未准备好"的安全饰品。
 *
 * <p><b>获得方式</b>：<b>只在第一次进入这个世界时发放一次</b>——标记写下之后，无论之后是丢失、
 * 被烧掉、被丢进箱子还是重新登录，都不会再补发。发放时自动装进 Curios <b>原本就有的</b>
 * 通用 {@code curio} 槽（<b>不额外增加任何饰品栏</b>）；玩家可以随后自行把它取下来放回背包，
 * 那时诅咒会恢复生效（{@link #wearing} 只看是否真装着）。
 *
 * <p><b>效果</b>：装备期间无效『祂』诅咒与『兽』诅咒的全部负面
 * （见 {@link CuriosEffects#curseActive} 与 {@code BeastEffects#curseActive} 中的短路）。
 *
 * <p><b>槽位</b>：直接使用玩家已有的通用 {@code curio} 槽，不修改槽位数量。
 * 若那个槽此刻不可用（尚未分配 / 已被占满），物品会先落进背包，
 * 之后每 tick 再尝试一次自动装上去（成功一次后置位标记，此后不再干预）。
 *
 * <p>本类含 Curios API 引用，只允许在确认 Curios 已安装后由
 * {@code DivineBeastMod} 调用 {@link #register()} 加载。
 */
public final class CowardChoice {

    private static final Logger LOGGER = LogManager.getLogger();

    /** 目标槽位：Curios 内置的通用饰品槽（沿用玩家已有的那一格，不新增） */
    private static final String SLOT_ID = "curio";
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
        LOGGER.info("[divinebeast] 懦弱的抉择：进入世界自动发放并装备到已有的通用 curio 槽。");
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
        ItemStack stack = new ItemStack(ModItems.COWARD_CHOICE.get());
        if (equipIntoGenericSlot(player, stack)) {
            player.getPersistentData().putBoolean(TAG_EQUIPPED, true);
        } else if (!player.getInventory().add(stack)) {
            player.drop(stack, false); // 兜底：通用槽与背包都塞不下就掉在脚下，绝不吞物品
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

    /** 尝试放进现有通用 curio 槽的第一个空位；成功返回 true。不新增槽位。 */
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
    // 每 tick：首次装备补装（不涉及槽位增减）
    // ------------------------------------------------------------------

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        // 已经有结果了就直接跳过，日常开销只是一次 NBT 读取
        if (event.player.getPersistentData().getBoolean(TAG_EQUIPPED)) {
            return;
        }
        Player player = event.player;
        // 有外部容器/界面打开时不做饰品写入：避免与 Curios 的容器同步互相打架
        // （与 万藏 同源的保守做法）。关掉界面后下个 tick 自会继续。
        if (player.containerMenu != player.inventoryMenu) {
            return;
        }
        // 登录那一刻 Curios 可能尚未就绪，或通用槽当时被占满 → 之后补装一次。
        // 只在"从未成功装备过"时尝试；成功一次后置位，玩家此后可自由取下。
        if (wearing(player)) {
            player.getPersistentData().putBoolean(TAG_EQUIPPED, true); // 已经装着了，收工
        } else {
            tryEquipFromInventory(player);
        }
    }

    /** 背包里还有这件饰品、且现有通用槽有空位 → 装进去（仅用于首次自动装备） */
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
}
