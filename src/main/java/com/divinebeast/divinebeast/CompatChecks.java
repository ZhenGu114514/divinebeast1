package com.divinebeast.divinebeast;

import net.minecraftforge.fml.loading.moddiscovery.ModList;

/**
 * 可选前置检查工具。
 *
 * <p>本类<b>不</b>引用任何 Curios 类，可在任意代码中安全调用；
 * 真正带 Curios 引用的 CuriosCompat / CuriosEffects 只会在确认
 * Curios 已安装后的分支中被引用（类加载发生在运行时用到它们时）。
 */
public final class CompatChecks {

    public static final String CURIOS_MOD_ID = "curios";

    private CompatChecks() {
    }

    public static boolean curiosLoaded() {
        return ModList.get().isLoaded(CURIOS_MOD_ID);
    }
}
