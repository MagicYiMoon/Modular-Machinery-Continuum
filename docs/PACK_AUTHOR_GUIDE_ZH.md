# Modular Machinery: Continuum：整合包作者基础与 GeckoLib 进阶教程

1.1.0 新增：请先阅读[独立控制器、颜色编辑与升级说明](DEDICATED_CONTROLLERS_ZH.md)。新机器身份与首次开启独立工厂控制器需要重启两端。

核对日期：**2026-10-20**。教程入口与历史记录的区别见[文档目录](README.md)。当前网络协议为 **32**，客户端与服务器应使用同一版 JAR。

本文面向整合包作者，示例全部针对 Forge 1.20.1 移植版。基础机器和配方使用 JSON；需要动态模型、动画或工厂核心线程时，再使用可选的 CraftTweaker 14。

相关文件：

```text
config/modularmachinery/machinery/    机器定义
config/modularmachinery/recipes/      配方定义
scripts/                               CraftTweaker 脚本
resourcepacks/<资源包>/assets/         GeckoLib 模型、动画和纹理
```

主模组发布文件名为 `modularmachinerycontinuum-1.2.0-1.20.1-all.jar`，GeckoLib Forge 4.8.4 已内嵌，不需要另装。CraftTweaker、JEI、AE2、Mekanism、TOP、Applied Mekanistics、Botania、Blood Magic、Nature’s Aura、社区 Astral Sorcery 和 Extra Utilities Rebirth 均为可选联动，不会内嵌到发布包；各模组的必需前置仍要安装。

基础版已在 Forge 47.3.0 验证，全联动组合已在 **47.4.23** 验证。Rebirth 内部版本 1.7.3 需要使用已验证的 47.4.23，不能在 47.3.0 正常加载。社区依赖固定版本与来源见[依赖说明](../libs/optional/README.md)；源质、GP、Impetus 六个接口继续占位。

阅读顺序：第 2～6 节是机器、配方、批量、线程与智能接口基础；第 7 节是 GeckoLib；第 1.1 节介绍可视化编辑器；文末各独立章节介绍可选资源与 ME 气体。

## 1. 配置目录和重新加载

首次启动后，编辑实例中的：

```text
config/modularmachinery/machinery/<机器文件>.json
config/modularmachinery/recipes/<配方文件>.json
```

机器和配方文件递归扫描子目录，不需要修改模组源码或登记文件名。机器文件名可以和 JSON 中的 `registryname` 不同，但整合包内最好保持一致；配方真正使用的是 JSON 中的 `registryName`，该值必须唯一。子目录不提供独立命名空间，避免使用重复文件名或注册名。

`default_*` 只在配置主目录首次创建时复制为模板；复制模板时会把目录层级扁平化，这不限制作者自行使用子目录。已有配置不会被新 JAR 覆盖，删除的默认文件不会自动补回。

修改后在游戏内执行 `/reload`。如果改动了资源包中的 `.geo.json`、动画或纹理，还需要重新加载客户端资源（通常按 `F3+T`），并重新执行 `/reload` 让脚本和服务器定义同步。


## 1.1 图形化机器和配方编辑器

拥有权限等级 2 的玩家可以执行：

```text
/modularmachinery gui
```

编辑器会从服务器读取当前已加载的机器和配方。左栏选择机器，中栏单击配方选中并查看需求，双击同一配方编辑显示名称与运行时间；右栏的输入、输出各有 `+` 按钮，点击后先选择物品、流体或能量。物品与流体可按中文名称或 ID 搜索，也支持 `@模组ID` 和 `#标签`，例如 `铁锭`、`水`、`@minecraft`、`#forge:ingots`。在普通物品或流体列表中，搜索标签用于筛选具体对象。流体显示自身材质，不依赖是否存在对应桶物品。能量直接填写 FE/t。

如果输入需要接受一组可互换物品，点击输入栏 `+` 后选择“物品标签（输入）”。列表显示 `#命名空间:路径`、成员数量和示例图标，悬停可查看成员示例。可搜索标签 ID 或成员在当前客户端语言下的名称，使用 `#forge:ingots` 筛选标签 ID，使用 `@forge` 筛选标签的命名空间；这里的 `@` 对应标签归属，而不是成员物品所属模组。选择后设置数量与概率并保存。例如选择 `#minecraft:planks`，数量设为 4，会接受任意一种木板，而不会固定成图标中的木板。数量仍为 1～64；标签必须存在且包含可用物品。标签仅用于输入，输出需要指定具体物品。

上述木板输入对应的配方 JSON 写法：

```json
{
  "type": "modularmachinery:item",
  "io-type": "input",
  "item": "tag:minecraft:planks",
  "amount": 4,
  "chance": 1.0
}
```

选中物品或流体后填写数量、概率，再点击“保存”添加；取消或按 Esc 不会添加需求，也不会写入文件。普通物品数量为 1～64，需要更多时使用机器或并行控制器的批量倍率；流体数量单位为 mB；能量单位为 FE/t，不设置概率。物品和流体概率为 0～1，例如 `0.25` 表示 25%。点击现有需求可以修改这些数值，红色 `×` 删除对应对象。配方至少保留一个需求；要替换最后一条，请先添加新需求，或直接删除整个配方。

编辑普通物品、物品标签输入或流体需求时，可以点击“重新选择”，通过对应的搜索界面更换对象。标签输入会重新选择标签，普通物品仍选择具体物品；如需改变这两种类型，请先添加新的需求再删除旧需求。窗口会显示当前对象的图标、名称和 ID；更换对象会保留数量、概率以及输入输出方向，也会保留尚未保存的数值输入。在搜索界面点击“返回”或按 Esc 会返回需求窗口；在需求窗口点击“保存”才会一起写入更换结果和数值，点击“取消”或按 Esc 则放弃整次修改。新增需求也可以在保存前重新选择。旧版元数据表达式、带有 NBT 条件或 NBT 显示数据的需求，以及空标签和标签输出暂不支持此替换操作，请修改源文件。

配方栏的 `+` 创建本地草稿，可以先修改名称和运行时间，保存第一个需求时才一起写入服务器的配方 JSON。尚未保存首个需求就关闭编辑器，草稿会丢失。机器需要通过构建工具或命令创建。

双击已保存配方，在名称／运行时间窗口点击“高级设置”。窗口分为“加工与显示”“工厂线程”和“附加说明”三页，切换页签不会丢弃未保存内容。在“加工与显示”页可以调整配方优先级和每 tick 处理失败时是否取消本次加工。优先级为 -2147483648～2147483647 的整数，默认 0，**数值越小越先尝试**，例如 -10 先于 0、0 先于 10；相同优先级继续使用引擎原有排序。这里是配方搜索优先级，与智能接口类型的分配优先级不同。

“每 tick 处理失败时取消”对应源版 `cancelIfPerTickFails`，默认关闭：资源或输出空间不足时暂停等待；开启后，本次配方在持续处理失败时取消，已经消耗的输入不返还。机器自身失败进度策略及事件脚本仍可影响最终行为，该开关不设置物品或流体概率。

“允许批量加工”对应源版 `parallelized`，默认由全局 `recipe-parallelize-enabled-bydefault` 决定（初始为开启）：允许该配方使用机器和并行控制器提供的批量容量，实际批量仍受资源和空间限制；关闭后，该配方每次只加工一份，普通控制器和工厂线程均适用。它不改变机器自身的倍率，也不改变其他配方的开关或工厂线程数量。正在加工的任务保留开工时已消耗材料所对应的批量；修改开关影响之后的新开工。

“在 JEI 中显示配方”对应源版 `loadJEI`，默认开启。关闭只隐藏该配方的 JEI 页面，机器仍可加工，编辑器仍保留该配方，机器蓝图和结构预览也继续显示。保存后通过现有定义同步流程刷新已连接客户端的 JEI 索引，重新开启可恢复显示；未安装 JEI 也可保存该设置。它不用于禁止配方运行。

“工厂线程”页提供“工厂线程名称”和“配方线程上限”。名称对应 `threadName`，留空表示不限名称；填写后精确匹配机器脚本中已声明的核心线程，区分大小写和空格，也继续受核心线程配方列表限制。这里不会创建线程。名称最多 256 字，不能含换行或控制字符，不支持颜色代码转换。

配方线程上限对应 `maxThreads`：`-1` 为默认，不额外限制；`0` 禁止本配方在工厂中开始新的加工（包括核心线程）；正整数表示源版的线程上限，最大 2147483647。当前移植版同一配方仍最多占用一个线程，因此填写大于 1 不会重复开工。它不改变机器总线程数，也不改变机器或并行控制器的批量倍率。这两项设置只限制工厂开工，普通控制器不受影响，已启动且已消耗输入的加工不会因保存设置而取消。

“附加说明”页编辑配方根字段 `tooltips`，它会作为 JEI 配方详情中的附加文字显示。点击 `+ 行` 添加，选中列表中的行后在下方输入框填写；`↑`／`↓` 调整顺序，行末红色 `×` 删除。列表区可以滚轮翻页，允许空行、重复行及清空全部说明。最多 64 行，每行最多 512 字，总长度最多 8192 字；每个数组条目是一行，不接受行内换行或控制字符。超出这些限制的旧配置需先缩减后保存，界面会提示原因。

说明可填写普通文字或语言翻译键，例如 `item.minecraft.diamond`；客户端存在对应语言条目时按翻译显示，没有对应条目则显示原文，不会把翻译结果写回服务器。新输入或修改的行支持 `&6金色说明&r` 等颜色／格式代码；未编辑的原有 `§` 格式和普通 `&` 内容保持原样。点击“预览说明”查看当前客户端语言和颜色，长文字自动换行，预览区可滚动；“返回编辑”或首次 Esc 返回编辑，预览不保存。这与催化剂等需求自身的 `tooltips` 是不同字段，本页不会修改需求提示。

JEI 配方页的长说明会按页宽换行，超过页面剩余空间时显示“更多说明（悬停查看）”，可悬停下方文字区域查看说明；特别长的内容可在编辑器的滚动预览中完整查看。若关闭“在 JEI 中显示配方”，附加说明仍保存，但该配方页面也随之隐藏。

高级窗口的“应用”只更新本地草稿，返回配方窗口点击“保存”才一起提交名称、时间和三个页签的高级设置；高级窗口“取消”或 Esc 仅丢弃子窗口本次修改（说明预览中首次 Esc 返回编辑），配方窗口“取消”或 Esc 放弃整次修改。切换页签、进入高级窗口、返回及调整窗口大小保留未完成输入，返回后可看到优先级、批量开关与 JEI 显示摘要。新配方需先保存首个需求再编辑高级设置。共享配方文件会影响其所有机器变体；脚本和适配器来源只读。保存保留配方 ID、需求、NBT、接口分组及其他未编辑字段，并拒绝覆盖未经重载的外部文件修改。

底部可修改机器显示名称，点击“预览”查看颜色与格式，确认后点击“保存名称”。配方编辑窗口也提供“显示名称”和“预览名称”，例如 `&6&l合金冶炼&r`；点击“保存”会一起保存名称与运行时间。配方名称留空则恢复显示原 ID。预览不保存修改，返回后仍可继续编辑。

配方显示名称使用 JSON 中可选的 `"displayName": "&6合金冶炼"`，支持与机器名称相同的颜色和格式代码。配方列表会同时显示名称和原 ID，JEI 页面、工厂普通线程标题使用该名称；悬停 JEI 名称、工厂运行行或普通控制器状态区域可查看完整名称和 ID。工厂预设核心线程保留自己的线程标题，悬停仍可查看当前配方名称。`registryName` 保持不变，不会因改显示名称而改变脚本引用或运行配方的身份。旧配方无需补字段；同名显示名称也不会产生 ID 冲突。

保存操作只允许权限等级 2 的玩家执行，并且由服务器验证物品/流体 ID、数量、概率和配置路径。文件写入使用临时文件替换，保存后会自动重新加载机器和配方。

名称修改、需求增删和机器／配方删除都会等待服务器确认。等待时界面显示保存状态并暂时禁用编辑，成功后刷新为服务器实际加载的内容；失败时显示原因并保留尚未保存的输入。服务器确认和内容同步全部到达后才完成这次操作，其他未保存的新配方草稿会保留。保存等待期间按 Esc 可以退出界面，已提交给服务器的操作仍会继续。若打开界面后定义被其他操作更改，服务器会拒绝旧版本的保存，请重新打开编辑器读取最新内容。

输入栏 `+` 的“候选物品组（输入）”用于手动指定替代关系。例如一项需要 2 个铁锭，另一项需要 3 个金锭，只需满足其中一项即可。点击 `+ 物品` 或 `+ 标签` 搜索添加候选项；单击某项后，在下方修改该项数量，用“重新选择”更换对象，用红色 `×` 删除。上下按钮改变尝试顺序，上方候选项优先；列表超过四项时使用滚轮翻页。每组支持 1～64 项，每项数量为 1～64。右下的“消耗概率”作用于整个输入需求，范围为 0～1。点击“保存”一次提交整组；点击“取消”放弃本次所有修改。

每份加工必须由一个完整候选项支付，不能将不够 2 个的铁锭和不够 3 个的金锭拼成一份；批量加工的不同份数可以分别使用不同候选项。标签候选项仍接受标签内任意成员，图标只是示例。对应 JSON 示例：

```json
{
  "type": "modularmachinery:ingredient_array_input",
  "io-type": "input",
  "chance": 1.0,
  "items": [
    { "item": "minecraft:iron_ingot", "amount": 2 },
    { "item": "minecraft:gold_ingot", "amount": 3 }
  ]
}
```

现有普通候选输入组也可以点击打开编辑。保存会保留配方和需求的附加字段，以及原候选项在重排或重新选择前的附加字段。输入组的随机数量或特殊权重、带 NBT 条件、旧版表达式或超出编辑器数量范围的候选组需要修改源文件，避免简化编辑时改变其含义。

输出栏 `+` 的“随机物品组（输出）”用于按权重抽出一种产物。点击 `+ 物品` 搜索添加候选产物，单击产物后填写“最少”“最多”和“权重”。数量范围为 1～64，包含两端；最少与最多相同就是固定数量。权重可用非负小数，权重 0 的项不参与抽取。界面即时显示选中项按全部候选权重计算的“抽中占比”；实际抽取仍受现有引擎的输出空间检查影响。输出列表每页三项，滚轮翻页；上下按钮调整显示顺序，抽中占比由权重决定。

“产出概率”控制整组是否产出，范围为 0～1，与各项权重分开。例如整组概率 `0.5`，铁锭权重 `2`、金锭权重 `1`，在输出空间充足时，每次完成配方约有 33.33% 产出铁锭、16.67% 产出金锭、50% 不产出该组物品。铁锭数量若设为 2～4，抽中铁锭后会在 2、3、4 中取一个整数。批量倍率沿用现有引擎：抽取一种产物及数量后再乘本次倍率，不会把这组候选项全部产出。概率和权重支持科学计数法，例如 `1e-8`。

整组概率大于 0 时，至少一个产物的权重需要大于 0；整组概率为 0 时允许全部权重为 0。每组支持 1～64 项，输出必须是具体物品。已有标签输出、NBT 输出或超出数量范围的配置保留源文件编辑。保存会同时提交候选物品、数量范围、权重和整组概率，取消放弃整次修改；输入组和输出组不能在重新编辑时互换方向。

上述随机输出的 JSON 示例（候选项的 `chance` 表示权重，外层的 `chance` 表示整组产出概率）：

```json
{
  "type": "modularmachinery:ingredient_array_input",
  "io-type": "output",
  "chance": 0.5,
  "items": [
    { "item": "minecraft:iron_ingot", "amount": 2, "minAmount": 2, "maxAmount": 4, "chance": 2.0 },
    { "item": "minecraft:gold_ingot", "amount": 1, "minAmount": 1, "maxAmount": 1, "chance": 1.0 }
  ]
}
```

输入栏 `+` 的“催化剂（可选输入）”用于配置存在时才生效的效果。通过 `+ 物品`／`+ 标签` 添加候选项，逐项填写数量（1～64），用上下按钮调整查找顺序；满足其中一项即可。每组支持 1～64 项，列表每页三项，可滚轮翻页。缺少催化剂不会阻止配方运行，找到催化剂后才为本次加工应用其修改器。这里的“消耗概率”只决定是否扣除催化剂：`0` 表示保留催化剂且效果仍生效，`1` 表示每次扣除。默认沿用源版批量规则，一批加工只消耗一份候选项数量。

点击“修改器”进入子界面，用 `+ 修改器` 添加效果。点击属性按钮循环选择目标（运行时间、能量、物品、流体）、输入／输出、加法／乘法；物品和流体还能切换修改数量或概率。时间单位为 tick，能量单位为 FE/t，流体数量单位为 mB。乘法 `0.5` 表示减半，加法 `-10` 表示减少 10 个相应单位；概率加法 `0.1` 表示增加 10 个百分点。时间与能量不提供概率选项；时间的输入输出方向不影响结果。支持有限负数、小数与科学计数法，最多 32 个修改器。时间修改沿用引擎先加后乘的规则，其他目标按列表顺序应用；用上下按钮调整顺序、红色 `×` 删除选中行。物品目标也作用于候选物品组，流体目标包含每 tick 流体需求。

子界面的“应用”只更新本地催化剂草稿，返回后点击“保存”才写入服务器。“取消”或 Esc 放弃子界面本次更改；回到催化剂窗口再取消会放弃整次修改。调整窗口大小保留未完成的数值输入。保存继续等待服务端确认，并保留已有配方、催化剂、候选项及修改器的附加字段。NBT、高级候选表达式、特殊候选权重、超出数量范围的催化剂，以及气体等当前界面未支持的修改器目标需要修改源文件，不能用普通编辑覆盖。

点击催化剂窗口中的“提示文字（N）”，可编辑当前催化剂需求自身的 `tooltips`。`+ 行` 添加，选中行后输入文字，`↑`／`↓` 调整顺序，行末红色 `×` 删除；列表与预览分别支持滚动。最多 64 行、每行 512 字、合计 8192 字，不接受行内换行或控制字符；保留空行、空格及重复行。旧配置超出限制或提示数组格式异常时请先在源文件中调整，避免普通编辑覆盖高级定义。

提示与源版一致，显示在 **JEI 催化剂物品的悬停信息**中，按原文显示，不解析语言翻译键。新输入或实际修改的行支持 `&6金色文字&r` 等格式代码；未修改的旧 `§` 格式和普通 `&` 内容保持原样。“预览说明”按当前宽度换行并可滚动查看；首次 Esc 返回编辑，再次 Esc 放弃子窗口本次更改。“应用”只更新催化剂草稿，回到催化剂窗口点击“保存”才写入服务器；父窗口取消会一起放弃提示、候选项和修改器更改。调整窗口大小保留输入与预览状态。

催化剂提示与配方根字段的附加说明互相独立，不影响催化效果。清空已有提示会保存 `tooltips: []`；从未设置且未添加文字时继续省略该字段。保存还会比较已加载的完整配方源文档，拒绝覆盖未经重载的外部修改。

以下需求表示：存在一个钻石时，运行时间减半、输入能量降至 80%，每次有 20% 概率消耗钻石；没有钻石仍按原参数运行。

```json
{
  "type": "modularmachinery:catalyst",
  "io-type": "input",
  "chance": 0.2,
  "items": [ { "item": "minecraft:diamond", "amount": 1 } ],
  "modifiers": [
    { "target": "modularmachinery:duration", "io": "input", "operation": 1, "multiplier": 0.5, "affectChance": false },
    { "target": "modularmachinery:energy", "io": "input", "operation": 1, "multiplier": 0.8, "affectChance": false }
  ]
}
```

输入栏 `+` 的“工具耐久（输入）”可以搜索具体工具，“耐久标签（输入）”可以搜索工具标签。类型选择窗口按两列排列。选择后分别填写“使用次数”（1～64）、“每次扣耐久”（1～2000000000）和“扣耐久概率”（0～1，支持科学计数法）。重新选择时保留这些输入；保存后写入并重载，取消不写文件。工具只能换工具，标签只能换标签，需要更改模式时另建需求。

新建需求使用源版普通物品格式，例如：

```json
{
  "type": "modularmachinery:item",
  "io-type": "input",
  "item": "minecraft:iron_pickaxe",
  "amount": 2,
  "consumeDurability": 10,
  "chance": 1.0
}
```

这里 `amount` 表示使用次数，不是工具件数。同一把工具可以承担多次使用；批量倍率 3 时使用 6 次，工具耐久充足时扣 60。剩余耐久不足一次消耗时仍算最后一次使用，然后损坏；多件匹配工具可以分担次数。次数不足时整次输入失败，不会先损坏部分工具。概率 `0` 不扣耐久，但开工仍须提供足够的可用次数。显式扣耐久不受耐久附魔的随机免伤影响，`Unbreakable` 物品不能供应耐久。

标签写成 `"item": "tag:minecraft:pickaxes"`，接受标签内可扣耐久的成员；不可损坏物品不参与。搜索和图标也只显示可扣耐久成员，图标仅为示例，不会将标签改成具体工具。已有 `item_durability` 兼容别名、附加字段、触发时间等源设置在修改时保留。NBT、旧表达式以及超出界面范围的耐久需求仍请修改源文件。

输入栏 `+` 的“熔炉燃料（输入）”用于添加任意熔炉燃料需求。直接填写燃烧时间（1～2000000000 tick）和消耗概率（0～1，支持科学计数法），无需搜索某一种燃料。煤炭图标仅为示例，实际接受 Forge 熔炼燃料规则认可的物品，包括其他模组注册的燃料。若需要固定数量的煤炭，应使用普通物品输入。

```json
{
  "type": "modularmachinery:item",
  "io-type": "input",
  "item": "any:fuel",
  "time": 1600,
  "chance": 1.0
}
```

`time` 表示所需燃料的熔炉燃烧时间，不是配方运行时间，也不读取 `amount` 作为燃料数量。煤炭和木炭通常各提供 1600 tick（以游戏内燃料规则为准）；上述配置在批量倍率为 3 时需支付 4800 tick，可以使用混合燃料支付。按整件燃料扣除，多出的燃烧时间丢弃，不留到下次配方。熔岩桶等容器燃料使用后在原槽留下容器；异常堆叠的容器燃料不参与消耗。库存不足时不先扣一部分；燃料与其他显式物品输入也不能重复使用同一件物品。消耗概率 `0` 不扣燃料，但开工仍须具备足够的燃烧时间。

燃料窗口支持修改、取消及调整窗口大小后保留输入；原有配方和需求附加字段、触发设置及遗留 `amount` 字段保留。NBT 条件、超出界面范围或其他高级燃料配置仍请修改源文件。燃料需求不能通过“重新选择”变成普通煤炭需求；需要更改类型时另建需求。

### 需求触发时间与重复触发

选中已保存的一次性输入需求，点击底部“触发设置”，或 **Ctrl+右键需求行**。支持普通物品／标签、耐久、燃料、候选输入组、普通流体，以及安装 Mekanism 时的普通气体输入；催化剂、智能接口条件、持续流体／气体、FE/t 和输出不在此窗口编辑。新需求先保存，脚本及适配器来源继续在源文件中维护。

“触发 tick”留空表示删除 `triggerTime`，恢复默认开工扣料；填写 `0` 同样在开工扣料，但保留显式字段。正整数表示运行到该进度 tick 时才扣料，须小于配方运行时间。例如时长 100 tick，填 40：开工不扣这条需求，在进度 40 时支付。时间受配方时长修改器影响。此窗口限制写入可到达的基础时间；手工缩短配方时间或脚本动态改动时长后，作者仍需确认触发点可以到达。

“重复触发”可切换默认（删除 `triggerRepeatable`）、关闭（显式 `false`）和开启（`true`）。关闭时，同一轮加工成功支付后不会再次支付；开启时，失败回退后再次经过同一触发 tick，或者因其他需求失败停留在该 tick，可以再次支付。**它不是“每隔 N tick 扣料”或周期执行设置**，设置为 0／默认开工时重复标志不增加开工次数。

触发时缺料会走机器原有失败策略：默认等待供料；机器配置的回退／重置或配方取消策略仍有效。整批不足时不先扣部分数量；多个延迟输入按顺序支付，已成功的一条会记住完成状态，关闭重复时，后续条目缺料不会让前一条重复扣料。取消或 Esc 不保存；缩放保留输入；保存只改这两个字段，保留数量、概率、NBT、接口分组和批量设置。共用文件会同时修改各机器变体。客户端和服务端均需更新至协议 32。

```json
{
  "type": "modularmachinery:item",
  "io-type": "input",
  "item": "minecraft:iron_ingot",
  "amount": 2,
  "triggerTime": 40,
  "triggerRepeatable": false
}
```

两个字段对应源版脚本 `setTriggerTime`／`setTriggerRepeatable` 能力。已核对的 1.12.2 源码中 `ioTick` 的普通需求入口与内部正数判断存在相互矛盾的条件；这里沿用移植版既有的正数延迟输入实现，并修复失败跳过和未成功支付就标记的问题，不宣称照搬这一源码分支的实际执行结果。输出按现有完成阶段产出，持续资源沿用逐 tick 处理。

通过底部“批量设置”，或 **Shift+右键已有需求行**，可独立设置选中需求是否随本次批量倍率放大。窗口提供三种策略：“恢复默认”移除该需求的 `parallelizeUnaffected` 字段；“随批量放大”保存 `false`；“不随批量放大”保存 `true`。例如需求 2 个物品、实际批量为 4，通常消耗或产出 8 个，选择不放大后整批仍为 2 个。普通流体 mB、持续流体 mB/t、能量 FE/t、工具使用次数和燃料时间预算同理，概率与配方时长不会由此更改。

这是每条需求的设置，不改变机器／并行控制器倍率、线程数或其他需求。多个需求可分别设置；不放大时该需求仍须能支付一份或容纳一份产物。支持普通物品／标签、耐久、燃料、候选输入组、随机物品输出组、普通／持续流体和能量；安装 Mekanism 时已有气体／持续气体需求也支持此独立入口。NBT、触发时间、接口分组和其他源字段原样保留，所以基本物品选择器不能编辑的高级 NBT 需求也可单独设置批量策略。

催化剂默认每批只取一份，其可选效果有独立生命周期，本入口不修改催化剂；智能接口数值条件没有消耗量，也不提供此设置。新建需求先完成保存，再使用本入口；脚本和适配器生成配方仍只读。取消或 Esc 不写文件，调整窗口大小保留选择，保存等待服务器确认并重载。共享源文件会同时更新所有机器变体；拒绝覆盖未重载的外部修改。

`parallelizeUnaffected` 是移植版对源版 CraftTweaker `setParallelizeUnaffected` 的 JSON 扩展，并非声称原版 JSON 原生支持该键。现在 JSON 加载器实际应用此字段；已有文件中写过 `true` 的需求会开始遵守它，需留意对应整合包的批量平衡。必须使用布尔值 `true`／`false`，错误类型会拒绝该配方并记录原因。能量输出现在会在每个成功推进的 tick 发电；输出空间不足时等待且不提前扣除同一 tick 的流体／能量输入，已完成等待出料时不再发电。

右键已保存的需求行，或使用底部“接口分组”，可单独修改 `selector-tag`。窗口显示当前绑定，支持搜索结构中声明的分组并单击选择；“不限接口”移除分组限制，允许使用整个成型结构的对应接口，包括已分组和未分组接口。保存立即重载，取消或 Esc 不写文件，调整窗口大小保留选择和搜索。已绑定需求行显示蓝色“组”，悬停可查看完整标签。

`selector-tag` 是结构接口分组，和第 6 节的智能接口数值类型不同。机器结构与需求使用同一标签：

```json
{ "x": 1, "y": 0, "z": 0, "elements": "modularmachinery:blockinputbus@0", "selector-tag": "ore_input" }
```

```json
{ "type": "modularmachinery:item", "io-type": "input", "item": "minecraft:iron_ore", "amount": 1, "selector-tag": "ore_input" }
```

标签精确匹配、区分大小写；`ore_input` 与 `Ore_Input` 不同。没有对应接口时不会借用其他分组。输出需求同样可指定输出口。窗口列出静态结构、动态节段及端盖模板的标签；实际可用接口仍取决于当前成型结构，模板声明分组不保证该次成型一定包含它。这里不能新建结构分组，需先修改机器结构文件；多台机器共用一份配方文件时，候选列表仅包含它们共有的标签，保存会作用于该文件产生的全部配方。现有未声明标签可原样保留或解除，但不能随意输入新标签。

接口分组保存只更新需求的 `selector-tag`，不会改数量、概率、NBT 或其他源字段，因此有高级 NBT 条件的内置需求也可单独改分组。新建需求需先完成首次保存，再编辑分组。脚本／适配器生成配方和未明确支持此字段的第三方需求仍不可通过该入口修改。服务端会验证结构声明、权限、窗口、文档版本及磁盘源需求，源文件变化后需重载并重新打开。

输入栏 `+` 还可选择“智能接口数值（输入）”，搜索机器已声明的类型并填写最小值、最大值。已有条件单击即可修改，需求行显示比较器图标、类型和范围，悬停查看完整文本。支持负数、小数和科学计数法，使用与源版相同的 float 精度；两端都包含，上下限相同表示精确匹配。该条件仅检查智能接口发布的数字，不消耗物品、不设置概率、不受批量倍率放大。机器需先在 `smart-interface-types` 中声明类型，并在成型结构中提供对应的智能接口，详见第 6 节。

共用配方文件的多台机器仅提供共同声明的数值类型，修改会影响该文件生成的全部配方；没有共同类型时无法保存。已有未声明类型可改选已声明类型，不能原样保存为有效配置。新配方允许以该条件作为首个需求；取消或 Esc 不落盘，调整窗口大小保留搜索、选择和上下限。保存只修改 `interface`、`min`、`max`，保留需求分组、触发设置及其他源字段；源文件在未重载的情况下被外部修改时拒绝覆盖。

输入、输出栏的 `+` 均提供“流体/刻”，搜索具体流体后设置速率，范围为 1～2000000000 mB/t 的整数，不设置概率。单击已有持续流体需求可修改速率，点击“重新选择”更换流体；取消不保存，调整窗口大小保留未完成输入，搜索中返回也保留原有速率。需求行显示流体纹理和 `mB/t`，悬停可查看完整流体 ID 与持续输入／输出说明。

每个成功推进的配方 tick 按速率处理流体，批量倍率通常放大速率；例如 100 mB/t、运行 80 tick、批量 2，需要共 16000 mB，时长修改器也会影响总量。缺料或输出满仓时暂停，恢复供应或空间后继续；已完成等待其他产物出料时不继续处理持续流体。普通“流体”仍为一次性数量，二者不会通过修改窗口相互转换，需另建需求。保留源需求的接口分组、触发设置、批量设置和其他附加字段，原有 `chance: 1` 可保留。带 NBT、流体标签等高级选择器请修改源文件，独立的接口分组编辑仍可使用；脚本／适配器来源只读。保存检查源需求及所属机器，拒绝覆盖未经重载的外部修改。

适配器生成配方、CraftTweaker 脚本配方和第三方需求类型在当前版本显示为只读或需要回到源文件编辑。NBT 内容仍需修改源文件，但内置需求的接口分组可通过上述独立入口修改。普通物品标签输入、固定数量的候选物品输入组、具体物品的随机输出组、上述普通催化剂、耐久输入、熔炉燃料输入、智能接口数值条件和每 tick 流体需求已支持可视化编辑；流体标签等高级选择器仍请修改源文件。客户端与服务器需要使用同一版 JAR，当前网络协议为 32。

配方行上的“只读”表示没有可直接编辑的独立 JSON，悬停可查看原因。例如默认 Alloy Smelter 的 `smelting_…` 配方由 `alloy_smelter_furnaces.adapter.json` 中的 `minecraft:furnace` 适配器从游戏熔炼配方自动生成，每次重载会重新生成。需要统一调整时编辑 `config/modularmachinery/recipes/alloy_smelter_furnaces.adapter.json` 的修改器；需要调整熔炼源配方时修改对应数据包。适配器模板中的修改器会作用于这一组生成配方，不能当作单条配方的修改入口。

## 2. 制作一台基础多方块机器

### 2.1 机器 JSON

创建 `config/modularmachinery/machinery/example_machine.json`：

```json
{
  "registryname": "example_machine",
  "localizedname": "示例加工机",
  "parallelism": 2,
  "max-parallelism": 16,
  "parts": [
    { "x": -1, "y": 0, "z": -1, "elements": "modularmachinery:blockcasing@4" },
    { "x":  0, "y": 0, "z": -1, "elements": "modularmachinery:blockcasing@4" },
    { "x":  1, "y": 0, "z": -1, "elements": "modularmachinery:blockcasing@4" },
    { "x": -1, "y": 0, "z":  0, "elements": "modularmachinery:blockcasing@4" },
    { "x":  1, "y": 0, "z":  0, "elements": "modularmachinery:blockcasing@4" },
    { "x": -1, "y": 0, "z":  1, "elements": "modularmachinery:blockcasing@4" },
    { "x":  0, "y": 0, "z":  1, "elements": "modularmachinery:blockcasing@4" },
    { "x":  1, "y": 0, "z":  1, "elements": "modularmachinery:blockcasing@4" },
    { "x":  0, "y": 1, "z": -1, "elements": "modularmachinery:blockinputbus" },
    { "x":  0, "y": 1, "z":  1, "elements": "modularmachinery:blockoutputbus" },
    { "x": -1, "y": 1, "z":  0, "elements": "modularmachinery:blockenergyinputhatch" }
  ]
}
```

要点：

- `registryname` 是机器的逻辑名称，配方中的 `machine` 必须完全相同。
- `localizedname` 是预览、JEI 和控制器界面使用的显示名称。
- `parts` 的坐标相对控制器，控制器固定为 `(0, 0, 0)`，不要在 `parts` 中重复声明。
- 控制器朝向会旋转相对坐标；测试结构时保持朝向一致。
- `elements` 可以是字符串，也可以是数组。数组表示该位置接受任意一个候选方块。
- `@4` 是方块状态变体，不是物品数量。用 JEI 查看各部件可用的变体。
- `parallelism` 是机器自身的基础批量倍率；`max-parallelism` 是上限。

机器也可以声明：

```json
{
  "registryname": "single_batch_machine",
  "localizedname": "单批次机器",
  "parallelizable": false,
  "requires-blueprint": true,
  "parts": []
}
```

`parallelizable: false` 会关闭该机器的并行批量；`requires-blueprint: true` 要求通过蓝图或绑定命令明确指定机器，不参与未绑定控制器的自动结构识别；它不要求先开启世界投影。实际机器必须提供有效的 `parts`，上面的空数组只用于展示字段。

### 2.2 结构检查

管理员可以看着控制器执行：

```text
/modularmachinery bind example_machine
/modularmachinery form
/modularmachinery check
```

`form` 的错误报告使用世界坐标。`check` 会逐条说明配方当前是输入不足、输出空间不足、能量不足还是智能接口数值不匹配。

### 2.3 用多方块构建器从世界生成机器 JSON

当前移植版提供 `modularmachinery:itemconstructtool`（建造选择工具）和 `/modularmachinery register <机器名>` 命令。建造工具负责标记范围，命令负责用指定名称写入机器文件：

1. 手持建造选择工具，普通右键第一个方块，标记第一个角。
2. 潜行并右键第二个角，标记另一个角；客户端会显示选区框。
3. 看着机器控制器执行 `/modularmachinery register example_machine`。命令会读取两个角之间的方块、方块实体 NBT 和相对控制器坐标，并写入指定名称的机器 JSON。
4. 只有一个角时，继续标记第二个角；潜行并右键空气可以清除选区。

该命令需要权限等级 2，只能由玩家执行；命令方块和服务器控制台没有玩家选区，不能使用此命令。机器名必须使用小写字母、数字、点、下划线或连字符，已加载或已存在的文件不会被覆盖。

如果不需要自定义名称，也可以直接看着控制器右键建造选择工具，继续使用自动生成的 `machine-玩家名-时间.json` 文件名。

命令生成的文件为 `config/modularmachinery/machinery/example_machine.json`；直接使用工具自动命名时，生成文件位于：

```text
config/modularmachinery/machinery/machine-<玩家名>-<时间>.json
```

工具自动命名会避让重名，生成的 `registryname` 和 `localizedname` 也会使用同一个安全文件名。使用自动命名的作者通常需要在生成后手动改为稳定名称，并同步修改 JSON 中的 `registryname` 与 `localizedname`，再执行 `/reload`。使用指定名称的 `register` 命令则无需这一步。

专用服务器会把文件写在服务器的 `config` 目录，并在聊天栏提示；需要从服务器取出文件，再放入整合包的配置或模板目录。选区体积超过工具上限时不会写入文件。

这个流程与源版的关系如下：源版 `ItemConstructTool` 是逐个方块点选，点击控制器后自动保存；当前 1.20.1 移植版改为两角盒选区，并保留了自动保存行为，同时新增了带机器名的 `register` 命令。源版的 `MachineBuilder.registerMachine(...)` 仍然是 CraftTweaker 脚本 API，用于代码声明机器，不是读取世界选区的命令。

## 3. 编写配方 JSON

创建 `config/modularmachinery/recipes/example_machine_iron.json`：

```json
{
  "machine": "example_machine",
  "registryName": "example_machine_iron",
  "recipeTime": 200,
  "priority": 0,
  "requirements": [
    {
      "type": "modularmachinery:energy",
      "io-type": "input",
      "energyPerTick": 100
    },
    {
      "type": "modularmachinery:item",
      "io-type": "input",
      "item": "minecraft:iron_ingot",
      "amount": 16
    },
    {
      "type": "modularmachinery:fluid",
      "io-type": "input",
      "fluid": "minecraft:water",
      "amount": 1000
    },
    {
      "type": "modularmachinery:item",
      "io-type": "output",
      "item": "minecraft:iron_block",
      "amount": 1
    }
  ]
}
```

字段规则：

- `machine` 对应机器的 `registryname`。
- `registryName` 是配方自己的唯一名称，注意大写 `N`。
- `recipeTime` 使用 tick，20 tick 等于 1 秒。
- `energyPerTick` 是每 tick 的能量需求；并行批量会按批量倍率增加消耗。
- 物品 `amount` 按每批计算，移植版会将单项物品数量限制在 1 到 64。
- 流体 `amount` 使用 mB；连续供液配方使用 `modularmachinery:fluid_pertick`。
- 输出可以增加 `chance`，例如 `0.5` 表示 50% 概率产出。

常用需求类型：

```json
{ "type": "modularmachinery:item", "io-type": "input", "item": "minecraft:coal", "amount": 4 }
{ "type": "modularmachinery:item", "io-type": "output", "item": "minecraft:diamond", "amount": 1 }
{ "type": "modularmachinery:fluid", "io-type": "input", "fluid": "minecraft:water", "amount": 1000 }
{ "type": "modularmachinery:energy", "io-type": "input", "energyPerTick": 40 }
```

Mekanism 安装后可以使用 `modularmachinery:gas` 和 `modularmachinery:gas_pertick`。耐久、燃料、催化剂、NBT 和输入候选数组的完整字段见 [ADDING_MACHINES.md](ADDING_MACHINES.md)。

## 4. 并行控制器和批量倍率

在机器的 `parts` 中加入：

```json
{ "x": 1, "y": 1, "z": 0, "elements": "modularmachinery:blockparallelcontroller" }
```

玩家右键并行控制器后，可以使用 `-100/-10/-1`、`+1/+10/+100` 或直接输入数字调整倍率。有效批量遵循：

```text
机器 parallelism + 所有并行控制器的倍率
```

随后再受 `max-parallelism`、`parallelizable`、输入库存、能量、流体和输出空间限制。整合包作者不应把“设置了 8 倍”理解为一定会消耗 8 批；资源不足时系统会自动降到能完整运行的批量。

## 5. 工厂控制器和线程设置

把结构中的普通控制器换成 `modularmachinery:blockfactorycontroller`，即可使用工厂模式。机器的工厂线程上限默认是 10（源版配置加载后的默认值），可以通过 COMMON 配置 `factory-system.default-factory-max-thread` 或 CraftTweaker 调整。

配方可以声明：

```json
{
  "machine": "example_machine",
  "registryName": "example_machine_gold",
  "recipeTime": 100,
  "maxThreads": 2,
  "threadName": "gold_line",
  "requirements": [
    { "type": "modularmachinery:item", "io-type": "input", "item": "minecraft:gold_ingot", "amount": 1 },
    { "type": "modularmachinery:item", "io-type": "output", "item": "minecraft:gold_nugget", "amount": 1 }
  ]
}
```

实际配方必须填入有效的需求。`maxThreads: 0` 禁止该配方在工厂模式运行；省略时为 `-1`，表示不额外限制。`threadName` 用于匹配 CraftTweaker 声明的核心线程。

当前线程调度规则：

- 一个线程同一时间只运行一个配方。
- 同一工厂中，同一种配方最多占用一个线程；其他线程可以运行不同配方。
- 机器自身并行倍率和并行控制器倍率仍然有效；线程数量与每线程批量是独立限制。
- 输出空间或输入资源不足时，线程等待，不会因为等待直接丢失必定输出。

### 5.1 使用 CraftTweaker 设置工厂上限和核心线程

创建 `scripts/modularmachinery/factory.zs`：

```zenscript
import mods.modularmachinery.FactoryRecipeThread;
import mods.modularmachinery.MachineModifier;

// 这台机器最多保留 4 个工厂线程。
MachineModifier.setMaxThreads("example_machine", 4);

// 名为 "gold_line" 的核心线程只搜索列出的配方。
val goldLine = FactoryRecipeThread.createCoreThread("gold_line")
    .addRecipe("example_machine_iron")
    .addRecipe("example_machine_gold");
MachineModifier.addCoreThread("example_machine", goldLine);
```

核心线程的配方必须先用 JSON 或 CraftTweaker 注册，再把完整的配方名加入线程。脚本修改后执行 `/reload`。同一配方即使 `maxThreads` 大于 1，在同一台工厂中仍不会被多个线程同时占用。

## 6. 智能接口：用一个数字切换多条配方

### 6.1 在机器中声明类型

在机器 JSON 顶层加入：

```json
{
  "registryname": "example_machine",
  "smart-interface-types": [
    {
      "type": "mode",
      "default-value": 1,
      "priority": 100,
      "value-info": "%.0f",
      "not-equal-message": "模式不匹配"
    }
  ]
}
```

并在结构中放置：

```json
{ "x": -1, "y": 1, "z": 0, "elements": "modularmachinery:blocksmartinterface" }
```

`default-value` 是机器成型时发布的初始值。`value-info` 是 Java `String.format` 格式；`priority` 只在声明多个类型、接口数量不足时参与选择。

### 6.2 让配方读取该数字

```json
{
  "type": "modularmachinery:interface_number_input",
  "io-type": "input",
  "interface": "mode",
  "min": 1,
  "max": 1
}
```

再复制一条配方，把 `min` 和 `max` 改成 `2`，输出改成另一种物品。玩家右键智能接口，输入 `1` 或 `2` 后按回车，机器就会选择对应配方。`max` 省略时等于 `min`，范围两端都包含。

常见错误：

- `interface` 没有在 `smart-interface-types` 中声明，配方永远不会成立。
- `default-value` 不落在任何配方范围内，机器成型后不会自动运行。
- 同一结构放多个接口时，额外接口会按声明优先级分配类型；只需要一个数字时放一个接口最清楚。

也可以用 CraftTweaker 声明类型和界面文本：

```zenscript
import mods.modularmachinery.MachineModifier;
import mods.modularmachinery.SmartInterfaceType;

val mode = SmartInterfaceType.create("mode", 1)
    .setValueInfo("%.0f")
    .setNotEqualMessage("模式不匹配");
MachineModifier.addSmartInterfaceType("example_machine", mode);
```

如果 JSON 已经声明了同名类型，脚本不会重复添加。

## 7. GeckoLib 动态模型、纹理和动画（高级）

当前移植版的 GeckoLib 渲染链绑定的是**控制器方块实体**，普通控制器和工厂控制器共用同一个渲染器。模型只在机器成型后接管控制器外观；未成型时仍显示普通控制器方块。蓝图和 JEI 的模拟成型预览也会使用已绑定的模型。

### 7.1 准备资源文件

用 Blockbench 的 GeckoLib 模板制作模型并导出，资源可以放在整合包资源包中：

```text
resourcepacks/ExamplePack/assets/examplepack/geo/dream_core.geo.json
resourcepacks/ExamplePack/assets/examplepack/animations/dream_core.animation.json
resourcepacks/ExamplePack/assets/examplepack/textures/dream_core.png
```

资源包根目录还需要一个正常的 `pack.mcmeta`。也可以把同样的目录放进自定义附属模组的 `src/main/resources/assets/examplepack/`。推荐使用自定义命名空间 `examplepack`，不要把资源放到 `minecraft` 命名空间。

资源路径在脚本中必须写成 `namespace:path`：

```text
examplepack:geo/dream_core.geo.json
examplepack:textures/dream_core.png
examplepack:animations/dream_core.animation.json
```

geometry、texture 和 animation 三个文件的路径必须同时存在。动画文件中的动画名称必须与脚本传给 `addGeoMachineAnimation` 的名称完全一致，例如：

```json
{
  "format_version": "1.8.0",
  "animations": {
    "animation.dream_core.idle": {
      "loop": true,
      "bones": {}
    }
  }
}
```

实际骨骼动画建议由 Blockbench 导出，不要手工删改导出的 geometry 文件结构。模型原点和尺寸应按控制器方块位置设计，否则会出现偏移或穿模。

### 7.2 用 CraftTweaker 绑定模型

创建 `scripts/modularmachinery/gecko.zs`：

```zenscript
import mods.modularmachinery.MachineModifier;

// 第一个参数必须是机器 JSON 的 registryname。
MachineModifier.setMachineGeoModel(
    "dream_energy_core",
    "examplepack:geo/dream_core.geo.json",
    "examplepack:textures/dream_core.png",
    "examplepack:animations/dream_core.animation.json"
);

// 第一个声明的动画会作为默认动画。
MachineModifier.addGeoMachineAnimation(
    "dream_energy_core",
    "animation.dream_core.idle",
    true
);
```

`setMachineGeoModel(machine, model, texture, animation)` 会同时注册模型并把它绑定到同名机器。机器名不存在或资源路径为空时，脚本会报告错误；资源文件不存在或路径大小写错误，则会在客户端资源加载/渲染日志中出现缺失资源。

### 7.3 复用同一模型

同一套模型可以给多台机器复用：

```zenscript
import mods.modularmachinery.GeoMachineModel;
import mods.modularmachinery.MachineModifier;

GeoMachineModel.registerGeoMachineModel(
    "dream_core_visual",
    "examplepack:geo/dream_core.geo.json",
    "examplepack:textures/dream_core.png",
    "examplepack:animations/dream_core.animation.json"
);

MachineModifier.setMachineGeoModel("dream_energy_core", "dream_core_visual");
MachineModifier.setMachineGeoModel("dream_energy_core_factory", "dream_core_visual");
```

`GeoMachineModel.registerGeoMachineModel` 只注册一个命名模型，不会自动绑定机器；必须再调用 `setMachineGeoModel(machine, modelName)`。

### 7.4 多个动画和动画回调

可以声明多个动画，顺序就是默认候选顺序：

```zenscript
MachineModifier.addGeoMachineAnimation("dream_energy_core", "animation.dream_core.idle", true);
MachineModifier.addGeoMachineAnimation("dream_energy_core", "animation.dream_core.active", true);
```

需要在每次 GeckoLib predicate 执行时改写动画列表时，可以注册：

```zenscript
import mods.modularmachinery.MMEvents;

MMEvents.onControllerModelAnimation("dream_energy_core", function(event) {
    event.setAnimation("animation.dream_core.active", true);
    event.playState = 0;
});
```

在当前移植版中，`playState = 0` 继续播放，非零值停止。回调每次渲染都会执行，脚本应保持轻量；事件对象只修改本次 predicate 的动画列表，不会污染全局模型注册表。要切换 geometry、纹理和动画文件的整套模型，可以注册多个命名模型，再使用 `MMEvents.onControllerModelGet` 修改 `modelName`。

### 7.5 资源和脚本的刷新顺序

推荐顺序：

1. 放入或更新资源包中的 `geo`、`textures`、`animations` 文件。
2. 按 `F3+T` 重新加载客户端资源。
3. 执行 `/reload` 重新执行 CraftTweaker 和 Modular Machinery 定义同步。
4. 重新绑定或重新成型控制器；模型只在成型状态显示。
5. 用蓝图或 JEI 打开预览，确认几何、纹理和动画三者一致。

资源重载后 MMCE 会刷新自身的 GeckoLib 模型版本并重置旧动画实例。如果仍然显示紫黑缺失纹理，优先检查命名空间、路径大小写和动画名称。

## 8. 配置分发与开发验证

整合包应分发自己的 `config/modularmachinery/`、所需的 `scripts/` 和已启用的资源包。专用服务器的 JSON 与脚本放在服务器实例；模型、动画和纹理需要发给客户端，定义同步不会代替资源包分发。不要为刷新模板而删除真实实例的配置目录；需要检查首次初始化时使用新的隔离实例。

源码工作区的自动回归方法及最近结果见 [VERIFY](../VERIFY.md)。自测会操作测试世界和配置，应使用专门运行目录；普通整合包作者无需安装 Gradle。JSON 修改后用 `/reload`，资源修改后用客户端 `F3+T`。服务端回归不能代替客户端模型与界面验证。

结构、NBT、动态结构等补充说明见 [ADDING_MACHINES.md](ADDING_MACHINES.md)，CraftTweaker 14 示例见 [recipe_builder.zs](examples/recipe_builder.zs) 和 [factory_core_threads.zs](examples/factory_core_threads.zs)。带日期的交付记录中旧文件名、哈希和缺口仅对应当时版本。


## 普通流体仓与 FE 能量仓容量

配置文件为实例的 `config/modularmachinery-common.toml`。容量按档位配置，同档位普通输入／输出仓共用设置；ME 总线不使用这些配置。

从本轮起，普通 FE 仓的初始容量调整为：

```toml
[energyhatch.size]
    tiny = 1000
    small = 10000
    normal = 100000
    reinforced = 1000000
    big = 10000000
    huge = 100000000
    ludicrous = 1000000000
    ultimate = 2147483647
```

单位为 FE，最高档使用有符号 32 位整数上限；次高档为十亿，其后每降一级除以 10。此调整只改变容量，`[energyhatch.limit]` 下各档对外传输设置保留原值；可单独调整为所需 FE/t。两类值均允许 1～2147483647。

**已有配置优先于新默认值。** 新 JAR 不覆盖旧值，需要把上述容量值合并到已有 `[energyhatch.size]` 段；不要重复添加同名段，也不要用片段覆盖完整配置。[可复制的容量片段](../examples/config/energy-capacities.toml)只包含这八项。修改后重启客户端／服务器，让已放置仓重新读取容量，单独 `/reload` 不会重建仓。调小容量前先清空对应仓。

流体仓仍使用 `[fluidhatch]` 下的同名档位键，例如 `normal = 64000` 表示 64 桶；单位为 mB（1000 mB＝1 桶），最高档名为 `vacuum`，容量允许 1～2147483647。流体仓的初始容量此次未调整。

## 全局加工默认值（Forge 1.20.1）

实例的 `config/modularmachinery-common.toml` 支持以下源版键名：

```toml
[parallel-controller]
    machine-parallelize-enabled-bydefault = true
    recipe-parallelize-enabled-bydefault = true
    max-machine-parallelism = 2048

[factory-system]
    default-factory-max-thread = 10

[mods.botania]
    "Mana Capacity" = 100000
```

机器 JSON 的 `parallelizable`、`max-parallelism` 和配方的 `parallelized` 显式值优先于全局默认；脚本在加载后对机器进行的修改继续生效。更改配置后重新启动实例，再执行 `/reload` 重新加载机器与配方定义；Forge 已触发 COMMON 配置热加载时，执行 `/reload` 即可让新默认用于定义。已经付费开工的批次保持原倍率。服务器会同步解析后的机器和配方数值，客户端本地 COMMON 配置不会覆盖这些结果。

`default-factory-max-thread` 的配置范围为 1～100。源版源码字段初始化为 20，但读取配置时缺省为 10；这里采用实际加载默认。脚本显式线程设置继续覆盖它。旧移植版本未写线程设置的机器将从 20 改为 10，需要保持旧容量的整合包可在此配置中填写 20。

源版 `enable-factory-controller-bydefault` 决定是否为每台机器额外注册独立工厂控制器物品；当前移植版使用一个可绑定机器的通用工厂控制器，未添加没有对应作用的同名配置。工厂仍遵循同一种配方最多占用一个线程，线程内可以批量加工。

## ME 气体输入／输出总线

可选依赖是 AE2 15、Mekanism 10.4 和 **Applied Mekanistics 1.4.3**（Forge 1.20.1）。AE2 本体没有气体存储类型，气体需存入 Applied Mekanistics 的化学品存储元件，不能放入普通流体元件。联机时客户端和服务器安装同一组依赖。

- 输入：`modularmachinery:blockmegasinputbus`，九个气体配置格与九格缓存。把装有气体的 Mekanism 容器拿到鼠标上，左击配置格选择该容器内的第一种气体；右击配置格清空。容器不会被消耗。总线从网络补满对应缓存。
- 输出：`modularmachinery:blockmegasoutputbus`，机器把产物写入缓存，总线自动送回网络。网络离线或拒收时保留剩余气体。
- 每格初始容量 8000 mB，最多五张 AE2 容量卡，每张使容量乘以 4。移除容量卡不会删除已有气体，输入总线会尝试把超量部分退回网络。
- 输入配置可使用 AE2 存储卡复制；普通 `modularmachinery:gas`、持续 `modularmachinery:gas_pertick`、批量和选择器均能使用这些缓存。
- 未安装所需依赖时保留方块 ID 和外观，右击会提示缺失依赖，不提供气体网络功能。

## Botania 魔力输入／输出接口

安装 Forge 1.20.1 的 Botania 及其必需依赖 Patchouli、Curios 后，原命名空间中的两个方块启用：

- `modularmagic:blockmanaproviderinput`：接收魔力发射器的魔力脉冲，供机器配方消耗。
- `modularmagic:blockmanaprovideroutput`：接受机器产出的魔力，按源版作为输出型魔力池加入 Botania 魔力网络；右击接口显示当前魔力与容量。

这两个方块加入机器结构后，可以使用源版配方格式：

```json
{
  "machine": "your_machine",
  "registryName": "your_mana_recipe",
  "recipeTime": 100,
  "requirements": [
    {"type": "modularmachinery:mana", "io-type": "input", "amount": 1000},
    {"type": "modularmachinery:mana", "io-type": "output", "amount": 10, "perTick": true}
  ]
}
```

`perTick` 缺省为 `false`：输入开工扣除一次，输出完工产生一次；`true` 表示每个工作 tick 的魔力量。`amount` 为 0～2147483647 的整数，支持需求批量倍率、`parallelizeUnaffected`、选择器和修饰符。默认接口容量为 100000，可通过上述 `Mana Capacity` 调整。多个魔力需求共享接口时会合并预留；魔力不足或输出满时机器等待。

Blood Magic 的生命源质／意志、Nature’s Aura 世界灵气、社区 Astral Sorcery 星能／星座及 Extra Utilities Rebirth 虹彩检测见后续章节。源质、GP 和 Impetus 继续保留占位。

## Blood Magic 生命源质和恶魔意志

可选依赖为 Forge 1.20.1 的 **Blood Magic 3.3.8-50** 和其必需依赖 **Patchouli**；客户端和服务器安装相同版本。发布包不内嵌 Blood Magic。

### 生命源质（LP）

将 `modularmagic:blocklifeessenceproviderinput`／`modularmagic:blocklifeessenceprovideroutput` 放入机器结构。右击打开接口，放入已经绑定主人的血之宝珠；Shift 点击可在宝珠槽和背包之间转移。宝珠槽只接受宝珠，宝珠不会成为机器配方的普通物品输入。

输入接口从宝珠主人的灵魂网络补充缓存，输出接口将机器产出的 LP 送回该网络。网络不足时输入停止补充，网络已满时输出保留剩余 LP。替换或移除宝珠不清除已经付费的缓存。缓存容量按源版配方预留和补充规则取**宝珠网络容量的十分之一**：弱效宝珠 500 LP，学徒宝珠 2500 LP，法师宝珠 15000 LP，大师宝珠 100000 LP，大魔导师宝珠 1000000 LP。

```json
{
  "machine": "your_machine",
  "registryName": "your_lp_recipe",
  "recipeTime": 100,
  "requirements": [
    {"type": "modularmachinery:lifeessence", "io-type": "input", "amount": 1000},
    {"type": "modularmachinery:lifeessence", "io-type": "output", "amount": 10, "perTick": true}
  ]
}
```

`amount` 为非负整数；`perTick` 缺省为 `false`，输入开工扣除一次，输出完工产生一次；`true` 则每个工作 tick 扣除／产出。支持机器批量、`parallelizeUnaffected`、选择器和修饰符；同组需求共享预留，不会因接口重复出现而多算资源。

### 恶魔意志

将 `modularmagic:blockwillproviderinput`／`modularmagic:blockwillprovideroutput` 放入结构。它们直接操作**接口所在区块**的恶魔意志，不存放意志宝石；可以配合 Blood Magic 的恶魔结晶器等设施建立区块意志环境。右击接口可查看五种意志的当前数量。多个同区块接口访问同一份资源，不会使资源翻倍。

```json
{
  "machine": "your_machine",
  "registryName": "your_will_recipe",
  "recipeTime": 100,
  "requirements": [
    {"type": "modularmachinery:will", "io-type": "input", "will-type": "raw", "amount": 1.25, "min": 2},
    {"type": "modularmachinery:will", "io-type": "output", "will-type": "corrosive", "amount": 0.5, "max": 100}
  ]
}
```

`will-type` 使用源版名称：`raw`、`corrosive`、`destructive`、`vengeful`、`steadfast`。`amount` 可以是小数；输入消耗后必须保留至少 `min`（默认 0），输出加入后不得超过 `max`（默认 100）。数值须有限且非负。与源版一致，输入只使用 `min`，输出只使用 `max`；输入的 `min` 可以超过未使用的默认 `max`。

与源版一致，意志是开工／完工的一次性操作，**不随机器批量倍率放大**，也不支持 `perTick: true`。同一配方最多占一个工厂线程；同线程内的物品、流体、LP 等仍可批量加工。源版意志输出检查中的减法笔误已改为“当前值加产出不超过上限”，避免超过配置上限。

JEI 配方页会显示 Blood Magic 需求摘要；将鼠标移到配方文字区域，可查看全部 LP 与意志需求。


## Nature’s Aura：世界灵气输入与输出

另行安装 Forge 1.20.1 的 **Nature’s Aura 39.4** 及其所需的 Patchouli。本体不内嵌 Nature’s Aura。

结构内使用 `modularmagic:blockauraproviderinput`（输入）和 `modularmagic:blockauraprovideroutput`（输出）。这两种接口不存放物品，也没有独立缓存；它们访问接口位置半径 1 格球形区域的世界灵气。右击可查看当地的灵气类型及数值；未安装依赖时会提示。

```json
{
  "machine": "your_machine",
  "registryName": "your_aura_recipe",
  "recipeTime": 100,
  "parallelized": true,
  "requirements": [
    {"type": "modularmachinery:aura", "io-type": "input", "aura-type": "naturesaura:overworld", "amount": 10000, "min-aura": 0},
    {"type": "modularmachinery:aura", "io-type": "output", "aura-type": "naturesaura:overworld", "amount": 2000, "max-aura": 1500000}
  ]
}
```

`amount`、`min-aura`、`max-aura` **统一使用 Nature’s Aura 原始单位**，不使用瓶数或额外倍率。无消耗点时，基础环境灵气通常为 1,000,000。输入开工扣除，输出完工加入；没有 `perTick: true` 模式。`amount` 须为非负整数，两个限制可为有符号整数。`min-aura` 只影响输入，`max-aura` 只影响输出，二者无需交叉比较。

**建议明确写 `min-aura: 0`。** 为兼容源版字段默认值，不写时最低值为 -2,147,483,648，允许抽取至负灵气，并可能触发 Nature’s Aura 自身的环境效果。不写 `max-aura` 时默认上限为 2,147,483,647。

`aura-type` 必须是已注册的灵气类型，例如 `naturesaura:overworld`、`naturesaura:nether`、`naturesaura:end`；接口所在地的类型必须匹配。仅修改此字段不会把主世界灵气转成下界灵气。

同一区块按源版规则每项需求只选一个接口；多个接口不能将同一份灵气重复计数。相邻区块接口的半径 1 格区域如果重叠，预检也会合并它们对世界的影响，遵守各需求的最低／最高限制。选择器 `selector-tag`（与其他需求相同）可以限定接口所属的结构部件。

灵气支持机器／并行控制器批量倍率、配方数量修饰符及 `parallelizeUnaffected: true` 批量豁免。工厂中同种配方最多占一个线程，线程内仍可批量。`ignoreOutputCheck: true` 允许丢弃超过灵气上限的那部分产出；重要产出建议保持默认检查。

源版实现将查询原始灵气与实际扣产的 100,000 倍混用，而且某些分支会扣掉所有可用灵气。本移植版保留 JSON 字段并改为精确原始单位；迁移旧整合包时请按实际期望的灵气消耗调整 `amount`。例如希望消耗 100,000 原始灵气，就写 `amount: 100000`。JEI 显示灵气需求摘要，在文字区域悬停可查看完整类型、数量和限制。

## 社区 Astral Sorcery：星能与星座（实验联动）

此联动针对 [MrWizard94-Compile 的 Forge 1.20.1 社区移植](https://github.com/MrWizard94-Compile/Astral-Sorcery-1.20.1-Port)，本次固定提交为 `3cb52433420fbe951089dba1ccf63bd4598eceba`，构建版本 `1.20.1-1.0.0.0`。它是外部依赖，不随本模组内嵌；官方 1.21.1 NeoForge 版不能代替。其他同名社区构建没有自动获得兼容保证。

结构中使用：

- `modularmagic:blockstarlightproviderinput`：星能输入口；接受真实星光网络输入，也在露天且 Y > 40 时被动采集。
- `modularmagic:blockstarlightprovideroutput`：星能输出口；配方运行时作为真实网络星光源。
- `modularmagic:blockconstellationprovider`：星座检测口；查询依赖模组当前世界的活跃星座。

星能需求例子：

```json
{"type":"modularmachinery:starlight","io-type":"input","amount":3000}
```

与源版一致，这表示所需**星光强度**，不是消耗 3000 个 FE 或每 tick 扣除 3000 库存。输入强度上限 10000，逐 tick 衰减 5%；被动采集受高度、天空可见性、星空分布和昼夜影响；网络来光按源版乘 200 转成输入强度。机器运行期间继续检查强度，不足时暂停。`amount` 接受非负有限浮点数。

```json
{"type":"modularmachinery:starlight","io-type":"output","amount":4000}
```

输出按源版 `amount / 4000` 提供网络星光，每 tick 刷新，配方停止后短暂宽限到期便不再供光。它不要求露天，因为星光由机器配方产生。输出没有星光库存，加载存档不会恢复停机前的产量。不同运行配方对同一输出口的当 tick 供光相加。

星光强度沿用源版非消耗条件的批量规则，**不随机器或并行控制器批量倍率相乘**；同一配方中的物品、流体等仍按各自批量规则处理。星能数量修饰符仍会修改强度阈值或输出强度。支持 `selector-tag` 选择输入／输出口。

用 Astral 的连接工具依次右击星光源与目标，连接水晶、接口或传输节点；接口保存连接，重载后重新接入网络。持连接工具潜行右击接口可解除其连接。空手右击输入口可查看实际强度；空手右击输出口可查看供光值和星座，潜行空手右击输出口可切换星座。

星座需求例子：

```json
{"type":"modularmachinery:constellation","io-type":"input","constellation":"astralsorcery:discidia"}
```

也兼容 `discidia` 和源版 `astralsorcery.constellation.discidia` 拼写。必须为依赖中实际注册的星座；未知名字拒绝加载。该条件在开工时检查，不消耗星座，不支持输出；不把夜空肉眼可见或接口是否被屋顶覆盖作为额外要求。右击星座检测口可查看当前活跃星座，`selector-tag` 可限制检测口。

JEI 显示强度与星座摘要，文字区悬停可查看全部需求。未安装兼容依赖时方块 ID 与外观仍保留，右击提示缺少依赖，相关配方不会忽略条件而继续运行。

## Extra Utilities Rebirth：相邻虹彩运行检测

此适配针对 [AuramMods/ExtraUtilities 的发布标签 1.0.0](https://github.com/AuramMods/ExtraUtilities/releases/tag/1.0.0) 中 `ExtraUtilities.jar`，**JAR 内部版本为 1.7.3，mod ID 为 `extrautilitiesrebirth`**。安装时按内部版本辨认；需要支持其初始化方式的新版 Forge，本轮验证目标为 47.4.23。依赖单独安装，不内嵌在本模组中。

在结构中加入 `modularmagic:blockrainbowprovider`，并在其六个面任意一侧紧贴放置虹彩发电机的主体 `extrautilitiesrebirth:rainbow_generator`。发电机可以在结构之外，距离超过相邻一格、只有底座、或者发电机停止，都不满足条件。

```json
{"type":"modularmachinery:rainbow","io-type":"input"}
```

这是持续运行条件，没有 `amount`／`power`，不产生或消耗 FE，也不因批量倍率改变。适配读取该固定版本发电机自身的真实 `rgb_running` 状态：其 16 种发电机条件满足才成立。每个机器 tick 付款前先检查，条件消失时暂停；支持 `selector-tag`，右击检测口可查看结果。

**与 1.12.2 源版的区别：**源版查询 Extra Utilities 2 的同玩家频率网络；Rebirth 没有对应网络，本适配明确使用相邻检测。迁移旧整合包时需要调整发电机摆放。此功能不提供 GP 网络兼容，也不会将 FE 当成 GP。缺少依赖或内部版本不同则保留占位行为，不接受条件配方。

本次还确认 Rebirth 1.7.3 自身的 `forge:frosty_gen_tag`、`forge:pink_items` 物品标签含霜冰、细雪方块、盆栽、墙旗等没有物品形式的 ID，导致两个标签加载失败。项目附有可选修复示例 [`examples/datapacks/mmr-extrautilitiesrebirth-1.7.3-tags`](../examples/datapacks/mmr-extrautilitiesrebirth-1.7.3-tags/pack.mcmeta)：将整个目录放到目标存档的 `datapacks/` 下并重载。示例用 `replace: true` 保留原列表中的有效物品，移除无效引用；不会修改机械模组或外部 JAR。已有自定义发电机材料标签的整合包应在自己的数据包里合并修正，避免被示例覆盖。

## Flux Networks：能源仓通量连接

MMC 1.2.0 新增通量网络专用接口；普通 FE 库存与容量不变。节点贴输入仓、插头贴输出仓，详见[通量网络联动教程](FLUX_NETWORKS_ZH.md)。
