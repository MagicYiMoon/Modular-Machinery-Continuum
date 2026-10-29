# 环境检测器与配方条件

MMC 1.3.0 新增。此功能只依赖 Minecraft，不需要安装 gugu-utils。两端须使用包含本次改动的同一版本，新增方块后重启游戏。

## 放入结构

环境检测器 ID 为 `modularmachinery:blockenvironmentdetector`。将它加入结构 JSON，或在选区工具导出机器时包含它。没有匹配检测器的配方不会回退到控制器坐标。高度、群系和降雪取检测器位置，维度、世界天气、时间和月相取所在世界。

```json
{"x": 0, "y": 1, "z": 0, "elements": "modularmachinery:blockenvironmentdetector", "selector-tag": "sensor"}
```

支持生存合成：顶部阳光探测器，中间机械外壳及两侧红石，底部铁锭。检测器本身不消耗资源，无独立 GUI，也不每 tick 自行扫描；配方检查时读取环境。石镐或更高级镐可正常挖掘掉落。外观使用现有机械外壳与原版阳光探测器顶部贴图，可用资源包覆盖。

## JSON 条件

1.4.0 新增可视化入口：在 `/modularmachinery gui` 中选中可编辑配方，点击输入框的 `+` → “环境条件（输入）”，选择高度、时间、维度、群系／标签、月相或天气。维度与群系列表每行填写一个 ID，群系标签加 `#`；月相可多选，时间允许跨午夜范围。选中已有环境需求可重新编辑，保存仍需服务器确认。模板的追加需求也可使用同一面板，先返回模板再统一保存。此条件只支持输入，机器结构仍需包含相应环境检测器。

每项都使用 `"type": "modularmachinery:environment"`、`"io-type": "input"`。以下每行是一项完整需求，可按需放进配方 `requirements` 数组。

```json
{"type":"modularmachinery:environment","io-type":"input","condition":"altitude","min":64,"max":128,"selector-tag":"sensor"}
{"type":"modularmachinery:environment","io-type":"input","condition":"dimensions","values":["minecraft:overworld"]}
{"type":"modularmachinery:environment","io-type":"input","condition":"biomes","values":["minecraft:plains","#minecraft:is_forest"]}
{"type":"modularmachinery:environment","io-type":"input","condition":"time","min":23000,"max":1000}
{"type":"modularmachinery:environment","io-type":"input","condition":"moonphase","values":[0,4]}
{"type":"modularmachinery:environment","io-type":"input","condition":"weather","weather":"sunny"}
```

| 条件 | 写法与规则 |
| --- | --- |
| 高度 `altitude` | 检测器方块的 Y；整数 min ≤ max，含边界，支持负高度。 |
| 维度 `dimensions` | 使用维度注册 ID，不使用旧版数字维度 ID。values 内任意一项满足即可。 |
| 群系 `biomes` | 注册 ID 或 `#群系标签`，values 内为“或”。旧版 BiomeManager 气候枚举不移植，改用数据包群系标签。未知但格式正确的 ID／标签保留为未满足条件，不强制加载世界。 |
| 时间 `time` | 游戏 dayTime 对 24000 取模，min/max 为 0～23999，含边界。0=06:00，6000=12:00，12000=18:00，18000=00:00。min > max 表示跨零点，例如 23000～1000。min=max 只匹配该 tick。 |
| 月相 `moonphase` | 0～7 的整数列表，0 满月、4 新月；沿用 gugu 仅夜间满足的规则。夜间按 Minecraft `isDay()` 判断，受维度昼夜行为影响，不额外保证能看到月亮。 |
| 天气 `weather` | `sunny`：世界不降雨且不打雷；`raining`：世界降雨，包含雷雨，即使沙漠也可满足；`thundering`：世界雷暴；`snowing`：世界降水且检测器上方露天、当地群系当前为雪，无需实际堆积雪层。 |

多个需求之间为“且”。同一项需求只需任意一个匹配其 selector-tag 的检测器满足；不同需求可由不同检测器满足。希望都限定在同一个检测点时，为该检测器设置独有标签，并在每项需求中写相同 selector-tag。

条件不消耗资源，不随并行倍率放大，不受配方资源修饰符改变。开始前检查，运行时每个推进 tick 再检查；环境不符会在该 tick 扣能源／流体之前阻止推进。默认按现有失败策略暂停；若配方开启 `cancelIfPerTickFails` 则取消。开始时已经消耗的物品不会自动退回。已完成而等待输出空间的配方不再重复检查环境。

JEI 配方详情／悬停说明显示条件；控制器状态区显示缺少检测器或具体条件失败。当前没有为环境条件新增可视化编辑面板，使用 JSON 或 CraftTweaker 编写。

## CraftTweaker 14

构建链提供以下方法，`setTag` 只作用于最近追加的一项条件：

```zenscript
.addEnvironmentDimensions("minecraft:overworld")
.addEnvironmentBiomes("minecraft:plains", "#minecraft:is_overworld")
.addEnvironmentAltitude(64, 128)
.addEnvironmentTime(23000, 1000)
.addEnvironmentMoonPhases(0, 4)
.addEnvironmentWeather("sunny").setTag("sensor")
```

不要把全部示例条件机械地拼在一起，例如白天时间范围与夜间月相可能互相冲突。完整可安装示例见[环境实验台](../examples/environment/README.md)。

## 来源与验证

行为参考 gugu-utils：检测器坐标、逐 tick 条件、跨午夜时间段和夜间月相；用现代注册 ID／群系标签替代旧数字维度和气候枚举。实现使用 MMC 现有需求、网络快照与事务检查链，独立编写，没有引入其旧执行器或复制纹理。降雪使用检测器上方位置和降水高度，修正旧实现将实体方块位置作为积雪位置的问题。

测试证据及限制见[功能范围与测试说明](../TESTING.md)与 [功能范围与测试说明](../TESTING.md)。
