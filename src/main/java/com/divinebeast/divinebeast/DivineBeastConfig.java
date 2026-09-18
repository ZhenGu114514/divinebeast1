package com.divinebeast.divinebeast;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * 模组配置（COMMON）。
 *
 * <p>注册后在「模组列表 → Divine &amp; Beast 祂 → 配置」里会直接出现这些开关，
 * 不需要占用键位。目前有两项：
 *
 * <ul>
 *   <li><b>htrue_true_damage</b> —— 真者祂攻击时附加的「天罚」等值真伤与「归墟」等值虚空伤害；</li>
 *   <li><b>htrue_execution</b> —— 真者祂「神威·诛灭」（多段 / 范围 / 逻辑致死 / 兜底抹除）。</li>
 * </ul>
 *
 * <p>注意：『祂』救赎的两项效果（生物不主动把你选为目标 / 攻击未带印记生物改为回满血）
 * <b>不进配置</b>，它们合并在同一个游戏内按键上（默认未绑定，见 {@code ClientKeybinds}
 * 的「救赎形态 开关」）。
 *
 * <p>注意：「10 光之领域」的范围伤害<b>保持原样</b>，仍由游戏内按键控制
 * （默认未绑定键位，见 {@code ClientKeybinds}），不进配置。
 *
 * <p>用 COMMON 而非 SERVER 类型，是为了让它出现在模组列表里（SERVER 类型只在存档内配置）。
 * 判定发生在服务端，因此配置以服务端（单人游戏即本机）的值为准。
 */
public final class DivineBeastConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** 真者祂：攻击附加的等值真伤（天罚）与等值虚空伤害（归墟） */
    public static final ForgeConfigSpec.BooleanValue HTRUE_TRUE_DAMAGE = BUILDER
            .comment("真者祂：攻击时附加「天罚」等值真伤 + 「归墟」等值虚空伤害 (default: true)",
                    "True One: extra equal true damage + equal void damage on every attack")
            .define("htrue_true_damage", true);

    /** 真者祂：神威·诛灭（多段 / 范围 / 逻辑致死 / 兜底抹除） */
    public static final ForgeConfigSpec.BooleanValue HTRUE_EXECUTION = BUILDER
            .comment("真者祂：神威·诛灭 —— 命中时依次施展 多段 / 范围 / 逻辑致死 / 兜底抹除 (default: true)",
                    "True One: Omnipotent Execution - multi-hit / area / logic-kill / fallback erasure",
                    "（游戏内绑定按键后也可用该键临时开关；两者都为开才生效）")
            .define("htrue_execution", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private DivineBeastConfig() {
    }

    /** 在 mod 构造期调用：把配置注册到模组列表。 */
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "divinebeast-common.toml");
    }
}
