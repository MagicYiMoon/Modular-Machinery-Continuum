# Modular Machinery: Continuum 2.0.3 — Minecraft 1.20.1

## 中文

- 修复木棍自动搭建替换能源仓后沿用旧档位容量和传输上限的问题。修复流体仓放置后未应用对应档位容量的问题。
- 修正仓室配方的档位数据。内置微型／小型配方现在产出正确档位，更高档仍可由整合包自定义配方。
- 升级总线界面显示已装模块的信息，并将当前档位不可用的槽位暗化。
- 新增模块化拆卸工具。用铁锭、木棍和模块化锭无序合成，主手持工具潜行右键已成型且空闲的控制器。控制器、蓝图、共享结构和受保护方块保留；掉落优先进入可用的无线 ME 网络，其次是玩家背包，放不下的落地。

网络协议更新至 **50**，联机时客户端和服务端须同时更新至 **2.0.3**。

## English

- Fixes energy hatches retaining the previous tier's capacity and transfer limit when replaced by stick assembly. Fluid hatches now apply their tier's capacity when placed.
- Fixes tier data in hatch recipes. Built-in tiny and small recipes now produce the correct tier. Modpacks can define recipes for higher tiers.
- Upgrade buses show installed module information and dim slots unavailable at the current tier.
- Adds the Modular Disassembler. Craft it from an iron ingot, a stick and a Modularium Ingot in any arrangement. Hold it in your main hand and sneak-right-click a formed, idle controller. The controller, blueprint, shared structure blocks and protected blocks stay in place. Drops go to available wireless ME storage, then your inventory, with any overflow dropped in the world.

Network protocol updated to **50**. Update both the client and server to **2.0.3** for multiplayer.

---

# Modular Machinery: Continuum 2.0.2 — Minecraft 1.20.1

## 中文

- 修复可视化编辑器中输入 E 会关闭界面，以及其他常规界面快捷键抢占编辑器的问题。
- 保留文字编辑、Tab 焦点切换、Enter 按钮操作和 Esc 逐层返回；修改背包按键后同样生效。

## English

- Fixes E closing the visual editor and prevents normal screen shortcuts from taking over the editor.
- Preserves text editing, Tab navigation, Enter button activation and layered Escape behavior, including when the inventory key is rebound.

---

# Modular Machinery: Continuum 2.0.1 — Minecraft 1.20.1

## 中文

- 修复与 AdvancedAE 同时安装时，AE2 合成服务的 Mixin 注入冲突导致的启动崩溃。
- 调整 EC 产物接收前的 CPU 有效性检查，保留跨 ME 网络的产物隔离。

已使用 AdvancedAE **1.3.6** 与 AE2 **15.4.11** 验证服务端启动、EC 合成、网络拆分及区块卸载后的任务恢复。建议客户端与服务端一并更新至 **2.0.1**。

## English

- Fixes a startup crash when installed alongside AdvancedAE, caused by conflicting Mixins in AE2's crafting service.
- Adjusts EC CPU validation before accepting crafting outputs, preserving output isolation between ME networks.

Verified with AdvancedAE **1.3.6** and AE2 **15.4.11**: server startup, EC crafting, network splits and task recovery after chunk unloading. Updating both client and server to **2.0.1** is recommended.

---

# Modular Machinery: Continuum 2.0.0 — Minecraft 1.20.1

## 中文

- 新增 NECO ES 大容量存储系统：S4／S6／S9 主机、矩阵驱动器、物品／流体／气体矩阵及内部 AE 能源单元，包含库存界面、实时占用统计与成型外观。
- 新增 EC 合成 CPU 系统：线程核心、合成内存、并行部件、任务查看与取消，以及断电／卸载后的任务恢复。
- 新增 EF 制造系统：工作核心、样板总线与样板检索、制造队列、并行、冷却、超频和产物回网；监控界面显示累计制造量与近期制造速度。
- 新增 HyperNet 计算中心、数据处理器与消费节点，支持 CPU／GPU／RAM 升级参数、算力配方需求、共享预算、热量、冷却与中心维护。TOP 可查看处理器平均负载。
- 新增研究数据库、研究站及独立 HyperNet 网络终端，支持研究依赖、进度保存、研究门槛、完成通知和带 NBT 的物品图标；创造模式管理员可中键快速研究。
- 可视化编辑器可配置算力、研究、等级标签和说明；等级标签仅供显示，实际能力由参数决定。小窗口下编辑界面整体缩放，相关界面支持平滑字体和资源包替换。
- 木棍自动搭建可从背包内可用的 AE 无线终端网络取料，并合并请求合成缺少的材料；支持等待恢复、超时和潜行右键取消。已提交的 AE 订单继续执行，成品留在 ME 网络。详见[使用说明](docs/WIRELESS_ASSEMBLY_ZH.md)。
- 登录前自动取得服务端独立控制器注册信息，缺少注册项时缓存并提示重启客户端；本地作者 JSON 保留，运行定义以服务端同步为准。新增独立方块仍须在服务端启动时注册，不能运行时增加 Forge 注册项。详见[同步说明](docs/SERVER_DEFINITION_SYNC_ZH.md)。
- 普通 FE 舱室默认传输上限随容量调整为每 tick 10%；已有明确配置的上限保留。

需要 **Java 17／Forge 1.20.1**，推荐 Forge **47.4.23**。网络协议为 **49**，客户端和服务端必须同时更新。GeckoLib **4.8.4** 已内嵌；ME 功能需要另装 AE2 及其前置，气体矩阵另需 Applied Mekanistics。

NECO 系统的机器结构、生存配方、模块物品与数值由整合包作者提供；本次没有内置新星工程的完整玩法配置。循环研究保留占位，新式模块化服务器装配器及历史曲线暂未提供。升级前备份存档和配置，并保留控制器注册文件。

## English

- Adds the NECO ES storage system: S4/S6/S9 hosts, matrix drives, item/fluid/gas matrices and internal AE energy cells, with inventory screens, live usage statistics and formed models.
- Adds the EC crafting CPU system with thread cores, crafting memory, parallel components, task monitoring/cancellation and recovery after power loss or unloading.
- Adds the EF fabrication system with work cores, pattern buses and search, persistent queues, parallel crafting, cooling, overclocking and output delivery to ME storage. The monitor shows total crafts and recent crafting throughput.
- Adds HyperNet centers, data processors and consumer nodes, CPU/GPU/RAM upgrade parameters, recipe computation requirements, shared budgets, heat, cooling and center maintenance. TOP can display average processor load.
- Adds research databases, research stations and a separate HyperNet terminal, with prerequisites, saved progress, recipe research gates, completion notifications and NBT-aware item icons. Creative administrators can middle-click to fast-track research.
- Extends the visual editor with computation, research, tier labels and descriptions. Labels are informational; configured parameters determine capabilities. Editor screens scale to smaller windows, and supported screens use replaceable smooth fonts.
- Stick assembly can draw materials through a carried AE wireless terminal and submit aggregated crafting orders for shortages. Supports waiting/resume, timeouts and sneak-right-click cancellation. Submitted orders continue and leave their output in ME storage.
- Downloads dedicated controller identities before Forge registry synchronization. Missing identities are cached with a client restart prompt; local author files are preserved and runtime definitions remain server-authoritative. New dedicated blocks still require server startup registration.
- Ordinary FE hatches now default to a per-tick transfer limit of 10% of their capacity. Existing explicit limits are preserved.

Requires **Java 17 / Forge 1.20.1**; Forge **47.4.23** is recommended. Network protocol is **49**: update both client and server. GeckoLib **4.8.4** is bundled. ME features require AE2 and its dependencies; gas matrices additionally require Applied Mekanistics.

Modpack authors provide NECO machine structures, survival recipes, module items and balancing. This release does not include Nova Engineering's complete pack configuration. Repeatable research remains a placeholder; the newer modular server assembler and historical charts are not provided. Back up worlds/configuration and retain the controller registry file before upgrading.

---

# Modular Machinery: Continuum 1.4.4 — Minecraft 1.20.1

## 中文

- 机器编辑器的“控制器设置”新增“需要蓝图”开关，可直接保存 `requires-blueprint`，保留结构与其他机器设置。
- 关闭后，通用控制器与通用工厂控制器可自动识别匹配的结构；开启后，自动成型需要对应机器蓝图。未设置此字段时保留默认关闭的行为。
- 保存后重新加载并同步在线客户端的机器与编辑器数据；方块和 NBT 条件仍须满足。
- 修复指令／建造工具导出的空物品列表被要求始终为空的问题：结构 SNBT 列表使用源版的包含匹配，放入物品后不会仅因原先的 `Items: []` 条件失去成型。数字类型、数组和字面字符串仍保留原意。

网络协议更新为 **35**，客户端和服务端需同时更换 JAR。此次不改变启动时的独立控制器注册规则。

## English

- Adds a **Requires Blueprint** toggle under the machine editor's **Controller Settings**, preserving the structure and other machine fields.
- When disabled, generic controllers and factory controllers can automatically recognize matching structures. When enabled, automatic formation requires the matching blueprint. Omitted values remain disabled by default.
- Saved settings reload and sync to connected clients. Block and NBT constraints still apply.
- Fixes exported empty item lists requiring buses to stay empty. Structure SNBT lists now follow the source mod's containment rules, so inserting items does not invalidate an exported `Items: []` condition. Numeric types, arrays and literal strings are preserved.

Network protocol is now **35**; update both client and server. Startup registration of dedicated controllers is unchanged.

---

# Modular Machinery: Continuum 1.4.3 — Minecraft 1.20.1

## 中文

- 修复带 NBT 舱室的机器导出后无法加载、GUI 列表缺失的问题，兼容旧建造工具导出的 SNBT 字符串。
- 修正新导出的 JSON 转义，保留方块实体的原始 NBT，避免错误加入原版结构文件的 `data`／`palette` 字段。
- 原有 JSON 对象形式的结构 NBT 匹配规则保留；SNBT 字符串按具体 NBT 值读取，保留数字类型、数组和字面字符串。

协议更新为 **34**，客户端和服务端都需更换 JAR。旧导出文件可以加载，无需重新创建；作者明确写入的 NBT 条件仍会生效。GeckoLib 4.8.4 继续内嵌。

## English

- Fixes exported machines with hatch block-entity NBT failing to load or appear in the editor; accepts literal SNBT strings from older tool exports.
- Writes correctly escaped JSON and preserves original block-entity NBT without injecting vanilla structure-file `data`/`palette` fields.
- Keeps existing JSON-object NBT patterns; literal SNBT preserves numeric types, arrays and literal strings.

Network protocol is now **34**; update both client and server. Existing exports can load without recreation, while their explicit NBT constraints remain active. GeckoLib 4.8.4 remains bundled.

---

# Modular Machinery: Continuum 1.4.2 — Minecraft 1.20.1

## 中文

- `register` 创建机器后自动加载并向在线客户端同步，无需手动执行 `/reload` 即可打开 `/modularmachinery gui` 编辑新机器。
- 自动加载失败时明确报告错误并保留文件与选区。新增独立控制器仍需两端启动前统一注册文件并重启；本更新不改变 Forge 注册规则。

协议仍为 **33**，配置与存档格式不变，GeckoLib 4.8.4 继续内嵌。

## English

- Machines created with `register` now load immediately and sync to connected clients, making them available in `/modularmachinery gui` without a manual `/reload`.
- Failed automatic loads report an error and preserve the file and selection. New dedicated controller registrations still require matching startup files and a restart on both sides.

Network protocol remains **33**. Configuration and world formats are unchanged; GeckoLib 4.8.4 remains bundled.

---

# Modular Machinery: Continuum 1.4.1 — Minecraft 1.20.1

## 中文

- 修复主手持建造选择工具右键仓室等可交互方块时，先打开 GUI、无法标记选区角点的问题。工具现在优先处理选区操作。
- 保留普通右键第一角、潜行右键第二角、右键控制器导出及潜行右键空气清空选区的操作。
- `register` 新增可选的显示名称参数，支持直接填写中文，例如 `/modularmachinery register alloy_smelter 合金炉`；机器 ID 保持原有规则。

网络协议仍为 **33**，配置与存档格式不变。替换两端的 MMC JAR；GeckoLib 4.8.4 继续内嵌。

## English

- Fixes the construction selection tool opening hatch GUIs instead of selecting corners. Main-hand tool interactions now run before interactive block actions.
- Preserves first/second corner selection, controller export and sneak-right-click-air clearing.
- Adds an optional Unicode display name to `register`, e.g. `/modularmachinery register alloy_smelter 合金炉`, while retaining valid registry IDs.

Network protocol remains **33**. Configuration and world formats are unchanged. Replace the MMC JAR on both sides; GeckoLib 4.8.4 remains bundled.

---

# Modular Machinery: Continuum 1.4.0 — Minecraft 1.20.1

## 中文

- 物品选择器新增背包入口，支持带入数量和可选 NBT，不消耗真实物品。
- 新增环境条件可视化编辑、候选组／催化剂内部 NBT，以及跨机器配方复制和目标引用校验。
- 新增导入模板编辑：选择适配器，配置修改器与追加需求，查看实际导入数量及跳过报告。
- 加强模板保存校验、加载失败撤销和外部修改保护，失败保留草稿并允许重试。
- 补充教程，修正模板界面的说明、内部占位时长显示，以及保存提示覆盖导入按钮的问题。

网络协议升为 **33**，请同时更新客户端和服务器；旧 1.3.0 JAR 使用协议 32。机器、配方与存档格式保持兼容，生成配方仍需通过模板修改。可选联动模组需另装，GeckoLib 继续内嵌。

## English

- Adds an inventory picker with stack counts and optional NBT, without consuming items.
- Adds visual environment conditions, nested candidate/catalyst NBT editing, and cross-machine recipe copying with reference validation.
- Adds adapter-template editing with modifiers, additional requirements, and import reports.
- Protects template files against failed reloads and external changes, retaining drafts for retry.
- Updates guides and fixes misleading template labels, placeholder duration display, and save messages overlapping the import button.

Network protocol is now **33**. Update both client and server; the previous 1.3.0 JAR uses protocol 32. Existing machine, recipe and world formats remain compatible. Generated recipes remain editable through their templates. Optional integrations are separate; GeckoLib remains bundled.

---

# Modular Machinery: Continuum 1.3.0 — Minecraft 1.20.1

## 中文

- 扩展可视化配方编辑：普通 JSON 配方复制，PNC 空气／压力／温度，Botania 魔力、Embers 余烬、Blood Magic LP／意志与自然灵气，以及物品／流体 NBT 条件、实际输出和显示数据。
- 新增环境检测器、Botania 火花魔力仓、PNC 压缩空气仓与升级、八档余烬输入仓、定向 FE 输出端口及六方向束流显示。
- 接入 GTCEu Modern 7.5.3 普通物品／流体／EU 仓室；增加匠魂、Thermal、NuclearCraft Neoteric 和 PNC 的部分静态配方导入。
- 补充控制器／工厂线程脚本接口与 GeckoLib 动画上下文，修复共享部件、结构重检、配方排序、性能与投影相关问题。
- 普通 FE 仓传输限制默认自动取容量的 10%；已有配置需将 `[energyhatch.limit]` 对应档位设为 0 才启用自动值。

客户端和服务器一起更新并分发相同机器配置，更新前备份存档与配置。Java 17，推荐 Forge 47.4.23；仅内嵌 GeckoLib 4.8.4，其他联动及前置另装。新机器注册身份需要重启，保留 `controller-registry.json`。环境条件的 GUI、导入模板 GUI、跨机器复制、GTCEu 激光／特殊 ME／摆放代理尚未提供；源质、GP、Impetus 继续占位。

## English

- Expands the recipe editor with JSON recipe duplication, pneumatic and magic resource requirements, and separate item/fluid NBT matching, output and display data.
- Adds environment detectors, Botania spark mana hatches, pneumatic hatches and upgrades, tiered Ember input hatches, and directional FE output ports with beams.
- Integrates regular GTCEu Modern 7.5.3 item/fluid/EU hatches and selected static recipe imports from Tinkers' Construct, Thermal, NuclearCraft Neoteric and PneumaticCraft.
- Extends controller/factory scripting and GeckoLib animation context, with fixes for shared components, structure checks, recipe ordering, performance and projections.
- FE hatch transfer limits can now follow 10% of capacity. Set the existing `[energyhatch.limit]` entries to 0 to enable automatic limits.

Update both sides, distribute matching machine definitions, and back up worlds and configs first. Requires Java 17; Forge 47.4.23 is recommended. GeckoLib 4.8.4 is bundled; optional integrations and their prerequisites are separate. Preserve `controller-registry.json`. Some advanced integrations and editor panels remain unavailable; see the included guides for scope.

---

# Modular Machinery: Continuum 1.2.1 — Minecraft 1.20.1

修复 JEI 配方页过高：参考源版恢复左输入、处理中箭头、右输出的紧凑布局；材料过多可分别滚动，详细说明可悬停查看。
修复独立控制器按 U 缺少结构预览入口、工厂控制器拦截投影仪／构建工具右键，以及世界投影丢失相机旋转导致错位的问题。

Fixed oversized JEI recipe pages with a compact input → process → output layout, independent scrolling for large ingredient lists, and full details on hover.
Fixed missing structure previews when pressing U on dedicated controllers, factory controllers intercepting projector/construction-tool interactions, and misplaced world projections caused by an incorrect camera transform.

替换旧版 MMC，客户端和服务端一起更新。网络协议仍为 32，配置和存档格式不变；GeckoLib 继续内嵌，可选联动模组仍需另装。
Replace the previous MMC JAR on both client and server. Network protocol remains 32; configuration and world formats are unchanged.

---

# Modular Machinery: Continuum 1.2.0 — Minecraft 1.20.1

## 通量网络联动 / Flux Networks integration

- 新增 Flux Networks 7.2.1.15 原生能量接口，能源输入／输出仓与 FE 共用库存。
- 支持通量节点供能和输出仓向通量插头主动输送；保留仓室方向、容量、传输限制及标准 FE 回退。
- 长整型请求安全限制到现有 FE 容量范围，负数请求不执行传输；接口在方块卸载／移除时失效。
- 新增默认开启的 general.enable-fluxnetworks-integration 开关，仅控制专用联动，不禁止标准 FE 连接。
- Flux Networks 是可选外部依赖，需要另装；发行包仍只内嵌 GeckoLib 4.8.4。
- 不包含新星工程的梦之能量端口或超大整数储能系统。

Adds optional native Flux Networks 7.2.1.15 support for energy hatches. Flux Points supply input hatches, and output hatches feed adjacent Flux Plugs. Both native Flux and Forge Energy share the existing buffer and respect hatch direction, capacity and transfer limits. Long requests are bounded safely; no additional storage is introduced. Flux Networks must be installed separately. Standard FE remains available when the dedicated integration is disabled.

安装 modularmachinerycontinuum-1.2.0-1.20.1-all.jar，替换旧版 MMC，客户端和服务端一起更新。网络协议仍为 32；现有控制器与能源仓存档格式不变。连接及配置见[通量网络教程](docs/FLUX_NETWORKS_ZH.md)。

---

# Modular Machinery: Continuum 1.1.0 — Minecraft 1.20.1

本次大更新为机器增加独立控制器身份，并在已有可视化编辑器中加入控制器外观设置。

## 新增

- 启动时为每种 JSON 机器注册 `<机器名>_controller` 普通控制器。
- 按源版 `has-factory` 或全局默认开关注册 `<机器名>_factory_controller` 工厂控制器。
- 专属控制器放置后固定绑定对应机器；蓝图限制、普通加工、工厂线程、批量与升级沿用已有逻辑。
- `/modularmachinery gui` → 选中机器 → “控制器外观”：RGB 输入、调色板、普通与工厂实时物品预览、独立工厂开关。
- 名称与颜色重载后同步；世界投影、蓝图模型、材料排序与 JEI 接入独立控制器身份。
- 自动生成可被作者资源包／数据包覆盖的模型、掉落表和工具标签。
- 独立控制器与升级教程，涵盖联机配置、注册历史、资源包、数据包和生存配方。

## 修复

修复组件挖掘回调漏调原版掉落方法的问题，并按源版 `pickaxe` 等级 1 补全组件工具标签（石镐及以上）。新增控制器、旧通用控制器和机械外壳均验证实际生存挖掘；控制器内蓝图与控制器物品各掉落一次。

## 升级说明

安装 `modularmachinerycontinuum-1.1.0-1.20.1-all.jar`，替换旧 MMC JAR，避免重复安装。适用 Forge 1.20.1、Java 17，内嵌 GeckoLib Forge 4.8.4；外部可选模组及其必需前置仍需另装。

旧通用控制器、方块实体 ID、蓝图和配置路径保留，已有机器不必拆除重建。新独立控制器不会自动获得整合包的生存合成配方，需要作者添加。

客户端与服务端必须一起更新，本版网络协议为 **32**。颜色、名称与配方可重载；新增机器身份或首次开启独立工厂控制器后，需要两端分发相同配置并重启。

请备份并分发 `config/modularmachinery/controller-registry.json`。该文件保存已注册的历史身份，删除机器定义或关闭工厂开关不会移除旧方块 ID。不要清空已有世界的注册记录。生成的模型／数据缓存不是作者配置入口，应使用自己的资源包与数据包覆盖。

完整说明见 [独立控制器教程](docs/DEDICATED_CONTROLLERS_ZH.md)。旧版教程中的“修改 JSON 后重载”不适用于新增方块注册身份。

源质、GP、Impetus 六个接口仍为占位；本次未扩展这些资源，也不提供 1.12.2 世界直接转换。GeckoLib 动画继续由整合包作者配置，不附送梦之能量核心示例资源。

## 验证与许可

发布前运行独立环境、全联动环境及首次启动回归，覆盖固定绑定、蓝图、真实加工、掉落与所有者、投影、资源包加载、颜色编辑协议及权限保护。功能与测试范围见 [TESTING.md](TESTING.md)。服务端回归不能代替客户端新界面的实际画面验证。

源码采用 GPL-3.0-only，发布 JAR 时同时提供对应源码与许可证。内嵌 GeckoLib 的 MIT 许可与第三方声明保留。
