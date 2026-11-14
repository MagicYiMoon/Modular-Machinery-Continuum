# NECO 系统使用指南（MMC 2.0.0）

MMC 适配了 NovaEngineering Core 的 ES、EC、EF 及旧 HyperNet 系统。结构、生存配方、模块物品和数值由整合包作者提供；仓库示例不是新星工程整合包的完整配置。

## 依赖与示例

使用 Java 17、Minecraft Forge 1.20.1。ES／EC／EF 的 ME 功能需要 AE2 及其前置 GuideME；ES 气体矩阵还需要 Mekanism 和 Applied Mekanistics。HyperNet 后端不依赖 AE2，示例 `.zs` 脚本需要 CraftTweaker 14。

| 系统 | 示例及安装说明 |
| --- | --- |
| ES 存储 | [ES 最小结构](../examples/neco-estorage/README.md) |
| EC 合成 CPU | [EC 最小结构](../examples/neco-ecalculator/README.md) |
| EF 制造 | [EF 最小结构](../examples/neco-efabricator/README.md) |
| HyperNet 算力与研究 | [机器、脚本、研究及终端](../examples/neco-hypernet/README.md) |

只安装需要的示例，不覆盖同名机器。机器 JSON 放入 `config/modularmachinery/machinery/`，配方放入 `config/modularmachinery/recipes/`，研究放入 `config/modularmachinery/research/`，CT 脚本放入 `scripts/`。新增独立控制器遵守[启动注册与同步规则](SERVER_DEFINITION_SYNC_ZH.md)。

## ES：矩阵存储

S4／S6／S9 主机使用固定机器 ID `extendable_digital_storage_subsystem_l4`、`_l6`、`_l9`，使用对应 ES 专用控制器。结构内的专用驱动器、ME 通讯接口和能源单元由主机绑定。

持矩阵潜行右键驱动器插入，主手空手潜行右键取出。物品矩阵最多存储 315 种资源，流体／气体矩阵最多 25 种；实际数量容量受类型开销影响。矩阵不能放入普通 ME 驱动器或 ME 箱子。分区、模糊及升级配置通过 AE2 元件工作台操作。

内部能源单元以 AE 为单位，L4／L6／L9 容量为 10,000,000／100,000,000／1,000,000,000 AE，不是 FE。它们通过 ME 网格充放电，主机和受支持矩阵的待机费用由网格能源服务处理。右键主机查看容量、类型、能源及矩阵列表，左侧切换实时统计。

## EC：合成 CPU

EC 使用固定 ID `extendable_calculator_subsystem_l4`、`_l6`、`_l9` 的专用主机，接入 AE2 合成 CPU。线程核心决定可用任务槽，晶阵提供合成内存，并行部件影响处理能力；这些槽与 MMC 工厂配方线程不同。

持 EC 闪存晶阵潜行右键驱动器插入，主手空手潜行右键取出。EC 闪存晶阵与 ES 存储矩阵不能互换。连接有电、有频道的 ME 网络后，通过 AE2 正常下单，主机界面可查看任务和取消状态。

暂时断电／缺频道时任务可等待恢复。实际拆除节点、结构失效或网格分裂可能触发 AE2 的任务取消，不保证任意外部生产机器都能恢复在途加工。成型大型模型需要预留空间，正式结构可使用 `workers` 动态段安排工作部件与尾部，详见示例。

## EF：制造与冷却

EF 使用 `extendable_fabricator_subsystem_l4`、`_l6`、`_l9` 专用主机及工作核心、样板总线、ME 通讯接口和并行核心。样板总线有 72 格，存入合成样板后通过 ME 网络下单。加工、切石和锻造样板可以保存，但不由 EF 发布执行。

主机界面显示核心、队列、实际并行、累计制造量和近期制造速度。左侧按钮控制超频和主动冷却。普通模式每 20 tick 执行工作周期，工作核心有 500,000 AE 缓存，基础单次合成消耗 100 AE，产物回网另付 AE2 传输费用。

主动冷却时每次合成还需要 5 冷却单位。转换在 common 配置 `[neco.efabricator]` 的 `coolants` 中设置，格式为 `输入流体|输入mB|输出流体|输出mB|冷却单位`，无输出用 `-|0`。必须使用已注册流体；缺失或歧义的流体不形成有效转换，气体不能冒充流体。缺液或输出堵塞时等待；主动冷却去除对应超频耗能惩罚与并行负面修正，并按工作核心数缩短间隔，最短 1 tick。

## HyperNet 算力

机器通过 `hypernet.role` 声明角色：`center`、`processor`、`node`、`database`、`research_station` 或 `terminal`。中心与处理器依赖指定工厂预设核心线程实际运行；填写线程名不会自动创建配方。字段和工作脚本以 HyperNet 示例为起点。

处理器在真实升级总线内同时安装 CPU／GPU 与 RAM，按服务端 `modules` 表的 `upgrade`、`kind`、`capacity`、`energy` 参数提供算力。模块是作者映射的已有物品，不读取物品自带数字作为能力。供给受模块、RAM、供电和热量限制；模块费用目前使用处理器绑定的 MMC 普通 FE 输入舱。

中心省略 `connection-card` 时使用内置 `modularmachinery:hypernet_connect_card`。手持卡片右键中心写卡，再右键处理器或消费节点连接，遵守归属、同维度、区块加载和运行条件。已有自定义卡片配置保持原物品。

配方算力输入示例：

```json
{"type":"modularmachinery:computation","io-type":"input","amount":2.0,"triggerFailure":false}
```

按实际工作 tick 消费，通常随实际批量放大，不跨 tick 储存。默认不足时保留进度暂停；`triggerFailure` 开启后使用配方失败策略。公平调度是可选 MMC 扩展，默认按请求顺序消费。监控和 TOP 查询只读，不产生算力；处理器平均负载取最近最多 100 个连续工作 tick。

计算产热，超过 85% 热量后效率下降，过热锁定后需冷却至零恢复。主动冷却和中心电路维修使用作者定义的配方，不自动生成材料或费用。

## 研究与终端

研究定义包含 ID、显示名、科技等级、所需点数、最低每 tick 算力、前置与说明。完整 JSON 见 [basic_materials.json](../examples/neco-hypernet/research/basic_materials.json)。数据库容量限制完成与进行中的研究条目，服务核心持续运行，输入 FE 倍率为 `1 + 有效已完成成果数 × 0.1`。

研究站、数据库和中心必须真实运行并连接同一网络，每网最多一台研究站。智能接口 `overclocking` 范围为 1..5；研究按实际交付算力增长，取消保留数据库点数，不能换网继续原任务。

终端保留机器名 `hypernet_terminal`、角色 `terminal`、`requires-blueprint:false`、`has-factory:false`，使用 `modularmachinery:hypernet_terminal_controller`，无需配方线程。每网最多一台终端。向右下卡槽放已写中心的卡片；连接时 2,000 FE/t，未连接时 200 FE/t，单个输入舱须能支付全额。

左键选研究，滚轮／滚动条翻页，左键开始图标启动，右键两次取消。创造模式且具有 OP2 权限的玩家可中键快速研究，仍需满足前置、等级、数据库和正常核心条件，不覆盖忙碌任务。

配方研究门槛在开工前检查，不消耗成果、不随批量放大；已启动配方不因数据库中途离线而直接取消。脚本可使用 `.requireResearch("basic_materials")`，可视化入口为配方输入栏 `+ → 研究门槛`，最多 64 项且要求全部满足。

## 可视化配置

执行 `/modularmachinery gui`，选机器后进入“算力/说明”。网络页配置角色参数，研究页创建或修改全局研究、前置、图标和说明。机器参数与研究分别保存，已有研究 ID 固定，GUI 暂无删除入口；修改定义不授予成果、不清空数据库。

普通图标使用 `preview-item`；带物品数据时使用 `preview-stack` 完整 SNBT，不能同时填写。图标数量归一为 1，SNBT 上限 16 KiB、嵌套最多 64 层。图标页支持背包复制与 NBT 编辑，不消耗真实物品。等级标签仅供显示，实际能力仍由参数决定。服务端重载后同步研究定义，客户端不必手动复制 JSON。

## 保留项目与来源

循环研究保留占位。新式模块化服务器仅有装配库存、模块目录和参数预览后端，没有可用装配器方块、服务器物品或新式运行网络；与本指南的旧 HyperNet 不互通。历史曲线未提供，统计为实时或有限窗口均值。

代码和资源来源见 [THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md)，联动版本见[依赖说明](../libs/optional/README.md)。
