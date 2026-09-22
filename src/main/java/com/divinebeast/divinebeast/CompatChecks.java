package com.divinebeast.divinebeast;

import net.minecraftforge.fml.ModList;

/**
 * 可选前置检查工具。
 *
 * <p>本类<b>不</b>引用任何 Curios / 车万女仆类，可在任意代码中安全调用；
 * 真正带 Curios 引用的 CuriosCompat / CuriosEffects 只会在确认
 * Curios 已安装后的分支中被引用（类加载发生在运行时用到它们时）。
 */
public final class CompatChecks {

    public static final String CURIOS_MOD_ID = "curios";
    /** 车万女仆的 modid（女仆适配的判断统一走这里） */
    public static final String MAID_MOD_ID = "touhou_little_maid";

    private CompatChecks() {
    }

    public static boolean curiosLoaded() {
        return ModList.get().isLoaded(CURIOS_MOD_ID);
    }

    /** 是否装了车万女仆（新手礼盒多给一份、额外补发真者祂等都看它）。 */
    public static boolean maidLoaded() {
        return ModList.get().isLoaded(MAID_MOD_ID);
    }
}
