# Modular Machinery: Continuum

**MMC · 模块化机械：延续 · Forge 1.20.1 · 2.0.0**

A GPL-3.0 derivative port of Modular Machinery Community Edition for Forge 1.20.1, providing modpack authors with configurable multiblock machines and recipes.

基于 Modular Machinery Community Edition 的 Forge 1.20.1 衍生移植，为整合包作者提供自定义多方块机器与配方的工具。

## 安装

使用 **Java 17**，推荐 **Forge 47.4.23**。将 `modularmachinerycontinuum-2.0.0-1.20.1-all.jar` 放入两端的 `mods` 目录，替换旧版 MMC。GeckoLib Forge 4.8.4 已内嵌；可选联动与它们的前置需要另装。

升级前备份存档与配置，保留 `config/modularmachinery/controller-registry.json`。客户端和服务器须使用相同 MMC 版本及相容的联动依赖。运行中的机器／配方定义由服务端同步，本地作者 JSON 保留；新增独立控制器仍须在服务端启动时注册，客户端缺少对应注册项时会缓存注册信息并提示重启一次。详见[同步说明](docs/SERVER_DEFINITION_SYNC_ZH.md)。

## 功能

- JSON 多方块定义，独立普通／工厂控制器、染色、结构校验及升级。
- 物品、流体、FE、催化剂、耐久、候选输入与随机输出、批量、触发及智能接口。
- 工厂核心线程与脚本接口；同一配方 ID 最多占一个线程，线程内允许批量加工。
- 管理员可视化编辑器：名称与格式预览、需求搜索、配方复制、联动资源、物品／流体 NBT 数据。
- JEI 三维预览、分层和投影，木棍生存自动搭建，工具选区导出机器。
- GeckoLib 模型／动画绑定；模型和动画由整合包作者提供。
- 环境检测器、定向 FE 输出端口，以及可选的魔力／空气／余烬等资源部件。
- AE2 无线取料搭建及缺料自动合成；需要背包内可用的无线终端、网络权限及合成资源。
- NECO ES 大容量矩阵存储、EC 合成 CPU、EF 制造系统；ME 功能需要 AE2，气体矩阵另需 Applied Mekanistics。
- HyperNet 计算中心、CPU／GPU／RAM 模块参数、算力配方需求、热量／冷却与维护，以及研究数据库、研究站和独立网络终端。
- 算力与研究可视化编辑、等级标签和说明、含 NBT 的研究图标；等级标签仅供显示，实际能力由参数决定。

机器定义放入 `config/modularmachinery/machinery/`，配方放入 `config/modularmachinery/recipes/`。首次配置会生成模板，已有文件不被覆盖。执行 `/modularmachinery gui` 打开管理员编辑器；机器结构通过工具、命令或 JSON 创建。

## 可选联动

JEI、The One Probe、CraftTweaker 14、AE2／GuideME、Mekanism／Applied Mekanistics、Flux Networks、Botania、Blood Magic、Nature’s Aura、PneumaticCraft、Embers，以及部分 GTCEu 普通仓室和外部配方导入。社区 Astral Sorcery 和 Extra Utilities Rebirth 使用固定的实验版本；准确版本与来源见[依赖说明](libs/optional/README.md)。

外部模组不随 MMC 分发，只有 GeckoLib 被内嵌。源质、GP、Impetus 仍为占位；GTCEu 激光／特殊 ME／摆放代理尚未提供。详细支持范围见对应教程。

编辑器操作见[模板编辑](docs/ADAPTER_TEMPLATE_EDITOR_ZH.md)、[背包选择](docs/INVENTORY_PICKER_ZH.md)和[作者教程](docs/PACK_AUTHOR_GUIDE_ZH.md)。NECO 的结构、配方、模块物品和参数由整合包作者配置，示例不代表新星工程的完整结构或平衡；研究终端及算力字段见[适配说明](docs/NECO_GUIDE_ZH.md)。循环研究保留占位，新式模块化服务器装配器与历史曲线不包含在本次可用功能中。

2.0.0 的网络协议为 **49**，升级须同时替换客户端和服务器。

## 文档

- [教程目录](docs/README.md)
- [玩家教程](docs/USER_GUIDE_ZH.md)
- [整合包作者与 GeckoLib 教程](docs/PACK_AUTHOR_GUIDE_ZH.md)
- [NBT 编辑教程](docs/NBT_EDITOR_ZH.md)
- [从源码构建](BUILDING.md)
- [开发测试方法](TESTING.md)
- [中英文更新说明](RELEASE_NOTES.md)

## 许可与来源

本项目遵循 [GPL-3.0-only](LICENSE)，保留 HellFirePvP、wiiv、KasumiNova、youyihj、ikexing 及其他社区贡献者署名。NECO 适配代码与资源来源、GeckoLib 和字体等第三方许可见[第三方声明](THIRD-PARTY-NOTICES.md)。分发 JAR 时请同时提供对应源码。

- [Modular Machinery 原项目](https://github.com/HellFirePvP/ModularMachinery)
- [Modular Machinery Community Edition](https://github.com/KasumiNova/ModularMachinery-Community-Edition)
- [MMC 仓库与问题反馈](https://github.com/MagicYiMoon/Modular-Machinery-Continuum)
