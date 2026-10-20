# Modular Machinery: Continuum：JSON 补充参考

> 2026-10-20 已对照加载器更新目录扫描、需求类型、重载及蓝图说明。本文保留部分早期实现说明与章节编号，提供 JSON 补充示例，**不是完整字段全集**。初次制作机器请先读[整合包作者教程](PACK_AUTHOR_GUIDE_ZH.md)，所有教程入口见[文档目录](README.md)。

---

## 0. 放哪两个目录

**从 §636/§641 起，加载器读的是**配置目录**，不再是 jar。jar 里的那两个 `default_*` 目录只是**首次启动时被解压出来的模板**。**

| 内容 | 你放文件的地方（**运行时真正被读的**） |
|---|---|
| **机器定义** | `config/modularmachinery/machinery/<名字>.json` |
| **变量表** | `config/modularmachinery/machinery/variables/<名字>.var.json` |
| **配方** | `config/modularmachinery/recipes/<名字>.json`（支持子目录） |

**⇒ 两个目录（`machinery/` 与 `recipes/`）在启动时由 `ModDataHolder.setup()` 自动创建，不用你手建。** ✓

首次创建配置主目录时，`ModDataHolder.copyDefaultMachinery()` 会复制 JAR 中的默认机器、配方和变量表，不覆盖已有文件；之后的启动不会补回已删除文件。模板数量不是作者需要遵守的限制。

**⇒ ⚠️ 解压只在**首次**（`config/modularmachinery/` 这个目录原本不存在的那一次）发生：之后删掉某个文件，它**不会**被补回来 —— 这正是"配置目录是你的，jar 只播种一次"的含义。** ✓

模板复制会扁平化目录，例如 `default_recipes/alloy_smelter/x.json` 变成 `recipes/x.json`。**这仅是首次模板复制方式**；运行时的机器／配方加载器递归扫描子目录，作者可以自行分类。子目录不提供独立注册名，仍需避免重名。

**⇒ 在整合包里发自定义命名空间的那种做法（`assets/<你的命名空间>/default_machinery/`）**不适用于本移植**：`ModDataHolder` 只解压 `modularmachinery` 命名空间下的模板，别的命名空间需要自己把文件写进 `config/modularmachinery/`。** ✓

---

## 1. 机器 JSON 的常用键

以下列出基础键；`prefix`、`hide-components-when-formed` 等扩展字段及脚本设置不构成完整列表。最大并行度和默认可并行状态还会受 COMMON 配置影响，见作者教程的“全局加工默认值”。

| 键 | 类型 | 必需 | 作用 |
|---|---|---|---|
| **`registryname`** | 字符串 | ****是**** | **机器的注册名。配方靠 `"machine"` 指它** |
| `localizedname` | 字符串 | 否 | 界面显示名（也可走语言文件） |
| **`parts`** | 数组 | 否 | ****结构本身**** —— 见 §2 |
| `requires-blueprint` | 布尔 | 否 | 要求通过蓝图或绑定命令明确指定机器，不参与未绑定控制器的自动识别；无需先投影 |
| **`parallelism`** | 整数 | 否 | ****机器自带并行度（默认 0）**** —— 见 §5 |
| **`max-parallelism`** | 整数 | 否 | ****并行度上限（默认 2048）**** |
| `parallelizable` | 布尔 | 否 | **默认 `true`；设 `false` 则该机器不并行** |
| `failure-action` | 字符串 | 否 | 合成失败时做什么（见 `RecipeFailureActions`） |
| `color` | 字符串 | 否 | 控制器/结构的着色 |
| `modifiers` | 数组 | 否 | **替换方块**（例如通风口）：某个位置换成别的块时给配方加成 —— 见 §4 |
| `dynamic-patterns` | 数组 | 否 | **可重复段**（例如流水线任意长度）—— 见 §6 |
| **`smart-interface-types`** | 数组 | 否 | ****智能接口能发布哪些数值****：声明一个类型后，结构里的智能接口才会拿到它，配方才能按那个数字挑配方 —— 见 §8.6 |

---

## 2. `parts` —— 结构是怎么声明的

每个 part 可以声明一个位置，或用坐标数组声明一组位置：

```json
{
    "x": 1, "y": 0, "z": 1,
    "elements": "modularmachinery:blockinputbus"
}
```

从 2026-10-10 的坐标数组补齐版本开始，`x`、`y`、`z` 支持整数或非空整数数组，省略的轴默认为 0。例如：

```json
{"x":[-1,1],"y":[0,1],"z":2,"elements":"minecraft:stone"}
```

这会展开为四个位置（两个 X × 两个 Y × 一个 Z），不是把数组按下标配对，也不是区间。数组内重复坐标只算一次；不同 part 声明同一位置时，后声明的部件覆盖前者。`dynamic-patterns` 的 `parts` / `parts-end` 和单方块 `modifiers` 也支持此写法；修饰替代条件不作用于控制器原点。动态段的起始/步进偏移仍要求标量。

坐标必须是 32 位整数，空数组、字符串、布尔值、小数或越界值会使机器定义被拒绝并写入加载诊断。单次展开及每个静态/动态模板最多 65,536 个不同位置。坐标数组按各轴候选值展开；请遵守上述展开数量限制。

### ⚠️ 三条必须知道的事

**① 坐标是**相对控制器**的，而控制器自己是 `(0,0,0)`。** ✓

**② 坐标**随控制器的朝向旋转** —— 玩家把控制器朝北放和朝东放，`x=1` 指向的世界方向不同。** **⇒ 所以你按「控制器正面」来想，而不是按绝对坐标。** ✓

**③ `elements` 可以是**字符串**，也可以是**数组** —— 而数组的意思是**任选其一**：**

```json
"elements": [ "modularmachinery:blockcasing@4", "modularmachinery:blockparallelcontroller" ]
```

**⇒ 那表示**这个位置放外壳或放并行控制器都算成形**。** ✓ **⇒ 数组会被压成逗号分隔串再由 `MachineVariables#resolve` 展开成候选 —— 那是移植自己的注释写明的（`MachineLoader` L384-386）。** ✓

### `@N` 是方块状态变体

**⇒ `modularmachinery:blockcasing@4` 里的 `@4` 是**变体序号**。** **⇒ 外壳有 7 种（`plain` / `vent` / `firebox` / `gearbox` / `reinforced` / `circuitry` …），总线与 hatch 各有 7-8 档（`tiny` … `ludicrous`）。** ✓ **⇒ 而**它们都显示各自的名字**（§629 修过这件事 —— 之前在世界上只显示基础名）。** ✓

### 一个最小的完整机器

```json
{
    "registryname": "my_machine",
    "localizedname": "我的机器",
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

**⇒ 那是最小的**可用**结构：一个控制器 + 一圈外壳 + 输入总线 + 输出总线 + 能量仓。** ✓

**⇒ 而**外壳的数量不限** —— 示例里的合金炉声明了 26 个位置，而那些位置**必须逐个列出**（没有「自动填满」）。** ✓

---

## 3. 配方 JSON 的常用键

| 键 | 类型 | 必需 | 作用 |
|---|---|---|---|
| **`machine`** | 字符串 | ****是**** | **机器的 `registryname`** |
| **`registryName`** | 字符串 | ****是**** | **这条配方自己的注册名**（注意这里是大写 `N` —— 与机器的 `registryname` 不同，那是移植与上游一致的写法） |
| **`recipeTime`** | 整数 | ****是**** | ****持续多少 tick**（20 tick = 1 秒）** |
| **`requirements`** | 数组 | ****是**** | **需求列表 —— 见 §3.1** |
| `priority` | 整数 | 否 | 多条配方同时满足时谁先跑 |
| `cancelIfPerTickFails` | 布尔 | 否 | **每 tick 检查失败时**放弃**这条配方（默认 `false`，即**停顿等待**）** |

### 3.1 四类基础需求示例

下面介绍物品、流体、FE、智能接口四类基础需求，**不是当前全部类型**。另有耐久、候选／随机物品组、催化剂、持续流体和可选资源需求，完整概览见 §3.2。类型 ID 均加 `modularmachinery:` 前缀。

#### 物品

```json
{ "type": "modularmachinery:item", "io-type": "input",  "item": "minecraft:coal",    "amount": 32 }
{ "type": "modularmachinery:item", "io-type": "output", "item": "minecraft:diamond", "amount": 1 }
{ "type": "modularmachinery:item", "io-type": "output", "item": "minecraft:diamond", "amount": 1, "chance": 0.4 }
```

**⇒ `chance` 只在**输出**上有意义（概率产出），而**没写就是 1.0（必出）**。** ✓

**⚠️ 一台机器写**多条**输出时，房间是**合起来算的**（这是本移植与上游 1.12.2 的一处**已记录的有意偏离**，PLAN 704）：**

- **必出的输出（没写 `chance`，或 `chance` ≥ 1）被**加总**之后一次性问「这条配方的全部产物放得下吗」。放不下 → **配方卡在 100% 不完成**，机器上显示 `craftcheck.failure.item.output.space`（流体是 `...fluid.output.space`），**一条都不放**；腾出格子后它自己就完成了。** ✓
- 必定物品／流体输出默认会合并检查空间，防止多条输出重复计算同一空位。显式关闭输出检查会改变保护行为；也不能把预检理解为任意外部模组同时改仓时都绝对不会丢产物。
- 带概率的物品输出不提前预留空间，掷中后尝试插入；空间不足的剩余部分可能无法产出，并写入日志。设计重要产物时应保留足够空间，不能依赖概率输出保证不丢失。
- **⇒ 对你的配方意味着什么：** 一条产出很多种东西的配方（随模组发货的**离心机 `iron_centrifuge_centrifuge_grass` 就是 9 种产物**）需要**足够多的空槽**才会完成 —— 6 格输出总线 + 9 种必出产物 = 永远卡住；把它拆成两条配方，或者给机器加一个更大的输出总线。**✓

#### 流体

```json
{ "type": "modularmachinery:fluid", "io-type": "input",  "fluid": "minecraft:water", "amount": 1000 }
{ "type": "modularmachinery:fluid", "io-type": "output", "fluid": "minecraft:lava",  "amount": 250, "chance": 0.5 }
```

**⇒ ⚠️ 而概率流体输出曾经**完全不生效**（§578 修好的）—— 那是 2.3.2 上游一个「记录为已修而实际没修」的缺陷，现在掷骰了。** ✓

#### 能量

```json
{ "type": "modularmachinery:energy", "io-type": "input", "energyPerTick": 100 }
```

**⇒ `energyPerTick` 是**每 tick**，所以一条 `recipeTime: 200`、`energyPerTick: 100` 的配方**总共需要 20000 FE**，而**每 tick 只要 100 在仓里**。** ✓ **⇒ 而**并行度 N 会让它每 tick 抽 `100 × N`**（§611.3 修好的平衡漏洞 —— 之前只抽一份）。** ✓

#### 智能接口的数值（用数字挑配方）

```json
{ "type": "modularmachinery:interface_number_input", "io-type": "input",
  "interface": "mode", "min": 2 }
{ "type": "modularmachinery:interface_number_input", "io-type": "input",
  "interface": "mode", "min": 0.5, "max": 1.0 }
```

**⇒ `interface` 必须是**机器在 `smart-interface-types` 里声明过的类型名**（见 §8.6），**`min`/`max` 两端都含**，而**不写 `max` 就等于 `max = min`**（单个数字）。** ✓ **⇒ `io-type` 只能是 `input`（读一个发布出来的值不产出任何东西），写 `output` 会被**拒绝加载并报出来**。** ✓

**⇒ ⚠️ 它是**这台机器上一个数字决定跑哪条配方**的唯一手段 —— 完整的用法与两个可直接抄的例子在 §8.6。** ✓

---

### 3.2 当前需求与扩展字段概览

| 类型 ID 后缀（统一加 `modularmachinery:`） | 用途 | 可选依赖 |
| --- | --- | --- |
| `item` | 普通物品；`item: "any:fuel"` 为熔炉燃烧时间需求 | 无 |
| `item_durability` | 耐久输入别名，必须提供正整数 `consumeDurability`；也可直接用 `item` 声明 | 无 |
| `ingredient_array_input` | 候选物品输入／随机物品输出，候选写在 `items` 数组 | 无 |
| `catalyst` | 可选催化剂与修饰效果；其 `chance` 是消耗概率 | 无 |
| `fluid`、`fluid_pertick` | 一次性／每 tick 流体 | 无 |
| `energy` | FE/t 输入／输出 | 无 |
| `interface_number_input` | 智能接口非消耗数值条件 | 无 |
| `gas`、`gas_pertick` | 一次性／每 tick 气体 | Mekanism 10.4 |
| `mana` | 魔力输入／输出 | Botania |
| `lifeessence`、`will` | LP／区块恶魔意志 | Blood Magic |
| `aura` | 世界灵气输入／输出 | Nature’s Aura |
| `starlight`、`constellation` | 星能网络与星座条件 | 固定社区 Astral Sorcery 构建 |
| `rainbow` | 相邻虹彩发电机运行条件 | Extra Utilities Rebirth 内部版本 1.7.3 |

没有单独的 `modularmachinery:fuel` 类型，燃料通过 `item` 的 `any:fuel` 声明。可选类型只在对应支持依赖存在时注册；源质 `aspect`、GP `grid`、Impetus `impetus` 仍未实现。具体字段、批量与消耗规则见[作者教程](PACK_AUTHOR_GUIDE_ZH.md)；不要把星能强度或星座检测当作可扣库存。

常用配方级扩展有 `displayName`（显示名）、`tooltips`（附加说明）、`parallelized`（是否批量）、`loadJEI`（是否在 JEI 显示）、`maxThreads`、`threadName`（工厂限制）。显示名不会修改 `registryName`；机器名称与配方名称是不同设置。

需求级可用 `selector-tag` 路由到结构分组、`nbt`／`nbt-display` 指定物品或流体数据、`triggerTime`／`triggerRepeatable` 控制普通需求触发、`parallelizeUnaffected` 控制支持需求的批量豁免。每 tick 需求、非消耗条件和催化剂有独立语义，详见作者教程。输出默认检查空间，显式 `ignoreOutputCheck` 会改变此保护。

## 4. `modifiers` —— 替换方块给加成

**⇒ 那是「某个位置换成别的块时，配方得到修饰」的机制。合金炉用它做通风口：**

```json
"modifiers": [
    {
        "elements": "modularmachinery:blockcasing@1",
        "x": 0, "y": 1, "z": 0,
        "description": "把控制器上方的块换成通风口\n物品产出翻倍！",
        "modifier": { "io": "output", "type": "item", "multiplier": 2.0 }
    }
]
```

**⇒ ⚠️ 而这里有一个坑：`modifiers` 里的位置**必须是 `parts` 里已声明的位置之一** —— 它替换的是那个位置的**可接受集合**，而不是新增一个位置。** ✓

---

## 5. 并行度与批量倍率

```
"parallelism": 8           ⇒ 机器自带 8
"max-parallelism": 64      ⇒ 上限 64（默认 2048）
"parallelizable": false    ⇒ 这台机器完全不并行
```

**⇒ 而有效并行度是 `机器自带 + Σ(并行控制器)`，夹到上限，再**逐个需求收窄**（取最小值）。** ✓

**⇒ ⚠️ 而**需求会拒绝供不起的份数**：**一条需要 32 煤的配方在只有 64 煤时并行度最多 2**，而**能量仓里只有一份的量时它会归约到 1**。** ✓ **⇒ 那正是**设计行为**（上游也是这个形状），而**它解释了「我设了 8 却只跑 1 份」**。** ✓

**⇒ 而**每个并行控制器可以在机器的 parts 里声明一个位置**（用 §2 那个数组写法），或者整合包让玩家放在玩家想放的位置 —— **而后者**不成立**，因为结构匹配会拒绝。** ✓

---

## 6. `dynamic-patterns` —— 可重复段（进阶）

**⇒ 用于「任意长度」的结构（示例里 `assembly_line` 用它）。键（从 `MachineLoader` L441-510 读出）：**

| 键 | 作用 |
|---|---|
| `faces` | 数组 —— 这一段往哪些方向重复 |
| `minSize` / `maxSize` | 最少/最多重复几次 |
| `parts-end` | 尾段（重复到哪儿为止的标记块） |
| `structure-size-offset-start` / `structure-size-offset` | 尺寸偏移校正 |

**⇒ 而**它比静态 `parts` 复杂得多**，所以**先用静态结构把机器做出来，需要任意长度时再加它**。** ✓

---

## 7. ⚠️ 加完之后必须做的三件事

**① 执行 `/reload`。** JSON 和脚本由服务器重新加载并同步给客户端，无需每次退出世界。资源包中的模型、动画、纹理还需要客户端 `F3+T`；已成型机器会重新绑定组件。若加载失败，先查看日志中的文件名和原因。

**② 检查运行结果。** 普通作者可用 `/modularmachinery list`、`/modularmachinery recipes <机器名>`、`/modularmachinery form` 与 `/modularmachinery check` 确认加载和结构。源码工作区的自动回归使用隔离运行目录，见 [VERIFY](../VERIFY.md)；普通作者无需运行 Gradle，也不应对真实存档启动自测。

**③ 文件放对地方就够了 —— 名册已经没有了（§641 step 3 改掉了）**

**⇒ 这一条在本轮之前是"把名字加进名册"，而那是本移植与上游的一处**真实差异**：**

```
上游 (MachineLoader.java L58-L65):  **扫目录** —— 而 ModDataHolder.java:72 先把机器文件从 jar **解压到目录**
移植 (到 §641 之前):                **硬编码名册** MACHINE_FILES / RECIPE_FILES
                                    ⇒ 不在名册里的文件被**静默忽略**：没有错误、没有日志
§641 step 3 之后（现在）:            **两者都换成了上游那种目录扫描**
                                    MachineLoader.discoverDirectory  /  RecipeLoader.discoverDirectory
```

**⇒ 所以现在**往 `config/modularmachinery/machinery/` 丢一个 JSON 就多一台机器，往 `recipes/` 丢一个 JSON 就多一个配方，**不用改 mod 源码** —— 与上游一致。** ✓

**⇒ ⚠️ 而**文件名**仍然影响两件事，别搞错：** ✓

| | 谁的名字 | 说明 |
|---|---|---|
| **机器** | 文件名 = 机器的**资源 id**（`new ResourceLocation(MODID, 文件名)`），而**匹配靠 JSON 里的 `registryname`** | 文件名与 `registryname` 不同也能用（例：`power_transformer.json` 的 `registryname` 是 `transformer`） |
| **配方** | **靠 JSON 里的 `registryName`**，与文件名无关 | 只有**没有** `registryName` 的适配器模板（`*.adapter.json`）才回落到文件名 |

**⇒ 自检里有一项专门盯这件事：「every machine and recipe file in the config directory is read, none skipped」—— 磁盘上有文件而加载器没读，它会**失败并报出文件名**。** ✓

---

## 8. 一个可以直接抄的完整例子

把这两段存成两个文件（路径见 §0），在游戏内执行 `/reload` 后即可绑定、搭建并运行这台钻石机；不需要修改源码或重新构建模组。

**`config/modularmachinery/machinery/my_machine.json`**

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

**`config/modularmachinery/recipes/my_machine_diamond.json`**

```json
{
    "machine": "my_machine",
    "registryName": "my_machine_diamond",
    "recipeTime": 200,
    "requirements": [
        { "type": "modularmachinery:energy", "io-type": "input",  "energyPerTick": 100 },
        { "type": "modularmachinery:item",   "io-type": "input",  "item": "minecraft:coal", "amount": 32 },
        { "type": "modularmachinery:item",   "io-type": "input",  "item": "minecraft:tnt",  "amount": 8 },
        { "type": "modularmachinery:item",   "io-type": "output", "item": "minecraft:diamond", "amount": 1 }
    ]
}
```

**⇒ 这台机器: 控制器 + 环绕外壳 + 输入总线在 (1,0,1) + 输出总线在 (−1,0,1) + 能量仓在 (0,0,2)。** ✓
**⇒ 把 64 煤 + 16 TNT 放进输入总线、接上持续 200 FE/tick 的电，跑 200 tick（10 秒）⇒ 出 **2 个钻石**（因为 `"parallelism": 2`）。** ✓

---

## 8.5 ✅ 结构部件的 `nbt` 与 `preview-nbt`

**⇒ 建造工具照上游的样子，会把方块实体的 NBT 写进生成的 JSON（每个 part 的 `"nbt"`）。**

```
✅ 现在 `nbt` 会作为方块实体 NBT 子树匹配：方块状态正确、存在方块实体且定义的字段满足时，该部件才算匹配。
`preview-nbt` 用于投影数据和自动构造放置后的方块实体初始化。
```

**⇒ 这两个键仍保留原始 JSON，理由有两条：**

**① 保留上游字段形状。** 迁移时仍需更新方块 ID、状态和方块实体 NBT；不同 Minecraft／外部模组版本的 NBT 不保证可以直接互换。
**② 服务器定义快照会保留这些字段** —— 客户端预览和服务器匹配使用同一份定义 ✓

**⇒ `nbt` 必须是 JSON object；非法类型会拒绝该机器定义。`nbt` 是匹配约束，`preview-nbt` 是具体预览/构造数据。** ✓

---

## 8.6 ✅ 用智能接口的数字，让一台机器跑不同的配方

**⇒ 这就是「智能接口」（`modularmachinery:blocksmartinterface`）的用途：**机器定义里声明一个「数值类型」，结构里放智能接口方块，玩家在它的界面里输入一个数字，而**配方用 `interface_number_input` 需求按那个数字决定自己是否成立**。** ✓

**⇒ 于是「同一台机器、同一套结构」可以有**多条配方**，而**同一时刻只有一条成立** —— 例如输入 1 跑压缩机配方，输入 2 跑打粉机配方。** ✓

### 键名（都是从 `MachineLoader.readSmartInterfaceTypes` 与 `InterfaceNumberRequirementType` 读出来的，不是猜的）

**机器侧 `smart-interface-types` 是**一个数组，每个元素一个类型**：**

| 键 | 类型 | 必需 | 作用 |
|---|---|---|---|
| **`type`** | 字符串 | ****是**** | ****类型的名字**** —— **配方的 `interface` 指的就是它**；**缺了它这一条会被报告并跳过** |
| **`default-value`** | 数字 | 否 | ****成形时发布的初始值****（默认 0）—— 玩家没输入之前机器用的就是它 |
| `priority` | 整数 | 否 | 机器声明**多个**类型时谁先被分发（**大的优先**） |
| `header-info` | 字符串 | 否 | 界面上值上面那行的**语言键** |
| `value-info` | 字符串 | 否 | 值的格式（`String.format`，例如 `"%.2f"`；**留空就显示 mod 自带的 `Current Value: <数字>`**） |
| `footer-info` | 字符串 | 否 | 值下面那行的**语言键** |
| `not-equal-message` | 字符串 | 否 | **数字不匹配时机器给出的那句话**（不写则用 mod 自带的提示） |
| `jei-tooltip` / `jei-tooltip-args` | 字符串 / 整数 | 否 | 配方面板上的额外说明行及其参数个数 |

**配方侧的第四个需求类型：**

```json
{ "type": "modularmachinery:interface_number_input", "io-type": "input",
  "interface": "<上面 type 的字符串>", "min": 1, "max": 2 }
```

**⇒ `min` 必需，`max` 可省（省了就等于 `min`）；**范围两端都含**。** ✓

### 一个可以直接抄的例子：一个数字在两条配方之间切换

**`config/modularmachinery/machinery/my_switch.json`**

```json
{
    "registryname": "my_switch",
    "localizedname": "模式切换机",
    "smart-interface-types": [
        { "type": "mode", "default-value": 1, "priority": 100,
          "value-info": "%.0f",
          "not-equal-message": "模式不对：这台机器现在不跑这条配方" }
    ],
    "parts": [
        { "x": -1, "y": 0, "z": -1, "elements": "modularmachinery:blockcasing" },
        { "x":  0, "y": 0, "z": -1, "elements": "modularmachinery:blockcasing" },
        { "x":  1, "y": 0, "z": -1, "elements": "modularmachinery:blockcasing" },
        { "x": -1, "y": 0, "z":  0, "elements": "modularmachinery:blockcasing" },
        { "x":  1, "y": 0, "z":  0, "elements": "modularmachinery:blockcasing" },
        { "x": -1, "y": 0, "z":  1, "elements": "modularmachinery:blockcasing" },
        { "x":  0, "y": 0, "z":  1, "elements": "modularmachinery:blockcasing" },
        { "x":  1, "y": 0, "z":  1, "elements": "modularmachinery:blockcasing" },
        { "x":  0, "y": 1, "z": -1, "elements": "modularmachinery:blockinputbus" },
        { "x":  0, "y": 1, "z":  1, "elements": "modularmachinery:blockoutputbus" },
        { "x": -1, "y": 1, "z":  0, "elements": "modularmachinery:blocksmartinterface" }
    ]
}
```

**`config/modularmachinery/recipes/my_switch_1.json`（数字 1 ⇒ 这条成立）**

```json
{
    "machine": "my_switch",
    "registryName": "my_switch_one",
    "recipeTime": 5,
    "requirements": [
        { "type": "modularmachinery:item", "io-type": "input",  "item": "minecraft:coal",    "amount": 1 },
        { "type": "modularmachinery:item", "io-type": "output", "item": "minecraft:diamond", "amount": 1 },
        { "type": "modularmachinery:interface_number_input", "io-type": "input",
          "interface": "mode", "min": 1 }
    ]
}
```

**`config/modularmachinery/recipes/my_switch_2.json`（数字 2 ⇒ 这条成立）**

```json
{
    "machine": "my_switch",
    "registryName": "my_switch_two",
    "recipeTime": 5,
    "requirements": [
        { "type": "modularmachinery:item", "io-type": "input",  "item": "minecraft:coal",    "amount": 1 },
        { "type": "modularmachinery:item", "io-type": "output", "item": "minecraft:emerald", "amount": 1 },
        { "type": "modularmachinery:interface_number_input", "io-type": "input",
          "interface": "mode", "min": 2 }
    ]
}
```

**⇒ 用法：造出这台结构（智能接口在控制器左上方 `(-1,1,0)`）。** ✓ **⇒ 成形时 `mode` 立刻发布 `default-value`（这里是 1），于是**只有 `my_switch_one` 成立**，输入总线里放煤就出钻石。** ✓ **⇒ 右键智能接口，在输入框里打 `2` 回车 —— 界面会把数字改成 2，而**机器立刻改跑 `my_switch_two`**（出绿宝石）；再打 `1` 回车就切回来。** ✓

### ⚠️ 四个会被静默坑到的点

| # | 坑 | 症状 |
|---|---|---|
| **1** | **机器没声明 `smart-interface-types`（或 `interface` 写了一个没声明的名字）** | **接口界面上写「Not Found Bound Machine」，输入框回车**什么也不会发生**（那条需求永远找不到值 ⇒ 配方永远不成立）** |
| **2** | **结构里放了智能接口，机器却没声明任何类型** | **同上 —— 声明是唯一的**生产者**：没有声明，成形时**不会发布任何值**** |
| **3** | **`default-value` 与任何一条配方的范围都不重叠** | 机器**一条都不跑**并显示你写的 `not-equal-message` —— 这是**设计行为**，不是故障 |
| **4** | **接口比声明的类型多** | 多出来的接口**共用优先级最高的那个类型**（上游行为，已保留）：它们各自保留自己的数字，而**需求读的是结构里第一个发布该类型的接口** —— 所以**一台机器只用一个数字时，放一个接口就够**，多放只会让你分不清自己改的是哪一个 |

**⇒ 数值的**唯一写入路径**是智能接口界面里的输入框 + 回车；而**负号打不进去**（上游的过滤只允许数字、`.` 和 `E`），所以负数只能来自 `default-value`。** ✓

### ✅ 已经随模组发货的例子：`iron_centrifuge`（强化铁离心机）—— 就是你已经搭好的那一台

**⇒ §8.6 上面那个 `my_switch` 只是**写法样本**；而**真正跟着模组发货、可以直接上手试**的，是这台机器。** ✓ **它就是你已经成形的那台——所以不需要再搭任何新结构。** ✓

**⇒ 机器文件（`config/modularmachinery/machinery/iron_centrifuge.json`）现在有两处改动：**

**① 顶层多了一段**声明**（`smart-interface-types` 是**数组**）：**

```json
    "smart-interface-types": [
        {
            "type": "mode",
            "default-value": 1,
            "value-info": "%.0f",
            "not-equal-message": "Wrong mode: the number in this machine's smart interface is not the one this recipe needs / 模式不符：机器智能接口上的数字不是这条配方要的"
        }
    ]
```

**② 地板层**正对控制器下方那一格**（相对控制器 `(0,-1,0)`）的 `elements` **从变量 `casings_all` 换成了一个数组**：**

```json
            "elements": [
                "modularmachinery:blockcasing",
                "modularmachinery:blockinputbus",
                "modularmachinery:blockenergyinputhatch",
                "modularmachinery:blockfluidinputhatch",
                "modularmachinery:blockoutputbus",
                "modularmachinery:blockenergyoutputhatch",
                "modularmachinery:blockfluidoutputhatch",
                "modularmachinery:blocksmartinterface"
            ]
```

**⚠️ 为什么是 8 个而不是 2 个 —— 这是**故意的**，理由有两条，都是读代码读出来的：**
- **`casings_all` 不是「外壳」一个块，而是**7 个块的集合**（`default_variables/casings.var.json`：`blockcasing` + 输入/输出总线 + 能量/流体接口）。**你原来在那一格放的可能是总线或接口**；若只写 `["blockcasing","blocksmartinterface"]`，你那台**现在就会散架**。
- **而且变量不能**套进**数组**：`MachineVariables.resolve` 先在**整串**里查变量名（`"casings_all,blocksmartinterface"` 查不到），再**按逗号切分**，于是 `"casings_all"` 会变成一个**谁也解析不出的字面块名**（被记进未解析清单，而不是展开）✓
**⇒ 所以这里把 7 个块**逐个列全**再补上智能接口：**旧机器的成形条件**原封不动**，只是多了一个可选块。** ✓

**⇒ 配方侧（`config/modularmachinery/recipes/`）：6 条配方按接口数字分成两组**，每条都在 `requirements` 末尾多了这一段（`min`/`max` **两端都含**）：**

| 组 | 配方 `registryName` | 加的内容 |
|---|---|---|
| **数字 1** | `iron_centrifuge_centrifuge_blaze_powder` · `..._grass` · `..._magma_cream` · `..._wool` | `{ "type": "modularmachinery:interface_number_input", "io-type": "input", "interface": "mode", "min": 1, "max": 1 }` |
| **数字 2** | `iron_centrifuge_wash_glowstone` · `..._wash_redstone` | 同上，`"min": 2, "max": 2` |

**⇒ 于是「1 = 4 条离心配方，2 = 2 条洗涤配方」，而 `default-value` 是 1 —— **机器一成形就在跑数字 1 那一组**。** ✓

### 怎么用（三步，都不用重启游戏）

1. **把智能接口放进那一格**：**控制器正下方那一格**（相对控制器 `(0,-1,0)`）。它在地板层，四面被中间层的铁块/铁栅栏围着，但**外侧那一面**（朝向控制器正前方 `-z` 的那面）是露在外面的 —— **站在机器前面贴着地板那一排右键就能打开它**；从下面掏也一样。
2. **右键它、在输入框里打 `1` 或 `2` 回车** —— 打 `1` 跑 4 条离心配方（羊毛→线、岩浆膏、烈焰粉、草），打 `2` 跑 2 条洗涤配方（红石粉→火药、萤石粉→火药）。**打错数字时机器显示的就是你写的那句 `not-equal-message`**（后面还会自动补上 `(interface 'mode' = <实际值>, expected <范围>)`）。**✓
3. **想确认自己改对了：对着控制器站着执行 `/modularmachinery recipes iron_centrifuge`** —— 它会逐条列出 `canStart=` / `canFinish=`，并把挡住的那条需求原样打出来（例如数字 2 时那 4 条离心配方会显示 `blocked start: interface 'mode' in [1.0, 1.0] (input)`）。**✓

### ⚠️ 改这些文件时最容易白忙一场的一点：**已抽出的文件永远不会被覆盖**

**⇒ 机器/配方文件是**首次启动时从模组里抽到 `config/modularmachinery/` 的**；而抽取的规则是**目标已存在就跳过**（`ModDataHolder.copy`：`if (target.exists()) { kept++; continue; }` —— 那里写得很清楚：「**已经在那儿的文件是玩家的**」）。** ✓
**⇒ 所以：只改模组里的模板（`assets/modularmachinery/default_machinery|default_recipes/`）**不会有任何效果**，你会以为功能又坏了。** ✓

**⇒ 处理办法（二选一）：**
- **直接改 `config/modularmachinery/` 里**已经抽出来**的那一份 —— 本次 §692 就是这么做的（模板 + 你客户端的实例目录 `...\versions\<版本>\config\modularmachinery\` 里**同一份文件**都改了，两份内容一致）。
- 或者**删掉**该文件（机器名或配方名对应的 `.json`）**重启游戏**，让它按新模板重新抽一份。

**⇒ 无论用哪种，**改完执行一次游戏内 `/reload` 就够了**：定义是挂在 `AddReloadListenerEvent` 上的（`MachineReloadListener.onAddReloadListeners`），`/reload` 会重新读取整个 `config/modularmachinery/`。** ✓

### ⚠️ 这一节里字段名的两个坑（都是从代码里读出来的，不是猜的）

| # | 坑 | 依据 |
|---|---|---|
| **1** | **`not-equal-message` 里写**语言键**会被**原样打出来****（显示成 `craftcheck.failure...` 而不是那句话）—— 这个字段**不过翻译**。** | `RequirementInterfaceNumInput.describeFailure`：`customMessage.isEmpty() ? Component.translatable("craftcheck.failure.interface.number.notequal").getString() : customMessage` —— **只有内置那句走翻译**；自定义那句由 `RecipeCraftingContext.describeBlockers` 作为**纯字符串**拼进状态里。**⇒ 所以这里写的是一句中英并列的**字面文本**。** ✓ |
| **2** | **`header-info` / `footer-info` **是**语言键（会被 `Component.translatable` 翻译），而 `value-info` 是 `String.format` 的格式串**（如 `"%.0f"`）—— 三个字段三种含义，别混。** | `ScreenSmartInterface` 对 header/footer 调 `Component.translatable(...)`；`TileSmartInterface` 用 `String.format(getValueInfo(), value)`。**✓ |

**⇒ 本次改动的验证方式（不是靠眼睛）：自检套件里新加的一条检查 `the shipped iron_centrifuge gates its recipes on a declared interface number` 直接从**已加载的配方对象**上读 `min`/`max`/接口名/`default-value`，断言：**每条配方恰好带 1 条接口需求** · **接口名是机器**声明过**的** · **各组范围两两不相交** · **至少两组** · **`default-value` 恰好落在一组里**；而 16/16 那一半里 `case2`（就是这台机器 + `..._grass`）**真的把配方跑完**（4 个部件全部绑定，含智能接口）。** ✓

---

## 9. 三个会被静默坑到的点（都是本移植实际踩过的）

| # | 坑 | 症状 |
|---|---|---|
| **1** | **`registryname`（机器）与 `registryName`（配方）大小写不同** | **写错那个不会报错，配方只是**永远不被匹配**** |
| **2** | **`elements` 数组是**任选其一**，不是「必须全部」** | **写 `["A","B"]` 以为要两个块，实际是 A 或 B** |
| **3** | **配方的 `machine` 必须精确等于机器的 `registryname`** | **而**一个错的但真实存在的机器名**会让配方挂在一台**别的**机器上** —— **而那正是 §634 花了三轮才找到的形状** ✓ |


全局批量默认、工厂默认线程数、ME 气体总线及各类魔法接口的当前写法，见[整合包作者教程](PACK_AUTHOR_GUIDE_ZH.md)。本文件较早示例中的 2048 表示初始全局默认，实际以 COMMON 配置和机器显式设置为准。
