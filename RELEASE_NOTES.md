# Modular Machinery: Continuum 1.0.0 — Minecraft 1.20.1

面向整合包作者的自定义多方块机械模组，移植自 Modular Machinery Community Edition 2.3.2。

- 普通机器、工厂独立线程和批量加工；同种配方最多占用一个线程。
- 物品、流体、FE、智能接口、升级、修饰符、蓝图三维预览、世界投影和生存自动装配。
- 机器与配方 JSON、CraftTweaker 脚本和已有机器／配方的可视化编辑。
- 内嵌 GeckoLib Forge 4.8.4，支持作者配置机器动态模型；不附送梦之能量核心示例资源。
- JEI、AE2、Mekanism、The One Probe 及部分魔法模组可选联动，详见作者教程。

安装 `modularmachinerycontinuum-1.0.0-1.20.1-all.jar`。基础版验证环境为 Forge 47.3.0；使用 Extra Utilities Rebirth 1.7.3 时使用已验证的 Forge 47.4.23。外部可选模组及其前置需另装。客户端和服务器需安装相同版本，本版网络协议 31。

本次项目名称统一为 **Modular Machinery: Continuum（MMC，模块化机械：延续）**，内部 ID 仍为 `modularmachinery`，配置与脚本路径沿用。更新时用新 JAR 替换旧 Reborn JAR，避免重复安装。

普通 FE 仓容量 tiny → ultimate：1000、10000、100000、1000000、10000000、100000000、1000000000、2147483647 FE。旧配置不会自动覆盖，按作者教程合并 `[energyhatch.size]`，传输速率保持原设置。

源质、GP、Impetus 六个接口仍为占位，不具备相应资源处理功能；虹彩采用相邻发电机运行检测，与原版玩家频率网络存在差别。不要直接将 1.12.2 存档或全部脚本视为兼容。

本模组采用 GPL-3.0-only，分发时提供对应源码和许可证。内嵌 GeckoLib 按其 MIT 许可分发，其声明和许可证保留在 JAR 内。原版及社区版作者署名保留在源码与模组元数据中。
