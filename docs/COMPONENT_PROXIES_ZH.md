# 第三方部件代理扩展（Forge 1.20.1）

MMC 1.3.0 新增。本功能面向 Java 附属模组开发者；没有新增 JSON／ZenScript 注册语法。

> 2026-10-28 后续修正：common setup 期间 Forge 游戏总线尚未启动，注册事件现使用 MOD 总线。旧版示例中的 `Bus.FORGE` 应改为 `Bus.MOD`。

## 接入方式

结构 JSON 允许一个方块，只决定它能否匹配结构。让它参与 MMC 配方，还需要附属模组注册 `MachineComponentProxy`，明确识别哪些方块实体可以接入，并提供机器内部使用的处理器。

`RegisterMachineComponentProxiesEvent` 在排队执行的 common setup 阶段通过 **MOD 生命周期事件总线**发布一次（每个订阅模组收到一次）。附属模组监听此事件，向 `event.getRegistry()` 注册代理。注册项按插入顺序匹配，重名抛出异常；第一个支持该方块的代理决定结果，返回 null 表示当前不可用。原生 `MachineComponentTile` 始终优先，不能用代理覆盖。

相关类型：

```java
hellfirepvp.modularmachinery.common.event.RegisterMachineComponentProxiesEvent
hellfirepvp.modularmachinery.common.machine.component.MachineComponentProxy
hellfirepvp.modularmachinery.common.machine.component.MachineComponentProxyRegistry
hellfirepvp.modularmachinery.common.machine.component.ProxiedMachineComponent
```

下面是附属模组内的最小示例：**主动把原版木桶注册为物品输入**。本体没有内置木桶规则，原版木桶不会自动变成输入总线。把 `your_addon` 换成附属模组 ID，并确保它依赖包含本次更新的 MMC。

```java
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(
    modid = "your_addon", bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class BarrelInputBridge {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void register(
            hellfirepvp.modularmachinery.common.event.RegisterMachineComponentProxiesEvent event) {
        event.getRegistry().register("your_addon:barrel_input",
            new hellfirepvp.modularmachinery.common.machine.component.MachineComponentProxy() {
                public boolean isSupported(net.minecraft.world.level.block.entity.BlockEntity be) {
                    return be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity;
                }
                public hellfirepvp.modularmachinery.common.machine.component.ProxiedMachineComponent
                        proxyComponent(net.minecraft.world.level.block.entity.BlockEntity be) {
                    var items = be.getCapability(
                        net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElse(null);
                    return items == null ? null :
                        hellfirepvp.modularmachinery.common.machine.component.ProxiedMachineComponent.of(
                            hellfirepvp.modularmachinery.common.machine.IOType.INPUT, items, null, null);
                }
            });
    }
}
```

然后在机器结构相应位置使用 `minecraft:barrel`，该位置才会参加配方输入。代理不会自动修改机器结构、增加候选方块、读取该模组配方或改变木棍自动搭建的放置规则。

## 处理器约定

| 项目 | 约定 |
| --- | --- |
| 输入／输出 | 返回明确的 `IOType.INPUT` 或 `OUTPUT`；无方向的代理不接入 |
| 物品 | `IItemHandler`，输入支持抽取，输出支持插入 |
| 流体 | `IFluidHandler`，输入支持排出，输出支持灌入 |
| FE | `IEnergyStorage`，输入支持 `extractEnergy`，输出支持 `receiveEnergy`；当前此通道数值为 int FE |
| 选择器 | 原有结构 `selectorTag` 继续过滤该位置 |
| 多位置同库存 | 必须返回同一个稳定处理器对象，让预留与去重识别同一份库存 |
| 同一方块多类 IO | `of(direction, items, fluids, energy)` 可提供同方向的多类处理器；无对应类型传 null |

输入仓对外通常只允许灌入，机器加工却需要从内部取出，不能把外部“禁止抽取”的能力直接当作内部处理器。具体模组适配应使用它的内部库存／能力接口，并落实其权限、方向、失效规则。EU、激光等能量不是声明一个 FE 能力就完成转换；转换倍率、电压和限制必须在专用适配中明确实现。

模拟调用不得修改库存，执行结果须与同一服务器线程上紧接着的模拟一致。代理不得在查询／模拟中改世界、触发配方或换库存。现有加工器会先联合预留再执行；它不提供跨任意第三方处理器的通用事务回滚。实现违反模拟契约可能造成部分扣料或重复尝试，不应把这种处理器注册进去。

`proxyComponent` 可以返回临时视图，但视图暴露的底层处理器身份应稳定；不要每次查询都创建新的库存包装器。能力失效或方块替换后必须返回新的有效处理器或 null，不能保留失效的 `LazyOptional` 解析结果。MMC 每次能力查询重新解析代理，不把外部方块实体或库存存入全局缓存；原生能量仓的内部视图仅在当前加工上下文内按方块实体身份缓存。

## 生命周期与范围

成型时只记录实际结构内的外部部件位置；加工使用这些位置，并在访问能力前检查区块已加载。重绑会重建列表，删除、替换或注销代理后后续查询不再得到原处理器。结构破坏使加工停止，保留的历史位置仍按既有规则用于后续重绑，不会给外部方块写入 MMC 的控制器归属 NBT。

注册应在启动事件内完成。运行中新增代理不会主动让所有已成型机器重新扫描；需要重新成型／重绑。`/reload` 不重复发布启动注册事件。Java 注册表提供 `unregister`，但它不承担附属模组的能力清理工作。

这次提供通用注册与加工接入，已新增 [GTCEu Modern 7.5.3 普通物品总线／流体仓／EU 能量仓适配](GTCEU_ZH.md)，**激光和特殊 ME 等类型尚未实现**，也未新增通用外部升级总线、智能接口或跨模组 AE 样板隔离适配。已有 MMC 样板供应器和镜像仍走原生隔离路径。源版针对旧 GTCEu 的类名、能力接口及部分单槽限制不能直接照搬成现代联动。

实际验证范围见[功能范围与测试说明](../TESTING.md)与 [功能范围与测试说明](../TESTING.md)。
