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

安装 `modularmachinerycontinuum-1.1.0-1.20.1-all.jar`，替换旧 MMC／Reborn JAR，避免重复安装。适用 Forge 1.20.1、Java 17，内嵌 GeckoLib Forge 4.8.4；外部可选模组及其必需前置仍需另装。

旧通用控制器、方块实体 ID、蓝图和配置路径保留，已有机器不必拆除重建。新独立控制器不会自动获得整合包的生存合成配方，需要作者添加。

客户端与服务端必须一起更新，本版网络协议为 **32**。颜色、名称与配方可重载；新增机器身份或首次开启独立工厂控制器后，需要两端分发相同配置并重启。

请备份并分发 `config/modularmachinery/controller-registry.json`。该文件保存已注册的历史身份，删除机器定义或关闭工厂开关不会移除旧方块 ID。不要清空已有世界的注册记录。生成的模型／数据缓存不是作者配置入口，应使用自己的资源包与数据包覆盖。

完整说明见 [独立控制器教程](docs/DEDICATED_CONTROLLERS_ZH.md)。旧版教程中的“修改 JSON 后重载”不适用于新增方块注册身份。

源质、GP、Impetus 六个接口仍为占位；本次未扩展这些资源，也不提供 1.12.2 世界直接转换。GeckoLib 动画继续由整合包作者配置，不附送梦之能量核心示例资源。

## 验证与许可

发布前运行独立环境、全联动环境及首次启动回归，覆盖固定绑定、蓝图、真实加工、掉落与所有者、投影、资源包加载、颜色编辑协议及权限保护。具体结果见 [VERIFY.md](VERIFY.md)。服务端回归不能代替客户端新界面的实际画面验证。

源码采用 GPL-3.0-only，发布 JAR 时同时提供对应源码与许可证。内嵌 GeckoLib 的 MIT 许可与第三方声明保留。
