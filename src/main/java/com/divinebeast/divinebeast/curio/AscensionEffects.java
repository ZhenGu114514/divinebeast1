package com.divinebeast.divinebeast.curio;

import com.divinebeast.divinebeast.item.ModItems;
import com.divinebeast.divinebeast.net.AscensionScreenMessage;
import com.divinebeast.divinebeast.net.Networking;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;

/**
 * 证悟线五阶段引擎（仅 Curios 存在时加载）。
 *
 * <p><b>槽位链（每件一个独立槽，size=0 + 槽位 modifier 驱动）：</b>
 * he_first(祂者初) / redemption(救赎) / he_extreme(祂者极) / trueheart(本心) / he_true(真者祂)。
 *
 * <p><b>阶段（玩家持久 NBT {@code divinebeast.asc_stage}）：</b>
 * 0 无 → 1 祂者初（两形态集齐自动获得，he_first 槽开）
 * → 2 自我救赎（佩戴救赎死亡后经抉择界面复活：销毁 祂者初+救赎，he_extreme 槽开、发放祂者极）
 * → 3 找回自我（佩戴本心死亡后经抉择界面复活：销毁 祂者极+本心，he_true 槽开、发放真者祂）。
 *
 * <p><b>效果：</b>祂者初/祂者极 佩戴即全免伤/禁伤/隐身/虚空保护/禁 /tp/禁破坏；
 * 救赎 或 本心 佩戴（effectsDisabled）封印除自身外一切饰品效果，因此佩戴者会死 ——
 * 其死亡会弹出「自我救赎 / 找回自我」抉择界面（见死亡迁移）。
 */
public final class AscensionEffects {

    // 槽位 id（与 data/divinebeast/curios/slots/*.json、entities、tag 一一对应）
    public static final String HE_FIRST_SLOT = "he_first";
    public static final String REDEMPTION_SLOT = "redemption";
    public static final String HE_EXTREME_SLOT = "he_extreme";
    public static final String TRUEHEART_SLOT = "trueheart";
    public static final String HE_TRUE_SLOT = "he_true";

    // 阶段持久 NBT
    public static final String TAG_ASC_STAGE = "divinebeast.asc_stage";
    public static final int STAGE_NONE = 0;
    public static final int STAGE_HE_FIRST = 1;
    public static final int STAGE_HE_EXTREME = 2;
    public static final int STAGE_HE_TRUE = 3;

    private static final double BEACON_RADIUS = 5.0D;
    /** 同步标记：当前伤害来自祂者极光柱（豁免"不可造成伤害"规则） */
    private static boolean beaconStrike = false;

    private AscensionEffects() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(AscensionEffects::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(AscensionEffects::onLivingAttack);
        MinecraftForge.EVENT_BUS.addListener(AscensionEffects::onLivingDamage);
        MinecraftForge.EVENT_BUS.addListener(AscensionEffects::onDeath);
        MinecraftForge.EVENT_BUS.addListener(AscensionEffects::onLeftClickBlock);
        MinecraftForge.EVENT_BUS.addListener(AscensionEffects::onTeleportCommand);
    }

    // ==================================================================
    // 阶段状态
    // ==================================================================

    public static int stageOf(Player player) {
        CompoundTag tag = player.getPersistentData();
        return tag.contains(TAG_ASC_STAGE) ? tag.getInt(TAG_ASC_STAGE) : STAGE_NONE;
    }

    public static void setStage(Player player, int stage) {
        player.getPersistentData().putInt(TAG_ASC_STAGE, stage);
    }

    // ==================================================================
    // 佩戴判定（每件独立槽）
    // ==================================================================

    public static boolean wearingHeFirst(Player p) {
        return wearsInSlot(p, HE_FIRST_SLOT, ModItems.HE_FIRST.get());
    }

    public static boolean wearingRedemption(Player p) {
        return wearsInSlot(p, REDEMPTION_SLOT, ModItems.REDEMPTION.get());
    }

    public static boolean wearingHeExtreme(Player p) {
        return wearsInSlot(p, HE_EXTREME_SLOT, ModItems.HE_EXTREME.get());
    }

    public static boolean wearingTrueHeart(Player p) {
        return wearsInSlot(p, TRUEHEART_SLOT, ModItems.TRUE_HEART.get());
    }

    public static boolean wearingHeTrue(Player p) {
        return wearsInSlot(p, HE_TRUE_SLOT, ModItems.HE_TRUE.get());
    }

    private static boolean wearsInSlot(Player player, String slotId, net.minecraft.world.item.Item item) {
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isEmpty()) {
            return false;
        }
        for (top.theillusivec4.curios.api.SlotResult result : optional.get().findCurios(slotId)) {
            ItemStack stack = result.stack();
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    /** 救赎 / 本心：封印自身之外的一切饰品效果（含 祂者初/祂者极）。 */
    public static boolean effectsDisabled(Player player) {
        return wearingRedemption(player) || wearingTrueHeart(player);
    }

    /**
     * 真者『祂』（终形态）是否正在生效。
     *
     * <p>用于让<b>低阶形态的限制给终形态让位</b>：祂者初 / 祂者极 是"冒险·不存在式"阶段，
     * 会禁伤、禁破坏方块、禁一切 /tp。这些限制在戴上真者祂后若仍然生效，终形态的核心权能
     * （攻击力 +1 億、挖掘掉落 ×10、18 界缚自定义传送权限）会被整体废掉 ——
     * 实测症状就是「装备真者祂后打不动末影龙」：伤害在 {@code LivingAttackEvent} 里
     * 被祂者初直接取消了。
     */
    private static boolean heTrueActive(Player player) {
        return wearingHeTrue(player) && !effectsDisabled(player);
    }

    // ==================================================================
    // 每 tick：隐身 + 祂者极光柱 + 虚空保护
    // ==================================================================

    // ==================================================================
    // 抉择状态（超时兜底：pending 超过 10 秒未选择 → 自动普通重生）
    // ==================================================================
    private static final String TAG_PENDING = "divinebeast.asc_pending";
    private static final long PENDING_TIMEOUT = 200L;

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        CompoundTag tag = player.getPersistentData();
        if (tag.contains(TAG_PENDING) && player instanceof ServerPlayer serverPlayer) {
            long pendingAt = tag.getLong(TAG_PENDING);
            if (player.level().getGameTime() - pendingAt > PENDING_TIMEOUT) {
                tag.remove(TAG_PENDING);
                handleChoice(serverPlayer, 0);
                return;
            }
        }
        boolean phasing = !effectsDisabled(player)
                && (wearingHeFirst(player) || wearingHeExtreme(player));
        if (phasing) {
            if (!player.isInvisible()) {
                player.setInvisible(true);
            }
            if (player.getY() < player.level().getMinBuildHeight() - 4) {
                player.teleportTo(player.getX(), player.level().getMinBuildHeight() + 4, player.getZ());
            }
        } else {
            if (player.isInvisible() && !player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
                player.setInvisible(false);
            }
        }
        if (!effectsDisabled(player) && wearingHeExtreme(player) && player.tickCount % 20 == 0) {
            float dmg = 10.0F + player.experienceLevel;
            for (LivingEntity mob : player.level().getEntitiesOfClass(LivingEntity.class,
                    net.minecraft.world.phys.AABB.ofSize(player.position(),
                            BEACON_RADIUS * 2, BEACON_RADIUS * 2, BEACON_RADIUS * 2))) {
                // 敌对单位（Monster 与 末影龙等 Enemy 生物，末影龙不是 Monster）
                if (mob instanceof net.minecraft.world.entity.monster.Enemy && mob.isAlive()) {
                    beaconStrike = true;
                    try {
                        mob.hurt(mob.damageSources().playerAttack(player), dmg);
                    } finally {
                        beaconStrike = false;
                    }
                }
            }
        }
    }

    // ==================================================================
    // 攻击 / 受击
    // ==================================================================

    private static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide || beaconStrike) {
            return;
        }
        Player attacker = null;
        if (event.getSource().getEntity() instanceof Player player) {
            attacker = player;
        }
        // 只有『祂者初』禁伤（冒险/不存在式）；『祂者极』可正常造成伤害（光柱之外也可攻击）
        // 真者祂生效时不再禁伤：终形态必须能打出伤害（否则连末影龙都打不动）
        if (attacker != null && !effectsDisabled(attacker)
                && !heTrueActive(attacker)
                && wearingHeFirst(attacker)
                && !event.getEntity().is(attacker)) {
            event.setCanceled(true);
        }
    }

    /** 攻击来源是否为『祂者初』佩戴者（其伤害一律无效；祂者极不在其列） */
    private static boolean attackerWearingHeFirst(DamageSource source) {
        Player attacker = null;
        Entity e = source.getEntity();
        if (e instanceof Player player) {
            attacker = player;
        } else if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof Player player2) {
            attacker = player2;
        }
        if (attacker == null || effectsDisabled(attacker) || heTrueActive(attacker)) {
            return false;
        }
        return wearingHeFirst(attacker);
    }

    private static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || beaconStrike) {
            return;
        }
        // 免伤：祂者初/祂者极 均免疫一切伤害（含虚空）
        if (event.getEntity() instanceof Player player
                && !effectsDisabled(player)
                && (wearingHeFirst(player) || wearingHeExtreme(player))) {
            event.setCanceled(true);
            return;
        }
        // 来自『祂者初』佩戴者的伤害无效（含弹射物）；祂者极的伤害正常结算
        if (attackerWearingHeFirst(event.getSource())
                && !event.getEntity().is(event.getSource().getEntity())) {
            event.setCanceled(true);
        }
    }

    /** 冒险式：不可破坏方块（真者祂生效时让位，否则"挖掘掉落 ×10"永远触发不了） */
    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && !effectsDisabled(player)
                && !heTrueActive(player)
                && (wearingHeFirst(player) || wearingHeExtreme(player))) {
            event.setCanceled(true);
        }
    }

    /** 禁 /tp 涉及你（真者祂生效时让位，交给 18 界缚 的精细规则处理） */
    private static void onTeleportCommand(EntityTeleportEvent.TeleportCommand event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player
                && !effectsDisabled(player)
                && !heTrueActive(player)
                && (wearingHeFirst(player) || wearingHeExtreme(player))) {
            event.setCanceled(true);
        }
    }

    // ==================================================================
    // 本心效果：击杀生物时自己死亡（进入「找回自我」迁移通道）
    // ==================================================================

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        // 本心佩戴者主动击杀 → 自己死亡
        if (!(event.getEntity() instanceof Player)) {
            Entity source = event.getSource().getEntity();
            if (source instanceof Player player && wearingTrueHeart(player) && !player.isDeadOrDying()) {
                player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            }
            return;
        }
        // 玩家死亡 → 进入证悟迁移判断
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        boolean offer = tryOpenRedemption(serverPlayer) || tryOpenTrueHeart(serverPlayer);
        if (offer) {
            event.setCanceled(true);
            serverPlayer.getPersistentData().putLong(TAG_PENDING, serverPlayer.level().getGameTime());
            serverPlayer.setHealth(1.0F);
            serverPlayer.setInvulnerable(true);
            // 虚空死亡被取消后原地仍会再触发：先拉回可站立高度
            if (serverPlayer.getY() < serverPlayer.level().getMinBuildHeight() + 1) {
                serverPlayer.teleportTo(serverPlayer.getX(), serverPlayer.level().getMinBuildHeight() + 4, serverPlayer.getZ());
            }
        }
    }

    /** 救赎阶段死亡：佩戴 救赎(+祂者初) 且 stage==1 → 弹「自我救赎」界面 */
    private static boolean tryOpenRedemption(ServerPlayer player) {
        if (stageOf(player) != STAGE_HE_FIRST || !wearingRedemption(player)) {
            return false;
        }
        Networking.sendToPlayer(player, new AscensionScreenMessage(AscensionScreenMessage.KIND_REDEMPTION));
        return true;
    }

    /** 本心阶段死亡：佩戴 本心(+祂者极) 且 stage==2 → 弹「找回自我」界面 */
    private static boolean tryOpenTrueHeart(ServerPlayer player) {
        if (stageOf(player) != STAGE_HE_EXTREME || !wearingTrueHeart(player)) {
            return false;
        }
        Networking.sendToPlayer(player, new AscensionScreenMessage(AscensionScreenMessage.KIND_TRUE_HEART));
        return true;
    }

    // ==================================================================
    // 抉择界面回调（服务端由 AscensionChoiceMessage 调用）
    // ==================================================================

    /** kind=KIND_REDEMPTION / KIND_TRUE_HEART 或 KIND_NORMAL(普通重生，不迁移)。 */
    public static void handleChoice(ServerPlayer player, int kind) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        player.getPersistentData().remove(TAG_PENDING);
        boolean migrated = false;
        if (kind == AscensionScreenMessage.KIND_REDEMPTION
                && stageOf(player) == STAGE_HE_FIRST && wearingRedemption(player)) {
            // 先清被解锁槽（redemption）内的『救赎』，再移除解锁来源『祂者初』，
            // 避免 Curios 回收槽位时把仍在内物品挤回背包。
            destroyItem(player, ModItems.REDEMPTION.get());
            destroyItem(player, ModItems.HE_FIRST.get());
            setStage(player, STAGE_HE_EXTREME);
            giveBound(player, ModItems.HE_EXTREME.get());
            player.sendSystemMessage(Component.translatable("divinebeast.msg.asc.to_extreme")
                    .withStyle(ChatFormatting.GOLD));
            migrated = true;
        } else if (kind == AscensionScreenMessage.KIND_TRUE_HEART
                && stageOf(player) == STAGE_HE_EXTREME && wearingTrueHeart(player)) {
            // 先清被解锁槽（trueheart）内的『本心』，再移除解锁来源『祂者极』。
            destroyItem(player, ModItems.TRUE_HEART.get());
            destroyItem(player, ModItems.HE_EXTREME.get());
            setStage(player, STAGE_HE_TRUE);
            giveBound(player, ModItems.HE_TRUE.get());
            player.sendSystemMessage(Component.translatable("divinebeast.msg.asc.to_true")
                    .withStyle(ChatFormatting.DARK_PURPLE));
            migrated = true;
        }
        // 无论是否迁移：复活到出生点（若用户选普通重生则不迁移但同样回出生点）
        if (migrated) {
            // 证悟迁移：若身处地狱/末地，强制返回主世界出生点
            com.divinebeast.divinebeast.net.AscensionEffectsNoCurios.reviveHome(player);
        } else {
            com.divinebeast.divinebeast.net.AscensionEffectsNoCurios.revive(player);
        }
        player.setInvulnerable(false);
        if (!migrated && kind != AscensionScreenMessage.KIND_REDEMPTION
                && kind != AscensionScreenMessage.KIND_TRUE_HEART) {
            player.sendSystemMessage(Component.translatable("divinebeast.msg.asc.normal")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void destroyItem(Player player, net.minecraft.world.item.Item item) {
        // 清空背包中同名物品
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(item)) {
                stack.shrink(stack.getCount());
            }
        }
        // 清空 curio 槽中同名物品。必须走 Curios 正规 API（setEquippedCurio → setStackInSlot）
        // 而非直接 stack.shrink：置空会触发 onUnequip / CurioChangeEvent，
        // Curios 据此自动移除该物品的 item 级槽位解锁修饰符
        // （祂者初→redemption、祂者极→trueheart），否则这些槽会残留不消失。
        Optional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(player).resolve();
        if (optional.isPresent()) {
            ICuriosItemHandler handler = optional.get();
            for (top.theillusivec4.curios.api.SlotResult result : handler.findCurios(item)) {
                top.theillusivec4.curios.api.SlotContext ctx = result.slotContext();
                handler.setEquippedCurio(ctx.identifier(), ctx.index(), ItemStack.EMPTY);
            }
        }
    }

    private static void giveBound(Player player, net.minecraft.world.item.Item item) {
        ItemStack gift = new ItemStack(item);
        gift.enchant(Enchantments.BINDING_CURSE, 1);
        if (!player.getInventory().add(gift)) {
            player.drop(gift, false);
        }
    }
}
