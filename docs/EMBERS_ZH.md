# Embers Rekindled 余烬联动

MMC 1.3.0 新增；两端必须使用相同版本和依赖组合。

## 安装与范围

客户端和服务器另装 **Embers Rekindled 1.20.1-1.4.7**，固定验证文件为 [Modrinth QNWjEwxw](https://modrinth.com/mod/embers/version/QNWjEwxw)。上游将其标为 **beta**。Embers 本体没有内嵌到 MMC；未安装时，仓室保留注册占位，但没有方块实体和条件合成，也不能执行余烬需求。

本功能参考 gugu-utils 的余烬输入仓，属于 MMCE 附属功能的现代适配。仅支持**一次性输入**，没有余烬输出仓、每 tick 消耗或 FE 换算。2026-10-28 源码已增加余烬可视化编辑面板，见下节；也可用 JSON 或 CraftTweaker 编写需求，JEI 配方详情会显示余烬用量。

## 八档仓室

方块 ID 为 `modularmachinery:blockemberinputhatch`，等级通过方块状态 `size` 区分。容量沿用旧附属模组，不跟随普通 FE 仓容量配置。

| size | 名称 | 余烬容量 | 接受不稳定余烬 |
| --- | --- | ---: | --- |
| tiny | 微型 | 400 | 否 |
| small | 小型 | 800 | 否 |
| medium | 中型 | 1,600 | 否 |
| reinforced | 强化 | 3,200 | 是 |
| big | 大型 | 6,400 | 是 |
| huge | 巨型 | 9,600 | 是 |
| ludicrous | 超巨型 | 12,800 | 是 |
| extreme | 极限 | 16,000 | 是 |

注意：这里使用 `medium` 和 `extreme`，不是 FE 仓的 `normal` 和 `ultimate`。空手右击仓室查看储量；物品提示也显示储量与容量。使用石镐或更高等级镐正常采集，会保留等级及已储存的余烬，再放置恢复；创造模式直接破坏仍遵循原版不掉落规则。

安装 Embers 时提供八个工作台配方：上下各一个 Embers 铜质单元 `embers:copper_cell`，左右各一个红石，中间是对应等级的 MMC 能源输入仓。`medium` 使用 normal 能源仓，`extreme` 使用 ultimate 能源仓，其余同名对应。每档独立制作，不消耗已充能的低档余烬仓升级。作为材料的 FE 仓只匹配配方规定的等级 NBT；建议使用新合成的空仓。

## 供能与结构

在余烬仓外侧附着 Embers 原生余烬接收器，再按 Embers 的方式连接发射器供能。比如接收器放在仓室正上方、朝向 UP 时，会向下方仓室传输；不是把发射器直接瞄准 MMC 仓室。仓室六面均暴露原生余烬能力，供能速度由外部供能方决定：固定版本的普通接收器每 2 tick 最多送入 10 余烬。更高容量本身不提高接收器速度，不稳定余烬还受所用接收器自身规则限制。

在机器 `parts` 中指定等级，并给余烬入口选择器：

```json
{
  "x": 0,
  "y": 1,
  "z": 0,
  "elements": "modularmachinery:blockemberinputhatch@0",
  "selector-tag": "ember_in"
}
```

坐标相对朝北控制器，整体旋转沿用普通结构规则。`@0`～`@7` 按上表顺序限定等级；省略 `@序号` 会接受该方块的所有等级。要允许部分等级，请按[结构参考](ADDING_MACHINES.md)写候选数组。

## JSON 配方

在配方的 `requirements` 数组中添加：

```json
{
  "type": "modularmachinery:ember",
  "io-type": "input",
  "ember": 12.25,
  "selector-tag": "ember_in"
}
```

开始加工时一次性扣除 12.25 余烬，运行中不继续扣除。数量支持有限非负小数，不能写字符串、负数或无限值。不支持 `io-type: output`、`perTick: true`；这些声明会被明确拒绝。旧需求名 `gugu-utils:ember` 作为类型别名接受，但这不代表旧版机器 JSON、物品 ID 和脚本可以原样运行。

同一需求可从多个匹配仓室合计扣除；同一配方的多条余烬需求共用预留，储量不足时不先扣一部分。同一机器批量加工两份通常消耗 24.5；加上 `"parallelizeUnaffected": true` 则此需求不随批量翻倍。选择器、输入资源数量修改器和普通／工厂执行共用现有规则。已开始的任务不会因为外部继续供能而重复扣款；后续取消仍遵循本模组已扣输入不自动退回的规则。

## CraftTweaker

另装 CraftTweaker 14，示例：

```zenscript
import mods.modularmachinery.RecipeBuilder;

RecipeBuilder.newBuilder("ember_lab_ct", "ember_lab", 100)
    .addItemInput(<item:minecraft:coal>)
    .addItemOutput(<item:minecraft:diamond>)
    .addEmberInput(12.25).setTag("ember_in")
    .build();
```

先定义 `ember_lab` 机器。`setTag` 作用于刚添加的需求；没有匹配标签的有效仓室时不能开工。JSON 与脚本配方二选一，避免重复注册。

完整小型结构见[余烬实验台示例](../examples/embers/README.md)。实际服务端测试覆盖原生接收器、普通／工厂批量、采集与重新放置；客户端材质、JEI 悬停及粒子画面尚未启动验证。实现证据见[功能范围与测试说明](../TESTING.md)和 [功能范围与测试说明](../TESTING.md)。


## 可视化编辑

管理员执行 `/modularmachinery gui`，选择机器和普通 JSON 配方，在输入框点击 `+` →“魔法资源”→“Embers 余烬”。填写有限非负用量，可用小数或科学计数法，例如 `12.125`、`7.25e1`。余烬只支持开工时一次性输入，因此没有逐 tick、输出或概率选项。`0` 表示不消耗余烬，不代表必须安装一个仓室的检测条件。

点击已有余烬需求可修改用量，保存前检查数值，等待服务器保存并重载后才更新界面。取消未提交编辑不写文件；调整 GUI 缩放保留小数草稿。保留已有 `gugu-utils:ember` 类型别名、接口分组、批量豁免、触发和作者其他字段，不把旧源文件改成客户端显示快照。

保存后可以右键需求设置接口分组，使用已有批量设置修改倍率策略。脚本／适配器生成配方仍只读；未安装 Embers 时不提供余烬选项，服务器也拒绝相关写入。带概率等歧义字段的高级定义需检查源文件。当前版本只验证编辑器画面，不扩大为仓室粒子或所有整合包组合已验证。见[功能范围与测试说明](../TESTING.md)和 [功能范围与测试说明](../TESTING.md)。
