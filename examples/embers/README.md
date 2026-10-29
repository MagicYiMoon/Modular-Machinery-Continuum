# 余烬实验台（源码待构建）

需要包含余烬联动源码的 MMC，以及两端另装 Embers Rekindled 1.20.1-1.4.7 beta。现有 1.2.1 JAR 不包含此功能。

1. 把 `machinery/ember_lab.json` 放入实例的 `config/modularmachinery/machinery/`。
2. 配方二选一：把 `recipes/ember_lab_coal.json` 放入 `config/modularmachinery/recipes/`；或者另装 CraftTweaker 14，把 `ember_lab.zs` 放入 `scripts/`。
3. 新机器身份需要重启两端。放置对应独立控制器；或看着通用控制器执行 `/modularmachinery bind ember_lab`。
4. 相对于朝北控制器，右侧放默认 tiny 物品输入总线，左侧放默认 tiny 物品输出总线，上方放 tiny 余烬输入仓。结构可以整体旋转，不需要 FE 仓或蓝图。
5. 在余烬仓外侧附着 Embers 接收器，例如仓室上方朝 UP 的接收器；按 Embers 的方式给接收器供能。
6. 放入煤，储量达到 12.25 余烬后开始，100 个推进 tick 后得到钻石。余烬只在开始时支付一次。

这是展示用配方，请自行调整平衡。更多等级、选择器、批量和限制见[余烬教程](../../docs/EMBERS_ZH.md)。
