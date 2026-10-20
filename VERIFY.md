# 开发回归运行方法

截至 2026-10-20，Forge 47.3.0 独立运行与 Forge 47.4.23 全联动服务端回归均通过，包含 FE 大容量及菜单同步专项。原始本机日志没有放进公开源码包；以下命令供开发者复现检查。

服务端回归不等于客户端画面或所有整合包兼容性保证。功能范围见[项目说明](README.md)，教程见[文档目录](docs/README.md)。

## 在源码工作区运行

使用 Java 17，先按[依赖说明](libs/optional/README.md)准备编译依赖。在独立的开发运行目录中配置 `eula.txt` 与 `server.properties`，仅在同意 Minecraft EULA 后设置 `eula=true`；不要把 `selfTestRunDir` 指向真实游戏存档。自测会放置测试机器、改动测试配置，并在结束后关闭服务器。

```powershell
.\gradlew.bat build runServer -PselfTest -PselfTestRunDir=run-docs-standalone
```

上面不传任何 `with...Integration` 开关，用于验证缺少可选模组时仍能运行。只安装新 Astral／虹彩联动的测试命令：

```powershell
.\gradlew.bat build runServer '-Pforge_version=47.4.23' -PselfTest -PwithAstralIntegration -PwithRainbowIntegration -PselfTestRunDir=run-docs-astral-rainbow
```

启用虹彩回归前，把[可选标签修复数据包](examples/datapacks/mmr-extrautilitiesrebirth-1.7.3-tags/README.md)复制到该测试世界的 `datapacks/`，默认路径为 `run-docs-astral-rainbow/world/datapacks/`。虹彩专项会断言依赖的有效冰与粉红染料标签成员，因此运行该回归需要此修复示例。

全联动组合另外添加 `-PwithIntegrations -PwithGasIntegration -PwithMagicIntegration -PwithBloodMagicIntegration -PwithAuraIntegration`。开发开关只决定运行时加载哪些外部模组，不会把它们打进发布 JAR。

缓存完整时可加 `--offline`。先确认日志存在 `OVERALL: PASS` 且没有专项失败，再确认 Gradle 构建成功；单独的 `BUILD SUCCESSFUL` 不能证明服务端自测成功。旧日志里故意触发的坏 JSON 保存错误属于失败保护回归，不等于本轮联动异常。

发布产物为 `build/libs/modularmachinerycontinuum-1.0.0-1.20.1-all.jar`，Jar-in-Jar 仅包含 GeckoLib 4.8.4。文档修改本身无需重新构建模组。
