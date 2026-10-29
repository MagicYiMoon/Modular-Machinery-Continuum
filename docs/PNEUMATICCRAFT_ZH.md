# PneumaticCraft 压缩空气联动

此联动自 MMC 1.3.0 提供。目标为 Forge 1.20.1 的 **PneumaticCraft: Repressurized 6.0.23**，须另外安装其必需前置 Patchouli。开发运行开关为 `-PwithPneumaticIntegration`，不会把这两个模组内嵌进 MMC。客户端与服务器须使用相同版本的 MMC。

## 接入机器

将 `modularmachinery:blockpressureinputhatch`（压缩空气输入仓）或 `modularmachinery:blockpressureoutputhatch`（压缩空气输出仓）明确加入机器结构，可以通过选区工具导出。它们不会自动替换普通流体仓或 Mekanism 气体仓。

```json
{"x":1,"y":0,"z":0,"elements":"modularmachinery:blockpressureinputhatch","selector-tag":"air_in"}
```

两种仓室的基础容积都是 **16,000 mL**，沿用 gugu 附属功能的二级压力设计。压力不是储量：`压力 bar = 空气量 mL ÷ 有效容积 mL`，例如 32,000 mL 空气在本仓中为 2 bar。六面提供 PNC 原生空气处理器，可连接原生高级压力管。原生管网会平衡压力，输入／输出方向限定 MMC 配方读写，不是管网单向阀；需要控制流向时使用 PNC 管道模块。

空手右击打开仓室界面，查看空气量、有效容积、压力与安全升级状态，右侧为四个升级槽，下方为玩家背包；支持 Shift 点击移入／移出。压力条包括真空区间，悬停显示危险／临界压力。也可使用 PNC 原生压力表。持工具右击保留物品自身交互。输入仓断开管道后保留空气；输出仓在没有任何有效空气连接时向上漏气，沿用旧附属实现。管道连接变化会刷新原生连接缓存。

仓室按 PNC 二级处理器运行：**20 bar 为危险压力，25 bar 为临界压力**。超过危险压力可能触发原生超压行为。配方输出会预检到 25 bar 的剩余空间，但这不代表 20～25 bar 区间安全，外部管网仍可继续升压。原生压力、漏气和超压检查由 PNC 执行。可在仓室界面安装 PNC 的容积升级与安全升级；没有安全升级时仍须通过管网控制压力。

生存合成为顶部高级压力管，中间对应方向的普通流体仓，两侧红石，底部压缩铁锭。石镐或更高级镐可掉落仓室，拆除不保留内部空气，已安装升级会掉落。外观暂复用 MMC 现有面板与原版铜块顶部。未安装 PNC 时方块保留占位，无功能方块实体，不加载这两个合成。

## 容积与安全升级

四个槽位接受 PNC 原生升级物品；同种升级仅占一个槽。容积升级最多 **64**，安全升级最多 **1**；不接受速度等未实现效果的升级。

容积由 PNC 原生公式计算：无升级为 16,000 mL；有升级时为 `16,000 × 2 × √升级数`，取整数。1／4／64 个分别为 **32,000／64,000／256,000 mL**。增装容积升级保留空气量，压力相应下降；取出时原生处理器按比例减少空气，保持压力，避免拆升级后突然超压。模拟插入和查询槽位上限不会改变容积或空气量。

安全升级使用 PNC 原生泄压：超过 **20 bar** 时向上排气，并沿用原生停止泄压的回差和临界压力保护。它不提高压力等级，空气也可能因泄压损失。卸下后恢复原生无安全升级的超压规则；输出仓断开管网时的漏气不受安全升级开关影响。

升级和空气一同保存，界面按完整 32 位数值同步，包含升级后的大数和负压。升级通过 GUI 手动管理，不提供自动化升级物品接口；方块拆除时退还升级，不保留空气。

## JSON 配方

```json
{
  "machine":"your_machine",
  "registryName":"air_recipe",
  "recipeTime":100,
  "requirements":[
    {"type":"modularmachinery:compressed_air","io-type":"input","pressure":2.0,"air":1000,"selector-tag":"air_in"},
    {"type":"modularmachinery:compressed_air","io-type":"output","air":100}
  ]
}
```

输入必须写 `pressure`，表示开工前最低压力；`air` 可省略为 0，仅检查压力。输出必须写 `air`，表示产出的空气量，压力由仓内实际储量计算，不由配方直接设置。`air` 必须为 0～2147483647 的整数，`pressure` 为 -1～25 的有限数值。

`perTick` 缺省 false：输入开工扣一次，输出完工产一次。写 `"perTick":true` 或将类型改为 `modularmachinery:compressed_air_pertick`，则在每个工作 tick 检查／消耗／产出。单次最低压力只在开工时检查；若需运行期间持续保压，使用逐 tick 需求。旧需求名 `gugu-utils:compressed_air`／`gugu-utils:compressed_air_pertick` 也可识别，旧 `time` 字段不决定现代配方持续时间，统一使用 `recipeTime`；不保证整个旧 gugu 配置无需修改即可导入。

负压力表示真空条件，例如 `"pressure":-0.5,"air":0` 要求实际压力不大于 -0.5 bar。负压下正的耗气量表示继续抽走空气，最低到原生硬真空 -1 bar；到达硬真空后不能凭空再抽出空气。正压条件的耗气不会将仓室抽成负压。

普通／工厂控制器和批量倍率均支持。批量只放大空气量，不放大压力阈值；`parallelizeUnaffected:true` 可豁免需求批量放大。相同资源需求共享预留，多个输入不足或输出空间不足时不先支付同组输入。`ignoreOutputCheck` 沿用可丢弃输出规则。selector-tag 只匹配结构中对应方向的仓室；同一种配方最多占一个工厂线程的规则不变。

## CraftTweaker 14

```zenscript
import mods.modularmachinery.RecipeBuilder;

RecipeBuilder.newBuilder("my_air_recipe", "your_machine", 100)
    .addCompressedAirInput(2.0 as float, 1000).setTag("air_in")
    .addCompressedAirOutput(100)
    .addCompressedAirPerTickInput(1.0 as float, 5)
    .addCompressedAirPerTickOutput(2)
    .build();
```

四个方法分别对应单次输入／单次输出／逐 tick 输入／逐 tick 输出。输入方法第一个参数是 bar，第二个是 mL；输出方法只有空气量。脚本执行前必须存在目标机器，且安装 PNC。JEI 详情展示气量、逐 tick 标志和压力条件。2026-10-28 源码已增加可视化编辑，见下节；1.3.0 已包含。

空气、升级和控制器绑定随区块保存；异常 NBT 的真空下界按有效容积钳制，无升级时为 -16,000 mL。四升级槽、独立读数 GUI、温度条件与三类静态原生配方导入已实现，范围见[外部配方导入](EXTERNAL_RECIPE_ADAPTERS_ZH.md)。来源和测试证据见[功能范围与测试说明](../TESTING.md)与 [功能范围与测试说明](../TESTING.md)。


## 原生温度与持续温度条件

压力仓同时公开 PNC 原生热交换能力，输入和输出仓都能与相邻原生热交换器、热源或环境交换热量。热容量 100、热阻 1；空气压力和温度是独立状态。拆除／卸载会解除双向热连接，保存温度，旧存档默认 300 K。界面新增 Kelvin 读数。

```json
{"type":"modularmachinery:temperature","io-type":"input","min":400,"max":600,"selector-tag":"heat"}
```

`min`／`max` 至少写一项，非负整数，包含上下界；省略 min 为 0，省略 max 为 2147483647。按原生整数 Kelvin 读数判断，例如 500.9 K 对应 500 K。**不是摄氏度**，400 K 约为 127℃。温度不随批量放大，不直接扣除热量，但自然换热仍会改变仓温。开工前和每个工作 tick 都检查所选结构部件，任何一个符合范围即满足；降温时沿用已有暂停／取消配方策略。单次压力需求仍只在开工检查，两者不要混淆。

CraftTweaker 在 `RecipeBuilder` 链中调用 `.addTemperatureInput(400, 600).setTag("heat")`。JSON、脚本与快照均要求安装 PNC；源码编辑器用法见下节。炼油等导入策略见[外部配方教程](EXTERNAL_RECIPE_ADAPTERS_ZH.md)。


## 可视化编辑

1. 安装 PneumaticCraft 后，以管理权限执行 `/modularmachinery gui`，选择机器和普通 JSON 配方。
2. 点击输入或输出框的 `+`，选择“气动／温度”。空气可切换“单次”或“逐 tick”，分别按 mL 或 mL/t 填写，允许 0。输入另填 -1～25 bar 的压力阈值，负数表示真空；输出压力由仓内实际空气决定，不提供压力设定框。
3. 输入侧可切换“持续温度条件”，填写包含两端的最低／最高整数 Kelvin 温度。用 `0` 和 `2147483647` 表示不限制对应一端；温度只检查范围，不消耗、不随批量缩放。
4. 保存后等待服务器写入并重载。点击已有气动需求可修改同类参数；取消不写文件。编辑已有需求不允许直接改变输入／输出或空气／温度家族，需要时另建需求。

零空气输入适合只检查压力。需持续保压时选择“逐 tick”；单次输入只在开始检查。输入压力为正时要求实际压力 ≥ 阈值，负数时要求实际压力 ≤ 阈值。温度条件会在开工与运行中持续检查，且必须有匹配的气压仓和实际热源。

服务器未安装 PNC 时不显示入口，也拒绝相应写入。脚本和适配器来源仍只读。保存只改空气用量／压力／逐 tick 或温度边界，保留已有 `selector-tag`、批量豁免、触发信息及作者其他字段；旧 `_pertick` 类型切换为单次时会规范成基础类型，使开关实际生效。不设置概率。保存后可继续通过右键需求设置接口分组，通过已有批量入口修改空气的倍率策略。
