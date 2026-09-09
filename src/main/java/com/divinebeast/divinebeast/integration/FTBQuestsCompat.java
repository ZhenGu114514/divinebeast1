package com.divinebeast.divinebeast.integration;

import com.divinebeast.divinebeast.DivineBeastMod;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * FTB Quests 适配：把本模组自带的饰品图鉴章节注入服务端任务书。
 *
 * <p>FTB Quests（Forge 1.20.1）不在数据包中读任务，而是在服务端启动时扫描
 * {@code config/ftbquests/quests/chapters/*.snbt}。本类在服务端启动早期把内置的
 * {@code divinebeast_guide.snbt} 复制到该目录：目标文件不存在时创建（幂等，不覆盖
 * 玩家/整合包已有文件）。章节内全部文案走 JSON translate 键（divinebeast.ftb.*），
 * 随游戏语言显示中/英。
 *
 * <p>本类不引用任何 FTB Quests 类：只依赖 Forge 事件与文件 IO，因此 FTB Quests
 * 为可选前置——未安装时本类不注册监听，无副作用。
 */
public final class FTBQuestsCompat {

    private static final Logger LOGGER = LogManager.getLogger();

    /** 内置章节资源（jar 内路径，含前导 /） */
    private static final String RESOURCE_PATH = "/assets/divinebeast/ftbquests/divinebeast_guide.snbt";
    /** 写入 FTB 任务书的目标文件名 */
    private static final String CHAPTER_FILENAME = "divinebeast_guide.snbt";

    private FTBQuestsCompat() {
    }

    /** 仅在 FTB Quests 已安装时注册（类本身可安全加载）。 */
    public static void registerIfPresent() {
        if (ModList.get().isLoaded("ftbquests")) {
            MinecraftForge.EVENT_BUS.addListener(FTBQuestsCompat::onServerStarting);
            LOGGER.info("[{}] FTB Quests 已安装：注册任务书章节注入。", DivineBeastMod.MOD_ID);
        }
    }

    /** Forge 服务端启动早期（早于 FTB Quests 自己的 load），幂等写入章节。 */
    private static void onServerStarting(ServerStartingEvent event) {
        try {
            Path chaptersDir = FMLPaths.CONFIGDIR.get()
                    .resolve("ftbquests").resolve("quests").resolve("chapters");
            Path target = chaptersDir.resolve(CHAPTER_FILENAME);
            if (Files.exists(target)) {
                return; // 已存在（用户可能已编辑），不覆盖
            }
            try (InputStream in = FTBQuestsCompat.class.getResourceAsStream(RESOURCE_PATH)) {
                if (in == null) {
                    LOGGER.warn("[{}] 未找到内置任务书资源 {}", DivineBeastMod.MOD_ID, RESOURCE_PATH);
                    return;
                }
                Files.createDirectories(chaptersDir);
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("[{}] 已注入 FTB Quests 章节 -> {}", DivineBeastMod.MOD_ID, target);
            }
        } catch (IOException ex) {
            LOGGER.warn("[{}] 注入 FTB Quests 章节失败", DivineBeastMod.MOD_ID, ex);
        }
    }
}
