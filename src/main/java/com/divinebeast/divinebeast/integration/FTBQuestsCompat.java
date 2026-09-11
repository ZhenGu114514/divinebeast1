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
 * {@code divinebeast_guide.snbt} 写入该目录。章节内全部文案走 JSON translate 键
 * （divinebeast.ftb.*），随游戏语言显示中/英。
 *
 * <p><b>更新策略</b>（旧版是"目标文件已存在就永远跳过"，导致模组加了新任务也永远进不了
 * 已经装过本模组的存档，必须手动删文件）：
 * <ol>
 *   <li>每次启动算出<b>内置章节内容的指纹</b>（长度 + CRC32），与旁边
 *       {@code .divinebeast_guide.injected} 里记的上一次注入指纹比对；</li>
 *   <li>一致 → 说明内置内容没变，<b>完全不碰目标文件</b>
 *       （玩家/整合包在游戏内用 FTB 编辑器改过的内容会被保留）；</li>
 *   <li>不一致（模组更新了章节）→ <b>覆盖写入</b>，覆盖前把旧文件备份为
 *       {@code .divinebeast_guide.snbt.bak}。</li>
 * </ol>
 * 因为所有既有任务的 id 从未改动，覆盖章节<b>不会丢失任何任务进度</b>
 * （进度按任务 id 记在存档里）。
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
    /**
     * 记录"上次注入的是哪一份内容"的指纹文件。故意用非 .snbt 后缀 + 前导点，
     * 既不会被 FTB Quests 当成章节加载，也不会出现在普通文件列表里。
     */
    private static final String MARKER_FILENAME = ".divinebeast_guide.injected";
    /** 覆盖前保留的旧文件备份（只保留最近一份） */
    private static final String BACKUP_FILENAME = ".divinebeast_guide.snbt.bak";

    private FTBQuestsCompat() {
    }

    /** 仅在 FTB Quests 已安装时注册（类本身可安全加载）。 */
    public static void registerIfPresent() {
        if (ModList.get().isLoaded("ftbquests")) {
            MinecraftForge.EVENT_BUS.addListener(FTBQuestsCompat::onServerStarting);
            LOGGER.info("[{}] FTB Quests 已安装：注册任务书章节注入。", DivineBeastMod.MOD_ID);
        }
    }

    /** Forge 服务端启动早期（早于 FTB Quests 自己的 load），按内容指纹决定是否写入。 */
    private static void onServerStarting(ServerStartingEvent event) {
        try {
            Path chaptersDir = FMLPaths.CONFIGDIR.get()
                    .resolve("ftbquests").resolve("quests").resolve("chapters");
            Path target = chaptersDir.resolve(CHAPTER_FILENAME);
            Path marker = chaptersDir.resolve(MARKER_FILENAME);

            byte[] builtin = readResource();
            if (builtin == null) {
                LOGGER.warn("[{}] 未找到内置任务书资源 {}", DivineBeastMod.MOD_ID, RESOURCE_PATH);
                return;
            }
            String newFingerprint = fingerprint(builtin);
            String lastFingerprint = readFingerprint(marker);
            int questCount = countQuestEntries(builtin);

            if (Files.exists(target)) {
                if (newFingerprint.equals(lastFingerprint)) {
                    // 内置内容与上次注入的一致 → 不覆盖，保留游戏内的编辑
                    LOGGER.info("[{}] 任务书章节已是最新（指纹 {}，含 {} 个任务条目），跳过。",
                            DivineBeastMod.MOD_ID, newFingerprint, questCount);
                    return;
                }
                try {
                    Files.copy(target, chaptersDir.resolve(BACKUP_FILENAME),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    LOGGER.warn("[{}] 备份旧任务书章节失败（继续覆盖）", DivineBeastMod.MOD_ID, ex);
                }
                LOGGER.info("[{}] 检测到内置任务书章节有更新（{} -> {}），覆盖写入；旧文件已备份为 {}。",
                        DivineBeastMod.MOD_ID, lastFingerprint.isEmpty() ? "旧版本" : lastFingerprint,
                        newFingerprint, BACKUP_FILENAME);
            } else {
                LOGGER.info("[{}] 未发现任务书章节，注入内置章节。", DivineBeastMod.MOD_ID);
            }

            Files.createDirectories(chaptersDir);
            Files.write(target, builtin);
            Files.writeString(marker, newFingerprint, java.nio.charset.StandardCharsets.UTF_8);
            LOGGER.info("[{}] 已写入 FTB Quests 章节 -> {}（指纹 {}，含 {} 个任务条目）",
                    DivineBeastMod.MOD_ID, target, newFingerprint, questCount);
        } catch (IOException ex) {
            LOGGER.warn("[{}] 注入 FTB Quests 章节失败", DivineBeastMod.MOD_ID, ex);
        }
    }

    /** 读取 jar 内的内置章节；资源缺失返回 null。 */
    private static byte[] readResource() throws IOException {
        try (InputStream in = FTBQuestsCompat.class.getResourceAsStream(RESOURCE_PATH)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    /** 读取上次注入的指纹；文件不存在或读失败都返回空串。 */
    private static String readFingerprint(Path marker) {
        try {
            if (!Files.exists(marker)) {
                return "";
            }
            return Files.readString(marker, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            return "";
        }
    }

    /**
     * 内容指纹：字节长度 + CRC32。只用于判断"内置章节这次和上次是不是同一份"，
     * 不用于任何安全用途，因此 CRC32 足够。
     */
    private static String fingerprint(byte[] data) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        return data.length + "-" + Long.toHexString(crc.getValue());
    }

    /**
     * 粗略统计内置章节里的任务条目数，仅用于日志自检
     * （方便一眼确认"打进 jar 的章节到底有几个任务"，与游戏里看到的条数对照）。
     *
     * <p>章节文件里每个任务对象都以两格 tab 缩进的 {@code {} 或 {@code ,{} 开头，
     * 而章节本身缩进为 0、任务内部的 tasks/rewards 对象缩进为三格以上，据此区分。
     */
    private static int countQuestEntries(byte[] data) {
        int count = 0;
        String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        for (String line : text.split("\n", -1)) {
            String trimmed = line.strip();
            if (!trimmed.equals("{") && !trimmed.equals(",{")) {
                continue;
            }
            if (line.startsWith("\t\t") && !line.startsWith("\t\t\t")) {
                count++;
            }
        }
        return count;
    }
}
