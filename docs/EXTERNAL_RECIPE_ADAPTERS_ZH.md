# 外部配方导入（Forge 1.20.1）

MMC 1.3.0 新增。外部模组单独安装，不打包进 MMC。导入器读取服务端当前 RecipeManager，包括数据包已修改的配方；修改后执行 `/reload`。不等于直接运行外部机器，其升级、燃料、控制器和吞吐机制不会自动迁入。

## 已接入的类型

| 依赖及核对版本 | adapter | 转换规则 |
| --- | --- | --- |
| TConstruct 3.12.1.231 + Mantle 1.11.117（上游 beta） | `tconstruct:smeltery_melting` | 固定产物的普通熔炼；时间为温度 ÷ 10，至少 1 tick；不自动添加 FE／燃料 |
| 同上 | `tconstruct:smeltery_alloy` | 非催化剂流体合金，4 tick；不自动添加 FE |
| Thermal Expansion 11.0.1.29 + Foundation 11.0.6.70 + CoFH Core 11.0.2.56 | `thermal:insolator` | 种植机原料、水和基础产物／概率；20 FE/t，时间向上取整为原能耗 ÷ 20 |
| 同上 | `thermalexpansion:insolator` | 上一行的旧名称别名，二选一，勿重复导入 |
| NuclearCraft: Neoteric 1.2.36 | `nuclearcraft:alloy_smelter`、`chemical_reactor`、`fluid_infuser`、`melter` | 原生处理器基础耗时／功率 × 配方倍率，向上取整；数量和模组优先产物由原生配方提供 |
| PneumaticCraft 6.0.23 | `pneumaticcraft:pressure_chamber` | 静态压力室配方，保留堆叠数和压力条件 |
| 同上 | `pneumaticcraft:assembly` | 静态装配配方，保留输入数量和输出，不自动添加气压与耗气 |
| 同上 | `pneumaticcraft:refinery` | 静态炼油配方，保留流体数量和原生 Kelvin 温度区间 |

匠魂和 Thermal 的时间／FE 策略参照旧 MMCE 适配器；新版本底层配方不同，不承诺完全复刻旧整合包的产量。Thermal 不自动使用肥料或催化剂倍率；大于 100% 的基础产出先应用概率修改器，再拆为保底数量与额外概率。新版没有原版旧接口的树种分类，因此不注册 `insolator_tree`。NuclearCraft Neoteric 是现代对应实现，不冒用旧 NCO 类型名。

PNC 三类默认 100 tick、无隐含耗气／燃料；这是一项 MMC 转换策略，**不是原生机器实际运行时间**。压力室自动保留压力阈值，但耗气量应由作者在模板 `requirements` 里指定；装配若需最低气压也应显式补充。炼油温度检查结构里的 MMC 压力仓，需安装 PNC 并通过原生热交换加热；不直接查找周边任意 PNC 机器，不消耗一个虚构的“温度资源”。

## 模板示例

先使用工具／指令定义 `your_machine`，再把以下模板保存到 `config/modularmachinery/recipes/your_pressure.adapter.json`：

```json
{
  "machine": "your_machine",
  "adapter": "pneumaticcraft:pressure_chamber",
  "modifiers": [
    {"target":"modularmachinery:duration","io":"input","operation":1,"multiplier":2}
  ],
  "requirements": [
    {"type":"modularmachinery:compressed_air","io-type":"input","pressure":0,"air":1000}
  ]
}
```

时间修改器把 100 tick 改成 200 tick；原生配方材料和压力由适配器生成；模板再追加一次消耗 1,000 mL 空气。追加需求按所写值加入，不再次套用模板修改器。配方输入／输出仍走普通控制器、工厂、批量和选择器的既有执行规则。

`operation:0` 为加法、`1` 为乘法；物品／流体／能量与时间可使用对应目标，概率用 `affectChance:true`。输入物品标签保留为标签；流体候选按当前加载结果展开为确定配方，每个源配方最多 256 种组合。重载重建候选和稳定生成 ID，不把空原料当免费配方。

当前版本新外部导入器的 ID 按模板相对路径、模板配方名、所属机器、原配方 ID 与流体候选序号生成；同一模板重复重载，以及增删排在它前面的其他模板，不改变其生成 ID。移动／改名模板、修改所属机器或流体候选集合可能改变 ID。旧版已有的原版烹饪适配器命名规则保持原状。Java 扩展应调用带 `templateScope` 的重载以获得这个保证；不带作用域的直接调用仍按调用次数区分。

## 明确跳过的情况

- 匠魂工具耐久、材质、矿石倍率等动态熔炼，非消耗流体催化剂；源版 `temperatureRequired` 事件元数据未迁入，当前温度只参与基础时间策略，不自动成为匠魂熔炼门槛，可按机器规则另加持续温度条件。
- PNC 动态附魔／祛魔、依赖额外上下文的可变产物。热气加工机／冷却框等其他类型未纳入当前版本。
- 自定义／NBT 原料类型尚无对应转换器、产物无法确定、流体候选超过上限或数值不合法。
- IC2 与 TC6 旧版适配不在当前版本注册；不能用模组 ID 相同或新分支存在作为旧 API 可用的证明，保留待核对项。

日志 `[MMC][foreign-adapter]` 给出源配方数、生成数、跳过数，以及前 8 条原因。一个源配方有不支持的部分时整条跳过，避免漏扣原料；未安装目标依赖时不注册对应导入器。生成数可能大于源配方数，因为流体替代项会展开。没有为自定义机器自动生成原生模组配方，只有作者配置模板后才导入。

## 开发依赖与复现

执行 `python tools/prepare_recipe_integrations.py` 校验并准备固定官方 API。版本 ID、URL、SHA-512 和 Foundation 内嵌 core 的 SHA-256 固定在脚本中。`compileOnly` 不嵌入外部 JAR；`-PwithRecipeIntegrations` 用于服务端完整回归，也可用 `-PwithTinkersIntegration`、`-PwithThermalIntegration`、`-PwithNuclearIntegration` 分别加载。PNC 使用已有 `-PwithPneumaticIntegration`。

真实配方扫描、替代流体、保留 NBT、单次修改器、普通／工厂执行与温度专项证据见 [功能范围与测试说明](../TESTING.md)。可支持的静态配方不等于全部外部模组功能已移植。
