# MMC 1.1.0：独立控制器与颜色编辑

适用 Minecraft 1.20.1、Forge 47.x、Java 17；客户端和服务端都更新到 MMC 1.1.0，网络协议为 32。

## 每台机器的控制器

与源版 MMCE 一样，机器定义在启动时生成专属控制器。以 `registryname` 为 `my_smelter` 的机器为例：

| 方块／物品 ID | 注册条件 |
| --- | --- |
| `modularmachinery:my_smelter_controller` | 启动时读取到该机器 JSON |
| `modularmachinery:my_smelter_factory_controller` | 该机器声明 `"has-factory": true`，或开启全局默认工厂注册 |

独立控制器放置后固定绑定自己的机器，不必先用指令绑定；普通与工厂控制器复用原来的结构匹配、加工、线程、升级与 GeckoLib 渲染逻辑。同一结构可以用于不同机器，它们的专属控制器仍各自运行自己的配方。`requires-blueprint` 为 `true` 时仍需放入对应蓝图；其他机器的蓝图不能插入专属控制器。

创造模式和指令可取得独立控制器，JEI 的结构页会显示它们；查看某个独立控制器的用途，可定位到所属机器的配方。模组不会自动为整合包自定义控制器生成生存合成配方，作者需要用数据包或 CraftTweaker 添加。

旧通用控制器 `modularmachinery:blockcontroller`、`modularmachinery:blockfactorycontroller` 保留原来的绑定与加工方式，已有存档不必拆除重建。专属控制器不能通过 `/modularmachinery bind`、其他蓝图或方块实体 NBT 改绑成另一台机器。

## 配置与可视化编辑

以下字段加入已有的机器 JSON，结构部分仍按原教程编写：

```json
{
  "registryname": "my_smelter",
  "localizedname": "我的冶炼炉",
  "color": "55AAFF",
  "has-factory": true,
  "requires-blueprint": false,
  "parts": [
    { "x": 1, "y": 0, "z": 0, "elements": "modularmachinery:blockinputbus" },
    { "x": 2, "y": 0, "z": 0, "elements": "modularmachinery:blockoutputbus" }
  ]
}
```

`color` 是六位 RGB 十六进制字符串，不带 `#`；`has-factory` 默认 `false`。全局注册开关位于 `config/modularmachinery-common.toml`：

```toml
[factory-system]
enable-factory-controller-bydefault = false
```

权限等级 2 的作者执行 `/modularmachinery gui`，选中机器后点击“控制器外观”。窗口支持六位 RGB 输入（可带 `#`）、预设调色板、普通与工厂控制器预览和独立工厂开关。“保存”只修改源机器 JSON 的 `color` 与 `has-factory`，保留结构和其他字段；“返回”或 Esc 放弃未提交的修改。保存等待服务器确认，失败会保留输入并显示原因。

颜色作用于带染色索引的控制器外壳，正面图案保留；普通与工厂共用机器的 `color`。旧通用控制器放在世界后按绑定机器染色，通用控制器物品保持原外观。这里的预览不运行 GeckoLib 动画，也不改模型贴图；GeckoLib 仍按高级教程配置。

名称仍在主编辑界面的名称栏修改，支持原来的颜色与格式代码。改显示名称不会改变方块 ID。资源包也可通过 `machine.modularmachinery.my_smelter.name` 提供不同语言的机器名称，控制器名称会沿用该翻译。

## 什么需要重启

| 操作 | 生效方式 |
| --- | --- |
| 修改机器名称、颜色、配方、结构 | 保存自动重载，或手动 `/reload` |
| 新机器 JSON 首次生成专属控制器 | 配置放到两端后，重启客户端与服务端 |
| 首次开启一台机器的独立工厂控制器 | 保存后重启两端 |
| 修改全局工厂注册开关 | 重启两端 |

Forge 在启动阶段冻结方块注册表，因此无法通过游戏内重载凭空增加方块。通过建造工具或 `/modularmachinery register` 创建机器后，可先使用通用控制器；新专属控制器在下次启动注册。只有运行时脚本创建、没有启动 JSON 的机器也继续使用通用控制器；要独立方块身份，应提供稳定的机器 JSON。

远程服务器的定义同步会更新名称、颜色与配方，但不会在客户端补注册方块。作者应在启动前向两端分发相同的机器 JSON、工厂注册设置及下面的注册记录。

## 升级已有整合包与保留旧方块

1. 备份世界和 `config/modularmachinery/`，用 1.1.0 的 `-all.jar` 替换旧 MMC JAR。
2. 把机器 JSON 与工厂注册设置同步给客户端和服务端，再完整启动。
3. 首次启动会创建 `config/modularmachinery/controller-registry.json`。它记录已经注册过的机器身份与工厂类型，应与机器配置一起备份和分发。

该记录保留历史身份：删除机器 JSON 或关闭 `has-factory` 后，旧控制器 ID 仍注册，已有方块和物品不会因此被删除。缺失定义的控制器不能继续提供该机器的加工能力，恢复原定义后再重载。关闭工厂开关不会强行删除已注册的工厂类型。

不要在已有世界中清空、删除或随意修改注册记录，也不要为了改显示名称而改 `registryname`。改注册名会产生另一个身份；机器名与现有方块／物品 ID 冲突时会报错，应选择另一个稳定名字。损坏的注册记录会阻止启动，请从备份恢复。

本更新兼容本移植版本原有控制器与方块实体 ID，不提供 Minecraft 1.12.2 世界的直接转换。

## 自定义材质、掉落与合成

模组自动生成低优先级的内置资源包和数据包，为每个专属 ID 提供普通模型、掉落与石镐及以上工具标签。作者资源包和世界数据包可正常覆盖它们。`config/modularmachinery/generated_controller_assets/` 与 `generated_controller_data/` 是生成缓存，内容可能重新生成，不要把它们当作作者配置编辑入口。

资源包可覆盖：

```text
assets/modularmachinery/blockstates/my_smelter_controller.json
assets/modularmachinery/models/item/my_smelter_controller.json
```

工厂将文件名后缀改为 `_factory_controller`。默认方块状态沿用 `facing` 和 `formed`；自定义外壳模型需要染色时使用 `tintindex: 1`，正面覆盖层可以不染色。完整 GeckoLib 模型继续使用[整合包作者教程](PACK_AUTHOR_GUIDE_ZH.md)中的绑定方式。

数据包可添加 `data/<你的命名空间>/recipes/my_smelter_controller.json`：

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    { "item": "modularmachinery:blockcontroller" },
    { "item": "minecraft:redstone" }
  ],
  "result": { "item": "modularmachinery:my_smelter_controller", "count": 1 }
}
```

此例要求 `my_smelter` 已在启动时注册。自定义掉落表的路径为 `data/modularmachinery/loot_tables/blocks/my_smelter_controller.json`；默认掉落对应专属物品并保留所有者，复制方块也保留身份与所有者。需要改变生存获取方式时，优先维护自己的数据包。
