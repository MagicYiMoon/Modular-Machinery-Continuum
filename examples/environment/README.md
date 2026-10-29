# 环境实验台示例（源码待构建）

先使用包含环境检测源码的同一版 MMC 启动两端。现有发布 JAR 尚不包含本示例需要的方块和需求。

1. 将 `machinery/environment_lab.json` 放到游戏实例 `config/modularmachinery/machinery/`。
2. 配方二选一：将 `recipes/environment_lab_daylight.json` 放到 `config/modularmachinery/recipes/`；或安装 CraftTweaker 14，把 `environment_lab.zs` 放到实例 `scripts/`。
3. 新机器身份需要重启两端。放置其独立控制器；也可看着通用控制器执行 `/modularmachinery bind environment_lab`。
4. 控制器上方放环境检测器，右侧放默认 tiny 输入总线，左侧放默认 tiny 输出总线。所有位置相对于朝北控制器，Y 均为 0，检测器为 Y=1；结构可整体旋转。不需要蓝图或能源仓。
5. 主世界白天（0～12000 tick，含边界）放入煤，100 个推进 tick 后得到钻石。夜间运行会暂停，次日恢复。开始时消耗的煤不会因等待而返还。

详细配置、其他条件和行为差异见[环境教程](../../docs/ENVIRONMENT_ZH.md)。这里只是演示配方，请按整合包平衡需要修改产物。
