# MMC 2.0.3 测试记录

在 Java 17／Forge 47.4.23 下，独立服务端和启用 AE2 联动的服务端均通过 `[component-feedback] OVERALL: PASS`。

- 验证内置 12 份微型／小型物品、流体与能源输入／输出仓配方，以及产物放置后的容量和拆除掉落。
- 验证创造模式木棍替换能源仓后，已取得的能量能力和存储 FE 均遵循新档位。
- 验证五档升级总线的菜单数据和模块描述来自服务端。客户端界面的暗化和排版尚未进游戏实测。
- 验证生存拆卸、Forge 取消破坏、保留共享结构、带 NBT 的模块退还及背包回收。AE2 组也验证了实际无线入库及拒收时的回退，并通过 `[wireless-assembly] OVERALL: PASS`。

构建方式见 [BUILDING.md](BUILDING.md)，改动和联机升级说明见 [RELEASE_NOTES.md](RELEASE_NOTES.md)。
