# Modular Machinery: Continuum 使用教程（Forge 1.20.1）

1.1.0 新增：请先阅读[独立控制器、颜色编辑与升级说明](DEDICATED_CONTROLLERS_ZH.md)。新机器身份与首次开启独立工厂控制器需要重启两端。

核对日期：**2026-10-20**。全部当前教程见[文档目录](README.md)。

这是一份面向玩家和整合包作者的快速教程，内容按当前 `Modular Machinery: Continuum` 1.20.1 移植版的实际行为编写。

当前发布文件名：

```text
modularmachinerycontinuum-1.2.0-1.20.1-all.jar
```

主模组可以单独运行。JEI、AE2、Mekanism、The One Probe（TOP）和 CraftTweaker 都是可选集成，安装后才会启用相应功能。GeckoLib 已随本体打包，不需要再放一个 GeckoLib jar。

## 1. 安装

1. 使用 Minecraft 1.20.1 与 Forge 47.x。基础版已在 47.3.0 验证；安装下述社区星辉／虹彩联动建议使用已验证的 **47.4.23**，Extra Utilities Rebirth 1.7.3 不能在 47.3.0 正常加载。
2. 将发布 JAR 放入实例的 `mods` 文件夹。
3. 启动一次游戏。首次启动会创建：

   ```text
   config/modularmachinery/machinery/
   config/modularmachinery/recipes/
   ```

4. 自定义机器放入 `config/modularmachinery/machinery/`，自定义配方放入 `config/modularmachinery/recipes/`。

两个目录均递归扫描子目录，机器名和配方名仍需保持唯一。jar 内的 `default_machinery` 和 `default_recipes` 只在 `config/modularmachinery/` 首次创建时复制为模板，不覆盖已有文件；之后删除某个文件也不会自动补回。修改配置目录中的 JSON 后，在世界内执行 `/reload`；如果仍未更新，先查看加载日志中的报错。

Botania、Blood Magic、Nature’s Aura、社区 Astral Sorcery 和 Extra Utilities Rebirth 也是可选依赖，不随本体打包；ME 气体需要 AE2、Mekanism 和 Applied Mekanistics。固定版本、前置和用法见[作者教程](PACK_AUTHOR_GUIDE_ZH.md)及[社区依赖说明](../libs/optional/README.md)。源质、GP、Impetus 六个接口仍是占位，不提供对应资源功能。客户端与服务器应使用相同版本的模组；当前网络协议为 **32**。

## 2. 生存模式快速开始

可以通过 JEI 或创造模式取得以下物品：

```text
modularmachinery:blockcontroller          普通控制器
modularmachinery:blockfactorycontroller   工厂控制器
modularmachinery:blockcasing              机械外壳
modularmachinery:blockinputbus            物品输入总线
modularmachinery:blockoutputbus           物品输出总线
modularmachinery:blockfluidinputhatch     流体输入仓
modularmachinery:blockfluidoutputhatch    流体输出仓
modularmachinery:blockenergyinputhatch    能量输入仓
modularmachinery:blockenergyoutputhatch   能量输出仓
modularmachinery:blocksmartinterface      智能接口
modularmachinery:blockparallelcontroller  并行控制器
modularmachinery:blockupgradebus          升级总线
modularmachinery:itemconstructtool        建造选择工具
modularmachinery:itemblueprint            蓝图
modularmachinery:machine_projector        机器投影器
```

`itemconstructtool` 用来选择两个角点并生成机器定义；它和木棍不是同一件物品。木棍是生存模式自动装配工具，默认配置键为 `auto-assembly-item`，可以在配置中改成其他物品。

最简单的流程是：

1. 放置控制器和结构方块。
2. 先让控制器绑定机器：看着控制器执行 `/modularmachinery bind <机器名>`，或者把对应蓝图右键放入控制器。
3. 手持木棍右键控制器，开始自动补齐缺失方块并尝试成型；结构错误时聊天栏会列出世界内坐标。也可以执行 `/modularmachinery form` 手动检查和成型。
4. 输入物品、流体和能量，等待配方完成。

控制器的朝向会影响结构坐标。结构 JSON 中的坐标都是相对于控制器的坐标，控制器本身位于 `(0, 0, 0)`，不需要在 `parts` 中再次声明。

## 3. 可视化编辑器、蓝图和投影

### 3.1 管理员可视化编辑器

整合包作者可以使用 `/modularmachinery gui` 打开图形化编辑器。单击配方选中查看需求，双击配方修改显示名称和运行时间；输入、输出栏的 `+` 先选择物品、流体或能量。物品和流体搜索支持名称或 ID、`@模组ID` 和 `#标签`，确认数量与概率后点击“保存”添加，取消不写入。物品数量为 1～64，流体单位为 mB，概率为 0～1；能量直接填写 FE/t。点击现有需求修改数值，红色 `×` 删除需求；最后一个需求需要先添加替代项，或直接删除整个配方。配方栏 `+` 创建草稿，保存首个需求时才写入文件，关闭前未保存的草稿会丢失。机器与配方名称支持 `&a`、`&l` 等颜色和格式代码，可先预览再保存。配方显示名称留空时使用原 ID，修改显示名称不会改变配方 ID。脚本和适配器生成的配方显示“只读”，悬停可查看原因，需要修改源脚本、适配器模板或源配方。该功能需要权限等级 2。

输入栏的 `+` 还提供“物品标签（输入）”：选择例如 `#minecraft:planks` 后可接受标签内任意物品，图标仅为示例。支持按标签 ID、成员名称、`#标签` 或 `@标签命名空间` 搜索，悬停可查看成员示例。数量为 1～64，也可设置概率；空标签和标签输出不能添加。普通物品列表中的 `#标签` 搜索仍是筛选具体物品。

普通物品、物品标签输入和流体的需求窗口还提供“重新选择”，可以通过搜索更换对象，保留数量、概率和输入输出方向。标签输入只能重新选择标签。搜索界面“返回”会保留当前编辑内容；需求窗口“保存”才写入文件，“取消”放弃整次修改。带 NBT、旧版元数据或其他高级选择器的需求请回到源文件修改。

编辑器的保存与删除操作会显示服务器确认状态，等待期间暂时禁用编辑。成功后刷新服务器实际加载的内容；失败则保留输入并显示原因。如果提示内容已经发生变化，请重新打开编辑器后再修改。等待保存时按 Esc 退出界面不会撤销已经提交的操作。

输入栏 `+` 的“候选物品组（输入）”可配置多个替代选项，例如 2 个铁锭或 3 个金锭。使用 `+ 物品`／`+ 标签` 搜索添加，单击候选项后修改数量，用上下按钮调整优先顺序，用红色 `×` 删除；超过四项可滚轮翻页。每组 1～64 项，每项数量 1～64，可设置整组消耗概率。每份加工必须付清某一完整选项，批量的不同份数可使用不同选项。保存提交整组，取消放弃整组修改；带 NBT、随机数量或特殊权重的高级输入候选组仍请修改源文件。更新时客户端与服务器均应使用同一版 JAR。

输出栏 `+` 的“随机物品组（输出）”可以添加多种具体产物，逐项设置最少／最多数量（1～64，包含两端）和非负权重。权重 0 的项不参与抽取，权重 2 与 1 的抽中占比约为 66.67% 与 33.33%。整组“产出概率”另设为 0～1，决定这次是否产出该组；批量运行沿用现有引擎抽取一种产物及数量后乘倍率的规则。界面显示按配置权重计算的占比，实际抽取仍受输出空间影响。列表每页三项，可滚轮翻页，支持重新选择、删除和调整显示顺序。产出概率大于 0 时至少一项权重大于 0。概率与权重支持 `1e-8` 等写法；已有 NBT 或标签输出请修改源文件。保存提交整组，取消放弃修改。


已保存的一次性输入需求还可以 **Ctrl+右键**，或选中后点击底部“触发设置”。触发 tick 留空恢复默认，0 为开工扣料，正整数为运行到该进度再扣料，且必须小于配方时长；时长修改器也会影响触发点。支持物品／标签、耐久、燃料、候选输入、普通流体和可选 Mekanism 普通气体，持续需求、催化剂、智能条件与输出不开放此编辑。

重复触发可切换默认、关闭、开启。它只控制回退后再次经过同一 tick、或在同一 tick 重试时是否再次扣料，**不是每隔 N tick 执行**。关闭时已支付的需求不会重复扣料；缺料沿用机器的等待／回退／重置和配方取消策略。保存立即重载，只修改触发设置，保留数量、NBT、概率、分组和批量策略；取消或 Esc 不写文件，调整窗口大小保留草稿。新建需求先保存；脚本及适配器来源只读。两端需同时更新到协议 32。


选中已有需求后使用底部“批量设置”，或 **Shift+右键需求行**，可选择“恢复默认”“随批量放大”“整批只算一份”。例如需求 2 个、批量 4，正常处理 8 个，选整批一份后处理 2 个；流体、FE/t、mB/t、工具使用次数和燃料预算同理。它只改当前需求，不改概率、时长、机器倍率或线程数。支持高级 NBT 需求；催化剂仍保留默认规则，智能接口条件不提供此设置。

取消或 Esc 不保存，调整窗口大小保留选择；保存立即重载并同步，保留其他需求字段。新建需求先保存，脚本／适配器生成配方只读。机器共用同一配方文件时会一起变化。旧 JSON 中的 `parallelizeUnaffected` 现在实际生效：`true` 为不放大，`false` 为随批量放大。能量输出 FE/t 也已接入实际发电路径，满仓等待时不会继续扣除该 tick 的能量／流体输入。客户端和服务端需同时更新到协议 32。

输入栏 `+` 的“催化剂（可选输入）”用于配置可选材料及其效果。通过物品／标签搜索添加候选项，每组最多 64 项、每项数量 1～64；满足其中一项即可。缺少催化剂仍可加工，找到后才应用修改器。消耗概率 `0` 表示不扣除材料且效果仍生效，`1` 表示扣除；批量加工默认只扣一份候选项数量。候选列表每页三项，可滚轮翻页，支持重新选择、删除和调整顺序。

点击“修改器”后，用 `+ 修改器` 添加效果；点击按钮切换时间／能量／物品／流体、输入／输出、加法／乘法。物品和流体可切换修改数量或概率；时间和能量分别使用 tick、FE/t。乘法 `0.5` 表示减半，概率加法 `0.1` 表示增加 10 个百分点。数值支持有限负数和科学计数法，最多 32 项；上下按钮重排，红色 `×` 删除。时间先加后乘，其他目标按列表顺序应用。“应用”只更新本地催化剂草稿，回到上一级点击“保存”才写入；子界面“取消”或 Esc 放弃子界面本次修改。附加字段保留；NBT、高级候选条件或界面未支持的修改器请修改源文件。

催化剂窗口新增“提示文字（N）”：逐行添加／编辑／排序／删除，支持空行、重复行和颜色代码，以及可滚动的换行预览。最多 64 行、每行 512 字、合计 8192 字，不接受行内控制字符。文字显示于 JEI 催化剂物品的悬停提示，忠实于源版按原文显示，语言键也按原文显示；与配方高级设置中的“附加说明”独立。新编辑的 `&6文字&r` 转换为颜色格式，未修改的旧字符串保持原样。

“应用”只更新催化剂本地草稿，回到父窗口点击“保存”才生效；取消子窗口放弃本次提示更改，取消父窗口放弃整次编辑。预览中首次 Esc 返回编辑，再次 Esc 取消子窗口。列表和预览可独立滚动，调整窗口大小保留输入。超限或异常的旧提示请先修改源文件。客户端与服务端须同时更新到协议 32。

输入栏 `+` 现在还提供“工具耐久（输入）”和“耐久标签（输入）”，类型选择按两列排列。搜索工具或标签后，填写使用次数（1～64）、每次扣耐久（1～2000000000）及扣耐久概率（0～1）。这里的次数不等于工具件数，同一把工具可用多次；批量倍率通常放大次数。例如使用 2 次、每次扣 10、倍率 3，耐久充足时扣 60。工具耗尽损坏，最后不足一次的剩余耐久也算一次；多件匹配工具可以分担。次数不足时不进行部分扣除。概率 0 保留工具，但开工仍需足够的可用次数。显式扣耐久不受耐久附魔的随机免伤影响，不可损坏物品不能供应耐久。

耐久标签仅接受其中可扣耐久的成员，图标仅为示例。重新搜索保留次数、损伤和概率；取消不保存，调整窗口大小保留未完成输入。已有 NBT 或旧表达式等高级耐久需求请修改源文件；普通源字段及兼容别名保留。

输入栏 `+` 的“熔炉燃料（输入）”可直接设置所需燃烧时间（1～2000000000 tick）和消耗概率（0～1）。这里不是配方运行时间或物品件数：煤炭和木炭通常各提供 1600 tick，实际遵循模组燃料规则；煤炭图标仅作示例，可混用不同燃料。批量倍率放大所需时间，按整件燃料扣除，多出的时间丢弃，容器燃料使用后留下容器。燃料不足时不部分扣除；概率 0 不扣燃料，但开工仍需足够燃烧时间。固定煤炭数量请使用普通物品输入。

已有燃料需求可修改时间和概率，取消不写文件，调整窗口大小保留输入；已有附加字段和触发设置保留。NBT 等高级燃料条件请修改源文件。

右键已保存的需求行，或点击底部“接口分组”，可搜索、选择结构中声明的 `selector-tag`；点击“不限接口”解除限制，然后保存。标签精确匹配且区分大小写，没有对应接口时不会使用其他分组；不限接口包含已标记和未标记接口。绑定后的需求行显示蓝色“组”，悬停查看完整标签。输出也可选择分组，只向对应输出口送出产物。

这里的接口分组与智能接口的数值类型不同。候选标签来自机器静态结构和动态模板，实际接口取决于本次成型；新分组需先在结构文件声明。共享同一配方文件的多台机器只显示共有分组，保存会同时影响该文件产生的配方。现有未声明标签可保持或解除。单独修改分组仅更新 `selector-tag`，保留 NBT、数量、概率及其他源字段；新建需求需先保存。脚本和适配器生成配方仍通过源文件维护。取消或 Esc 不保存，调整窗口大小保留当前选择和搜索。客户端和服务器需同时更新，当前协议为 32。

输入、输出栏的 `+` 还可选择“流体/刻”，搜索具体流体并填写持续速率（1～2000000000 mB/t 的整数），不设置概率。单击已有需求可修改速率或重新选择流体，需求行显示流体纹理和 `mB/t`。每个配方进度 tick 处理一次，批量倍率通常放大速率；缺料或满仓时暂停，恢复后继续，完成后等待出料时不再处理。普通“流体”仍使用一次性 mB 数量，二者需分别添加。取消不保存，调整窗口大小或从流体搜索返回时保留输入；保存保留接口分组、触发设置及其他源字段。NBT 等高级条件需修改源文件，脚本和适配器生成配方仍只读。

输入栏 `+` 的“智能接口数值（输入）”可搜索机器声明的数值类型，并设置最小值和最大值；单击已有条件可修改。上下限都包含，相同值表示精确匹配；支持负数、小数和科学计数法，精度与源版 float 一致。需求行用比较器图标显示此条件，悬停查看完整类型与范围。它只检查智能接口发布的数字，不消耗物品、不设置概率，也不受批量倍率放大。机器需先在 `smart-interface-types` 声明类型，并提供对应智能接口；共享文件只显示所有对应机器的共同类型，没有共同类型时无法保存。取消或 Esc 放弃修改，调整窗口大小保留未完成输入。服务器会保留接口分组、触发设置和其他源字段，并拒绝覆盖未经重载的外部源需求修改。

编辑已保存配方时，双击配方并点击“高级设置”，可修改优先级和每 tick 处理失败时是否取消本次加工。优先级默认 0，数值越小越先尝试，允许负数；同优先级按引擎原有规则排序。失败取消默认关闭，关闭时暂停等待供应或空间恢复，开启后取消本次加工，已消耗的输入不返还；机器失败策略与事件脚本仍可影响行为。

高级设置的“加工与显示”页还有两个开关，默认均开启。“允许批量加工”关闭后，此配方每次只加工一份，普通控制器和工厂均适用；其他配方的批量设置不受影响，已开始的加工保留已消耗材料对应的原批量。“在 JEI 中显示配方”关闭后，只隐藏 JEI 配方页面，机器仍可加工，编辑器仍能找到该配方；保存后自动同步刷新 JEI，重新开启可恢复显示，机器蓝图和结构预览不隐藏。

切换到“工厂线程”页可设置工厂线程名称和配方线程上限。名称留空表示不限；填写后必须与机器脚本中已声明的核心线程名称完全一致，区分大小写和空格，核心线程配方列表限制仍然有效。线程上限 `-1` 表示默认，`0` 禁止本配方在工厂开工（包括核心线程），正整数为源版线程上限。当前同配方仍最多一个线程，填大于 1 不会增加重复加工；机器和并行控制器的批量倍率保持不变。这些线程选项只限制工厂的新开工，不影响普通控制器，也不取消已开始的加工。悬停输入框可查看详细说明。“应用”只更新草稿，返回配方窗口“保存”才写入文件。

“附加说明”页可编辑 JEI 配方详情中的文字。点击 `+ 行` 添加，选中一行后在下方输入，`↑`／`↓` 排序，红色 `×` 删除；列表区可滚动。支持空行、重复行、普通文字、语言翻译键和新输入的 `&6颜色&r` 等格式代码，未修改的旧文字原样保留。最多 64 行、每行 512 字、总计 8192 字；使用多行条目表达换行，不接受行内控制字符。本页修改的是配方说明，催化剂自身的提示不变。

点击“预览说明”可按当前客户端语言预览颜色和文字，长行自动换行，预览区可滚动；“返回编辑”或首次 Esc 返回编辑。预览不会保存。JEI 页放不下时显示“更多说明（悬停查看）”，悬停下方文字区域可查看说明，特别长的内容可在编辑器滚动预览中查看。

高级窗口点击“应用”只更新三个页签的本地草稿，返回配方窗口点击“保存”才写入；取消或 Esc 可以放弃修改（说明预览中首次 Esc 返回编辑）。切换页签、切换窗口和调整窗口大小保留输入，新配方需先保存首个需求。共享配方文件会同时影响所有机器变体；保存保留需求、NBT、接口分组及其他未编辑配置，并拒绝覆盖未经重载的外部文件修改。

### 3.2 蓝图、投影和 JEI 结构预览

管理员可以执行：

```text
/mm-get_blueprint <机器名>
```

例如：

```text
/mm-get_blueprint test_multiblock
```

拿到蓝图后右键即可打开该机器的结构预览。蓝图会保存机器名；机器 JSON 修改后，蓝图会按重新加载后的定义解析。

机器已经绑定到控制器时，用 `machine_projector` 右键控制器可以在世界中显示结构投影。投影和 JEI 结构预览支持：

- 鼠标射线选中结构方块；
- 显示缺失或错误方块；
- 切换全部结构和单层结构；
- 查看机器信息、升级信息和成型状态；
- 在 JEI 中从机器配方进入结构预览。

## 4. 创建一台最小自定义机器

把下面内容保存为：

```text
config/modularmachinery/machinery/my_machine.json
```

```json
{
  "registryname": "my_machine",
  "localizedname": "钻石机",
  "parallelism": 2,
  "parts": [
    { "x": -1, "y": -1, "z": 0, "elements": "modularmachinery:blockcasing@4" },
    { "x":  0, "y": -1, "z": 0, "elements": "modularmachinery:blockcasing@2" },
    { "x":  1, "y": -1, "z": 0, "elements": "modularmachinery:blockcasing@4" },
    { "x":  1, "y":  0, "z": 1, "elements": "modularmachinery:blockinputbus" },
    { "x": -1, "y":  0, "z": 1, "elements": "modularmachinery:blockoutputbus" },
    { "x":  0, "y":  0, "z": 2, "elements": "modularmachinery:blockenergyinputhatch" }
  ]
}
```

说明：

- `registryname` 是机器名，配方的 `machine` 必须完全相同。
- `localizedname` 是预览和 JEI 中使用的显示名。
- `parts` 中每一项是相对控制器的坐标。
- `elements` 可以是一个字符串，也可以是数组；数组表示该位置可以放其中任意一种方块。
- `@4` 是机械外壳的方块变体。使用 JEI 查看可用的外壳、总线和仓的变体。
- `parallelism: 2` 让这台机器最多同时处理两批配方，实际数量还会受输入、输出、能量和 `max-parallelism` 限制。

这份结构需要控制器、外壳、一个输入总线、一个输出总线和一个能量输入仓。控制器原点不计入 `parts`。

## 5. 给机器添加配方

把下面内容保存为：

```text
config/modularmachinery/recipes/my_machine_diamond.json
```

```json
{
  "machine": "my_machine",
  "registryName": "my_machine_diamond",
  "recipeTime": 200,
  "requirements": [
    {
      "type": "modularmachinery:energy",
      "io-type": "input",
      "energyPerTick": 100
    },
    {
      "type": "modularmachinery:item",
      "io-type": "input",
      "item": "minecraft:coal",
      "amount": 32
    },
    {
      "type": "modularmachinery:item",
      "io-type": "input",
      "item": "minecraft:tnt",
      "amount": 8
    },
    {
      "type": "modularmachinery:item",
      "io-type": "output",
      "item": "minecraft:diamond",
      "amount": 1
    }
  ]
}
```

这条配方持续 `200` tick，也就是 10 秒。机器的基础并行度是 2，因此在资源充足时每次完成会输出 2 颗钻石，并消耗 64 个煤炭、16 个 TNT。`energyPerTick` 是每 tick 的能量消耗，不是整条配方的总能量。

配方字段的大小写必须保留：机器使用 `registryname`，配方使用 `registryName`。文件名可以自定义，但配方中的 `registryName` 必须唯一。

## 6. 常用需求类型

### 物品

```json
{ "type": "modularmachinery:item", "io-type": "input",  "item": "minecraft:iron_ingot", "amount": 4 }
{ "type": "modularmachinery:item", "io-type": "output", "item": "minecraft:iron_block", "amount": 1 }
```

输出可以增加 `chance`，例如 `"chance": 0.5` 表示 50% 概率输出。必定输出的物品会在完成前统一检查输出空间，输出总线空间不足时配方会等待。

### 流体

```json
{ "type": "modularmachinery:fluid", "io-type": "input",  "fluid": "minecraft:water", "amount": 1000 }
{ "type": "modularmachinery:fluid", "io-type": "output", "fluid": "minecraft:lava",  "amount": 250 }
```

`amount` 的单位是 mB。连续消耗流体可以使用 `modularmachinery:fluid_pertick`，它按每 tick 的速率检查和消耗，适合持续供液的配方。

### 能量

```json
{ "type": "modularmachinery:energy", "io-type": "input", "energyPerTick": 100 }
```

能量输入仓使用 Forge 能量能力。并行度为 `N` 时，每 tick 的能量需求也会按 `N` 倍计算。

### 智能接口数值

机器先声明接口类型，并在结构中放置智能接口：

```json
{
  "registryname": "mode_machine",
  "localizedname": "模式机器",
  "smart-interface-types": [{ "type": "mode", "default-value": 0 }],
  "parts": [
    { "x": 0, "y": 1, "z": 0, "elements": "modularmachinery:blocksmartinterface" }
  ]
}
```

配方可以按接口数值筛选：

```json
{
  "type": "modularmachinery:interface_number_input",
  "io-type": "input",
  "interface": "mode",
  "min": 1,
  "max": 1
}
```

在智能接口 GUI 中输入数值并按回车提交后，只有数值落在 `min` 到 `max`（含边界）的配方会成立。接口类型名必须先在机器的 `smart-interface-types` 中以对象形式声明。

更多需求和高级字段（NBT、耐久、燃料、催化剂、输入候选数组、Mekanism 气体）见 [JSON 补充参考](ADDING_MACHINES.md)。魔力、LP、意志、灵气、星能、星座和虹彩的独立规则见[作者教程](PACK_AUTHOR_GUIDE_ZH.md)，这些资源并不统一按 FE 的消耗或批量规则处理。

### 普通仓容量

普通 FE 输入／输出仓容量现从 tiny 的 1000 FE 逐档增长，ultimate 为 2147483647 FE；自定义各档容量及旧配置更新方法见[作者教程](PACK_AUTHOR_GUIDE_ZH.md)。已有配置不会被新 JAR 自动覆盖。

## 7. 并行控制器和批量倍率

在机器的 `parts` 中声明一个并行控制器即可让玩家在结构中放置它：

```json
{ "x": 0, "y": 1, "z": 1, "elements": "modularmachinery:blockparallelcontroller" }
```

并行控制器右键打开 GUI，可以使用 `-100`、`-10`、`-1`、`+1`、`+10`、`+100` 或直接输入数字调整倍率。有效并行度为：

```text
机器的 parallelism + 结构中所有并行控制器的倍率
```

最终值会被机器的 `max-parallelism` 限制，并且会根据实际输入数量、能量、流体和输出空间进一步降低。不能提供完整批量资源时，机器会自动使用能运行的较小批量。

如果机器不允许并行，可以在机器 JSON 中写：

```json
{
  "registryname": "my_machine",
  "parallelizable": false
}
```

## 8. 工厂控制器和线程

将普通控制器替换为 `blockfactorycontroller` 可以使用工厂模式。工厂控制器会为同一套结构管理多个配方线程；GUI 会显示线程编号、运行进度和阻塞原因。

工厂模式的规则如下：

- 每个线程同一时间只运行一个配方。
- 同一台工厂中，同一种配方最多占用一个线程；其他线程可以运行不同配方。
- 机器的并行倍率和并行控制器仍然有效；线程数量与批量数量是两套独立限制。
- 输出空间不足或输入、能量不足时，线程会等待，不会直接丢弃已预留的必定输出。

配方可以用 `maxThreads` 设置该配方的工厂线程预算；写成 `0` 会禁止它在工厂模式运行，省略时使用默认值。同一台工厂中，同一种配方仍最多占用一个线程。

## 9. 常用命令

以下命令需要权限等级 2：

```text
/modularmachinery list
/modularmachinery recipes
/modularmachinery recipes <machine>
/modularmachinery bind <machine>
/modularmachinery register <machine>
/modularmachinery form
/modularmachinery check
/modularmachinery status
/modularmachinery gui
/mm-get_blueprint <machine>
```

用途：

- `list`：列出已加载的机器定义。
- `recipes`：列出已加载配方，可按机器名筛选。
- `bind`：将准星指向的控制器绑定到机器定义。
- `register`：使用玩家当前建造工具的两个角选区，将准星指向控制器并保存为指定名称的机器 JSON。
- `form`：尝试让准星指向的控制器成型，并输出缺失方块的世界坐标。
- `check`：检查当前结构和仓内资源能否运行各条配方。
- `status`：查看控制器绑定的机器和成型状态。
- `gui`：打开可视化编辑器；机器结构仍通过建造工具、命令或 JSON 创建。
- `mm-get_blueprint`：获得指定机器的蓝图。

## 10. 常见问题

**修改 jar 内的 JSON 没有生效。**

运行时读取的是 `config/modularmachinery/machinery` 和 `config/modularmachinery/recipes`。jar 内文件是模板，不是运行时配置入口。

**控制器提示没有绑定机器。**

看着控制器执行 `/modularmachinery bind <机器名>`，或用木棍右键控制器尝试自动绑定。机器名必须与 JSON 的 `registryname` 完全一致。

**结构明明对了却无法成型。**

检查控制器朝向、相对坐标、方块变体后缀（例如 `@4`）以及总线、仓的位置。执行 `/modularmachinery form`，错误会使用世界内坐标报告。

**配方不显示或永远不能运行。**

检查配方的 `machine` 是否精确等于机器的 `registryname`，以及 `registryName` 是否重复。然后执行 `/reload`，再用 `/modularmachinery recipes <machine>` 查看加载结果。

**并行倍率没有达到预期。**

并行度会受到输入数量、能量、流体、输出空间、机器 `max-parallelism` 和 `parallelizable` 设置共同限制。资源不足时自动降到可运行的批量。

**JEI、AE2、Mekanism 或 TOP 功能没有出现。**

这些是可选集成。确认对应的 Forge 1.20.1 模组已安装，并检查客户端和服务器两端的版本。

## 11. 进阶资料

- [PACK_AUTHOR_GUIDE_ZH.md](PACK_AUTHOR_GUIDE_ZH.md)：整合包作者的机器、配方、线程、智能接口和 GeckoLib 教程。
- [ADDING_MACHINES.md](ADDING_MACHINES.md)：JSON 结构、智能接口和高级需求补充。
- [examples/recipe_builder.zs](examples/recipe_builder.zs)：CraftTweaker 14 配方示例；其他脚本入口见作者教程。

最近服务端回归与构建证据见 [VERIFY](../VERIFY.md)。模板 JSON 可作为配置起点，示例中的机器名、接口和资源文件仍需与整合包实际配置相配；新联动的客户端效果尚需实测。
