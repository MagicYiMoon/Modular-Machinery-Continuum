# Extra Utilities Rebirth 1.7.3 标签修复示例

此数据包可选，用于 GitHub `AuramMods/ExtraUtilities` 发布标签 `1.0.0`、内部模组版本 `1.7.3` 的原始 JAR。

原发布包的 `forge:frosty_gen_tag` 和 `forge:pink_items` 把部分只有方块、没有物品的 ID 放进物品标签，导致整组标签加载失败。此示例用 `replace: true` 保留原列表中的有效物品并去重，移除五项无效引用，不添加新的替代燃料。

复制此整个目录到目标存档的 `datapacks/` 下，执行 `/reload`。在服务器上使用服务器存档的数据包目录。目录中须能直接看到 `pack.mcmeta` 和 `data/`。

如果整合包已自定义这两组标签，请在自己的数据包里修正，不要直接用本示例覆盖配方设计。移除此目录并再次重载即可撤销。MM 发布 JAR 不自动安装该数据包。
