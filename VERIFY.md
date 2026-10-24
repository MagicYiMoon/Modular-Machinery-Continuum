# 开发回归运行方法

MMC 1.2.0 已通过 Forge 47.3.0 独立环境与 Flux Networks 7.2.1.15 联动服务端回归，包含全部能源仓等级与六面原生接口、真实无线传输与能量守恒，以及独立控制器等已有专项。运行方法如下；本机日志不包含在公开包内。

未启动真实客户端，客户端画面与交互仍需验证。通量网络连接方式见[教程](docs/FLUX_NETWORKS_ZH.md)。

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

通量网络开发运行另外添加 `-PwithFluxIntegration`。全联动组合另外添加 `-PwithIntegrations -PwithGasIntegration -PwithMagicIntegration -PwithBloodMagicIntegration -PwithAuraIntegration`。开发开关只决定运行时加载哪些外部模组，不会把它们打进发布 JAR。

缓存完整时可加 `--offline`。先确认日志存在 `OVERALL: PASS` 且没有专项失败，再确认 Gradle 构建成功；单独的 `BUILD SUCCESSFUL` 不能证明服务端自测成功。旧日志里故意触发的坏 JSON 保存错误属于失败保护回归，不等于本轮联动异常。

发布产物为 `build/libs/modularmachinerycontinuum-1.2.0-1.20.1-all.jar`，Jar-in-Jar 仅包含 GeckoLib 4.8.4。文档修改本身无需重新构建模组。

## 独立控制器的启动条件

测试无法在注册冻结后创建新方块。想同时验证专属普通与工厂控制器，可以在首次启动前放入一份有效机器 JSON 并声明 `"has-factory": true`，或在没有 `config/modularmachinery/` 的隔离实例中，仅预先创建 `config/modularmachinery-common.toml`：

```toml
[factory-system]
enable-factory-controller-bydefault = true
```

然后按前文命令运行自测。前一种验证按机器注册，后一种同时验证首次默认配置提取及全局工厂注册。没有启用任何工厂时，自测仍验证普通独立控制器的四个朝向。回归会临时修改隔离机器定义并恢复原文件，不要对真实整合包运行。
