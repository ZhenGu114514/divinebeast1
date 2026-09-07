# Divine & Beast —— 一键编译与安装说明

> 适用：把本模组加入**任意 Minecraft 1.20.1 + Forge 整合包**或**正在游玩的世界**（无需改存档/新建世界）。

---

## 第一步：准备编译环境（只需一次）

1. 安装 **JDK 17+**：https://adoptium.net/ （装完把 `bin` 加入 PATH，命令行输入 `java -version` 能显示 17 即可）
2. 安装 **Gradle 8.1.1**：下载 https://services.gradle.org/distributions/gradle-8.1.1-bin.zip → 解压 → 把 `gradle-8.1.1\bin` 加入 PATH，命令行 `gradle --version` 能显示 8.1.1 即可

## 第二步：一键编译

双击项目里的 **`build.cmd`**（或在项目目录 `E:\deepseek\divinebeast` 命令行执行 `build.cmd`）。
首次运行需联网下载 Forge/Curios 依赖，耗时较长属正常。

成功后会在 **`build\libs\divinebeast-1.4.1.jar`** 生成模组 jar。

## 第三步：加入整合包 / 正在玩的世界

```
<你的 Forge 1.20.1 实例或整合包根目录>\mods\
├─ divinebeast-1.4.1.jar              ← 本模组（上一步产物）
└─ curios-forge-1.20.1-5.14.1.jar     ← Curios 前置（若包里没有：www.curseforge.com/minecraft/mc-mods/curios）
```

- 单人旧档：关闭游戏 → 放入 → 启动 → **直接进入原存档**即可。槽位/数据由模组数据包自动生效，无需改档。
- 多人服务器：**服务端与每位玩家客户端都放**同样的两个 jar；先停服再放 jar 再启服。
- 其它整合包：要求该包是 **1.20.1 + Forge**；Curios 已有则不要重复放。

## 第四步：进游戏验证

1. 主菜单 Mods → 能看到 Divine & Beast 1.4.1 与 Curios；
2. 进世界打开 Curios 界面（背包右下角按钮）→ 可见「祂」「兽」两个槽位；
3. `/give @s divinebeast:deity` 拖入「祂」槽 → 解锁 5 个时刻槽，成功。

## 注意事项

- 所有饰品**暂无合成配方**：用创造标签「祂 · 兽」或 `/give` 获取（物品 id 见 README/聊天指南）。
- 加 mod 前**备份世界/服务器存档**。
- 游戏内玩法（祂两阶段/兽三阶段、C 键开关等）详见根目录 `README.md`。
- **对外发布整合包**：`mods.toml` 目前 license 为 `All rights reserved`（占位），分发前请把 license 改为开放条款（如 MIT），并通常需标注模组来源。
