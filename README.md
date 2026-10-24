# Modular Machinery: Continuum（MMC，模块化机械：延续）

将 Modular Machinery Community Edition 2.3.2 从 Forge 1.12.2 移植到 **Minecraft 1.20.1 / Forge 47.x**，供整合包作者定义多方块机器与配方。本文核对日期：**2026-10-24**。

发布文件：`build/libs/modularmachinerycontinuum-1.2.0-1.20.1-all.jar`。内部模组 ID 继续使用 `modularmachinery`，魔法接口继续使用 `modularmagic`，配置目录和物品 ID 不随显示名称改变。客户端和服务器应安装同一版 JAR；当前网络协议为 **32**。

## 1.2.0 通量网络联动

新增 Flux Networks 7.2.1.15 可选联动：通量节点向能源输入仓供能，能源输出仓向通量插头送电。原生通量接口与 FE 共用库存，保留容量、方向和传输限制。安装和配置见[通量网络教程](docs/FLUX_NETWORKS_ZH.md)。

## 1.1.0 独立控制器

每种机器现在独立注册普通控制器，按 `has-factory` 或全局默认配置注册工厂控制器。控制器固定绑定机器，名称和外壳颜色来自机器定义；可在 `/modularmachinery gui` 的“控制器外观”中输入 RGB、使用调色板并预览。

旧通用控制器、旧方块实体 ID、蓝图和配置路径保留。颜色和配方可重载；新增机器身份、新增工厂控制器需客户端与服务端一起重启。分发配置时同时保留 `controller-registry.json`，详细迁移和资源包用法见[独立控制器教程](docs/DEDICATED_CONTROLLERS_ZH.md)。

## 教程入口

- [文档目录与阅读顺序](docs/README.md)
- [玩家使用教程](docs/USER_GUIDE_ZH.md)：安装、控制器、木棍自动装配、蓝图、投影与常用命令。
- [整合包作者教程](docs/PACK_AUTHOR_GUIDE_ZH.md)：机器制作、配方、可视化编辑、线程、智能接口、GeckoLib 动态模型和可选资源联动。
- [JSON 补充参考](docs/ADDING_MACHINES.md)：结构、NBT、修饰符、动态结构和智能接口示例。
- [可选社区依赖的来源与版本](libs/optional/README.md)

使用教程以这些入口为准。本公开包不含内部开发日记、审计文件、测试世界和本机日志。

## 安装与当前功能

基础模组可单独运行，**已内嵌 GeckoLib Forge 4.8.4**。普通机器、工厂线程、批量倍率、物品／流体／FE、智能接口、升级与修饰符、蓝图 3D 预览、世界投影和生存自动装配均已实现。

机器和配方分别放在 `config/modularmachinery/machinery/`、`config/modularmachinery/recipes/`，支持递归扫描子目录；修改后执行 `/reload`。JAR 的 `default_*` 仅在配置主目录首次创建时提供模板，不覆盖已有配置，也不会自动补回删除的文件。

管理员可用 `/modularmachinery gui` 编辑已有机器名称和配方；机器结构通过建造工具、`/modularmachinery register <机器名>` 或 JSON 创建。编辑器的具体支持范围见作者教程。

工厂的线程数量和配方批量倍率相互独立。同一工厂内同种配方最多占一个线程，线程内仍可批量加工，其他线程可执行不同配方。

普通 FE 仓默认容量现从 tiny 的 1000 FE 递增到 ultimate 的 2147483647 FE，输入／输出仓共用容量配置。已有配置需要自行合并新值，见[容量配置教程](docs/PACK_AUTHOR_GUIDE_ZH.md)。

## 可选联动

下列模组均需另装，发布包只内嵌 GeckoLib。可选模组自身的必需前置也需要安装；客户端与服务器使用相同依赖组合。

| 联动 | 当前用途／验证目标 |
| --- | --- |
| JEI 15.62.0.216 | 配方、结构预览、分层与投影入口 |
| CraftTweaker 14.0.60 | 脚本配方、机器修改、核心线程与 GeckoLib 绑定 |
| AE2 15.4.11 + GuideME 20.1.7 | ME 物品／流体总线、样板供应等 |
| Mekanism 10.4.16.80 | 气体仓与气体配方 |
| Flux Networks 7.2.1.15 | 普通 FE 能源仓原生通量接口、插头／节点连接 |
| Applied Mekanistics 1.4.3 + AE2 + Mekanism | ME 气体输入／输出总线 |
| The One Probe 10.0.4 | 控制器、配方进度与阻塞原因提示 |
| Botania（Forge 1.20.1）+ Patchouli + Curios | 魔力输入／输出 |
| Blood Magic 3.3.8-50 + Patchouli | 生命源质 LP 与区块恶魔意志 |
| Nature’s Aura 39.4 + Patchouli | 世界灵气输入／输出 |
| 社区 Astral Sorcery 1.20.1 固定源码构建 | 真实星能网络输入／输出、星座检测 |
| Extra Utilities Rebirth 内部版本 1.7.3 | 相邻虹彩发电机运行检测 |

基础版本已在 Forge **47.3.0** 验证；包含上述新联动的组合在 **47.4.23** 验证。安装 Rebirth 1.7.3 请使用已验证的 **47.4.23**，它不能在 47.3.0 正常加载。社区 Astral 的固定提交和下载／构建说明见[依赖说明](libs/optional/README.md)。

虹彩使用相邻检测，与源版 Extra Utilities 2 的玩家频率网络不同；不提供 GP。Rebirth 1.7.3 自身存在两组无效物品标签，另附[可选数据包修复说明](examples/datapacks/mmr-extrautilitiesrebirth-1.7.3-tags/README.md)。

**源质、GP、Impetus 的六个接口仍保留占位**，暂不执行对应资源需求。其他第三方配方／能源适配、配方复制和可视化编辑器进一步扩展暂缓。注册方块不代表已实现源版所有行为。

## 构建与验证

开发环境为 **Java 17、Gradle 8.8、ForgeGradle 6.0.54、MixinGradle 0.7.38**，映射使用 Mojang official 1.20.1。默认 Forge 版本仍为 `gradle.properties` 中的 47.3.0。

编译需要可选 API，即使运行时不安装这些模组。先按[依赖说明](libs/optional/README.md)将固定社区 JAR 放入 `libs/optional/`；其他 Maven 依赖首次构建时下载：

```powershell
.\gradlew.bat build jarJar reobfJarJar
```

缓存完整后可用 `--offline`。指定新版 Forge 的 PowerShell 写法：

```powershell
.\gradlew.bat build jarJar reobfJarJar '-Pforge_version=47.4.23'
```

服务端回归方法与实际通过记录见 [VERIFY.md](VERIFY.md)。1.1.0 的独立、全联动和全新配置环境覆盖固定控制器身份、实际加工与生存挖掘、颜色编辑保护、投影和资源包。客户端新界面与染色画面仍需实际验证；本模组不保证兼容所有 1.12.2 整合包。版本变化见[发布说明](RELEASE_NOTES.md)。

## 许可与来源

本项目为 GPL-3.0-only 衍生作品，保留原作者署名，见 [LICENSE](LICENSE) 与[第三方声明](src/main/resources/META-INF/THIRD-PARTY-NOTICES.md)。分发时应按 GPL 提供对应源码。原作者包括 HellFirePvP、wiiv、KasumiNova、youyihj、ikexing 及其他社区贡献者；社区依赖的许可由各自项目声明。

完整发行包构建方法见 [BUILDING.md](BUILDING.md)。
