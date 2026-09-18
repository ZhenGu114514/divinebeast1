package com.divinebeast.divinebeast.block;

import com.divinebeast.divinebeast.DivineBeastMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 三个 boss 的「同名方块」。
 *
 * <p>外观与 泥土 / 下界岩 / 末地石 <b>完全一致</b>（模型直接引用原版贴图，不额外做材质），
 * 但是三个独立方块：可以分解成各自的锭（见 {@code data/divinebeast/recipes/*_ingot_from_block.json}），
 * 再由锭合成武器与盔甲。
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, DivineBeastMod.MOD_ID);

    /** 「我」块：外观同泥土 */
    public static final RegistryObject<Block> SELF_BLOCK =
            BLOCKS.register("self_block", () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIRT)));
    /** 「兽」块：外观同下界岩 */
    public static final RegistryObject<Block> BEAST_BLOCK =
            BLOCKS.register("beast_block", () -> new Block(BlockBehaviour.Properties.copy(Blocks.NETHERRACK)));
    /** 「祂」块：外观同末地石 */
    public static final RegistryObject<Block> HE_BLOCK =
            BLOCKS.register("he_block", () -> new Block(BlockBehaviour.Properties.copy(Blocks.END_STONE)));

    // 建筑变体：台阶 / 楼梯 / 墙（三种材料各一套）
    public static final RegistryObject<Block> SELF_STAIRS = stairs("self_stairs", SELF_BLOCK);
    public static final RegistryObject<Block> SELF_SLAB = slab("self_slab", SELF_BLOCK);
    public static final RegistryObject<Block> SELF_WALL = wall("self_wall", SELF_BLOCK);
    public static final RegistryObject<Block> BEAST_STAIRS = stairs("beast_stairs", BEAST_BLOCK);
    public static final RegistryObject<Block> BEAST_SLAB = slab("beast_slab", BEAST_BLOCK);
    public static final RegistryObject<Block> BEAST_WALL = wall("beast_wall", BEAST_BLOCK);
    public static final RegistryObject<Block> HE_STAIRS = stairs("he_stairs", HE_BLOCK);
    public static final RegistryObject<Block> HE_SLAB = slab("he_slab", HE_BLOCK);
    public static final RegistryObject<Block> HE_WALL = wall("he_wall", HE_BLOCK);

    private ModBlocks() {
    }

    private static RegistryObject<Block> stairs(String id, RegistryObject<Block> base) {
        return BLOCKS.register(id, () -> new StairBlock(base.get().defaultBlockState(),
                BlockBehaviour.Properties.copy(base.get())));
    }

    private static RegistryObject<Block> slab(String id, RegistryObject<Block> base) {
        return BLOCKS.register(id, () -> new SlabBlock(BlockBehaviour.Properties.copy(base.get())));
    }

    private static RegistryObject<Block> wall(String id, RegistryObject<Block> base) {
        return BLOCKS.register(id, () -> new WallBlock(BlockBehaviour.Properties.copy(base.get())));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
